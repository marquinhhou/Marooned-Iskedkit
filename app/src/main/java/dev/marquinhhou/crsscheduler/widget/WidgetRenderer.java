package dev.marquinhhou.crsscheduler.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
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
import dev.marquinhhou.crsscheduler.ui.NoteEditActivity;
import dev.marquinhhou.crsscheduler.ui.Theming;
import dev.marquinhhou.crsscheduler.ui.WeekScheduleActivity;
import dev.marquinhhou.crsscheduler.ui.WidgetActionActivity;
import dev.marquinhhou.crsscheduler.ui.WidgetForm5PromptActivity;
import dev.marquinhhou.crsscheduler.ui.WidgetSaveActivity;

/** Builds RemoteViews for all widgets. resolveThemeAssets() picks the _ge/_ne/_adaptive set per build. */
public final class WidgetRenderer {

    public static final String[] DAY_LABELS = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
    public static final int[] WEEK_ORDER = {1, 2, 3, 4, 5, 6, 0}; // Mon..Sun

    // Resolved once per build call by resolveThemeAssets() -- see class comment.
    private static int accent, ink, inkDim, bg, lineStrong;
    private static boolean useDotRing;
    private static int layoutToday, layoutTodayCompact, layoutWeek, layoutWeekExpanded;
    private static int layoutRowClass, layoutRowWeekDot, layoutRowWeekTable;
    private static int layoutCellTime, layoutCellDayLabel, layoutCellClass, layoutRowMoreIndicator;
    private static int layoutNotesWidget, layoutRowNote, layoutRowNoteGroupHeader;
    private static int drawableCardBg, drawableCardBgNow, drawableChipFilledBg, drawableChipOutlineBg;
    private static int drawableRowBg, drawableRowBgNow, drawableDotAccent, drawableDotDim, drawableDotHasClass;
    private static int drawableDotCheckedNote, drawableIcArchive, drawableIcCopy, drawableIcChevronDown;
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
        layoutRowNoteGroupHeader = Theming.pick(context, R.layout.row_note_group_header_ge, R.layout.row_note_group_header_ne, R.layout.row_note_group_header_adaptive);

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
        colorGreen = Theming.color(context, R.color.ge_green, R.color.ne_green, R.color.adaptive_green);
        colorError = Theming.color(context, R.color.ge_error, R.color.ne_error, R.color.adaptive_error);
    }

    /** So TodayClassesRemoteViewsService's out-of-bounds fallback row picks the right theme too. */
    public static int rowMoreIndicatorLayout(Context context) {
        resolveThemeAssets(context);
        return layoutRowMoreIndicator;
    }

    // Today widget

    public static RemoteViews buildToday(Context context, Bundle options, int appWidgetId) {
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
                ? buildTodayFull(context, schedule, todays, appWidgetId, phase, rawPhase)
                : buildTodayCompact(context, schedule, todays, phase, rawPhase);
    }

    private static RemoteViews buildTodayFull(Context context, List<ClassSession> schedule,
                                               List<ClassSession> todays, int appWidgetId,
                                               SettingsStore.SemesterPhase phase, SettingsStore.SemesterPhase rawPhase) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), layoutToday);
        bindGear(context, rv);
        bindViewFullSchedule(context, rv);
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
            showHero(rv, "NOW", ongoing, progress, ongoing.end, "%s left", true, true);
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
            showHero(rv, label, next, progress, next.start, "in %s", false, true);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, todayMinuteToEpochMillis(next.start));
        } else if (phase == SettingsStore.SemesterPhase.UPCOMING) {
            LocalDate start = SettingsStore.getSemesterStart(context);
            showEmptyHero(rv, "Semester hasn't started yet.",
                    "Classes begin " + formatSemesterDate(start) + daysSuffix(daysUntil(start)) + ". Tap to preview anyway.", true);
            bindToggleTap(context, rv, R.id.hero_empty_sub, TodayWidgetProvider.class,
                    TodayWidgetProvider.ACTION_TOGGLE_PREVIEW, 6);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        } else if (phase == SettingsStore.SemesterPhase.ENDED) {
            LocalDate end = SettingsStore.getSemesterEnd(context);
            showEmptyHero(rv, "Semester has ended.",
                    "Classes ran through " + formatSemesterDate(end) + ".", true);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        } else if (!schedule.isEmpty()) {
            showEmptyHero(rv, "Day's clear from here.", "Nothing left on today's schedule.", true);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        } else {
            showEmptyHero(rv, "No schedule loaded.", "Tap the gear to load your CRS classes.", true);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        }

        boolean showTomorrow = SettingsStore.isShowTomorrowEnabled(context);
        rv.setTextViewText(R.id.today_list_title,
                (!outOfSession && !schedule.isEmpty())
                        ? (showTomorrow ? "TOMORROW'S CLASSES" : "TODAY'S CLASSES") : "");
        rv.setTextViewText(R.id.today_list_empty, todayListEmptyText(schedule, today, showTomorrow, outOfSession));
        bindTomorrowToggle(context, rv, showTomorrow);

        bindTodayListAdapter(context, rv, appWidgetId);

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
                                                  SettingsStore.SemesterPhase rawPhase) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), layoutTodayCompact);
        bindGear(context, rv);
        bindViewFullSchedule(context, rv);
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
            showHero(rv, "NOW", ongoing, progress, 0, "", true, false);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, todayMinuteToEpochMillis(ongoing.end));
        } else if (next != null) {
            boolean hadEarlierClassToday = false;
            for (ClassSession c : todays) if (c.end <= nowMin) { hadEarlierClassToday = true; break; }
            String label = hadEarlierClassToday ? "FREE TIME" : "NEXT";
            int gapWindow = 240;
            float progress = 1f - Math.min(1f, (next.start - nowMin) / (float) gapWindow);
            showHero(rv, label, next, progress, 0, "", false, false);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, todayMinuteToEpochMillis(next.start));
        } else if (phase == SettingsStore.SemesterPhase.UPCOMING) {
            long days = daysUntil(SettingsStore.getSemesterStart(context));
            showEmptyHero(rv, days > 0 ? "Semester in " + days + (days == 1 ? " day" : " days")
                    : "Semester hasn't started yet.", "", false);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        } else if (phase == SettingsStore.SemesterPhase.ENDED) {
            showEmptyHero(rv, "Semester has ended.", "", false);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        } else if (!schedule.isEmpty()) {
            showEmptyHero(rv, "Day's clear from here.", "", false);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        } else {
            showEmptyHero(rv, "No schedule loaded.", "", false);
            WidgetRefreshScheduler.scheduleNextTransitionTick(context, null);
        }

        return rv;
    }

    private static void showHero(RemoteViews rv, String label, ClassSession c, float progress,
                                  int countdownTargetMinute, String countdownFormat,
                                  boolean isNow, boolean includeCountdown) {
        rv.setViewVisibility(R.id.hero_content, View.VISIBLE);
        rv.setViewVisibility(R.id.hero_empty, View.GONE);
        android.graphics.Bitmap ring = useDotRing
                ? RingBitmapFactory.buildDotRing(progress, 180, accent)
                : RingBitmapFactory.buildArcRing(progress, 180, accent, lineStrong);
        rv.setImageViewBitmap(R.id.hero_ring, ring);
        rv.setTextViewText(R.id.hero_label, label);
        rv.setTextViewText(R.id.hero_class, c.name);
        rv.setTextViewText(R.id.hero_meta, c.displayRoom() + " \u00B7 " + minToLabel(c.start) + "\u2013" + minToLabel(c.end));
        if (includeCountdown) {
            long targetEpochMillis = todayMinuteToEpochMillis(countdownTargetMinute);
            long base = SystemClock.elapsedRealtime() + (targetEpochMillis - System.currentTimeMillis());
            rv.setChronometer(R.id.hero_countdown, base, countdownFormat, true);
            rv.setChronometerCountDown(R.id.hero_countdown, true);
        }
        rv.setInt(R.id.hero_card, "setBackgroundResource", isNow ? drawableCardBgNow : drawableCardBg);
    }

    private static long todayMinuteToEpochMillis(int minuteOfDay) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        cal.set(Calendar.MINUTE, minuteOfDay % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static void showEmptyHero(RemoteViews rv, String title, String sub, boolean includeSub) {
        rv.setViewVisibility(R.id.hero_content, View.GONE);
        rv.setViewVisibility(R.id.hero_empty, View.VISIBLE);
        rv.setTextViewText(R.id.hero_empty_title, title);
        if (includeSub) rv.setTextViewText(R.id.hero_empty_sub, sub);
        rv.setInt(R.id.hero_card, "setBackgroundResource", drawableCardBg);
    }

    // Week widget

    public static RemoteViews buildWeekSummary(Context context, Bundle options) {
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
        return buildWeekExpanded(context, schedule, breakpoints, activeDays, occupiedRows, scale, rawPhase);
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
        bindViewFullSchedule(context, rv);
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
                    dot.setImageViewResource(R.id.week_dot, drawableDotAccent);
                } else {
                    dot.setImageViewResource(R.id.week_dot, hasClasses ? drawableDotHasClass : drawableDotDim);
                }
                rv.addView(R.id.week_dots_row, dot);
            }
        }

        return rv;
    }

    /** CRS-style time/day grid. Always shows every occupied time slot -- row size adapts (see buildWeekTable). */
    private static RemoteViews buildWeekExpanded(Context context, List<ClassSession> schedule,
                                                  List<Integer> breakpoints, List<Integer> activeDays,
                                                  List<Integer> occupiedRows, float scale,
                                                  SettingsStore.SemesterPhase rawPhase) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), layoutWeekExpanded);
        bindGear(context, rv);
        bindViewFullSchedule(context, rv);
        bindSaveButton(context, rv);
        bindForm5Button(context, rv);
        bindHeaderBrandLabel(context, rv, WeekWidgetProvider.class,
                WeekWidgetProvider.ACTION_TOGGLE_PREVIEW, "WEEKLY", rawPhase, 5);

        double totalUnits = 0;
        for (ClassSession c : schedule) if (!c.creditsExcluded) totalUnits += c.credits;
        rv.setTextViewText(R.id.table_headline,
                schedule.size() + (schedule.size() == 1 ? " class" : " classes")
                        + " \u00B7 " + String.format(Locale.US, "%.1f", totalUnits) + " units");

        buildWeekTable(context, rv, schedule, breakpoints, activeDays, occupiedRows, scale);

        String noClassDays = noClassDaysLabel(activeDays);
        if (noClassDays.isEmpty()) {
            rv.setViewVisibility(R.id.no_class_days_note, View.GONE);
        } else {
            rv.setViewVisibility(R.id.no_class_days_note, View.VISIBLE);
            rv.setTextViewText(R.id.no_class_days_note, noClassDays);
        }

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
        scaleTimeCell(corner, scale, density);
        headerRow.addView(R.id.table_row, corner);
        for (int dayIdx : activeDays) {
            RemoteViews dayCell = new RemoteViews(context.getPackageName(), layoutCellDayLabel);
            dayCell.setTextViewText(R.id.cell_daylabel_text, DAY_LABELS[dayIdx].substring(0, 1));
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
            scaleTimeCell(timeCell, scale, density);
            row.addView(R.id.table_row, timeCell);

            for (int dayIdx : activeDays) {
                ClassSession match = findClassInSlot(schedule, dayIdx, slotStart, slotEnd);
                RemoteViews classCell = new RemoteViews(context.getPackageName(), layoutCellClass);
                if (match != null) {
                    classCell.setViewVisibility(R.id.cell_class_text, View.VISIBLE);
                    classCell.setViewVisibility(R.id.cell_dot_image, View.GONE);
                    classCell.setTextViewText(R.id.cell_class_text, abbreviateName(match.name));
                    classCell.setInt(R.id.cell_class_text, "setTextColor", dayIdx == today ? accent : ink);
                } else {
                    classCell.setViewVisibility(R.id.cell_class_text, View.GONE);
                    classCell.setViewVisibility(R.id.cell_dot_image, View.VISIBLE);
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
        cell.setTextViewTextSize(R.id.cell_class_text, TypedValue.COMPLEX_UNIT_SP, 7f * scale);
        int vPad = dpToPx(density, 2f * scale);
        cell.setViewPadding(R.id.cell_class_text, 0, vPad, 0, vPad);
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
        resolveThemeAssets(context);
        RemoteViews row = new RemoteViews(context.getPackageName(), layoutRowClass);
        // Always set explicitly -- recycled rows keep a stale padding otherwise (the squished-row bug).
        int bottomPaddingDp = isLast ? 0 : 4;
        int bottomPaddingPx = Math.round(bottomPaddingDp * context.getResources().getDisplayMetrics().density);
        row.setViewPadding(R.id.row_class_root, 0, 0, 0, bottomPaddingPx);
        row.setTextViewText(R.id.row_time, minToLabel(c.start) + "\n" + minToLabel(c.end));
        row.setTextViewText(R.id.row_name, c.name);
        String meta = c.displayRoom() + (c.instructor != null && !c.instructor.isEmpty() ? " \u00B7 " + c.instructor : "");
        row.setTextViewText(R.id.row_room, meta);
        row.setInt(R.id.row_root, "setBackgroundResource", isNow ? drawableRowBgNow : drawableRowBg);
        row.setInt(R.id.row_time, "setTextColor", isNow ? accent : inkDim);
        row.setImageViewResource(R.id.row_badge, isNow ? drawableDotAccent : drawableDotDim);

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
        return row;
    }

    private static void bindGear(Context context, RemoteViews rv) {
        Intent intent = new Intent(context, ConfigureActivity.class);
        intent.setData(Uri.parse("crsscheduler://configure/gear"));
        PendingIntent pi = PendingIntent.getActivity(
                context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_settings, pi);
    }

    private static void bindViewFullSchedule(Context context, RemoteViews rv) {
        Intent intent = new Intent(context, WeekScheduleActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_view_full_schedule, pi);
    }

    private static void bindSaveButton(Context context, RemoteViews rv) {
        Intent intent = new Intent(context, WidgetSaveActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                context, 8, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_save, pi);
    }

    /** Opens the PDF directly if one's attached; otherwise pops the widget's upload prompt. */
    private static void bindForm5Button(Context context, RemoteViews rv) {
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
            boolean previewOn = SettingsStore.isPreviewBeforeStartEnabled(context);
            rv.setTextViewText(R.id.header_brand_label, previewOn ? "PREVIEW" : defaultLabel);
            rv.setInt(R.id.header_brand_label, "setTextColor", previewOn ? bg : accent);
            rv.setInt(R.id.header_brand_label, "setBackgroundResource",
                    previewOn ? drawableChipFilledBg : drawableChipOutlineBg);
            bindToggleTap(context, rv, R.id.header_brand_label, providerClass, toggleAction, requestCode);
        } else {
            rv.setTextViewText(R.id.header_brand_label, defaultLabel);
            rv.setInt(R.id.header_brand_label, "setTextColor", inkDim);
            rv.setInt(R.id.header_brand_label, "setBackgroundResource", android.R.color.transparent);
        }
    }

    private static void bindTomorrowToggle(Context context, RemoteViews rv, boolean active) {
        rv.setTextViewText(R.id.btn_toggle_tomorrow, active ? "TODAY" : "TMRW");
        rv.setInt(R.id.btn_toggle_tomorrow, "setBackgroundResource",
                active ? drawableChipFilledBg : drawableChipOutlineBg);
        rv.setInt(R.id.btn_toggle_tomorrow, "setTextColor", active ? bg : inkDim);
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
        resolveThemeAssets(context);
        RemoteViews rv = new RemoteViews(context.getPackageName(), layoutNotesWidget);
        bindGear(context, rv);

        int openCount = 0;
        for (Note n : NotesStore.load(context)) if (!n.completed && !n.archived) openCount++;
        rv.setTextViewText(R.id.notes_count_label, openCount == 0 ? "" : (openCount + " OPEN"));

        bindArchiveButton(context, rv);
        bindCopyAllButton(context, rv);
        bindViewToggle(context, rv);
        bindAddNoteButton(context, rv);
        bindTapEmptyToCreate(context, rv);
        bindNotesListAdapter(context, rv, appWidgetId);
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
        Intent intent = new Intent(context, NoteEditActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                context, 10, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_add_note, pi);
    }

    private static void bindArchiveButton(Context context, RemoteViews rv) {
        rv.setImageViewResource(R.id.btn_archive, drawableIcArchive);
        Intent intent = new Intent(context, ArchivedNotesActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                context, 12, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_archive, pi);
    }

    private static void bindCopyAllButton(Context context, RemoteViews rv) {
        rv.setImageViewResource(R.id.btn_copy_all, drawableIcCopy);
        Intent intent = new Intent(context, NotesWidgetProvider.class);
        intent.setAction(NotesWidgetProvider.ACTION_COPY_ALL);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, 13, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_copy_all, pi);
    }

    private static void bindViewToggle(Context context, RemoteViews rv) {
        boolean grouped = NotesStore.isGroupedView(context);
        rv.setTextViewText(R.id.btn_view_toggle, grouped ? "GROUPED" : "LIST");
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
        resolveThemeAssets(context);
        RemoteViews row = new RemoteViews(context.getPackageName(), layoutRowNote);
        int bottomPaddingDp = isLast ? 0 : 4;
        int bottomPaddingPx = Math.round(bottomPaddingDp * context.getResources().getDisplayMetrics().density);
        row.setViewPadding(R.id.row_note_root, 0, 0, 0, bottomPaddingPx);

        row.setTextViewText(R.id.row_note_subject_label, note.isMisc() ? "MISC" : abbreviateName(note.subjectName));
        row.setViewVisibility(R.id.row_note_urgent_label, note.urgent ? View.VISIBLE : View.GONE);
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
        }

        bindDeadlineBadge(row, note);
        row.setImageViewResource(R.id.row_note_check, note.completed ? drawableDotCheckedNote : drawableDotDim);

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
            row.setImageViewResource(R.id.row_note_archive, drawableIcArchive);
            Intent archiveFillIn = new Intent();
            archiveFillIn.putExtra(NotesWidgetProvider.EXTRA_NOTE_ID, note.id);
            archiveFillIn.putExtra(NotesWidgetProvider.EXTRA_SUB_ACTION, NotesWidgetProvider.SUB_ACTION_ARCHIVE);
            archiveFillIn.setData(Uri.parse("crsscheduler://note_action/archive/" + note.id));
            row.setOnClickFillInIntent(R.id.row_note_archive, archiveFillIn);
        } else {
            row.setViewVisibility(R.id.row_note_archive, View.GONE);
        }

        return row;
    }

    /** Called from NotesRemoteViewsService. */
    public static RemoteViews buildNoteGroupHeaderForAdapter(Context context, String groupKey, String label,
                                                               int count, boolean collapsed) {
        resolveThemeAssets(context);
        RemoteViews header = new RemoteViews(context.getPackageName(), layoutRowNoteGroupHeader);
        header.setTextViewText(R.id.row_group_label, label);
        header.setTextViewText(R.id.row_group_count, String.valueOf(count));
        header.setImageViewResource(R.id.row_group_chevron, drawableIcChevronDown);
        // Rotates the "down" chevron instead of using a second icon.
        header.setFloat(R.id.row_group_chevron, "setRotation", collapsed ? -90f : 0f);

        Intent fillIn = new Intent();
        fillIn.putExtra(NotesWidgetProvider.EXTRA_GROUP_KEY, groupKey);
        fillIn.putExtra(NotesWidgetProvider.EXTRA_SUB_ACTION, NotesWidgetProvider.SUB_ACTION_TOGGLE_GROUP);
        fillIn.setData(Uri.parse("crsscheduler://note_action/toggle_group/" + groupKey));
        header.setOnClickFillInIntent(R.id.row_group_header_root, fillIn);

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
            color = accent;
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
