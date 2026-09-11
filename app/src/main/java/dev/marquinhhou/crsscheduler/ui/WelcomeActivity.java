package dev.marquinhhou.crsscheduler.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.SettingsStore;

/**
 * v3.0.0 rebrand transition -- shows exactly once per install, on the first entry into the
 * app after updating (routed through ConfigureActivity, the app's single real entry point).
 * Existing users' data is untouched; this is purely the "Welcome to Marooned IskedKit"
 * beat between the old app and the new one. New installs land here too, then fall through
 * to Configure which starts its own setup wizard.
 */
public class WelcomeActivity extends AppCompatActivity {

    private static final long SHOW_DURATION_MS = 1800;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean dismissed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theming.applyActivityTheme(this);
        // Mark immediately rather than on dismiss: a process death mid-animation must not
        // turn the next widget-gear tap into a second welcome beat.
        SettingsStore.setWelcomeV3Shown(this, true);

        float density = getResources().getDisplayMetrics().density;
        int ink = Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink);
        int inkDim = Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim);
        int accent = Theming.color(this, R.color.ge_accent, R.color.ne_accent, R.color.adaptive_accent);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(Math.round(32 * density), 0, Math.round(32 * density), 0);
        setContentView(root);

        TextView dot = new TextView(this);
        dot.setText("\u25CF");
        dot.setTextSize(14f);
        dot.setTextColor(accent);
        dot.setGravity(Gravity.CENTER);
        root.addView(dot);

        TextView title = new TextView(this);
        title.setText(getString(R.string.rebrand_welcome_title));
        title.setTextSize(24f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(ink);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Math.round(14 * density);
        title.setLayoutParams(titleLp);
        root.addView(title);

        TextView tagline = new TextView(this);
        tagline.setText(getString(R.string.app_tagline));
        tagline.setTextSize(13f);
        tagline.setTextColor(accent);
        tagline.setLetterSpacing(0.08f);
        tagline.setGravity(Gravity.CENTER);
        root.addView(tagline);

        TextView body = new TextView(this);
        body.setText(getString(R.string.rebrand_welcome_body));
        body.setTextSize(11.5f);
        body.setTextColor(inkDim);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = Math.round(18 * density);
        body.setLayoutParams(bodyLp);
        root.addView(body);

        TextView status = new TextView(this);
        status.setText("Checking your data\u2026");
        status.setTextSize(11f);
        status.setTextColor(inkDim);
        status.setLetterSpacing(0.15f);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = Math.round(48 * density);
        status.setLayoutParams(statusLp);
        root.addView(status);

        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(420);
        fadeIn.setFillAfter(true);
        root.startAnimation(fadeIn);

        handler.postDelayed(this::dismiss, SHOW_DURATION_MS);
    }

    private void dismiss() {
        if (dismissed) return;
        dismissed = true;
        finish();
    }

    @Override
    public void onBackPressed() {
        dismiss();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
