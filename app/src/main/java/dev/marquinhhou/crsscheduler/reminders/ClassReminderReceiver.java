package dev.marquinhhou.crsscheduler.reminders;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.ScheduleStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.model.ClassSession;
import dev.marquinhhou.crsscheduler.ui.WeekScheduleActivity;
import dev.marquinhhou.crsscheduler.widget.WidgetRenderer;

/** Fires per scheduled reminder. Re-reads the schedule from disk so edits/deletions since are respected. */
public class ClassReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String classCode = intent.getStringExtra(ClassReminderScheduler.EXTRA_CLASS_CODE);
        int day = intent.getIntExtra(ClassReminderScheduler.EXTRA_DAY, -1);

        if (classCode != null && day >= 0) {
            SettingsStore.SemesterPhase phase = SettingsStore.currentSemesterPhase(context);
            boolean inSession = phase != SettingsStore.SemesterPhase.UPCOMING && phase != SettingsStore.SemesterPhase.ENDED;
            if (inSession) {
                ClassSession match = findClass(context, classCode, day);
                if (match != null) postNotification(context, match);
            }
        }

        // Re-anchor every reminder (including this one, a week out) and pick
        // up any schedule/lead-time changes, rather than just this one alarm.
        ClassReminderScheduler.rescheduleAll(context);
    }

    private ClassSession findClass(Context context, String classCode, int day) {
        List<ClassSession> schedule = ScheduleStore.load(context);
        for (ClassSession c : schedule) {
            if (c.code.equals(classCode) && c.days.contains(day)) return c;
        }
        return null;
    }

    private void postNotification(Context context, ClassSession c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationHelper.ensureChannel(context);

        int lead = SettingsStore.getReminderLeadMinutes(context);
        String title = c.name + " starts in " + lead + " min";
        String text = c.displayRoom() + " \u00B7 "
                + WidgetRenderer.minToLabel(c.start) + "\u2013" + WidgetRenderer.minToLabel(c.end);

        Intent open = new Intent(context, WeekScheduleActivity.class);
        PendingIntent contentPi = PendingIntent.getActivity(context, c.code.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(contentPi)
                .build();

        NotificationManagerCompat.from(context).notify(c.code.hashCode(), notification);
    }
}
