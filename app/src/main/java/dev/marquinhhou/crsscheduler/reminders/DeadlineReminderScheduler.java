package dev.marquinhhou.crsscheduler.reminders;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import dev.marquinhhou.crsscheduler.data.NotesStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.model.Note;

/** One alarm per active note with a deadline, at deadline - leadMinutes. One-shot, not weekly. */
public final class DeadlineReminderScheduler {

    public static final String ACTION_DEADLINE_REMINDER = "dev.marquinhhou.crsscheduler.DEADLINE_REMINDER";
    public static final String EXTRA_NOTE_ID = "extra_note_id";

    // A date-only deadline is treated as due end-of-day, same default the time picker seeds.
    private static final LocalTime DEFAULT_DEADLINE_TIME = LocalTime.of(23, 59);

    private DeadlineReminderScheduler() {}

    /** Cancels whatever's pending, then re-schedules from each note's own reminder setting. Cheap enough to call often. */
    public static void rescheduleAll(Context context) {
        cancelAll(context);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        long now = System.currentTimeMillis();
        List<Integer> requestCodes = new ArrayList<>();
        for (Note note : NotesStore.load(context)) {
            if (note.completed || note.archived || note.deadlineEpochDay == null || note.reminderLeadMinutes <= 0) continue;

            long triggerAt = deadlineMillis(note) - note.reminderLeadMinutes * 60_000L;
            if (triggerAt <= now) continue; // already due or past its lead window -- nothing to arm

            int requestCode = requestCodeFor(note.id);
            scheduleOne(context, am, note.id, triggerAt, requestCode);
            requestCodes.add(requestCode);
        }
        SettingsStore.setScheduledDeadlineReminderRequestCodes(context, requestCodes);
    }

    public static void cancelAll(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        List<Integer> codes = SettingsStore.getScheduledDeadlineReminderRequestCodes(context);
        if (am != null) {
            for (int code : codes) am.cancel(pendingIntentFor(context, code, -1));
        }
        SettingsStore.setScheduledDeadlineReminderRequestCodes(context, new ArrayList<>());
    }

    private static void scheduleOne(Context context, AlarmManager am, long noteId, long triggerAt, int requestCode) {
        PendingIntent pi = pendingIntentFor(context, requestCode, noteId);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException e) {
            // A few OEMs can revoke the exact-alarm grant behind the app's back.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    /** Wall-clock millis the deadline itself falls at, defaulting a date-only deadline to end-of-day. */
    private static long deadlineMillis(Note note) {
        LocalDate date = LocalDate.ofEpochDay(note.deadlineEpochDay);
        LocalTime time = note.deadlineMinuteOfDay != null
                ? LocalTime.of(note.deadlineMinuteOfDay / 60, note.deadlineMinuteOfDay % 60)
                : DEFAULT_DEADLINE_TIME;
        return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static int requestCodeFor(long noteId) {
        return Long.hashCode(noteId);
    }

    private static PendingIntent pendingIntentFor(Context context, int requestCode, long noteId) {
        Intent intent = new Intent(context, DeadlineReminderReceiver.class);
        intent.setAction(ACTION_DEADLINE_REMINDER);
        if (noteId >= 0) intent.putExtra(EXTRA_NOTE_ID, noteId);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
