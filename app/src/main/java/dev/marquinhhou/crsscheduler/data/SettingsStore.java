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
    private static final String KEY_SIMPLE_SETUP_MODE = "simple_setup_mode";
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
    private static final String KEY_EXPORT_SHOW_FACEBOOK = "export_show_facebook";
    private static final String KEY_EXPORT_SHOW_INSTAGRAM = "export_show_instagram";
    private static final String KEY_EXPORT_SHOW_TWITTER = "export_show_twitter";
    private static final String KEY_EXPORT_SHOW_LINKEDIN = "export_show_linkedin";
    private static final String KEY_EXPORT_SHOW_WEBSITE = "export_show_website";
    private static final String KEY_EXPORT_SHOW_ADDRESS = "export_show_address";
    private static final String KEY_EXPORT_SHOW_MAIL = "export_show_mail";

    // v3.0.0 -- university affiliation. All additive keys: existing installs read their
    // defaults until onboarding/migration writes them.
    private static final String KEY_UNIVERSITY_TYPE = "university_type";
    private static final String KEY_UP_CAMPUS = "up_campus";
    private static final String KEY_UNIVERSITY_NAME = "manual_university_name";
    private static final String KEY_WELCOME_V3_SHOWN = "welcome_v3_shown";

    // Profile card -- additive fields beyond the export-image basics already stored above.
    private static final String KEY_PROFILE_PHOTO_PATH = "profile_photo_path";
    private static final String KEY_PROFILE_MAIL = "profile_mail";
    private static final String KEY_PROFILE_PHONE = "profile_phone";
    private static final String KEY_PROFILE_FACEBOOK = "profile_facebook";
    private static final String KEY_PROFILE_INSTAGRAM = "profile_instagram";
    private static final String KEY_PROFILE_TWITTER = "profile_twitter";
    private static final String KEY_PROFILE_LINKEDIN = "profile_linkedin";
    private static final String KEY_PROFILE_WEBSITE = "profile_website";
    private static final String KEY_PROFILE_ADDRESS = "profile_address";
    private static final String KEY_PROFILE_FACEBOOK_ENABLED = "profile_facebook_enabled";
    private static final String KEY_PROFILE_INSTAGRAM_ENABLED = "profile_instagram_enabled";
    private static final String KEY_PROFILE_TWITTER_ENABLED = "profile_twitter_enabled";
    private static final String KEY_PROFILE_LINKEDIN_ENABLED = "profile_linkedin_enabled";
    private static final String KEY_PROFILE_WEBSITE_ENABLED = "profile_website_enabled";
    private static final String KEY_PROFILE_ORGANIZATIONS = "profile_organizations";
    private static final String KEY_PROFILE_MAIL_ENABLED = "profile_mail_enabled";
    private static final String KEY_PROFILE_PHONE_ENABLED = "profile_phone_enabled";
    private static final String KEY_PROFILE_ADDRESS_ENABLED = "profile_address_enabled";
    private static final String KEY_PROFILE_STUDENTNO_ENABLED = "profile_studentno_enabled";
    private static final String KEY_PROFILE_COURSE_ENABLED = "profile_course_enabled";
    private static final String KEY_PROFILE_YEARSTANDING_ENABLED = "profile_yearstanding_enabled";
    private static final String KEY_PROFILE_ORGANIZATIONS_ENABLED = "profile_organizations_enabled";

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

    /**
     * GE, NE, ADAPTIVE are the original three; CUSTOM is new (photo or solid color, see
     * getCustomThemeMode/etc below). GE is retired as of the rebrand -- it's no longer
     * selectable and getThemeFamily() below silently migrates any existing GE user away from
     * it -- but the enum value has to stay so old saved prefs ("GE") still parse instead of
     * falling through the catch block below. Its resource files are unreferenced but left in
     * the repo; deleting them would mean touching every Theming.pick() call site across the
     * app with no way to compile-check the result, for a purely cosmetic cleanup.
     */
    public enum ThemeFamily { GE, NE, ADAPTIVE, CUSTOM }

    /**
     * Falls back to Adaptive (or Nothing, on API &lt; 31 where Adaptive isn't available) if
     * ADAPTIVE was saved on a device that no longer supports it, or if GE -- retired -- is
     * what's on file. The GE case also persists the migrated value so this only happens once
     * per install rather than resolving silently on every read.
     */
    public static ThemeFamily getThemeFamily(Context context) {
        boolean adaptiveOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        String raw = prefs(context).getString(KEY_THEME_FAMILY, adaptiveOk ? ThemeFamily.ADAPTIVE.name() : ThemeFamily.NE.name());
        ThemeFamily family;
        try {
            family = ThemeFamily.valueOf(raw);
        } catch (IllegalArgumentException e) {
            family = adaptiveOk ? ThemeFamily.ADAPTIVE : ThemeFamily.NE;
        }
        if (family == ThemeFamily.GE) {
            family = adaptiveOk ? ThemeFamily.ADAPTIVE : ThemeFamily.NE;
            setThemeFamily(context, family);
        } else if (family == ThemeFamily.ADAPTIVE && !adaptiveOk) {
            return ThemeFamily.NE;
        }
        return family;
    }

    public static void setThemeFamily(Context context, ThemeFamily family) {
        prefs(context).edit().putString(KEY_THEME_FAMILY, family.name()).apply();
    }

    public enum CustomThemeMode { PHOTO, COLOR }

    private static final String KEY_CUSTOM_MODE = "custom_theme_mode";
    private static final String KEY_CUSTOM_PHOTO_URI = "custom_theme_photo_uri";
    private static final String KEY_CUSTOM_PHOTO_OPACITY = "custom_theme_photo_opacity"; // 10-90
    private static final String KEY_CUSTOM_PHOTO_BLUR = "custom_theme_photo_blur"; // 0-25 (dp radius)
    private static final String KEY_CUSTOM_COLOR_PRIMARY = "custom_theme_color_primary";
    private static final String KEY_CUSTOM_COLOR_ACCENT = "custom_theme_color_accent"; // optional, 0 = unset

    public static CustomThemeMode getCustomThemeMode(Context context) {
        String raw = prefs(context).getString(KEY_CUSTOM_MODE, CustomThemeMode.COLOR.name());
        try {
            return CustomThemeMode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return CustomThemeMode.COLOR;
        }
    }

    public static void setCustomThemeMode(Context context, CustomThemeMode mode) {
        prefs(context).edit().putString(KEY_CUSTOM_MODE, mode.name()).apply();
    }

    /**
     * Null if no photo has been picked yet -- callers should fall back to Color mode's look.
     * This is an absolute path to a file in the app's own private storage (see
     * ConfigureActivity's copyPickedPhotoToLocalStorage), NOT an external content:// URI --
     * the whole photo-loading rebuild exists because depending on an external provider's URI
     * staying valid/permitted across the picker round-trip, dialog rebuilds, and app restarts
     * was the actual root cause of the repeated decode failures. Owning a local copy outright
     * removes that dependency entirely.
     */
    public static String getCustomPhotoPath(Context context) {
        return prefs(context).getString(KEY_CUSTOM_PHOTO_URI, null);
    }

    public static void setCustomPhotoPath(Context context, String absolutePath) {
        prefs(context).edit().putString(KEY_CUSTOM_PHOTO_URI, absolutePath).apply();
    }

    /** 10-90, clamped; default 55 (mid-range -- keeps content readable without the picker needing to be touched). */
    public static int getCustomPhotoOpacity(Context context) {
        int v = prefs(context).getInt(KEY_CUSTOM_PHOTO_OPACITY, 55);
        return Math.max(10, Math.min(90, v));
    }

    public static void setCustomPhotoOpacity(Context context, int opacity) {
        prefs(context).edit().putInt(KEY_CUSTOM_PHOTO_OPACITY, Math.max(10, Math.min(90, opacity))).apply();
    }

    /** 0-25dp blur radius, clamped; default 12. */
    public static int getCustomPhotoBlur(Context context) {
        int v = prefs(context).getInt(KEY_CUSTOM_PHOTO_BLUR, 12);
        return Math.max(0, Math.min(25, v));
    }

    public static void setCustomPhotoBlur(Context context, int blur) {
        prefs(context).edit().putInt(KEY_CUSTOM_PHOTO_BLUR, Math.max(0, Math.min(25, blur))).apply();
    }

    /** Defaults to the UP-maroon shade so a first-time Custom+Color user doesn't see black. */
    public static int getCustomColorPrimary(Context context) {
        return prefs(context).getInt(KEY_CUSTOM_COLOR_PRIMARY, 0xFF7A0019);
    }

    public static void setCustomColorPrimary(Context context, int argb) {
        prefs(context).edit().putInt(KEY_CUSTOM_COLOR_PRIMARY, argb).apply();
    }

    /** 0 = unset -- callers should derive/borrow an accent rather than render literal black. */
    public static int getCustomColorAccent(Context context) {
        return prefs(context).getInt(KEY_CUSTOM_COLOR_ACCENT, 0);
    }

    public static void setCustomColorAccent(Context context, int argb) {
        prefs(context).edit().putInt(KEY_CUSTOM_COLOR_ACCENT, argb).apply();
    }

    private static final String KEY_CUSTOM_PHOTO_DERIVED_PRIMARY = "custom_theme_photo_derived_primary";
    private static final String KEY_CUSTOM_PHOTO_DERIVED_ACCENT = "custom_theme_photo_derived_accent";

    /**
     * Extracted once from the photo itself when it's picked (see
     * CustomThemeBackground.extractDominantColors), so Photo mode's whole look -- background,
     * text, chips, everything -- derives from what's actually in the picture instead of
     * whatever Color mode's primary/accent happen to be set to. 0 = not yet extracted (no
     * photo has been picked).
     */
    public static int getCustomPhotoDerivedPrimary(Context context) {
        return prefs(context).getInt(KEY_CUSTOM_PHOTO_DERIVED_PRIMARY, 0);
    }

    public static void setCustomPhotoDerivedPrimary(Context context, int argb) {
        prefs(context).edit().putInt(KEY_CUSTOM_PHOTO_DERIVED_PRIMARY, argb).apply();
    }

    public static int getCustomPhotoDerivedAccent(Context context) {
        return prefs(context).getInt(KEY_CUSTOM_PHOTO_DERIVED_ACCENT, 0);
    }

    public static void setCustomPhotoDerivedAccent(Context context, int argb) {
        prefs(context).edit().putInt(KEY_CUSTOM_PHOTO_DERIVED_ACCENT, argb).apply();
    }

    /** Once true, Configure always opens in its normal (non-wizard) form. */
    public static boolean hasCompletedOnboarding(Context context) {
        return prefs(context).getBoolean(KEY_ONBOARDING_COMPLETE, false);
    }

    public static void setOnboardingComplete(Context context, boolean complete) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply();
    }

    /** Chosen on the wizard's first screen ("Simple" vs "Full" setup) -- Simple skips the
     * Profile & Extras step (Form5, Maps, Reminders, Theme, Notes, profile prompt) entirely,
     * going straight from Schedule to done. Read only during the wizard itself. */
    public static boolean isSimpleSetupMode(Context context) {
        return prefs(context).getBoolean(KEY_SIMPLE_SETUP_MODE, false);
    }

    public static void setSimpleSetupMode(Context context, boolean simple) {
        prefs(context).edit().putBoolean(KEY_SIMPLE_SETUP_MODE, simple).apply();
    }

    // Profile -- optional, only ever shown on the exported schedule image, and only for
    // whichever fields their matching export-show toggle turns on (all off by default).

    public static String getProfileName(Context context) {
        return prefs(context).getString(KEY_PROFILE_NAME, "");
    }

    public static void setProfileName(Context context, String name) {
        prefs(context).edit().putString(KEY_PROFILE_NAME, name).commit();
    }

    public static String getProfileStudentNo(Context context) {
        return prefs(context).getString(KEY_PROFILE_STUDENT_NO, "");
    }

    public static void setProfileStudentNo(Context context, String studentNo) {
        prefs(context).edit().putString(KEY_PROFILE_STUDENT_NO, studentNo).commit();
    }

    public static String getProfileCourse(Context context) {
        return prefs(context).getString(KEY_PROFILE_COURSE, "");
    }

    public static void setProfileCourse(Context context, String course) {
        prefs(context).edit().putString(KEY_PROFILE_COURSE, course).commit();
    }

    public static String getProfileYearStanding(Context context) {
        return prefs(context).getString(KEY_PROFILE_YEAR_STANDING, "");
    }

    public static void setProfileYearStanding(Context context, String yearStanding) {
        prefs(context).edit().putString(KEY_PROFILE_YEAR_STANDING, yearStanding).commit();
    }

    public static boolean isExportShowNameEnabled(Context context) {
        return prefs(context).getBoolean(KEY_EXPORT_SHOW_NAME, false);
    }

    public static void setExportShowNameEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_EXPORT_SHOW_NAME, enabled).apply();
    }

    public static boolean isExportShowMailEnabled(Context context) {
        return prefs(context).getBoolean(KEY_EXPORT_SHOW_MAIL, false);
    }

    public static void setExportShowMailEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_EXPORT_SHOW_MAIL, enabled).apply();
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

    public static boolean isExportShowFacebookEnabled(Context context) {
        return prefs(context).getBoolean(KEY_EXPORT_SHOW_FACEBOOK, false);
    }

    public static void setExportShowFacebookEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_EXPORT_SHOW_FACEBOOK, enabled).apply();
    }

    public static boolean isExportShowInstagramEnabled(Context context) {
        return prefs(context).getBoolean(KEY_EXPORT_SHOW_INSTAGRAM, false);
    }

    public static void setExportShowInstagramEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_EXPORT_SHOW_INSTAGRAM, enabled).apply();
    }

    public static boolean isExportShowTwitterEnabled(Context context) {
        return prefs(context).getBoolean(KEY_EXPORT_SHOW_TWITTER, false);
    }

    public static void setExportShowTwitterEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_EXPORT_SHOW_TWITTER, enabled).apply();
    }

    public static boolean isExportShowLinkedinEnabled(Context context) {
        return prefs(context).getBoolean(KEY_EXPORT_SHOW_LINKEDIN, false);
    }

    public static void setExportShowLinkedinEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_EXPORT_SHOW_LINKEDIN, enabled).apply();
    }

    public static boolean isExportShowWebsiteEnabled(Context context) {
        return prefs(context).getBoolean(KEY_EXPORT_SHOW_WEBSITE, false);
    }

    public static void setExportShowWebsiteEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_EXPORT_SHOW_WEBSITE, enabled).apply();
    }

    public static boolean isExportShowAddressEnabled(Context context) {
        return prefs(context).getBoolean(KEY_EXPORT_SHOW_ADDRESS, false);
    }

    public static void setExportShowAddressEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_EXPORT_SHOW_ADDRESS, enabled).apply();
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

    // ---- University affiliation (v3.0.0) ------------------------------------------------

    public enum UniversityType { UP, OTHER }

    /** The nine UP constituent universities. UPD is the flagship with the CRS parser; the rest get every other feature. */
    public enum UpCampus {
        UPD("UP Diliman", "Quezon City"),
        UPLB("UP Los Ba\u00f1os", "Laguna"),
        UPM("UP Manila", "Manila"),
        UPV("UP Visayas", "Miagao, Iloilo"),
        UPOU("UP Open University", "Los Ba\u00f1os, Laguna"),
        UPMIN("UP Mindanao", "Mintal, Davao City"),
        UPB("UP Baguio", "Baguio"),
        UPC("UP Cebu", "Cebu City"),
        UPTAC("UP Tacloban", "Tacloban, Leyte");

        public final String displayName;
        public final String location;
        UpCampus(String displayName, String location) {
            this.displayName = displayName;
            this.location = location;
        }
    }

    /** Null until onboarding/migration records an answer -- callers treat null as "not yet asked". */
    public static UniversityType getUniversityType(Context context) {
        String raw = prefs(context).getString(KEY_UNIVERSITY_TYPE, null);
        try {
            return raw == null ? null : UniversityType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void setUniversityType(Context context, UniversityType type) {
        prefs(context).edit().putString(KEY_UNIVERSITY_TYPE, type == null ? null : type.name()).apply();
    }

    /** Only meaningful when {@link #getUniversityType} == UP. Defaults to UPD for safety of parser gating. */
    public static UpCampus getUpCampus(Context context) {
        String raw = prefs(context).getString(KEY_UP_CAMPUS, UpCampus.UPD.name());
        try {
            return UpCampus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return UpCampus.UPD;
        }
    }

    public static void setUpCampus(Context context, UpCampus campus) {
        prefs(context).edit().putString(KEY_UP_CAMPUS, campus.name()).apply();
    }

    /** The user's own university name for non-UP affiliation; empty string when unset or when UP. */
    public static String getManualUniversityName(Context context) {
        return prefs(context).getString(KEY_UNIVERSITY_NAME, "");
    }

    public static void setManualUniversityName(Context context, String name) {
        prefs(context).edit().putString(KEY_UNIVERSITY_NAME, name == null ? "" : name.trim()).apply();
    }

    /**
     * The CRS page parser understands one system only: Diliman's CRS registration markup.
     * Gating it here means a non-UPD user simply never sees the parser UI -- manual entry,
     * .ics import, and everything else keep working unchanged.
     */
    public static boolean isCrsParserAvailable(Context context) {
        return getUniversityType(context) == UniversityType.UP
                && getUpCampus(context) == UpCampus.UPD;
    }

    /** True once this install has seen the v3 welcome screen (existing users included -- it shows exactly once). */
    public static boolean isWelcomeV3Shown(Context context) {
        return prefs(context).getBoolean(KEY_WELCOME_V3_SHOWN, false);
    }

    public static void setWelcomeV3Shown(Context context, boolean shown) {
        prefs(context).edit().putBoolean(KEY_WELCOME_V3_SHOWN, shown).apply();
    }

    // ---- Profile card fields (v3.0.0) ----------------------------------------------------

    /** Absolute path to the photo copied into app-private storage at pick time (same pattern as the theme photo). */
    public static String getProfilePhotoPath(Context context) {
        return prefs(context).getString(KEY_PROFILE_PHOTO_PATH, null);
    }

    public static void setProfilePhotoPath(Context context, String absolutePath) {
        prefs(context).edit().putString(KEY_PROFILE_PHOTO_PATH, absolutePath).apply();
    }

    /** For UP students: their UP Mail address. Free text otherwise. */
    public static String getProfileMail(Context context) {
        return prefs(context).getString(KEY_PROFILE_MAIL, "");
    }

    public static void setProfileMail(Context context, String mail) {
        prefs(context).edit().putString(KEY_PROFILE_MAIL, mail == null ? "" : mail.trim()).commit();
    }

    public static String getProfilePhone(Context context) {
        return prefs(context).getString(KEY_PROFILE_PHONE, "");
    }

    public static void setProfilePhone(Context context, String phone) {
        prefs(context).edit().putString(KEY_PROFILE_PHONE, phone == null ? "" : phone.trim()).commit();
    }

    public static String getProfileFacebook(Context context) {
        return prefs(context).getString(KEY_PROFILE_FACEBOOK, "");
    }

    public static void setProfileFacebook(Context context, String value) {
        prefs(context).edit().putString(KEY_PROFILE_FACEBOOK, value == null ? "" : value.trim()).commit();
    }

    public static String getProfileInstagram(Context context) {
        return prefs(context).getString(KEY_PROFILE_INSTAGRAM, "");
    }

    public static void setProfileInstagram(Context context, String value) {
        prefs(context).edit().putString(KEY_PROFILE_INSTAGRAM, value == null ? "" : value.trim()).commit();
    }

    public static String getProfileTwitter(Context context) {
        return prefs(context).getString(KEY_PROFILE_TWITTER, "");
    }

    public static void setProfileTwitter(Context context, String value) {
        prefs(context).edit().putString(KEY_PROFILE_TWITTER, value == null ? "" : value.trim()).commit();
    }

    public static String getProfileLinkedin(Context context) {
        return prefs(context).getString(KEY_PROFILE_LINKEDIN, "");
    }

    public static void setProfileLinkedin(Context context, String value) {
        prefs(context).edit().putString(KEY_PROFILE_LINKEDIN, value == null ? "" : value.trim()).commit();
    }

    public static String getProfileWebsite(Context context) {
        return prefs(context).getString(KEY_PROFILE_WEBSITE, "");
    }

    public static void setProfileWebsite(Context context, String value) {
        prefs(context).edit().putString(KEY_PROFILE_WEBSITE, value == null ? "" : value.trim()).commit();
    }

    public static String getProfileAddress(Context context) {
        return prefs(context).getString(KEY_PROFILE_ADDRESS, "");
    }

    public static void setProfileAddress(Context context, String value) {
        prefs(context).edit().putString(KEY_PROFILE_ADDRESS, value == null ? "" : value.trim()).commit();
    }

    /** Whether each link shows on the exported profile card -- one toggle per field, beside
     * its text box. All default OFF: with 5 possible links + 6 details + organizations all
     * defaulting on, a fresh profile immediately blew past both the link cap (3) and the
     * details cap (5) before the person had touched anything, greying out most of the
     * remaining toggles right out of the gate and making the card look overloaded by
     * default. Defaulting off makes every toggle a deliberate opt-in instead. */
    public static boolean getProfileFacebookEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PROFILE_FACEBOOK_ENABLED, false);
    }

    public static void setProfileFacebookEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PROFILE_FACEBOOK_ENABLED, enabled).apply();
    }

    public static boolean getProfileInstagramEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PROFILE_INSTAGRAM_ENABLED, false);
    }

    public static void setProfileInstagramEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PROFILE_INSTAGRAM_ENABLED, enabled).apply();
    }

    public static boolean getProfileTwitterEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PROFILE_TWITTER_ENABLED, false);
    }

    public static void setProfileTwitterEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PROFILE_TWITTER_ENABLED, enabled).apply();
    }

    public static boolean getProfileLinkedinEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PROFILE_LINKEDIN_ENABLED, false);
    }

    public static void setProfileLinkedinEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PROFILE_LINKEDIN_ENABLED, enabled).apply();
    }

    public static boolean getProfileWebsiteEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PROFILE_WEBSITE_ENABLED, false);
    }

    public static void setProfileWebsiteEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PROFILE_WEBSITE_ENABLED, enabled).apply();
    }

    /**
     * Organizations list -- repeatable org/role entries shown on the profile card, same
     * JSON-array-in-a-string shape as AttachmentStore's lists. Order is preserved (the
     * order the person added them in); no cap on count.
     */
    public static java.util.List<dev.marquinhhou.crsscheduler.model.Organization> getProfileOrganizations(Context context) {
        java.util.List<dev.marquinhhou.crsscheduler.model.Organization> out = new ArrayList<>();
        String json = prefs(context).getString(KEY_PROFILE_ORGANIZATIONS, null);
        if (json == null) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                out.add(dev.marquinhhou.crsscheduler.model.Organization.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            // Corrupt entry -- treat as empty rather than crash the profile editor.
        }
        return out;
    }

    public static void setProfileOrganizations(Context context, java.util.List<dev.marquinhhou.crsscheduler.model.Organization> organizations) {
        JSONArray arr = new JSONArray();
        try {
            for (dev.marquinhhou.crsscheduler.model.Organization o : organizations) arr.put(o.toJson());
        } catch (JSONException ignored) {
            return;
        }
        prefs(context).edit().putString(KEY_PROFILE_ORGANIZATIONS, arr.toString()).apply();
    }

    /** Whether each DETAILS field shows on the profile card -- one toggle per field, beside its
     * text box, same pattern as the link toggles. All default OFF, same reasoning as the link
     * toggles above -- everything defaulting on immediately exceeded the details cap (5 of 7
     * possible) before the person had touched anything. Full name has no toggle -- it's the
     * card's headline, not optional supplementary content. */
    public static boolean getProfileMailEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PROFILE_MAIL_ENABLED, false);
    }

    public static void setProfileMailEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PROFILE_MAIL_ENABLED, enabled).apply();
    }

    public static boolean getProfilePhoneEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PROFILE_PHONE_ENABLED, false);
    }

    public static void setProfilePhoneEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PROFILE_PHONE_ENABLED, enabled).apply();
    }

    public static boolean getProfileAddressEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PROFILE_ADDRESS_ENABLED, false);
    }

    public static void setProfileAddressEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PROFILE_ADDRESS_ENABLED, enabled).apply();
    }

    public static boolean getProfileStudentNoEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PROFILE_STUDENTNO_ENABLED, false);
    }

    public static void setProfileStudentNoEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PROFILE_STUDENTNO_ENABLED, enabled).apply();
    }

    public static boolean getProfileCourseEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PROFILE_COURSE_ENABLED, false);
    }

    public static void setProfileCourseEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PROFILE_COURSE_ENABLED, enabled).apply();
    }

    public static boolean getProfileYearStandingEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PROFILE_YEARSTANDING_ENABLED, false);
    }

    public static void setProfileYearStandingEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PROFILE_YEARSTANDING_ENABLED, enabled).apply();
    }

    /** Master toggle for the whole Organizations list -- one switch, not per-entry, since
     * entries are a homogeneous repeatable list rather than distinct named fields. Defaults
     * OFF, same reasoning as the toggles above. */
    public static boolean getProfileOrganizationsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PROFILE_ORGANIZATIONS_ENABLED, false);
    }

    public static void setProfileOrganizationsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PROFILE_ORGANIZATIONS_ENABLED, enabled).apply();
    }
}
