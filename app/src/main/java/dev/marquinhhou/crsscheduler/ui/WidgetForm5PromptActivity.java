package dev.marquinhhou.crsscheduler.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.widget.WidgetRefreshScheduler;

/** Widget's Form 5 button when nothing's uploaded yet -- prompts for a PDF right there. */
public class WidgetForm5PromptActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String[]> form5Picker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) onForm5Picked(uri); else finish();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theming.applyDialogTheme(this);
        setContentView(Theming.pick(this,
                R.layout.activity_widget_form5_ge, R.layout.activity_widget_form5_ne, R.layout.activity_widget_form5_adaptive));

        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.btn_upload_form5).setOnClickListener(v -> form5Picker.launch(new String[]{"application/pdf"}));
    }

    @Override
    protected void onResume() {
        super.onResume();
        CustomThemeBackground.applyToCard(this, findViewById(R.id.dialog_card_root));
        CustomThemeBackground.styleControl(this, findViewById(R.id.btn_upload_form5), CustomThemeBackground.ControlTier.PRIMARY);
        CustomThemeBackground.styleControl(this, findViewById(R.id.btn_cancel), CustomThemeBackground.ControlTier.QUIET);
        // The card's text roles are colored explicitly: retintTree's value-matching has
        // proven unreliable on these prompt cards, which left the accent eyebrow (and on
        // light systems, every text) stuck on raw Adaptive colors.
        ((android.widget.TextView) findViewById(R.id.dialog_form5_eyebrow)).setTextColor(
                Theming.color(this, R.color.ge_accent, R.color.ne_accent, R.color.adaptive_accent));
        ((android.widget.TextView) findViewById(R.id.dialog_form5_title)).setTextColor(
                Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink));
        ((android.widget.TextView) findViewById(R.id.dialog_form5_body)).setTextColor(
                Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim));
    }

    private void onForm5Picked(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            // Some providers don't support a persistable grant -- it'll still work for this
            // session, but may need re-picking after the app restarts.
        }
        SettingsStore.setForm5Uri(this, uri);
        WidgetRefreshScheduler.updateAllWidgets(this);
        Toast.makeText(this, "Form 5 saved.", Toast.LENGTH_SHORT).show();
        finish();
    }
}
