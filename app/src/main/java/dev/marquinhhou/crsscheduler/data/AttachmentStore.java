package dev.marquinhhou.crsscheduler.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import dev.marquinhhou.crsscheduler.model.Attachment;

/**
 * Locally-attached files, grouped under a caller-chosen namespace + key so unrelated
 * attachment lists (class syllabi vs. note attachments) never collide.
 *
 * Syllabi are keyed by ClassSession.code rather than held on ClassSession itself, since a
 * schedule reload/reparse replaces ClassSession instances wholesale (see ScheduleParser /
 * ConfigureActivity.applyEdit) -- only the stable `code` string survives a re-import, the
 * same way manual room/instructor edits are expected to.
 *
 * Actually taking the persistable URI permission grant (so it survives app restarts/reboots)
 * is the caller's responsibility at pick time, same convention as SettingsStore's Form 5 Uri.
 */
public final class AttachmentStore {

    private static final String PREFS = "nothing_schedule_attachments";
    public static final String NAMESPACE_SYLLABUS = "syllabus";
    public static final String NAMESPACE_NOTE = "note";

    private AttachmentStore() {}

    public static List<Attachment> get(Context context, String namespace, String key) {
        List<Attachment> out = new ArrayList<>();
        String json = prefs(context).getString(prefKey(namespace, key), null);
        if (json == null) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                out.add(Attachment.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            // Corrupt entry -- treat as empty rather than crash the dialog/widget that reads it.
        }
        return out;
    }

    public static void add(Context context, String namespace, String key, Attachment attachment) {
        List<Attachment> current = get(context, namespace, key);
        for (Attachment a : current) if (a.uri.equals(attachment.uri)) return; // already attached
        current.add(attachment);
        save(context, namespace, key, current);
    }

    public static void remove(Context context, String namespace, String key, String uri) {
        List<Attachment> current = get(context, namespace, key);
        List<Attachment> updated = new ArrayList<>();
        for (Attachment a : current) if (!a.uri.equals(uri)) updated.add(a);
        save(context, namespace, key, updated);
    }

    /** Called when a note is permanently deleted (not just archived) so its attachments don't leak. */
    public static void clear(Context context, String namespace, String key) {
        prefs(context).edit().remove(prefKey(namespace, key)).apply();
    }

    private static void save(Context context, String namespace, String key, List<Attachment> attachments) {
        JSONArray arr = new JSONArray();
        try {
            for (Attachment a : attachments) arr.put(a.toJson());
        } catch (JSONException ignored) {
            return;
        }
        prefs(context).edit().putString(prefKey(namespace, key), arr.toString()).apply();
    }

    private static String prefKey(String namespace, String key) {
        return namespace + ":" + key;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
