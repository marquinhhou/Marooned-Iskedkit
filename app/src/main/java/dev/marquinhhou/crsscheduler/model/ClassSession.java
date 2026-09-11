package dev.marquinhhou.crsscheduler.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One enlisted class/session. Room/instructor/units correctable via withEditedInfo(). */
public class ClassSession {

    public final String code;
    public final String name;
    public final double credits;
    public final boolean creditsExcluded;
    public final List<Integer> days; // 0=Sun .. 6=Sat, can contain multiple (e.g. T/Th)
    public final int start; // minutes from midnight
    public final int end;   // minutes from midnight
    public final String type; // lec / lab / rec / disc / pe
    public final String room;
    public final String instructor;

    public ClassSession(String code, String name, double credits, boolean creditsExcluded,
                         List<Integer> days, int start, int end,
                         String type, String room, String instructor) {
        this.code = code;
        this.name = name;
        this.credits = credits;
        this.creditsExcluded = creditsExcluded;
        this.days = days;
        this.start = start;
        this.end = end;
        this.type = type;
        this.room = room;
        this.instructor = instructor;
    }

    /** Returns a copy with the room/instructor/units manually edited via Edit Class Info. */
    public ClassSession withEditedInfo(String newRoom, String newInstructor, double newCredits) {
        return withEditedInfo(newRoom, newInstructor, newCredits, days, start, end);
    }

    /** Same as above, but also allows correcting the days and start/end time. */
    public ClassSession withEditedInfo(String newRoom, String newInstructor, double newCredits,
                                        List<Integer> newDays, int newStart, int newEnd) {
        return new ClassSession(code, name, newCredits, creditsExcluded, newDays, newStart, newEnd,
                type, newRoom, newInstructor);
    }

    /** The room text to display: falls back to "TBA" when blank. */
    public String displayRoom() {
        return (room == null || room.trim().isEmpty()) ? "TBA" : room;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("code", code);
        o.put("name", name);
        o.put("credits", credits);
        o.put("creditsExcluded", creditsExcluded);
        JSONArray daysArr = new JSONArray();
        for (int d : days) daysArr.put(d);
        o.put("days", daysArr);
        o.put("start", start);
        o.put("end", end);
        o.put("type", type);
        o.put("room", room);
        o.put("instructor", instructor);
        return o;
    }

    public static ClassSession fromJson(JSONObject o) throws JSONException {
        List<Integer> days = new ArrayList<>();
        JSONArray daysArr = o.getJSONArray("days");
        for (int i = 0; i < daysArr.length(); i++) days.add(daysArr.getInt(i));
        return new ClassSession(
                o.getString("code"),
                o.getString("name"),
                o.getDouble("credits"),
                o.optBoolean("creditsExcluded", false),
                days,
                o.getInt("start"),
                o.getInt("end"),
                o.optString("type", ""),
                o.optString("room", ""),
                o.optString("instructor", "")
        );
    }
}
