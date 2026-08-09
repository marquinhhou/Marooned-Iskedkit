package dev.marquinhhou.crsscheduler.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.IcsImporter;
import dev.marquinhhou.crsscheduler.data.ScheduleHistoryStore;
import dev.marquinhhou.crsscheduler.data.ScheduleParser;
import dev.marquinhhou.crsscheduler.data.ScheduleStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore.ThemeFamily;
import dev.marquinhhou.crsscheduler.model.ClassSession;
import dev.marquinhhou.crsscheduler.widget.WidgetRefreshScheduler;
import dev.marquinhhou.crsscheduler.widget.WidgetRenderer;

public class ConfigureActivity extends AppCompatActivity {

    /** Set by the widget's Form 5 button to jump straight to that card. */
    public static final String EXTRA_EXPAND_FORM5 = "extra_expand_form5";

    // Wizard survives recreate() (e.g. theme pick) via these; see setUpWizard().
    private static final String KEY_IN_WIZARD_MODE = "in_wizard_mode";
    private static final String KEY_WIZARD_STEP = "wizard_step";

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private EditText htmlInput;
    private TextView statusText;
    private EditText inputCampus;
    private SwitchCompat switchMapsEnabled;
    private View groupCampusHint;
    private SwitchCompat switchEditMode;
    private LinearLayout editClassList;
    private Button btnSemesterStart;
    private Button btnSemesterEnd;
    private SwitchCompat switchSemesterOngoing;
    private TextView[] reminderChips;
    private static final int[] REMINDER_LEAD_OPTIONS = {0, 5, 10, 15, 30};

    // Collapsible "settings" cards -- collapsed by default, auto-expanded in
    // onCreate() if that section already has a non-default value set.
    private View headerMaps, bodyMaps, headerSemester, bodySemester, headerReminders, bodyReminders;
    private View headerProfile, bodyProfile, headerForm5, bodyForm5;
    private ImageView chevronMaps, chevronSemester, chevronReminders, chevronProfile, chevronForm5;
    private TextView summaryMaps, summarySemester, summaryReminders, summaryProfile, summaryForm5;
    private ViewGroup configureRoot;

    private EditText inputProfileName, inputProfileStudentNo, inputProfileCourse, inputProfileYearStanding;
    private SwitchCompat switchExportName, switchExportStudentNo, switchExportCourse, switchExportYearStanding;
    private SwitchCompat switchNotesTapEmpty;
    private TextView textForm5Status, textForm5Remove;
    private Button btnForm5Primary, btnForm5Replace;

    // First-run setup wizard -- only shown when there's no schedule loaded yet and onboarding
    // hasn't been completed before; returning users always see the normal all-cards view.
    private View wizardHeader, wizardNavRow, wizardDot1, wizardDot2, wizardDot3;
    private View cardImport, cardTheme, cardEditClassInfo, cardMaps, cardSemester, cardReminders, cardProfile, cardForm5, cardNotesSettings;
    private TextView wizardStepTitle;
    private Button btnWizardBack, btnWizardNext, doneBtn;
    private boolean inWizardMode = false;
    private int wizardStep = 0;

    private TextView chipThemeGe, chipThemeNe, chipThemeAdaptive;

    // Resolved once in onCreate() by resolveThemeAssets() -- see Theming.
    private int layoutDialogTerms, layoutRowEditClass, layoutDialogEditClass, layoutDialogAddClass;
    private int drawableChipFilledBg, drawableChipOutlineBg;
    private int colorBg, colorInkDim, colorGreen, colorError;

