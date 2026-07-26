package dev.marquinhhou.crsscheduler.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.marquinhhou.crsscheduler.widget.WidgetRefreshScheduler;

/** Alarms don't survive a reboot -- restores class reminders and widget tick alarms. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            ClassReminderScheduler.rescheduleAll(context);
            DeadlineReminderScheduler.rescheduleAll(context);
            WidgetRefreshScheduler.restoreTicksAfterBoot(context);
        }
    }
}
