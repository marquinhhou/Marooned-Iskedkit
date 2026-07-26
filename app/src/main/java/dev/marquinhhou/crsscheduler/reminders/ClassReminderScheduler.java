package dev.marquinhhou.crsscheduler.reminders;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import dev.marquinhhou.crsscheduler.data.ScheduleStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.model.ClassSession;

/** One alarm per (class, day), at classStart - leadMinutes. Re-anchors weekly on each fire. */
public final class ClassReminderScheduler {

    public static final String ACTION_CLASS_REMINDER = "dev.marquinhhou.crsscheduler.CLASS_REMINDER";
    public static final String EXTRA_CLASS_CODE = "extra_class_code";
    public static final String EXTRA_DAY = "extra_day";

    private ClassReminderScheduler() {}

    /** Cancels whatever's pending, then re-schedules from the current schedule/settings. Cheap enough to call often. */
    public static void rescheduleAll(Context context) {
        cancelAll(context);

        int lead = SettingsStore.getReminderLeadMinutes(context);
        if (lead <= 0) return;

        // Reminders track the *real* semester phase, not the widgets'
        // "preview before start" toggle -- previewing is a display
        // convenience, not a guarantee those class times are actually live.
        SettingsStore.SemesterPhase phase = SettingsStore.currentSemesterPhase(context);
        if (phase == SettingsStore.SemesterPhase.UPCOMING || phase == SettingsStore.SemesterPhase.ENDED) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        List<ClassSession> schedule = ScheduleStore.load(context);
        List<Integer> requestCodes = new ArrayList<>();
        for (ClassSession c : schedule) {
            for (int day : c.days) {
                int requestCode = requestCodeFor(c.code, day);
                scheduleOne(context, am, c, day, lead, requestCode);
                requestCodes.add(requestCode);
            }
        }
        SettingsStore.setScheduledReminderRequestCodes(context, requestCodes);
    }

    public static void cancelAll(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        List<Integer> codes = SettingsStore.getScheduledReminderRequestCodes(context);
        if (am != null) {
            // Extras don't factor into PendingIntent/Intent matching, so a
            // bare cancellation intent (same requestCode + component +
            // action) matches the one originally scheduled.
            for (int code : codes) am.cancel(pendingIntentFor(context, code, null, -1));
        }
        SettingsStore.setScheduledReminderRequestCodes(context, new ArrayList<>());
    }

    private static void scheduleOne(Context context, AlarmManager am, ClassSession c, int day, int lead, int requestCode) {
        long triggerAt = nextOccurrenceMillis(day, c.start, lead);
        PendingIntent pi = pendingIntentFor(context, requestCode, c.code, day);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // No exact-alarm permission granted -- still deliver it, just
                // not to the exact minute. Better than silently not reminding.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException e) {
            // A few OEMs can revoke the exact-alarm grant behind the app's
            // back; fall back rather than crash.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    /** Wall-clock millis for the next time `dayOfWeekJs` occurs at (startMin - leadMinutes), always in the future. */
    private static long nextOccurrenceMillis(int dayOfWeekJs, int startMin, int leadMinutes) {
        int targetMinuteOfDay = startMin - leadMinutes;

        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, Math.floorDiv(targetMinuteOfDay, 60));
        target.set(Calendar.MINUTE, Math.floorMod(targetMinuteOfDay, 60));
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        int todayJs = target.get(Calendar.DAY_OF_WEEK) - 1; // Calendar.SUNDAY=1 -> JS Sun=0
        int daysAhead = Math.floorMod(dayOfWeekJs - todayJs, 7);
        target.add(Calendar.DAY_OF_YEAR, daysAhead);
        if (target.getTimeInMillis() <= System.currentTimeMillis()) {
            target.add(Calendar.DAY_OF_YEAR, 7);
        }
        return target.getTimeInMillis();
    }

    private static int requestCodeFor(String classCode, int day) {
        return (classCode + "_" + day).hashCode();
    }

    private static PendingIntent pendingIntentFor(Context context, int requestCode, String classCode, int day) {
        Intent intent = new Intent(context, ClassReminderReceiver.class);
        intent.setAction(ACTION_CLASS_REMINDER);
        if (classCode != null) {
            intent.putExtra(EXTRA_CLASS_CODE, classCode);
            intent.putExtra(EXTRA_DAY, day);
        }
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
