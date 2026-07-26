package dev.marquinhhou.crsscheduler.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.SettingsStore;

/** Tier 1 widget: today's classes. Sized to stack with WeekWidgetProvider in launchers. */
public class TodayWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_TICK = "dev.marquinhhou.crsscheduler.TODAY_TICK";
    public static final String ACTION_TOGGLE_PREVIEW = "dev.marquinhhou.crsscheduler.TODAY_TOGGLE_PREVIEW";
    public static final String ACTION_TOGGLE_TOMORROW = "dev.marquinhhou.crsscheduler.TODAY_TOGGLE_TOMORROW";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateOne(context, appWidgetManager, id);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                           int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        appWidgetManager.updateAppWidget(appWidgetId, WidgetRenderer.buildToday(context, newOptions, appWidgetId));
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.today_list_listview);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_TICK.equals(action)) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(context, TodayWidgetProvider.class));
            for (int id : ids) {
                updateOne(context, mgr, id);
            }
        } else if (ACTION_TOGGLE_PREVIEW.equals(action)) {
            SettingsStore.setPreviewBeforeStartEnabled(context, !SettingsStore.isPreviewBeforeStartEnabled(context));
            WidgetRefreshScheduler.updateAllWidgets(context); // refreshes both widget types + reminders
        } else if (ACTION_TOGGLE_TOMORROW.equals(action)) {
            SettingsStore.setShowTomorrowEnabled(context, !SettingsStore.isShowTomorrowEnabled(context));
            WidgetRefreshScheduler.updateAllWidgets(context);
        }
    }

    private void updateOne(Context context, AppWidgetManager appWidgetManager, int id) {
        Bundle options = appWidgetManager.getAppWidgetOptions(id);
        appWidgetManager.updateAppWidget(id, WidgetRenderer.buildToday(context, options, id));
        appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.today_list_listview);
    }

    @Override
    public void onEnabled(Context context) {
        WidgetRefreshScheduler.scheduleTicks(context, TodayWidgetProvider.class, ACTION_TICK);
    }

    @Override
    public void onDisabled(Context context) {
        WidgetRefreshScheduler.cancelTicks(context, TodayWidgetProvider.class, ACTION_TICK);
    }
}
