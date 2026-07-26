package dev.marquinhhou.crsscheduler.data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.marquinhhou.crsscheduler.model.ClassSession;

/** Reverse of IcsExporter: .ics back into ClassSessions. Imports at 0 units, creditsExcluded. */
public final class IcsImporter {

    public static final class ParseException extends Exception {
        public enum Reason { NO_CALENDAR, NO_EVENTS }
        public final Reason reason;
        public ParseException(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }
    }

    public static final class Result {
        public final List<ClassSession> classes;
        public final int skipped;
        public Result(List<ClassSession> classes, int skipped) {
            this.classes = classes;
            this.skipped = skipped;
        }
    }

    private static final String[] BYDAY = {"SU", "MO", "TU", "WE", "TH", "FR", "SA"};
    private static final Pattern BYDAY_PATTERN = Pattern.compile("BYDAY=([A-Za-z,]+)");
    private static final Pattern TYPE_PREFIX = Pattern.compile("^(LEC|LAB|REC|DISC|PE)\\b\\s*", Pattern.CASE_INSENSITIVE);

    private IcsImporter() {}

    public static Result parse(String ics) throws ParseException {
        if (ics == null || !ics.toUpperCase(Locale.US).contains("BEGIN:VCALENDAR")) {
            throw new ParseException(ParseException.Reason.NO_CALENDAR);
        }

        List<Map<String, String>> rawEvents = extractEvents(ics);
        if (rawEvents.isEmpty()) throw new ParseException(ParseException.Reason.NO_EVENTS);

        // Merge events sharing (name, room, start, end) into one multi-day class --
        // undoes IcsExporter's "one VEVENT per class-day" splitting.
        Map<String, MergedClass> merged = new LinkedHashMap<>();
        int skipped = 0;
        for (Map<String, String> ev : rawEvents) {
            RawEvent parsed = parseOne(ev);
            if (parsed == null) { skipped++; continue; }
            String key = parsed.name + "\u0001" + parsed.room + "\u0001" + parsed.start + "\u0001" + parsed.end;
            MergedClass mc = merged.get(key);
            if (mc == null) {
                merged.put(key, new MergedClass(parsed));
            } else {
                mc.days.addAll(parsed.days);
            }
        }

        List<ClassSession> classes = new ArrayList<>();
        for (MergedClass mc : merged.values()) {
            if (mc.days.isEmpty()) { skipped++; continue; }
            String code = mc.name.length() > 40 ? mc.name.substring(0, 40) : mc.name;
            classes.add(new ClassSession(code, mc.name, 0, true,
                    new ArrayList<>(mc.days), mc.start, mc.end, mc.type, mc.room, mc.instructor));
        }

        if (classes.isEmpty()) throw new ParseException(ParseException.Reason.NO_EVENTS);
        return new Result(classes, skipped);
    }

