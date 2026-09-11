package dev.marquinhhou.crsscheduler.ui;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.NotesStore;
import dev.marquinhhou.crsscheduler.data.ScheduleStore;
import dev.marquinhhou.crsscheduler.model.ClassSession;
import dev.marquinhhou.crsscheduler.model.Note;
import dev.marquinhhou.crsscheduler.widget.WidgetRefreshScheduler;
import dev.marquinhhou.crsscheduler.widget.WidgetRenderer;

/** Add/edit screen for a note. Subject chips come from ScheduleStore, plus MISC. */
public class NoteEditActivity extends AppCompatActivity {

    public static final String EXTRA_NOTE_ID = "note_id";

    private Note editing; // null in create mode

    private LinearLayout subjectChipContainer;
    private EditText inputTitle;
    private EditText inputBody;
    private TextView btnSetDeadline;
    private View btnClearDeadline;
    private TextView btnSetTime;
    private View btnClearTime;
    private View reminderSection;
    private TextView[] reminderChips;
    private static final int[] REMINDER_LEAD_OPTIONS = {0, 60, 180, 1440, 4320}; // off/1h/3h/1d/3d
    private TextView chipMarkDone;
    private TextView chipUrgent;
    private View btnDelete;
    private View subjectInfoContainer;
    private TextView subjectInfoName, subjectInfoCode, subjectInfoSchedule, subjectInfoLocation;
    private LinearLayout attachmentsContainer;
    private View attachmentsLabel, btnAttachFile;

    private final List<TextView> subjectChips = new ArrayList<>();
    private final List<String[]> subjectOptions = new ArrayList<>(); // [0]=code, [1]=name; index 0 is always MISC ("","")
    private final List<ClassSession> subjectSessions = new ArrayList<>(); // index-matched with subjectOptions; index 0 is null
    private int selectedSubjectIndex = 0;
    private LocalDate deadlineDate;
    private LocalTime deadlineTime;
    private int reminderLeadMinutes;
    private boolean urgent;
    private boolean completed;

