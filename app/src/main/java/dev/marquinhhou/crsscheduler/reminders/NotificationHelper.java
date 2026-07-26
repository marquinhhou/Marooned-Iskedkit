package dev.marquinhhou.crsscheduler.reminders;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/** One-time setup of the notification channels class/deadline reminders post to. */
public final class NotificationHelper {

    public static final String CHANNEL_ID = "class_reminders";
    public static final String CHANNEL_ID_DEADLINES = "deadline_reminders";

    private NotificationHelper() {}

    public static void ensureChannel(Context context) {
        ensure(context, CHANNEL_ID, "Class reminders", "A heads-up a few minutes before each class starts.");
    }

    public static void ensureDeadlineChannel(Context context) {
        ensure(context, CHANNEL_ID_DEADLINES, "Deadline reminders", "A heads-up before a note's deadline arrives.");
    }

    private static void ensure(Context context, String id, String name, String description) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(id) != null) return;

        NotificationChannel channel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(description);
        nm.createNotificationChannel(channel);
    }
}
