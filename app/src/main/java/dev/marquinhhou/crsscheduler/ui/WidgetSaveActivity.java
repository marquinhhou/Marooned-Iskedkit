package dev.marquinhhou.crsscheduler.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.ScheduleStore;
import dev.marquinhhou.crsscheduler.model.ClassSession;

/** Widget's Save button -- offers Image or .ics; same export logic as WeekScheduleActivity. */
public class WidgetSaveActivity extends AppCompatActivity {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) saveScheduleToGallery(); else finish();
            });

    private final ActivityResultLauncher<String> icsExportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/calendar"), uri -> {
                if (uri != null) writeIcsToUri(uri); else finish();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theming.applyDialogTheme(this);
        setContentView(Theming.pick(this,
                R.layout.activity_widget_save_ge, R.layout.activity_widget_save_ne, R.layout.activity_widget_save_adaptive));

        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save_image).setOnClickListener(v -> onSaveImageTapped());
        findViewById(R.id.btn_save_ics).setOnClickListener(v -> onSaveIcsTapped());
    }

    private void onSaveImageTapped() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
            saveScheduleToGallery();
        } else {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    private void saveScheduleToGallery() {
        List<ClassSession> schedule = ScheduleStore.load(this);
        if (schedule.isEmpty()) {
            Toast.makeText(this, "No schedule loaded yet.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        ioExecutor.execute(() -> {
            Bitmap bitmap = ScheduleImageExporter.render(this, schedule);
            String message;
            if (bitmap == null) {
                message = "Nothing to save yet.";
            } else {
                try {
                    ScheduleImageExporter.saveToGallery(this, bitmap);
                    message = "Saved to Gallery.";
                } catch (IOException e) {
                    message = "Couldn't save image: " + e.getMessage();
                } finally {
                    bitmap.recycle();
                }
            }
            String finalMessage = message;
            runOnUiThread(() -> {
                Toast.makeText(this, finalMessage, Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void onSaveIcsTapped() {
        List<ClassSession> schedule = ScheduleStore.load(this);
        if (schedule.isEmpty()) {
            Toast.makeText(this, "No schedule loaded yet.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        icsExportLauncher.launch(IcsExporter.suggestedFileName());
    }

    private void writeIcsToUri(Uri uri) {
        List<ClassSession> schedule = ScheduleStore.load(this);
        ioExecutor.execute(() -> {
            String message;
            try {
                String ics = IcsExporter.build(this, schedule);
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("Could not open an output stream for the calendar file.");
                    out.write(ics.getBytes(StandardCharsets.UTF_8));
                }
                message = "Saved calendar file. Import it from your calendar app.";
            } catch (IOException e) {
                message = "Couldn't save calendar file: " + e.getMessage();
            }
            String finalMessage = message;
            runOnUiThread(() -> {
                Toast.makeText(this, finalMessage, Toast.LENGTH_LONG).show();
                finish();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
    }
}
