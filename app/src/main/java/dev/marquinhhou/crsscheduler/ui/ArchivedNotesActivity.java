package dev.marquinhhou.crsscheduler.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.NotesStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore.ThemeFamily;
import dev.marquinhhou.crsscheduler.model.Note;
import dev.marquinhhou.crsscheduler.widget.WidgetRefreshScheduler;
import dev.marquinhhou.crsscheduler.widget.WidgetRenderer;

/** Browse, restore, or delete archived notes. */
public class ArchivedNotesActivity extends AppCompatActivity {

    private LinearLayout container;
    private TextView emptyText;
    private int layoutRowArchivedEntry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theming.applyActivityTheme(this);
        setContentView(Theming.pick(this,
                R.layout.activity_archived_notes_ge, R.layout.activity_archived_notes_ne, R.layout.activity_archived_notes_adaptive));
        layoutRowArchivedEntry = Theming.pick(this,
                R.layout.row_archived_entry_ge, R.layout.row_archived_entry_ne, R.layout.row_archived_entry_adaptive);

        container = findViewById(R.id.archived_container);
        emptyText = findViewById(R.id.archived_empty_text);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        container.removeAllViews();
        List<Note> archived = new ArrayList<>();
        for (Note n : NotesStore.load(this)) if (n.archived) archived.add(n);
        // Most recently archived first.
        archived.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        emptyText.setVisibility(archived.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Note note : archived) {
            View row = inflater.inflate(layoutRowArchivedEntry, container, false);
            TextView subjectView = row.findViewById(R.id.archived_row_subject);
            subjectView.setText(note.isMisc() ? "MISC" : WidgetRenderer.abbreviateName(note.subjectName));
            ((TextView) row.findViewById(R.id.archived_row_title)).setText(note.title);
            TextView bodyView = row.findViewById(R.id.archived_row_body);
            if (note.body.trim().isEmpty()) {
                bodyView.setVisibility(View.GONE);
            } else {
                bodyView.setVisibility(View.VISIBLE);
                bodyView.setText(note.body.trim());
            }
            View restoreBtn = row.findViewById(R.id.archived_row_restore);
            View copyBtn = row.findViewById(R.id.archived_row_copy);
            View deleteBtn = row.findViewById(R.id.archived_row_delete);
            copyBtn.setOnClickListener(v -> copyNote(note));
            restoreBtn.setOnClickListener(v -> restore(note));
            deleteBtn.setOnClickListener(v -> confirmDelete(note));
            // copy/delete were sampling to the same SURFACE tone as the row they sit inside and
            // visually disappearing into it -- same fix as everywhere else this showed up:
            // Secondary steps to SURFACE_2. The subject badge matches the widget's "MISC" badge
            // convention (inkDim, not the layout's static accent) for the same reason every other
            // chip label in the app uses inkDim rather than a one-off accent color.
            CustomThemeBackground.styleControl(this, restoreBtn, CustomThemeBackground.ControlTier.PRIMARY);
            CustomThemeBackground.styleControl(this, copyBtn, CustomThemeBackground.ControlTier.SECONDARY);
            CustomThemeBackground.styleControl(this, deleteBtn, CustomThemeBackground.ControlTier.SECONDARY);
            if (Theming.family(this) == ThemeFamily.CUSTOM) {
                subjectView.setTextColor(Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim));
            }
            container.addView(row);
        }
        CustomThemeBackground.apply(this);
    }

    private void copyNote(Note note) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Note", note.toPlainText()));
        Toast.makeText(this, "Copied to clipboard.", Toast.LENGTH_SHORT).show();
    }

    private void restore(Note note) {
        NotesStore.setArchived(this, note.id, false);
        WidgetRefreshScheduler.updateAllWidgets(this);
        Toast.makeText(this, "Restored.", Toast.LENGTH_SHORT).show();
        render();
    }

    private void confirmDelete(Note note) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delete this note permanently?")
                .setMessage("This can't be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    NotesStore.delete(this, note.id);
                    WidgetRefreshScheduler.updateAllWidgets(this);
                    Toast.makeText(this, "Deleted.", Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
        CustomThemeBackground.applyToDialog(dialog);
        CustomThemeBackground.styleDialogButtons(this, dialog);
    }
}
