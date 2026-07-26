package dev.marquinhhou.crsscheduler.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import dev.marquinhhou.crsscheduler.model.ClassSession;

/**
 * Persists the parsed schedule on-device, equivalent to the web widget's
 * localStorage.setItem('crs_schedule_v1', ...) usage.
 */
public final class ScheduleStore {

    private static final String PREFS = "nothing_schedule_prefs";
    private static final String KEY_SCHEDULE = "crs_schedule_v1";

    private ScheduleStore() {}

    public static List<ClassSession> load(Context context) {
        SharedPreferences prefs = prefs(context);
        String json = prefs.getString(KEY_SCHEDULE, null);
        List<ClassSession> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                out.add(ClassSession.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return out;
    }

    public static void save(Context context, List<ClassSession> classes) {
        JSONArray arr = new JSONArray();
        try {
            for (ClassSession c : classes) arr.put(c.toJson());
        } catch (JSONException e) {
            // Shouldn't happen; toJson() only fails on null fields we control.
        }
        prefs(context).edit().putString(KEY_SCHEDULE, arr.toString()).apply();
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(KEY_SCHEDULE).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
