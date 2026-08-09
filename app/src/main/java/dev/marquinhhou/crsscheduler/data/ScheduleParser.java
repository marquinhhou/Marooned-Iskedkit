package dev.marquinhhou.crsscheduler.data;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.marquinhhou.crsscheduler.model.ClassSession;

/**
 * Extracts a class-list table from a saved CRS page into ClassSessions.
 * Handles both "My Enlisted Classes" (Registration) and "My Desired Classes"
 * (Preenlistment) -- see buildGrid() for why the latter needs special care.
 */
public final class ScheduleParser {

    public static final class ParseException extends Exception {
        public enum Reason { NO_TABLE, NO_ROWS }
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

    private static final Pattern HEADER_CODE = Pattern.compile("class\\s*code", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_CREDITS = Pattern.compile("credits", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_INSTRUCTOR = Pattern.compile("instructor", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_SCHEDULE = Pattern.compile("schedule", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_STATUS = Pattern.compile("status", Pattern.CASE_INSENSITIVE);

    // Matches both Registration's "My Enlisted Classes" and Preenlistment's "My Desired Classes".
    private static final Pattern SECTION_HEADING = Pattern.compile(
            "enlisted|desired\\s*classes", Pattern.CASE_INSENSITIVE);

    // Preenlistment rows carry a status icon (enlisted / desired / with conflict); only
    // "enlisted" rows are a locked-in schedule -- the rest are just ranked hopes.
    private static final Pattern STATUS_ENLISTED = Pattern.compile("enlisted", Pattern.CASE_INSENSITIVE);

    private static final Pattern SCHEDULE_LINE = Pattern.compile(
            "^([A-Za-z]+)\\s+([\\d:apmAPM-]+)\\s*(lec|lab|rec|disc|pe)?\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MODE_PATTERN = Pattern.compile(
            "^(BM\\d*:|F2F:|OFF|FULLY)", Pattern.CASE_INSENSITIVE);

    private static final Pattern TIME_RANGE = Pattern.compile(
            "^(\\d{1,2}(?::\\d{2})?)(AM|PM)?-(\\d{1,2}(?::\\d{2})?)(AM|PM)?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CAMPUS_HINT = Pattern.compile(
            "UP\\s+(Diliman|Los Ba\u00f1os|Manila|Visayas|Mindanao|Baguio|Cebu|Open University)",
            Pattern.CASE_INSENSITIVE);

    private ScheduleParser() {}

    /** Sniffs a campus name (e.g. "UP Diliman") from the page, for scoping the Maps search. */
    public static String detectCampusHint(String html) {
        Matcher m = CAMPUS_HINT.matcher(html);
        if (m.find()) return m.group(0).replaceAll("\\s+", " ").trim();
        return null;
    }

    public static Result parse(String html) throws ParseException {
        Document doc = Jsoup.parse(html);
        Element table = findEnlistedTable(doc);
        if (table == null) throw new ParseException(ParseException.Reason.NO_TABLE);

        List<List<Element>> grid = buildGrid(table);
        Columns cols = detectColumns(grid);
        if (cols == null) throw new ParseException(ParseException.Reason.NO_TABLE);

        List<ClassSession> classes = new ArrayList<>();
        int skipped = 0;

        for (int i = 1; i < grid.size(); i++) {
            List<Element> row = grid.get(i);

            if (cols.status != -1 && !statusAllowsEnlist(cellAt(row, cols.status))) {
                skipped++;
                continue;
            }

            // Each of these cells can hold more than one class stacked with a blank-line
            // separator (cross-listed rows, e.g. two class codes sharing one CRS row).
            List<List<String>> codeBlocks = extractBlocks(cellAt(row, cols.code));
            List<List<String>> classBlocks = extractBlocks(cellAt(row, cols.className));
            List<List<String>> creditsBlocks = extractBlocks(cellAt(row, cols.credits));
            List<List<String>> scheduleBlocks = extractBlocks(cellAt(row, cols.schedule));

            if (codeBlocks.isEmpty() || classBlocks.isEmpty()
                    || creditsBlocks.isEmpty() || scheduleBlocks.isEmpty()) {
                skipped++;
                continue;
            }

            int blockCount = codeBlocks.size();
            boolean aligned = classBlocks.size() == blockCount
                    && creditsBlocks.size() == blockCount
                    && scheduleBlocks.size() == blockCount;
            // If the cells disagree on how many classes are packed into the row, don't
            // guess how to pair them up -- just read the first (and usually only) one.
            if (!aligned) blockCount = 1;

            for (int b = 0; b < blockCount; b++) {
                String code = codeBlocks.get(b).get(0).trim();
                String name = classBlocks.get(b).get(0).trim();
                String creditsRaw = creditsBlocks.get(b).get(0).trim();
                if (code.isEmpty() || name.isEmpty()) { skipped++; continue; }

                boolean creditsExcluded = creditsRaw.matches("^\\(.*\\)$");
                double credits;
                try {
                    credits = Double.parseDouble(creditsRaw.replaceAll("[()]", ""));
                } catch (NumberFormatException e) {
                    credits = 0;
                }

                List<String> lines = scheduleBlocks.get(b);
                Matcher m = SCHEDULE_LINE.matcher(lines.get(0));
                if (!m.matches()) { skipped++; continue; }

                String dayStr = m.group(1);
                String timeStr = m.group(2);
                String type = m.group(3) == null ? "" : m.group(3).toLowerCase(Locale.US);
                String room = m.group(4) == null ? "" : m.group(4).trim();

                List<Integer> days = parseDays(dayStr);
                TimeRange time = parseTimeRange(timeStr);
                if (time == null || days.isEmpty()) { skipped++; continue; }

                String instructor = "";
                for (int li = 1; li < lines.size(); li++) {
                    if (!MODE_PATTERN.matcher(lines.get(li)).find()) {
                        instructor = lines.get(li);
                        break;
                    }
                }

                classes.add(new ClassSession(code, name, credits, creditsExcluded,
                        days, time.start, time.end, type, room, instructor));
            }
        }

        if (classes.isEmpty()) throw new ParseException(ParseException.Reason.NO_ROWS);
        return new Result(classes, skipped);
    }

    private static Element cellAt(List<Element> row, int idx) {
        return idx >= 0 && idx < row.size() ? row.get(idx) : null;
    }

    // Table discovery

    /**
     * Finds the class-list table: "My Enlisted Classes" on Registration, "My Desired
     * Classes" on Preenlistment. The page has several tables that share a near-identical
     * header (Waitlisted, Canceled, etc.), so we anchor on the section heading first
     * (most reliable), and only fall back to guessing from header keywords alone.
     */
    private static Element findEnlistedTable(Document doc) {
        Element byHeading = findTableAfterHeading(doc);
        if (byHeading != null) return byHeading;
        return findTableByHeaderRow(doc);
    }

    private static Element findTableAfterHeading(Document doc) {
        Elements markers = doc.select("h1, h2, h3, h4, h5, legend, caption, table");
        boolean latched = false;
        for (Element el : markers) {
            if (el.tagName().equalsIgnoreCase("table")) {
                if (latched) return el;
            } else {
                // Sticky on purpose: CRS sometimes inserts an unrelated aside (e.g. a
                // "Notes" legend) between the section heading and its table, and that
                // shouldn't cancel a real match -- only the next table does.
                if (SECTION_HEADING.matcher(el.text().trim()).find()) latched = true;
            }
        }
        return null;
    }

    private static Element findTableByHeaderRow(Document doc) {
        Elements tables = doc.select("table");
        for (Element t : tables) {
            Elements rows = t.select("tr");
            if (rows.isEmpty()) continue;
            String headText = rows.get(0).text();
            if (HEADER_CODE.matcher(headText).find()
                    && HEADER_CREDITS.matcher(headText).find()
                    && HEADER_INSTRUCTOR.matcher(headText).find()) {
                return t;
            }
        }
        return null;
    }

    // Grid construction

    /**
     * Resolves a table's rows into a rectangular grid of cells, accounting for rowspan
     * and colspan. Needed because CRS's Preenlistment "My Desired Classes" table rowspans
     * the Rank/Status/Restrictions columns across a whole group when a course has more than
     * one component (lecture + discussion each need their own class code) -- so a component
     * row's physical &lt;td&gt; list is shorter than the header's, and reading cells by a
     * flat header-derived index silently misaligns onto the wrong columns. This rebuilds
     * each row to its full logical width first, so column lookups always land correctly.
     */
    private static List<List<Element>> buildGrid(Element table) {
        Elements rows = table.select("tr");
        List<List<Element>> grid = new ArrayList<>();

        Map<Integer, Element> carryElement = new HashMap<>();
        Map<Integer, Integer> carryRemaining = new HashMap<>();

        for (Element row : rows) {
            Elements cells = row.select("> td, > th");

            // Snapshot carries that were already active coming into this row -- only
            // these get decremented once consumed, not any rowspan this row itself starts.
            Map<Integer, Element> carriedCols = new HashMap<>();
            for (Map.Entry<Integer, Integer> e : carryRemaining.entrySet()) {
                if (e.getValue() > 0) carriedCols.put(e.getKey(), carryElement.get(e.getKey()));
            }

            Map<Integer, Element> rowOut = new HashMap<>();
            int col = 0;
            int ci = 0;
            while (ci < cells.size() || anyCarriedAtOrAfter(carriedCols, col)) {
                if (carriedCols.containsKey(col)) {
                    rowOut.put(col, carriedCols.get(col));
                    col++;
                    continue;
                }
                if (ci >= cells.size()) break;
                Element cell = cells.get(ci);
                int colspan = parseSpan(cell.attr("colspan"));
                int rowspan = parseSpan(cell.attr("rowspan"));
                for (int k = 0; k < colspan; k++) {
                    rowOut.put(col + k, cell);
                    if (rowspan > 1) {
                        carryElement.put(col + k, cell);
                        carryRemaining.put(col + k, rowspan - 1);
                    }
                }
                col += colspan;
                ci++;
            }

            for (Integer c : carriedCols.keySet()) {
                carryRemaining.put(c, carryRemaining.get(c) - 1);
            }

            int width = -1;
            for (Integer c : rowOut.keySet()) width = Math.max(width, c);
            List<Element> rowList = new ArrayList<>();
            for (int c = 0; c <= width; c++) rowList.add(rowOut.get(c));
            grid.add(rowList);
        }

        int gridWidth = 0;
        for (List<Element> r : grid) gridWidth = Math.max(gridWidth, r.size());
        for (List<Element> r : grid) while (r.size() < gridWidth) r.add(null);
        return grid;
    }

    private static boolean anyCarriedAtOrAfter(Map<Integer, Element> carriedCols, int col) {
        for (Integer c : carriedCols.keySet()) if (c >= col) return true;
        return false;
    }

    private static int parseSpan(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 1;
        try {
            int n = Integer.parseInt(raw.trim());
            return n > 0 ? n : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    // Column discovery

    private static final class Columns {
        final int code, className, credits, schedule, status;
        Columns(int code, int className, int credits, int schedule, int status) {
            this.code = code;
            this.className = className;
            this.credits = credits;
            this.schedule = schedule;
            this.status = status;
        }
    }

    /**
     * Reads the header row to find which cell holds what, instead of assuming fixed
     * positions -- both target tables put Status first, but not every CRS variant does.
     * Status is optional (-1 if absent); the other four are required.
     */
    private static Columns detectColumns(List<List<Element>> grid) {
        if (grid.isEmpty()) return null;
        List<Element> header = grid.get(0);

        int codeIdx = -1, classIdx = -1, creditsIdx = -1, scheduleIdx = -1, statusIdx = -1;
        for (int i = 0; i < header.size(); i++) {
            Element cell = header.get(i);
            if (cell == null) continue;
            String text = cell.text();

            if (statusIdx == -1 && HEADER_STATUS.matcher(text).find()
                    && !HEADER_CODE.matcher(text).find()) {
                statusIdx = i;
            }
            if (codeIdx == -1 && HEADER_CODE.matcher(text).find()) {
                codeIdx = i;
            } else if (creditsIdx == -1 && HEADER_CREDITS.matcher(text).find()) {
                creditsIdx = i;
            } else if (scheduleIdx == -1 && HEADER_SCHEDULE.matcher(text).find()) {
                scheduleIdx = i;
            } else if (classIdx == -1 && text.toLowerCase(Locale.US).contains("class")
                    && !HEADER_CODE.matcher(text).find()) {
                classIdx = i;
            }
        }

        if (codeIdx == -1 || classIdx == -1 || creditsIdx == -1 || scheduleIdx == -1) return null;
        return new Columns(codeIdx, classIdx, creditsIdx, scheduleIdx, statusIdx);
    }

    /** True unless the status cell's icon clearly says this row isn't actually enlisted. */
    private static boolean statusAllowsEnlist(Element statusCell) {
        if (statusCell == null) return true;
        Element img = statusCell.selectFirst("img");
        if (img == null) return true;
        String label = (img.attr("alt") + " " + img.attr("title")).trim();
        if (label.isEmpty()) return true;
        return STATUS_ENLISTED.matcher(label).find();
    }

    /**
     * Splits a cell's &lt;br&gt;-separated lines into blocks, starting a new block on a blank
     * line (a "&lt;br&gt;&lt;br&gt;"). A cross-listed CRS row packs two classes into one row this
     * way -- e.g. the Class Code cell holds "66661&lt;br&gt;&lt;br&gt;66655" for two sections
     * sharing a row, and the Schedule cell holds the matching two schedule/instructor groups.
     * A normal single-class cell just comes back as one block.
     */
    private static List<List<String>> extractBlocks(Element cell) {
        List<List<String>> blocks = new ArrayList<>();
        if (cell == null) return blocks;

        List<String> rawLines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode) {
                    current.append(((TextNode) node).text());
                } else if (node instanceof Element) {
                    Element el = (Element) node;
                    if (el.tagName().equalsIgnoreCase("br")) {
                        rawLines.add(current.toString());
                        current.setLength(0);
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) { }
        }, cell);
        rawLines.add(current.toString());

        List<String> currentBlock = new ArrayList<>();
        for (String raw : rawLines) {
            String t = raw.trim().replaceAll("\\s+", " ");
            if (t.isEmpty()) {
                if (!currentBlock.isEmpty()) {
                    blocks.add(currentBlock);
                    currentBlock = new ArrayList<>();
                }
            } else {
                currentBlock.add(t);
            }
        }
        if (!currentBlock.isEmpty()) blocks.add(currentBlock);
        return blocks;
    }

    // Day / time parsing

    private static List<Integer> parseDays(String str) {
        List<Integer> days = new ArrayList<>();
        int i = 0;
        while (i < str.length()) {
            String two = i + 2 <= str.length() ? str.substring(i, i + 2) : "";
            if (two.equals("Th")) { days.add(4); i += 2; continue; }
            if (two.equals("Su")) { days.add(0); i += 2; continue; }
            char c = str.charAt(i);
            if (c == 'M') days.add(1);
            else if (c == 'T') days.add(2);
            else if (c == 'W') days.add(3);
            else if (c == 'F') days.add(5);
            else if (c == 'S') days.add(6);
            i += 1;
        }
        return days;
    }

    private static final class TimeRange {
        final int start, end;
        TimeRange(int start, int end) { this.start = start; this.end = end; }
    }

    private static TimeRange parseTimeRange(String raw) {
        String str = raw.replaceAll("\\s+", "");
        Matcher m = TIME_RANGE.matcher(str);
        if (!m.matches()) return null;

        String sTime = m.group(1);
        String eTime = m.group(3);
        String sMer = m.group(2) != null ? m.group(2).toUpperCase(Locale.US) : null;
        String eMer = m.group(4) != null ? m.group(4).toUpperCase(Locale.US) : null;

        if (eMer == null && sMer != null) eMer = sMer;
        if (sMer == null && eMer != null) sMer = eMer;
        if (sMer == null) { sMer = "AM"; eMer = "AM"; }

        int start = toMinutes(sTime, sMer);
        int end = toMinutes(eTime, eMer);
        if (start >= end) {
            String flipped = sMer.equals("AM") ? "PM" : "AM";
            int alt = toMinutes(sTime, flipped);
            if (alt < end) start = alt;
        }
        return new TimeRange(start, end);
    }

    private static int toMinutes(String t, String mer) {
        String[] parts = t.split(":");
        int h = Integer.parseInt(parts[0]);
        int mm = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        if (mer.equals("AM")) { if (h == 12) h = 0; }
        else { if (h != 12) h += 12; }
        return h * 60 + mm;
    }
}
