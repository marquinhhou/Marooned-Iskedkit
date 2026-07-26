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

    private String room;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theming.applyDialogTheme(this);
        setContentView(Theming.pick(this,
                R.layout.activity_widget_action_ge, R.layout.activity_widget_action_ne, R.layout.activity_widget_action_adaptive));

        String className = getIntent().getStringExtra(EXTRA_CLASS_NAME);
        room = getIntent().getStringExtra(EXTRA_ROOM);

        ((TextView) findViewById(R.id.dialog_class_name)).setText(className != null ? className : "Class");
        ((TextView) findViewById(R.id.dialog_room)).setText(room != null ? room : "");

        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.btn_open_maps).setOnClickListener(v -> openMaps());
    }

    private void openMaps() {
        if (room == null || room.trim().isEmpty()) {
            finish();
            return;
        }
        String campus = SettingsStore.getCampusHint(this);
        String query = campus != null && !campus.trim().isEmpty() ? room + " " + campus : room;
        try {
            Intent geo = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)));
            startActivity(geo);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No maps app found to handle this.", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
