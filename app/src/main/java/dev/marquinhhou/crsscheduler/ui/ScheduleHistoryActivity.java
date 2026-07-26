package dev.marquinhhou.crsscheduler.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.ScheduleHistoryStore;
import dev.marquinhhou.crsscheduler.data.ScheduleStore;
import dev.marquinhhou.crsscheduler.model.ClassSession;
import dev.marquinhhou.crsscheduler.model.ScheduleSnapshot;
import dev.marquinhhou.crsscheduler.widget.WidgetRefreshScheduler;

/** Browse, reactivate, or delete previously-active schedules (auto-archived by ConfigureActivity). */
public class ScheduleHistoryActivity extends AppCompatActivity {

    private LinearLayout container;
    private TextView emptyText;
    private int layoutRowHistoryEntry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theming.applyActivityTheme(this);
        setContentView(Theming.pick(this,
                R.layout.activity_schedule_history_ge, R.layout.activity_schedule_history_ne, R.layout.activity_schedule_history_adaptive));
        layoutRowHistoryEntry = Theming.pick(this,
                R.layout.row_history_entry_ge, R.layout.row_history_entry_ne, R.layout.row_history_entry_adaptive);

        container = findViewById(R.id.history_container);
        emptyText = findViewById(R.id.history_empty_text);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        container.removeAllViews();
        List<ScheduleSnapshot> all = ScheduleHistoryStore.loadAll(this);
        emptyText.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (ScheduleSnapshot snap : all) {
            View row = inflater.inflate(layoutRowHistoryEntry, container, false);
            ((TextView) row.findViewById(R.id.history_row_label)).setText(snap.displayName());
            row.findViewById(R.id.history_row_reactivate).setOnClickListener(v -> confirmReactivate(snap));
            row.findViewById(R.id.history_row_rename).setOnClickListener(v -> promptRename(snap));
            row.findViewById(R.id.history_row_delete).setOnClickListener(v -> confirmDelete(snap));
            container.addView(row);
        }
    }

    private void promptRename(ScheduleSnapshot snap) {
        EditText input = new EditText(this);
        input.setText(snap.customName != null ? snap.customName : "");
        input.setHint(snap.label);
        input.setSelection(input.getText().length());
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad / 2, pad, pad / 2);

        new AlertDialog.Builder(this)
                .setTitle("Rename this saved schedule")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    ScheduleHistoryStore.rename(this, snap.id, input.getText().toString());
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmReactivate(ScheduleSnapshot snap) {
        new AlertDialog.Builder(this)
                .setTitle("Reactivate this schedule?")
                .setMessage("This replaces your currently active schedule. The one it replaces gets saved here too, so nothing is lost.")
                .setPositiveButton("Reactivate", (d, w) -> reactivate(snap))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void reactivate(ScheduleSnapshot snap) {
        List<ClassSession> current = ScheduleStore.load(this);
        if (!current.isEmpty()) {
            ScheduleHistoryStore.archive(this, current);
        }
        ScheduleHistoryStore.delete(this, snap.id);
        ScheduleStore.save(this, snap.classes);
        WidgetRefreshScheduler.updateAllWidgets(this);
        Toast.makeText(this, "Reactivated \u2014 " + snap.classes.size() + " classes.", Toast.LENGTH_SHORT).show();
        render();
    }

    private void confirmDelete(ScheduleSnapshot snap) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this saved schedule?")
                .setMessage("This can't be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    ScheduleHistoryStore.delete(this, snap.id);
                    Toast.makeText(this, "Deleted.", Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