    private static List<Map<String, String>> extractEvents(String ics) {
        List<Map<String, String>> events = new ArrayList<>();
        Map<String, String> current = null;
        for (String line : unfold(ics)) {
            String upper = line.toUpperCase(Locale.US);
            if (upper.startsWith("BEGIN:VEVENT")) {
                current = new LinkedHashMap<>();
            } else if (upper.startsWith("END:VEVENT")) {
                if (current != null) events.add(current);
                current = null;
            } else if (current != null) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String propName = line.substring(0, colon).split(";")[0].toUpperCase(Locale.US);
                current.put(propName, line.substring(colon + 1));
            }
        }
        return events;
    }

    private static final class RawEvent {
        String name, room, instructor, type;
        int start, end;
        TreeSet<Integer> days = new TreeSet<>();
    }

    private static final class MergedClass {
        final String name, room, instructor, type;
        final int start, end;
        final TreeSet<Integer> days = new TreeSet<>();
        MergedClass(RawEvent e) {
            name = e.name; room = e.room; instructor = e.instructor; type = e.type;
            start = e.start; end = e.end;
            days.addAll(e.days);
        }
    }

    private static RawEvent parseOne(Map<String, String> ev) {
        String dtstart = ev.get("DTSTART");
        if (dtstart == null) return null;
        Integer start = timeOfDayMinutes(dtstart);
        if (start == null) return null;

        String dtend = ev.get("DTEND");
        Integer end = dtend != null ? timeOfDayMinutes(dtend) : null;
        if (end == null) end = start + 60; // no/unparseable DTEND -- assume a 1hr block rather than dropping the event
        if (end <= start) return null;

        TreeSet<Integer> days = new TreeSet<>();
        String rrule = ev.get("RRULE");
        if (rrule != null) {
            Matcher m = BYDAY_PATTERN.matcher(rrule);
            if (m.find()) {
                for (String code : m.group(1).split(",")) {
                    int idx = indexOfByDay(code.trim().toUpperCase(Locale.US));
                    if (idx >= 0) days.add(idx);
                }
            }
        }
        if (days.isEmpty()) {
            Integer day = dayOfWeekFromDate(dtstart);
            if (day != null) days.add(day);
        }
        if (days.isEmpty()) return null;

        RawEvent out = new RawEvent();
        out.name = unescapeText(firstNonEmpty(ev.get("SUMMARY"), "Untitled class"));
        out.room = unescapeText(ev.getOrDefault("LOCATION", ""));
        out.start = start;
        out.end = end;
        out.days = days;

        String description = unescapeText(ev.getOrDefault("DESCRIPTION", ""));
        Matcher tm = TYPE_PREFIX.matcher(description);
        if (tm.find()) {
            out.type = tm.group(1).toLowerCase(Locale.US);
            description = description.substring(tm.end());
        } else {
            out.type = "";
        }
        // Our own exporter joins "TYPE \u00B7 instructor \u00B7 X units" -- keep
        // just the first remaining segment as a best-effort instructor name.
        String[] parts = description.split("\u00B7");
        out.instructor = parts.length > 0 ? parts[0].trim() : "";

        return out;
    }

    private static int indexOfByDay(String code) {
        for (int i = 0; i < BYDAY.length; i++) if (BYDAY[i].equals(code)) return i;
        return -1;
    }

    /** Minutes-from-midnight for a DTSTART/DTEND value, regardless of floating/UTC/TZID form -- the date part is ignored. */
    private static Integer timeOfDayMinutes(String value) {
        String v = value.endsWith("Z") ? value.substring(0, value.length() - 1) : value;
        int tIdx = v.indexOf('T');
        if (tIdx < 0 || v.length() < tIdx + 5) return null;
        String time = v.substring(tIdx + 1);
        try {
            int h = Integer.parseInt(time.substring(0, 2));
            int m = Integer.parseInt(time.substring(2, 4));
            return h * 60 + m;
        } catch (Exception e) {
            return null;
        }
    }

    /** JS-style day-of-week (0=Sun..6=Sat) from a DTSTART's date portion -- fallback when there's no RRULE/BYDAY. */
    private static Integer dayOfWeekFromDate(String value) {
        try {
            String datePart = value.substring(0, 8);
            LocalDate date = LocalDate.of(
                    Integer.parseInt(datePart.substring(0, 4)),
                    Integer.parseInt(datePart.substring(4, 6)),
                    Integer.parseInt(datePart.substring(6, 8)));
            DayOfWeek dow = date.getDayOfWeek();
            return dow == DayOfWeek.SUNDAY ? 0 : dow.getValue();
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonEmpty(String a, String fallback) {
        return (a != null && !a.trim().isEmpty()) ? a.trim() : fallback;
    }

    /** Reverses IcsExporter's escape(): backslash-escaped comma/semicolon/backslash/newline. */
    private static String unescapeText(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n': case 'N': out.append('\n'); i++; break;
                    case ',': out.append(','); i++; break;
                    case ';': out.append(';'); i++; break;
                    case '\\': out.append('\\'); i++; break;
                    default: out.append(c);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Unfolds RFC 5545 line continuations (a leading space/tab means "still the previous line"). */
    private static List<String> unfold(String ics) {
        String[] raw = ics.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        List<String> out = new ArrayList<>();
        for (String line : raw) {
            if (!line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t') && !out.isEmpty()) {
                out.set(out.size() - 1, out.get(out.size() - 1) + line.substring(1));
            } else {
                out.add(line);
            }
        }
        return out;
    }
}
