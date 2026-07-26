package dev.marquinhhou.crsscheduler.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A note tied to a subject (snapshot, not live) or "" for Miscellaneous. */
public class Note {

    public final long id;
    public final String subjectCode;   // "" for a Miscellaneous note
    public final String subjectName;   // "" for a Miscellaneous note
    public final String title;
    public final String body;          // "" if left blank
    public final Long deadlineEpochDay;     // null = no deadline
    public final Integer deadlineMinuteOfDay; // null = date only
    public final int reminderLeadMinutes; // 0 = off; minutes before the deadline to notify
    public final boolean urgent;       // a manual flag, independent of any deadline
    public final boolean completed;
    public final boolean archived;     // only valid once completed
    public final long createdAt;       // epoch millis, for stable ordering of no-deadline notes

    public Note(long id, String subjectCode, String subjectName, String title, String body,
                Long deadlineEpochDay, Integer deadlineMinuteOfDay, int reminderLeadMinutes,
                boolean urgent, boolean completed, boolean archived, long createdAt) {
        this.id = id;
        this.subjectCode = subjectCode == null ? "" : subjectCode;
        this.subjectName = subjectName == null ? "" : subjectName;
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.deadlineEpochDay = deadlineEpochDay;
        // Time and reminder both need a date to mean anything.
        this.deadlineMinuteOfDay = deadlineEpochDay == null ? null : deadlineMinuteOfDay;
        this.reminderLeadMinutes = deadlineEpochDay == null ? 0 : reminderLeadMinutes;
        this.urgent = urgent;
        this.completed = completed;
        this.archived = archived && completed;
        this.createdAt = createdAt;
    }

    public boolean isMisc() {
        return subjectCode.isEmpty();
    }

    /** Returns a copy with the given fields edited; id/createdAt never change. */
    public Note withEdits(String newSubjectCode, String newSubjectName, String newTitle,
                           String newBody, Long newDeadlineEpochDay, Integer newDeadlineMinuteOfDay,
                           int newReminderLeadMinutes, boolean newUrgent, boolean newCompleted) {
        // Un-completing pulls it out of the archive too.
        boolean stillArchived = archived && newCompleted;
        return new Note(id, newSubjectCode, newSubjectName, newTitle, newBody,
                newDeadlineEpochDay, newDeadlineMinuteOfDay, newReminderLeadMinutes, newUrgent, newCompleted, stillArchived, createdAt);
    }

    public Note withCompleted(boolean newCompleted) {
        return new Note(id, subjectCode, subjectName, title, body, deadlineEpochDay, deadlineMinuteOfDay,
                reminderLeadMinutes, urgent, newCompleted, archived && newCompleted, createdAt);
    }

    public Note withArchived(boolean newArchived) {
        return new Note(id, subjectCode, subjectName, title, body, deadlineEpochDay, deadlineMinuteOfDay,
                reminderLeadMinutes, urgent, completed, newArchived, createdAt);
    }

    /** Plain-text rendering for the clipboard -- one note's worth. */
    public String toPlainText() {
        StringBuilder sb = new StringBuilder();
        sb.append(isMisc() ? "[MISCELLANEOUS]" : "[" + subjectName + "]");
        if (urgent) sb.append(" [URGENT]");
        sb.append("\n");
        sb.append(completed ? "[DONE] " : "").append(title).append("\n");
        if (deadlineEpochDay != null) {
            String dateStr = LocalDate.ofEpochDay(deadlineEpochDay)
                    .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US));
            if (deadlineMinuteOfDay != null) {
                LocalTime t = LocalTime.of(deadlineMinuteOfDay / 60, deadlineMinuteOfDay % 60);
                dateStr += " at " + t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US));
            }
            sb.append("Due: ").append(dateStr).append("\n");
        }
        if (!body.trim().isEmpty()) {
            sb.append(body.trim()).append("\n");
        }
        return sb.toString();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("subjectCode", subjectCode);
        o.put("subjectName", subjectName);
        o.put("title", title);
        o.put("body", body);
        if (deadlineEpochDay != null) o.put("deadlineEpochDay", (long) deadlineEpochDay);
        if (deadlineMinuteOfDay != null) o.put("deadlineMinuteOfDay", (int) deadlineMinuteOfDay);
        if (reminderLeadMinutes > 0) o.put("reminderLeadMinutes", reminderLeadMinutes);
        o.put("urgent", urgent);
        o.put("completed", completed);
        o.put("archived", archived);
        o.put("createdAt", createdAt);
        return o;
    }

    public static Note fromJson(JSONObject o) throws JSONException {
        Long deadline = o.has("deadlineEpochDay") ? o.getLong("deadlineEpochDay") : null;
        Integer minuteOfDay = o.has("deadlineMinuteOfDay") ? o.getInt("deadlineMinuteOfDay") : null;
        return new Note(
                o.getLong("id"),
                o.optString("subjectCode", ""),
                o.optString("subjectName", ""),
                o.optString("title", ""),
                o.optString("body", ""),
                deadline,
                minuteOfDay,
                o.optInt("reminderLeadMinutes", 0),
                o.optBoolean("urgent", false),
                o.optBoolean("completed", false),
                o.optBoolean("archived", false),
                o.optLong("createdAt", o.getLong("id"))
        );
    }
}
