package dev.marquinhhou.crsscheduler.widget;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import dev.marquinhhou.crsscheduler.data.ScheduleStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.model.ClassSession;

/** Today's-classes list adapter -- rows fill in against the ListView's PendingIntent template. */
public class TodayClassesRemoteViewsService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    private static final class Factory implements RemoteViewsFactory {
        private final Context context;

        /**
         * Bundles the today's-classes list together with the index of whichever one of them is
         * "now", as a single object. onDataSetChanged() used to write these as two separate
         * instance fields (todays, then ongoingIndex right after); RemoteViewsFactory methods
         * are Binder callbacks and the framework is free to run getViewAt() concurrently with a
         * refresh, so a reader could see the NEW list paired with the OLD ongoing index (or vice
         * versa) -- two field writes are never atomic together. That mismatch is exactly what
         * put the "now" dot on the wrong row in the screenshot report: the badge is decided by
         * `position == ongoingIndex`, and ongoingIndex was computed for a list that wasn't the
         * one being drawn. Replacing the pair with one immutable snapshot behind a single
         * volatile reference makes the swap atomic (one write in onDataSetChanged(), one read at
         * the top of each getCount()/getViewAt() call) so every row render sees a matching
         * (list, index) pair from the same refresh, never a torn mix of two different ones.
         */
        private static final class Snapshot {
            final List<ClassSession> todays;
            final int ongoingIndex;
            Snapshot(List<ClassSession> todays, int ongoingIndex) {
                this.todays = todays;
                this.ongoingIndex = ongoingIndex;
            }
        }

        private volatile Snapshot snapshot = new Snapshot(new ArrayList<>(), -1);

        Factory(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate() {}

        @Override
        public void onDataSetChanged() {
            SettingsStore.SemesterPhase phase = SettingsStore.effectiveDisplayPhase(context);
            if (phase == SettingsStore.SemesterPhase.UPCOMING || phase == SettingsStore.SemesterPhase.ENDED) {
                snapshot = new Snapshot(new ArrayList<>(), -1);
                return;
            }

            boolean showTomorrow = SettingsStore.isShowTomorrowEnabled(context);
            List<ClassSession> schedule = ScheduleStore.load(context);
            Calendar now = Calendar.getInstance();
            int today = WidgetRenderer.calendarDayToJs(now.get(Calendar.DAY_OF_WEEK));
            int targetDay = showTomorrow ? (today + 1) % 7 : today;
            int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

            List<ClassSession> list = new ArrayList<>();
            for (ClassSession c : schedule) if (c.days.contains(targetDay)) list.add(c);
            list.sort((a, b) -> Integer.compare(a.start, b.start));

            // "Ongoing" only makes sense for today's own list -- a class
            // shown because it's tomorrow can never be the one happening now.
            int found = -1;
            if (!showTomorrow) {
                for (int i = 0; i < list.size(); i++) {
                    ClassSession c = list.get(i);
                    if (nowMin >= c.start && nowMin < c.end) { found = i; break; }
                }
            }
            snapshot = new Snapshot(list, found); // Single atomic publish -- see Snapshot's Javadoc.
        }

        @Override
        public void onDestroy() {
            snapshot = new Snapshot(new ArrayList<>(), -1);
        }

        @Override
        public int getCount() {
            return snapshot.todays.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            Snapshot s = snapshot; // One read -- list and index always agree, see Snapshot's Javadoc.
            if (position < 0 || position >= s.todays.size()) {
                return new RemoteViews(context.getPackageName(), WidgetRenderer.rowMoreIndicatorLayout(context));
            }
            try {
                ClassSession c = s.todays.get(position);
                boolean isLast = position == s.todays.size() - 1;
                return WidgetRenderer.buildClassRowForAdapter(context, c, position == s.ongoingIndex, position, isLast);
            } catch (Throwable t) {
                return new RemoteViews(context.getPackageName(), WidgetRenderer.rowMoreIndicatorLayout(context));
            }
        }

        @Override
        public RemoteViews getLoadingView() {
            return null; // default platform placeholder is fine while a row loads
        }

        @Override
        public int getViewTypeCount() {
            // Class row, plus the out-of-bounds fallback row (see getViewAt) -- both are
            // distinct layouts getViewAt can return. See NotesRemoteViewsService for why
            // undercounting this is a real bug, not just a cosmetic mismatch.
            return 2;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
