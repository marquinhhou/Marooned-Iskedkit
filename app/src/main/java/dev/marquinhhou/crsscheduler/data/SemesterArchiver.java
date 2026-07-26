package dev.marquinhhou.crsscheduler.data;

import android.content.Context;

import java.time.LocalDate;
import java.util.List;

import dev.marquinhhou.crsscheduler.model.ClassSession;

/** Auto-archives the live schedule to history once semesterEnd passes, same as a manual Clear. */
public final class SemesterArchiver {

    private SemesterArchiver() {}

    public static void archiveIfSemesterEnded(Context context) {
        LocalDate end = SettingsStore.getSemesterEnd(context);
        if (end == null) return; // "ongoing" -- nothing to auto-archive
        if (SettingsStore.currentSemesterPhase(context) != SettingsStore.SemesterPhase.ENDED) return;
        if (end.equals(SettingsStore.getLastAutoArchivedEnd(context))) return; // already handled this end date

        List<ClassSession> current = ScheduleStore.load(context);
        if (!current.isEmpty()) {
            ScheduleHistoryStore.archive(context, current);
            ScheduleStore.clear(context);
        }
        SettingsStore.setLastAutoArchivedEnd(context, end);
    }
}
