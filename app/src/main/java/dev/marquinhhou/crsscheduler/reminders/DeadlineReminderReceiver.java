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

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.NotesStore;
import dev.marquinhhou.crsscheduler.model.Note;
import dev.marquinhhou.crsscheduler.ui.NoteEditActivity;
import dev.marquinhhou.crsscheduler.widget.WidgetRenderer;

/** Fires per scheduled deadline reminder. Re-reads the note from disk so edits since are respected. */
public class DeadlineReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long noteId = intent.getLongExtra(DeadlineReminderScheduler.EXTRA_NOTE_ID, -1);
        if (noteId >= 0) {
            Note note = NotesStore.find(context, noteId);
            if (note != null && !note.completed && !note.archived && note.deadlineEpochDay != null) {
                postNotification(context, note);
            }
        }

        // Rebuilds every deadline alarm from the current notes/settings, same as class reminders.
        DeadlineReminderScheduler.rescheduleAll(context);
    }

    private void postNotification(Context context, Note note) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationHelper.ensureDeadlineChannel(context);

        String subject = note.isMisc() ? "Miscellaneous" : note.subjectName;
        String title = note.title + " is due soon";
        String text = subject + " \u00B7 " + WidgetRenderer.absoluteDeadlineText(note);

        Intent open = new Intent(context, NoteEditActivity.class);
        open.putExtra(NoteEditActivity.EXTRA_NOTE_ID, note.id);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int notificationId = Long.hashCode(note.id);
        PendingIntent contentPi = PendingIntent.getActivity(context, notificationId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID_DEADLINES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(contentPi)
                .build();

        NotificationManagerCompat.from(context).notify(notificationId, notification);
    }
}
