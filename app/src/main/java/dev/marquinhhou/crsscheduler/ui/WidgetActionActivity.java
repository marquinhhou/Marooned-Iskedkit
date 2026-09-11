package dev.marquinhhou.crsscheduler.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.SettingsStore;

/** Dialog-themed Activity for the "Open Maps?" prompt -- widgets can't show a real AlertDialog. */
public class WidgetActionActivity extends AppCompatActivity {

    public static final String EXTRA_ROOM = "extra_room";
    public static final String EXTRA_CLASS_NAME = "extra_class_name";
    public static final String EXTRA_ATTACHMENT_URI = "extra_attachment_uri";

    private String room;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theming.applyDialogTheme(this);

        String attachmentUri = getIntent().getStringExtra(EXTRA_ATTACHMENT_URI);
        if (attachmentUri != null) {
            openAttachment(attachmentUri);
            return;
        }

        if (!SettingsStore.isMapsEnabled(this)) {
            finish();
            return;
        }
        setContentView(Theming.pick(this,
                R.layout.activity_widget_action_ge, R.layout.activity_widget_action_ne, R.layout.activity_widget_action_adaptive));

        String className = getIntent().getStringExtra(EXTRA_CLASS_NAME);
        room = getIntent().getStringExtra(EXTRA_ROOM);

        ((TextView) findViewById(R.id.dialog_class_name)).setText(className != null ? className : "Class");
        ((TextView) findViewById(R.id.dialog_room)).setText(room != null ? room : "");

        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.btn_open_maps).setOnClickListener(v -> openMaps());
    }

    @Override
    protected void onResume() {
        super.onResume();
        CustomThemeBackground.applyToCard(this, findViewById(R.id.dialog_card_root));
        CustomThemeBackground.styleControl(this, findViewById(R.id.btn_open_maps), CustomThemeBackground.ControlTier.PRIMARY);
        CustomThemeBackground.styleControl(this, findViewById(R.id.btn_cancel), CustomThemeBackground.ControlTier.SECONDARY);
        // Explicit text roles -- retintTree's value-matching is unreliable on these cards.
        int accent = Theming.color(this, R.color.ge_accent, R.color.ne_accent, R.color.adaptive_accent);
        int ink = Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink);
        int inkDim = Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim);
        ((android.widget.TextView) findViewById(R.id.dialog_action_eyebrow)).setTextColor(accent);
        ((android.widget.TextView) findViewById(R.id.dialog_class_name)).setTextColor(ink);
        ((android.widget.TextView) findViewById(R.id.dialog_room)).setTextColor(inkDim);
        ((android.widget.TextView) findViewById(R.id.dialog_maps_question)).setTextColor(inkDim);
        ((android.widget.TextView) findViewById(R.id.dialog_maps_disclaimer)).setTextColor(accent);
    }

    private void openAttachment(String uriString) {
        try {
            Uri fileUri = Uri.parse(uriString);
            String type = getContentResolver().getType(fileUri);
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(fileUri, type != null ? type : "*/*");
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(view);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open that file with.", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, "Can't open that file anymore -- try attaching it again.", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private void openMaps() {
        if (room == null || room.trim().isEmpty()) {
            finish();
            return;
        }
        // v3.0.0 "Auto-fill from imported schedule": when it's off, the search opens EMPTY
        // -- no room, no campus hint -- so the user types freely without context.
        String query = "";
        if (SettingsStore.isCampusAutoDetectEnabled(this)) {
            String campus = SettingsStore.getCampusHint(this);
            query = campus != null && !campus.trim().isEmpty() ? room + " " + campus : room;
        }
        try {
            Uri geoUri = query.isEmpty() ? Uri.parse("geo:0,0")
                    : Uri.parse("geo:0,0?q=" + Uri.encode(query));
            Intent geo = new Intent(Intent.ACTION_VIEW, geoUri);
            startActivity(geo);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No maps app found to handle this.", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
