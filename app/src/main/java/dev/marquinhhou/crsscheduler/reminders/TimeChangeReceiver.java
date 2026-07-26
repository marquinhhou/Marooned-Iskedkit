package dev.marquinhhou.crsscheduler.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.marquinhhou.crsscheduler.widget.WidgetRefreshScheduler;

/** Manual clock/timezone changes or day rollover can leave armed alarms targeting the wrong
 *  moment -- rebuild everything from scratch, same as BootReceiver, rather than patch in place. */
public class TimeChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)) {
            WidgetRefreshScheduler.updateAllWidgets(context);
        }
    }
}
