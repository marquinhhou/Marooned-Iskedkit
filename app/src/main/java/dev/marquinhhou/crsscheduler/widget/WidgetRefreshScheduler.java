package dev.marquinhhou.crsscheduler.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.SemesterArchiver;
import dev.marquinhhou.crsscheduler.reminders.ClassReminderScheduler;
import dev.marquinhhou.crsscheduler.reminders.DeadlineReminderScheduler;

/** Alarm scheduling for both widgets, plus a "refresh everything" used after schedule edits. */
public final class WidgetRefreshScheduler {

    private static final long REFRESH_INTERVAL_MS = 15 * 60 * 1000L;

    // Separate request code so this one-off doesn't collide with the repeating tick.
    private static final int TRANSITION_TICK_REQUEST_CODE = TodayWidgetProvider.class.getName().hashCode() ^ 0x5A5A5A5A;

    private WidgetRefreshScheduler() {}

    public static void updateAllWidgets(Context context) {
        SemesterArchiver.archiveIfSemesterEnded(context);
        ClassReminderScheduler.rescheduleAll(context);
        DeadlineReminderScheduler.rescheduleAll(context);

        AppWidgetManager mgr = AppWidgetManager.getInstance(context);

        // Every build is guarded: a throw in one widget's builder degrades to a minimal
        // card instead of crashing whichever screen triggered the refresh (or leaving the
        // launcher with a half-applied update and a blank widget).
        int[] todayIds = mgr.getAppWidgetIds(new ComponentName(context, TodayWidgetProvider.class));
        for (int id : todayIds) {
            Bundle options = mgr.getAppWidgetOptions(id);
            mgr.updateAppWidget(id, safely(context, "Today", () -> WidgetRenderer.buildToday(context, options, id)));
            mgr.notifyAppWidgetViewDataChanged(id, R.id.today_list_listview);
        }

        int[] weekIds = mgr.getAppWidgetIds(new ComponentName(context, WeekWidgetProvider.class));
        for (int id : weekIds) {
            Bundle options = mgr.getAppWidgetOptions(id);
            mgr.updateAppWidget(id, safely(context, "Weekly", () -> WidgetRenderer.buildWeekSummary(context, options)));
        }

        int[] notesIds = mgr.getAppWidgetIds(new ComponentName(context, NotesWidgetProvider.class));
        for (int id : notesIds) {
            Bundle options = mgr.getAppWidgetOptions(id);
            mgr.updateAppWidget(id, safely(context, "Notes", () -> WidgetRenderer.buildNotes(context, options, id)));
            mgr.notifyAppWidgetViewDataChanged(id, R.id.notes_list_listview);
        }
    }

    private static android.widget.RemoteViews safely(Context context, String which,
            java.util.concurrent.Callable<android.widget.RemoteViews> build) {
        try {
            return build.call();
        } catch (Throwable t) {
            android.util.Log.e("WidgetRefresh", which + " widget build failed", t);
            return WidgetRenderer.buildMinimalErrorWidget(context,
                    which + " widget hit a snag. It'll recover on its next refresh.");
        }
    }

    /** Re-arms both widgets' tick alarms after a reboot (they don't survive one) and rebuilds. */
    public static void restoreTicksAfterBoot(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        if (mgr.getAppWidgetIds(new ComponentName(context, TodayWidgetProvider.class)).length > 0) {
            scheduleTicks(context, TodayWidgetProvider.class, TodayWidgetProvider.ACTION_TICK);
        }
        if (mgr.getAppWidgetIds(new ComponentName(context, WeekWidgetProvider.class)).length > 0) {
            scheduleTicks(context, WeekWidgetProvider.class, WeekWidgetProvider.ACTION_TICK);
        }
        if (mgr.getAppWidgetIds(new ComponentName(context, NotesWidgetProvider.class)).length > 0) {
            scheduleTicks(context, NotesWidgetProvider.class, NotesWidgetProvider.ACTION_TICK);
        }
        updateAllWidgets(context);
    }

    static void scheduleTicks(Context context, Class<?> providerClass, String tickAction) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + REFRESH_INTERVAL_MS,
                REFRESH_INTERVAL_MS,
                tickPendingIntent(context, providerClass, tickAction));
    }

    static void cancelTicks(Context context, Class<?> providerClass, String tickAction) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tickPendingIntent(context, providerClass, tickAction));
    }

    private static PendingIntent tickPendingIntent(Context context, Class<?> providerClass, String tickAction) {
        Intent intent = new Intent(context, providerClass);
        intent.setAction(tickAction);
        return PendingIntent.getBroadcast(
                context, providerClass.getName().hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Rebuilds the Today widget right at its next NOW/NEXT boundary, Doze-proof. Null cancels. */
    public static void scheduleNextTransitionTick(Context context, Long targetEpochMillis) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = transitionTickPendingIntent(context);
        am.cancel(pi);
        if (targetEpochMillis == null) return;

        // +1s so it fires just after the boundary, not a moment before.
        long triggerAtElapsed = SystemClock.elapsedRealtime()
                + (targetEpochMillis - System.currentTimeMillis()) + 1000L;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pi);
        }
    }

    private static PendingIntent transitionTickPendingIntent(Context context) {
        Intent intent = new Intent(context, TodayWidgetProvider.class);
        intent.setAction(TodayWidgetProvider.ACTION_TICK);
        return PendingIntent.getBroadcast(
                context, TRANSITION_TICK_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
