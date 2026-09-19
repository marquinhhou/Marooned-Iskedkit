package dev.marquinhhou.crsscheduler.ui;

import android.content.Context;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.model.ClassSession;

/** Builds an .ics: one weekly-recurring VEVENT per (class, day), floating local time, no TZID. */
public final class IcsExporter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String[] BYDAY = {"SU", "MO", "TU", "WE", "TH", "FR", "SA"};

    private IcsExporter() {}

    public static String build(Context context, List<ClassSession> schedule) {
        LocalDate start = SettingsStore.getSemesterStart(context);
        LocalDate end = SettingsStore.getSemesterEnd(context);
        LocalDate anchor = start != null ? start : LocalDate.now();

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//marquinhhou//Marooned IskedKit//EN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");

        String dtstamp = nowUtcStamp();
        for (ClassSession c : schedule) {
            for (int day : c.days) {
                appendEvent(sb, c, day, anchor, end, dtstamp);
            }
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    public static String suggestedFileName() {
        return "IskedKit_Schedule_" + LocalDate.now().format(DATE_FMT) + ".ics";
    }

    private static void appendEvent(StringBuilder sb, ClassSession c, int day, LocalDate anchor, LocalDate end, String dtstamp) {
        LocalDate eventDate = firstOccurrenceOnOrAfter(anchor, day);
        String dateStr = eventDate.format(DATE_FMT);

        sb.append("BEGIN:VEVENT\r\n");
        foldLine(sb, "UID:" + c.code + "-" + day + "-" + dateStr + "@crsscheduler.marquinhhou.dev");
        sb.append("DTSTAMP:").append(dtstamp).append("\r\n");
        sb.append("DTSTART:").append(dateStr).append("T").append(timeOfDay(c.start)).append("\r\n");
        sb.append("DTEND:").append(dateStr).append("T").append(timeOfDay(c.end)).append("\r\n");

        StringBuilder rrule = new StringBuilder("RRULE:FREQ=WEEKLY;BYDAY=").append(BYDAY[day]);
        if (end != null) {
            rrule.append(";UNTIL=").append(end.format(DATE_FMT)).append("T235959");
        }
        foldLine(sb, rrule.toString());

        foldLine(sb, "SUMMARY:" + escape(c.name));
        String location = c.displayRoom();
        if (location != null && !location.trim().isEmpty()) {
            foldLine(sb, "LOCATION:" + escape(location));
        }
        String description = describeClass(c);
        if (!description.isEmpty()) {
            foldLine(sb, "DESCRIPTION:" + escape(description));
        }
        sb.append("END:VEVENT\r\n");
    }

    private static String describeClass(ClassSession c) {
        StringBuilder d = new StringBuilder();
        if (c.type != null && !c.type.trim().isEmpty()) d.append(c.type.toUpperCase(Locale.US));
        if (c.instructor != null && !c.instructor.trim().isEmpty()) {
            if (d.length() > 0) d.append(" \u00B7 ");
            d.append(c.instructor);
        }
        if (!c.creditsExcluded) {
            if (d.length() > 0) d.append(" \u00B7 ");
            d.append(String.format(Locale.US, "%.1f units", c.credits));
        }
        return d.toString();
    }

    private static LocalDate firstOccurrenceOnOrAfter(LocalDate anchor, int dayOfWeekJs) {
        int isoTarget = dayOfWeekJs == 0 ? 7 : dayOfWeekJs; // JS Sun=0 -> ISO Sun=7
        int isoAnchor = anchor.getDayOfWeek().getValue();
        int daysAhead = Math.floorMod(isoTarget - isoAnchor, 7);
        return anchor.plusDays(daysAhead);
    }

    private static String timeOfDay(int minutesFromMidnight) {
        int h = minutesFromMidnight / 60;
        int m = minutesFromMidnight % 60;
        return String.format(Locale.US, "%02d%02d00", h, m);
    }

    private static String nowUtcStamp() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
    }

    /** RFC 5545 TEXT escaping: backslash, semicolon, comma, then newlines. */
    private static String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }

    /** RFC 5545 folds lines past 75 octets; continuation lines start with a single space. Conservative char-based fold. */
    private static void foldLine(StringBuilder out, String line) {
        int max = 74;
        if (line.length() <= max) {
            out.append(line).append("\r\n");
            return;
        }
        int i = 0;
        boolean first = true;
        while (i < line.length()) {
            int end = Math.min(i + (first ? max : max - 1), line.length());
            out.append(first ? "" : " ").append(line, i, end).append("\r\n");
            i = end;
            first = false;
        }
    }
}
