package dev.marquinhhou.crsscheduler.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** SharedPreferences: campus hint, semester dates, reminders, theme, profile, Form 5, terms gate. */
public final class SettingsStore {

    private static final String PREFS = "nothing_schedule_settings";
    private static final String KEY_CAMPUS = "campus_hint";
    private static final String KEY_MAPS_ENABLED = "maps_enabled";
    private static final String KEY_CAMPUS_AUTODETECT = "campus_autodetect_enabled";
    private static final String KEY_TERMS_ACCEPTED = "terms_accepted";
    private static final String KEY_SEMESTER_START = "semester_start_epoch_day";
    private static final String KEY_SEMESTER_END = "semester_end_epoch_day";
    private static final String KEY_LAST_AUTO_ARCHIVED_END = "semester_last_auto_archived_end_epoch_day";
    private static final String KEY_PREVIEW_BEFORE_START = "widget_preview_before_start";
    private static final String KEY_SHOW_TOMORROW = "widget_show_tomorrow";
    private static final String KEY_REMINDER_LEAD_MINUTES = "reminder_lead_minutes";
    private static final String KEY_REMINDER_REQUEST_CODES = "reminder_scheduled_request_codes";
    private static final String KEY_DEADLINE_REMINDER_REQUEST_CODES = "deadline_reminder_scheduled_request_codes";
    private static final String KEY_THEME_FAMILY = "theme_family";
    private static final String KEY_ONBOARDING_COMPLETE = "onboarding_complete";
    private static final String KEY_PROFILE_NAME = "profile_name";
    private static final String KEY_PROFILE_STUDENT_NO = "profile_student_no";
    private static final String KEY_PROFILE_COURSE = "profile_course";
    private static final String KEY_PROFILE_YEAR_STANDING = "profile_year_standing";
    private static final String KEY_FORM5_URI = "form5_uri";
    private static final String KEY_EXPORT_SHOW_NAME = "export_show_name";
    private static final String KEY_NOTES_TAP_EMPTY = "notes_tap_empty_to_create";
    private static final String KEY_EXPORT_SHOW_STUDENT_NO = "export_show_student_no";
    private static final String KEY_EXPORT_SHOW_COURSE = "export_show_course";
    private static final String KEY_EXPORT_SHOW_YEAR_STANDING = "export_show_year_standing";

    private SettingsStore() {}

    public static String getCampusHint(Context context) {
        return prefs(context).getString(KEY_CAMPUS, "");
    }

    public static void setCampusHint(Context context, String campus) {
        prefs(context).edit().putString(KEY_CAMPUS, campus).apply();
    }

