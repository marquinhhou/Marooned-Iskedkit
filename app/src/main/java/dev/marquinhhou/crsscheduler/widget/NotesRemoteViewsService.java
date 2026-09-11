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

            List<Object> built;
            try {
                built = NotesStore.isGroupedView(context) ? buildGrouped(active) : new ArrayList<>(sortForDisplay(active));
            } catch (Throwable t) {
                // Grouping is the one path exercised only in grouped mode -- if anything in it
                // throws (bad data, an unexpected null), fall back to the flat list rather than
                // leave the adapter with a stale/inconsistent item set, which is what a widget
                // host surfaces as a failed bind ("Couldn't add widget.").
                built = new ArrayList<>(sortForDisplay(active));
            }
            items = built;
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
            try {
                Object item = items.get(position);
                if (item instanceof GroupHeader) {
                    GroupHeader h = (GroupHeader) item;
                    return WidgetRenderer.buildNoteGroupHeaderForAdapter(context, h.key, h.label, h.count, h.collapsed);
                }
                boolean isLast = position == items.size() - 1;
                return WidgetRenderer.buildNoteRowForAdapter(context, (Note) item, isLast);
            } catch (Throwable t) {
                // A single bad row (e.g. a group header built from unexpected data) must not
                // take down the whole widget -- a widget host that gets an uncaught exception
                // here shows the generic "Couldn't add widget." failure instead of any content.
                return new RemoteViews(context.getPackageName(), WidgetRenderer.rowMoreIndicatorLayout(context));
            }
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            // Note row (also used for group headers now -- see buildNoteGroupHeaderForAdapter)
            // and the out-of-bounds fallback row (see getViewAt). Both are distinct layouts
            // getViewAt can return, and RemoteViewsAdapter sizes its internal view-type cache
            // off this count, so it must match reality exactly.
            return 2;
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