    private static final DateTimeFormatter SETTINGS_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) readFileIntoInput(uri);
            });

    private final ActivityResultLauncher<String[]> form5Picker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) onForm5Picked(uri);
            });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, "Notifications are off for this app, so reminders won't show. You can allow them from system Settings any time.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        Theming.applyActivityTheme(this);
        resolveThemeAssets();
        setContentView(Theming.pick(this,
                R.layout.activity_configure_ge, R.layout.activity_configure_ne, R.layout.activity_configure_adaptive));

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        configureRoot = findViewById(R.id.configure_root);
        htmlInput = findViewById(R.id.html_input);
        statusText = findViewById(R.id.status_text);
        inputCampus = findViewById(R.id.input_campus);
        switchEditMode = findViewById(R.id.switch_edit_mode);
        editClassList = findViewById(R.id.edit_class_list);
        btnSemesterStart = findViewById(R.id.btn_semester_start);
        btnSemesterEnd = findViewById(R.id.btn_semester_end);
        switchSemesterOngoing = findViewById(R.id.switch_semester_ongoing);
        reminderChips = new TextView[]{
                findViewById(R.id.chip_reminder_off),
                findViewById(R.id.chip_reminder_5),
                findViewById(R.id.chip_reminder_10),
                findViewById(R.id.chip_reminder_15),
                findViewById(R.id.chip_reminder_30),
        };
        for (int i = 0; i < reminderChips.length; i++) {
            int minutes = REMINDER_LEAD_OPTIONS[i];
            reminderChips[i].setOnClickListener(v -> onReminderLeadPicked(minutes));
        }

        chipThemeGe = findViewById(R.id.chip_theme_ge);
        chipThemeNe = findViewById(R.id.chip_theme_ne);
        chipThemeAdaptive = findViewById(R.id.chip_theme_adaptive);
        chipThemeGe.setOnClickListener(v -> onThemeFamilyPicked(ThemeFamily.GE));
        chipThemeNe.setOnClickListener(v -> onThemeFamilyPicked(ThemeFamily.NE));
        chipThemeAdaptive.setOnClickListener(v -> onThemeFamilyPicked(ThemeFamily.ADAPTIVE));
        if (!Theming.supportsAdaptive()) {
            chipThemeAdaptive.setAlpha(0.35f);
            chipThemeAdaptive.setOnClickListener(v -> Toast.makeText(this,
                    "Adaptive needs Android 12 or newer.", Toast.LENGTH_SHORT).show());
        }
        refreshThemeChips();

        headerMaps = findViewById(R.id.header_maps);
        bodyMaps = findViewById(R.id.body_maps);
        chevronMaps = findViewById(R.id.chevron_maps);
        summaryMaps = findViewById(R.id.summary_maps);

        headerSemester = findViewById(R.id.header_semester);
        bodySemester = findViewById(R.id.body_semester);
        chevronSemester = findViewById(R.id.chevron_semester);
        summarySemester = findViewById(R.id.summary_semester);

        headerReminders = findViewById(R.id.header_reminders);
        bodyReminders = findViewById(R.id.body_reminders);
        chevronReminders = findViewById(R.id.chevron_reminders);
        summaryReminders = findViewById(R.id.summary_reminders);

        headerProfile = findViewById(R.id.header_profile);
        bodyProfile = findViewById(R.id.body_profile);
        chevronProfile = findViewById(R.id.chevron_profile);
        summaryProfile = findViewById(R.id.summary_profile);

        headerForm5 = findViewById(R.id.header_form5);
        bodyForm5 = findViewById(R.id.body_form5);
        chevronForm5 = findViewById(R.id.chevron_form5);
        summaryForm5 = findViewById(R.id.summary_form5);

        headerMaps.setOnClickListener(v -> toggleSection(bodyMaps, chevronMaps));
        headerSemester.setOnClickListener(v -> toggleSection(bodySemester, chevronSemester));
        headerReminders.setOnClickListener(v -> toggleSection(bodyReminders, chevronReminders));
        headerProfile.setOnClickListener(v -> toggleSection(bodyProfile, chevronProfile));
        headerForm5.setOnClickListener(v -> toggleSection(bodyForm5, chevronForm5));

        setUpProfileSection();
        setUpNotesSection();
        setUpForm5Section();

        refreshReminderChips();

        Button pickFileBtn = findViewById(R.id.btn_pick_file);
        Button parseBtn = findViewById(R.id.btn_parse);
        Button clearBtn = findViewById(R.id.btn_clear);
        Button viewHistoryBtn = findViewById(R.id.btn_view_history);
        doneBtn = findViewById(R.id.btn_done);
        Button clearDatesBtn = findViewById(R.id.btn_semester_clear);
        Button addClassBtn = findViewById(R.id.btn_add_class);
        addClassBtn.setOnClickListener(v -> showAddClassDialog());

        inputCampus.setText(SettingsStore.getCampusHint(this));
        inputCampus.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateMapsSummary(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        switchMapsEnabled = findViewById(R.id.switch_maps_enabled);
        groupCampusHint = findViewById(R.id.group_campus_hint);
        switchMapsEnabled.setChecked(SettingsStore.isMapsEnabled(this));
        applyMapsEnabledState(SettingsStore.isMapsEnabled(this));
        switchMapsEnabled.setOnCheckedChangeListener((btn, checked) -> {
            SettingsStore.setMapsEnabled(this, checked);
            applyMapsEnabledState(checked);
            updateMapsSummary();
        });
        updateMapsSummary();

        pickFileBtn.setOnClickListener(v -> filePicker.launch(new String[]{"text/html", "text/calendar", "*/*"}));
        parseBtn.setOnClickListener(v -> handleParse(htmlInput.getText().toString()));
        viewHistoryBtn.setOnClickListener(v -> startActivity(new Intent(this, ScheduleHistoryActivity.class)));

        clearBtn.setOnClickListener(v -> {
            ScheduleHistoryStore.archive(this, ScheduleStore.load(this));
            ScheduleStore.clear(this);
            htmlInput.setText("");
            showStatus("Cleared saved schedule. Find it under Saved Schedules if you need it back.", true);
            renderEditList();
            WidgetRefreshScheduler.updateAllWidgets(this);
        });

        switchEditMode.setOnCheckedChangeListener((btn, checked) ->
                editClassList.setVisibility(checked ? View.VISIBLE : View.GONE));

        refreshSemesterDateViews();

        btnSemesterStart.setOnClickListener(v -> pickDate(SettingsStore.getSemesterStart(this), picked -> {
            SettingsStore.setSemesterStart(this, picked);
            refreshSemesterDateViews();
            WidgetRefreshScheduler.updateAllWidgets(this);
        }));

        btnSemesterEnd.setOnClickListener(v -> pickDate(SettingsStore.getSemesterEnd(this), picked -> {
            SettingsStore.setSemesterEnd(this, picked);
            refreshSemesterDateViews();
            WidgetRefreshScheduler.updateAllWidgets(this);
        }));

        clearDatesBtn.setOnClickListener(v -> {
            SettingsStore.setSemesterStart(this, null);
            SettingsStore.setSemesterEnd(this, null);
            SettingsStore.setLastAutoArchivedEnd(this, null);
            refreshSemesterDateViews();
            WidgetRefreshScheduler.updateAllWidgets(this);
            Toast.makeText(this, "Semester dates cleared.", Toast.LENGTH_SHORT).show();
        });

        doneBtn.setOnClickListener(v -> completeAndClose());

        boolean expandMaps = !SettingsStore.getCampusHint(this).trim().isEmpty() || !SettingsStore.isMapsEnabled(this);
        boolean expandSemester = SettingsStore.getSemesterStart(this) != null || SettingsStore.getSemesterEnd(this) != null;
        boolean expandReminders = SettingsStore.getReminderLeadMinutes(this) > 0;
        boolean expandProfile = !SettingsStore.getProfileName(this).isEmpty()
                || !SettingsStore.getProfileStudentNo(this).isEmpty()
                || !SettingsStore.getProfileCourse(this).isEmpty()
                || !SettingsStore.getProfileYearStanding(this).isEmpty();
        boolean expandForm5 = SettingsStore.getForm5Uri(this) != null;
        setSectionExpanded(bodyMaps, chevronMaps, expandMaps);
        setSectionExpanded(bodySemester, chevronSemester, expandSemester);
        setSectionExpanded(bodyReminders, chevronReminders, expandReminders);
        setSectionExpanded(bodyProfile, chevronProfile, expandProfile);
        setSectionExpanded(bodyForm5, chevronForm5, expandForm5);

        if (getIntent().getBooleanExtra(EXTRA_EXPAND_FORM5, false)) {
            setSectionExpanded(bodyForm5, chevronForm5, true);
            headerForm5.post(() -> headerForm5.requestRectangleOnScreen(
                    new android.graphics.Rect(0, 0, headerForm5.getWidth(), headerForm5.getHeight() + bodyForm5.getHeight()), true));
        }

        setUpWizard(savedInstanceState);

        if (!SettingsStore.hasAcceptedTerms(this)) {
            showTermsGate();
        }
    }

    /** Lets the wizard (and which step it's on) survive recreate() -- see setUpWizard(). */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IN_WIZARD_MODE, inWizardMode);
        outState.putInt(KEY_WIZARD_STEP, wizardStep);
    }

    /** Everything Done normally does -- also used by the wizard's Finish button. */
    private void completeAndClose() {
        SettingsStore.setCampusHint(this, inputCampus.getText().toString().trim());
        SettingsStore.setProfileName(this, inputProfileName.getText().toString().trim());
        SettingsStore.setProfileStudentNo(this, inputProfileStudentNo.getText().toString().trim());
        SettingsStore.setProfileCourse(this, inputProfileCourse.getText().toString().trim());
        SettingsStore.setProfileYearStanding(this, inputProfileYearStanding.getText().toString().trim());
        if (inWizardMode) SettingsStore.setOnboardingComplete(this, true);
        finishWithUpdate();
        finish();
    }

    /** First-run only: no schedule yet, onboarding never finished, Terms never accepted before.
     *  Walks Import -&gt; Review -&gt; Profile &amp; Extras, reusing the normal screen's cards/logic.
     *  Terms get accepted within this same onCreate(), so re-deriving on recreate() would wrongly
     *  flip inWizardMode false -- trust the saved instance state instead once it's known. */
    private void setUpWizard(Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_IN_WIZARD_MODE)) {
            inWizardMode = savedInstanceState.getBoolean(KEY_IN_WIZARD_MODE);
        } else {
            inWizardMode = ScheduleStore.load(this).isEmpty()
                    && !SettingsStore.hasCompletedOnboarding(this)
                    && !SettingsStore.hasAcceptedTerms(this);
        }
        if (!inWizardMode) return;

        wizardHeader = findViewById(R.id.wizard_header);
        wizardStepTitle = findViewById(R.id.wizard_step_title);
        wizardDot1 = findViewById(R.id.wizard_dot_1);
        wizardDot2 = findViewById(R.id.wizard_dot_2);
        wizardDot3 = findViewById(R.id.wizard_dot_3);
        wizardNavRow = findViewById(R.id.wizard_nav_row);
        btnWizardBack = findViewById(R.id.btn_wizard_back);
        btnWizardNext = findViewById(R.id.btn_wizard_next);
        cardImport = findViewById(R.id.card_import);
        cardTheme = findViewById(R.id.card_theme);
        cardEditClassInfo = findViewById(R.id.card_edit_class_info);
        cardMaps = findViewById(R.id.card_maps);
        cardSemester = findViewById(R.id.card_semester);
        cardReminders = findViewById(R.id.card_reminders);
        cardProfile = findViewById(R.id.card_profile);
        cardForm5 = findViewById(R.id.card_form5);
        cardNotesSettings = findViewById(R.id.card_notes_settings);

        wizardHeader.setVisibility(View.VISIBLE);
        wizardNavRow.setVisibility(View.VISIBLE);
        doneBtn.setVisibility(View.GONE);

        btnWizardBack.setOnClickListener(v -> applyWizardStep(wizardStep - 1));
        btnWizardNext.setOnClickListener(v -> {
            if (wizardStep >= 2) completeAndClose();
            else applyWizardStep(wizardStep + 1);
        });

        int restoredStep = savedInstanceState != null
                ? savedInstanceState.getInt(KEY_WIZARD_STEP, 0) : 0;
        applyWizardStep(restoredStep);
    }

    private void applyWizardStep(int step) {
        wizardStep = step;

        cardImport.setVisibility(step == 0 ? View.VISIBLE : View.GONE);
        cardEditClassInfo.setVisibility(step == 0 || step == 1 ? View.VISIBLE : View.GONE);
        boolean extrasStep = step == 2;
        cardProfile.setVisibility(extrasStep ? View.VISIBLE : View.GONE);
        cardForm5.setVisibility(extrasStep ? View.VISIBLE : View.GONE);
        cardMaps.setVisibility(extrasStep ? View.VISIBLE : View.GONE);
        cardSemester.setVisibility(extrasStep ? View.VISIBLE : View.GONE);
        cardReminders.setVisibility(extrasStep ? View.VISIBLE : View.GONE);
        cardTheme.setVisibility(extrasStep ? View.VISIBLE : View.GONE);
        cardNotesSettings.setVisibility(extrasStep ? View.VISIBLE : View.GONE);

        if (step == 1) {
            switchEditMode.setChecked(true);
            editClassList.setVisibility(View.VISIBLE);
        }

        String[] titles = {"STEP 1 OF 3 \u00B7 IMPORT YOUR SCHEDULE",
                "STEP 2 OF 3 \u00B7 REVIEW YOUR CLASSES", "STEP 3 OF 3 \u00B7 PROFILE & EXTRAS"};
        wizardStepTitle.setText(titles[step]);
        wizardDot1.setBackgroundResource(dotDrawable(step >= 0));
        wizardDot2.setBackgroundResource(dotDrawable(step >= 1));
        wizardDot3.setBackgroundResource(dotDrawable(step >= 2));

        btnWizardBack.setVisibility(step > 0 ? View.VISIBLE : View.GONE);
        btnWizardNext.setText(step == 2 ? "FINISH" : "NEXT");
        updateWizardNextEnabled();

        configureRoot.post(() -> {
            if (wizardHeader != null) {
                wizardHeader.requestRectangleOnScreen(
                        new android.graphics.Rect(0, 0, wizardHeader.getWidth(), wizardHeader.getHeight()), true);
            }
        });
    }

    /** Step 1 (Import) can't advance until something's actually been loaded. No-op outside wizard mode. */
    private void updateWizardNextEnabled() {
        if (!inWizardMode) return;
        boolean enabled = wizardStep != 0 || !ScheduleStore.load(this).isEmpty();
        btnWizardNext.setEnabled(enabled);
        btnWizardNext.setAlpha(enabled ? 1f : 0.4f);
    }

    private int dotDrawable(boolean active) {
        return active
                ? Theming.pick(this, R.drawable.dot_accent_ge, R.drawable.dot_accent_ne, R.drawable.dot_accent_adaptive)
                : Theming.pick(this, R.drawable.dot_dim_ge, R.drawable.dot_dim_ne, R.drawable.dot_dim_adaptive);
    }

    private void resolveThemeAssets() {
        layoutDialogTerms = Theming.pick(this, R.layout.dialog_terms_ge, R.layout.dialog_terms_ne, R.layout.dialog_terms_adaptive);
        layoutRowEditClass = Theming.pick(this, R.layout.row_edit_class_ge, R.layout.row_edit_class_ne, R.layout.row_edit_class_adaptive);
        layoutDialogEditClass = Theming.pick(this, R.layout.dialog_edit_class_ge, R.layout.dialog_edit_class_ne, R.layout.dialog_edit_class_adaptive);
        layoutDialogAddClass = Theming.pick(this, R.layout.dialog_add_class_ge, R.layout.dialog_add_class_ne, R.layout.dialog_add_class_adaptive);
        drawableChipFilledBg = Theming.pick(this, R.drawable.chip_filled_bg_ge, R.drawable.chip_filled_bg_ne, R.drawable.chip_filled_bg_adaptive);
        drawableChipOutlineBg = Theming.pick(this, R.drawable.chip_outline_bg_ge, R.drawable.chip_outline_bg_ne, R.drawable.chip_outline_bg_adaptive);
        colorBg = Theming.color(this, R.color.ge_bg, R.color.ne_bg, R.color.adaptive_bg);
        colorInkDim = Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim);
        colorGreen = Theming.color(this, R.color.ge_green, R.color.ne_green, R.color.adaptive_green);
        colorError = Theming.color(this, R.color.ge_error, R.color.ne_error, R.color.adaptive_error);
    }

    private void onThemeFamilyPicked(ThemeFamily family) {
        if (family == ThemeFamily.ADAPTIVE && !Theming.supportsAdaptive()) return;
        if (family == Theming.family(this)) return;
        SettingsStore.setThemeFamily(this, family);
        recreate();
    }

    private void refreshThemeChips() {
        ThemeFamily active = Theming.family(this);
        bindThemeChip(chipThemeGe, active == ThemeFamily.GE);
        bindThemeChip(chipThemeNe, active == ThemeFamily.NE);
        bindThemeChip(chipThemeAdaptive, active == ThemeFamily.ADAPTIVE);
    }

    private void bindThemeChip(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? drawableChipFilledBg : drawableChipOutlineBg);
        chip.setTextColor(selected ? colorBg : colorInkDim);
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<ClassSession> existing = ScheduleStore.load(this);
        statusText.setTextColor(colorInkDim);
        statusText.setText(existing.isEmpty() ? "" : existing.size() + " classes currently loaded.");
        renderEditList();
    }

    /** Shown once, before the rest of the screen can be used. Declining cancels setup. */
    private void showTermsGate() {
        View termsView = LayoutInflater.from(this).inflate(layoutDialogTerms, null);
        new AlertDialog.Builder(this)
                .setView(termsView)
                .setCancelable(false)
                .setPositiveButton(R.string.terms_agree, (d, w) ->
                        SettingsStore.setAcceptedTerms(this, true))
                .setNegativeButton(R.string.terms_decline, (d, w) -> {
                    Toast.makeText(this, "You need to accept the terms to use this widget.", Toast.LENGTH_LONG).show();
                    finish();
                })
                .show();
    }

    /** Reflects the stored semester start/end into the buttons and the ongoing switch. */
    private void refreshSemesterDateViews() {
        LocalDate start = SettingsStore.getSemesterStart(this);
        LocalDate end = SettingsStore.getSemesterEnd(this);
        boolean ongoing = end == null;

        btnSemesterStart.setText(start != null ? start.format(SETTINGS_DATE_FORMAT) : "Not set");

        // Detach first so setting the checked state here doesn't re-trigger the
        // listener and loop back into this method a second time.
        switchSemesterOngoing.setOnCheckedChangeListener(null);
        switchSemesterOngoing.setChecked(ongoing);
        switchSemesterOngoing.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                SettingsStore.setSemesterEnd(this, null);
                refreshSemesterDateViews();
                WidgetRefreshScheduler.updateAllWidgets(this);
            } else {
                // Meaningless without an end date -- ask for one right away.
                pickDate(SettingsStore.getSemesterEnd(this), picked -> {
                    SettingsStore.setSemesterEnd(this, picked);
                    refreshSemesterDateViews();
                    WidgetRefreshScheduler.updateAllWidgets(this);
                }, () -> refreshSemesterDateViews()); // cancelled -- snap back to "ongoing"
            }
        });

        btnSemesterEnd.setEnabled(!ongoing);
        btnSemesterEnd.setAlpha(ongoing ? 0.45f : 1f);
        btnSemesterEnd.setText(ongoing ? "Ongoing" : end.format(SETTINGS_DATE_FORMAT));

        if (summarySemester != null) {
            if (start == null && end == null) {
                summarySemester.setText("Not set");
            } else {
                String startLabel = start != null ? start.format(SETTINGS_DATE_FORMAT) : "Not set";
                String endLabel = ongoing ? "Ongoing" : end.format(SETTINGS_DATE_FORMAT);
                summarySemester.setText(startLabel + " \u2013 " + endLabel);
            }
        }
    }

    /** Opens a DatePickerDialog seeded at `current` (or today if unset), and hands back the picked date. */
    private void pickDate(LocalDate current, java.util.function.Consumer<LocalDate> onPicked) {
        pickDate(current, onPicked, null);
    }

    /** Also runs `onCancelled` on dismiss-without-picking (back/outside-tap/negative button). */
    private void pickDate(LocalDate current, java.util.function.Consumer<LocalDate> onPicked, Runnable onCancelled) {
        LocalDate seed = current != null ? current : LocalDate.now();
        boolean[] picked = {false};
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            picked[0] = true;
            onPicked.accept(LocalDate.of(year, month + 1, dayOfMonth));
        }, seed.getYear(), seed.getMonthValue() - 1, seed.getDayOfMonth());
        if (onCancelled != null) {
            dialog.setOnDismissListener(d -> {
                if (!picked[0]) onCancelled.run();
            });
        }
        dialog.show();
    }

    /** Seeded at `currentMinute` (or noon); hands back minutes-from-midnight, same as ClassSession. */
    private void pickTime(int currentMinute, java.util.function.IntConsumer onPicked) {
        int seedMinute = currentMinute >= 0 ? currentMinute : 12 * 60;
        new TimePickerDialog(this, (view, hourOfDay, minute) ->
                onPicked.accept(hourOfDay * 60 + minute),
                seedMinute / 60, seedMinute % 60, false).show();
    }

    // Collapsible sections

    /** Animates the whole configure_root subtree so cards below also reflow smoothly. */
    private void toggleSection(View body, ImageView chevron) {
        if (configureRoot != null) {
            TransitionSet transition = new TransitionSet()
                    .addTransition(new Fade(Fade.IN | Fade.OUT))
                    .addTransition(new ChangeBounds())
                    .setOrdering(TransitionSet.ORDERING_TOGETHER)
                    .setDuration(220)
                    .setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
            TransitionManager.beginDelayedTransition(configureRoot, transition);
        }
        setSectionExpanded(body, chevron, body.getVisibility() != View.VISIBLE);
    }

    private void setSectionExpanded(View body, ImageView chevron, boolean expanded) {
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        chevron.setRotation(expanded ? 180f : 0f);
    }

    private void updateMapsSummary() {
        if (!SettingsStore.isMapsEnabled(this)) {
            summaryMaps.setText("Off");
            return;
        }
        String v = inputCampus.getText().toString().trim();
        summaryMaps.setText(v.isEmpty() ? "Not set" : v);
    }

    /** Dims the campus-hint fields when Maps is turned off; they're moot either way. */
    private void applyMapsEnabledState(boolean enabled) {
        groupCampusHint.setAlpha(enabled ? 1f : 0.4f);
        inputCampus.setEnabled(enabled);
    }

    // Notes

    private void setUpNotesSection() {
        switchNotesTapEmpty = findViewById(R.id.switch_notes_tap_empty);
        switchNotesTapEmpty.setChecked(SettingsStore.isNotesTapEmptyToCreateEnabled(this));
        switchNotesTapEmpty.setOnCheckedChangeListener((btn, checked) ->
                SettingsStore.setNotesTapEmptyToCreateEnabled(this, checked));
    }

    // Profile

    /** Populates fields/switches from SettingsStore and wires listeners; text fields save on Done, switches save immediately. */
    private void setUpProfileSection() {
        inputProfileName = findViewById(R.id.input_profile_name);
        inputProfileStudentNo = findViewById(R.id.input_profile_student_no);
        inputProfileCourse = findViewById(R.id.input_profile_course);
        inputProfileYearStanding = findViewById(R.id.input_profile_year_standing);
        switchExportName = findViewById(R.id.switch_export_name);
        switchExportStudentNo = findViewById(R.id.switch_export_student_no);
        switchExportCourse = findViewById(R.id.switch_export_course);
        switchExportYearStanding = findViewById(R.id.switch_export_year_standing);

        inputProfileName.setText(SettingsStore.getProfileName(this));
        inputProfileStudentNo.setText(SettingsStore.getProfileStudentNo(this));
        inputProfileCourse.setText(SettingsStore.getProfileCourse(this));
        inputProfileYearStanding.setText(SettingsStore.getProfileYearStanding(this));
        switchExportName.setChecked(SettingsStore.isExportShowNameEnabled(this));
        switchExportStudentNo.setChecked(SettingsStore.isExportShowStudentNoEnabled(this));
        switchExportCourse.setChecked(SettingsStore.isExportShowCourseEnabled(this));
        switchExportYearStanding.setChecked(SettingsStore.isExportShowYearStandingEnabled(this));

        switchExportName.setOnCheckedChangeListener((btn, checked) -> SettingsStore.setExportShowNameEnabled(this, checked));
        switchExportStudentNo.setOnCheckedChangeListener((btn, checked) -> SettingsStore.setExportShowStudentNoEnabled(this, checked));
        switchExportCourse.setOnCheckedChangeListener((btn, checked) -> SettingsStore.setExportShowCourseEnabled(this, checked));
        switchExportYearStanding.setOnCheckedChangeListener((btn, checked) -> SettingsStore.setExportShowYearStandingEnabled(this, checked));

        EditText[] profileInputs = {inputProfileName, inputProfileStudentNo, inputProfileCourse, inputProfileYearStanding};
        for (EditText input : profileInputs) {
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateProfileSummary(); }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
        updateProfileSummary();
    }

    private void updateProfileSummary() {
        String name = inputProfileName.getText().toString().trim();
        if (!name.isEmpty()) {
            summaryProfile.setText(name);
            return;
        }
        int filled = 0;
        if (!inputProfileStudentNo.getText().toString().trim().isEmpty()) filled++;
        if (!inputProfileCourse.getText().toString().trim().isEmpty()) filled++;
        if (!inputProfileYearStanding.getText().toString().trim().isEmpty()) filled++;
        summaryProfile.setText(filled == 0 ? "Not set" : filled + " of 4 fields set");
    }

    // Form 5

    /** A picked PDF's Uri, held via a persistable permission grant so it survives app restarts. */
    private void setUpForm5Section() {
        textForm5Status = findViewById(R.id.text_form5_status);
        textForm5Remove = findViewById(R.id.text_form5_remove);
        btnForm5Primary = findViewById(R.id.btn_form5_primary);
        btnForm5Replace = findViewById(R.id.btn_form5_replace);

        btnForm5Primary.setOnClickListener(v -> {
            Uri existing = SettingsStore.getForm5Uri(this);
            if (existing != null) {
                openForm5(existing);
            } else {
                form5Picker.launch(new String[]{"application/pdf"});
            }
        });
        btnForm5Replace.setOnClickListener(v -> form5Picker.launch(new String[]{"application/pdf"}));
        textForm5Remove.setOnClickListener(v -> confirmRemoveForm5());

        refreshForm5Views();
    }

    private void onForm5Picked(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            // Some providers don't support a persistable grant -- it'll still work for this
            // session, but may need re-picking after the app restarts.
        }
        SettingsStore.setForm5Uri(this, uri);
        refreshForm5Views();
        WidgetRefreshScheduler.updateAllWidgets(this);
        Toast.makeText(this, "Form 5 saved.", Toast.LENGTH_SHORT).show();
    }

    private void openForm5(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (SecurityException e) {
            Toast.makeText(this, "Can't open that file anymore -- try uploading it again.", Toast.LENGTH_LONG).show();
            clearForm5(false);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open a PDF with.", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmRemoveForm5() {
        new AlertDialog.Builder(this)
                .setTitle("Remove Form 5?")
                .setMessage("This only removes it from CRS Scheduler -- the original file is untouched.")
                .setPositiveButton("Remove", (d, w) -> clearForm5(true))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearForm5(boolean showToast) {
        Uri uri = SettingsStore.getForm5Uri(this);
        if (uri != null) {
            try {
                getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Wasn't holding a persistable grant on it (or it's gone) -- nothing to release.
            }
        }
        SettingsStore.setForm5Uri(this, null);
        refreshForm5Views();
        WidgetRefreshScheduler.updateAllWidgets(this);
        if (showToast) Toast.makeText(this, "Form 5 removed.", Toast.LENGTH_SHORT).show();
    }

    private void refreshForm5Views() {
        Uri uri = SettingsStore.getForm5Uri(this);
        boolean uploaded = uri != null;
        String name = uploaded ? queryDisplayName(uri) : null;

        textForm5Status.setText(uploaded ? (name != null ? name : "Uploaded") : "No file uploaded yet.");
        btnForm5Primary.setText(uploaded ? "OPEN" : "UPLOAD FORM 5 PDF");
        btnForm5Replace.setVisibility(uploaded ? View.VISIBLE : View.GONE);
        textForm5Remove.setVisibility(uploaded ? View.VISIBLE : View.GONE);
        summaryForm5.setText(uploaded ? (name != null ? name : "Uploaded") : "Not uploaded");
    }

    /** Best-effort filename lookup for a content:// Uri; null if the provider doesn't report one. */
    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Exception ignored) {
            // Falls back to a generic "Uploaded" label.
        }
        return null;
    }

    // Class reminders

    private void refreshReminderChips() {
        int lead = SettingsStore.getReminderLeadMinutes(this);
        for (int i = 0; i < reminderChips.length; i++) {
            boolean active = REMINDER_LEAD_OPTIONS[i] == lead;
            reminderChips[i].setBackgroundResource(active ? drawableChipFilledBg : drawableChipOutlineBg);
            reminderChips[i].setTextColor(active ? colorBg : colorInkDim);
        }
        if (summaryReminders != null) {
            summaryReminders.setText(lead > 0 ? lead + " min before class" : "Off");
        }
    }

    private void onReminderLeadPicked(int minutes) {
        SettingsStore.setReminderLeadMinutes(this, minutes);
        refreshReminderChips();
        if (minutes > 0) ensureReminderPermissions();
        // Also re-anchors ClassReminderScheduler against the new lead time.
        WidgetRefreshScheduler.updateAllWidgets(this);
        Toast.makeText(this,
                minutes > 0 ? "Reminders on \u00B7 " + minutes + " min before class" : "Reminders off",
                Toast.LENGTH_SHORT).show();
    }

    /** Requests whatever's missing for reminders to actually fire: POST_NOTIFICATIONS (33+) and, best-effort, exact-alarm scheduling (31+). */
    private void ensureReminderPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
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
                    // No such settings screen on this OEM build -- reminders
                    // will still fire, just not necessarily to the exact minute.
                }
            }
        }
    }

    private void readFileIntoInput(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IOException("empty stream");
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            String content = sb.toString();

            // Detect .ics by content, not file extension (content providers don't always expose one).
            if (content.toUpperCase(Locale.US).contains("BEGIN:VCALENDAR")) {
                handleIcsImport(content);
            } else {
                htmlInput.setText(content);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Couldn't read that file.", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleIcsImport(String ics) {
        try {
            IcsImporter.Result result = IcsImporter.parse(ics);

            List<ClassSession> outgoing = ScheduleStore.load(this);
            if (!outgoing.isEmpty() && !sameSchedule(outgoing, result.classes)) {
                ScheduleHistoryStore.archive(this, outgoing);
            }
            ScheduleStore.save(this, result.classes);

            String msg = "Imported " + result.classes.size() + " classes from calendar file"
                    + (result.skipped > 0 ? " \u00B7 " + result.skipped + " event(s) skipped" : "");
            showStatus(msg, true);
            renderEditList();
            WidgetRefreshScheduler.updateAllWidgets(this);
            updateWizardNextEnabled();
        } catch (IcsImporter.ParseException e) {
            if (e.reason == IcsImporter.ParseException.Reason.NO_CALENDAR) {
                showStatus("That doesn't look like a calendar (.ics) file.", false);
            } else {
                showStatus("Found the calendar file but couldn't read any events from it.", false);
            }
        } catch (Exception e) {
            showStatus("Something went wrong reading that calendar file.", false);
        }
    }

    private void handleParse(String html) {
        if (html == null || html.trim().isEmpty()) {
            showStatus("Paste the page source or choose a file first.", false);
            return;
        }
        try {
            ScheduleParser.Result result = ScheduleParser.parse(html);

            List<ClassSession> outgoing = ScheduleStore.load(this);
            if (!outgoing.isEmpty() && !sameSchedule(outgoing, result.classes)) {
                ScheduleHistoryStore.archive(this, outgoing);
            }
            ScheduleStore.save(this, result.classes);

            // auto-fill the campus field the first time, if we can sniff one out
            if (inputCampus.getText().toString().trim().isEmpty()) {
                String detected = ScheduleParser.detectCampusHint(html);
                if (detected != null) {
                    inputCampus.setText(detected);
                    SettingsStore.setCampusHint(this, detected);
                }
            }

            double units = 0;
            for (ClassSession c : result.classes) units += c.credits;
            String msg = "Loaded " + result.classes.size() + " classes \u00B7 "
                    + String.format(Locale.US, "%.1f", units) + " units"
                    + (result.skipped > 0 ? " \u00B7 " + result.skipped + " row(s) skipped" : "");

            showStatus(msg, true);
            renderEditList();
            WidgetRefreshScheduler.updateAllWidgets(this);
            updateWizardNextEnabled();
        } catch (ScheduleParser.ParseException e) {
            if (e.reason == ScheduleParser.ParseException.Reason.NO_TABLE) {
                showStatus("Couldn't find a class schedule table \u2014 make sure you copied a Registration (\u201cMy Enlisted Classes\u201d) or Preenlistment (\u201cMy Desired Classes\u201d) page.", false);
            } else {
                showStatus("Found the table but couldn't read any class rows.", false);
            }
        } catch (Exception e) {
            showStatus("Something went wrong reading that file.", false);
        }
    }

    /** True if two schedules serialize identically -- used to skip archiving a no-op re-import. */
    private boolean sameSchedule(List<ClassSession> a, List<ClassSession> b) {
        if (a.size() != b.size()) return false;
        try {
            org.json.JSONArray arrA = new org.json.JSONArray();
            for (ClassSession c : a) arrA.put(c.toJson());
            org.json.JSONArray arrB = new org.json.JSONArray();
            for (ClassSession c : b) arrB.put(c.toJson());
            return arrA.toString().equals(arrB.toString());
        } catch (org.json.JSONException e) {
            return false;
        }
    }

    // Edit Class Info

    private void renderEditList() {
        editClassList.removeAllViews();
        List<ClassSession> schedule = ScheduleStore.load(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ClassSession c : schedule) {
            View row = inflater.inflate(layoutRowEditClass, editClassList, false);
            ((TextView) row.findViewById(R.id.edit_row_name)).setText(c.name);
            String daysLabel = daysToLabel(c.days);
            String meta = daysLabel + " \u00B7 " + WidgetRenderer.minToLabel(c.start)
                    + "\u2013" + WidgetRenderer.minToLabel(c.end) + " \u00B7 " + c.displayRoom()
                    + " \u00B7 " + String.format(Locale.US, "%.1f", c.credits) + " units";
            ((TextView) row.findViewById(R.id.edit_row_meta)).setText(meta);
            row.setOnClickListener(v -> showEditDialog(c));
            editClassList.addView(row);
        }
    }

    private String daysToLabel(List<Integer> days) {
        String[] letters = {"Su", "M", "T", "W", "Th", "F", "S"};
        StringBuilder sb = new StringBuilder();
        for (int d : days) sb.append(letters[d]);
        return sb.toString();
    }

    private void showEditDialog(ClassSession c) {
        View dialogView = LayoutInflater.from(this).inflate(layoutDialogEditClass, null);
        ((TextView) dialogView.findViewById(R.id.dialog_edit_class_name)).setText(c.name);

        EditText roomInput = dialogView.findViewById(R.id.dialog_input_room);
        EditText instructorInput = dialogView.findViewById(R.id.dialog_input_instructor);
        EditText unitsInput = dialogView.findViewById(R.id.dialog_input_units);

        roomInput.setText(c.room != null && !c.room.equalsIgnoreCase("TBA") ? c.room : "");
        instructorInput.setText(c.instructor != null ? c.instructor : "");
        unitsInput.setText(String.format(Locale.US, "%.1f", c.credits));

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("SAVE", (d, w) -> {
                    String newRoom = roomInput.getText().toString().trim();
                    String newInstructor = instructorInput.getText().toString().trim();
                    double newUnits;
                    try {
                        newUnits = Double.parseDouble(unitsInput.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        newUnits = c.credits; // keep the original value if the field was left blank/invalid
                    }
                    applyEdit(c, newRoom, newInstructor, newUnits);
                })
                .setNeutralButton("DELETE", (d, w) -> confirmDelete(c))
                .setNegativeButton("CANCEL", null)
                .show();
    }

    /** Deletion has no undo button here, unlike an edit, so it needs a confirmation first. */
    private void confirmDelete(ClassSession c) {
        new AlertDialog.Builder(this)
                .setTitle("Remove this class?")
                .setMessage(c.name + " will be removed from your schedule. You can still recover it "
                        + "from Saved Schedules afterward.")
                .setPositiveButton("REMOVE", (d, w) -> applyDelete(c))
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void applyDelete(ClassSession original) {
        List<ClassSession> schedule = ScheduleStore.load(this);
        // Snapshot the full pre-delete schedule to history first, same as Clear does --
        // deleting one class shouldn't be a dead end if it turns out to be the wrong one.
        ScheduleHistoryStore.archive(this, schedule);
        List<ClassSession> updated = new ArrayList<>();
        for (ClassSession c : schedule) {
            if (!c.code.equals(original.code)) updated.add(c);
        }
        ScheduleStore.save(this, updated);
        renderEditList();
        WidgetRefreshScheduler.updateAllWidgets(this);
        Toast.makeText(this, "Removed.", Toast.LENGTH_SHORT).show();
    }

    private void applyEdit(ClassSession original, String newRoom, String newInstructor, double newUnits) {
        List<ClassSession> schedule = ScheduleStore.load(this);
        List<ClassSession> updated = new ArrayList<>();
        for (ClassSession c : schedule) {
            if (c.code.equals(original.code)) {
                updated.add(c.withEditedInfo(newRoom.isEmpty() ? "TBA" : newRoom, newInstructor, newUnits));
            } else {
                updated.add(c);
            }
        }
        ScheduleStore.save(this, updated);
        renderEditList();
        WidgetRefreshScheduler.updateAllWidgets(this);
        Toast.makeText(this, "Updated.", Toast.LENGTH_SHORT).show();
    }

    // Add Class -- for classes that didn't come from an HTML/ICS import (a lab session added
    // after the fact, a study group, anything CRS itself doesn't know about).

    private void showAddClassDialog() {
        View dialogView = LayoutInflater.from(this).inflate(layoutDialogAddClass, null);

        EditText nameInput = dialogView.findViewById(R.id.dialog_add_input_name);
        EditText roomInput = dialogView.findViewById(R.id.dialog_add_input_room);
        EditText instructorInput = dialogView.findViewById(R.id.dialog_add_input_instructor);
        EditText unitsInput = dialogView.findViewById(R.id.dialog_add_input_units);
        Button startTimeBtn = dialogView.findViewById(R.id.btn_add_start_time);
        Button endTimeBtn = dialogView.findViewById(R.id.btn_add_end_time);

        TextView[] dayChips = {
                dialogView.findViewById(R.id.chip_add_day_0),
                dialogView.findViewById(R.id.chip_add_day_1),
                dialogView.findViewById(R.id.chip_add_day_2),
                dialogView.findViewById(R.id.chip_add_day_3),
                dialogView.findViewById(R.id.chip_add_day_4),
                dialogView.findViewById(R.id.chip_add_day_5),
                dialogView.findViewById(R.id.chip_add_day_6),
        };
        boolean[] selectedDays = new boolean[7];
        for (int i = 0; i < dayChips.length; i++) {
            int idx = i;
            bindThemeChip(dayChips[i], false);
            dayChips[i].setOnClickListener(v -> {
                selectedDays[idx] = !selectedDays[idx];
                bindThemeChip(dayChips[idx], selectedDays[idx]);
            });
        }

        int[] startMinute = {-1};
        int[] endMinute = {-1};
        startTimeBtn.setOnClickListener(v -> pickTime(startMinute[0], picked -> {
            startMinute[0] = picked;
            startTimeBtn.setText(WidgetRenderer.minToLabel(picked));
        }));
        endTimeBtn.setOnClickListener(v -> pickTime(endMinute[0], picked -> {
            endMinute[0] = picked;
            endTimeBtn.setText(WidgetRenderer.minToLabel(picked));
        }));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("ADD", null) // wired manually below so a validation miss can keep the dialog open
                .setNegativeButton("CANCEL", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                nameInput.setError("Enter a name.");
                return;
            }
            List<Integer> days = new ArrayList<>();
            for (int i = 0; i < selectedDays.length; i++) if (selectedDays[i]) days.add(i);
            if (days.isEmpty()) {
                Toast.makeText(this, "Pick at least one day.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (startMinute[0] < 0 || endMinute[0] < 0) {
                Toast.makeText(this, "Set a start and end time.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (endMinute[0] <= startMinute[0]) {
                Toast.makeText(this, "End time must be after the start time.", Toast.LENGTH_SHORT).show();
                return;
            }
            double units;
            try {
                units = Double.parseDouble(unitsInput.getText().toString().trim());
            } catch (NumberFormatException e) {
                units = 0; // blank/invalid units is fine for a manual entry -- default to 0 rather than block
            }
            String room = roomInput.getText().toString().trim();
            String instructor = instructorInput.getText().toString().trim();

            // "manual-" + a UUID keeps this unique among CRS-issued codes (which are numeric)
            // without needing one -- code is only ever used internally as an identifier
            // (edit matching, reminder request codes, ICS UIDs), never shown in the UI.
            applyAdd(new ClassSession("manual-" + UUID.randomUUID(), name, units, false,
                    days, startMinute[0], endMinute[0], "", room, instructor));
            dialog.dismiss();
        }));

        dialog.show();
    }

    private void applyAdd(ClassSession newClass) {
        List<ClassSession> schedule = ScheduleStore.load(this);
        schedule.add(newClass);
        ScheduleStore.save(this, schedule);
        switchEditMode.setChecked(true); // reveal the list so the new class is visible right away
        renderEditList();
        WidgetRefreshScheduler.updateAllWidgets(this);
        updateWizardNextEnabled();
        Toast.makeText(this, "Added.", Toast.LENGTH_SHORT).show();
    }

    // Shared

    private void showStatus(String msg, boolean ok) {
        statusText.setText((ok ? "\u2713 " : "\u2715 ") + msg);
        statusText.setTextColor(ok ? colorGreen : colorError);
    }

    private void finishWithUpdate() {
        WidgetRefreshScheduler.updateAllWidgets(this);
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Intent result = new Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(Activity.RESULT_OK, result);
        }
    }
}
