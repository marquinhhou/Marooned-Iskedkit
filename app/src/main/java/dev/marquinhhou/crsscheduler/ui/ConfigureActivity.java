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
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.io.BufferedReader;
import java.io.File;
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
    /** Tracks the value seedMapsContextFromUniversity last wrote into inputCampus, so repeated
     * calls (e.g. from live typing in the manual-school field) know whether the person has since
     * edited Maps context themselves -- in which case auto-seeding must stop -- versus the field
     * still holding exactly what was auto-filled, in which case it's still safe to keep syncing. */
    private String lastAutoSeededMapsContext = null;
    private SwitchCompat switchMapsEnabled;
    private SwitchCompat switchCampusAutoDetect;
    private View groupCampusHint;
    private SwitchCompat switchEditMode;
    private LinearLayout editClassList;
    private LinearLayout editScheduleInfoBody;
    private Button btnSemesterStart;
    private Button btnSemesterEnd;
    private SwitchCompat switchSemesterOngoing;
    private TextView[] reminderChips;
    private static final int[] REMINDER_LEAD_OPTIONS = {0, 5, 10, 15, 30};

    // Collapsible "settings" cards -- collapsed by default, auto-expanded in
    // onCreate() if that section already has a non-default value set.
    private View headerMaps, bodyMaps, headerSemester, headerReminders, bodyReminders;
    private ViewGroup bodySemester;
    private View headerProfile, bodyProfile, headerForm5, bodyForm5;
    private ImageView chevronMaps, chevronSemester, chevronReminders, chevronProfile, chevronForm5;
    private TextView summaryMaps, summarySemester, summaryReminders, summaryProfile, summaryForm5;
    private ViewGroup configureRoot;

    private EditText inputProfileName, inputProfileStudentNo, inputProfileCourse, inputProfileYearStanding;
    private EditText inputProfileAddress, inputProfileFacebook, inputProfileInstagram, inputProfileTwitter, inputProfileLinkedin, inputProfileWebsite;
    private SwitchCompat switchExportName, switchExportStudentNo, switchExportCourse, switchExportYearStanding;
    private SwitchCompat switchExportAddress, switchExportFacebook, switchExportInstagram, switchExportTwitter, switchExportLinkedin, switchExportWebsite;
    private SwitchCompat switchNotesTapEmpty;
    private TextView textForm5Status, textForm5Remove;
    private Button btnForm5Primary, btnForm5Replace;

    // First-run setup wizard -- only shown when there's no schedule loaded yet and onboarding
    // hasn't been completed before; returning users always see the normal all-cards view.
    private View wizardHeader, wizardNavRow, wizardDot1, wizardDot2, wizardDot3;
    private View cardImport, cardTheme, cardEditClassInfo, cardMaps, cardSemester, cardReminders, cardProfile, cardForm5, cardNotesSettings;
    private View cardProfilePrompt;
    private View cardSetupMode;
    private TextView wizardStepTitle;
    private Button btnWizardBack, btnWizardNext, doneBtn;
    private boolean inWizardMode = false;
    private boolean editInfoExpanded = false;
    private ImageView editChevron;
    private Button parseBtnField;
    private final List<View> crsViews = new ArrayList<>();
    private int wizardStep = -1;

    private TextView chipThemeGe, chipThemeNe, chipThemeAdaptive;

    // v3.0.0 -- university affiliation (built programmatically so all four layout variants
    // share one implementation; see setUpUniversityCard).
    private LinearLayout cardUniversity;
    private TextView chipUniUp, chipUniOther, btnUniversityCampus, universitySummary;
    private EditText inputManualUniversity;

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

    // Class code the syllabus picker was launched for -- OpenDocument's callback fires
    // asynchronously (possibly after this Activity was recreated), so it can't just be a
    // local variable captured at launch time.
    private String pendingSyllabusClassCode;
    private LinearLayout activeSyllabusContainer;

    private final ActivityResultLauncher<String[]> syllabusPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && pendingSyllabusClassCode != null) onSyllabusPicked(pendingSyllabusClassCode, uri);
            });

    private AlertDialog activeCustomThemeDialog;

    private final ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> customThemePhotoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null) return; // user backed out of the picker
                File destination = new File(getFilesDir(), "custom_theme_photo.jpg");
                try {
                    CustomThemeBackground.copyPickedPhotoToLocalStorage(this, uri, destination);
                    SettingsStore.setCustomPhotoPath(this, destination.getAbsolutePath());
                    // The dialog that launched the picker very likely auto-dismissed while the
                    // system picker was in the foreground (Android tears down a hosting
                    // Activity's dialogs when it stops) -- rebuild fresh rather than depending
                    // on the old dialog surviving, and it naturally re-reads the just-saved path.
                    showCustomThemeDialog(true);
                } catch (Exception e) {
                    Toast.makeText(this, "Couldn't save that photo: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, "Notifications are off for this app, so reminders won't show. You can allow them from system Settings any time.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            onCreateBody(savedInstanceState);
        } catch (Throwable t) {
            showCrashScreen(t);
        }
    }

    private void onCreateBody(Bundle savedInstanceState) {
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
        statusText.setMinimumHeight(Math.round(48 * getResources().getDisplayMetrics().density));
        statusText.setGravity(android.view.Gravity.CENTER_VERTICAL);
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

        chipThemeGe = findViewById(R.id.chip_theme_ge); // now the CUSTOM chip -- see activity_configure_*.xml
        chipThemeNe = findViewById(R.id.chip_theme_ne);
        chipThemeAdaptive = findViewById(R.id.chip_theme_adaptive);
        chipThemeGe.setOnClickListener(v -> showCustomThemeDialog(false));
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
        setUpUniversityCard();
        mergeEditIntoImportCard();
        tuneProfileCardSection();

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
        switchCampusAutoDetect = findViewById(R.id.switch_campus_autodetect);
        groupCampusHint = findViewById(R.id.group_campus_hint);
        switchMapsEnabled.setChecked(SettingsStore.isMapsEnabled(this));
        switchCampusAutoDetect.setChecked(SettingsStore.isCampusAutoDetectEnabled(this));
        applyMapsEnabledState(SettingsStore.isMapsEnabled(this));
        switchMapsEnabled.setOnCheckedChangeListener((btn, checked) -> {
            SettingsStore.setMapsEnabled(this, checked);
            applyMapsEnabledState(checked);
            updateMapsSummary();
        });
        switchCampusAutoDetect.setOnCheckedChangeListener((btn, checked) ->
                SettingsStore.setCampusAutoDetectEnabled(this, checked));
        updateMapsSummary();

        pickFileBtn.setOnClickListener(v -> pickScheduleFile());
        parseBtn.setOnClickListener(v -> handleParse(htmlInput.getText().toString()));
        viewHistoryBtn.setOnClickListener(v -> startActivity(new Intent(this, ScheduleHistoryActivity.class)));
        applyImportGating();

        clearBtn.setOnClickListener(v -> {
            ScheduleHistoryStore.archive(this, ScheduleStore.load(this));
            ScheduleStore.clear(this);
            htmlInput.setText("");
            showStatus("Cleared saved schedule. Find it under Saved Schedules if you need it back.", true);
            renderEditList();
            WidgetRefreshScheduler.updateAllWidgets(this);
        });

        // (The old EDIT CLASS INFO switch is retired -- the dropdown header drives visibility.)

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

        // v3.0.0 rebrand beat -- exactly once per install, on top of whatever entry
        // screen brought the user here.
        if (!SettingsStore.isWelcomeV3Shown(this)) {
            startActivity(new Intent(this, WelcomeActivity.class));
        }

        addProfileAvatarButton();

        if (!SettingsStore.hasAcceptedTerms(this)) {
            showTermsGate();
        }
    }

    /** Emergency diagnostics: renders the stack trace on screen so a crash on this
     *  screen can be reported by screenshot instead of being a silent "keeps stopping". */
    private void showCrashScreen(Throwable t) {
        try {
            android.util.Log.e("ConfigureCrash", "Settings failed to load", t);
            float d = getResources().getDisplayMetrics().density;
            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            setContentView(sv);
            android.widget.LinearLayout col = new android.widget.LinearLayout(this);
            col.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad = Math.round(16 * d);
            col.setPadding(pad, pad, pad, pad);
            sv.addView(col);
            android.widget.TextView head = new android.widget.TextView(this);
            head.setText("Settings failed to load");
            head.setTextColor(0xFFE05555);
            head.setTextSize(16f);
            head.setTypeface(null, android.graphics.Typeface.BOLD);
            col.addView(head);
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            Throwable cause = t.getCause();
            while (cause != null) {
                sw.append("\n\nCAUSE:\n");
                cause.printStackTrace(new java.io.PrintWriter(sw));
                cause = cause.getCause();
            }
            android.widget.TextView trace = new android.widget.TextView(this);
            trace.setText(sw.toString());
            trace.setTextColor(0xFFE0E0E0);
            trace.setTextSize(9f);
            trace.setFontFeatureSettings("");
            trace.setTypeface(android.graphics.Typeface.MONOSPACE);
            col.addView(trace);
            android.widget.TextView copy = new android.widget.TextView(this);
            copy.setText("COPY TRACE");
            copy.setTextColor(0xFFE0E0E0);
            copy.setGravity(android.view.Gravity.CENTER);
            copy.setPadding(0, pad, 0, 0);
            copy.setOnClickListener(v -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", sw.toString()));
                android.widget.Toast.makeText(this, "Trace copied.", Toast.LENGTH_SHORT).show();
            });
            col.addView(copy);
        } catch (Throwable ignored) {
            // Even the error screen failed -- let the system dialog take it from here.
            throw t instanceof RuntimeException ? (RuntimeException) t : new IllegalStateException(t);
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
        buildProfilePromptCard();
        buildSetupModeCard();

        wizardHeader.setVisibility(View.VISIBLE);
        wizardNavRow.setVisibility(View.VISIBLE);
        doneBtn.setVisibility(View.GONE);
        // The UP question belongs to initial setup only -- afterwards, changing your
        // university happens inside the profile menu. The old PROFILE card is retired
        // there too.
        cardUniversity.setVisibility(inWizardMode ? View.VISIBLE : View.GONE);

        btnWizardBack.setOnClickListener(v -> applyWizardStep(wizardStep - 1));
        btnWizardNext.setOnClickListener(v -> {
            // Simple mode skips Profile & Extras entirely -- its last real step is Schedule
            // (1), not Extras (2). Step -1 (choose setup mode) has no shared Next button at
            // all; its own two option buttons both save the choice and advance on tap, so
            // this handler never actually needs to act on wizardStep == -1.
            int lastStep = SettingsStore.isSimpleSetupMode(this) ? 1 : 2;
            if (wizardStep >= lastStep) completeAndClose();
            else applyWizardStep(wizardStep + 1);
        });

        // New wizard runs start at -1 (choose Simple/Full setup) -- a restored instance
        // (rotation, process death) resumes at whatever step it was actually on, which may
        // still be -1 if the person hadn't picked yet.
        int restoredStep = savedInstanceState != null
                ? savedInstanceState.getInt(KEY_WIZARD_STEP, -1) : -1;
        applyWizardStep(restoredStep);
    }

    private void applyWizardStep(int step) {
        wizardStep = step;
        boolean simple = SettingsStore.isSimpleSetupMode(this);

        // v3.1 flow: [choose Simple/Full] -> University -> Schedule (parser for UPD /
        // manual entry for everyone else -- the merged Edit Class tools live inside this
        // same card) -> Profile & Extras (Full setup only -- Simple ends after Schedule).
        cardSetupMode.setVisibility(step == -1 ? View.VISIBLE : View.GONE);
        cardUniversity.setVisibility(step == 0 ? View.VISIBLE : View.GONE);
        cardImport.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        boolean extrasStep = step == 2;
        cardProfile.setVisibility(View.GONE); // profile editing lives behind the avatar menu now
        cardProfilePrompt.setVisibility(extrasStep ? View.VISIBLE : View.GONE);
        cardForm5.setVisibility(extrasStep ? View.VISIBLE : View.GONE);
        cardMaps.setVisibility(extrasStep ? View.VISIBLE : View.GONE);
        cardSemester.setVisibility(View.GONE); // semester dates moved into EDIT SCHEDULE INFO
        cardReminders.setVisibility(extrasStep ? View.VISIBLE : View.GONE);
        cardTheme.setVisibility(extrasStep ? View.VISIBLE : View.GONE);
        cardNotesSettings.setVisibility(extrasStep ? View.VISIBLE : View.GONE);

        if (step == 1) {
            setEditInfoExpanded(true);
        }

        int totalSteps = simple ? 2 : 3;
        if (step == -1) {
            wizardStepTitle.setText("GET STARTED");
        } else {
            String[] labels = {"YOUR UNIVERSITY", "YOUR SCHEDULE", "PROFILE & EXTRAS"};
            wizardStepTitle.setText("STEP " + (step + 1) + " OF " + totalSteps + " \u00B7 " + labels[step]);
        }
        renderProgressSegments(step, totalSteps);

        // Step -1 (choose Simple/Full) has no shared Back/Next -- its own two option cards
        // both save the choice and advance to step 0 on tap, so the nav row would just be
        // redundant (and Back has nowhere to go from here anyway).
        wizardNavRow.setVisibility(step == -1 ? View.GONE : View.VISIBLE);
        btnWizardBack.setVisibility(step > 0 ? View.VISIBLE : View.GONE);
        int lastStep = simple ? 1 : 2;
        btnWizardNext.setText(step == lastStep ? "FINISH" : "NEXT");
        updateWizardNextEnabled();

        configureRoot.post(() -> {
            if (wizardHeader != null) {
                wizardHeader.requestRectangleOnScreen(
                        new android.graphics.Rect(0, 0, wizardHeader.getWidth(), wizardHeader.getHeight()), true);
            }
        });
    }

    /**
     * v3.0.0 progress indicator: one uniform-thickness horizontal segment per step --
     * completed steps filled with the accent, the current step outlined in it, upcoming
     * steps gray. Replaces the old three fixed dots so any step count renders correctly.
     * totalSteps now varies (2 for Simple setup, 3 for Full) instead of being fixed at 3;
     * currentStep == -1 (the Simple/Full choice screen, before the numbered steps start)
     * naturally renders every segment as upcoming/gray with no extra casing needed, since
     * -1 never satisfies "i < currentStep" or "i == currentStep" for any real segment i.
     */
    private void renderProgressSegments(int currentStep, int totalSteps) {
        LinearLayout row = findViewById(R.id.wizard_progress_row);
        if (row == null) return;
        int accent = Theming.color(this, R.color.ge_accent, R.color.ne_accent, R.color.adaptive_accent);
        int dim = Theming.color(this,
                R.color.ge_line_strong, R.color.ne_line_strong, R.color.adaptive_line_strong);
        float density = getResources().getDisplayMetrics().density;
        row.removeAllViews();
        for (int i = 0; i < totalSteps; i++) {
            View segment = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.round(3 * density), 1f);
            if (i < totalSteps - 1) lp.setMarginEnd(Math.round(6 * density));
            segment.setLayoutParams(lp);
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setCornerRadius(1.5f * density);
            if (i < currentStep) {
                // Completed: half-strength accent.
                shape.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 130));
            } else if (i == currentStep) {
                // Current: the brightest segment.
                shape.setColor(accent);
            } else {
                // Upcoming: neutral gray.
                shape.setColor(dim);
            }
            segment.setBackground(shape);
            row.addView(segment);
        }
    }

    /** Step 2 (Import) can't advance until something's actually been loaded. No-op outside wizard mode. */
    private void updateWizardNextEnabled() {
        if (!inWizardMode) return;
        boolean enabled = wizardStep != 1 || !ScheduleStore.load(this).isEmpty();
        btnWizardNext.setEnabled(enabled);
        btnWizardNext.setAlpha(enabled ? 1f : 0.4f);
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
        WidgetRefreshScheduler.updateAllWidgets(this);
        recreate();
    }

    private void refreshThemeChips() {
        ThemeFamily active = Theming.family(this);
        bindThemeChip(chipThemeGe, active == ThemeFamily.CUSTOM);
        bindThemeChip(chipThemeNe, active == ThemeFamily.NE);
        bindThemeChip(chipThemeAdaptive, active == ThemeFamily.ADAPTIVE);
    }

    private void bindThemeChip(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? drawableChipFilledBg : drawableChipOutlineBg);
        // Selected = text ON the accent fill, so contrast-pick against the accent's own
        // luminance rather than assuming the theme bg pairs with it (Custom can derive a
        // dark accent on a light backdrop, where the old bg-colored label vanished).
        chip.setTextColor(selected ? selectedLabelOn() : colorInkDim);
        CustomThemeBackground.tintChip(this, chip, selected);
    }

    /** The fill behind a "selected" chip is the family accent -- black or white text, whichever reads. */
    private int selectedLabelOn() {
        int accent = Theming.color(this, R.color.ge_accent, R.color.ne_accent, R.color.adaptive_accent);
        return androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.55
                ? 0xFF000000 : 0xFFFFFFFF;
    }

    // ---- v3.0.0 university affiliation -------------------------------------------------------

    /**
     * The circular profile button, pinned to the upper-right of the screen above every card.
     * Shows the user's photo when one is set, otherwise their initials on an accent disc;
     * tapping opens the Profile Card screen. Built programmatically for the same reason as
     * the university card: one implementation instead of four layout forks.
     */
    private void addProfileAvatarButton() {
        float d = getResources().getDisplayMetrics().density;
        int size = Math.round(44 * d);
        int accentDim = Theming.color(this, R.color.ge_accent_dim, R.color.ne_accent_dim, R.color.adaptive_accent_dim);
        int accent = Theming.color(this, R.color.ge_accent, R.color.ne_accent, R.color.adaptive_accent);

        android.widget.FrameLayout avatar = new android.widget.FrameLayout(this);
        avatar.setClickable(true);
        avatar.setFocusable(true);
        avatar.setForeground(getDrawable(R.drawable.ripple_rounded_14dp));
        avatar.setContentDescription("Open profile card");
        avatar.setOnClickListener(v -> startActivity(new Intent(this, ProfileCardActivity.class)));
        android.graphics.drawable.GradientDrawable disc =
                new android.graphics.drawable.GradientDrawable();
        disc.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        disc.setColor(accentDim);
        disc.setStroke(Math.max(1, Math.round(d)), accent);
        avatar.setBackground(disc);
        avatar.setClipToOutline(true);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline outline) {
                outline.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });

        String photoPath = SettingsStore.getProfilePhotoPath(this);
        boolean hasPhoto = false;
        if (photoPath != null) {
            try {
                android.graphics.Bitmap decoded = CustomThemeBackground.decodeFileDownsampled(photoPath, size * 2, size * 2);
                int min = Math.min(decoded.getWidth(), decoded.getHeight());
                android.graphics.Bitmap square = android.graphics.Bitmap.createBitmap(decoded,
                        (decoded.getWidth() - min) / 2, (decoded.getHeight() - min) / 2, min, min);
                android.widget.ImageView photoView = new android.widget.ImageView(this);
                photoView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                photoView.setImageBitmap(android.graphics.Bitmap.createScaledBitmap(square, size, size, true));
                avatar.addView(photoView, new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                hasPhoto = true;
            } catch (Exception | OutOfMemoryError ignored) {
            }
        }
        if (!hasPhoto) {
            TextView initials = new TextView(this);
            initials.setText(initialsFallback());
            initials.setTextColor(accent);
            initials.setTextSize(15f);
            initials.setTypeface(null, android.graphics.Typeface.BOLD);
            initials.setGravity(android.view.Gravity.CENTER);
            avatar.addView(initials, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // Same line as the app title: the intro texts move into a left column and the
        // avatar sits right-aligned beside them, nudged in from the edge.
        if (configureRoot.getChildCount() >= 2) {
            View titleView = configureRoot.getChildAt(0);
            View subtitleView = configureRoot.getChildAt(1);
            configureRoot.removeViews(0, 2);

            LinearLayout titleCol = new LinearLayout(this);
            titleCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            colLp.setMarginEnd(Math.round(10 * d));
            titleCol.setLayoutParams(colLp);
            titleCol.addView(titleView);
            titleCol.addView(subtitleView);

            LinearLayout headerRow = new LinearLayout(this);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            headerRow.addView(titleCol);
            LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(size, size);
            avatarLp.setMarginEnd(Math.round(6 * d)); // pushed in a little from the edge
            avatar.setLayoutParams(avatarLp);
            headerRow.addView(avatar);
            configureRoot.addView(headerRow, 0);
        } else {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.END);
            row.addView(avatar);
            configureRoot.addView(row, 0);
        }
    }

    /**
     * The old PROFILE settings card is repurposed: it no longer collects identity fields
     * (those live in the profile menu) -- it now only asks what should appear on the
     * exported schedule image. Heading/summary retitled; the four entry boxes hidden so
     * just the toggles remain.
     */
    private void tuneProfileCardSection() {
        if (cardProfile == null) cardProfile = findViewById(R.id.card_profile);
        if (cardProfile == null) return;
        ViewGroup profileRoot = (ViewGroup) cardProfile;
        // header child 0 = [texts column]; its TextViews = heading, summary.
        ViewGroup headerRow = profileRoot.getChildCount() > 0
                && profileRoot.getChildAt(0) instanceof ViewGroup
                ? (ViewGroup) profileRoot.getChildAt(0) : null;
        ViewGroup textsCol = null;
        for (int i = 0; headerRow != null && i < headerRow.getChildCount(); i++) {
            View c = headerRow.getChildAt(i);
            if (c instanceof ViewGroup) { textsCol = (ViewGroup) c; break; }
        }
        if (textsCol != null) {
            // Positional, not text-content matching -- this method now runs again on every
            // onResume, and matching on getText().equals("PROFILE") only ever fires once: by
            // the second call the heading already reads "EXPORTED SCHEDULE IMAGE", not
            // "PROFILE", so it fell through to the else branch and got overwritten with the
            // summary text too, leaving both lines identical. The heading is simply whichever
            // TextView comes first in this column; treat it as such regardless of its current text.
            int textViewsSeen = 0;
            for (int i = 0; i < textsCol.getChildCount(); i++) {
                View c = textsCol.getChildAt(i);
                if (c instanceof TextView) {
                    if (textViewsSeen == 0) {
                        ((TextView) c).setText("EXPORTED SCHEDULE IMAGE");
                    } else {
                        ((TextView) c).setText("Choose what appears on your exported schedule image.");
                    }
                    textViewsSeen++;
                }
            }
        }
        ViewGroup body = findViewById(R.id.body_profile);
        if (body == null) return;
        int ink = Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink);
        String[] switchLabels = {"NAME", "STUDENT NUMBER", "DEGREE PROGRAM", "YEAR & STANDING",
                "DORM / ADDRESS", "FACEBOOK", "INSTAGRAM", "TWITTER / X", "LINKEDIN", "WEBSITE / PORTFOLIO"};
        // Same order as switchLabels -- a field with nothing typed into it (checked here,
        // called fresh every time this runs) has nothing to put on the exported image, so its
        // toggle gets greyed out and disabled below rather than letting the person turn on a
        // switch for content that doesn't exist.
        String[] currentValues = {
                SettingsStore.getProfileName(this), SettingsStore.getProfileStudentNo(this),
                SettingsStore.getProfileCourse(this), SettingsStore.getProfileYearStanding(this),
                SettingsStore.getProfileAddress(this), SettingsStore.getProfileFacebook(this),
                SettingsStore.getProfileInstagram(this), SettingsStore.getProfileTwitter(this),
                SettingsStore.getProfileLinkedin(this), SettingsStore.getProfileWebsite(this),
        };
        for (int i = 0; i < body.getChildCount(); i++) {
            View row = body.getChildAt(i);
            if (row instanceof ViewGroup) {
                ViewGroup r = (ViewGroup) row;
                // body_profile's child 0 is a plain description TextView, not a field row --
                // it fails this instanceof check and is skipped, so the field rows actually
                // start at child index 1. switchLabels lines up with those rows in order
                // (index 0..9), not with the raw child index (1..10) -- labelIndex corrects
                // for that offset. (body_profile's XML used to be missing a closing tag right
                // after the Year & Standing row, which silently nested Address/Facebook/
                // Instagram/Twitter/LinkedIn/Website INSIDE that row instead of as its
                // siblings -- this loop only ever saw body_profile's direct children, so it
                // never reached or toggle-ified any of those 6 rows at all. Fixed in the
                // layout XML, not here.)
                int labelIndex = i - 1;

                // Found by TYPE, not fixed position -- this method now runs again on every
                // onResume (so the grey-out state below stays current after editing the
                // profile elsewhere), and the first run's r.addView(label, 0) shifts every
                // subsequent child's index by one. Looking up getChildAt(0)/getChildAt(1)
                // directly, like this used to, finds the label instead of the EditText/Switch
                // starting on the second call and silently no-ops the whole row from then on.
                EditText editText = null;
                SwitchCompat sw = null;
                TextView existingLabel = null;
                for (int c = 0; c < r.getChildCount(); c++) {
                    View child = r.getChildAt(c);
                    if (child instanceof EditText) editText = (EditText) child;
                    else if (child instanceof SwitchCompat) sw = (SwitchCompat) child;
                    else if (child instanceof TextView) existingLabel = (TextView) child;
                }

                // Hide the EditText whose text now lives in the profile menu
                if (editText != null) editText.setVisibility(View.GONE);

                // Reveal the Switch and prepend a label with the field name
                if (sw != null && labelIndex >= 0 && labelIndex < switchLabels.length) {
                    sw.setVisibility(View.VISIBLE);
                    TextView label;
                    if (existingLabel == null || !existingLabel.getText().toString().equals(switchLabels[labelIndex])) {
                        label = new TextView(this);
                        label.setText(switchLabels[labelIndex]);
                        label.setTextColor(ink);
                        label.setTextSize(11f);
                        label.setLetterSpacing(0.1f);
                        label.setTypeface(null, android.graphics.Typeface.BOLD);
                        label.setLayoutParams(new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                        r.addView(label, 0);
                    } else {
                        label = existingLabel;
                    }

                    boolean hasContent = !currentValues[labelIndex].trim().isEmpty();
                    sw.setEnabled(hasContent);
                    float alpha = hasContent ? 1f : 0.4f;
                    sw.setAlpha(alpha);
                    label.setAlpha(alpha);
                    // Force off (not just visually greyed) so the persisted export
                    // preference never says "show" a field that's actually empty -- e.g.
                    // the person had Facebook filled in and toggled it on, then cleared
                    // the field back in the profile menu without remembering to also
                    // flip this switch off.
                    if (!hasContent && sw.isChecked()) sw.setChecked(false);
                }
            }
        }
    }

    /**
     * Falls back to "?" instead of the person-silhouette emoji when there's no name yet --
     * the initials themselves render as plain bold letters, so a full-color emoji glyph here
     * was the one inconsistent element in an otherwise plain-text avatar.
     */
    private String initialsFallback() {
        String name = SettingsStore.getProfileName(this).trim();
        if (name.isEmpty()) return "?";
        String[] parts = name.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() >= 2) break;
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0)));
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    /** Builds the affiliation card programmatically and slots it in ahead of the wizard header. */
    private void setUpUniversityCard() {
        float d = getResources().getDisplayMetrics().density;
        int accent = Theming.color(this, R.color.ge_accent, R.color.ne_accent, R.color.adaptive_accent);
        int ink = Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink);

        cardUniversity = new LinearLayout(this);
        cardUniversity.setOrientation(LinearLayout.VERTICAL);
        cardUniversity.setBackgroundResource(Theming.pick(this,
                R.drawable.card_bg_ge, R.drawable.card_bg_ne, R.drawable.card_bg_adaptive));
        cardUniversity.setPadding(Math.round(18 * d), Math.round(18 * d), Math.round(18 * d), Math.round(18 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(14 * d);
        cardUniversity.setLayoutParams(lp);

        TextView heading = new TextView(this);
        heading.setText("UNIVERSITY");
        heading.setTextColor(colorInkDim);
        heading.setTextSize(12f);
        heading.setLetterSpacing(0.15f);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        cardUniversity.addView(heading);

        universitySummary = new TextView(this);
        universitySummary.setTextColor(colorInkDim);
        universitySummary.setTextSize(11f);
        universitySummary.setPadding(0, Math.round(6 * d), 0, 0);
        cardUniversity.addView(universitySummary);

        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        typeRow.setPadding(0, Math.round(10 * d), 0, 0);
        LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        upLp.setMarginEnd(Math.round(6 * d));
        LinearLayout.LayoutParams otherLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        chipUniUp = new TextView(this);
        chipUniUp.setText("UP STUDENT");
        chipUniUp.setGravity(android.view.Gravity.CENTER);
        chipUniUp.setTextSize(10.5f);
        chipUniUp.setTypeface(null, android.graphics.Typeface.BOLD);
        chipUniUp.setAllCaps(true);
        chipUniUp.setMinimumHeight(Math.round(44 * d));
        chipUniUp.setPadding(Math.round(8 * d), Math.round(8 * d), Math.round(8 * d), Math.round(8 * d));
        chipUniUp.setLayoutParams(upLp);
        chipUniUp.setOnClickListener(v -> onUniversityTypeTapped(SettingsStore.UniversityType.UP));
        typeRow.addView(chipUniUp);
        chipUniOther = new TextView(this);
        chipUniOther.setText("OTHER SCHOOL");
        chipUniOther.setGravity(android.view.Gravity.CENTER);
        chipUniOther.setTextSize(10.5f);
        chipUniOther.setTypeface(null, android.graphics.Typeface.BOLD);
        chipUniOther.setAllCaps(true);
        chipUniOther.setMinimumHeight(Math.round(44 * d));
        chipUniOther.setPadding(Math.round(8 * d), Math.round(8 * d), Math.round(8 * d), Math.round(8 * d));
        chipUniOther.setLayoutParams(otherLp);
        chipUniOther.setOnClickListener(v -> onUniversityTypeTapped(SettingsStore.UniversityType.OTHER));
        typeRow.addView(chipUniOther);
        cardUniversity.addView(typeRow);

        btnUniversityCampus = new TextView(this);
        btnUniversityCampus.setGravity(android.view.Gravity.CENTER);
        btnUniversityCampus.setTextSize(11f);
        btnUniversityCampus.setTypeface(null, android.graphics.Typeface.BOLD);
        btnUniversityCampus.setAllCaps(true);
        btnUniversityCampus.setTextColor(ink);
        btnUniversityCampus.setBackgroundResource(Theming.pick(this,
                R.drawable.row_bg_ge, R.drawable.row_bg_ne, R.drawable.row_bg_adaptive));
        btnUniversityCampus.setForeground(getDrawable(R.drawable.ripple_rounded_14dp));
        btnUniversityCampus.setMinimumHeight(Math.round(44 * d));
        LinearLayout.LayoutParams campusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        campusLp.topMargin = Math.round(10 * d);
        btnUniversityCampus.setLayoutParams(campusLp);
        btnUniversityCampus.setOnClickListener(v -> showCampusPickerDialog());
        cardUniversity.addView(btnUniversityCampus);

        inputManualUniversity = new EditText(this);
        inputManualUniversity.setHint("Your university / college name");
        inputManualUniversity.setTextColor(
                Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink));
        inputManualUniversity.setHintTextColor(colorInkDim);
        inputManualUniversity.setTextSize(11f);
        inputManualUniversity.setBackgroundResource(Theming.pick(this,
                R.drawable.row_bg_ge, R.drawable.row_bg_ne, R.drawable.row_bg_adaptive));
        inputManualUniversity.setPadding(Math.round(12 * d), Math.round(12 * d),
                Math.round(12 * d), Math.round(12 * d));
        LinearLayout.LayoutParams manualLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        manualLp.topMargin = Math.round(10 * d);
        inputManualUniversity.setLayoutParams(manualLp);
        inputManualUniversity.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                SettingsStore.setManualUniversityName(ConfigureActivity.this, s.toString());
                refreshUniversitySummary();
                // Keeps the Maps context field in sync as the person types their school name --
                // seedMapsContextFromUniversity only ever ran on chip/campus taps before, so
                // typing a manual school name here never reached it and Maps context stayed
                // blank (or stale) while typing. Still a no-op once the person has actually
                // edited Maps context themselves -- seedMapsContextFromUniversity tracks its own
                // last auto-written value and stops touching the field the moment it no longer
                // matches that.
                seedMapsContextFromUniversity(SettingsStore.UniversityType.OTHER);
            }
        });
        cardUniversity.addView(inputManualUniversity);

        int insertAt = configureRoot.indexOfChild(wizardHeader != null ? wizardHeader : findViewById(R.id.card_import));
        if (insertAt < 0) insertAt = configureRoot.getChildCount();
        configureRoot.addView(cardUniversity, insertAt);

        refreshUniversityUi(false);
    }

    /**
     * Wizard-only prompt for Step 3 ("Profile & Extras"). Profile editing itself lives in
     * ProfileCardActivity behind the avatar button now, not in this Activity's own view tree
     * at all -- so without this, the step's own card content had nothing to do with the
     * "Profile" half of its title; the avatar button is still there, but as a small icon
     * pinned in a corner, not a guided, explicit part of the onboarding flow a first-time
     * user would necessarily notice. Only built/shown during the wizard -- once onboarding
     * is done, the avatar button alone is the normal, permanent way in.
     */
    private void buildProfilePromptCard() {
        float d = getResources().getDisplayMetrics().density;
        // Not a class field -- colorInkDim is, but the non-dim "ink" color never was; this
        // was mistakenly assumed to already exist as a field (colorInk), which doesn't
        // compile since no such field is declared anywhere in this class.
        int ink = Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink);

        cardProfilePrompt = new LinearLayout(this);
        ((LinearLayout) cardProfilePrompt).setOrientation(LinearLayout.VERTICAL);
        cardProfilePrompt.setBackgroundResource(Theming.pick(this,
                R.drawable.card_bg_ge, R.drawable.card_bg_ne, R.drawable.card_bg_adaptive));
        cardProfilePrompt.setPadding(Math.round(18 * d), Math.round(18 * d), Math.round(18 * d), Math.round(18 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(14 * d);
        cardProfilePrompt.setLayoutParams(lp);

        TextView heading = new TextView(this);
        heading.setText("PROFILE");
        heading.setTextColor(colorInkDim);
        heading.setTextSize(12f);
        heading.setLetterSpacing(0.15f);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        ((LinearLayout) cardProfilePrompt).addView(heading);

        TextView desc = new TextView(this);
        desc.setText("Optional. Add your name, contact info, links, and organizations -- shown "
                + "on your own exportable profile card. You can always come back to this later "
                + "from the profile button in the corner.");
        desc.setTextColor(colorInkDim);
        desc.setTextSize(11f);
        desc.setLineSpacing(0, 1.3f);
        desc.setPadding(0, Math.round(6 * d), 0, Math.round(12 * d));
        ((LinearLayout) cardProfilePrompt).addView(desc);

        TextView openProfileBtn = new TextView(this);
        openProfileBtn.setText("SET UP YOUR PROFILE");
        openProfileBtn.setGravity(android.view.Gravity.CENTER);
        openProfileBtn.setTextSize(11f);
        openProfileBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        openProfileBtn.setAllCaps(true);
        openProfileBtn.setTextColor(ink);
        openProfileBtn.setBackgroundResource(Theming.pick(this,
                R.drawable.row_bg_ge, R.drawable.row_bg_ne, R.drawable.row_bg_adaptive));
        openProfileBtn.setForeground(getDrawable(R.drawable.ripple_rounded_14dp));
        openProfileBtn.setMinimumHeight(Math.round(44 * d));
        openProfileBtn.setOnClickListener(v -> startActivity(new Intent(this, ProfileCardActivity.class)));
        ((LinearLayout) cardProfilePrompt).addView(openProfileBtn);
        CustomThemeBackground.styleControl(this, openProfileBtn, CustomThemeBackground.ControlTier.PRIMARY);

        int profileInsertAt = configureRoot.indexOfChild(cardForm5);
        if (profileInsertAt < 0) profileInsertAt = configureRoot.getChildCount();
        configureRoot.addView(cardProfilePrompt, profileInsertAt);
    }

    /**
     * Wizard's new leading screen (step -1, before University): asks Simple vs. Full setup.
     * Simple skips Profile & Extras (Form5, Maps, Reminders, Theme, Notes, profile prompt)
     * entirely, going straight from Schedule to done -- for someone who just wants their
     * schedule on their home screen right now. Each option button both saves the choice and
     * advances the wizard itself, so there's no separate "confirm" step needed.
     */
    private void buildSetupModeCard() {
        float d = getResources().getDisplayMetrics().density;

        cardSetupMode = new LinearLayout(this);
        ((LinearLayout) cardSetupMode).setOrientation(LinearLayout.VERTICAL);
        cardSetupMode.setBackgroundResource(Theming.pick(this,
                R.drawable.card_bg_ge, R.drawable.card_bg_ne, R.drawable.card_bg_adaptive));
        cardSetupMode.setPadding(Math.round(18 * d), Math.round(18 * d), Math.round(18 * d), Math.round(18 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardSetupMode.setLayoutParams(lp);

        TextView heading = new TextView(this);
        heading.setText("HOW MUCH DO YOU WANT TO SET UP RIGHT NOW?");
        heading.setTextColor(colorInkDim);
        heading.setTextSize(12f);
        heading.setLetterSpacing(0.1f);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        ((LinearLayout) cardSetupMode).addView(heading);

        addSetupModeOption((LinearLayout) cardSetupMode, d,
                "SIMPLE SETUP", "Just your university and schedule -- on your home screen in a "
                        + "couple minutes. Everything else (profile, maps, reminders, theme) is "
                        + "still there any time from Settings.",
                true);
        addSetupModeOption((LinearLayout) cardSetupMode, d,
                "FULL SETUP", "Set up everything now -- profile, maps search, class reminders, "
                        + "app theme, and notes.",
                false);

        int setupInsertAt = configureRoot.indexOfChild(cardUniversity);
        if (setupInsertAt < 0) setupInsertAt = configureRoot.getChildCount();
        configureRoot.addView(cardSetupMode, setupInsertAt);
    }

    private void addSetupModeOption(LinearLayout parent, float d, String title, String desc, boolean simple) {
        LinearLayout option = new LinearLayout(this);
        option.setOrientation(LinearLayout.VERTICAL);
        option.setBackgroundResource(Theming.pick(this,
                R.drawable.row_bg_ge, R.drawable.row_bg_ne, R.drawable.row_bg_adaptive));
        option.setForeground(getDrawable(R.drawable.ripple_rounded_14dp));
        option.setPadding(Math.round(14 * d), Math.round(12 * d), Math.round(14 * d), Math.round(12 * d));
        LinearLayout.LayoutParams optLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        optLp.topMargin = Math.round(12 * d);
        option.setLayoutParams(optLp);
        option.setClickable(true);
        option.setFocusable(true);

        TextView title_ = new TextView(this);
        title_.setText(title);
        title_.setTextColor(Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink));
        title_.setTextSize(13f);
        title_.setTypeface(null, android.graphics.Typeface.BOLD);
        title_.setLetterSpacing(0.05f);
        option.addView(title_);

        TextView descView_ = new TextView(this);
        descView_.setText(desc);
        descView_.setTextColor(colorInkDim);
        descView_.setTextSize(11f);
        descView_.setLineSpacing(0, 1.3f);
        descView_.setPadding(0, Math.round(4 * d), 0, 0);
        option.addView(descView_);

        option.setOnClickListener(v -> {
            SettingsStore.setSimpleSetupMode(this, simple);
            applyWizardStep(0);
        });

        parent.addView(option);
    }

    /**
     * v3.0.0 -- the Edit Class Info tools live INSIDE the import card, directly below the
     * parser (Diliman) or in its place (everyone else), separated by a hairline + caption
     * so they read as one surface with two clear zones. The old standalone card is emptied
     * and hidden; its views keep their ids, so all existing listeners/wiring still work.
     */
    private void mergeEditIntoImportCard() {
        ViewGroup importCard = findViewById(R.id.card_import);
        if (importCard == null) return;
        float d = getResources().getDisplayMetrics().density;
        int inkDim = Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim);
        int ink = Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink);

        TextView title = findViewById(R.id.import_card_title);
        TextView instructions = findViewById(R.id.import_instructions);
        Button pickBtn = findViewById(R.id.btn_pick_file);
        EditText pasteBox = findViewById(R.id.html_input);
        Button clearBtn = findViewById(R.id.btn_clear);
        Button savedBtn = findViewById(R.id.btn_view_history);
        TextView statusBox = findViewById(R.id.status_text);
        Button addClassBtn = findViewById(R.id.btn_add_class);
        LinearLayout editList = findViewById(R.id.edit_class_list);
        SwitchCompat editSwitch = findViewById(R.id.switch_edit_mode);
        Button parseBtn = findViewById(R.id.btn_parse);

        // Unpack: everything out of BOTH cards -- the parser card AND the standalone
        // Edit Class Info card whose tools are being merged in. (The Edit tools are NOT
        // inside card_import in the XML; forgetting this step is the "child already has
        // a parent" crash.) The emptied Edit card is hidden for good.
        List<View> parts = new ArrayList<>();
        ViewGroup editCard = findViewById(R.id.card_edit_class_info);
        if (editCard != null) {
            while (editCard.getChildCount() > 0) {
                View child = editCard.getChildAt(0);
                editCard.removeViewAt(0);
                parts.add(child);
            }
            editCard.setVisibility(View.GONE);
        }
        while (importCard.getChildCount() > 0) {
            View child = importCard.getChildAt(0);
            importCard.removeViewAt(0);
            parts.add(child);
        }

        View descView = null;
        View switchRow = editSwitch.getParent() instanceof View ? (View) editSwitch.getParent() : null;
        if (switchRow != null) {
            int idx = parts.indexOf(switchRow);
            if (idx >= 0 && idx + 1 < parts.size()) descView = parts.get(idx + 1);
            parts.remove(switchRow); // toggle retired -- simple dropdown below
        }

        // Detach every view we're about to re-parent (addView throws otherwise).
        ViewGroup parseRow = parseBtn.getParent() instanceof ViewGroup
                ? (ViewGroup) parseBtn.getParent() : null;
        if (parseRow != null) parseRow.removeView(parseBtn);
        ViewGroup clearOldRow = clearBtn.getParent() instanceof ViewGroup
                ? (ViewGroup) clearBtn.getParent() : null;
        // NOTE: parseBtn and clearBtn are siblings in the SAME row in the XML, so
        // clearOldRow == parseRow here -- that used to skip this removeView() call
        // (guarded by "clearOldRow != parseRow"), leaving clearBtn still parented
        // to the row when pairRow.addView(clearBtn) ran below. That's the exact
        // "child already has a parent" crash. Detaching parseBtn above does NOT
        // detach its sibling clearBtn, so this must run unconditionally.
        if (clearOldRow != null) clearOldRow.removeView(clearBtn);

        boolean crs = SettingsStore.isCrsParserAvailable(this);

        java.util.function.BiConsumer<View, Integer> attach = (v, topDp) -> {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(topDp * d);
            v.setLayoutParams(lp);
            importCard.addView(v);
        };

        attach.accept(title, 0);
        attach.accept(instructions, 10);

        // Both the parser flow (pasteBox/pickBtn/parseBtn/statusBox) and the manual-entry
        // flow (addClassBtn) must stay attached to the tree UNCONDITIONALLY here -- only
        // ONE of the two used to get re-attached, based on crs, and the other stayed
        // permanently orphaned (removed above, never added back anywhere). An orphaned
        // view is unreachable by findViewById(), so onCreateBody()'s later unconditional
        // findViewById(R.id.btn_pick_file)/(R.id.btn_add_class) + setOnClickListener()
        // NPE'd depending on which branch ran (crs=false orphans the parser controls --
        // the default for every fresh install, since university type starts unanswered).
        // Tree membership must never depend on crs; only VISIBILITY may, via crsViews
        // below (parser controls).
        pasteBox.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(140 * d)));
        importCard.addView(pasteBox);
        attach.accept(pickBtn, 8);
        attach.accept(parseBtn, 8);
        attach.accept(statusBox, 8);

        // Populates the visibility-gating list applyImportGating() reads -- previously
        // declared but never filled in, so the parser controls never actually hid
        // themselves for non-CRS users even before this method's crash was fixed.
        crsViews.clear();
        crsViews.add(pasteBox);
        crsViews.add(pickBtn);
        crsViews.add(parseBtn);
        crsViews.add(statusBox);

        // Secondary pair -- |CLEAR| |SAVED SCHEDULES| -- deliberately smaller than the
        // primary rectangles above, side by side, in every mode. CLEAR is destructive red.
        LinearLayout pairRow = new LinearLayout(this);
        pairRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clearBtn.setLayoutParams(clearLp);
        pairRow.addView(clearBtn);
        LinearLayout.LayoutParams savedLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        savedLp.setMarginStart(Math.round(8 * d));
        savedBtn.setLayoutParams(savedLp);
        pairRow.addView(savedBtn);
        LinearLayout.LayoutParams pairLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pairLp.topMargin = Math.round(crs ? 12 : 12) * 1;
        importCard.addView(pairRow, pairLp);

        View divider = new View(this);
        divider.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(inkDim, 70));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(d)));
        divLp.topMargin = Math.round(14 * d);
        importCard.addView(divider, divLp);

        // v3.0.0 -- EDIT SCHEDULE INFO gets its own nested rectangle inside the "add your
        // classes" card, instead of sitting as plain unstyled rows directly in importCard.
        // Semester dates move in here too (their old standalone settings card is retired
        // below), so this one dropdown now covers everything about "how this schedule is
        // set up": when it runs, and which classes are in it.
        java.util.List<View> semesterViews = new java.util.ArrayList<>();
        while (bodySemester.getChildCount() > 0) {
            View child = bodySemester.getChildAt(0);
            bodySemester.removeViewAt(0);
            semesterViews.add(child);
        }
        // cardSemester isn't assigned as a field until later in onCreateBody (it's read
        // from the Extras step's card list further down) -- this method runs earlier, so
        // fetch it directly here rather than relying on the not-yet-set field. Matches the
        // same defensive re-fetch applyImportGating() already does for cardImport.
        View cardSemesterView = cardSemester != null ? cardSemester : findViewById(R.id.card_semester);
        cardSemesterView.setVisibility(View.GONE);
        cardSemester = cardSemesterView;

        LinearLayout scheduleInfoCard = new LinearLayout(this);
        scheduleInfoCard.setOrientation(LinearLayout.VERTICAL);
        scheduleInfoCard.setBackgroundResource(Theming.pick(this,
                R.drawable.card_bg_ge, R.drawable.card_bg_ne, R.drawable.card_bg_adaptive));

        // Now that this header genuinely sits inside its own nested card (not just as a
        // plain row inside importCard), it gets the full header_maps-style 18dp uniform
        // padding, matching how every other "header inside its own card" looks.
        LinearLayout dropHeader = new LinearLayout(this);
        dropHeader.setOrientation(LinearLayout.HORIZONTAL);
        dropHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        dropHeader.setClickable(true);
        dropHeader.setFocusable(true);
        dropHeader.setForeground(getDrawable(R.drawable.ripple_rounded_22dp));
        int hPad = Math.round(18 * d);
        dropHeader.setPadding(hPad, hPad, hPad, hPad);
        dropHeader.setMinimumHeight(Math.round(48 * d));
        TextView dropLabel = new TextView(this);
        dropLabel.setText("EDIT SCHEDULE INFO");
        dropLabel.setTextColor(ink);
        dropLabel.setTextSize(12f);
        dropLabel.setLetterSpacing(0.15f);
        dropLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        dropLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        dropHeader.addView(dropLabel);
        editChevron = new ImageView(this);
        editChevron.setImageResource(Theming.pick(this,
                R.drawable.ic_chevron_down_ge, R.drawable.ic_chevron_down_ne, R.drawable.ic_chevron_down_adaptive));
        editChevron.setLayoutParams(new LinearLayout.LayoutParams(Math.round(20 * d), Math.round(20 * d)));
        dropHeader.addView(editChevron);
        // This was the "doesn't function" bug: dropHeader was clickable/focusable with a
        // ripple foreground (so tapping it visibly animates) but never actually had a
        // listener attached, so nothing happened afterward. Tap either the row or the
        // label to toggle, matching the profile screen's own EDIT PROFILE & UNIVERSITY
        // header (editHeader/editLabel in ProfileCardActivity). Also now animated the same
        // way the other settings dropdowns are, via beginRootTransition().
        View.OnClickListener toggleEditInfo = v -> {
            beginRootTransition();
            setEditInfoExpanded(!editInfoExpanded);
        };
        dropHeader.setOnClickListener(toggleEditInfo);
        dropLabel.setOnClickListener(toggleEditInfo);
        editChevron.setClickable(false);
        scheduleInfoCard.addView(dropHeader);

        editScheduleInfoBody = new LinearLayout(this);
        editScheduleInfoBody.setOrientation(LinearLayout.VERTICAL);
        editScheduleInfoBody.setVisibility(View.GONE);
        editScheduleInfoBody.setPadding(hPad, 0, hPad, hPad);
        for (View v : semesterViews) editScheduleInfoBody.addView(v);

        View semesterDivider = new View(this);
        semesterDivider.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(inkDim, 70));
        LinearLayout.LayoutParams semDivLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(d)));
        semDivLp.topMargin = Math.round(14 * d);
        semDivLp.bottomMargin = Math.round(14 * d);
        editScheduleInfoBody.addView(semesterDivider, semDivLp);

        // Explains that classes -- whether parsed or manually added -- are tap-to-edit, any
        // time. This used to be hidden in both branches below (the CRS branch's own
        // reasoning was "Diliman keeps its desc under the parser instructions instead" --
        // but those instructions only cover the INITIAL import; they never mention that a
        // parsed class can be corrected afterward either). With no visible explanation
        // anywhere, tapping a class row to edit it went undiscovered despite already
        // working correctly -- exactly the "only add class, no way to edit" gap this fixes.
        if (descView != null) {
            editScheduleInfoBody.addView(descView);
        }
        // "+ ADD CLASS" belongs here, not in the outer import card -- this dropdown IS
        // "edit/add the classes" (its own instructional text says so: "any time -- or add
        // a new one from scratch"). It previously ended up detached into importCard with
        // visibility inverted to CRS status (hidden for CRS/UP students entirely), which
        // left EDIT SCHEDULE INFO able to edit existing rows but never add one for anyone
        // using the parser -- the exact regression this restores. Manual add is a
        // legitimate supplement to parsed classes for every affiliation, not just non-CRS
        // schools, so it's unconditionally visible now; applyImportGating() no longer
        // touches it.
        LinearLayout.LayoutParams addClassLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addClassLp.topMargin = Math.round(12 * d);
        addClassBtn.setLayoutParams(addClassLp);
        editScheduleInfoBody.addView(addClassBtn);
        editScheduleInfoBody.addView(editList);
        // editList's own XML declaration has android:visibility="gone" baked in -- a
        // leftover from before this merge, when the class list was individually toggled by
        // its OWN visibility (setEditInfoExpanded's now-dead "else if (editClassList !=
        // null)" branch below). Visibility control moved to editScheduleInfoBody (the
        // parent) instead, but nothing ever flipped this child's stale "gone" back to
        // "visible" to match -- so no matter how many classes renderEditList() populated it
        // with, the list itself stayed permanently invisible, regardless of the parent
        // being shown. The parent's own visibility is what actually gates this section now,
        // so the child just needs to not be independently hidden underneath it.
        editList.setVisibility(View.VISIBLE);
        scheduleInfoCard.addView(editScheduleInfoBody);

        attach.accept(scheduleInfoCard, 12);

        setEditInfoExpanded(false);
    }

    /** Expands/collapses the EDIT CLASS INFO dropdown; replaces the old switch. */
    private void setEditInfoExpanded(boolean expanded) {
        editInfoExpanded = expanded;
        // editScheduleInfoBody now holds both the semester-date controls and the class
        // list together (see mergeEditIntoImportCard) -- toggle the whole nested body, not
        // just the class list, or semester dates would stay permanently visible/hidden
        // regardless of this dropdown's state.
        if (editScheduleInfoBody != null) {
            editScheduleInfoBody.setVisibility(expanded ? View.VISIBLE : View.GONE);
        } else if (editClassList != null) {
            editClassList.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        if (editChevron != null) {
            editChevron.setRotation(editInfoExpanded ? 180f : 0f);
        }
    }

    /** Switching affiliation asks once when there's real data behind the change; then re-gates import UI. */
    private void onUniversityTypeTapped(SettingsStore.UniversityType tapped) {
        SettingsStore.UniversityType current = SettingsStore.getUniversityType(this);
        boolean hasRealData = !ScheduleStore.load(this).isEmpty();
        boolean noChange = current == null || current == tapped;
        Runnable apply = () -> {
            SettingsStore.setUniversityType(this, tapped);
            refreshUniversityUi(true);
            applyImportGating();
        };
        if (noChange || !hasRealData) {
            apply.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Change university?")
                .setMessage("Your schedules, notes, and settings will be preserved. Continue?")
                .setPositiveButton("CONTINUE", (dlg, w) -> apply.run())
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void showCampusPickerDialog() {
        SettingsStore.UpCampus[] campuses = SettingsStore.UpCampus.values();
        String[] labels = new String[campuses.length];
        for (int i = 0; i < campuses.length; i++) labels[i] = campuses[i].displayName + "  (" + campuses[i].location + ")";
        int checkedIndex = SettingsStore.getUpCampus(this).ordinal();
        new AlertDialog.Builder(this)
                .setTitle("Which campus?")
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    confirmAndSetCampus(campuses[which]);
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void confirmAndSetCampus(SettingsStore.UpCampus picked) {
        if (picked == SettingsStore.getUpCampus(this)) return;
        Runnable apply = () -> {
            SettingsStore.setUpCampus(this, picked);
            refreshUniversityUi(true);
            applyImportGating();
        };
        if (!ScheduleStore.load(this).isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Switch to " + picked.displayName + "?")
                    .setMessage("Your data will be preserved. The CRS auto-import"
                            + (picked == SettingsStore.UpCampus.UPD ? "" : " is only available on UP Diliman")
                            + ". Continue?")
                    .setPositiveButton("CONTINUE", (dlg, w) -> apply.run())
                    .setNegativeButton("CANCEL", null)
                    .show();
        } else {
            apply.run();
        }
    }

    /** Re-renders chips/campus button/manual field/summary from stored state. */
    private void refreshUniversityUi(boolean alsoGateImport) {
        SettingsStore.UniversityType type = SettingsStore.getUniversityType(this);
        boolean isUp = type == SettingsStore.UniversityType.UP;
        bindThemeChip(chipUniUp, isUp);
        bindThemeChip(chipUniOther, type == SettingsStore.UniversityType.OTHER);
        btnUniversityCampus.setVisibility(isUp ? View.VISIBLE : View.GONE);
        btnUniversityCampus.setText(SettingsStore.getUpCampus(this).displayName + "  \u00b7  TAP TO CHANGE");
        inputManualUniversity.setVisibility(type == SettingsStore.UniversityType.OTHER ? View.VISIBLE : View.GONE);
        if (!isUp && type == SettingsStore.UniversityType.OTHER
                && !inputManualUniversity.getText().toString().equals(SettingsStore.getManualUniversityName(this))) {
            inputManualUniversity.setText(SettingsStore.getManualUniversityName(this));
        }
        seedMapsContextFromUniversity(type);
        refreshUniversitySummary();
        CustomThemeBackground.applySubtree(this, cardUniversity);
        if (alsoGateImport) applyImportGating();
    }

    /**
     * Maps context previously had to be typed in by hand even though the person had already
     * told the app their school during onboarding. Seeds it from that selection -- never
     * overwrites text the person has actually edited themselves (tracked via
     * {@link #lastAutoSeededMapsContext}: the field is fair game to keep auto-updating as long
     * as it's empty OR still holds exactly what auto-seeding last put there; the moment it holds
     * anything else, the person has taken over and this stops touching it). Re-runs on every
     * university/campus change AND on every keystroke in the manual-school field, so it stays
     * live-synced while the person is still typing their school name, not just seeded once.
     */
    private void seedMapsContextFromUniversity(SettingsStore.UniversityType type) {
        if (inputCampus == null) return;
        String current = inputCampus.getText().toString().trim();
        if (!current.isEmpty() && !current.equals(lastAutoSeededMapsContext)) return; // person's own edit -- leave it alone
        String seed;
        if (type == SettingsStore.UniversityType.UP) {
            SettingsStore.UpCampus campus = SettingsStore.getUpCampus(this);
            seed = campus.displayName + ", " + campus.location;
        } else if (type == SettingsStore.UniversityType.OTHER) {
            seed = SettingsStore.getManualUniversityName(this).trim();
        } else {
            return; // not yet answered -- nothing to seed from
        }
        if (seed.isEmpty() || seed.equals(current)) return;
        inputCampus.setText(seed);
        lastAutoSeededMapsContext = seed;
        SettingsStore.setCampusHint(this, seed);
        updateMapsSummary();
    }

    private void refreshUniversitySummary() {
        SettingsStore.UniversityType type = SettingsStore.getUniversityType(this);
        String text;
        if (type == null) {
            text = "Pick your school to set up the right import tools.";
        } else if (type == SettingsStore.UniversityType.UP) {
            SettingsStore.UpCampus campus = SettingsStore.getUpCampus(this);
            text = campus.displayName + " \u00b7 " + campus.location
                    + (campus == SettingsStore.UpCampus.UPD
                       ? " \u00b7 CRS auto-import enabled"
                       : " \u00b7 manual & .ics import");
        } else {
            String name = SettingsStore.getManualUniversityName(this);
            text = name.isEmpty() ? "Non-UP school \u00b7 manual & .ics import" : name + " \u00b7 manual & .ics import";
        }
        universitySummary.setText(text);
    }

    private void showCustomThemeDialog(boolean forcePhotoTab) {
        if (activeCustomThemeDialog != null && activeCustomThemeDialog.isShowing()) {
            activeCustomThemeDialog.dismiss();
        }
        int padPx = Math.round(16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padPx, padPx, padPx, padPx);

        // Mode toggle
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView photoTab = new TextView(this);
        TextView colorTab = new TextView(this);
        for (TextView tab : new TextView[]{photoTab, colorTab}) {
            tab.setGravity(android.view.Gravity.CENTER);
            tab.setPadding(padPx, padPx / 2, padPx, padPx / 2);
            tab.setTextColor(colorInkDim);
            tab.setTextSize(11f);
            tab.setTypeface(tab.getTypeface(), android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(Math.round(6 * getResources().getDisplayMetrics().density));
            tab.setLayoutParams(lp);
        }
        photoTab.setText("PHOTO");
        colorTab.setText("COLOR");
        modeRow.addView(photoTab);
        modeRow.addView(colorTab);
        root.addView(modeRow);

        // Color section
        LinearLayout colorSection = new LinearLayout(this);
        colorSection.setOrientation(LinearLayout.VERTICAL);
        colorSection.setPadding(0, padPx, 0, 0);

        TextView primaryLabel = new TextView(this);
        primaryLabel.setText("PRIMARY COLOR");
        primaryLabel.setTextColor(colorInkDim);
        primaryLabel.setTextSize(10.5f);
        colorSection.addView(primaryLabel);

        EditText hexInput = new EditText(this);
        hexInput.setHint("#7A0019");
        hexInput.setTextColor(colorInkDim);
        hexInput.setText(String.format(Locale.US, "#%06X", (0xFFFFFF & SettingsStore.getCustomColorPrimary(this))));
        View swatch = new View(this);
        LinearLayout.LayoutParams swatchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(40 * getResources().getDisplayMetrics().density));
        swatchLp.topMargin = Math.round(10 * getResources().getDisplayMetrics().density);
        swatch.setLayoutParams(swatchLp);
        swatch.setBackgroundColor(SettingsStore.getCustomColorPrimary(this));
        hexInput.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                Integer parsed = parseHexColor(s.toString());
                if (parsed != null) swatch.setBackgroundColor(parsed);
            }
        });
        colorSection.addView(hexInput);
        colorSection.addView(swatch);

        // Optional complementary accent -- CustomThemeBackground.colorForRole() already
        // reads getCustomColorAccent() for buttons/highlights in Color mode, falling back
        // to the primary when unset (0). That data path existed with no UI ever able to
        // set it, so every Color-mode theme was silently monochromatic. Blank clears it
        // back to "derive from primary" (unchanged fallback behavior).
        TextView accentLabel = new TextView(this);
        accentLabel.setText("ACCENT COLOR -- OPTIONAL");
        accentLabel.setTextColor(colorInkDim);
        accentLabel.setTextSize(10.5f);
        accentLabel.setPadding(0, padPx, 0, 0);
        colorSection.addView(accentLabel);

        EditText accentHexInput = new EditText(this);
        accentHexInput.setHint("Leave blank to auto-derive");
        accentHexInput.setTextColor(colorInkDim);
        int savedAccent = SettingsStore.getCustomColorAccent(this);
        accentHexInput.setText(savedAccent == 0 ? "" : String.format(Locale.US, "#%06X", (0xFFFFFF & savedAccent)));
        View accentSwatch = new View(this);
        LinearLayout.LayoutParams accentSwatchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(40 * getResources().getDisplayMetrics().density));
        accentSwatchLp.topMargin = Math.round(10 * getResources().getDisplayMetrics().density);
        accentSwatch.setLayoutParams(accentSwatchLp);
        accentSwatch.setBackgroundColor(savedAccent != 0 ? savedAccent : SettingsStore.getCustomColorPrimary(this));
        accentHexInput.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                if (s.toString().trim().isEmpty()) {
                    accentSwatch.setBackgroundColor(SettingsStore.getCustomColorPrimary(ConfigureActivity.this));
                    return;
                }
                Integer parsed = parseHexColor(s.toString());
                if (parsed != null) accentSwatch.setBackgroundColor(parsed);
            }
        });
        colorSection.addView(accentHexInput);
        colorSection.addView(accentSwatch);
        root.addView(colorSection);

        // Photo section
        LinearLayout photoSection = new LinearLayout(this);
        photoSection.setOrientation(LinearLayout.VERTICAL);
        photoSection.setPadding(0, padPx, 0, 0);
        photoSection.setVisibility(View.GONE);

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(120 * getResources().getDisplayMetrics().density));
        preview.setLayoutParams(previewLp);
        preview.setBackgroundColor(colorInkDim);

        // Decoded once per dialog build (not re-read from disk on every slider tick) so the
        // opacity/blur sliders can preview live without redundant disk I/O per drag frame.
        android.graphics.Bitmap[] previewBaseBitmap = {null};
        String existingPhotoPath = SettingsStore.getCustomPhotoPath(this);
        if (existingPhotoPath != null) {
            try {
                int maxDimPx = Math.round(240 * getResources().getDisplayMetrics().density);
                previewBaseBitmap[0] = CustomThemeBackground.decodeFileDownsampled(existingPhotoPath, maxDimPx, maxDimPx);
            } catch (Exception | OutOfMemoryError e) {
                Toast.makeText(this, "Couldn't load that photo: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        photoSection.addView(preview);

        // Proper chip buttons instead of bare text links -- CHOOSE as the standard
        // Secondary outlined control, REMOVE as a quiet destructive outline in the
        // error tone. Both get real touch targets and the app's 12dp silhouette.
        float btnDensity = getResources().getDisplayMetrics().density;
        TextView choosePhotoBtn = new TextView(this);
        choosePhotoBtn.setText("+ CHOOSE PHOTO");
        choosePhotoBtn.setGravity(android.view.Gravity.CENTER);
        choosePhotoBtn.setAllCaps(true);
        choosePhotoBtn.setTextSize(11f);
        choosePhotoBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        choosePhotoBtn.setLetterSpacing(0.1f);
        choosePhotoBtn.setMinimumHeight(Math.round(44 * btnDensity));
        choosePhotoBtn.setPadding(Math.round(12 * btnDensity), Math.round(10 * btnDensity),
                Math.round(12 * btnDensity), Math.round(10 * btnDensity));
        LinearLayout.LayoutParams chooseLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chooseLp.topMargin = Math.round(12 * btnDensity);
        choosePhotoBtn.setLayoutParams(chooseLp);
        choosePhotoBtn.setForeground(getDrawable(R.drawable.ripple_rounded_12dp));
        // Built programmatically inside the theme dialog, so retintTree never reaches
        // it -- styleControl guarantees the Secondary outline look under Custom, and the
        // plain row_bg keeps it presentable for every other family.
        choosePhotoBtn.setBackgroundResource(R.drawable.row_bg);
        CustomThemeBackground.styleControl(this, choosePhotoBtn, CustomThemeBackground.ControlTier.SECONDARY);
        choosePhotoBtn.setOnClickListener(v -> customThemePhotoPicker.launch(
                new androidx.activity.result.PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        photoSection.addView(choosePhotoBtn);

        TextView removePhotoBtn = new TextView(this);
        removePhotoBtn.setText("REMOVE PHOTO");
        removePhotoBtn.setGravity(android.view.Gravity.CENTER);
        removePhotoBtn.setAllCaps(true);
        removePhotoBtn.setTextColor(colorError);
        removePhotoBtn.setTextSize(11f);
        removePhotoBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        removePhotoBtn.setLetterSpacing(0.1f);
        removePhotoBtn.setMinimumHeight(Math.round(40 * btnDensity));
        removePhotoBtn.setPadding(Math.round(12 * btnDensity), Math.round(8 * btnDensity),
                Math.round(12 * btnDensity), Math.round(8 * btnDensity));
        LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        removeLp.topMargin = Math.round(8 * btnDensity);
        removePhotoBtn.setLayoutParams(removeLp);
        removePhotoBtn.setForeground(getDrawable(R.drawable.ripple_rounded_12dp));
        removePhotoBtn.setBackgroundResource(R.drawable.row_bg);
        removePhotoBtn.setVisibility(existingPhotoPath != null ? View.VISIBLE : View.GONE);
        removePhotoBtn.setOnClickListener(v -> {
            new File(getFilesDir(), "custom_theme_photo.jpg").delete();
            SettingsStore.setCustomPhotoPath(this, null);
            SettingsStore.setCustomPhotoDerivedPrimary(this, 0);
            SettingsStore.setCustomPhotoDerivedAccent(this, 0);
            showCustomThemeDialog(true);
        });
        // Same quiet outline as CHOOSE, but the label keeps the semantic error tone.
        CustomThemeBackground.styleControl(this, removePhotoBtn, CustomThemeBackground.ControlTier.SECONDARY);
        removePhotoBtn.setTextColor(colorError);
        photoSection.addView(removePhotoBtn);

        if (previewBaseBitmap[0] != null) {
            TextView paletteLabel = new TextView(this);
            paletteLabel.setText("ACCENT COLOR -- FROM PHOTO");
            paletteLabel.setTextColor(colorInkDim);
            paletteLabel.setTextSize(10.5f);
            paletteLabel.setPadding(0, padPx, 0, 0);
            photoSection.addView(paletteLabel);

            LinearLayout swatchRow = new LinearLayout(this);
            swatchRow.setOrientation(LinearLayout.HORIZONTAL);
            swatchRow.setPadding(0, Math.round(6 * getResources().getDisplayMetrics().density), 0, 0);
            photoSection.addView(swatchRow);

            int[] candidates = CustomThemeBackground.extractPaletteCandidates(previewBaseBitmap[0], 6);
            int currentAccent = SettingsStore.getCustomPhotoDerivedAccent(this);
            int swatchSize = Math.round(32 * getResources().getDisplayMetrics().density);
            int swatchMargin = Math.round(8 * getResources().getDisplayMetrics().density);
            View[] swatchViews = new View[candidates.length];

            for (int i = 0; i < candidates.length; i++) {
                int color = candidates[i];
                View paletteSwatch = new View(this);
                LinearLayout.LayoutParams swatchLpEach = new LinearLayout.LayoutParams(swatchSize, swatchSize);
                swatchLpEach.setMarginEnd(swatchMargin);
                paletteSwatch.setLayoutParams(swatchLpEach);
                android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
                dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                dot.setColor(color);
                dot.setStroke(Math.round((color == currentAccent ? 3 : 1) * getResources().getDisplayMetrics().density),
                        color == currentAccent ? 0xFFFFFFFF : 0x55FFFFFF);
                paletteSwatch.setBackground(dot);
                swatchViews[i] = paletteSwatch;
                final int colorForTap = color;
                paletteSwatch.setOnClickListener(v -> {
                    SettingsStore.setCustomPhotoDerivedAccent(this, colorForTap);
                    for (View sw : swatchViews) {
                        android.graphics.drawable.GradientDrawable d = (android.graphics.drawable.GradientDrawable) sw.getBackground();
                        boolean selected = sw == v;
                        d.setStroke(Math.round((selected ? 3 : 1) * getResources().getDisplayMetrics().density),
                                selected ? 0xFFFFFFFF : 0x55FFFFFF);
                    }
                });
                swatchRow.addView(paletteSwatch);
            }
        }

        TextView opacityLabel = new TextView(this);
        opacityLabel.setTextColor(colorInkDim);
        opacityLabel.setTextSize(10.5f);
        opacityLabel.setPadding(0, padPx, 0, 0);
        SeekBar opacitySeek = new SeekBar(this);
        opacitySeek.setMax(80); // maps to 10-90
        int initialOpacity = SettingsStore.getCustomPhotoOpacity(this);
        opacitySeek.setProgress(initialOpacity - 10);
        opacityLabel.setText("PHOTO OPACITY -- " + initialOpacity + "%");
        opacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                opacityLabel.setText("PHOTO OPACITY -- " + (progress + 10) + "%");
                preview.setImageAlpha(Math.round((progress + 10) / 100f * 255));
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        photoSection.addView(opacityLabel);
        photoSection.addView(opacitySeek);

        TextView blurLabel = new TextView(this);
        blurLabel.setTextColor(colorInkDim);
        blurLabel.setTextSize(10.5f);
        blurLabel.setPadding(0, padPx / 2, 0, 0);
        SeekBar blurSeek = new SeekBar(this);
        blurSeek.setMax(25);
        int initialBlur = SettingsStore.getCustomPhotoBlur(this);
        blurSeek.setProgress(initialBlur);
        blurLabel.setText("BACKGROUND BLUR -- " + initialBlur);
        blurSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                blurLabel.setText("BACKGROUND BLUR -- " + progress);
                refreshPreviewBlur(preview, previewBaseBitmap[0], progress, opacitySeek.getProgress() + 10);
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        photoSection.addView(blurLabel);
        photoSection.addView(blurSeek);

        // Initial paint, matching whatever the sliders start at.
        refreshPreviewBlur(preview, previewBaseBitmap[0], initialBlur, initialOpacity);

        root.addView(photoSection);

        boolean[] activeModeIsPhoto = {forcePhotoTab || SettingsStore.getCustomThemeMode(this) == SettingsStore.CustomThemeMode.PHOTO};
        Runnable refreshTabs = () -> {
            bindThemeChip(photoTab, activeModeIsPhoto[0]);
            bindThemeChip(colorTab, !activeModeIsPhoto[0]);
            photoSection.setVisibility(activeModeIsPhoto[0] ? View.VISIBLE : View.GONE);
            colorSection.setVisibility(activeModeIsPhoto[0] ? View.GONE : View.VISIBLE);
        };
        photoTab.setOnClickListener(v -> { activeModeIsPhoto[0] = true; refreshTabs.run(); });
        colorTab.setOnClickListener(v -> { activeModeIsPhoto[0] = false; refreshTabs.run(); });
        refreshTabs.run();

        activeCustomThemeDialog = new AlertDialog.Builder(this)
                .setTitle("Custom Theme")
                .setView(root)
                .setPositiveButton("SAVE", (d, w) -> {
                    if (activeModeIsPhoto[0]) {
                        if (SettingsStore.getCustomPhotoPath(this) == null) {
                            Toast.makeText(this, "Choose a photo first.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        SettingsStore.setCustomThemeMode(this, SettingsStore.CustomThemeMode.PHOTO);
                        SettingsStore.setCustomPhotoOpacity(this, opacitySeek.getProgress() + 10);
                        SettingsStore.setCustomPhotoBlur(this, blurSeek.getProgress());
                    } else {
                        Integer parsed = parseHexColor(hexInput.getText().toString());
                        if (parsed == null) {
                            Toast.makeText(this, "That doesn't look like a hex color, e.g. #7A0019.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String accentRaw = accentHexInput.getText().toString().trim();
                        if (accentRaw.isEmpty()) {
                            SettingsStore.setCustomColorAccent(this, 0); // cleared -> auto-derive
                        } else {
                            Integer parsedAccent = parseHexColor(accentRaw);
                            if (parsedAccent == null) {
                                Toast.makeText(this, "Accent doesn't look like a hex color, e.g. #FFD700.", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            SettingsStore.setCustomColorAccent(this, parsedAccent);
                        }
                        SettingsStore.setCustomThemeMode(this, SettingsStore.CustomThemeMode.COLOR);
                        SettingsStore.setCustomColorPrimary(this, parsed);
                    }
                    // Not onThemeFamilyPicked(CUSTOM) -- that no-ops if already on Custom, but a
                    // save here can change color/photo/opacity/blur even while already active.
                    SettingsStore.setThemeFamily(this, ThemeFamily.CUSTOM);
                    WidgetRefreshScheduler.updateAllWidgets(this);
                    recreate();
                })
                .setNegativeButton("CANCEL", null)
                .show();
        CustomThemeBackground.applyToDialog(activeCustomThemeDialog);
        CustomThemeBackground.styleDialogButtons(this, activeCustomThemeDialog);
    }

    /**
     * Loads a photo into a preview ImageView WITHOUT ever decoding it at full resolution --
     * ImageView.setImageURI(uri) does that on the main thread and is a real OutOfMemoryError
     * source for a modern camera photo (routinely 12-50MP). Degrades to a toast instead of
     * crashing if the file is unreadable or (still, even downsampled) too large to decode.
     */
    /**
     * Live opacity/blur preview for the Custom Theme dialog's Photo tab. Reprocesses from the
     * already-decoded base bitmap (never re-reads from disk) so dragging the sliders doesn't
     * cause repeated disk I/O, and composites through the SAME pipeline as every real surface
     * (base fill -> photo at opacity -> scrim) -- the preview shows what the widget/screen will
     * actually look like at these settings, instead of a raw uncomposited crop.
     */
    private void refreshPreviewBlur(ImageView preview, android.graphics.Bitmap base, int blur, int opacityPct) {
        if (base == null) return;
        preview.setImageBitmap(CustomThemeBackground.buildThemePreviewBitmap(this, base, blur, opacityPct));
    }

    private void showPreviewImageSafely(ImageView target, String filePath) {
        if (target == null) return;
        try {
            int maxDimPx = Math.round(240 * getResources().getDisplayMetrics().density);
            android.graphics.Bitmap bmp = CustomThemeBackground.decodeFileDownsampled(filePath, maxDimPx, maxDimPx);
            target.setImageBitmap(bmp);
        } catch (Exception | OutOfMemoryError e) {
            Toast.makeText(this, "Couldn't load that photo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Accepts "#RRGGBB" or "RRGGBB"; null if it doesn't parse. */
    private static Integer parseHexColor(String raw) {
        if (raw == null) return null;
        String hex = raw.trim().startsWith("#") ? raw.trim().substring(1) : raw.trim();
        if (hex.length() != 6) return null;
        try {
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    protected void onResume() {
        try {
            onResumeBody();
        } catch (Throwable t) {
            showCrashScreen(t);
        }
    }

    private void onResumeBody() {
        super.onResume();
        CustomThemeBackground.apply(this);
        styleSetupControls();
        List<ClassSession> existing = ScheduleStore.load(this);
        statusText.setTextColor(colorInkDim);
        // Baseline state, not a one-time message -- restored every time this screen resumes,
        // so it always reflects "nothing new happened since you got here," and only
        // showStatus() (called right after an actual parse/import) transiently overrides it
        // with a result message for that action.
        statusText.setText(existing.isEmpty()
                ? "No new schedule loaded \u2014 no schedule is currently active."
                : "No new schedule loaded \u2014 your current schedule is active (" + existing.size() + " classes).");
        renderEditList();
        // University/campus may have been switched inside the profile menu -- re-gate the
        // import card (parser visibility, titles, instructions) to match.
        refreshUniversityUi(false);
        applyImportGating();
        // Profile field VALUES (not just university/campus) may also have changed in the
        // profile menu -- re-run the empty-field grey-out pass so a field that got cleared
        // (or filled in) elsewhere is reflected here without needing to leave and reopen
        // this screen a second time.
        tuneProfileCardSection();
    }

    /**
     * Explicit Primary/Secondary pass over Setup's own action buttons -- these were already
     * being retinted correctly BY COLOR via retintTree's generic sampling, but PARSE & SAVE/DONE
     * (Primary) sampling to the same accent as each other is fine, while CLEAR/CHOOSE/SAVED
     * SCHEDULES/+ADD CLASS (Secondary) sampling to plain SURFACE -- the same tone their own
     * parent card already uses -- made them visually disappear into their own backdrop. See
     * styleControl's SECONDARY case. A no-op outside Custom mode.
     */
    private void styleSetupControls() {
        float pairDensity = getResources().getDisplayMetrics().density;
        int pairMin = Math.round(40 * pairDensity);
        CustomThemeBackground.styleControl(this, findViewById(R.id.btn_parse), CustomThemeBackground.ControlTier.PRIMARY);
        CustomThemeBackground.styleControl(this, doneBtn, CustomThemeBackground.ControlTier.PRIMARY);
        // CLEAR is destructive red; CLEAR + SAVED SCHEDULES form the smaller secondary
        // pair, so they sit a step below the primary rectangles on purpose.
        View btnClear = findViewById(R.id.btn_clear);
        if (btnClear != null) {
            CustomThemeBackground.styleControl(this, btnClear, CustomThemeBackground.ControlTier.DESTRUCTIVE);
            btnClear.setMinimumHeight(pairMin);
            if (btnClear instanceof TextView) ((TextView) btnClear).setTextSize(10.5f);
        }
        CustomThemeBackground.styleControl(this, findViewById(R.id.btn_pick_file), CustomThemeBackground.ControlTier.SECONDARY);
        View btnSaved = findViewById(R.id.btn_view_history);
        if (btnSaved != null) {
            CustomThemeBackground.styleControl(this, btnSaved, CustomThemeBackground.ControlTier.SECONDARY);
            btnSaved.setMinimumHeight(pairMin);
            if (btnSaved instanceof TextView) ((TextView) btnSaved).setTextSize(10.5f);
        }
        CustomThemeBackground.styleControl(this, findViewById(R.id.btn_add_class), CustomThemeBackground.ControlTier.SECONDARY);
        // Semester date pickers + clear -- previously left at whatever retintTree's generic
        // sampling guessed (or the Activity theme's stock button fill), reading as stray
        // system-colored controls inside an otherwise frosted card.
        CustomThemeBackground.styleControl(this, btnSemesterStart, CustomThemeBackground.ControlTier.SECONDARY);
        CustomThemeBackground.styleControl(this, btnSemesterEnd, CustomThemeBackground.ControlTier.SECONDARY);
        CustomThemeBackground.styleControl(this, findViewById(R.id.btn_semester_clear), CustomThemeBackground.ControlTier.DESTRUCTIVE);
        // First-run wizard nav (null outside wizard mode; styleControl no-ops on null).
        CustomThemeBackground.styleControl(this, btnWizardBack, CustomThemeBackground.ControlTier.SECONDARY);
        CustomThemeBackground.styleControl(this, btnWizardNext, CustomThemeBackground.ControlTier.PRIMARY);
        // Form 5 card actions -- same treatment as every other Primary/Secondary pair.
        CustomThemeBackground.styleControl(this, btnForm5Primary, CustomThemeBackground.ControlTier.PRIMARY);
        CustomThemeBackground.styleControl(this, btnForm5Replace, CustomThemeBackground.ControlTier.SECONDARY);
    }

    /** Shown once, before the rest of the screen can be used. Declining cancels setup. */
    private void showTermsGate() {
        View termsView = LayoutInflater.from(this).inflate(layoutDialogTerms, null);
        AlertDialog termsDialog = new AlertDialog.Builder(this)
                .setView(termsView)
                .setCancelable(false)
                .setPositiveButton(R.string.terms_agree, (d, w) ->
                        SettingsStore.setAcceptedTerms(this, true))
                .setNegativeButton(R.string.terms_decline, (d, w) -> {
                    Toast.makeText(this, "You need to accept the terms to use this widget.", Toast.LENGTH_LONG).show();
                    finish();
                })
                .show();
        CustomThemeBackground.applyToDialog(termsDialog);
        CustomThemeBackground.styleDialogButtons(this, termsDialog);
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
        beginRootTransition();
        setSectionExpanded(body, chevron, body.getVisibility() != View.VISIBLE);
    }

    /**
     * Shared by every collapsible section (toggleSection here, and EDIT SCHEDULE INFO's
     * dropHeader) so a smooth height/fade transition is one implementation, not a copy per
     * call site that could quietly drift apart or get skipped on a new one.
     */
    private void beginRootTransition() {
        if (configureRoot == null) return;
        TransitionSet transition = new TransitionSet()
                .addTransition(new Fade(Fade.IN | Fade.OUT))
                .addTransition(new ChangeBounds())
                .setOrdering(TransitionSet.ORDERING_TOGETHER)
                .setDuration(220)
                .setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
        TransitionManager.beginDelayedTransition(configureRoot, transition);
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
        switchCampusAutoDetect.setEnabled(enabled);
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
        inputProfileAddress = findViewById(R.id.input_profile_address);
        inputProfileFacebook = findViewById(R.id.input_profile_facebook);
        inputProfileInstagram = findViewById(R.id.input_profile_instagram);
        inputProfileTwitter = findViewById(R.id.input_profile_twitter);
        inputProfileLinkedin = findViewById(R.id.input_profile_linkedin);
        inputProfileWebsite = findViewById(R.id.input_profile_website);
        switchExportName = findViewById(R.id.switch_export_name);
        switchExportStudentNo = findViewById(R.id.switch_export_student_no);
        switchExportCourse = findViewById(R.id.switch_export_course);
        switchExportYearStanding = findViewById(R.id.switch_export_year_standing);
        switchExportAddress = findViewById(R.id.switch_export_address);
        switchExportFacebook = findViewById(R.id.switch_export_facebook);
        switchExportInstagram = findViewById(R.id.switch_export_instagram);
        switchExportTwitter = findViewById(R.id.switch_export_twitter);
        switchExportLinkedin = findViewById(R.id.switch_export_linkedin);
        switchExportWebsite = findViewById(R.id.switch_export_website);

        inputProfileName.setText(SettingsStore.getProfileName(this));
        inputProfileStudentNo.setText(SettingsStore.getProfileStudentNo(this));
        inputProfileCourse.setText(SettingsStore.getProfileCourse(this));
        inputProfileYearStanding.setText(SettingsStore.getProfileYearStanding(this));
        inputProfileAddress.setText(SettingsStore.getProfileAddress(this));
        inputProfileFacebook.setText(SettingsStore.getProfileFacebook(this));
        inputProfileInstagram.setText(SettingsStore.getProfileInstagram(this));
        inputProfileTwitter.setText(SettingsStore.getProfileTwitter(this));
        inputProfileLinkedin.setText(SettingsStore.getProfileLinkedin(this));
        inputProfileWebsite.setText(SettingsStore.getProfileWebsite(this));
        switchExportName.setChecked(SettingsStore.isExportShowNameEnabled(this));
        switchExportStudentNo.setChecked(SettingsStore.isExportShowStudentNoEnabled(this));
        switchExportCourse.setChecked(SettingsStore.isExportShowCourseEnabled(this));
        switchExportYearStanding.setChecked(SettingsStore.isExportShowYearStandingEnabled(this));
        switchExportAddress.setChecked(SettingsStore.isExportShowAddressEnabled(this));
        switchExportFacebook.setChecked(SettingsStore.isExportShowFacebookEnabled(this));
        switchExportInstagram.setChecked(SettingsStore.isExportShowInstagramEnabled(this));
        switchExportTwitter.setChecked(SettingsStore.isExportShowTwitterEnabled(this));
        switchExportLinkedin.setChecked(SettingsStore.isExportShowLinkedinEnabled(this));
        switchExportWebsite.setChecked(SettingsStore.isExportShowWebsiteEnabled(this));

        switchExportName.setOnCheckedChangeListener((btn, checked) -> SettingsStore.setExportShowNameEnabled(this, checked));
        switchExportStudentNo.setOnCheckedChangeListener((btn, checked) -> SettingsStore.setExportShowStudentNoEnabled(this, checked));
        switchExportCourse.setOnCheckedChangeListener((btn, checked) -> SettingsStore.setExportShowCourseEnabled(this, checked));
        switchExportYearStanding.setOnCheckedChangeListener((btn, checked) -> SettingsStore.setExportShowYearStandingEnabled(this, checked));
        switchExportAddress.setOnCheckedChangeListener((btn, checked) -> SettingsStore.setExportShowAddressEnabled(this, checked));
        switchExportFacebook.setOnCheckedChangeListener((btn, checked) -> SettingsStore.setExportShowFacebookEnabled(this, checked));
        switchExportInstagram.setOnCheckedChangeListener((btn, checked) -> SettingsStore.setExportShowInstagramEnabled(this, checked));
        switchExportTwitter.setOnCheckedChangeListener((btn, checked) -> SettingsStore.setExportShowTwitterEnabled(this, checked));
        switchExportLinkedin.setOnCheckedChangeListener((btn, checked) -> SettingsStore.setExportShowLinkedinEnabled(this, checked));
        switchExportWebsite.setOnCheckedChangeListener((btn, checked) -> SettingsStore.setExportShowWebsiteEnabled(this, checked));

        EditText[] profileInputs = {inputProfileName, inputProfileStudentNo, inputProfileCourse, inputProfileYearStanding,
                inputProfileAddress, inputProfileFacebook, inputProfileInstagram, inputProfileTwitter, inputProfileLinkedin, inputProfileWebsite};
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
        EditText[] rest = {inputProfileStudentNo, inputProfileCourse, inputProfileYearStanding,
                inputProfileAddress, inputProfileFacebook, inputProfileInstagram, inputProfileTwitter, inputProfileLinkedin, inputProfileWebsite};
        int filled = 0;
        for (EditText input : rest) {
            if (!input.getText().toString().trim().isEmpty()) filled++;
        }
        summaryProfile.setText(filled == 0 ? "Not set" : filled + " of " + rest.length + " fields set");
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
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Remove Form 5?")
                .setMessage("This only removes it from Marooned IskedKit -- the original file is untouched.")
                .setPositiveButton("Remove", (d, w) -> clearForm5(true))
                .setNegativeButton("Cancel", null)
                .show();
        CustomThemeBackground.applyToDialog(dialog);
        CustomThemeBackground.styleDialogButtons(this, dialog);
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
            reminderChips[i].setTextColor(active ? selectedLabelOn() : colorInkDim);
            CustomThemeBackground.tintChip(this, reminderChips[i], active);
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

    // ---- v3.0.0 import gating & reference files ---------------------------------------------

    /**
     * The CRS HTML parser understands Diliman's registration markup only -- everywhere else
     * the parser UI (paste box + PARSE & SAVE) is hidden, the picker narrows to .ics plus
     * portal screenshots/PDFs for reference, and the instructions swap to the mobile-browser
     * guide. Manual entry and .ics import work identically in both modes.
     */
    private void applyImportGating() {
        if (cardImport == null) cardImport = findViewById(R.id.card_import);
        boolean crs = SettingsStore.isCrsParserAvailable(this);
        TextView instructions = findViewById(R.id.import_instructions);
        TextView cardTitle = findViewById(R.id.import_card_title);
        // Non-Diliman affiliations get exactly one way in: manual entry. The parser zone,
        // file picker, and even the "N classes loaded" status box stay Diliman-only --
        // manual users see their class count reflected in the editable list instead.
        for (View v : crsViews) v.setVisibility(crs ? View.VISIBLE : View.GONE);
        htmlInput.setVisibility(crs ? View.VISIBLE : View.GONE);
        // addClassBtn now lives inside EDIT SCHEDULE INFO alongside the class list (see
        // mergeEditIntoImportCard()) and stays visible for every affiliation -- manual add
        // is a supplement to parsed classes, not a CRS-unavailable fallback, so this no
        // longer touches its visibility.
        if (cardTitle != null) {
            cardTitle.setText(getString(crs
                    ? R.string.import_title_crs : R.string.import_title_generic));
        }
        if (instructions != null) {
            instructions.setText(crs
                    ? getString(R.string.import_instructions_crs)
                    : getString(R.string.import_instructions_generic));
        }
    }

    /** Mimes depend on affiliation: CRS HTML only on UPD; .ics + portal screenshots/PDFs everywhere. */
    private void pickScheduleFile() {
        java.util.List<String> mimes = new java.util.ArrayList<>();
        if (SettingsStore.isCrsParserAvailable(this)) mimes.add("text/html");
        mimes.add("text/calendar");
        mimes.add("image/png");
        mimes.add("image/jpeg");
        mimes.add("application/pdf");
        filePicker.launch(mimes.toArray(new String[0]));
    }

    /** Copies a picked portal screenshot/PDF into app storage as a schedule reference attachment. */
    private void saveScheduleReference(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some providers don't grant persistable access; the link works for this session.
        }
        String name = queryDisplayName(uri);
        dev.marquinhhou.crsscheduler.data.AttachmentStore.add(this, "schedule_reference", "portal",
                new dev.marquinhhou.crsscheduler.model.Attachment(uri.toString(), name));
        showStatus("Saved \u201C" + name + "\u201D for reference. Add your classes by hand below.", true);
    }

    private void readFileIntoInput(Uri uri) {
        // Screenshots/PDFs of the portal page aren't text -- keep them as reference material
        // instead of trying to read them as HTML/ICS.
        String type = getContentResolver().getType(uri);
        boolean binary = type != null && (type.startsWith("image/") || type.equals("application/pdf"));
        if (!binary) {
            try (InputStream probe = getContentResolver().openInputStream(uri)) {
                // Fall back to sniffing when the provider reports */*: a leading PNG/JPEG/PDF
                // magic number means reference material, not pasteable text.
                if (probe != null) {
                    byte[] head = new byte[4];
                    int read = probe.read(head);
                    if (read >= 4
                            && ((head[0] == (byte) 0x89 && head[1] == 'P')
                                || (head[0] == (byte) 0xFF && head[1] == (byte) 0xD8)
                                || (head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F'))) {
                        binary = true;
                    }
                }
            } catch (IOException ignored) {
            }
        }
        if (binary) {
            saveScheduleReference(uri);
            return;
        }
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
            } else if (!SettingsStore.isCrsParserAvailable(this)) {
                showStatus("CRS page parsing is only available on UP Diliman. "
                        + "Import a .ics calendar file instead, or add your classes by hand below.", false);
            } else {
                // Explicitly choosing a file is a completed action -- unlike pasting, where
                // the person is still actively typing/editing and a manual PARSE & SAVE tap
                // is the natural "I'm done" signal, picking a file has no further step to
                // wait for. Auto-parsing here matches how .ics files already behave just
                // above (handleIcsImport runs immediately, no manual tap either) -- HTML was
                // the one path that picked a file and then still made the person press
                // another button for something they'd already told the app to do.
                htmlInput.setText(content);
                handleParse(content);
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
            if (SettingsStore.isCampusAutoDetectEnabled(this)
                    && inputCampus.getText().toString().trim().isEmpty()) {
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
        CustomThemeBackground.apply(this);
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

        // Days/start/end -- same chip-row + time-picker pattern as Add Class, pre-filled from
        // the class being edited rather than starting blank.
        TextView[] dayChips = {
                dialogView.findViewById(R.id.chip_edit_day_0),
                dialogView.findViewById(R.id.chip_edit_day_1),
                dialogView.findViewById(R.id.chip_edit_day_2),
                dialogView.findViewById(R.id.chip_edit_day_3),
                dialogView.findViewById(R.id.chip_edit_day_4),
                dialogView.findViewById(R.id.chip_edit_day_5),
                dialogView.findViewById(R.id.chip_edit_day_6),
        };
        boolean[] selectedDays = new boolean[7];
        for (int d : c.days) if (d >= 0 && d < 7) selectedDays[d] = true;
        for (int i = 0; i < dayChips.length; i++) {
            int idx = i;
            bindThemeChip(dayChips[i], selectedDays[i]);
            dayChips[i].setOnClickListener(v -> {
                selectedDays[idx] = !selectedDays[idx];
                bindThemeChip(dayChips[idx], selectedDays[idx]);
            });
        }

        Button startTimeBtn = dialogView.findViewById(R.id.btn_edit_start_time);
        Button endTimeBtn = dialogView.findViewById(R.id.btn_edit_end_time);
        int[] startMinute = {c.start};
        int[] endMinute = {c.end};
        startTimeBtn.setText(WidgetRenderer.minToLabel(c.start));
        endTimeBtn.setText(WidgetRenderer.minToLabel(c.end));
        startTimeBtn.setOnClickListener(v -> pickTime(startMinute[0], picked -> {
            startMinute[0] = picked;
            startTimeBtn.setText(WidgetRenderer.minToLabel(picked));
        }));
        endTimeBtn.setOnClickListener(v -> pickTime(endMinute[0], picked -> {
            endMinute[0] = picked;
            endTimeBtn.setText(WidgetRenderer.minToLabel(picked));
        }));

        LinearLayout syllabusContainer = dialogView.findViewById(R.id.syllabus_attachments_container);
        View attachSyllabusBtn = dialogView.findViewById(R.id.btn_attach_syllabus);
        activeSyllabusContainer = syllabusContainer;
        renderSyllabusAttachments(syllabusContainer, c.code);
        CustomThemeBackground.styleControl(this, attachSyllabusBtn, CustomThemeBackground.ControlTier.PRIMARY);
        attachSyllabusBtn.setOnClickListener(v -> {
            pendingSyllabusClassCode = c.code;
            syllabusPicker.launch(new String[]{"application/pdf", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain", "image/*"});
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("SAVE", null) // wired manually below so a validation miss can keep the dialog open
                .setNeutralButton("DELETE", (d, w) -> confirmDelete(c))
                .setNegativeButton("CANCEL", null)
                .create();

        dialog.setOnShowListener(d -> {
            CustomThemeBackground.applyToDialog(dialog);
            CustomThemeBackground.styleDialogButtons(this, dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                List<Integer> newDays = new ArrayList<>();
                for (int i = 0; i < selectedDays.length; i++) if (selectedDays[i]) newDays.add(i);
                if (newDays.isEmpty()) {
                    Toast.makeText(this, "Pick at least one day.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (endMinute[0] <= startMinute[0]) {
                    Toast.makeText(this, "End time must be after the start time.", Toast.LENGTH_SHORT).show();
                    return;
                }
                String newRoom = roomInput.getText().toString().trim();
                String newInstructor = instructorInput.getText().toString().trim();
                double newUnits;
                try {
                    newUnits = Double.parseDouble(unitsInput.getText().toString().trim());
                } catch (NumberFormatException e) {
                    newUnits = c.credits; // keep the original value if the field was left blank/invalid
                }
                applyEdit(c, newRoom, newInstructor, newUnits, newDays, startMinute[0], endMinute[0]);
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    /**
     * Rebuilds the syllabus attachment rows inside an open edit-class dialog. Called both before
     * the dialog is first shown AND later, from the syllabus-picker callback, while the dialog is
     * already on screen -- the latter rebuilds container's rows after applyToDialog's one-time
     * pass already ran, so it needs its own retint here rather than relying on that earlier pass.
     */
    private void renderSyllabusAttachments(LinearLayout container, String classCode) {
        container.removeAllViews();
        List<dev.marquinhhou.crsscheduler.model.Attachment> attachments =
                dev.marquinhhou.crsscheduler.data.AttachmentStore.get(this,
                        dev.marquinhhou.crsscheduler.data.AttachmentStore.NAMESPACE_SYLLABUS, classCode);
        for (dev.marquinhhou.crsscheduler.model.Attachment a : attachments) {
            container.addView(buildAttachmentRow(a, () -> openAttachment(a), () -> {
                dev.marquinhhou.crsscheduler.data.AttachmentStore.remove(this,
                        dev.marquinhhou.crsscheduler.data.AttachmentStore.NAMESPACE_SYLLABUS, classCode, a.uri);
                renderSyllabusAttachments(container, classCode);
            }));
        }
        CustomThemeBackground.applySubtree(this, container);
    }

    /** One "filename ... ×" row shared by the syllabus and (via NoteEditActivity) notes attachment lists. */
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
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
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
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open that file with.", Toast.LENGTH_SHORT).show();
        }
    }

    private void onSyllabusPicked(String classCode, Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            // Some providers don't support a persistable grant -- still usable this session.
        }
        String name = queryDisplayName(uri);
        dev.marquinhhou.crsscheduler.data.AttachmentStore.add(this,
                dev.marquinhhou.crsscheduler.data.AttachmentStore.NAMESPACE_SYLLABUS, classCode,
                new dev.marquinhhou.crsscheduler.model.Attachment(uri.toString(), name));
        if (activeSyllabusContainer != null) renderSyllabusAttachments(activeSyllabusContainer, classCode);
        Toast.makeText(this, "Syllabus attached.", Toast.LENGTH_SHORT).show();
    }

    /** Deletion has no undo button here, unlike an edit, so it needs a confirmation first. */
    private void confirmDelete(ClassSession c) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Remove this class?")
                .setMessage(c.name + " will be removed from your schedule. You can still recover it "
                        + "from Saved Schedules afterward.")
                .setPositiveButton("REMOVE", (d, w) -> applyDelete(c))
                .setNegativeButton("CANCEL", null)
                .show();
        CustomThemeBackground.applyToDialog(dialog);
        CustomThemeBackground.styleDialogButtons(this, dialog);
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

    private void applyEdit(ClassSession original, String newRoom, String newInstructor, double newUnits,
                            List<Integer> newDays, int newStart, int newEnd) {
        List<ClassSession> schedule = ScheduleStore.load(this);
        List<ClassSession> updated = new ArrayList<>();
        for (ClassSession c : schedule) {
            if (c.code.equals(original.code)) {
                updated.add(c.withEditedInfo(newRoom.isEmpty() ? "TBA" : newRoom, newInstructor, newUnits,
                        newDays, newStart, newEnd));
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

        dialog.setOnShowListener(d -> {
            CustomThemeBackground.applyToDialog(dialog);
            CustomThemeBackground.styleDialogButtons(this, dialog);
            // Add spacing between CANCEL (negative) and ADD (positive) buttons
            Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) pos.getLayoutParams();
                if (lp != null) {
                    lp.setMarginStart(Math.round(12 * getResources().getDisplayMetrics().density));
                    pos.setLayoutParams(lp);
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
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
            });
        });

        dialog.show();
    }

    private void applyAdd(ClassSession newClass) {
        List<ClassSession> schedule = ScheduleStore.load(this);
        schedule.add(newClass);
        ScheduleStore.save(this, schedule);
        setEditInfoExpanded(true); // reveal the list so the new class is visible right away
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
