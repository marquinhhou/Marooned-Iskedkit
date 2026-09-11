package dev.marquinhhou.crsscheduler.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A single locally-attached file: a persisted-permission content:// Uri plus the display
 * name shown to the user (the picker's Uri alone isn't human-readable). Used for both
 * class syllabi (keyed by class code, see AttachmentStore) and per-note attachments.
 */
public class Attachment {

    public final String uri;
    public final String name;

    public Attachment(String uri, String name) {
        this.uri = uri;
        this.name = name == null || name.trim().isEmpty() ? "Attachment" : name;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("uri", uri);
        o.put("name", name);
        return o;
    }

    public static Attachment fromJson(JSONObject o) throws JSONException {
        return new Attachment(o.getString("uri"), o.optString("name", "Attachment"));
    }
}
