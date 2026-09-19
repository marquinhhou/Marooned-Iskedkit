package dev.marquinhhou.crsscheduler.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.NotesStore;
import dev.marquinhhou.crsscheduler.data.ScheduleStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.model.ClassSession;
import dev.marquinhhou.crsscheduler.model.Note;
import dev.marquinhhou.crsscheduler.ui.ArchivedNotesActivity;
import dev.marquinhhou.crsscheduler.ui.ConfigureActivity;
import dev.marquinhhou.crsscheduler.ui.CustomThemeBackground;
import dev.marquinhhou.crsscheduler.ui.NoteEditActivity;
import dev.marquinhhou.crsscheduler.ui.Theming;
import dev.marquinhhou.crsscheduler.ui.WeekScheduleActivity;
import dev.marquinhhou.crsscheduler.ui.WidgetActionActivity;
import dev.marquinhhou.crsscheduler.ui.WidgetForm5PromptActivity;
import dev.marquinhhou.crsscheduler.ui.WidgetSaveActivity;

/** Builds RemoteViews for all widgets. resolveThemeAssets() picks the _ge/_ne/_adaptive set per build. */
public final class WidgetRenderer {

    /**
     * Last-resort fallback when building the real widget throws. Deliberately uses only a
     * system layout/resource id (not one of ours) so this path can't itself fail for the same
     * reason the real one did -- an uncaught exception here is exactly what makes a widget host
     * show its own "Couldn't add widget." placeholder instead of any content at all.
     */
    public static RemoteViews buildMinimalErrorWidget(Context context, String message) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
        rv.setTextViewText(android.R.id.text1, message);
        return rv;
    }

    public static final String[] DAY_LABELS = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
    public static final int[] WEEK_ORDER = {1, 2, 3, 4, 5, 6, 0}; // Mon..Sun

    /**
     * Every public entry point below that calls resolveThemeAssets() must run its ENTIRE body
     * (from that call through to the RemoteViews it returns) while holding this lock.
     *
     * resolveThemeAssets() populates the fields just below into class-level statics rather than
     * returning them, and every render method reads those statics by bare name for the rest of
     * its body. That's only safe if exactly one thread is ever inside "resolveThemeAssets() ...
     * read the fields it set" at a time. It isn't: RemoteViewsFactory callbacks (getViewAt() for
     * every visible row) arrive over Binder, and the framework is free to service them from
     * several pool threads at once, and separate widget instances/providers can refresh
     * concurrently too. Two interleaved calls -- e.g. one for a GE-themed row and one for a
     * Custom-themed row landing back to back -- can read a mix of each other's field writes:
     * a drawable resolved for one theme tinted with a color resolved for another, or a stale
     * layout id. The result isn't a clean wrong-but-consistent row; it's whatever partially-
     * overwritten int happened to be sitting in the field at read time, which is exactly the
     * "corrupted-looking" icon/row reports (seen under both Custom Photo and Custom Color, and
     * only on some devices/thread-pool timings -- never reproducible on demand). Wrapping every
     * entry point in this single lock serializes them, which is all resolveThemeAssets() ever
     * actually assumed.
     */
    private static final Object RENDER_LOCK = new Object();

    // Resolved once per build call by resolveThemeAssets() -- see class comment. Only ever
    // read/written while holding RENDER_LOCK (see its Javadoc above).
    private static int accent, ink, inkDim, bg, lineStrong;
    private static boolean useDotRing;
    private static int layoutToday, layoutTodayCompact, layoutWeek, layoutWeekExpanded;
    private static int layoutRowClass, layoutRowWeekDot, layoutRowWeekTable;
    private static int layoutCellTime, layoutCellDayLabel, layoutCellClass, layoutRowMoreIndicator;
    private static int layoutNotesWidget, layoutRowNote;
    private static int drawableCardBg, drawableCardBgNow, drawableChipFilledBg, drawableChipOutlineBg;
    private static int drawableRowBg, drawableRowBgNow, drawableDotAccent, drawableDotDim, drawableDotHasClass;
    private static int drawableDotCheckedNote, drawableIcArchive, drawableIcCopy, drawableIcChevronDown, drawableIcChevronRight;
    private static int drawableIcSettings, drawableIcSave, drawableIcCalendar, drawableIcForm5;
    private static int colorGreen, colorError;

    private static final int TODAY_BASE_HEIGHT_DP = 190;

    // Launcher-reported height is a rounded estimate, not exact -- pixel-matching row count
    // to it caused rows to get cropped/faded even with real room. Now the list only needs
    // space for its header; native ListView scrolling handles the rest. Slack below covers
    // boundary-adjacent sizes; only too-small-for-the-header falls back to compact.
    private static final int SIZE_ESTIMATE_SLACK_DP = 16;
    private static final int WEEK_BASE_HEIGHT_DP = 132;
    private static final int WEEK_ROW_HEIGHT_DP = 19;

    // Extra vertical room the compact view's day-dots row needs on top of WEEK_BASE_HEIGHT_DP
    // (header + summary card). At the smallest widget grid sizes there isn't room for it --
    // rather than let it render half-clipped by the OS, buildWeekCompact() skips it entirely.
    //
    // Unlike SIZE_ESTIMATE_SLACK_DP above (which leans toward showing content, since a cropped
    // list row is still readable), this case leans the other way: a day dot clipped down to a
    // couple of dp doesn't fade gracefully, it flattens into what reads as a stray dash. So on
    // top of the row's own footprint, WEEK_DOTS_ROW_MARGIN_DP pads the requirement further to
    // absorb the same launcher-rounding slop, erring toward hiding the row over showing it broken.
    private static final int WEEK_DOTS_ROW_HEIGHT_DP = 24;
    private static final int WEEK_DOTS_ROW_MARGIN_DP = 16;

    // No more "+N more" truncation -- the table always shows every occupied slot and
    // shrinks to fit, down to this floor, below which it falls back to buildWeekCompact().
    private static final float WEEK_MIN_ROW_SCALE = 0.8f;
    private static final int WEEK_MIN_ROW_HEIGHT_DP = Math.round(WEEK_ROW_HEIGHT_DP * WEEK_MIN_ROW_SCALE);

    private WidgetRenderer() {}

    private static void resolveThemeAssets(Context context) {
        accent = Theming.color(context, R.color.ge_accent, R.color.ne_accent, R.color.adaptive_accent);
        ink = Theming.color(context, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink);
        inkDim = Theming.color(context, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim);
        bg = Theming.color(context, R.color.ge_bg, R.color.ne_bg, R.color.adaptive_bg);
        lineStrong = Theming.color(context, R.color.ge_line_strong, R.color.ne_line_strong, R.color.adaptive_line_strong);
        useDotRing = Theming.usesDotRing(context);

        layoutToday = Theming.pick(context, R.layout.today_widget_ge, R.layout.today_widget_ne, R.layout.today_widget_adaptive);
        layoutTodayCompact = Theming.pick(context, R.layout.today_widget_compact_ge, R.layout.today_widget_compact_ne, R.layout.today_widget_compact_adaptive);
        layoutWeek = Theming.pick(context, R.layout.week_widget_ge, R.layout.week_widget_ne, R.layout.week_widget_adaptive);
        layoutWeekExpanded = Theming.pick(context, R.layout.week_widget_expanded_ge, R.layout.week_widget_expanded_ne, R.layout.week_widget_expanded_adaptive);
        layoutRowClass = Theming.pick(context, R.layout.row_class_ge, R.layout.row_class_ne, R.layout.row_class_adaptive);
        layoutRowWeekDot = Theming.pick(context, R.layout.row_week_dot_ge, R.layout.row_week_dot_ne, R.layout.row_week_dot_adaptive);
        layoutRowWeekTable = Theming.pick(context, R.layout.row_week_table_ge, R.layout.row_week_table_ne, R.layout.row_week_table_adaptive);
        layoutCellTime = Theming.pick(context, R.layout.cell_week_table_time_ge, R.layout.cell_week_table_time_ne, R.layout.cell_week_table_time_adaptive);
        layoutCellDayLabel = Theming.pick(context, R.layout.cell_week_table_daylabel_ge, R.layout.cell_week_table_daylabel_ne, R.layout.cell_week_table_daylabel_adaptive);
        layoutCellClass = Theming.pick(context, R.layout.cell_week_table_class_ge, R.layout.cell_week_table_class_ne, R.layout.cell_week_table_class_adaptive);
        layoutRowMoreIndicator = Theming.pick(context, R.layout.row_more_indicator_ge, R.layout.row_more_indicator_ne, R.layout.row_more_indicator_adaptive);
        layoutNotesWidget = Theming.pick(context, R.layout.notes_widget_ge, R.layout.notes_widget_ne, R.layout.notes_widget_adaptive);
        layoutRowNote = Theming.pick(context, R.layout.row_note_ge, R.layout.row_note_ne, R.layout.row_note_adaptive);

        drawableCardBg = Theming.pick(context, R.drawable.card_bg_ge, R.drawable.card_bg_ne, R.drawable.card_bg_adaptive);
        drawableCardBgNow = Theming.pick(context, R.drawable.card_bg_now_ge, R.drawable.card_bg_now_ne, R.drawable.card_bg_now_adaptive);
        drawableChipFilledBg = Theming.pick(context, R.drawable.chip_filled_bg_ge, R.drawable.chip_filled_bg_ne, R.drawable.chip_filled_bg_adaptive);
        drawableChipOutlineBg = Theming.pick(context, R.drawable.chip_outline_bg_ge, R.drawable.chip_outline_bg_ne, R.drawable.chip_outline_bg_adaptive);
        drawableRowBg = Theming.pick(context, R.drawable.row_bg_ge, R.drawable.row_bg_ne, R.drawable.row_bg_adaptive);
        drawableRowBgNow = Theming.pick(context, R.drawable.row_bg_now_ge, R.drawable.row_bg_now_ne, R.drawable.row_bg_now_adaptive);
        drawableDotAccent = Theming.pick(context, R.drawable.dot_accent_ge, R.drawable.dot_accent_ne, R.drawable.dot_accent_adaptive);
        drawableDotDim = Theming.pick(context, R.drawable.dot_dim_ge, R.drawable.dot_dim_ne, R.drawable.dot_dim_adaptive);
        drawableDotHasClass = Theming.pick(context, R.drawable.dot_has_class_ge, R.drawable.dot_has_class_ne, R.drawable.dot_has_class_adaptive);
        drawableDotCheckedNote = Theming.pick(context, R.drawable.ic_check_ge, R.drawable.ic_check_ne, R.drawable.ic_check_adaptive);
        drawableIcArchive = Theming.pick(context, R.drawable.ic_archive_ge, R.drawable.ic_archive_ne, R.drawable.ic_archive_adaptive);
        drawableIcCopy = Theming.pick(context, R.drawable.ic_copy_ge, R.drawable.ic_copy_ne, R.drawable.ic_copy_adaptive);
        drawableIcChevronDown = Theming.pick(context, R.drawable.ic_chevron_down_ge, R.drawable.ic_chevron_down_ne, R.drawable.ic_chevron_down_adaptive);
        drawableIcChevronRight = Theming.pick(context, R.drawable.ic_chevron_right_ge, R.drawable.ic_chevron_right_ne, R.drawable.ic_chevron_right_adaptive);
        // Previously never resolved at all -- btn_settings/btn_save/the Weekly calendar icon
        // were left at their static @drawable/ic_*_adaptive asset color, unlike archive/copy
        // right next to them, which already went through setIconMaybeCustom.
        drawableIcSettings = Theming.pick(context, R.drawable.ic_settings_ge, R.drawable.ic_settings_ne, R.drawable.ic_settings_adaptive);
        drawableIcSave = Theming.pick(context, R.drawable.ic_save_ge, R.drawable.ic_save_ne, R.drawable.ic_save_adaptive);
        // F5's header action is now the document icon at the same 16dp size as every other
        // header icon (it was an undersized boxed "F5" text chip -- visually inconsistent
        // with gear/save/calendar right next to it).
        drawableIcForm5 = Theming.pick(context, R.drawable.ic_form5_ge, R.drawable.ic_form5_ne, R.drawable.ic_form5_adaptive);
        drawableIcCalendar = Theming.pick(context, R.drawable.ic_calendar_ge, R.drawable.ic_calendar_ne, R.drawable.ic_calendar_adaptive);
        colorGreen = Theming.color(context, R.color.ge_green, R.color.ne_green, R.color.adaptive_green);
        colorError = Theming.color(context, R.color.ge_error, R.color.ne_error, R.color.adaptive_error);
    }

    /** So TodayClassesRemoteViewsService's out-of-bounds fallback row picks the right theme too. */
    public static int rowMoreIndicatorLayout(Context context) {
        synchronized (RENDER_LOCK) {
            resolveThemeAssets(context);
            return layoutRowMoreIndicator;
        }
    }

    // Today widget

    public static RemoteViews buildToday(Context context, Bundle options, int appWidgetId) {
        synchronized (RENDER_LOCK) {
            return buildTodayLocked(context, options, appWidgetId);
        }
    }

    /** Always invoked while holding {@link #RENDER_LOCK} -- see its Javadoc. */
    private static RemoteViews buildTodayLocked(Context context, Bundle options, int appWidgetId) {
        resolveThemeAssets(context);
        List<ClassSession> schedule = ScheduleStore.load(context);
        SettingsStore.SemesterPhase rawPhase = SettingsStore.currentSemesterPhase(context);
        SettingsStore.SemesterPhase phase = SettingsStore.effectiveDisplayPhase(context);
        int today = calendarDayToJs(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
        List<ClassSession> todays = new ArrayList<>();
        if (phase != SettingsStore.SemesterPhase.UPCOMING && phase != SettingsStore.SemesterPhase.ENDED) {
            for (ClassSession c : schedule) if (c.days.contains(today)) todays.add(c);
            todays.sort((a, b) -> Integer.compare(a.start, b.start));
        }

        int availableDp = currentPortraitHeightDp(options);
        boolean roomForList = availableDp == Integer.MAX_VALUE
                || availableDp + SIZE_ESTIMATE_SLACK_DP >= TODAY_BASE_HEIGHT_DP;

        return roomForList
                ? buildTodayFull(context, schedule, todays, appWidgetId, phase, rawPhase, options)
                : buildTodayCompact(context, schedule, todays, phase, rawPhase, options);
    }

    private static RemoteViews buildTodayFull(Context context, List<ClassSession> schedule,
                                               List<ClassSession> todays, int appWidgetId,
                                               SettingsStore.SemesterPhase phase, SettingsStore.SemesterPhase rawPhase,
                                               Bundle options) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), layoutToday);
        bindGear(context, rv);
        bindClock(context, rv);
        bindViewFullSchedule(context, rv, true); // full-size Today carries the labeled text chip
        bindSaveButton(context, rv);
        bindForm5Button(context, rv);
        bindHeaderBrandLabel(context, rv, TodayWidgetProvider.class,
                TodayWidgetProvider.ACTION_TOGGLE_PREVIEW, "TODAY", rawPhase, 5);

        Calendar now = Calendar.getInstance();
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int today = calendarDayToJs(now.get(Calendar.DAY_OF_WEEK));

        ClassSession ongoing = null;
        for (ClassSession c : todays) {
            if (nowMin >= c.start && nowMin < c.end) { ongoing = c; break; }
        }
        ClassSession next = null;
        if (ongoing == null) {
            for (ClassSession c : todays) if (c.start > nowMin) { next = c; break; }
        }

        boolean outOfSession = phase == SettingsStore.SemesterPhase.UPCOMING || phase == SettingsStore.SemesterPhase.ENDED;
        if (ongoing != null) {
            float progress = (nowMin - ongoing.start) / (float) (ongoing.end - ongoing.start);
            showHero(context, rv, "NOW", ongoing, progress, ongoing.end, "%s left", true, true);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, todayMinuteToEpochMillis(ongoing.end));
        } else if (next != null) {
            // A class already finished earlier today (as opposed to this being before the
            // day's first class) -- call this out as free time rather than just "next", so
            // the hero card doesn't read like a class is about to start out of nowhere.
            boolean hadEarlierClassToday = false;
            for (ClassSession c : todays) if (c.end <= nowMin) { hadEarlierClassToday = true; break; }
            String label = hadEarlierClassToday ? "FREE TIME" : "NEXT";
            int gapWindow = 240;
            float progress = 1f - Math.min(1f, (next.start - nowMin) / (float) gapWindow);
            showHero(context, rv, label, next, progress, next.start, "in %s", false, true);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, todayMinuteToEpochMillis(next.start));
        } else if (phase == SettingsStore.SemesterPhase.UPCOMING) {
            LocalDate start = SettingsStore.getSemesterStart(context);
            showEmptyHero(context, rv, "Semester hasn't started yet.",
                    "Classes begin " + formatSemesterDate(start) + daysSuffix(daysUntil(start)) + ". Tap to preview anyway.", true);
            bindToggleTap(context, rv, R.id.hero_empty_sub, TodayWidgetProvider.class,
                    TodayWidgetProvider.ACTION_TOGGLE_PREVIEW, 6);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        } else if (phase == SettingsStore.SemesterPhase.ENDED) {
            LocalDate end = SettingsStore.getSemesterEnd(context);
            showEmptyHero(context, rv, "Semester has ended.",
                    "Classes ran through " + formatSemesterDate(end) + ".", true);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        } else if (!schedule.isEmpty()) {
            showEmptyHero(context, rv, "Day's clear from here.", "Nothing left on today's schedule.", true);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        } else {
            showEmptyHero(context, rv, "No schedule loaded.", "Tap the gear to load your CRS classes.", true);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        }

        boolean showTomorrow = SettingsStore.isShowTomorrowEnabled(context);
        rv.setTextViewText(R.id.today_list_title,
                (!outOfSession && !schedule.isEmpty())
                        ? (showTomorrow ? "TOMORROW'S CLASSES" : "TODAY'S CLASSES") : "");
        // The section label kept its layout-default Adaptive color otherwise -- a
        // wallpaper-derived warm gray that never tracked Custom's ink tone, reading
        // inconsistent with every other text on the widget.
        rv.setInt(R.id.today_list_title, "setTextColor", inkDim);
        rv.setTextViewText(R.id.today_list_empty, todayListEmptyText(schedule, today, showTomorrow, outOfSession));
        // Empty-state copy rides directly on the widget background -- its layout default is a
        // static family color that never tracked Custom theming (invisible on photos).
        rv.setInt(R.id.today_list_empty, "setTextColor", inkDim);
        bindTomorrowToggle(context, rv, showTomorrow);

        bindTodayListAdapter(context, rv, appWidgetId);

        applyCustomWidgetBackground(context, rv, options);
        return rv;
    }

    /** "No classes today/tomorrow", appending "next class in N" if the week has more classes. */
    private static String todayListEmptyText(List<ClassSession> schedule, int today, boolean showTomorrow, boolean outOfSession) {
        String base = showTomorrow ? "No classes tomorrow" : "No classes today";
        if (outOfSession || schedule.isEmpty()) return base + ".";

        int targetDay = showTomorrow ? (today + 1) % 7 : today;
        boolean targetDayHasClasses = false;
        for (ClassSession c : schedule) if (c.days.contains(targetDay)) { targetDayHasClasses = true; break; }
        if (targetDayHasClasses) return base + ".";

        int daysAfterTarget = daysUntilNextClass(schedule, targetDay);
        if (daysAfterTarget <= 0) return base + ".";

        int daysFromToday = (showTomorrow ? 1 : 0) + daysAfterTarget;
        return base + ", next class in " + daysFromToday + (daysFromToday == 1 ? " day" : " days");
    }

    /** First offset (1..7) after {@code fromDayIdx} (exclusive) whose weekday has a class, or -1 if none all week. */
    private static int daysUntilNextClass(List<ClassSession> schedule, int fromDayIdx) {
        for (int offset = 1; offset <= 7; offset++) {
            int dayIdx = (fromDayIdx + offset) % 7;
            for (ClassSession c : schedule) if (c.days.contains(dayIdx)) return offset;
        }
        return -1;
    }

    /** Header + hero card only -- used when there's no room for even a single class row. */
    private static RemoteViews buildTodayCompact(Context context, List<ClassSession> schedule,
                                                  List<ClassSession> todays, SettingsStore.SemesterPhase phase,
                                                  SettingsStore.SemesterPhase rawPhase, Bundle options) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), layoutTodayCompact);
        bindGear(context, rv);
        bindClock(context, rv);
        bindViewFullSchedule(context, rv, false);
        bindSaveButton(context, rv);
        bindForm5Button(context, rv);
        bindHeaderBrandLabel(context, rv, TodayWidgetProvider.class,
                TodayWidgetProvider.ACTION_TOGGLE_PREVIEW, "TODAY", rawPhase, 5);

        Calendar now = Calendar.getInstance();
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        ClassSession ongoing = null;
        for (ClassSession c : todays) {
            if (nowMin >= c.start && nowMin < c.end) { ongoing = c; break; }
        }
        ClassSession next = null;
        if (ongoing == null) {
            for (ClassSession c : todays) if (c.start > nowMin) { next = c; break; }
        }

        if (ongoing != null) {
            float progress = (nowMin - ongoing.start) / (float) (ongoing.end - ongoing.start);
            showHero(context, rv, "NOW", ongoing, progress, 0, "", true, false);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, todayMinuteToEpochMillis(ongoing.end));
        } else if (next != null) {
            boolean hadEarlierClassToday = false;
            for (ClassSession c : todays) if (c.end <= nowMin) { hadEarlierClassToday = true; break; }
            String label = hadEarlierClassToday ? "FREE TIME" : "NEXT";
            int gapWindow = 240;
            float progress = 1f - Math.min(1f, (next.start - nowMin) / (float) gapWindow);
            showHero(context, rv, label, next, progress, 0, "", false, false);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, todayMinuteToEpochMillis(next.start));
        } else if (phase == SettingsStore.SemesterPhase.UPCOMING) {
            long days = daysUntil(SettingsStore.getSemesterStart(context));
            showEmptyHero(context, rv, days > 0 ? "Semester in " + days + (days == 1 ? " day" : " days")
                    : "Semester hasn't started yet.", "", false);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        } else if (phase == SettingsStore.SemesterPhase.ENDED) {
            showEmptyHero(context, rv, "Semester has ended.", "", false);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        } else if (!schedule.isEmpty()) {
            showEmptyHero(context, rv, "Day's clear from here.", "", false);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        } else {
            showEmptyHero(context, rv, "No schedule loaded.", "", false);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        }

        // The compact layout's bottom fade is a gradient into the family's own bg color --
        // over a Custom photo/color background it reads as a stray colored band, so it's
        // only shown for the families whose background it actually matches.
        rv.setViewVisibility(R.id.fade_bottom,
                Theming.family(context) == SettingsStore.ThemeFamily.CUSTOM ? View.GONE : View.VISIBLE);

        applyCustomWidgetBackground(context, rv, options);
        return rv;
    }

    private static void showHero(Context context, RemoteViews rv, String label, ClassSession c, float progress,
                                  int countdownTargetMinute, String countdownFormat,
                                  boolean isNow, boolean includeCountdown) {
        rv.setViewVisibility(R.id.hero_content, View.VISIBLE);
        rv.setViewVisibility(R.id.hero_empty, View.GONE);
        android.graphics.Bitmap ring = useDotRing
                ? RingBitmapFactory.buildDotRing(progress, 180, accent)
                : RingBitmapFactory.buildArcRing(progress, 180, accent, lineStrong);
        rv.setImageViewBitmap(R.id.hero_ring, ring);
        rv.setTextViewText(R.id.hero_label, label);
        rv.setInt(R.id.hero_label, "setTextColor", inkDim);
        rv.setTextViewText(R.id.hero_class, c.name);
        rv.setInt(R.id.hero_class, "setTextColor", ink);
        rv.setTextViewText(R.id.hero_meta, c.displayRoom() + " \u00B7 " + minToLabel(c.start) + "\u2013" + minToLabel(c.end));
        rv.setInt(R.id.hero_meta, "setTextColor", inkDim);
        if (includeCountdown) {
            long targetEpochMillis = todayMinuteToEpochMillis(countdownTargetMinute);
            long base = SystemClock.elapsedRealtime() + (targetEpochMillis - System.currentTimeMillis());
            rv.setChronometer(R.id.hero_countdown, base, countdownFormat, true);
            rv.setChronometerCountDown(R.id.hero_countdown, true);
            rv.setInt(R.id.hero_countdown, "setTextColor", ink);
        }
        rv.setInt(R.id.hero_card, "setBackgroundResource", isNow ? drawableCardBgNow : drawableCardBg);
        applyCustomRowSurface(context, rv, R.id.hero_card, isNow);
    }

    private static long todayMinuteToEpochMillis(int minuteOfDay) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        cal.set(Calendar.MINUTE, minuteOfDay % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static void showEmptyHero(Context context, RemoteViews rv, String title, String sub, boolean includeSub) {
        rv.setViewVisibility(R.id.hero_content, View.GONE);
        rv.setViewVisibility(R.id.hero_empty, View.VISIBLE);
        rv.setTextViewText(R.id.hero_empty_title, title);
        rv.setInt(R.id.hero_empty_title, "setTextColor", ink);
        if (includeSub) {
            rv.setTextViewText(R.id.hero_empty_sub, sub);
            rv.setInt(R.id.hero_empty_sub, "setTextColor", inkDim);
        }
        rv.setInt(R.id.hero_card, "setBackgroundResource", drawableCardBg);
        applyCustomRowSurface(context, rv, R.id.hero_card);
    }

    // Week widget

    public static RemoteViews buildWeekSummary(Context context, Bundle options) {
        synchronized (RENDER_LOCK) {
            return buildWeekSummaryLocked(context, options);
        }
    }

    /** Always invoked while holding {@link #RENDER_LOCK} -- see its Javadoc. */
    private static RemoteViews buildWeekSummaryLocked(Context context, Bundle options) {
        resolveThemeAssets(context);
        List<ClassSession> schedule = ScheduleStore.load(context);
        SettingsStore.SemesterPhase rawPhase = SettingsStore.currentSemesterPhase(context);
        SettingsStore.SemesterPhase phase = SettingsStore.effectiveDisplayPhase(context);
        if (phase == SettingsStore.SemesterPhase.UPCOMING || phase == SettingsStore.SemesterPhase.ENDED) {
            return buildWeekCompact(context, schedule, phase, rawPhase, options);
        }

        List<Integer> breakpoints = schedule.isEmpty() ? new ArrayList<>() : weekBreakpoints(schedule);
        List<Integer> activeDays = schedule.isEmpty() ? new ArrayList<>() : activeWeekDays(schedule);
        List<Integer> occupiedRows = schedule.isEmpty()
                ? new ArrayList<>() : occupiedRowIndices(schedule, breakpoints, activeDays);
        int totalGridRows = occupiedRows.size();

        if (schedule.isEmpty() || totalGridRows == 0) {
            return buildWeekCompact(context, schedule, phase, rawPhase, options);
        }

        int availableDp = currentPortraitHeightDp(options);
        if (availableDp != Integer.MAX_VALUE) {
            int availableForRows = availableDp - WEEK_BASE_HEIGHT_DP;
            if (availableForRows < totalGridRows * WEEK_MIN_ROW_HEIGHT_DP) {
                // Even the smallest legible row size can't fit every time slot -- rather than
                // clip or fade rows, fall back to the compact summary view.
                return buildWeekCompact(context, schedule, phase, rawPhase, options);
            }
        }

        float scale = computeWeekRowScale(totalGridRows, availableDp);
        return buildWeekExpanded(context, schedule, breakpoints, activeDays, occupiedRows, scale, rawPhase, options);
    }

    /** All rows always shown -- shrinks text/padding/height to fit instead of truncating. */
    private static float computeWeekRowScale(int totalRows, int availableDp) {
        if (totalRows <= 0 || availableDp == Integer.MAX_VALUE) return 1f;
        int availableForRows = availableDp - WEEK_BASE_HEIGHT_DP;
        float targetRowHeightDp = (float) availableForRows / totalRows;
        float scale = targetRowHeightDp / WEEK_ROW_HEIGHT_DP;
        return Math.max(WEEK_MIN_ROW_SCALE, Math.min(1f, scale));
    }

    /** Compact "headline + next class + week dots" summary -- used when the grid won't fit, or the semester isn't in session. */
    private static RemoteViews buildWeekCompact(Context context, List<ClassSession> schedule,
                                                 SettingsStore.SemesterPhase phase, SettingsStore.SemesterPhase rawPhase,
                                                 Bundle options) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), layoutWeek);
        bindGear(context, rv);
        bindClock(context, rv);
        bindViewFullSchedule(context, rv, false);
        bindSaveButton(context, rv);
        bindForm5Button(context, rv);
        bindHeaderBrandLabel(context, rv, WeekWidgetProvider.class,
                WeekWidgetProvider.ACTION_TOGGLE_PREVIEW, "WEEKLY", rawPhase, 5);

        Calendar now = Calendar.getInstance();
        int today = calendarDayToJs(now.get(Calendar.DAY_OF_WEEK));
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        if (phase == SettingsStore.SemesterPhase.UPCOMING) {
            LocalDate start = SettingsStore.getSemesterStart(context);
            rv.setTextViewText(R.id.summary_headline, "Semester hasn't started yet.");
            rv.setTextViewText(R.id.summary_sub,
                    "Classes begin " + formatSemesterDate(start) + daysSuffix(daysUntil(start)) + ". Tap to preview anyway.");
            rv.setTextViewText(R.id.summary_next, "");
            bindToggleTap(context, rv, R.id.summary_sub, WeekWidgetProvider.class,
                    WeekWidgetProvider.ACTION_TOGGLE_PREVIEW, 6);
        } else if (phase == SettingsStore.SemesterPhase.ENDED) {
            rv.setTextViewText(R.id.summary_headline, "Semester has ended.");
            rv.setTextViewText(R.id.summary_sub,
                    "Classes ran through " + formatSemesterDate(SettingsStore.getSemesterEnd(context)) + ".");
            rv.setTextViewText(R.id.summary_next, "");
        } else if (schedule.isEmpty()) {
            rv.setTextViewText(R.id.summary_headline, "No schedule loaded");
            rv.setTextViewText(R.id.summary_sub, "Tap the gear to load your CRS classes.");
            rv.setTextViewText(R.id.summary_next, "");
        } else {
            double totalUnits = 0;
            int classCount = 0;
            for (ClassSession c : schedule) {
                if (!c.creditsExcluded) totalUnits += c.credits;
                classCount++;
            }
            long daysToEnd = daysUntil(SettingsStore.getSemesterEnd(context));
            rv.setTextViewText(R.id.summary_headline,
                    classCount + (classCount == 1 ? " class" : " classes")
                            + " \u00B7 " + String.format(Locale.US, "%.1f", totalUnits) + " units"
                            + (daysToEnd > 0 ? " \u00B7 " + daysToEnd + "d left" : ""));

            ClassSession upcoming = null;
            int upcomingDay = -1;
            outer:
            for (int offset = 0; offset < 7; offset++) {
                int dayIdx = (today + offset) % 7;
                List<ClassSession> dayItems = new ArrayList<>();
                for (ClassSession c : schedule) if (c.days.contains(dayIdx)) dayItems.add(c);
                dayItems.sort((a, b) -> Integer.compare(a.start, b.start));
                for (ClassSession c : dayItems) {
                    if (offset == 0 && c.start <= nowMin && nowMin < c.end) {
                        upcoming = c; upcomingDay = dayIdx; break outer;
                    }
                    if (offset > 0 || c.start > nowMin) {
                        upcoming = c; upcomingDay = dayIdx; break outer;
                    }
                }
            }
            if (upcoming != null) {
                String when = upcomingDay == today ? "Today" : DAY_LABELS[upcomingDay];
                rv.setTextViewText(R.id.summary_sub, "Next: " + upcoming.name);
                rv.setTextViewText(R.id.summary_next,
                        when + " \u00B7 " + minToLabel(upcoming.start) + " \u00B7 " + upcoming.displayRoom());
            } else {
                rv.setTextViewText(R.id.summary_sub, "Nothing else scheduled this week.");
                rv.setTextViewText(R.id.summary_next, "");
            }
        }

        rv.removeAllViews(R.id.week_dots_row);
        // summary_headline/sub/next are set across several branches above depending on semester
        // phase/schedule state -- coloring once here after all of them, rather than repeating
        // the same setInt at every branch, covers every path with no risk of missing one.
        rv.setInt(R.id.summary_headline, "setTextColor", ink);
        rv.setInt(R.id.summary_sub, "setTextColor", inkDim);
        rv.setInt(R.id.summary_next, "setTextColor", inkDim);
        // The summary card kept Adaptive's own opaque surface in Custom mode -- the one big
        // card on this widget was exactly what read as "not frosted." Same treatment as Today's
        // hero card gets.
        applyCustomRowSurface(context, rv, R.id.summary_card);
        int availableDp = currentPortraitHeightDp(options);
        boolean roomForDots = availableDp == Integer.MAX_VALUE
                || availableDp - WEEK_BASE_HEIGHT_DP >= WEEK_DOTS_ROW_HEIGHT_DP + WEEK_DOTS_ROW_MARGIN_DP;
        if (roomForDots) {
            for (int dayIdx : WEEK_ORDER) {
                boolean hasClasses = false;
                for (ClassSession c : schedule) if (c.days.contains(dayIdx)) { hasClasses = true; break; }
                RemoteViews dot = new RemoteViews(context.getPackageName(), layoutRowWeekDot);
                dot.setTextViewText(R.id.week_dot_label, DAY_LABELS[dayIdx].substring(0, 1));
                dot.setInt(R.id.week_dot_label, "setTextColor", dayIdx == today ? ink : inkDim);
                if (dayIdx == today) {
                    setIconMaybeCustom(context, dot, R.id.week_dot, drawableDotAccent, IconTint.ACCENT);
                } else if (hasClasses) {
                    setIconMaybeCustom(context, dot, R.id.week_dot, drawableDotHasClass, IconTint.INK_DIM);
                } else {
                    setIconMaybeCustom(context, dot, R.id.week_dot, drawableDotDim, IconTint.LINE);
                }
                rv.addView(R.id.week_dots_row, dot);
            }
        }

        applyCustomWidgetBackground(context, rv, options);
        return rv;
    }

    /** CRS-style time/day grid. Always shows every occupied time slot -- row size adapts (see buildWeekTable). */
    private static RemoteViews buildWeekExpanded(Context context, List<ClassSession> schedule,
                                                  List<Integer> breakpoints, List<Integer> activeDays,
                                                  List<Integer> occupiedRows, float scale,
                                                  SettingsStore.SemesterPhase rawPhase, Bundle options) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), layoutWeekExpanded);
        bindGear(context, rv);
        bindClock(context, rv);
        bindViewFullSchedule(context, rv, false);
        bindSaveButton(context, rv);
        bindForm5Button(context, rv);
        bindHeaderBrandLabel(context, rv, WeekWidgetProvider.class,
                WeekWidgetProvider.ACTION_TOGGLE_PREVIEW, "WEEKLY", rawPhase, 5);

        double totalUnits = 0;
        for (ClassSession c : schedule) if (!c.creditsExcluded) totalUnits += c.credits;
        rv.setTextViewText(R.id.table_headline,
                schedule.size() + (schedule.size() == 1 ? " class" : " classes")
                        + " \u00B7 " + String.format(Locale.US, "%.1f", totalUnits) + " units");
        rv.setInt(R.id.table_headline, "setTextColor", ink);

        buildWeekTable(context, rv, schedule, breakpoints, activeDays, occupiedRows, scale);

        String noClassDays = noClassDaysLabel(activeDays);
        if (noClassDays.isEmpty()) {
            rv.setViewVisibility(R.id.no_class_days_note, View.GONE);
        } else {
            rv.setViewVisibility(R.id.no_class_days_note, View.VISIBLE);
            rv.setTextViewText(R.id.no_class_days_note, noClassDays);
            rv.setInt(R.id.no_class_days_note, "setTextColor", inkDim);
        }

        // The table's own container panel: in Custom mode it stayed at Adaptive's fixed opaque
        // row_bg drawable -- the schedule grid was a flat solid box sitting on the frosted
        // widget background. Frosted surface here, same as every other widget card.
        applyCustomRowSurface(context, rv, R.id.week_table_container);

        applyCustomWidgetBackground(context, rv, options);
        return rv;
    }

    /** Every occupied row, no truncation; shrinks cell size/padding when scale < 1f. */
    private static void buildWeekTable(Context context, RemoteViews rv, List<ClassSession> schedule,
                                        List<Integer> breakpoints, List<Integer> activeDays,
                                        List<Integer> occupiedRows, float scale) {
        rv.removeAllViews(R.id.week_table_container);

        Calendar now = Calendar.getInstance();
        int today = calendarDayToJs(now.get(Calendar.DAY_OF_WEEK));
        float density = context.getResources().getDisplayMetrics().density;

        RemoteViews headerRow = new RemoteViews(context.getPackageName(), layoutRowWeekTable);
        RemoteViews corner = new RemoteViews(context.getPackageName(), layoutCellTime);
        corner.setTextViewText(R.id.cell_time_text, "");
        // The layouts bake their own family's ink_dim in as a static XML color; setting it
        // here from the resolved palette is what makes this cell track Custom theming (and
        // keeps every family consistent even if a layout's default drifts).
        corner.setInt(R.id.cell_time_text, "setTextColor", inkDim);
        scaleTimeCell(corner, scale, density);
        headerRow.addView(R.id.table_row, corner);
        for (int dayIdx : activeDays) {
            RemoteViews dayCell = new RemoteViews(context.getPackageName(), layoutCellDayLabel);
            dayCell.setTextViewText(R.id.cell_daylabel_text, DAY_LABELS[dayIdx].substring(0, 1));
            // Neutral ink only -- table text never takes the theme accent.
            dayCell.setInt(R.id.cell_daylabel_text, "setTextColor", dayIdx == today ? ink : inkDim);
            scaleDayLabelCell(dayCell, scale, density);
            headerRow.addView(R.id.table_row, dayCell);
        }
        rv.addView(R.id.week_table_container, headerRow);

        for (int r : occupiedRows) {
            int slotStart = breakpoints.get(r);
            int slotEnd = breakpoints.get(r + 1);

            RemoteViews row = new RemoteViews(context.getPackageName(), layoutRowWeekTable);
            RemoteViews timeCell = new RemoteViews(context.getPackageName(), layoutCellTime);
            timeCell.setTextViewText(R.id.cell_time_text, compactRangeLabel(slotStart, slotEnd));
            timeCell.setInt(R.id.cell_time_text, "setTextColor", inkDim);
            scaleTimeCell(timeCell, scale, density);
            row.addView(R.id.table_row, timeCell);

            for (int dayIdx : activeDays) {
                ClassSession match = findClassInSlot(schedule, dayIdx, slotStart, slotEnd);
                RemoteViews classCell = new RemoteViews(context.getPackageName(), layoutCellClass);
                if (match != null) {
                    boolean isTodayColumn = dayIdx == today;
                    classCell.setViewVisibility(R.id.cell_class_text, View.VISIBLE);
                    classCell.setViewVisibility(R.id.cell_dot_image, View.GONE);
                    classCell.setTextViewText(R.id.cell_class_text, abbreviateName(match.name));
                    // No tile/pill behind the subject text -- the grid reads cleaner with
                    // bare labels (the old white chips turned every occupied cell into a
                    // bright blob). Today's column still stands out through its full-ink
                    // label; other days sit at the dim ink.
                    classCell.setInt(R.id.cell_class_text, "setBackgroundResource", R.drawable.bg_transparent);
                    classCell.setInt(R.id.cell_class_text, "setTextColor", isTodayColumn ? ink : inkDim);
                } else {
                    classCell.setViewVisibility(R.id.cell_class_text, View.GONE);
                    classCell.setViewVisibility(R.id.cell_dot_image, View.VISIBLE);
                    // Previously never tinted at all -- stayed at its static @drawable/dot_dim_*
                    // asset color regardless of theme, unlike the day-header dots right above it.
                    setIconMaybeCustom(context, classCell, R.id.cell_dot_image, drawableDotDim, IconTint.LINE);
                }
                scaleClassCell(classCell, scale, density);
                row.addView(R.id.table_row, classCell);
            }
            rv.addView(R.id.week_table_container, row);
        }
    }

    private static int dpToPx(float density, float dp) {
        return Math.round(dp * density);
    }

    private static void scaleTimeCell(RemoteViews cell, float scale, float density) {
        cell.setTextViewTextSize(R.id.cell_time_text, TypedValue.COMPLEX_UNIT_SP, 8f * scale);
        int vPad = dpToPx(density, 3f * scale);
        cell.setViewPadding(R.id.cell_time_text, 0, vPad, 0, vPad);
    }

    private static void scaleDayLabelCell(RemoteViews cell, float scale, float density) {
        cell.setTextViewTextSize(R.id.cell_daylabel_text, TypedValue.COMPLEX_UNIT_SP, 9f * scale);
        cell.setViewPadding(R.id.cell_daylabel_text, 0, 0, 0, dpToPx(density, 4f * scale));
    }

    private static void scaleClassCell(RemoteViews cell, float scale, float density) {
        cell.setTextViewTextSize(R.id.cell_class_text, TypedValue.COMPLEX_UNIT_SP, 7.5f * scale);
        // Bare labels (no pill) -- the padding is just breathing room around the
        // abbreviation and scales with the row so dense tables stay legible.
        int vPad = dpToPx(density, 2f * scale);
        int hPad = dpToPx(density, 3.5f * scale);
        cell.setViewPadding(R.id.cell_class_text, hPad, vPad, hPad, vPad);
        cell.setInt(R.id.cell_class_frame, "setMinimumHeight", dpToPx(density, 18f * scale));
    }

    public static List<Integer> weekBreakpoints(List<ClassSession> schedule) {
        TreeSet<Integer> set = new TreeSet<>();
        for (ClassSession c : schedule) {
            set.add(c.start);
            set.add(c.end);
        }
        return new ArrayList<>(set);
    }

    public static List<Integer> activeWeekDays(List<ClassSession> schedule) {
        List<Integer> result = new ArrayList<>();
        for (int dayIdx : WEEK_ORDER) {
            boolean hasClasses = false;
            for (ClassSession c : schedule) if (c.days.contains(dayIdx)) { hasClasses = true; break; }
            if (hasClasses) result.add(dayIdx);
        }
        return result;
    }

    public static List<Integer> occupiedRowIndices(List<ClassSession> schedule, List<Integer> breakpoints,
                                                    List<Integer> days) {
        List<Integer> result = new ArrayList<>();
        int totalRows = Math.max(0, breakpoints.size() - 1);
        for (int r = 0; r < totalRows; r++) {
            int slotStart = breakpoints.get(r);
            int slotEnd = breakpoints.get(r + 1);
            for (int dayIdx : days) {
                if (findClassInSlot(schedule, dayIdx, slotStart, slotEnd) != null) {
                    result.add(r);
                    break;
                }
            }
        }
        return result;
    }

    public static String noClassDaysLabel(List<Integer> activeDays) {
        StringBuilder sb = new StringBuilder();
        for (int dayIdx : WEEK_ORDER) {
            if (activeDays.contains(dayIdx)) continue;
            String label = DAY_LABELS[dayIdx];
            String titleCase = label.charAt(0) + label.substring(1).toLowerCase(Locale.US);
            if (sb.length() > 0) sb.append(", ");
            sb.append(titleCase);
        }
        return sb.length() == 0 ? "" : "No classes on: " + sb;
    }

    public static ClassSession findClassInSlot(List<ClassSession> schedule, int dayIdx, int slotStart, int slotEnd) {
        for (ClassSession c : schedule) {
            if (!c.days.contains(dayIdx)) continue;
            if (c.start <= slotStart && slotEnd <= c.end) return c;
        }
        return null;
    }

    /** "Philo 1 THV-3" -> "PHILO 1". Public so ScheduleImageExporter renders cells identically. */
    public static String abbreviateName(String name) {
        if (name == null) return "";
        String[] tokens = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, tokens.length); i++) {
            if (i > 0) sb.append(' ');
            sb.append(tokens[i].toUpperCase(Locale.US));
        }
        return sb.toString();
    }

    // Shared pieces

    /** Called from TodayClassesRemoteViewsService.Factory.getViewAt(); public for that reason. */
    public static RemoteViews buildClassRowForAdapter(Context context, ClassSession c, boolean isNow, int index, boolean isLast) {
        synchronized (RENDER_LOCK) {
            return buildClassRowForAdapterLocked(context, c, isNow, index, isLast);
        }
    }

    /** Always invoked while holding {@link #RENDER_LOCK} -- see its Javadoc. */
    private static RemoteViews buildClassRowForAdapterLocked(Context context, ClassSession c, boolean isNow, int index, boolean isLast) {
        resolveThemeAssets(context);
        RemoteViews row = new RemoteViews(context.getPackageName(), layoutRowClass);
        // Always set explicitly -- recycled rows keep a stale padding otherwise (the squished-row bug).
        int bottomPaddingDp = isLast ? 0 : 4;
        int bottomPaddingPx = Math.round(bottomPaddingDp * context.getResources().getDisplayMetrics().density);
        row.setViewPadding(R.id.row_class_root, 0, 0, 0, bottomPaddingPx);
        row.setTextViewText(R.id.row_time, minToLabel(c.start) + "\n" + minToLabel(c.end));
        row.setTextViewText(R.id.row_name, c.name);
        row.setInt(R.id.row_name, "setTextColor", ink);
        String meta = c.displayRoom() + (c.instructor != null && !c.instructor.isEmpty() ? " \u00B7 " + c.instructor : "");
        row.setTextViewText(R.id.row_room, meta);
        row.setInt(R.id.row_room, "setTextColor", inkDim);
        row.setInt(R.id.row_root, "setBackgroundResource", isNow ? drawableRowBgNow : drawableRowBg);
        applyCustomRowSurface(context, row, R.id.row_root, isNow);
        row.setInt(R.id.row_time, "setTextColor", isNow ? accent : inkDim);
        setIconMaybeCustom(context, row, R.id.row_badge, isNow ? drawableDotAccent : drawableDotDim, isNow ? IconTint.ACCENT : IconTint.LINE);

        String mapQuery = (c.room != null && !c.room.trim().isEmpty() && !c.room.equalsIgnoreCase("TBA"))
                ? c.room
                : null;
        if (mapQuery != null && SettingsStore.isMapsEnabled(context)) {
            Intent fillIn = new Intent();
            fillIn.putExtra(WidgetActionActivity.EXTRA_ROOM, mapQuery);
            fillIn.putExtra(WidgetActionActivity.EXTRA_CLASS_NAME, c.name);
            fillIn.setData(Uri.parse("crsscheduler://room/" + Uri.encode(c.code) + "/" + c.start));
            row.setOnClickFillInIntent(R.id.row_root, fillIn);
        }

        List<dev.marquinhhou.crsscheduler.model.Attachment> syllabus =
                dev.marquinhhou.crsscheduler.data.AttachmentStore.get(context,
                        dev.marquinhhou.crsscheduler.data.AttachmentStore.NAMESPACE_SYLLABUS, c.code);
        if (!syllabus.isEmpty()) {
            row.setViewVisibility(R.id.row_syllabus, View.VISIBLE);
            Intent syllabusFillIn = new Intent();
            syllabusFillIn.putExtra(WidgetActionActivity.EXTRA_ATTACHMENT_URI, syllabus.get(0).uri);
            syllabusFillIn.setData(Uri.parse("crsscheduler://syllabus/" + Uri.encode(c.code)));
            row.setOnClickFillInIntent(R.id.row_syllabus, syllabusFillIn);
        } else {
            row.setViewVisibility(R.id.row_syllabus, View.GONE);
        }

        return row;
    }

    /**
     * clock_time/clock_date (the TextClocks in Today's and Weekly's headers) had layout-default
     * colors only -- nothing ever repainted them for Custom, unlike almost every other text
     * element in the header. Safe to call unconditionally everywhere bindGear is (including
     * Notes, which has neither id): RemoteViews silently skips a missing id.
     */
    private static void bindClock(Context context, RemoteViews rv) {
        rv.setInt(R.id.clock_time, "setTextColor", ink);
        rv.setInt(R.id.clock_date, "setTextColor", inkDim);
    }

    private static void bindGear(Context context, RemoteViews rv) {
        setIconMaybeCustom(context, rv, R.id.btn_settings, drawableIcSettings, IconTint.INK_DIM);
        // header_dot: the small bullet before "TODAY"/"WEEKLY"/"NOTES" -- previously had no id at
        // all so nothing could ever recolor it; every layout with a header dot also calls
        // bindGear, so this one call covers all of them.
        setIconMaybeCustom(context, rv, R.id.header_dot, drawableDotAccent, IconTint.ACCENT);
        Intent intent = new Intent(context, ConfigureActivity.class);
        // Widget taps always start a CLEAN task: without these flags the launcher routes
        // the new screen into whatever app task already exists, so e.g. tapping VIEW FULL
        // after the settings screen stacked on top of it and Back revealed Settings.
        // In-app navigation (a screen startActivity-ing another) is unaffected -- those
        // intents don't carry these flags.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.setData(Uri.parse("crsscheduler://configure/gear"));
        PendingIntent pi = PendingIntent.getActivity(
                context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_settings, pi);
    }

    /**
     * Shared across Today (full: a labeled "VIEW FULL" text chip with a view_full_schedule_label
     * child) and Today compact + both Weekly sizes (a bare calendar icon, no pill). The two
     * variants are styled differently ON PURPOSE now -- a lone icon in a box looked like a
     * different species of header button next to the unboxed gear/save icons, so only the
     * labeled variant gets chip treatment ({@code labeledTextChip}). The label/icon setters
     * are safe either way: RemoteViews silently skips an id that isn't present in whichever
     * layout is actually inflated.
     */
    private static void bindViewFullSchedule(Context context, RemoteViews rv, boolean labeledTextChip) {
        if (labeledTextChip) {
            styleChip(context, rv, R.id.btn_view_full_schedule, 0, false);
            rv.setInt(R.id.view_full_schedule_label, "setTextColor", inkDim);
        }
        setIconMaybeCustom(context, rv, R.id.view_full_schedule_icon, drawableIcCalendar, IconTint.INK_DIM);
        Intent intent = new Intent(context, WeekScheduleActivity.class);
        // Same clean-task rule as the gear: Back from the full schedule exits to home
        // instead of revealing whatever screen a previous widget tap left behind.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(
                context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_view_full_schedule, pi);
    }

    private static void bindSaveButton(Context context, RemoteViews rv) {
        setIconMaybeCustom(context, rv, R.id.btn_save, drawableIcSave, IconTint.INK_DIM);
        Intent intent = new Intent(context, WidgetSaveActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                context, 8, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_save, pi);
    }

    /** Opens the PDF directly if one's attached; otherwise pops the widget's upload prompt. */
    private static void bindForm5Button(Context context, RemoteViews rv) {
        // A bare document icon matching its neighbors -- same size, no pill. The layouts
        // were rebuilt around this (btn_form5 is an ImageView now), and the old outline-chip
        // styling is what made it read as a different species of header button.
        setIconMaybeCustom(context, rv, R.id.btn_form5, drawableIcForm5, IconTint.INK_DIM);
        Uri form5Uri = SettingsStore.getForm5Uri(context);
        Intent intent;
        if (form5Uri != null) {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(form5Uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        } else {
            intent = new Intent(context, WidgetForm5PromptActivity.class);
        }
        PendingIntent pi = PendingIntent.getActivity(
                context, 9, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_form5, pi);
    }

    /** Doubles as the "preview before start" toggle whenever the raw phase is UPCOMING. */
    private static void bindHeaderBrandLabel(Context context, RemoteViews rv, Class<?> providerClass,
                                              String toggleAction, String defaultLabel,
                                              SettingsStore.SemesterPhase rawPhase, int requestCode) {
        if (rawPhase == SettingsStore.SemesterPhase.UPCOMING) {
            // Not styleChip(): unlike a normal outline chip, this one's INACTIVE label is
            // accent-colored (not inkDim) on purpose, to flag "PREVIEW" as an available action
            // even before it's toggled on. The ON state is the filled accent chip, so its
            // label contrast-picks against the accent itself (see styleChip).
            boolean previewOn = SettingsStore.isPreviewBeforeStartEnabled(context);
            rv.setTextViewText(R.id.header_brand_label, previewOn ? "PREVIEW" : defaultLabel);
            rv.setInt(R.id.header_brand_label, "setTextColor", previewOn ? inkOn(accent) : inkDim);
            rv.setInt(R.id.header_brand_label, "setBackgroundResource",
                    previewOn ? drawableChipFilledBg : drawableChipOutlineBg);
            CustomThemeBackground.tintWidgetChip(context, rv, R.id.header_brand_label, previewOn);
            bindToggleTap(context, rv, R.id.header_brand_label, providerClass, toggleAction, requestCode);
        } else {
            rv.setTextViewText(R.id.header_brand_label, defaultLabel);
            rv.setInt(R.id.header_brand_label, "setTextColor", inkDim);
            rv.setInt(R.id.header_brand_label, "setBackgroundResource", R.drawable.bg_transparent);
        }
    }

    /**
     * One shared styling pass for every filled/outline chip in a widget, activity-side
     * tintChip's RemoteViews counterpart: sets the normal (GE/NE/Adaptive) chip drawable + text
     * color pair, then layers a Custom-mode-only recolor on top via
     * CustomThemeBackground.tintWidgetChip. Every chip call site should go through this instead
     * of hand-rolling its own background/text pair -- that duplication (slightly different each
     * time) is what let GROUPED/TODAY/+NOTE/F5 drift into looking like different design systems.
     *
     * @param labelViewId the view actually holding the text -- same as chipViewId for a plain
     *                    TextView chip, or a separate child id when the chip is a wrapper
     *                    (e.g. btn_add_note's LinearLayout around its "+ NOTE" TextView).
     */
    private static void styleChip(Context context, RemoteViews rv, int chipViewId, int labelViewId, boolean filled) {
        rv.setInt(chipViewId, "setBackgroundResource", filled ? drawableChipFilledBg : drawableChipOutlineBg);
        if (labelViewId != 0) {
            // Filled chips put text ON the accent, so the label must be contrast-picked
            // against THE ACCENT'S own luminance -- not the theme's bg color. Pairing with bg
            // happened to work while every family's accent was mid/dark, but Custom can now
            // derive a genuinely dark accent on a light system backdrop, where bg-colored
            // (near-black) text vanished into the fill.
            rv.setInt(labelViewId, "setTextColor", filled ? inkOn(accent) : inkDim);
        }
        CustomThemeBackground.tintWidgetChip(context, rv, chipViewId, labelViewId, filled);
    }

    /** Black or white -- whichever reads on {@code surface}. Mirrors the activity-side pairing rules. */
    private static int inkOn(int surface) {
        return androidx.core.graphics.ColorUtils.calculateLuminance(surface) > 0.55
                ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static void styleChip(Context context, RemoteViews rv, int chipViewId, boolean filled) {
        styleChip(context, rv, chipViewId, chipViewId, filled);
    }

    private static void bindTomorrowToggle(Context context, RemoteViews rv, boolean active) {
        rv.setTextViewText(R.id.btn_toggle_tomorrow, active ? "TODAY" : "TMRW");
        styleChip(context, rv, R.id.btn_toggle_tomorrow, active);
        bindToggleTap(context, rv, R.id.btn_toggle_tomorrow, TodayWidgetProvider.class,
                TodayWidgetProvider.ACTION_TOGGLE_TOMORROW, 7);
    }

    private static void bindToggleTap(Context context, RemoteViews rv, int viewId, Class<?> providerClass,
                                       String action, int requestCode) {
        Intent intent = new Intent(context, providerClass);
        intent.setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(viewId, pi);
    }

    /** MAX_HEIGHT, not MIN_HEIGHT -- despite the name, MIN_HEIGHT is landscape, MAX_HEIGHT is portrait. */
    private static int currentPortraitHeightDp(Bundle options) {
        if (options == null) return Integer.MAX_VALUE;
        int h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, -1);
        return h > 0 ? h : Integer.MAX_VALUE;
    }

    /**
     * Sets the Custom-theme background (color or photo) on a widget's RemoteViews, sized to its
     * actual dimensions from the options Bundle. A no-op (ImageView stays GONE, per the layout's
     * default) for every theme other than Custom, and falls back to sensible fixed dimensions if
     * the options Bundle is missing the size keys -- the ImageView's fitXY scaleType means an
     * imperfect size estimate still fills correctly, just with the photo's crop framed slightly
     * differently than ideal.
     */
    private static void applyCustomWidgetBackground(Context context, RemoteViews rv, Bundle options) {
        float density = context.getResources().getDisplayMetrics().density;
        int widthDp = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250) : 250;
        int heightDp = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 150) : 150;
        int widthPx = Math.round(widthDp * density);
        int heightPx = Math.round(heightDp * density);
        float cornerRadiusPx = 26 * density; // matches widget_bg_adaptive.xml's corner radius

        Bitmap bg = CustomThemeBackground.buildWidgetBackgroundBitmap(context, widthPx, heightPx, cornerRadiusPx);
        if (bg != null) {
            rv.setImageViewBitmap(R.id.widget_custom_bg, bg);
            rv.setViewVisibility(R.id.widget_custom_bg, View.VISIBLE);
        } else {
            // This was the "switched away from Custom but the widget still shows the old
            // custom background" bug. RemoteViews.setViewVisibility()/setImageViewBitmap()
            // are recorded actions applied on top of whatever the widget host's existing
            // view already looks like -- not a full re-description of the view from the
            // layout's defaults. When buildWidgetBackgroundBitmap() returns null (not
            // Custom), leaving this branch empty meant no action was ever issued to hide
            // widget_custom_bg, so a host that reapplies onto the existing view (rather
            // than fully rebuilding it) could leave a stale VISIBLE bitmap on screen
            // indefinitely after switching themes. Explicitly clear it every time instead.
            rv.setViewVisibility(R.id.widget_custom_bg, View.GONE);
            rv.setImageViewBitmap(R.id.widget_custom_bg, null);
        }
    }

    /**
     * Sets a widget icon, custom-tinted when Custom theme is active, falling back to the normal
     * resource (with its baked-in Adaptive/Nothing/GE tint) otherwise. `accent` picks which role
     * the icon plays: true for an active/affirmative state (checked, "now", accent dot), false
     * for a neutral/default one (archive, copy, chevron, dim dot).
     */
    /**
     * Overrides a row/card background with the Custom glass surface when the Custom theme
     * is active -- left alone (still the family's own row_bg drawable) otherwise. The
     * glass recipe (translucent fill + hairline border, matching the outline chips) is
     * baked into row_bg_custom_light/_dark and applied with a plain setBackgroundResource:
     * RemoteViews background-TINT composites SRC_IN over the whole drawable, flattening
     * fill and stroke into one color, so a tinted drawable can never show its border.
     *
     * Convenience overload for the (more common) case where the row/card has no "now"
     * concept at all -- always applies the glass surface under Custom.
     */
    private static void applyCustomRowSurface(Context context, RemoteViews row, int rootViewId) {
        applyCustomRowSurface(context, row, rootViewId, false);
    }

    /**
     * This was the "current class only changes text/dot color, not its background" bug
     * (reported under both Custom Photo AND Custom Color -- both are ThemeFamily.CUSTOM, so
     * both hit the same code path here). The caller had already set rootViewId's background
     * to drawableRowBgNow/drawableCardBgNow -- the family's own purpose-built accent "now"
     * surface (which, per Theming.pick(), resolves to the Adaptive family's row_bg_now/
     * card_bg_now under Custom) -- but this method used to unconditionally stamp the plain
     * frosted-glass surface over it right afterward, silently discarding that accent
     * background. Text color and the badge dot are set by the caller AFTER this call, so
     * they were never affected -- only the background was ever being lost, which is exactly
     * why those were the only two things that still looked "active."
     * isNow=true now skips the override entirely, leaving the caller's own now/accent
     * background in place; isNow=false (every other row/card) is unaffected and still gets
     * the glass treatment exactly as before.
     */
    private static void applyCustomRowSurface(Context context, RemoteViews row, int rootViewId, boolean isNow) {
        if (isNow) return;
        if (Theming.family(context) != SettingsStore.ThemeFamily.CUSTOM) return;
        boolean light = CustomThemeBackground.isCustomBackgroundLight(context);
        row.setInt(rootViewId, "setBackgroundResource",
                light ? R.drawable.row_bg_custom_light : R.drawable.row_bg_custom_dark);
    }

    /**
     * Which baked-in color a non-accent icon needs once it's re-derived for Custom theme.
     * Introduced after a regression: collapsing every non-accent icon onto one "dim" color
     * (LINE, a very faint ~10-20% wash meant only for tiny decorative dot markers) made
     * ordinary UI icons -- settings, save, archive, copy, calendar, form5, the note-header
     * chevron -- look "greyed out" under Custom theme, because their own non-Custom drawables
     * (ic_settings_adaptive.xml etc.) actually bake in INK_DIM, a much more visible ~78%-alpha
     * ink tone, not LINE. The two dim dot markers (drawableDotDim, used for "no class"/
     * "unchecked") and one "has a class" dot (drawableDotHasClass) also don't agree with each
     * other -- dot_dim_*.xml bakes LINE, dot_has_class_*.xml bakes INK_DIM. Rather than
     * guessing again, this enum makes every call site say explicitly which of the two colors
     * its own specific drawable actually uses, matching that drawable's real baked-in tint.
     */
    private enum IconTint { ACCENT, INK_DIM, LINE }

    /**
     * Sets an icon's own resource, then tints it to match tint. See {@link IconTint}'s
     * Javadoc for why this takes an explicit tint rather than a plain accent/dim boolean.
     * For a completed/checked glyph specifically, use {@link #setIconSuccessTinted} instead.
     */
    private static void setIconMaybeCustom(Context context, RemoteViews rv, int viewId, int drawableResId, IconTint tint) {
        switch (tint) {
            case ACCENT:
                setIconTinted(context, rv, viewId, drawableResId, R.color.ge_accent, R.color.ne_accent, R.color.adaptive_accent);
                break;
            case LINE:
                setIconTinted(context, rv, viewId, drawableResId, R.color.ge_line, R.color.ne_line, R.color.adaptive_line);
                break;
            case INK_DIM:
            default:
                setIconTinted(context, rv, viewId, drawableResId, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim);
                break;
        }
    }

    /** Same idea as {@link #setIconMaybeCustom}, but for a "done/completed" glyph -- needs the
     *  green success color specifically, under every theme, not the accent color. Custom theme
     *  used to route this through ColorRole.ACCENT (see the fixed ROLE_BY_NAME entry for
     *  "adaptive_green" in CustomThemeBackground) -- Theming.color() now resolves adaptive_green
     *  to the real success color under Custom too, so this reuses that fix for free. */
    private static void setIconSuccessTinted(Context context, RemoteViews rv, int viewId, int drawableResId) {
        setIconTinted(context, rv, viewId, drawableResId, R.color.ge_green, R.color.ne_green, R.color.adaptive_green);
    }

    /**
     * Sets the icon's own resource, then tints it via the OS's native ImageView.setColorFilter
     * (through RemoteViews' generic setInt reflection) instead of hand-rendering a Bitmap via
     * Canvas + Drawable.mutate()/draw() the way this used to work (CustomThemeBackground.
     * renderTintedIcon, now unused -- left in place rather than deleted, in case anything else
     * ever needs that exact recipe again). That custom per-icon bitmap pipeline was the prime
     * suspect for a report that a couple of these small dot/checkbox icons intermittently
     * rendered a fragment of the Custom Photo backdrop instead of their intended glyph on one
     * specific device -- a device-specific graphics-pipeline hiccup allocating and drawing many
     * small ARGB_8888 bitmaps during a widget refresh was the closest fit for a bug that didn't
     * reproduce in an emulator. Routing through the OS's own, vastly more heavily-used
     * ImageView color-filter path removes that whole class of risk regardless of the exact
     * mechanism, rather than patching one specific theory about it. This also means every
     * theme (not just Custom) now gets an explicit, freshly-set filter on every render instead
     * of relying on "leave it alone, the XML's own baked-in color is still correct" -- which
     * closes off any stale-filter-on-a-recycled-view risk as a side effect.
     */
    private static void setIconTinted(Context context, RemoteViews rv, int viewId, int drawableResId,
                                       int geColorRes, int neColorRes, int adaptiveColorRes) {
        rv.setImageViewResource(viewId, drawableResId);
        int color = Theming.color(context, geColorRes, neColorRes, adaptiveColorRes);
        rv.setInt(viewId, "setColorFilter", color);
    }

    public static int calendarDayToJs(int calendarDayOfWeek) {
        return calendarDayOfWeek - 1;
    }

    public static String minToLabel(int min) {
        int h = min / 60, m = min % 60;
        String mer = h >= 12 ? "PM" : "AM";
        h = h % 12;
        if (h == 0) h = 12;
        return h + ":" + String.format(Locale.US, "%02d", m) + " " + mer;
    }

    public static String formatSemesterDate(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US));
    }

    /** Calendar days to {@code target}, or -1 if null/not strictly future. Unlike daysUntilNextClass()
     *  (weekday offsets in the weekly pattern), this counts real calendar days to a fixed date. */
    private static long daysUntil(LocalDate target) {
        if (target == null) return -1;
        long days = ChronoUnit.DAYS.between(LocalDate.now(), target);
        return days > 0 ? days : -1;
    }

    /** " (in N days)" / " (in 1 day)", or "" if days <= 0 (today, past, or not set). */
    private static String daysSuffix(long days) {
        if (days <= 0) return "";
        return " (in " + days + (days == 1 ? " day)" : " days)");
    }

    public static String compactTimeLabel(int min) {
        int h = min / 60, m = min % 60;
        String mer = h >= 12 ? "p" : "a";
        h = h % 12;
        if (h == 0) h = 12;
        return m == 0 ? (h + mer) : (h + ":" + String.format(Locale.US, "%02d", m) + mer);
    }

    public static String compactRangeLabel(int startMin, int endMin) {
        String startLabel = compactTimeLabel(startMin);
        String endLabel = compactTimeLabel(endMin);
        boolean sameMeridiem = (startMin / 60 >= 12) == (endMin / 60 >= 12);
        String start = sameMeridiem ? startLabel.substring(0, startLabel.length() - 1) : startLabel;
        return start + "\u2013" + endLabel;
    }

    private static void bindTodayListAdapter(Context context, RemoteViews rv, int appWidgetId) {
        Intent svcIntent = new Intent(context, TodayClassesRemoteViewsService.class);
        svcIntent.setData(Uri.parse("crsscheduler://today_list/" + appWidgetId));
        rv.setRemoteAdapter(R.id.today_list_listview, svcIntent);
        rv.setEmptyView(R.id.today_list_listview, R.id.today_list_empty);

        // No more row-count guessing/fade overlays -- ListView scrolls fine on its own; just
        // clear any padding a previous build might have left on the host's recycled view.
        rv.setViewPadding(R.id.today_list_wrapper, 0, 0, 0, 0);

        Intent templateIntent = new Intent(context, WidgetActionActivity.class);
        PendingIntent templatePi = PendingIntent.getActivity(
                context, 3, templateIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setPendingIntentTemplate(R.id.today_list_listview, templatePi);
    }

    // Notes widget

    public static RemoteViews buildNotes(Context context, Bundle options, int appWidgetId) {
        synchronized (RENDER_LOCK) {
            return buildNotesLocked(context, options, appWidgetId);
        }
    }

    /** Always invoked while holding {@link #RENDER_LOCK} -- see its Javadoc. */
    private static RemoteViews buildNotesLocked(Context context, Bundle options, int appWidgetId) {
        resolveThemeAssets(context);
        RemoteViews rv = new RemoteViews(context.getPackageName(), layoutNotesWidget);
        bindGear(context, rv);
        bindClock(context, rv);

        int openCount = 0;
        for (Note n : NotesStore.load(context)) if (!n.completed && !n.archived) openCount++;
        rv.setTextViewText(R.id.notes_count_label, openCount == 0 ? "" : (openCount + " OPEN"));
        rv.setInt(R.id.notes_count_label, "setTextColor", inkDim);
        // The NOTES brand label never went through bindHeaderBrandLabel (Today/Weekly
        // only), so it kept its layout-default Adaptive color -- a wallpaper-derived
        // warm gray that read inconsistent with every other text on the widget.
        rv.setInt(R.id.header_brand_label, "setTextColor", inkDim);

        bindArchiveButton(context, rv);
        bindCopyAllButton(context, rv);
        bindViewToggle(context, rv);
        bindAddNoteButton(context, rv);
        bindTapEmptyToCreate(context, rv);

        // Empty-state copy rides directly on the widget background -- layout-default colors
        // never tracked Custom theming (invisible on photos).
        rv.setInt(R.id.notes_empty_title, "setTextColor", ink);
        rv.setInt(R.id.notes_empty_sub, "setTextColor", inkDim);

        bindNotesListAdapter(context, rv, appWidgetId);
        applyCustomWidgetBackground(context, rv, options);
        return rv;
    }

    /** If enabled, tapping blank space also opens "add note". */
    private static void bindTapEmptyToCreate(Context context, RemoteViews rv) {
        if (!SettingsStore.isNotesTapEmptyToCreateEnabled(context)) return;
        Intent intent = new Intent(context, NoteEditActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                context, 15, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.notes_list_wrapper, pi);
    }

    private static void bindAddNoteButton(Context context, RemoteViews rv) {
        // Always the filled/"call to action" style -- there's no inactive state for +NOTE, but
        // it still needs to go through styleChip so its color actually tracks the active theme
        // (previously a static @color/adaptive_bg in the layout, which never reflected Custom's
        // user-picked color at all) instead of the layout's static default.
        styleChip(context, rv, R.id.btn_add_note, R.id.btn_add_note_label, true);
        Intent intent = new Intent(context, NoteEditActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                context, 10, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_add_note, pi);
    }

    private static void bindArchiveButton(Context context, RemoteViews rv) {
        setIconMaybeCustom(context, rv, R.id.btn_archive, drawableIcArchive, IconTint.INK_DIM);
        Intent intent = new Intent(context, ArchivedNotesActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                context, 12, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_archive, pi);
    }

    private static void bindCopyAllButton(Context context, RemoteViews rv) {
        setIconMaybeCustom(context, rv, R.id.btn_copy_all, drawableIcCopy, IconTint.INK_DIM);
        Intent intent = new Intent(context, NotesWidgetProvider.class);
        intent.setAction(NotesWidgetProvider.ACTION_COPY_ALL);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, 13, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_copy_all, pi);
    }

    private static void bindViewToggle(Context context, RemoteViews rv) {
        boolean grouped = NotesStore.isGroupedView(context);
        rv.setTextViewText(R.id.btn_view_toggle, grouped ? "GROUPED" : "LIST");
        // Previously never styled at all (layout default only) -- neither reflected which mode
        // was active nor tracked Custom's color, unlike every other toggle chip in the app.
        // Grouped mirrors TODAY/TMRW's convention: the "on" state is filled.
        styleChip(context, rv, R.id.btn_view_toggle, grouped);
        Intent intent = new Intent(context, NotesWidgetProvider.class);
        intent.setAction(NotesWidgetProvider.ACTION_TOGGLE_VIEW);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, 14, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_view_toggle, pi);
    }

    private static void bindNotesListAdapter(Context context, RemoteViews rv, int appWidgetId) {
        Intent svcIntent = new Intent(context, NotesRemoteViewsService.class);
        svcIntent.setData(Uri.parse("crsscheduler://notes_list/" + appWidgetId));
        rv.setRemoteAdapter(R.id.notes_list_listview, svcIntent);
        rv.setEmptyView(R.id.notes_list_listview, R.id.notes_list_empty);

        // One shared template; row actions are told apart by fillInIntent extras.
        Intent templateIntent = new Intent(context, NotesWidgetProvider.class);
        templateIntent.setAction(NotesWidgetProvider.ACTION_ROW_TAP);
        PendingIntent templatePi = PendingIntent.getBroadcast(
                context, 11, templateIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setPendingIntentTemplate(R.id.notes_list_listview, templatePi);
    }

    /** Called from NotesRemoteViewsService. */
    public static RemoteViews buildNoteRowForAdapter(Context context, Note note, boolean isLast) {
        synchronized (RENDER_LOCK) {
            return buildNoteRowForAdapterLocked(context, note, isLast);
        }
    }

    /** Always invoked while holding {@link #RENDER_LOCK} -- see its Javadoc. */
    private static RemoteViews buildNoteRowForAdapterLocked(Context context, Note note, boolean isLast) {
        resolveThemeAssets(context);
        RemoteViews row = new RemoteViews(context.getPackageName(), layoutRowNote);
        int bottomPaddingDp = isLast ? 0 : 4;
        int bottomPaddingPx = Math.round(bottomPaddingDp * context.getResources().getDisplayMetrics().density);
        row.setViewPadding(R.id.row_note_root, 0, 0, 0, bottomPaddingPx);
        // Explicit default background, same as buildClassRowForAdapterLocked's row_root --
        // this layout (layoutRowNote) is deliberately shared with a group-header row (see
        // buildNoteGroupHeaderForAdapterLocked), which sets row_root to bg_transparent
        // unconditionally. Without re-asserting the real card background here, a row the host
        // recycles from a header keeps that transparent background under every non-Custom
        // theme, since applyCustomRowSurface() below only ever touches row_root for Custom.
        row.setInt(R.id.row_root, "setBackgroundResource", drawableRowBg);
        applyCustomRowSurface(context, row, R.id.row_root);

        row.setTextViewText(R.id.row_note_subject_label, note.isMisc() ? "MISC" : abbreviateName(note.subjectName));
        // Explicit VISIBLE, not just styled/texted -- layoutRowNote is shared with
        // buildNoteGroupHeaderForAdapter below (deliberately, per that method's own comment),
        // which explicitly sets this chip GONE for header rows. The widget host recycles
        // actual View instances across both row kinds since they're the same layout/type, so
        // a view last updated as a collapsed group header keeps its subject chip hidden the
        // next time it's recycled into an ordinary note row, unless every regular note row
        // explicitly re-asserts VISIBLE itself -- exactly the "recycled rows keep stale state"
        // lesson row_note_urgent_label already applies two lines below, just missed here.
        // This is what showed up as "the subject tags sometimes disappear."
        row.setViewVisibility(R.id.row_note_subject_chip, View.VISIBLE);
        // Both previously had a chip_outline_bg_* background declared in the layout but were
        // never actually retinted for Custom (widgets have no retintTree safety net) -- the
        // "MISC"/subject badge and the "URGENT" badge, sitting right next to LIST/GROUPED and
        // the deadline badge which already went through this.
        styleChip(context, row, R.id.row_note_subject_chip, R.id.row_note_subject_label, false);
        row.setViewVisibility(R.id.row_note_urgent_label, note.urgent ? View.VISIBLE : View.GONE);
        if (note.urgent) {
            styleChip(context, row, R.id.row_note_urgent_label, false);
            // Urgency is semantic -- the quiet outline treatment must not swallow its
            // error-red label (styleChip just set it to the dim ink).
            row.setInt(R.id.row_note_urgent_label, "setTextColor", colorError);
        }
        row.setTextViewText(R.id.row_note_title, note.title);
        // Always set -- recycled rows keep a stale value otherwise.
        row.setInt(R.id.row_note_title, "setPaintFlags",
                note.completed ? (android.graphics.Paint.STRIKE_THRU_TEXT_FLAG | android.graphics.Paint.ANTI_ALIAS_FLAG)
                                : android.graphics.Paint.ANTI_ALIAS_FLAG);
        row.setInt(R.id.row_note_title, "setTextColor", note.completed ? inkDim : ink);

        if (note.body == null || note.body.trim().isEmpty()) {
            row.setViewVisibility(R.id.row_note_body, View.GONE);
        } else {
            row.setViewVisibility(R.id.row_note_body, View.VISIBLE);
            row.setTextViewText(R.id.row_note_body, note.body.trim());
            row.setInt(R.id.row_note_body, "setTextColor", inkDim);
        }

        bindDeadlineBadge(row, note);
        // Previously substituted a hollow outline ring (dot_check_ring) for the unchecked
        // state under Custom theme -- a transparent-centered shape sitting on Custom's
        // translucent glass row surface, which lets the raw, unblurred photo backdrop show
        // straight through its hollow middle. That's the other half of the "photo shows up
        // as this element" report: not a rendering bug in the icon itself, but a genuinely
        // see-through icon over a see-through surface. Using the same solid dim dot every
        // other theme already uses removes the hollow region entirely rather than trying to
        // back it with an opaque patch.
        if (note.completed) {
            setIconSuccessTinted(context, row, R.id.row_note_check, drawableDotCheckedNote);
        } else {
            setIconMaybeCustom(context, row, R.id.row_note_check, drawableDotDim, IconTint.LINE);
        }

        Intent openFillIn = new Intent();
        openFillIn.putExtra(NotesWidgetProvider.EXTRA_NOTE_ID, note.id);
        openFillIn.putExtra(NotesWidgetProvider.EXTRA_SUB_ACTION, NotesWidgetProvider.SUB_ACTION_OPEN);
        openFillIn.setData(Uri.parse("crsscheduler://note_action/open/" + note.id));
        row.setOnClickFillInIntent(R.id.row_note_content, openFillIn);

        Intent toggleFillIn = new Intent();
        toggleFillIn.putExtra(NotesWidgetProvider.EXTRA_NOTE_ID, note.id);
        toggleFillIn.putExtra(NotesWidgetProvider.EXTRA_SUB_ACTION, NotesWidgetProvider.SUB_ACTION_TOGGLE);
        toggleFillIn.setData(Uri.parse("crsscheduler://note_action/toggle/" + note.id));
        row.setOnClickFillInIntent(R.id.row_note_check, toggleFillIn);

        // Only shows once a note is done.
        if (note.completed) {
            row.setViewVisibility(R.id.row_note_archive, View.VISIBLE);
            setIconMaybeCustom(context, row, R.id.row_note_archive, drawableIcArchive, IconTint.INK_DIM);
            Intent archiveFillIn = new Intent();
            archiveFillIn.putExtra(NotesWidgetProvider.EXTRA_NOTE_ID, note.id);
            archiveFillIn.putExtra(NotesWidgetProvider.EXTRA_SUB_ACTION, NotesWidgetProvider.SUB_ACTION_ARCHIVE);
            archiveFillIn.setData(Uri.parse("crsscheduler://note_action/archive/" + note.id));
            row.setOnClickFillInIntent(R.id.row_note_archive, archiveFillIn);
        } else {
            row.setViewVisibility(R.id.row_note_archive, View.GONE);
        }

        // Tapping opens the first attachment directly (a persisted URI grant means no
        // Activity round-trip is needed). Extra attachments beyond the first are reachable
        // from the note's own edit screen -- the widget row has no room to pick among them.
        // NOTE: rows returned by a RemoteViewsFactory can only use setOnClickFillInIntent
        // (routed through the ListView's single PendingIntentTemplate) -- calling
        // setOnClickPendingIntent directly on a collection-item child view throws at bind time.
        List<dev.marquinhhou.crsscheduler.model.Attachment> attachments =
                dev.marquinhhou.crsscheduler.data.AttachmentStore.get(context,
                        dev.marquinhhou.crsscheduler.data.AttachmentStore.NAMESPACE_NOTE, String.valueOf(note.id));
        if (!attachments.isEmpty()) {
            row.setViewVisibility(R.id.row_note_attachment, View.VISIBLE);
            String fileUri = attachments.get(0).uri;
            Intent attachmentFillIn = new Intent();
            attachmentFillIn.putExtra(NotesWidgetProvider.EXTRA_SUB_ACTION, NotesWidgetProvider.SUB_ACTION_OPEN_ATTACHMENT);
            attachmentFillIn.putExtra(NotesWidgetProvider.EXTRA_ATTACHMENT_URI, fileUri);
            attachmentFillIn.setData(Uri.parse("crsscheduler://note_action/open_attachment/" + note.id));
            row.setOnClickFillInIntent(R.id.row_note_attachment, attachmentFillIn);
        } else {
            row.setViewVisibility(R.id.row_note_attachment, View.GONE);
        }

        return row;
    }

    /**
     * Called from NotesRemoteViewsService. Deliberately reuses layoutRowNote (the same layout
     * real note rows use) instead of a second, separate header layout/type -- every previous
     * attempt at a dedicated group-header row/type failed in some way that survived multiple
     * rounds of fixes, so this rebuild removes that whole axis of risk rather than patch it
     * again: there is now only one real row layout in use, period, and every action below is
     * one already proven to render correctly (it's the same set buildNoteRowForAdapter uses).
     */
    public static RemoteViews buildNoteGroupHeaderForAdapter(Context context, String groupKey, String label,
                                                               int count, boolean collapsed) {
        synchronized (RENDER_LOCK) {
            return buildNoteGroupHeaderForAdapterLocked(context, groupKey, label, count, collapsed);
        }
    }

    /** Always invoked while holding {@link #RENDER_LOCK} -- see its Javadoc. */
    private static RemoteViews buildNoteGroupHeaderForAdapterLocked(Context context, String groupKey, String label,
                                                               int count, boolean collapsed) {
        resolveThemeAssets(context);
        RemoteViews header = new RemoteViews(context.getPackageName(), layoutRowNote);
        header.setViewPadding(R.id.row_note_root, 0, 0, 0, 0);

        // Flat/transparent instead of the note row's card background -- a section header
        // shouldn't compete visually with the item card sitting directly below it.
        header.setInt(R.id.row_root, "setBackgroundResource", R.drawable.bg_transparent);
        int vPad = dpToPx(context.getResources().getDisplayMetrics().density, 4f);
        int hPad = dpToPx(context.getResources().getDisplayMetrics().density, 6f);
        header.setViewPadding(R.id.row_root, hPad, vPad, hPad, vPad);

        setIconMaybeCustom(context, header, R.id.row_note_check, collapsed ? drawableIcChevronRight : drawableIcChevronDown, IconTint.INK_DIM);

        // "MISCELLANEOUS" in the header's usual bold/ink style, count trailing in a smaller,
        // dimmer span within the same TextView -- setTextViewText's span support (ForegroundColorSpan
        // / RelativeSizeSpan) is a dedicated, first-class RemoteViews text action, not the generic
        // reflection mechanism that turned out to be the real problem last round.
        android.text.SpannableString styled = new android.text.SpannableString(label.toUpperCase(Locale.US) + "   " + count);
        int countStart = label.length() + 3;
        styled.setSpan(new android.text.style.ForegroundColorSpan(inkDim), countStart, styled.length(),
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new android.text.style.RelativeSizeSpan(0.85f), countStart, styled.length(),
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        header.setTextViewText(R.id.row_note_title, styled);
        header.setInt(R.id.row_note_title, "setPaintFlags", android.graphics.Paint.ANTI_ALIAS_FLAG);
        header.setInt(R.id.row_note_title, "setTextColor", ink);

        header.setViewVisibility(R.id.row_note_urgent_label, View.GONE);
        header.setViewVisibility(R.id.row_note_subject_chip, View.GONE);
        header.setViewVisibility(R.id.row_note_deadline_label, View.GONE);
        header.setViewVisibility(R.id.row_note_deadline_absolute, View.GONE);
        header.setViewVisibility(R.id.row_note_body, View.GONE);
        header.setViewVisibility(R.id.row_note_attachment, View.GONE);
        header.setViewVisibility(R.id.row_note_archive, View.GONE);

        Intent toggleFillIn = new Intent();
        toggleFillIn.putExtra(NotesWidgetProvider.EXTRA_GROUP_KEY, groupKey);
        toggleFillIn.putExtra(NotesWidgetProvider.EXTRA_SUB_ACTION, NotesWidgetProvider.SUB_ACTION_TOGGLE_GROUP);
        toggleFillIn.setData(Uri.parse("crsscheduler://note_action/toggle_group/" + groupKey));
        header.setOnClickFillInIntent(R.id.row_note_content, toggleFillIn);
        header.setOnClickFillInIntent(R.id.row_note_check, toggleFillIn);

        return header;
    }

    /** "OVERDUE" / "DUE TODAY" / "DUE 3:30 PM" / "DUE TMRW" / "DUE IN Nd" / "DONE", or hidden if no deadline. */
    private static void bindDeadlineBadge(RemoteViews row, Note note) {
        if (note.deadlineEpochDay == null) {
            row.setViewVisibility(R.id.row_note_deadline_label, View.GONE);
            row.setViewVisibility(R.id.row_note_deadline_absolute, View.GONE);
            return;
        }
        row.setViewVisibility(R.id.row_note_deadline_label, View.VISIBLE);
        row.setViewVisibility(R.id.row_note_deadline_absolute, View.VISIBLE);
        row.setTextViewText(R.id.row_note_deadline_absolute, "\u00b7 " + absoluteDeadlineText(note));
        // The absolute date only ever had its layout-default color -- the one text in a note
        // row that never tracked the theme (invisible on Custom photo backgrounds).
        row.setInt(R.id.row_note_deadline_absolute, "setTextColor", inkDim);

        if (note.completed) {
            row.setTextViewText(R.id.row_note_deadline_label, "DONE");
            row.setInt(R.id.row_note_deadline_label, "setTextColor", colorGreen);
            return;
        }
        long daysDiff = note.deadlineEpochDay - LocalDate.now().toEpochDay();
        String label;
        int color;
        if (daysDiff < 0) {
            label = "OVERDUE";
            color = colorError;
        } else if (daysDiff == 0) {
            if (note.deadlineMinuteOfDay != null) {
                LocalTime now = LocalTime.now();
                LocalTime due = LocalTime.of(note.deadlineMinuteOfDay / 60, note.deadlineMinuteOfDay % 60);
                if (now.isAfter(due)) {
                    label = "OVERDUE";
                    color = colorError;
                } else {
                    label = "DUE " + due.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)).toUpperCase(Locale.US);
                    color = colorError;
                }
            } else {
                label = "DUE TODAY";
                color = colorError;
            }
        } else if (daysDiff == 1) {
            label = "DUE TMRW";
            color = inkDim;
        } else {
            label = "DUE IN " + daysDiff + "D";
            color = inkDim;
        }
        row.setTextViewText(R.id.row_note_deadline_label, label);
        row.setInt(R.id.row_note_deadline_label, "setTextColor", color);
    }

    /** "Jul 30" or "Jul 30, 5:00 PM" -- the actual deadline. */
    public static String absoluteDeadlineText(Note note) {
        String date = LocalDate.ofEpochDay(note.deadlineEpochDay)
                .format(DateTimeFormatter.ofPattern("MMM d", Locale.US));
        if (note.deadlineMinuteOfDay == null) return date;
        LocalTime time = LocalTime.of(note.deadlineMinuteOfDay / 60, note.deadlineMinuteOfDay % 60);
        return date + ", " + time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US));
    }
}
