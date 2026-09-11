package dev.marquinhhou.crsscheduler.ui;

import android.content.Context;
import android.os.Build;

import androidx.core.content.ContextCompat;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore.ThemeFamily;

/** Resolves ThemeFamily to resource ids/colors -- RemoteViews can't apply a runtime theme. */
public final class Theming {

    private Theming() {}

    public static ThemeFamily family(Context context) {
        return SettingsStore.getThemeFamily(context);
    }

    public static boolean supportsAdaptive() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static int pick(Context context, int geRes, int neRes, int adaptiveRes) {
        switch (family(context)) {
            case NE: return neRes;
            case ADAPTIVE:
            case CUSTOM: return adaptiveRes;
            case GE:
            default: return geRes;
        }
    }

    public static int color(Context context, int geColorRes, int neColorRes, int adaptiveColorRes) {
        if (family(context) == ThemeFamily.CUSTOM) {
            Integer derived = CustomThemeBackground.deriveColor(context, adaptiveColorRes);
            if (derived != null) return derived;
        }
        return ContextCompat.getColor(context, pick(context, geColorRes, neColorRes, adaptiveColorRes));
    }

    public static int activityThemeRes(Context context) {
        switch (family(context)) {
            case NE: return R.style.Theme_CRSScheduler_NE;
            case ADAPTIVE:
            case CUSTOM: return R.style.Theme_CRSScheduler_Adaptive;
            case GE:
            default: return R.style.Theme_CRSScheduler_GE;
        }
    }

    public static int dialogThemeRes(Context context) {
        switch (family(context)) {
            case NE: return R.style.Theme_CRSScheduler_NE_Dialog;
            case ADAPTIVE:
            case CUSTOM: return R.style.Theme_CRSScheduler_Adaptive_Dialog;
            case GE:
            default: return R.style.Theme_CRSScheduler_GE_Dialog;
        }
    }

    /** Call in onCreate() before super.onCreate()/setContentView(). */
    public static void applyActivityTheme(android.app.Activity activity) {
        activity.setTheme(activityThemeRes(activity));
    }

    public static void applyDialogTheme(android.app.Activity activity) {
        activity.setTheme(dialogThemeRes(activity));
    }

    /** GE/Adaptive use a smooth stroked arc ring; NE keeps the original 28-dot glyph ring. */
    public static boolean usesDotRing(Context context) {
        return family(context) == ThemeFamily.NE;
    }

    /**
     * Whether surfaces should render their DARK variant. NE is fixed-dark regardless of
     * system; every other family follows the system's night mode -- which is also what
     * RemoteViews' resource resolution uses for the widget itself, so both sides agree.
     */
    public static boolean isDarkUi(Context context) {
        if (family(context) == ThemeFamily.NE) return true;
        int mask = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** GE/Adaptive use the system default font; NE keeps JetBrains Mono (see res/font). */
    public static boolean usesMonoFont(Context context) {
        return family(context) == ThemeFamily.NE;
    }
}
