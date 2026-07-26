package dev.marquinhhou.crsscheduler.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import dev.marquinhhou.crsscheduler.model.ClassSession;
import dev.marquinhhou.crsscheduler.model.ScheduleSnapshot;

/** Archive of past schedules so replacing/clearing the active one isn't a dead end. Newest first, capped at MAX_ENTRIES. */
public final class ScheduleHistoryStore {

    private static final String PREFS = "nothing_schedule_prefs";
    private static final String KEY_HISTORY = "crs_schedule_history_v1";
    private static final int MAX_ENTRIES = 20;

    private ScheduleHistoryStore() {}

    public static List<ScheduleSnapshot> loadAll(Context context) {
        SharedPreferences prefs = prefs(context);
        String json = prefs.getString(KEY_HISTORY, null);
        List<ScheduleSnapshot> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                out.add(ScheduleSnapshot.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return out;
    }

    /** Archives a schedule with an auto-generated "date · N classes · X.X units" label. */
    public static void archive(Context context, List<ClassSession> classes) {
        if (classes == null || classes.isEmpty()) return;

        double totalUnits = 0;
        for (ClassSession c : classes) if (!c.creditsExcluded) totalUnits += c.credits;
        String dateLabel = new SimpleDateFormat("MMM d, yyyy \u00B7 h:mm a", Locale.US)
                .format(Calendar.getInstance().getTime());
        String label = dateLabel + " \u2014 " + classes.size() + (classes.size() == 1 ? " class" : " classes")
                + " \u00B7 " + String.format(Locale.US, "%.1f", totalUnits) + " units";

        List<ScheduleSnapshot> all = loadAll(context);
        all.add(0, new ScheduleSnapshot(System.currentTimeMillis(), label, new ArrayList<>(classes)));
        while (all.size() > MAX_ENTRIES) {
            all.remove(all.size() - 1);
        }
        persist(context, all);
    }

    public static void delete(Context context, long id) {
        List<ScheduleSnapshot> all = loadAll(context);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id == id) {
                all.remove(i);
                break;
            }
        }
        persist(context, all);
    }

    /** Sets (or, if newName is null/blank, clears back to the auto-generated label) a saved schedule's display name. */
    public static void rename(Context context, long id, String newName) {
        String trimmed = newName == null ? null : newName.trim();
        List<ScheduleSnapshot> all = loadAll(context);
        for (int i = 0; i < all.size(); i++) {
            ScheduleSnapshot s = all.get(i);
            if (s.id == id) {
                all.set(i, new ScheduleSnapshot(s.id, s.label,
                        (trimmed == null || trimmed.isEmpty()) ? null : trimmed, s.classes));
                break;
            }
        }
        persist(context, all);
    }

    public static ScheduleSnapshot find(Context context, long id) {
        for (ScheduleSnapshot s : loadAll(context)) {
            if (s.id == id) return s;
        }
        return null;
    }

    private static void persist(Context context, List<ScheduleSnapshot> all) {
        JSONArray arr = new JSONArray();
        try {
            for (ScheduleSnapshot s : all) arr.put(s.toJson());
        } catch (JSONException e) {
            // Shouldn't happen; toJson() only fails on null fields we control.
        }
        prefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
