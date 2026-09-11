package dev.marquinhhou.crsscheduler.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import dev.marquinhhou.crsscheduler.data.SettingsStore;

/** Tier 2 widget: week summary, expands to a full grid when resized taller. */
public class WeekWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_TICK = "dev.marquinhhou.crsscheduler.WEEK_TICK";
    public static final String ACTION_TOGGLE_PREVIEW = "dev.marquinhhou.crsscheduler.WEEK_TOGGLE_PREVIEW";

    /** A builder throw must degrade to a minimal card -- never leave the host mid-update. */
    private android.widget.RemoteViews buildSafely(Context context, Bundle options) {
        try {
            return WidgetRenderer.buildWeekSummary(context, options);
        } catch (Throwable t) {
            android.util.Log.e("WeekWidget", "build failed", t);
            return WidgetRenderer.buildMinimalErrorWidget(context,
                    "Weekly widget hit a snag. It'll recover on its next refresh.");
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            Bundle options = appWidgetManager.getAppWidgetOptions(id);
            appWidgetManager.updateAppWidget(id, buildSafely(context, options));
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                           int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        appWidgetManager.updateAppWidget(appWidgetId, buildSafely(context, newOptions));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_TICK.equals(action)) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(context, WeekWidgetProvider.class));
            for (int id : ids) {
                Bundle options = mgr.getAppWidgetOptions(id);
                mgr.updateAppWidget(id, buildSafely(context, options));
            }
        } else if (ACTION_TOGGLE_PREVIEW.equals(action)) {
            SettingsStore.setPreviewBeforeStartEnabled(context, !SettingsStore.isPreviewBeforeStartEnabled(context));
            WidgetRefreshScheduler.updateAllWidgets(context); // refreshes both widget types + reminders
        }
    }

    @Override
    public void onEnabled(Context context) {
        WidgetRefreshScheduler.scheduleTicks(context, WeekWidgetProvider.class, ACTION_TICK);
    }

    @Override
    public void onDisabled(Context context) {
        WidgetRefreshScheduler.cancelTicks(context, WeekWidgetProvider.class, ACTION_TICK);
    }
}