    private int drawableChipFilledBg, drawableChipOutlineBg;
    private int colorBg, colorInkDim, colorInk, colorGreen, colorError;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, "Notifications are off for this app, so the reminder won't show. You can allow them from system Settings any time.", Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String[]> attachmentPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) onAttachmentPicked(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theming.applyActivityTheme(this);
        setContentView(Theming.pick(this,
                R.layout.activity_note_edit_ge, R.layout.activity_note_edit_ne, R.layout.activity_note_edit_adaptive));
        resolveThemeAssets();

        long noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, -1);
        editing = noteId >= 0 ? NotesStore.find(this, noteId) : null;

        subjectChipContainer = findViewById(R.id.subject_chip_container);
        inputTitle = findViewById(R.id.input_title);
        inputBody = findViewById(R.id.input_body);
        btnSetDeadline = findViewById(R.id.btn_set_deadline);
        btnClearDeadline = findViewById(R.id.btn_clear_deadline);
        btnSetTime = findViewById(R.id.btn_set_time);
        btnClearTime = findViewById(R.id.btn_clear_time);
        reminderSection = findViewById(R.id.reminder_section);
        reminderChips = new TextView[]{
                findViewById(R.id.chip_reminder_off),
                findViewById(R.id.chip_reminder_1h),
                findViewById(R.id.chip_reminder_3h),
                findViewById(R.id.chip_reminder_1d),
                findViewById(R.id.chip_reminder_3d),
        };
        chipMarkDone = findViewById(R.id.chip_mark_done);
        chipUrgent = findViewById(R.id.chip_urgent);
        btnDelete = findViewById(R.id.btn_delete);
        subjectInfoContainer = findViewById(R.id.subject_info_container);
        subjectInfoName = findViewById(R.id.subject_info_name);
        subjectInfoCode = findViewById(R.id.subject_info_code);
        subjectInfoSchedule = findViewById(R.id.subject_info_schedule);
        subjectInfoLocation = findViewById(R.id.subject_info_location);
        attachmentsContainer = findViewById(R.id.note_attachments_container);
        attachmentsLabel = findViewById(R.id.note_attachments_label);
        btnAttachFile = findViewById(R.id.btn_attach_file);
        btnAttachFile.setOnClickListener(v -> attachmentPicker.launch(new String[]{"*/*"}));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save).setOnClickListener(v -> onSaveTapped());
        findViewById(R.id.btn_copy).setOnClickListener(v -> onCopyTapped());
        btnSetDeadline.setOnClickListener(v -> showDatePicker());
        btnClearDeadline.setOnClickListener(v -> {
            deadlineDate = null;
            deadlineTime = null; // a time without a date doesn't mean anything
            reminderLeadMinutes = 0; // ...and neither does a reminder
            bindDeadline();
            bindReminderChips();
        });
        btnSetTime.setOnClickListener(v -> showTimePicker());
        btnClearTime.setOnClickListener(v -> {
            deadlineTime = null;
            bindDeadline();
        });
        for (int i = 0; i < reminderChips.length; i++) {
            int minutes = REMINDER_LEAD_OPTIONS[i];
            reminderChips[i].setOnClickListener(v -> {
                reminderLeadMinutes = minutes;
                bindReminderChips();
                if (minutes > 0) ensureReminderPermissions();
            });
        }
        chipMarkDone.setOnClickListener(v -> {
            completed = !completed;
            bindMarkDoneChip();
        });
        chipUrgent.setOnClickListener(v -> {
            urgent = !urgent;
            bindUrgentChip();
        });

        buildSubjectChips();

        if (editing != null) {
            ((TextView) findViewById(R.id.edit_title_label)).setText("EDIT NOTE");
            inputTitle.setText(editing.title);
            inputBody.setText(editing.body);
            deadlineDate = editing.deadlineEpochDay != null ? LocalDate.ofEpochDay(editing.deadlineEpochDay) : null;
            deadlineTime = editing.deadlineMinuteOfDay != null
                    ? LocalTime.of(editing.deadlineMinuteOfDay / 60, editing.deadlineMinuteOfDay % 60) : null;
            reminderLeadMinutes = editing.reminderLeadMinutes;
            completed = editing.completed;
            urgent = editing.urgent;
            selectedSubjectIndex = indexOfSubject(editing.subjectCode);
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> confirmDelete());
            attachmentsLabel.setVisibility(View.VISIBLE);
            attachmentsContainer.setVisibility(View.VISIBLE);
            btnAttachFile.setVisibility(View.VISIBLE);
            renderAttachments();
        } else {
            btnDelete.setVisibility(View.GONE);
            // A new note has no id yet to key attachments off -- save first, then attach.
            attachmentsLabel.setVisibility(View.GONE);
            attachmentsContainer.setVisibility(View.GONE);
            btnAttachFile.setVisibility(View.GONE);
        }

        bindSubjectChipSelection();
        bindSubjectInfo();
        bindDeadline();
        bindReminderChips();
        bindMarkDoneChip();
        bindUrgentChip();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CustomThemeBackground.apply(this);
        // Explicit Primary/Secondary/Quiet pass so the editor's buttons match every other
        // screen instead of relying on retintTree's generic sampling (or showing stock
        // system button chrome inside otherwise-frosted cards).
        CustomThemeBackground.styleControl(this, findViewById(R.id.btn_save), CustomThemeBackground.ControlTier.PRIMARY);
        CustomThemeBackground.styleControl(this, btnDelete, CustomThemeBackground.ControlTier.SECONDARY);
        CustomThemeBackground.styleControl(this, btnAttachFile, CustomThemeBackground.ControlTier.SECONDARY);
        CustomThemeBackground.styleControl(this, btnSetDeadline, CustomThemeBackground.ControlTier.SECONDARY);
        // Clearing a deadline is a destructive/undo-able action, same family as CLEAR /
        // CLEAR DATES elsewhere in the app -- QUIET (a neutral ink wash) didn't distinguish
        // it from a normal secondary control at all, which is what read as "the X button
        // is broken": nothing about it signaled "this removes something."
        CustomThemeBackground.styleControl(this, btnClearDeadline, CustomThemeBackground.ControlTier.DESTRUCTIVE);
        CustomThemeBackground.styleControl(this, btnSetTime, CustomThemeBackground.ControlTier.SECONDARY);
        CustomThemeBackground.styleControl(this, btnClearTime, CustomThemeBackground.ControlTier.QUIET);
        CustomThemeBackground.styleControl(this, findViewById(R.id.btn_copy), CustomThemeBackground.ControlTier.SECONDARY);
    }

    private void resolveThemeAssets() {
        drawableChipFilledBg = Theming.pick(this, R.drawable.chip_filled_bg_ge, R.drawable.chip_filled_bg_ne, R.drawable.chip_filled_bg_adaptive);
        drawableChipOutlineBg = Theming.pick(this, R.drawable.chip_outline_bg_ge, R.drawable.chip_outline_bg_ne, R.drawable.chip_outline_bg_adaptive);
        colorBg = Theming.color(this, R.color.ge_bg, R.color.ne_bg, R.color.adaptive_bg);
        colorInk = Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink);
        colorInkDim = Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim);
        colorGreen = Theming.color(this, R.color.ge_green, R.color.ne_green, R.color.adaptive_green);
        colorError = Theming.color(this, R.color.ge_error, R.color.ne_error, R.color.adaptive_error);
    }

    /** MISC first, then one chip per class code -- lec/disc/etc. already have their own. */
    private void buildSubjectChips() {
        subjectOptions.add(new String[]{"", ""});
        subjectSessions.add(null);
        Map<String, ClassSession> byCode = new LinkedHashMap<>();
        for (ClassSession c : ScheduleStore.load(this)) {
            if (!byCode.containsKey(c.code)) byCode.put(c.code, c);
        }
        for (ClassSession c : byCode.values()) {
            subjectOptions.add(new String[]{c.code, c.name});
            subjectSessions.add(c);
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < subjectOptions.size(); i++) {
            String[] opt = subjectOptions.get(i);
            TextView chip = (TextView) inflater.inflate(R.layout.chip_subject_option, subjectChipContainer, false);
            chip.setText(i == 0 ? "MISC" : WidgetRenderer.abbreviateName(opt[1]));
            int index = i;
            chip.setOnClickListener(v -> {
                selectedSubjectIndex = index;
                bindSubjectChipSelection();
                bindSubjectInfo();
            });
            subjectChips.add(chip);
            subjectChipContainer.addView(chip);
        }
    }

    private int indexOfSubject(String subjectCode) {
        if (subjectCode == null || subjectCode.isEmpty()) return 0;
        for (int i = 1; i < subjectOptions.size(); i++) {
            if (subjectOptions.get(i)[0].equals(subjectCode)) return i;
        }
        return 0; // the class this note pointed to is no longer in the active schedule -- falls back to MISC
    }

    private void bindSubjectChipSelection() {
        for (int i = 0; i < subjectChips.size(); i++) {
            TextView chip = subjectChips.get(i);
            boolean selected = i == selectedSubjectIndex;
            chip.setBackgroundResource(selected ? drawableChipFilledBg : drawableChipOutlineBg);
            chip.setTextColor(selected ? colorBg : colorInkDim);
            CustomThemeBackground.tintChip(this, chip, selected);
        }
    }

    /** Shows the selected class's detail. Hidden for MISC. */
    private void bindSubjectInfo() {
        ClassSession c = subjectSessions.get(selectedSubjectIndex);
        if (c == null) {
            subjectInfoContainer.setVisibility(View.GONE);
            return;
        }
        subjectInfoContainer.setVisibility(View.VISIBLE);
        subjectInfoName.setText(c.name);

        String units = (c.creditsExcluded ? "(" + c.credits + " units, excluded)" : c.credits + " units");
        subjectInfoCode.setText("Class Code " + c.code + " \u00b7 " + units);

        StringBuilder days = new StringBuilder();
        for (int d : c.days) {
            if (days.length() > 0) days.append("/");
            days.append(WidgetRenderer.DAY_LABELS[d]);
        }
        String type = c.type == null || c.type.isEmpty() ? "" : c.type.toUpperCase(Locale.US) + " \u00b7 ";
        subjectInfoSchedule.setText(type + days + " \u00b7 " + WidgetRenderer.minToLabel(c.start) + "\u2013" + WidgetRenderer.minToLabel(c.end));

        String room = c.displayRoom();
        String instructor = c.instructor == null ? "" : c.instructor.trim();
        String location;
        if (!room.isEmpty() && !instructor.isEmpty()) location = room + " \u00b7 " + instructor;
        else if (!room.isEmpty()) location = room;
        else location = instructor;
        subjectInfoLocation.setText(location);
        subjectInfoLocation.setVisibility(location.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showDatePicker() {
        LocalDate seed = deadlineDate != null ? deadlineDate : LocalDate.now();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            deadlineDate = LocalDate.of(year, month + 1, dayOfMonth);
            bindDeadline();
        }, seed.getYear(), seed.getMonthValue() - 1, seed.getDayOfMonth()).show();
    }

    private void showTimePicker() {
        LocalTime seed = deadlineTime != null ? deadlineTime : LocalTime.of(23, 59);
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            deadlineTime = LocalTime.of(hourOfDay, minute);
            bindDeadline();
        }, seed.getHour(), seed.getMinute(), false).show();
    }

    private void bindDeadline() {
        if (deadlineDate == null) {
            btnSetDeadline.setText("SET DEADLINE");
            btnSetDeadline.setTextColor(colorInkDim);
            btnClearDeadline.setVisibility(View.GONE);
            btnSetTime.setVisibility(View.GONE);
            btnClearTime.setVisibility(View.GONE);
            reminderSection.setVisibility(View.GONE);
            return;
        }

        btnSetDeadline.setText(deadlineDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)).toUpperCase(Locale.US));
        btnSetDeadline.setTextColor(colorInk);
        btnClearDeadline.setVisibility(View.VISIBLE);
        btnSetTime.setVisibility(View.VISIBLE);
        reminderSection.setVisibility(View.VISIBLE);

        if (deadlineTime == null) {
            btnSetTime.setText("SET TIME");
            btnSetTime.setTextColor(colorInkDim);
            btnClearTime.setVisibility(View.GONE);
        } else {
            btnSetTime.setText(deadlineTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)).toUpperCase(Locale.US));
            btnSetTime.setTextColor(colorInk);
            btnClearTime.setVisibility(View.VISIBLE);
        }
    }

    /** Optional, per-note -- off unless the person explicitly picks a lead time. */
    private void bindReminderChips() {
        for (int i = 0; i < reminderChips.length; i++) {
            boolean active = REMINDER_LEAD_OPTIONS[i] == reminderLeadMinutes;
            reminderChips[i].setBackgroundResource(active ? drawableChipFilledBg : drawableChipOutlineBg);
            reminderChips[i].setTextColor(active ? colorBg : colorInkDim);
            CustomThemeBackground.tintChip(this, reminderChips[i], active);
        }
    }

    /** Requests whatever's missing for the reminder to actually fire: POST_NOTIFICATIONS (33+) and, best-effort, exact-alarm scheduling (31+). */
    private void ensureReminderPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    // No such settings screen on this OEM build -- the reminder
                    // will still fire, just not necessarily to the exact minute.
                }
            }
        }
    }

    private void bindMarkDoneChip() {
        chipMarkDone.setBackgroundResource(completed ? drawableChipFilledBg : drawableChipOutlineBg);
        chipMarkDone.setTextColor(completed ? colorBg : colorInkDim);
        chipMarkDone.setText(completed ? "\u2713 MARKED AS DONE" : "MARK AS DONE");
        CustomThemeBackground.tintChip(this, chipMarkDone, completed);
    }

    /** Uses the error color -- urgency is a warning, not a selection. */
    private void bindUrgentChip() {
        chipUrgent.setBackgroundResource(urgent ? drawableChipFilledBg : drawableChipOutlineBg);
        if (urgent) {
            // mutate() first, so the tint doesn't leak to other chips.
            chipUrgent.getBackground().mutate().setTint(colorError);
            chipUrgent.setTextColor(colorBg);
        } else {
            chipUrgent.setTextColor(colorError);
        }
        chipUrgent.setText(urgent ? "\u26A0 URGENT" : "MARK AS URGENT");
    }

    private void onSaveTapped() {
        String title = inputTitle.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "Give the note a title first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String body = inputBody.getText().toString().trim();
        String[] subject = subjectOptions.get(selectedSubjectIndex);
        Long deadlineEpochDay = deadlineDate != null ? deadlineDate.toEpochDay() : null;
        Integer deadlineMinuteOfDay = deadlineTime != null ? deadlineTime.getHour() * 60 + deadlineTime.getMinute() : null;

        if (editing != null) {
            NotesStore.update(this, editing.withEdits(subject[0], subject[1], title, body,
                    deadlineEpochDay, deadlineMinuteOfDay, reminderLeadMinutes, urgent, completed));
        } else {
            NotesStore.create(this, subject[0], subject[1], title, body, deadlineEpochDay, deadlineMinuteOfDay, reminderLeadMinutes, urgent);
        }
        WidgetRefreshScheduler.updateAllWidgets(this);
        finish();
    }

    /** Copies this note to the clipboard as plain text. */
    private void onCopyTapped() {
        String title = inputTitle.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "Give the note a title first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] subject = subjectOptions.get(selectedSubjectIndex);
        Long deadlineEpochDay = deadlineDate != null ? deadlineDate.toEpochDay() : null;
        Integer deadlineMinuteOfDay = deadlineTime != null ? deadlineTime.getHour() * 60 + deadlineTime.getMinute() : null;
        // Built from the fields on screen, not `editing`.
        Note preview = new Note(0, subject[0], subject[1], title, inputBody.getText().toString().trim(),
                deadlineEpochDay, deadlineMinuteOfDay, reminderLeadMinutes, urgent, completed, false, 0);

        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Note", preview.toPlainText()));
        Toast.makeText(this, "Copied to clipboard.", Toast.LENGTH_SHORT).show();
    }

    /** Rebuilds the attachment rows for the note currently being edited. No-op in create mode. */
    private void renderAttachments() {
        if (editing == null) return;
        attachmentsContainer.removeAllViews();
        String key = String.valueOf(editing.id);
        List<dev.marquinhhou.crsscheduler.model.Attachment> attachments =
                dev.marquinhhou.crsscheduler.data.AttachmentStore.get(this,
                        dev.marquinhhou.crsscheduler.data.AttachmentStore.NAMESPACE_NOTE, key);
        for (dev.marquinhhou.crsscheduler.model.Attachment a : attachments) {
            attachmentsContainer.addView(buildAttachmentRow(a, () -> openAttachment(a), () -> {
                dev.marquinhhou.crsscheduler.data.AttachmentStore.remove(this,
                        dev.marquinhhou.crsscheduler.data.AttachmentStore.NAMESPACE_NOTE, key, a.uri);
                renderAttachments();
            }));
        }
        // Rows added here can happen well after onResume()'s one-time apply() pass (e.g. from
        // the attachment-picker callback), so they need their own retint rather than relying on
        // that earlier pass to have already covered content that didn't exist yet.
        CustomThemeBackground.applySubtree(this, attachmentsContainer);
    }

    /** One "filename ... ×" row -- same shape as ConfigureActivity's syllabus rows. */
    private View buildAttachmentRow(dev.marquinhhou.crsscheduler.model.Attachment a, Runnable onOpen, Runnable onRemove) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int padPx = Math.round(8 * getResources().getDisplayMetrics().density);
        row.setPadding(padPx, padPx, padPx, padPx);

        TextView nameView = new TextView(this);
        nameView.setText(a.name);
        nameView.setTextColor(colorInkDim);
        nameView.setTextSize(12f);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameView.setLayoutParams(nameParams);
        nameView.setOnClickListener(v -> onOpen.run());
        row.addView(nameView);

        TextView removeView = new TextView(this);
        removeView.setText("\u2715");
        removeView.setTextColor(colorError);
        removeView.setPadding(padPx, 0, 0, 0);
        removeView.setOnClickListener(v -> onRemove.run());
        row.addView(removeView);

        return row;
    }

    private void openAttachment(dev.marquinhhou.crsscheduler.model.Attachment a) {
        try {
            Uri uri = Uri.parse(a.uri);
            String type = getContentResolver().getType(uri);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, type != null ? type : "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (SecurityException e) {
            Toast.makeText(this, "Can't open that file anymore -- try attaching it again.", Toast.LENGTH_LONG).show();
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open that file with.", Toast.LENGTH_SHORT).show();
        }
    }

    private void onAttachmentPicked(Uri uri) {
        if (editing == null) return; // shouldn't happen -- the button's hidden in create mode
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            // Some providers don't support a persistable grant -- still usable this session.
        }
        String name = queryDisplayName(uri);
        dev.marquinhhou.crsscheduler.data.AttachmentStore.add(this,
                dev.marquinhhou.crsscheduler.data.AttachmentStore.NAMESPACE_NOTE, String.valueOf(editing.id),
                new dev.marquinhhou.crsscheduler.model.Attachment(uri.toString(), name));
        renderAttachments();
        Toast.makeText(this, "File attached.", Toast.LENGTH_SHORT).show();
    }

    /** Best-effort filename lookup for a content:// Uri; falls back to the Uri's last segment. */
    private String queryDisplayName(Uri uri) {
        String name = null;
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception ignored) {
            // Fall through to the Uri-based fallback below.
        }
        return name != null ? name : uri.getLastPathSegment();
    }

    private void confirmDelete() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delete this note?")
                .setMessage("This can't be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    NotesStore.delete(this, editing.id);
                    WidgetRefreshScheduler.updateAllWidgets(this);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
        CustomThemeBackground.applyToDialog(dialog);
        CustomThemeBackground.styleDialogButtons(this, dialog);
    }
}