    /** On by default -- existing users keep the "Open Maps" prompt until they turn it off. */
    public static boolean isMapsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_MAPS_ENABLED, true);
    }

    public static void setMapsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MAPS_ENABLED, enabled).apply();
    }

    /** On by default -- whether importing a CRS HTML file may auto-fill the campus hint. */
    public static boolean isCampusAutoDetectEnabled(Context context) {
        return prefs(context).getBoolean(KEY_CAMPUS_AUTODETECT, true);
    }

    public static void setCampusAutoDetectEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_CAMPUS_AUTODETECT, enabled).apply();
    }

    public static boolean hasAcceptedTerms(Context context) {
        return prefs(context).getBoolean(KEY_TERMS_ACCEPTED, false);
    }

    public static void setAcceptedTerms(Context context, boolean accepted) {
        prefs(context).edit().putBoolean(KEY_TERMS_ACCEPTED, accepted).apply();
    }

    /** The day the current schedule starts applying, or null if not set. */
    public static LocalDate getSemesterStart(Context context) {
        SharedPreferences p = prefs(context);
        return p.contains(KEY_SEMESTER_START) ? LocalDate.ofEpochDay(p.getLong(KEY_SEMESTER_START, 0)) : null;
    }

    /** Pass null to clear it. */
    public static void setSemesterStart(Context context, LocalDate date) {
        SharedPreferences.Editor e = prefs(context).edit();
        if (date == null) e.remove(KEY_SEMESTER_START); else e.putLong(KEY_SEMESTER_START, date.toEpochDay());
        e.apply();
    }

    /** The last day the current schedule applies, or null if it's ongoing / not set. */
    public static LocalDate getSemesterEnd(Context context) {
        SharedPreferences p = prefs(context);
        return p.contains(KEY_SEMESTER_END) ? LocalDate.ofEpochDay(p.getLong(KEY_SEMESTER_END, 0)) : null;
    }

    /** Pass null to clear it (meaning "no end date / still ongoing"). */
    public static void setSemesterEnd(Context context, LocalDate date) {
        SharedPreferences.Editor e = prefs(context).edit();
        if (date == null) e.remove(KEY_SEMESTER_END); else e.putLong(KEY_SEMESTER_END, date.toEpochDay());
        e.apply();
    }

    public enum SemesterPhase { NOT_SET, UPCOMING, ACTIVE, ENDED }

    /** Where "today" falls relative to the configured semester range. */
    public static SemesterPhase currentSemesterPhase(Context context) {
        LocalDate start = getSemesterStart(context);
        if (start == null) return SemesterPhase.NOT_SET;
        LocalDate today = LocalDate.now();
        if (today.isBefore(start)) return SemesterPhase.UPCOMING;
        LocalDate end = getSemesterEnd(context);
        if (end != null && today.isAfter(end)) return SemesterPhase.ENDED;
        return SemesterPhase.ACTIVE;
    }

    /** Same as currentSemesterPhase(), but UPCOMING folds into ACTIVE when preview-before-start is on. */
    public static SemesterPhase effectiveDisplayPhase(Context context) {
        SemesterPhase raw = currentSemesterPhase(context);
        if (raw == SemesterPhase.UPCOMING && isPreviewBeforeStartEnabled(context)) {
            return SemesterPhase.ACTIVE;
        }
        return raw;
    }

    public static boolean isPreviewBeforeStartEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PREVIEW_BEFORE_START, false);
    }

    public static void setPreviewBeforeStartEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PREVIEW_BEFORE_START, enabled).apply();
    }

    public static boolean isShowTomorrowEnabled(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_TOMORROW, false);
    }

    public static void setShowTomorrowEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SHOW_TOMORROW, enabled).apply();
    }

    public static LocalDate getLastAutoArchivedEnd(Context context) {
        SharedPreferences p = prefs(context);
        return p.contains(KEY_LAST_AUTO_ARCHIVED_END) ? LocalDate.ofEpochDay(p.getLong(KEY_LAST_AUTO_ARCHIVED_END, 0)) : null;
    }

    public static void setLastAutoArchivedEnd(Context context, LocalDate date) {
        SharedPreferences.Editor e = prefs(context).edit();
        if (date == null) e.remove(KEY_LAST_AUTO_ARCHIVED_END); else e.putLong(KEY_LAST_AUTO_ARCHIVED_END, date.toEpochDay());
        e.apply();
    }

    /** Minutes before each class to notify, or 0 for "off". */
    public static int getReminderLeadMinutes(Context context) {
        return prefs(context).getInt(KEY_REMINDER_LEAD_MINUTES, 0);
    }

    public static void setReminderLeadMinutes(Context context, int minutes) {
        prefs(context).edit().putInt(KEY_REMINDER_LEAD_MINUTES, minutes).apply();
    }

    /** Pending AlarmManager request codes, so ClassReminderScheduler can cancel exactly those. */
    public static List<Integer> getScheduledReminderRequestCodes(Context context) {
        return getIntListPref(context, KEY_REMINDER_REQUEST_CODES);
    }

    public static void setScheduledReminderRequestCodes(Context context, List<Integer> codes) {
        setIntListPref(context, KEY_REMINDER_REQUEST_CODES, codes);
    }

    /** Pending AlarmManager request codes, so DeadlineReminderScheduler can cancel exactly those. */
    public static List<Integer> getScheduledDeadlineReminderRequestCodes(Context context) {
        return getIntListPref(context, KEY_DEADLINE_REMINDER_REQUEST_CODES);
    }

    public static void setScheduledDeadlineReminderRequestCodes(Context context, List<Integer> codes) {
        setIntListPref(context, KEY_DEADLINE_REMINDER_REQUEST_CODES, codes);
    }

    private static List<Integer> getIntListPref(Context context, String key) {
        String json = prefs(context).getString(key, null);
        List<Integer> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) out.add(arr.getInt(i));
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return out;
    }

    private static void setIntListPref(Context context, String key, List<Integer> values) {
        JSONArray arr = new JSONArray();
        for (int v : values) arr.put(v);
        prefs(context).edit().putString(key, arr.toString()).apply();
    }

    /** GE: UP-maroon, follows system. NE: fixed dark red-on-black. ADAPTIVE: Material You (12+). */
    public enum ThemeFamily { GE, NE, ADAPTIVE }

    /** Falls back to GE if ADAPTIVE was saved on a device that no longer supports it (API &lt; 31). */
    public static ThemeFamily getThemeFamily(Context context) {
        String raw = prefs(context).getString(KEY_THEME_FAMILY, ThemeFamily.GE.name());
        ThemeFamily family;
        try {
            family = ThemeFamily.valueOf(raw);
        } catch (IllegalArgumentException e) {
            family = ThemeFamily.GE;
        }
        if (family == ThemeFamily.ADAPTIVE && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return ThemeFamily.GE;
        }
        return family;
    }

    public static void setThemeFamily(Context context, ThemeFamily family) {
        prefs(context).edit().putString(KEY_THEME_FAMILY, family.name()).apply();
    }

    /** Once true, Configure always opens in its normal (non-wizard) form. */
    public static boolean hasCompletedOnboarding(Context context) {
        return prefs(context).getBoolean(KEY_ONBOARDING_COMPLETE, false);
    }

    public static void setOnboardingComplete(Context context, boolean complete) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply();
    }

    // Profile -- optional, only ever shown on the exported schedule image, and only for
    // whichever fields their matching export-show toggle turns on (all off by default).

    public static String getProfileName(Context context) {
        return prefs(context).getString(KEY_PROFILE_NAME, "");
    }

    public static void setProfileName(Context context, String name) {
        prefs(context).edit().putString(KEY_PROFILE_NAME, name).apply();
    }

    public static String getProfileStudentNo(Context context) {
        return prefs(context).getString(KEY_PROFILE_STUDENT_NO, "");
    }

    public static void setProfileStudentNo(Context context, String studentNo) {
        prefs(context).edit().putString(KEY_PROFILE_STUDENT_NO, studentNo).apply();
    }

    public static String getProfileCourse(Context context) {
        return prefs(context).getString(KEY_PROFILE_COURSE, "");
    }

    public static void setProfileCourse(Context context, String course) {
        prefs(context).edit().putString(KEY_PROFILE_COURSE, course).apply();
    }

    public static String getProfileYearStanding(Context context) {
        return prefs(context).getString(KEY_PROFILE_YEAR_STANDING, "");
    }

    public static void setProfileYearStanding(Context context, String yearStanding) {
        prefs(context).edit().putString(KEY_PROFILE_YEAR_STANDING, yearStanding).apply();
    }

    public static boolean isExportShowNameEnabled(Context context) {
        return prefs(context).getBoolean(KEY_EXPORT_SHOW_NAME, false);
    }

    public static void setExportShowNameEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_EXPORT_SHOW_NAME, enabled).apply();
    }

    public static boolean isNotesTapEmptyToCreateEnabled(Context context) {
        return prefs(context).getBoolean(KEY_NOTES_TAP_EMPTY, false);
    }

    public static void setNotesTapEmptyToCreateEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_NOTES_TAP_EMPTY, enabled).apply();
    }

    public static boolean isExportShowStudentNoEnabled(Context context) {
        return prefs(context).getBoolean(KEY_EXPORT_SHOW_STUDENT_NO, false);
    }

    public static void setExportShowStudentNoEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_EXPORT_SHOW_STUDENT_NO, enabled).apply();
    }

    public static boolean isExportShowCourseEnabled(Context context) {
        return prefs(context).getBoolean(KEY_EXPORT_SHOW_COURSE, false);
    }

    public static void setExportShowCourseEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_EXPORT_SHOW_COURSE, enabled).apply();
    }

    public static boolean isExportShowYearStandingEnabled(Context context) {
        return prefs(context).getBoolean(KEY_EXPORT_SHOW_YEAR_STANDING, false);
    }

    public static void setExportShowYearStandingEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_EXPORT_SHOW_YEAR_STANDING, enabled).apply();
    }

    // Form 5 -- a picked PDF's content:// Uri, held via a persistable permission grant so it
    // survives app restarts/reboots (taken where the file is actually picked, ConfigureActivity).

    public static Uri getForm5Uri(Context context) {
        String raw = prefs(context).getString(KEY_FORM5_URI, null);
        return raw == null ? null : Uri.parse(raw);
    }

    /** Pass null to clear it. Doesn't itself release any persistable permission -- see Uri javadoc above. */
    public static void setForm5Uri(Context context, Uri uri) {
        SharedPreferences.Editor e = prefs(context).edit();
        if (uri == null) e.remove(KEY_FORM5_URI); else e.putString(KEY_FORM5_URI, uri.toString());
        e.apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
