package dev.marquinhhou.crsscheduler.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.marquinhhou.crsscheduler.model.Note;

/** Persists notes and the Notes widget's view-mode state. Same prefs file as ScheduleStore. */
public final class NotesStore {

    private static final String PREFS = "nothing_schedule_prefs";
    private static final String KEY_NOTES = "crs_notes_v1";
    private static final String KEY_GROUPED_VIEW = "crs_notes_grouped_view";
    private static final String KEY_COLLAPSED_GROUPS = "crs_notes_collapsed_groups";

    private NotesStore() {}

    public static List<Note> load(Context context) {
        SharedPreferences prefs = prefs(context);
        String json = prefs.getString(KEY_NOTES, null);
        List<Note> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                out.add(Note.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return out;
    }

    /** Creates a note with an auto-assigned id. */
    public static Note create(Context context, String subjectCode, String subjectName, String title,
                               String body, Long deadlineEpochDay, Integer deadlineMinuteOfDay,
                               int reminderLeadMinutes, boolean urgent) {
        List<Note> all = load(context);
        long id = System.currentTimeMillis();
        while (findIndex(all, id) >= 0) id++; // guard against same-millisecond double taps
        Note note = new Note(id, subjectCode, subjectName, title, body, deadlineEpochDay,
                deadlineMinuteOfDay, reminderLeadMinutes, urgent, false, false, id);
        all.add(note);
        persist(context, all);
        return note;
    }

    /** Replaces a note by id. No-op if not found. */
    public static void update(Context context, Note edited) {
        List<Note> all = load(context);
        int idx = findIndex(all, edited.id);
        if (idx < 0) return;
        all.set(idx, edited);
        persist(context, all);
    }

    public static void toggleCompleted(Context context, long id) {
        List<Note> all = load(context);
        int idx = findIndex(all, id);
        if (idx < 0) return;
        all.set(idx, all.get(idx).withCompleted(!all.get(idx).completed));
        persist(context, all);
    }

    public static void setArchived(Context context, long id, boolean archived) {
        List<Note> all = load(context);
        int idx = findIndex(all, id);
        if (idx < 0) return;
        all.set(idx, all.get(idx).withArchived(archived));
        persist(context, all);
    }

    public static void delete(Context context, long id) {
        List<Note> all = load(context);
        int idx = findIndex(all, id);
        if (idx < 0) return;
        all.remove(idx);
        persist(context, all);
    }

    public static Note find(Context context, long id) {
        int idx = findIndex(load(context), id);
        return idx < 0 ? null : load(context).get(idx);
    }

    private static int findIndex(List<Note> all, long id) {
        for (int i = 0; i < all.size(); i++) if (all.get(i).id == id) return i;
        return -1;
    }

    private static void persist(Context context, List<Note> all) {
        JSONArray arr = new JSONArray();
        try {
            for (Note n : all) arr.put(n.toJson());
        } catch (JSONException e) {
            // Shouldn't happen.
        }
        prefs(context).edit().putString(KEY_NOTES, arr.toString()).apply();
    }

    /** Plain text of every active note, for copy-all. */
    public static String formatAllAsText(Context context) {
        List<Note> all = load(context);
        StringBuilder sb = new StringBuilder("CRS SCHEDULER NOTES\n\n");
        boolean any = false;
        for (Note n : all) {
            if (n.archived) continue;
            any = true;
            sb.append(n.toPlainText()).append("\n");
        }
        return any ? sb.toString().trim() : "";
    }

    // Widget view state: list vs. grouped, and which groups are collapsed.

    public static boolean isGroupedView(Context context) {
        return prefs(context).getBoolean(KEY_GROUPED_VIEW, false);
    }

    public static void setGroupedView(Context context, boolean grouped) {
        prefs(context).edit().putBoolean(KEY_GROUPED_VIEW, grouped).apply();
    }

    public static boolean isGroupCollapsed(Context context, String groupKey) {
        return prefs(context).getStringSet(KEY_COLLAPSED_GROUPS, new HashSet<>()).contains(groupKey);
    }

    public static void setGroupCollapsed(Context context, String groupKey, boolean collapsed) {
        SharedPreferences p = prefs(context);
        Set<String> current = new HashSet<>(p.getStringSet(KEY_COLLAPSED_GROUPS, new HashSet<>()));
        if (collapsed) current.add(groupKey); else current.remove(groupKey);
        // Must be a fresh Set, not the one getStringSet() returned.
        p.edit().putStringSet(KEY_COLLAPSED_GROUPS, current).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
