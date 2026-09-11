package dev.marquinhhou.crsscheduler.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One entry in the profile card's "Organizations" list -- an org/club name plus an
 * optional role within it (e.g. "UP Computer Science Society" / "Vice President"). The
 * list itself is stored as a JSON array by SettingsStore.getProfileOrganizations, same
 * append/remove/save shape as AttachmentStore uses for its own JSON lists.
 */
public class Organization {

    public final String name;
    public final String role;

    public Organization(String name, String role) {
        this.name = name == null ? "" : name.trim();
        this.role = role == null ? "" : role.trim();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("role", role);
        return o;
    }

    public static Organization fromJson(JSONObject o) throws JSONException {
        return new Organization(o.optString("name", ""), o.optString("role", ""));
    }
}
