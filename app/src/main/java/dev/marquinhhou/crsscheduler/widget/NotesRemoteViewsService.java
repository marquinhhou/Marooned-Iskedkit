package dev.marquinhhou.crsscheduler.widget;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.marquinhhou.crsscheduler.data.NotesStore;
import dev.marquinhhou.crsscheduler.model.Note;

/** Notes list adapter, flat or grouped-by-subject. Sort: deadline, then no-deadline, then done. */
public class NotesRemoteViewsService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    /** A collapsible subject-group header, grouped mode only. key "" = Miscellaneous. */
    static final class GroupHeader {
        final String key;
        final String label;
        final int count;
        final boolean collapsed;

        GroupHeader(String key, String label, int count, boolean collapsed) {
            this.key = key;
            this.label = label;
            this.count = count;
            this.collapsed = collapsed;
        }
    }

    private static final class Factory implements RemoteViewsFactory {
        private final Context context;
        private List<Object> items = new ArrayList<>(); // Note or GroupHeader

        Factory(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate() {}

        @Override
        public void onDataSetChanged() {
            List<Note> active = new ArrayList<>();
            for (Note n : NotesStore.load(context)) if (!n.archived) active.add(n);

            items = NotesStore.isGroupedView(context) ? buildGrouped(active) : new ArrayList<>(sortForDisplay(active));
        }

        private List<Object> buildGrouped(List<Note> active) {
            Map<String, List<Note>> bySubject = new LinkedHashMap<>();
            Map<String, String> labelByKey = new LinkedHashMap<>();
            for (Note n : active) {
                bySubject.computeIfAbsent(n.subjectCode, k -> new ArrayList<>()).add(n);
                labelByKey.putIfAbsent(n.subjectCode, n.isMisc() ? "MISCELLANEOUS" : n.subjectName);
            }

            List<String> keys = new ArrayList<>(bySubject.keySet());
            keys.sort((a, b) -> {
                boolean aMisc = a.isEmpty(), bMisc = b.isEmpty();
                if (aMisc != bMisc) return aMisc ? 1 : -1; // Miscellaneous always sorts last
                return labelByKey.get(a).compareToIgnoreCase(labelByKey.get(b));
            });

            List<Object> flattened = new ArrayList<>();
            for (String key : keys) {
                List<Note> groupNotes = bySubject.get(key);
                boolean collapsed = NotesStore.isGroupCollapsed(context, key);
                flattened.add(new GroupHeader(key, labelByKey.get(key), groupNotes.size(), collapsed));
                if (!collapsed) flattened.addAll(sortForDisplay(groupNotes));
            }
            return flattened;
        }

        private List<Note> sortForDisplay(List<Note> notes) {
            List<Note> withDeadline = new ArrayList<>();
            List<Note> withoutDeadline = new ArrayList<>();
            List<Note> completed = new ArrayList<>();
            for (Note n : notes) {
                if (n.completed) completed.add(n);
                else if (n.deadlineEpochDay != null) withDeadline.add(n);
                else withoutDeadline.add(n);
            }
            withDeadline.sort((a, b) -> {
                int cmp = Long.compare(a.deadlineEpochDay, b.deadlineEpochDay);
                if (cmp != 0) return cmp;
                int aMin = a.deadlineMinuteOfDay == null ? 0 : a.deadlineMinuteOfDay;
                int bMin = b.deadlineMinuteOfDay == null ? 0 : b.deadlineMinuteOfDay;
                return Integer.compare(aMin, bMin);
            });
            withoutDeadline.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
            completed.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));

            List<Note> ordered = new ArrayList<>(notes.size());
            ordered.addAll(withDeadline);
            ordered.addAll(withoutDeadline);
            ordered.addAll(completed);
            return ordered;
        }

        @Override
        public void onDestroy() {
            items = new ArrayList<>();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= items.size()) {
                return new RemoteViews(context.getPackageName(), WidgetRenderer.rowMoreIndicatorLayout(context));
            }
            Object item = items.get(position);
            if (item instanceof GroupHeader) {
                GroupHeader h = (GroupHeader) item;
                return WidgetRenderer.buildNoteGroupHeaderForAdapter(context, h.key, h.label, h.count, h.collapsed);
            }
            boolean isLast = position == items.size() - 1;
            return WidgetRenderer.buildNoteRowForAdapter(context, (Note) item, isLast);
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 2; // note row, group header row
        }

        @Override
        public long getItemId(int position) {
            if (position < 0 || position >= items.size()) return position;
            Object item = items.get(position);
            if (item instanceof GroupHeader) {
                // Offset from MIN_VALUE so it can't collide with a real note id.
                return Long.MIN_VALUE + Math.abs((long) ((GroupHeader) item).key.hashCode());
            }
            return ((Note) item).id;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
