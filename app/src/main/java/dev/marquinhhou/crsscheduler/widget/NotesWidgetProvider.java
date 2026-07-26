package dev.marquinhhou.crsscheduler.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.NotesStore;
import dev.marquinhhou.crsscheduler.reminders.DeadlineReminderScheduler;
import dev.marquinhhou.crsscheduler.ui.NoteEditActivity;

/** Third widget: subject notes/deadlines/to-dos. Row taps share one PendingIntent template. */
public class NotesWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_TICK = "dev.marquinhhou.crsscheduler.NOTES_TICK";
    public static final String ACTION_ROW_TAP = "dev.marquinhhou.crsscheduler.NOTES_ROW_TAP";
    public static final String ACTION_TOGGLE_VIEW = "dev.marquinhhou.crsscheduler.NOTES_TOGGLE_VIEW";
    public static final String ACTION_COPY_ALL = "dev.marquinhhou.crsscheduler.NOTES_COPY_ALL";

    public static final String EXTRA_NOTE_ID = "note_id";
    public static final String EXTRA_GROUP_KEY = "group_key";
    public static final String EXTRA_SUB_ACTION = "sub_action";
    public static final String SUB_ACTION_OPEN = "open";
    public static final String SUB_ACTION_TOGGLE = "toggle";
    public static final String SUB_ACTION_ARCHIVE = "archive";
    public static final String SUB_ACTION_TOGGLE_GROUP = "toggle_group";

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
        updateOne(context, appWidgetManager, appWidgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_TICK.equals(action)) {
            refreshAll(context);
        } else if (ACTION_ROW_TAP.equals(action)) {
            handleRowTap(context, intent);
        } else if (ACTION_TOGGLE_VIEW.equals(action)) {
            NotesStore.setGroupedView(context, !NotesStore.isGroupedView(context));
            refreshAll(context);
        } else if (ACTION_COPY_ALL.equals(action)) {
            String text = NotesStore.formatAllAsText(context);
            if (text.isEmpty()) {
                Toast.makeText(context, "No notes to copy yet.", Toast.LENGTH_SHORT).show();
            } else {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("CRS Scheduler notes", text));
                Toast.makeText(context, "Notes copied to clipboard.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleRowTap(Context context, Intent intent) {
        String subAction = intent.getStringExtra(EXTRA_SUB_ACTION);
        if (subAction == null) return;
        switch (subAction) {
            case SUB_ACTION_TOGGLE: {
                long noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1);
                if (noteId < 0) return;
                NotesStore.toggleCompleted(context, noteId);
                DeadlineReminderScheduler.rescheduleAll(context);
                refreshAll(context);
                break;
            }
            case SUB_ACTION_ARCHIVE: {
                long noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1);
                if (noteId < 0) return;
                NotesStore.setArchived(context, noteId, true);
                DeadlineReminderScheduler.rescheduleAll(context);
                refreshAll(context);
                break;
            }
            case SUB_ACTION_TOGGLE_GROUP: {
                String groupKey = intent.getStringExtra(EXTRA_GROUP_KEY);
                if (groupKey == null) return;
                NotesStore.setGroupCollapsed(context, groupKey, !NotesStore.isGroupCollapsed(context, groupKey));
                refreshAll(context);
                break;
            }
            case SUB_ACTION_OPEN: {
                long noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1);
                if (noteId < 0) return;
                Intent edit = new Intent(context, NoteEditActivity.class);
                edit.putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId);
                edit.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(edit);
                break;
            }
        }
    }

    private void refreshAll(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(context, NotesWidgetProvider.class));
        for (int id : ids) updateOne(context, mgr, id);
    }

    private void updateOne(Context context, AppWidgetManager appWidgetManager, int id) {
        Bundle options = appWidgetManager.getAppWidgetOptions(id);
        appWidgetManager.updateAppWidget(id, WidgetRenderer.buildNotes(context, options, id));
        appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.notes_list_listview);
    }

    @Override
    public void onEnabled(Context context) {
        WidgetRefreshScheduler.scheduleTicks(context, NotesWidgetProvider.class, ACTION_TICK);
    }

    @Override
    public void onDisabled(Context context) {
        WidgetRefreshScheduler.cancelTicks(context, NotesWidgetProvider.class, ACTION_TICK);
    }
}
