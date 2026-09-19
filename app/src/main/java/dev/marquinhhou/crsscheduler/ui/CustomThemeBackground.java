package dev.marquinhhou.crsscheduler.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore.CustomThemeMode;
import dev.marquinhhou.crsscheduler.data.SettingsStore.ThemeFamily;

/**
 * Custom theme's own color engine -- deliberately independent of Adaptive, Nothing, and GE.
 * Every color it produces (see {@link ColorRole}/{@link #colorForRole}) is computed purely from
 * the user's chosen primary color, accent color, and/or photo, with no reference to what
 * Adaptive's (or anything else's) actual color values happen to be. The only thing Custom still
 * borrows from Adaptive is layout *structure* (which XML gets inflated, via Theming.pick()) --
 * rebuilding ~70 layout files from scratch for a 4th resource fork isn't a safe undertaking with
 * no way to compile-check the result, so Adaptive's XML is used purely as a positional skeleton.
 * Adaptive's specific color VALUES are what {@link #retintTree} exists to identify and replace.
 *
 * The photo itself is always decoded from a local file the app copied into its own private
 * storage at pick time (see copyPickedPhotoToLocalStorage, called from ConfigureActivity) --
 * earlier rounds kept decoding directly from the external picker's content:// URI, which
 * depended on that URI's permission grant and the source provider staying valid across dialog
 * rebuilds, app restarts, etc. That dependency, not the decoder API used, was the actual
 * repeated failure.
 *
 * Call apply() from onResume() (not onCreate() -- the content view needs to exist and have been
 * measured/attached first) on any Activity that should honor Custom theming. A no-op when the
 * active theme isn't CUSTOM, so it's safe to call unconditionally.
 */
public final class CustomThemeBackground {

    private CustomThemeBackground() {}

    /**
     * Samples a grid of pixels from the photo to derive a representative background tone
     * (the average) and a distinct accent (the most saturated sampled pixel, so buttons/chips
     * don't just look like a slightly-different shade of the background). Deliberately a
     * simple, dependency-free average/max-saturation scan rather than a palette-extraction
     * library -- easy to read and verify correctness of directly, no new Gradle dependency to
     * add sight-unseen.
     */
    public static int[] extractDominantColors(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int stepX = Math.max(1, w / 24);
        int stepY = Math.max(1, h / 24);

        long rSum = 0, gSum = 0, bSum = 0;
        int count = 0;
        int bestAccent = 0;
        float bestSaturation = -1f;
        float[] hsv = new float[3];

        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int pixel = bmp.getPixel(x, y);
                rSum += Color.red(pixel);
                gSum += Color.green(pixel);
                bSum += Color.blue(pixel);
                count++;

                Color.colorToHSV(pixel, hsv);
                // Saturated-but-not-too-dark and not-too-bright pixels make the most useful
                // accent -- a highly "saturated" near-black or near-white pixel isn't a
                // meaningful color pop, it's noise/shadow/highlight.
                if (hsv[1] > bestSaturation && hsv[2] > 0.2f && hsv[2] < 0.95f) {
                    bestSaturation = hsv[1];
                    bestAccent = pixel;
                }
            }
        }

        int primary = count > 0
                ? Color.rgb((int) (rSum / count), (int) (gSum / count), (int) (bSum / count))
                : Color.DKGRAY;
        // Previously fell back to `primary` (the numeric average of every sampled pixel)
        // whenever the single best pixel's saturation was <=0.25 -- but averaging many
        // different hues together is close to the WORST possible way to pick "a distinctive
        // color": it's mathematically biased toward desaturation (hues cancel out), so for any
        // photo that's generally muted/dim/foggy throughout (no single vivid pixel, but not
        // uniformly gray either), the fallback produced an accent that was MORE washed-out
        // than the individual pixel it was supposedly a fallback from. That muddy average
        // then fed into ensureAccentContrast below, which raises saturation but can't fix a
        // poorly-defined hue -- the net result read as an arbitrary, unremarkable tone
        // (often landing in muddy yellow/olive/khaki territory) that didn't look "highlighted"
        // against the very photo it was sampled from. bestAccent -- any single pixel that
        // cleared the value filter above -- keeps a genuine, intentional hue even at modest
        // saturation, and ensureAccentContrast's saturation floor (below) still boosts it to a
        // clearly-a-color result. `primary` is now only used in the genuinely degenerate case
        // where NOT ONE sampled pixel had a usable value (bestSaturation never updated from
        // its initial -1) -- e.g. a pixel grid that's entirely pure black or pure white.
        int accent = bestSaturation >= 0f ? (bestAccent | 0xFF000000) : primary;
        return new int[]{primary | 0xFF000000, accent};
    }

    /**
     * A spread of distinct candidate colors from the photo, for the user to pick from directly
     * (rather than only ever getting the one auto-picked "most saturated pixel" accent).
     * Buckets sampled pixels by hue into `bucketCount` equal ranges and keeps the most vibrant
     * (saturation x brightness) pixel seen in each -- this is what keeps the results genuinely
     * different from each other instead of several near-duplicate shades of whatever the single
     * most common tone is. Bins the photo has none of simply don't appear in the result, so the
     * returned array can be shorter than bucketCount.
     */
    public static int[] extractPaletteCandidates(Bitmap bmp, int bucketCount) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int stepX = Math.max(1, w / 32);
        int stepY = Math.max(1, h / 32);

        int[] bestPerBucket = new int[bucketCount];
        float[] bestScorePerBucket = new float[bucketCount];
        boolean[] found = new boolean[bucketCount];
        float[] hsv = new float[3];
        float bucketWidth = 360f / bucketCount;

        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int pixel = bmp.getPixel(x, y);
                Color.colorToHSV(pixel, hsv);
                if (hsv[2] < 0.15f || hsv[2] > 0.97f) continue; // skip near-black/near-white noise
                int bucket = Math.min(bucketCount - 1, (int) (hsv[0] / bucketWidth));
                float score = hsv[1] * hsv[2];
                if (!found[bucket] || score > bestScorePerBucket[bucket]) {
                    found[bucket] = true;
                    bestScorePerBucket[bucket] = score;
                    bestPerBucket[bucket] = pixel | 0xFF000000;
                }
            }
        }

        int populated = 0;
        for (boolean f : found) if (f) populated++;
        int[] result = new int[populated];
        int i = 0;
        for (int b = 0; b < bucketCount; b++) if (found[b]) result[i++] = bestPerBucket[b];
        return result;
    }


    /**
     * Renders a widget icon as a small custom-tinted bitmap. Widgets can't reuse retintTree's
     * approach of walking an already-inflated view tree and resampling colors -- a RemoteViews
     * is a declarative list of actions applied in the launcher's process, not a live tree this
     * process can inspect -- so each icon is tinted directly from its known semantic role
     * (accent vs. dim/neutral) at the call site instead. Returns null when Custom isn't active,
     * so callers fall back to the normal setImageViewResource(drawableRes) path unchanged.
     */
    public static Bitmap renderTintedIcon(Context context, int drawableResId, boolean accent) {
        if (Theming.family(context) != ThemeFamily.CUSTOM) return null;
        Drawable d = ContextCompat.getDrawable(context, drawableResId);
        if (d == null) return null;
        try {
            int size = Math.round(48 * context.getResources().getDisplayMetrics().density / 2f); // supersampled; the ImageView scales it to its own declared size
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Drawable mutable = d.mutate();
            mutable.setBounds(0, 0, size, size);
            mutable.setTint(colorForRole(context, accent ? ColorRole.ACCENT : ColorRole.INK_DIM));
            mutable.draw(canvas);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The color everything else is built on. This deliberately does NOT follow the photo's
     * dominant color anymore -- earlier rounds used the photo's average as the background,
     * which made ink contrast a coin flip (a dark-averaged photo of a bright scene got white
     * text over mostly-bright pixels) and made the blur/opacity sliders feel broken, since
     * lowering opacity revealed a flat block of whatever the most prominent color was instead
     * of a neutral backdrop.
     *
     * Photo mode composites onto the SYSTEM's light/dark backdrop (@color/custom_bg and its
     * values-night override): blur and opacity blend the photo toward a known neutral, and
     * every ink/glass color below derives from that same known neutral -- dark text when the
     * system is light, light text when it's dark, always legible regardless of what the photo
     * looks like. Color mode keeps the user's picked hex as the base, with the same
     * contrast-derived treatment relative to it.
     */
    public static int backgroundBaseColor(Context context) {
        if (SettingsStore.getCustomThemeMode(context) == CustomThemeMode.PHOTO) {
            return ContextCompat.getColor(context, R.color.custom_bg);
        }
        return SettingsStore.getCustomColorPrimary(context);
    }

    /**
     * A translucent-but-legible card surface tone, for widget row/card backgrounds -- widgets
     * can't reuse retintTree's "walk the live view tree" approach (a RemoteViews is a
     * declarative action list applied in the launcher's process, not a tree this process can
     * inspect), so callers set this directly via setInt(id, "setBackgroundColor", ...) on a
     * row's root. High enough opacity to keep text legible over any photo, low enough that
     * the blurred backdrop genuinely reads through as frosted glass.
     */
    public static int widgetCardSurfaceColor(Context context) {
        return colorForRole(context, ColorRole.SURFACE_2);
    }

    /** Whether the Custom background (system-based or Color-mode primary) reads as light or dark. */
    public static boolean isCustomBackgroundLight(Context context) {
        return isLight(colorForRole(context, ColorRole.BACKGROUND));
    }

    /**
     * Frosted-glass blur via progressive halving rather than one large downscale+upscale jump.
     * A single big jump (e.g. straight to 8% size then back up) interpolates the final image
     * from too few source pixels, and bilinear filtering over that large a scale factor reads
     * as blocky/pixelated rather than smoothly blurred. Repeatedly halving keeps every
     * individual step's scale factor modest (each step only ever interpolates from roughly
     * double or half its neighbor), which is what actually produces a smooth frosted look --
     * the same principle mipmapping relies on. Shared by the Activity background, the widget
     * background, and the theme picker's live preview, so there's exactly one blur
     * implementation instead of three copies that could quietly drift apart.
     */
    public static Bitmap applyFrostedBlur(Bitmap src, int blurLevel /* 0-25 */) {
        if (blurLevel <= 0) return src;
        int passes = 1 + Math.round((blurLevel / 25f) * 4f); // 1-5 halving passes

        Bitmap current = src;
        for (int i = 0; i < passes; i++) {
            int w = Math.max(2, current.getWidth() / 2);
            int h = Math.max(2, current.getHeight() / 2);
            current = Bitmap.createScaledBitmap(current, w, h, true);
        }
        for (int i = 0; i < passes; i++) {
            boolean lastStep = i == passes - 1;
            int w = lastStep ? src.getWidth() : current.getWidth() * 2;
            int h = lastStep ? src.getHeight() : current.getHeight() * 2;
            current = Bitmap.createScaledBitmap(current, w, h, true);
        }
        return current;
    }

    /**
     * Renders a widget's Custom-theme background (solid color or photo) as a single bitmap,
     * sized to the widget's actual pixel dimensions and clipped to a rounded rect matching the
     * app's card corner radius. Set via RemoteViews.setImageViewBitmap on a background ImageView
     * added to each widget layout -- deliberately bitmap-based rather than trying to recolor the
     * existing drawable background via a RemoteViews reflection action (setBackgroundColor works
     * for a flat fill, but there's no safe, first-class RemoteViews action for a *tinted rounded
     * shape*, and a reflection call with an unusual method name is exactly what caused the
     * earlier group-header crash). A no-op-returning-null when Custom isn't active, so callers
     * can skip the ImageView entirely for every other theme.
     *
     * Compositing order matches drawPhotoComposite exactly: system-neutral base fill, then the
     * blurred photo at the user's opacity, then a soft base-colored scrim. The widget path just
     * additionally clips the result to the rounded-corner shape.
     */
    public static Bitmap buildWidgetBackgroundBitmap(Context context, int widthPx, int heightPx, float cornerRadiusPx) {
        if (Theming.family(context) != ThemeFamily.CUSTOM) return null;
        widthPx = Math.max(1, widthPx);
        heightPx = Math.max(1, heightPx);

        Bitmap out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        android.graphics.Path clip = new android.graphics.Path();
        clip.addRoundRect(new android.graphics.RectF(0, 0, widthPx, heightPx), cornerRadiusPx, cornerRadiusPx, android.graphics.Path.Direction.CW);
        canvas.clipPath(clip);

        CustomThemeMode mode = SettingsStore.getCustomThemeMode(context);
        String photoPath = SettingsStore.getCustomPhotoPath(context);
        if (mode == CustomThemeMode.PHOTO && photoPath != null) {
            try {
                Bitmap photo = decodeFileDownsampled(photoPath, widthPx, heightPx);
                int blur = SettingsStore.getCustomPhotoBlur(context);
                drawPhotoComposite(canvas, context, photo, blur, SettingsStore.getCustomPhotoOpacity(context), widthPx, heightPx);
            } catch (Exception | OutOfMemoryError e) {
                Log.w("CustomThemeBackground", "Widget photo background falling back to solid color: " + e, e);
                canvas.drawColor(colorForRole(context, ColorRole.BACKGROUND));
            }
        } else {
            canvas.drawColor(colorForRole(context, ColorRole.BACKGROUND));
        }
        return out;
    }

    /**
     * The ONE photo-compositing recipe, shared by every surface that shows the backdrop
     * (activity root, widgets, dialogs, floating cards): base fill -> blurred photo at the
     * user's opacity -> a soft base-colored scrim on top.
     *
     * The scrim is what keeps ink legible over ANY photo: without it, a near-white region of
     * an otherwise-dark photo at high opacity pushes local luminance past what white text can
     * sit on. Scaled proportionally to the opacity setting so low-opacity (subtle backdrop)
     * stays subtle while high-opacity gets progressively calmed -- the slider keeps doing
     * what it says, it just always does it on top of a known, contrast-safe base instead of
     * over the photo's dominant color.
     */
    private static void drawPhotoComposite(Canvas canvas, Context context, Bitmap decodedPhoto,
                                            int blurLevel, int opacityPct, int w, int h) {
        int base = colorForRole(context, ColorRole.BACKGROUND);
        canvas.drawColor(base);

        Bitmap blurred = applyFrostedBlur(decodedPhoto, blurLevel);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setAlpha(Math.round(opacityPct / 100f * 255));
        drawCenterCrop(canvas, blurred, w, h, paint);

        Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
        scrim.setColor(base);
        scrim.setAlpha(Math.round((opacityPct / 100f) * 0.40f * 255));
        canvas.drawRect(0, 0, w, h, scrim);
    }

    /**
     * The theme picker's live preview, rendered through the exact same composite as every real
     * surface -- dragging the blur/opacity sliders shows what widgets and screens will actually
     * look like, instead of a raw uncomposited crop of the photo.
     */
    public static Bitmap buildThemePreviewBitmap(Context context, Bitmap photo, int blurLevel, int opacityPct) {
        int w = photo.getWidth(), h = photo.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        drawPhotoComposite(canvas, context, photo, blurLevel, opacityPct, w, h);
        return out;
    }

    /** Scales+centers a bitmap to fill a w x h area without distortion, cropping overflow (matches ImageView's centerCrop). */
    private static void drawCenterCrop(Canvas canvas, Bitmap bmp, int w, int h, Paint paint) {
        float scale = Math.max((float) w / bmp.getWidth(), (float) h / bmp.getHeight());
        float scaledW = bmp.getWidth() * scale;
        float scaledH = bmp.getHeight() * scale;
        float left = (w - scaledW) / 2f;
        float top = (h - scaledH) / 2f;
        android.graphics.RectF dst = new android.graphics.RectF(left, top, left + scaledW, top + scaledH);
        canvas.drawBitmap(bmp, null, dst, paint);
    }

    // ---- The independent color engine -------------------------------------------------------

    private enum ColorRole { BACKGROUND, SURFACE, SURFACE_2, ACCENT, ACCENT_DIM, ERROR, SUCCESS, INK, INK_DIM, LINE, LINE_STRONG, CONTROL_FILL }

    /**
     * Computes a role's color purely from the user's Custom settings. This is the single
     * source of truth every other method below delegates to, so "what does Custom's accent
     * color look like" is answered in exactly one place.
     *
     * The base/background is NOT the photo's dominant color (see backgroundBaseColor) -- it's
     * the system light/dark backdrop for Photo mode, or the user's picked hex for Color mode.
     * The photo still decides the ACCENT (so highlights genuinely relate to the picture), but
     * everything structural -- background, ink, glass surfaces -- derives from the known
     * neutral base, which is what makes text contrast deterministic instead of dependent on
     * how bright the photo happens to be.
     */
    private static int colorForRole(Context context, ColorRole role) {
        int base = backgroundBaseColor(context);
        int accent;
        if (SettingsStore.getCustomThemeMode(context) == CustomThemeMode.PHOTO) {
            // Photo mode: accent derives from what's actually in the picture, not from
            // whatever Color mode's saved primary/accent happen to be. Extracted once, at
            // pick time (see extractDominantColors). Falls back to the photo's average tone,
            // then to the base itself -- ensureAccentContrast below guarantees even that
            // degenerate case lands on something visibly distinct from the backdrop.
            int derivedAccent = SettingsStore.getCustomPhotoDerivedAccent(context);
            int derivedPrimary = SettingsStore.getCustomPhotoDerivedPrimary(context);
            accent = derivedAccent != 0 ? derivedAccent
                    : (derivedPrimary != 0 ? derivedPrimary : base);
        } else {
            int accentSetting = SettingsStore.getCustomColorAccent(context);
            accent = accentSetting != 0 ? accentSetting : base;
        }
        // A filled Primary control (DONE, PARSE & SAVE...) needs to read as "the button"
        // against its own SURFACE card and against the base backdrop -- nudged AFTER the
        // derivation above so both branches (photo and manual) get the same guarantee.
        accent = ensureAccentContrast(base, accent);
        boolean lightBg = isLight(base);
        int nearBlackOrWhite = lightBg ? Color.BLACK : Color.WHITE;

        // Frost surfaces are a literal WHITE FILM laid over the backdrop -- the
        // iOS/One UI control-center model: a quiet white film on a dark system, a
        // heavy milky one on a light system. This is what actually reads as "glass":
        // a panel a clear step lighter than what's behind it while the blurred photo
        // still shows through. (The previous model -- a near-opaque tone derived from
        // the base -- landed on almost the same gray as the raw Adaptive fills it
        // replaces, so no amount of alpha tuning could make it read as anything but a
        // flat slab.)
        // ACCENT is the only role driven by the user's own picked color; ERROR and SUCCESS
        // below are the two deliberate exceptions -- both need to read as "wrong"/"correct"
        // regardless of what that accent happens to be. Every other role is a plain white
        // film over the backdrop at differing strength.

        switch (role) {
            case BACKGROUND: return base;
            // Layered by nesting depth: SURFACE is the outermost/largest rectangle in any
            // given context (Setup's outer card, a dialog's panel); SURFACE_2 is a rectangle
            // NESTED inside one (a class row, an input box, a widget's hero/table/note
            // rectangle). On dark systems both are quiet white films -- the photo breathes
            // through and the step between them gives the nesting; on light systems the
            // film is heavier so panels stay bright and distinct from the warm-white base.
            case SURFACE: return withAlpha(Color.WHITE, lightBg ? 0.62f : 0.16f);
            case SURFACE_2: return withAlpha(Color.WHITE, lightBg ? 0.76f : 0.28f);
            case ACCENT: return accent;
            case ACCENT_DIM: return withAlpha(accent, 0.40f);
            // Deliberately NOT derived from the custom palette -- an error state needs to read
            // as "wrong" regardless of what color the user picked.
            case ERROR: return 0xFFE05555;
            // Same reasoning as ERROR, mirrored: a success state needs to read as "correct"
            // regardless of what color the user picked for their own accent -- which is
            // exactly the bug this fixes. "adaptive_green" used to fall through to ACCENT
            // above (nothing routed it anywhere else), so under Custom theme every "success"
            // status message was silently painted in the user's own accent color instead of
            // green -- invisible as a bug whenever that accent happened to be greenish, but
            // read as "wrong color, looks like an error" whenever the accent was a red/pink/
            // orange, which is exactly what was reported. Tuned to a similar brightness to
            // ERROR's 0xFFE05555 so the two read as a matched pair over a photo backdrop.
            case SUCCESS: return 0xFF3ECF6B;
            case INK: return withAlpha(nearBlackOrWhite, 1f);
            // Status/instruction text often sits directly on the composited backdrop rather
            // than inside a SURFACE-toned card; 0.78 keeps it clearly secondary while leaving
            // real contrast margin over both the light and dark system backdrops.
            case INK_DIM: return withAlpha(nearBlackOrWhite, 0.78f);
            // Hairline dividers/borders -- quiet by design.
            case LINE: return withAlpha(nearBlackOrWhite, 0.20f);
            // Strokes that must carry a little more presence than a passive divider (outline
            // control borders, the progress-ring track). Still just a whisper of ink, never a
            // filled surface.
            case LINE_STRONG: return withAlpha(nearBlackOrWhite, 0.34f);
            // The FILL of an outline/Secondary control (a chip, a Secondary button): a very
            // subtle wash of the ink color over whatever is behind it -- Apple/Samsung-style
            // quiet controls, not solid slabs.
            case CONTROL_FILL: return withAlpha(nearBlackOrWhite, 0.13f);
            default: return base;
        }
    }

    /** Clamps an HSV component (or any 0-1 float) into range after arithmetic that can drift outside it. */
    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /**
     * Guarantees an accent reads as a deliberate, distinct color against the base WITHOUT
     * leaving its hue family. The old version rotated the hue a hard +40 degrees whenever the
     * accent sat near the primary -- that produced clashing neighbors (salmon next to maroon,
     * teal next to green) instead of harmony. Analogous palettes (iOS/Material dynamic
     * schemes) keep the hue and move LIGHTNESS: on a dark backdrop the accent is lifted to a
     * brighter step of the same hue; on a light one it is pressed into a deeper shade.
     */
    private static int ensureAccentContrast(int base, int accent) {
        double baseLum = luminance(base);
        double accentLum = luminance(accent);

        float[] baseHsv = new float[3];
        float[] accentHsv = new float[3];
        Color.colorToHSV(base, baseHsv);
        Color.colorToHSV(accent, accentHsv);

        boolean lumaOk = Math.abs(accentLum - baseLum) >= 0.30;
        // A pale, nearly-gray accent can clear the luma gate on brightness alone while still
        // reading as "a slightly lighter/darker shade of the same muted photo," not a
        // deliberate highlight color -- this is exactly what let some photos' extracted
        // accent (typically a low-saturation olive/khaki/gray-green) slip through unchanged
        // even after the value/luma mismatch below was fixed for the branch that DOES run:
        // this early return never checked saturation at all, only luma distance. Saturation
        // now has to clear its own bar independently of how far away the luma already is.
        boolean satOk = accentHsv[1] >= 0.35f;
        if (lumaOk && satOk) return accent;

        // Same hue family -- at most a whisper of drift so it still reads as a related color.
        float hue = accentHsv[0];
        if (accentHsv[1] > 0.15f && baseHsv[1] > 0.15f) {
            float drift = baseHsv[0] - accentHsv[0];
            while (drift > 180f) drift -= 360f;
            while (drift < -180f) drift += 360f;
            drift = Math.max(-10f, Math.min(10f, drift));
            hue = (hue + drift + 360f) % 360f;
        }
        // Floor on saturation keeps the result reading as "a color," not a washed-out
        // pastel -- raised from a prior 0.42, which could still land on something muddy.
        float sat = clamp01(Math.max(accentHsv[1], 0.55f));

        // Solve for VALUE directly against the SAME luma() metric the gate above just used,
        // instead of nudging HSV "Value" by a fixed +/-0.5 and hoping it lands somewhere
        // equivalent. Those are different scales -- Value is just max(R,G,B)/255, while luma
        // weights channels 0.299/0.587/0.114 -- so for a saturated hue a fixed Value nudge
        // can undershoot the actual luma gap by a wide margin (this is exactly what made
        // some photos' extracted accent -- often a muted yellow/olive/khaki -- still read as
        // "not highlighted": the nudge produced a color that LOOKED brighter by its Value
        // number but didn't clear the same luma bar the gate check above requires). Luma is
        // exactly proportional to Value for a fixed hue/saturation (V uniformly scales the
        // whole RGB triple in the HSV model), so the Value needed to hit a target luma can be
        // solved in closed form rather than guessed.
        double lumaAtFullValue = luminance(Color.HSVToColor(new float[]{hue, sat, 1f}));
        // If luma already cleared its own gate, only saturation needed fixing -- target
        // roughly the SAME luma level rather than also pushing it further away. Otherwise
        // target 0.38 past base for real headroom above the gate being solved for -- capped
        // at 0.55 on the light-base side specifically: isLight() (used elsewhere to decide
        // black vs. white ink on top of this fill) flips at 0.60, and an uncapped base-0.38
        // target against a typical light backdrop (custom_bg's light luma is ~0.95) lands at
        // ~0.57 -- only 0.03 below that flip point. HSV->RGB integer rounding, or a
        // marginally different lumaAtFullValue for some hues, could easily push the actual
        // rendered luma to either side of 0.60, making the ink-color decision flip
        // inconsistently between rebuilds/hues for what should be a stable design. The dark-
        // base target (~0.44) already sits with plenty of margin (0.16) below 0.60, so only
        // the light-base branch needs the cap.
        double targetLuma = lumaOk ? accentLum
                : (baseLum > 0.5 ? Math.max(0.05, Math.min(0.55, baseLum - 0.38)) : Math.min(0.95, baseLum + 0.38));
        float value = lumaAtFullValue > 0.001
                ? clamp01((float) (targetLuma / lumaAtFullValue))
                : 1f;
        return Color.HSVToColor(new float[]{hue, sat, value});
    }

    /** Perceived luminance (ITU-R BT.601), consistent with isLight(). */
    private static double luminance(int color) {
        return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
    }

    private static int withAlpha(int color, float alpha) {
        return (Math.round(alpha * 255) << 24) | (color & 0x00FFFFFF);
    }

    /** Perceptual luminance (ITU-R BT.601) -- decides whether ink on this color should be black or white. */
    private static boolean isLight(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return luminance > 0.6;
    }

    /**
     * Contrast-picking for TRANSLUCENT surfaces. isLight() reads only the RGB, which lies
     * about a low-alpha film: a 16% white film would be judged "light" and get black ink
     * over a dark backdrop -- invisible text. Composite the film over the known base
     * backdrop first; the photo behind activity cards and widgets is pulled toward that
     * same base by the scrim, so the composite predicts the panel's perceived tone closely
     * enough to pick ink polarity safely.
     */
    private static boolean readsAsLight(Context context, int surfaceColor) {
        int base = backgroundBaseColor(context);
        float a = Color.alpha(surfaceColor) / 255f;
        int r = Math.round(Color.red(surfaceColor) * a + Color.red(base) * (1 - a));
        int g = Math.round(Color.green(surfaceColor) * a + Color.green(base) * (1 - a));
        int b = Math.round(Color.blue(surfaceColor) * a + Color.blue(base) * (1 - a));
        return isLight(Color.rgb(r, g, b));
    }

    /**
     * Every Adaptive color this app defines, and the role it plays. This is the only place
     * Custom mode reads anything from Adaptive at all -- everything it reads is discarded
     * immediately after classification; no Adaptive value is ever blended into or otherwise
     * influences a Custom color.
     */
    private static final Map<String, ColorRole> ROLE_BY_NAME = new HashMap<>();
    static {
        ROLE_BY_NAME.put("adaptive_bg", ColorRole.BACKGROUND);
        ROLE_BY_NAME.put("adaptive_surface", ColorRole.SURFACE);
        ROLE_BY_NAME.put("adaptive_surface2", ColorRole.SURFACE_2);
        ROLE_BY_NAME.put("adaptive_accent", ColorRole.ACCENT);
        ROLE_BY_NAME.put("adaptive_accent_dim", ColorRole.ACCENT_DIM);
        ROLE_BY_NAME.put("adaptive_green", ColorRole.SUCCESS);
        ROLE_BY_NAME.put("adaptive_error", ColorRole.ERROR);
        ROLE_BY_NAME.put("adaptive_ink", ColorRole.INK);
        ROLE_BY_NAME.put("adaptive_ink_dim", ColorRole.INK_DIM);
        ROLE_BY_NAME.put("adaptive_line", ColorRole.LINE);
        ROLE_BY_NAME.put("adaptive_line_strong", ColorRole.LINE_STRONG);
    }

    /** For Theming.color() callers that resolve a specific Adaptive color resource id in Java. */
    public static Integer deriveColor(Context context, int adaptiveColorRes) {
        if (Theming.family(context) != ThemeFamily.CUSTOM) return null;
        String name;
        try {
            name = context.getResources().getResourceEntryName(adaptiveColorRes);
        } catch (Exception e) {
            return null;
        }
        ColorRole role = ROLE_BY_NAME.get(name);
        return role != null ? colorForRole(context, role) : null;
    }

    /**
     * Walks an already-inflated view tree and repaints anything currently showing one of
     * Adaptive's known colors. Generalized beyond flat TextView/ColorDrawable colors: any
     * drawable (a chip's shape-drawable fill, an icon's baked-in vector tint) is sampled by
     * rendering it to a tiny offscreen bitmap and reading a pixel, since Android doesn't expose
     * a baked-in vector tint as a queryable property. This is what reaches icons and chip fills
     * generically instead of needing every one hand-enumerated (tintChip's 6 call sites stay as
     * an explicit, guaranteed-correct belt-and-suspenders for the highest-visibility ones).
     * Switches are handled separately, since their thumb/track colors come from the Activity's
     * theme attributes, not from any adaptive_* resource, so sampling can't classify them.
     */
    private static void retintTree(Context context, View root) {
        Map<Integer, ColorRole> byValue = new HashMap<>();
        for (Map.Entry<String, ColorRole> e : ROLE_BY_NAME.entrySet()) {
            int resId = context.getResources().getIdentifier(e.getKey(), "color", context.getPackageName());
            if (resId == 0) continue;
            try {
                byValue.putIfAbsent(ContextCompat.getColor(context, resId), e.getValue());
            } catch (Exception ignored) {
                // A missing/renamed resource shouldn't take down the whole repaint pass.
            }
        }
        walk(context, root, byValue);
    }

    private static void walk(Context context, View view, Map<Integer, ColorRole> byValue) {
        if (view instanceof SwitchCompat) {
            retintSwitch(context, (SwitchCompat) view);
        }

        // Background first -- text color (below) needs to know what it's actually being
        // drawn on, not just what role it originally mapped to.
        Integer resolvedBgColor = null;

        // android:backgroundTint is a separate View property from the background drawable --
        // a button styled with backgroundTint="@color/adaptive_accent" has a plain/neutral
        // placeholder drawable underneath that's tinted at render time. Sampling that drawable
        // (below) only ever sees its untinted fill, never the tint that's actually visible, so
        // this has to be checked and overridden first or those buttons stay un-recolored.
        ColorStateList existingTint = view.getBackgroundTintList();
        if (existingTint != null) {
            ColorRole tintRole = byValue.get(existingTint.getDefaultColor());
            if (tintRole != null) {
                resolvedBgColor = colorForRole(context, tintRole);
                view.setBackgroundTintList(ColorStateList.valueOf(resolvedBgColor));
            }
        }

        if (resolvedBgColor == null) {
            Drawable bg = view.getBackground();
            if (bg instanceof ColorDrawable) {
                ColorRole role = byValue.get(((ColorDrawable) bg).getColor());
                if (role != null) {
                    resolvedBgColor = colorForRole(context, role);
                    view.setBackgroundColor(resolvedBgColor);
                }
            } else if (bg != null) {
                Integer sampled = sampleDrawableColor(bg);
                ColorRole role = sampled != null ? byValue.get(sampled) : null;
                if (role != null) {
                    resolvedBgColor = colorForRole(context, role);
                    // Shape drawables get their FILL and STROKE recolored independently --
                    // setTint would flatten both into one color, erasing the hairline that
                    // makes a panel read as a distinct layer over the photo (the "still
                    // solid" look). setColor/setStroke keep the original corner radius,
                    // so a card stays a card and an input row stays an input row.
                    if (bg instanceof GradientDrawable) {
                        GradientDrawable gd = (GradientDrawable) bg.mutate();
                        gd.setColor(resolvedBgColor);
                        int strokeColor = (role == ColorRole.ACCENT)
                                ? resolvedBgColor
                                : colorForRole(context, ColorRole.LINE_STRONG);
                        gd.setStroke(Math.round(
                                context.getResources().getDisplayMetrics().density), strokeColor);
                        view.setBackground(gd);
                    } else {
                        Drawable mutated = bg.mutate();
                        mutated.setTint(resolvedBgColor);
                        view.setBackground(mutated);
                    }
                }
            }
        }

        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            ColorRole textRole = byValue.get(tv.getCurrentTextColor());
            if (textRole != null) {
                if (resolvedBgColor != null) {
                    // This view is its own colored surface (a filled button/chip, not plain
                    // text on the screen background) -- guarantee contrast against what it's
                    // ACTUALLY sitting on now, rather than trusting the text's old role to
                    // still pair correctly with the new, independently-derived background.
                    tv.setTextColor(readsAsLight(context, resolvedBgColor) ? Color.BLACK : Color.WHITE);
                } else {
                    tv.setTextColor(colorForRole(context, textRole));
                }
            }
            ColorRole hintRole = byValue.get(tv.getCurrentHintTextColor());
            if (hintRole != null) tv.setHintTextColor(colorForRole(context, hintRole));
        }

        if (view instanceof ImageView) {
            ImageView iv = (ImageView) view;
            Drawable icon = iv.getDrawable();
            Integer sampled = icon != null ? sampleDrawableColor(icon) : null;
            ColorRole iconRole = sampled != null ? byValue.get(sampled) : null;
            if (iconRole != null) {
                // Same local-contrast principle as text: an icon sitting on a filled surface
                // this view just got recolored to needs to read against THAT color.
                iv.setColorFilter(resolvedBgColor != null
                        ? (readsAsLight(context, resolvedBgColor) ? Color.BLACK : Color.WHITE)
                        : colorForRole(context, iconRole), PorterDuff.Mode.SRC_IN);
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) walk(context, vg.getChildAt(i), byValue);
        }
    }

    /**
     * Renders a drawable to a small offscreen bitmap and returns a representative opaque pixel
     * color, or null if nothing usable was found. Samples near the center first -- rounded-corner
     * shape drawables have anti-aliased, non-representative pixels right at their edges --
     * falling back to a full scan if the center happens to be transparent (e.g. a ring/outline
     * icon).
     */
    private static Integer sampleDrawableColor(Drawable d) {
        try {
            int w = Math.min(Math.max(d.getIntrinsicWidth(), 1), 24);
            int h = Math.min(Math.max(d.getIntrinsicHeight(), 1), 24);
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Drawable.ConstantState state = d.getConstantState();
            Drawable copy = state != null ? state.newDrawable().mutate() : d;
            copy.setBounds(0, 0, w, h);
            copy.draw(canvas);

            int centerPixel = bmp.getPixel(w / 2, h / 2);
            if (Color.alpha(centerPixel) > 200) return centerPixel | 0xFF000000;

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int pixel = bmp.getPixel(x, y);
                    if (Color.alpha(pixel) > 200) return pixel | 0xFF000000;
                }
            }
        } catch (Exception ignored) {
            // A drawable that can't be safely rendered offscreen just doesn't get resampled --
            // not worth failing the whole repaint pass over.
        }
        return null;
    }

    /**
     * Switch thumb/track colors come from the Activity theme's control-color attributes, not
     * from any adaptive_* resource, so retintTree's sampling-based classification can't reach
     * them -- tinted unconditionally instead, on-state to the accent, off-state to a dim/line
     * tone, whenever Custom mode is active.
     */
    private static void retintSwitch(Context context, SwitchCompat sw) {
        int accent = colorForRole(context, ColorRole.ACCENT);
        int off = colorForRole(context, ColorRole.LINE_STRONG);
        ColorStateList thumbTint = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{accent, colorForRole(context, ColorRole.SURFACE_2)});
        ColorStateList trackTint = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{withAlpha(accent, 0.5f), off});
        sw.setThumbTintList(thumbTint);
        sw.setTrackTintList(trackTint);
    }

    public enum ControlTier { PRIMARY, SECONDARY, QUIET, DESTRUCTIVE }

    /**
     * The one Primary/Secondary/Quiet styling pass for Activity-side controls -- tintChip's more
     * general form, and the explicit call path the audit asked for instead of leaving buttons to
     * retintTree's sampling alone. Handles a control with NO existing background at all (Cancel,
     * a plain "DONE" TextView): Primary/Secondary construct a rounded fill at runtime rather than
     * requiring the layout to have already declared a tintable shape.
     *
     * Secondary specifically does NOT reuse SURFACE -- a Secondary button living inside a card
     * that's already SURFACE-toned would sample to that same color and visually disappear into
     * its own backdrop (this is why CLEAR/CHOOSE/SAVED SCHEDULES read as flat even though they
     * were already being retinted correctly by color: retintTree has no notion of "this row is
     * nested inside a card," so it resolves both to the same tone). Stepping to SURFACE_2 plus a
     * LINE_STRONG stroke is what actually makes it read as its own control.
     */
    public static void styleControl(Context context, View control, ControlTier tier) {
        if (control == null || Theming.family(context) != ThemeFamily.CUSTOM) return;
        float density = context.getResources().getDisplayMetrics().density;
        boolean lightBg = isLight(colorForRole(context, ColorRole.BACKGROUND));
        int inkTone = lightBg ? Color.BLACK : Color.WHITE;
        int minControlHeight = Math.round(48 * density);
        // Every tier below sets a rounded colored background + minimum height, but relies
        // on whatever padding the control already had -- fine for a full-width app button
        // (already padded by its own layout), wrong for AlertDialog's built-in buttons,
        // whose platform default padding is minimal. Without this, the new pill-shaped
        // background reads as text hugging its own edges rather than a properly sized
        // button. Applied uniformly up front so every tier below gets it -- EXCEPT
        // ImageView icon buttons (e.g. the rename/delete pencil and trash icons on a saved-
        // schedule row): those are a small FIXED size (44dp) with padding already
        // calibrated to center a ~22dp icon inside it. Overwriting that with 20dp/10dp
        // left only ~4dp of width for the actual glyph -- squeezing a pencil or trash can
        // into a thin, unrecognizable sliver, which is exactly what showed up as "buttons
        // with weird icons that aren't recognizable." Text buttons still need this; only
        // fixed-size icon buttons need their own existing padding left alone.
        if (!(control instanceof ImageView)) {
            int hPad = Math.round(20 * density), vPad = Math.round(10 * density);
            control.setPadding(hPad, vPad, hPad, vPad);
        }
        switch (tier) {
            case PRIMARY: {
                int accent = colorForRole(context, ColorRole.ACCENT);
                GradientDrawable fill = new GradientDrawable();
                fill.setColor(accent);
                fill.setCornerRadius(12 * density);
                control.setBackground(fill);
                if (control.getMinimumHeight() < minControlHeight) control.setMinimumHeight(minControlHeight);
                if (control instanceof TextView) {
                    ((TextView) control).setTextColor(isLight(accent) ? Color.BLACK : Color.WHITE);
                }
                break;
            }
            case SECONDARY: {
                // A quiet control: a whisper of ink over whatever is behind it plus a
                // hairline border -- never a solid slab. (The old version filled these with
                // near-opaque SURFACE_2 glass, which on dark setups rendered as glaring
                // light-gray bars with white text.)
                GradientDrawable fill = new GradientDrawable();
                fill.setColor(withAlpha(inkTone, 0.12f));
                fill.setCornerRadius(12 * density);
                fill.setStroke(Math.round(density), withAlpha(inkTone, 0.26f));
                control.setBackground(fill);
                if (control.getMinimumHeight() < minControlHeight) control.setMinimumHeight(minControlHeight);
                if (control instanceof TextView) {
                    ((TextView) control).setTextColor(colorForRole(context, ColorRole.INK));
                }
                break;
            }
            case QUIET: {
                // Quiet is a sizing tier, not a shapeless one: CANCEL/CLEAR DATES sit in
                // stacks beside PRIMARY/SECONDARY rectangles, so they get the same footprint
                // -- a fainter wash + softer border -- with the dimmed label doing the
                // de-emphasis. Bare unstyled text next to filled buttons read as broken.
                GradientDrawable fill = new GradientDrawable();
                fill.setColor(withAlpha(inkTone, 0.07f));
                fill.setCornerRadius(12 * density);
                fill.setStroke(Math.round(density), withAlpha(inkTone, 0.16f));
                control.setBackground(fill);
                if (control instanceof TextView) {
                    ((TextView) control).setTextColor(colorForRole(context, ColorRole.INK_DIM));
                }
                int minTapPx = Math.round(44 * density);
                if (control.getMinimumHeight() < minTapPx) control.setMinimumHeight(minTapPx);
                break;
            }
            case DESTRUCTIVE: {
                // Same footprint as Secondary, but the wash/border/label all take the
                // error tone -- CLEAR reads as destructive without becoming a shout.
                int error = colorForRole(context, ColorRole.ERROR);
                GradientDrawable fill = new GradientDrawable();
                fill.setColor(withAlpha(error, 0.16f));
                fill.setCornerRadius(12 * density);
                fill.setStroke(Math.round(density), withAlpha(error, 0.60f));
                control.setBackground(fill);
                if (control.getMinimumHeight() < minControlHeight) control.setMinimumHeight(minControlHeight);
                if (control instanceof TextView) {
                    ((TextView) control).setTextColor(error);
                }
                break;
            }
        }
    }

    /**
     * Tints a chip/button's drawable background for Custom mode. Explicit, guaranteed-correct
     * treatment for the app's small set of known chip call sites (filled = accent with
     * contrast-derived text, outline = border derived from the background's own luminance) --
     * kept alongside retintTree's generic sampling-based repaint as a belt-and-suspenders for
     * the highest-visibility elements, not superseded by it.
     */
    public static void tintChip(Context context, View chip, boolean filled) {
        if (Theming.family(context) != ThemeFamily.CUSTOM) return;
        Drawable bg = chip.getBackground();
        if (bg == null) return;
        bg = bg.mutate();

        int fill;
        int strokeColor;
        int textColor;
        if (filled) {
            fill = colorForRole(context, ColorRole.ACCENT);
            strokeColor = fill; // accent-on-accent keeps the silhouette unchanged
            textColor = isLight(fill) ? 0xFF000000 : 0xFFFFFFFF;
        } else {
            // Outline chip = a whisper of ink over the backdrop plus its hairline -- not a
            // filled slab and not a borderless wash either.
            fill = colorForRole(context, ColorRole.CONTROL_FILL);
            strokeColor = colorForRole(context, ColorRole.LINE_STRONG);
            textColor = colorForRole(context, ColorRole.INK_DIM);
        }
        if (bg instanceof GradientDrawable) {
            GradientDrawable gd = (GradientDrawable) bg;
            gd.setColor(fill);
            gd.setStroke(Math.round(
                    context.getResources().getDisplayMetrics().density), strokeColor);
        } else {
            bg.setTint(fill);
        }
        if (chip instanceof TextView) {
            ((TextView) chip).setTextColor(textColor);
        }
        chip.setBackground(bg);
    }

    /**
     * tintChip's RemoteViews equivalent -- widgets can't reuse tintChip itself since a
     * RemoteViews is a declarative action list applied in the launcher's process, not a live
     * View this process can mutate directly. {@code chipViewId} gets the recolored background;
     * {@code labelViewId} (pass the same id when the chip IS its own label, e.g. a TextView
     * chip; pass a distinct id when the label is a separate child, e.g. a LinearLayout chip
     * wrapping a TextView; pass 0 if there's no separately-colorable label at all) gets
     * contrast-guaranteed text. A no-op when Custom isn't active.
     *
     * Background tinting itself needs an API split: RemoteViews.setColorStateList (the only
     * cross-process way to set backgroundTintList, which recolors the existing chip drawable
     * -- accent fill or outline -- while preserving its actual shape) only exists from API 31.
     * Below that there's no RemoteViews action that can tint an arbitrary shape drawable, so the
     * fallback is a flat setBackgroundColor -- the right color, square corners instead of the
     * chip's normal rounded/pill shape. Still correct, just not pixel-perfect on older Android.
     */
    public static void tintWidgetChip(Context context, RemoteViews rv, int chipViewId, int labelViewId, boolean filled) {
        if (Theming.family(context) != ThemeFamily.CUSTOM) return;
        int bgColor = filled ? colorForRole(context, ColorRole.ACCENT) : colorForRole(context, ColorRole.CONTROL_FILL);
        int textColor = filled
                ? (isLight(bgColor) ? Color.BLACK : Color.WHITE)
                : colorForRole(context, ColorRole.INK_DIM);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rv.setColorStateList(chipViewId, "setBackgroundTintList", ColorStateList.valueOf(bgColor));
        } else {
            rv.setInt(chipViewId, "setBackgroundColor", bgColor);
        }
        if (labelViewId != 0) rv.setInt(labelViewId, "setTextColor", textColor);
    }

    /** Convenience overload for a chip that IS its own label (a plain TextView chip). */
    public static void tintWidgetChip(Context context, RemoteViews rv, int chipViewId, boolean filled) {
        tintWidgetChip(context, rv, chipViewId, chipViewId, filled);
    }

    /**
     * Retints one already-inflated subtree without touching the rest of the Activity -- for
     * content built/rebuilt after the last full apply() pass and not part of a Dialog window
     * (e.g. rows added into a container that lives inside an already-shown dialog's view, or any
     * other late-inflated group). Runs retintTree for text/icon roles and then the explicit
     * structural-glass pass, matching what apply() does to a whole screen. A no-op when Custom
     * isn't active or subtree is null.
     */
    public static void applySubtree(Context context, View subtree) {
        if (subtree == null || Theming.family(context) != ThemeFamily.CUSTOM) return;
        retintTree(context, subtree);
        applyGlassSurfaces(context, subtree);
    }

    /**
     * Retints an already-SHOWN Dialog's window for Custom mode: a rounded, SURFACE-toned panel
     * background (so the dialog reads as part of the same frosted UI instead of a plain system
     * card) plus a retint pass over its content for anything built from an Adaptive-mapped
     * layout or hand-built View tree. Call this right after .show() (or dialog.show()) -- the
     * window doesn't exist yet before that. A no-op when Custom isn't active.
     *
     * Native AlertDialog chrome -- its own title/message TextViews, button ripple colors -- isn't
     * reachable this way, since those never hold one of Adaptive's own color values for
     * retintTree to recognize; only the panel itself and any content passed via setView()/
     * inflate() are covered. Good enough to fix "the dialog doesn't look frosted"; not a full
     * reskin of every pixel AlertDialog draws.
     */
    public static void applyToDialog(Dialog dialog) {
        if (dialog == null) return;
        Context context = dialog.getContext();
        if (Theming.family(context) != ThemeFamily.CUSTOM) return;
        Window window = dialog.getWindow();
        if (window == null) return;

        CustomThemeMode mode = SettingsStore.getCustomThemeMode(context);
        String photoPath = SettingsStore.getCustomPhotoPath(context);
        if (mode == CustomThemeMode.PHOTO && photoPath != null) {
            applyDialogPhotoBackground(context, window, photoPath);
        } else {
            applyDialogSolidPanel(context, window);
        }

        retintTree(context, window.getDecorView());
        applyGlassSurfaces(context, window.getDecorView());
    }

    private static void applyDialogSolidPanel(Context context, Window window) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(colorForRole(context, ColorRole.SURFACE));
        panel.setCornerRadius(22 * density); // matches card_bg_*'s corner radius
        // Hairline border in the ink tone -- an accent-colored stroke read as a hard colored
        // outline; iOS-style dialogs separate via elevation and a quiet edge instead.
        panel.setStroke(Math.round(density), colorForRole(context, ColorRole.LINE_STRONG));
        window.setBackgroundDrawable(panel);
    }

    /** applyCardPhotoBackground's counterpart for an AlertDialog's window -- see that method. */
    private static void applyDialogPhotoBackground(Context context, Window window, String photoPath) {
        applyDialogSolidPanel(context, window); // neutral placeholder while waiting for real bounds
        View decor = window.getDecorView();

        Runnable render = () -> {
            int w = decor.getWidth();
            int h = decor.getHeight();
            if (w <= 0 || h <= 0) return; // still nothing usable -- keep the placeholder panel
            try {
                float density = context.getResources().getDisplayMetrics().density;
                float radiusPx = 22 * density;

                Bitmap decoded = decodeFileDownsampled(photoPath, w, h);
                decoded = applyFrostedBlur(decoded, SettingsStore.getCustomPhotoBlur(context));

                Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(out);
                Path clip = new Path();
                clip.addRoundRect(new RectF(0, 0, w, h), radiusPx, radiusPx, Path.Direction.CW);
                canvas.clipPath(clip);
                // Same recipe as every other surface: base fill -> photo at opacity -> scrim.
                drawPhotoComposite(canvas, context, decoded, SettingsStore.getCustomPhotoBlur(context),
                        SettingsStore.getCustomPhotoOpacity(context), w, h);
                // The dialog's own SURFACE layer on top, so panel content reads like a card
                // sitting on the backdrop rather than directly on the raw photo.
                canvas.drawColor(colorForRole(context, ColorRole.SURFACE));

                Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                stroke.setStyle(Paint.Style.STROKE);
                float strokeWidthPx = density;
                stroke.setStrokeWidth(strokeWidthPx);
                stroke.setColor(colorForRole(context, ColorRole.LINE_STRONG));
                float inset = strokeWidthPx / 2f;
                canvas.drawRoundRect(new RectF(inset, inset, w - inset, h - inset), radiusPx, radiusPx, stroke);

                window.setBackgroundDrawable(new BitmapDrawable(context.getResources(), out));
            } catch (Exception | OutOfMemoryError e) {
                Log.w("CustomThemeBackground", "Dialog photo background failed, keeping neutral panel: " + e, e);
            }
        };

        if (decor.getWidth() > 0 && decor.getHeight() > 0) {
            render.run();
        } else {
            decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    decor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    render.run();
                }
            });
        }
    }

    /**
     * Runs every button an AlertDialog actually has through styleControl in one call: POSITIVE
     * (the action the dialog exists for -- SAVE, REMOVE, Agree) as Primary, NEUTRAL (a real
     * third choice, e.g. DELETE alongside SAVE/CANCEL) as Secondary, NEGATIVE (Cancel/Decline)
     * as Quiet. getButton() returns null for any slot the dialog didn't set, and styleControl
     * already no-ops on a null control, so this is safe to call unconditionally on any
     * AlertDialog regardless of which buttons it actually has. Call AFTER .show() -- the button
     * views don't exist before that.
     */
    public static void styleDialogButtons(Context context, AlertDialog dialog) {
        if (dialog == null) return;
        View negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        View neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        View positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        styleControl(context, positive, ControlTier.PRIMARY);
        styleControl(context, neutral, ControlTier.SECONDARY);
        styleControl(context, negative, ControlTier.QUIET);
        // styleControl above only touches each button's own background/padding/height -- it
        // has no way to reach the buttonPanel LinearLayout that actually places them side by
        // side. The platform's stock buttons get their visual separation from that style's
        // own minimal built-in padding reading as "enough" gap; once we replace it with a
        // filled pill background, the same zero-margin layout makes adjacent pills touch (the
        // "buttons aren't properly sized" bug -- they're sized fine, they just have no
        // breathing room between them). Explicit inter-button margin fixes that regardless of
        // how many of the three slots are actually present.
        float density = context.getResources().getDisplayMetrics().density;
        int gap = Math.round(8 * density);
        addDialogButtonGap(negative, gap);
        addDialogButtonGap(neutral, gap);
        addDialogButtonGap(positive, gap);

        // The same fill-in-a-pill problem shows up at the row's own edges, not just between
        // buttons: the stock buttonPanel's built-in side padding (a couple dp, tuned for bare
        // flat text buttons) reads as "touching the card's rounded corner" once DELETE/CANCEL/
        // SAVE become filled/washed pills -- most visible on a 3-button row, where NEUTRAL sits
        // pinned flush to the panel's start edge. Match the 20dp side inset the dialog's own
        // content (dialog_edit_class_*, dialog_add_class_*, ...) already uses.
        //
        // android.R.id.buttonPanel is an AOSP-internal id (not part of the public SDK), so it
        // doesn't exist as a compileable symbol and can't be looked up via findViewById at all.
        // The buttonPanel is reachable another way instead: it's simply the direct parent
        // ViewGroup shared by whichever of the three buttons above actually exist.
        if (Theming.family(context) == ThemeFamily.CUSTOM) {
            View anyButton = positive != null ? positive : (neutral != null ? neutral : negative);
            ViewParent parent = anyButton != null ? anyButton.getParent() : null;
            if (parent instanceof View) {
                View buttonPanel = (View) parent;
                int sidePad = Math.round(20 * density);
                int bottomPad = Math.round(12 * density);
                buttonPanel.setPadding(sidePad, buttonPanel.getPaddingTop(), sidePad,
                        Math.max(buttonPanel.getPaddingBottom(), bottomPad));
            }
        }
    }

    /** Gives one AlertDialog button breathing room from its neighbor(s) in the button bar. No-op if the button has no margin-capable LayoutParams (shouldn't happen for stock AlertDialog buttons). */
    private static void addDialogButtonGap(View button, int gap) {
        if (button == null) return;
        ViewGroup.LayoutParams lp = button.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).setMarginStart(gap);
            button.setLayoutParams(lp);
        }
    }

    /**
     * apply()'s counterpart for dialog-styled Activities (WidgetForm5PromptActivity,
     * WidgetSaveActivity, WidgetActionActivity -- Theming.applyDialogTheme: transparent window,
     * floating card over the system dim scrim). apply() paints the Activity's WHOLE content
     * root, which for a normal full-screen Activity IS the screen -- correct there. For these,
     * the content root is a screen-covering FrameLayout that only exists to center a small card;
     * painting that root with the photo/color background fills the entire window with it,
     * covering the transparent gap and hiding the dim scrim entirely (the "custom background
     * shows outside the box" bug -- the card stopped looking like a floating prompt and started
     * looking like a second full screen). This themes ONLY the given card -- same SURFACE-toned
     * rounded panel + retint pass as applyToDialog -- and leaves the screen-covering root alone,
     * so the window stays transparent and the system dim still shows around the card.
     */
    public static void applyToCard(Activity activity, View card) {
        if (card == null || Theming.family(activity) != ThemeFamily.CUSTOM) return;
        retintTree(activity, card);

        CustomThemeMode mode = SettingsStore.getCustomThemeMode(activity);
        String photoPath = SettingsStore.getCustomPhotoPath(activity);
        if (mode == CustomThemeMode.PHOTO && photoPath != null) {
            applyCardPhotoBackground(activity, card, photoPath);
        } else {
            applyCardSolidPanel(activity, card);
        }
    }

    private static void applyCardSolidPanel(Activity activity, View card) {
        float density = activity.getResources().getDisplayMetrics().density;
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(colorForRole(activity, ColorRole.SURFACE));
        panel.setCornerRadius(22 * density); // matches card_bg_*'s corner radius
        panel.setStroke(Math.round(density), colorForRole(activity, ColorRole.LINE_STRONG));
        card.setBackground(panel);
    }

    /**
     * Photo mode's counterpart to applyCardSolidPanel: composites the same blurred, center-
     * cropped photo every other Custom Photo surface shows (behind the same neutral SURFACE
     * scrim) into the card's own rounded background, instead of leaving dialog cards -- Save/
     * Form5/Maps prompts -- as the one place still showing a flat color with no photo, blur, or
     * transparency at all.
     *
     * The card's real pixel size isn't known synchronously: a dialog card sizes to its content,
     * which hasn't necessarily been measured yet at onResume() time (that's when this gets
     * called). A neutral placeholder goes up immediately so there's no flash of the wrong
     * background, and the actual composite renders once a real layout pass has happened --
     * immediately if one already has, or via ViewTreeObserver if not.
     */
    private static void applyCardPhotoBackground(Activity activity, View card, String photoPath) {
        applyCardSolidPanel(activity, card); // neutral placeholder while waiting for real bounds

        Runnable render = () -> {
            int w = card.getWidth();
            int h = card.getHeight();
            if (w <= 0 || h <= 0) return; // still nothing usable -- keep the placeholder panel
            try {
                float density = activity.getResources().getDisplayMetrics().density;
                float radiusPx = 22 * density;

                Bitmap decoded = decodeFileDownsampled(photoPath, w, h);

                Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(out);
                Path clip = new Path();
                clip.addRoundRect(new RectF(0, 0, w, h), radiusPx, radiusPx, Path.Direction.CW);
                canvas.clipPath(clip);
                // Same recipe as every other surface: base fill -> blurred photo at opacity
                // -> scrim (drawPhotoComposite applies the saved blur level itself).
                drawPhotoComposite(canvas, activity, decoded, SettingsStore.getCustomPhotoBlur(activity),
                        SettingsStore.getCustomPhotoOpacity(activity), w, h);
                canvas.drawColor(colorForRole(activity, ColorRole.SURFACE)); // same frosted layer every other card gets

                Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                stroke.setStyle(Paint.Style.STROKE);
                float strokeWidthPx = density;
                stroke.setStrokeWidth(strokeWidthPx);
                stroke.setColor(colorForRole(activity, ColorRole.LINE_STRONG));
                float inset = strokeWidthPx / 2f;
                canvas.drawRoundRect(new RectF(inset, inset, w - inset, h - inset), radiusPx, radiusPx, stroke);

                card.setBackground(new BitmapDrawable(activity.getResources(), out));
            } catch (Exception | OutOfMemoryError e) {
                Log.w("CustomThemeBackground", "Card photo background failed, keeping neutral panel: " + e, e);
            }
        };

        if (card.getWidth() > 0 && card.getHeight() > 0) {
            render.run();
        } else {
            card.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    card.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    render.run();
                }
            });
        }
    }

    // ---- Background (color or photo) ---------------------------------------------------------

    public static void apply(Activity activity) {
        if (Theming.family(activity) != ThemeFamily.CUSTOM) return;

        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup) || ((ViewGroup) content).getChildCount() == 0) return;
        View root = ((ViewGroup) content).getChildAt(0);

        try {
            CustomThemeMode mode = SettingsStore.getCustomThemeMode(activity);
            String photoPath = SettingsStore.getCustomPhotoPath(activity);
            if (mode == CustomThemeMode.PHOTO && photoPath != null) {
                root.setBackground(buildPhotoDrawable(activity, photoPath));
            } else {
                root.setBackground(new ColorDrawable(colorForRole(activity, ColorRole.BACKGROUND)));
            }
        } catch (Exception | OutOfMemoryError e) {
            Log.w("CustomThemeBackground", "Falling back to solid color: " + e, e);
            root.setBackground(new ColorDrawable(colorForRole(activity, ColorRole.BACKGROUND)));
        }

        retintTree(activity, root);
        applyGlassSurfaces(activity, root);
    }

    /**
     * Explicitly restyles every STRUCTURAL surface in the tree -- cards, rows, input fields,
     * neutral-filled buttons -- to the current glass recipe: translucent white fill plus a
     * hairline LINE_STRONG border (the outline-chip look, applied to whole panels).
     *
     * This deliberately does NOT go through retintTree's sample-and-match recoloring. That
     * pass compares sampled drawable colors against resolved resource VALUES, and on real
     * devices it has proven unreliable for these surfaces (the recurring "settings / full
     * schedule are still solid colors" report across multiple rounds) -- widgets, which
     * style imperatively, always updated while activities silently kept raw Adaptive fills.
     * This pass restyles unconditionally instead, gated only on shape signals that separate
     * structural panels from everything else: a GradientDrawable background, no
     * backgroundTint (accent-tinted controls keep their color), a modest corner radius
     * (excludes circular status dots and progress bars), and a low-saturation fill
     * (excludes intentionally-colored fills like accent buttons). Safe to re-run --
     * the restyled values themselves pass every gate.
     */
    public static void applyGlassSurfaces(Context context, View root) {
        if (root == null || Theming.family(context) != ThemeFamily.CUSTOM) return;
        int fill = colorForRole(context, ColorRole.SURFACE);
        int stroke = colorForRole(context, ColorRole.LINE_STRONG);
        float[] hsv = new float[3];
        int strokeWidthPx = Math.max(1, Math.round(
                context.getResources().getDisplayMetrics().density));
        glassifyWalk(root, fill, stroke, strokeWidthPx, hsv);
    }

    private static void glassifyWalk(View view, int fill, int stroke,
                                     int strokeWidthPx, float[] hsv) {
        Drawable bg = view.getBackground();
        if (bg instanceof GradientDrawable && view.getBackgroundTintList() == null) {
            GradientDrawable gd = (GradientDrawable) bg;
            boolean structural = false;
            try {
                // RECTANGLE only: every status dot / wizard progress bar is an OVAL shape,
                // so this one check excludes them all from being repainted as panels.
                // Gradients report no solid color and are skipped by the null check.
                if (gd.getShape() == GradientDrawable.RECTANGLE) {
                    ColorStateList fillList = gd.getColor();
                    if (fillList != null) {
                        Color.colorToHSV(fillList.getDefaultColor(), hsv);
                        // Structural surfaces are near-neutral; anything genuinely colored
                        // (an accent fill) is an intentional control, not a panel.
                        structural = hsv[1] <= 0.25f;
                    }
                }
            } catch (Exception ignored) {
                // An exotic drawable that won't report its own geometry just isn't touched.
            }
            if (structural) {
                GradientDrawable glass = (GradientDrawable) bg.mutate();
                glass.setColor(fill);
                glass.setStroke(strokeWidthPx, stroke);
                view.setBackground(glass);
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                glassifyWalk(vg.getChildAt(i), fill, stroke, strokeWidthPx, hsv);
            }
        }
    }

    /**
     * Renders the photo into a bitmap already sized to exactly fill the screen (center-cropped,
     * matching buildWidgetBackgroundBitmap's approach) instead of handing an arbitrary-aspect-
     * ratio bitmap to a plain BitmapDrawable. BitmapDrawable has no scale type of its own -- with
     * nothing else set, it stretches non-uniformly to fill whatever bounds it's given, which is
     * exactly what was distorting the photo (squishing it to match each screen/card's aspect
     * ratio) instead of cropping it the way a background photo should behave.
     *
     * Goes through drawPhotoComposite (base fill -> blurred photo -> scrim) so the screen shows
     * EXACTLY what widgets show for the same settings -- previously this path drew the photo at
     * raw opacity over whatever happened to be behind the window and never applied any base
     * color, which is why the activity looked different from the widget and why lowering
     * opacity revealed an unrelated backdrop instead of the neutral base.
     */
    private static Drawable buildPhotoDrawable(Activity activity, String photoPath) throws Exception {
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int w = dm.widthPixels;
        int h = dm.heightPixels;

        Bitmap decoded = decodeFileDownsampled(photoPath, w, h);

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        drawPhotoComposite(canvas, activity, decoded, SettingsStore.getCustomPhotoBlur(activity),
                        SettingsStore.getCustomPhotoOpacity(activity), w, h);

        return new BitmapDrawable(activity.getResources(), out);
    }

    // ---- Photo decode ---------------------------------------------------------------------

    /**
     * Decodes the app's own locally-stored copy of the Custom theme photo. Deliberately just
     * BitmapFactory.decodeFile() -- no ContentResolver, no external URI, no ImageDecoder. That
     * whole layer of complexity existed to cope with an *external* picker's URI, which is
     * exactly the dependency the local-copy rebuild removes; decoding a file this app wrote
     * itself doesn't need any of it, and decodeFile() has been simple and reliable since
     * Android 1.0.
     */
    public static Bitmap decodeFileDownsampled(String path, int targetW, int targetH) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IllegalStateException("Could not read image dimensions from local file");
        }

        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetW && bounds.outHeight / (sample * 2) >= targetH) {
            sample *= 2;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(path, opts);
        if (bmp == null) throw new IllegalStateException("BitmapFactory.decodeFile returned null");
        return bmp;
    }

    /**
     * The ONE place this class still touches an external content:// URI -- called once, right
     * after the picker returns, to copy the chosen photo into the app's own storage. Downsamples
     * during the copy (capped at 1600px) so a multi-MB camera photo isn't stored locally at full
     * size for what's only ever rendered as a blurred background. After this call, nothing else
     * in the app ever reads the original external URI again.
     */
    public static void copyPickedPhotoToLocalStorage(Activity activity, Uri sourceUri, File destination) throws Exception {
        Bitmap decoded;
        try (InputStream boundsIn = activity.getContentResolver().openInputStream(sourceUri)) {
            if (boundsIn == null) throw new IllegalStateException("Picker returned a URI with no readable content");
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(boundsIn, null, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw new IllegalStateException("Picker returned a URI that couldn't be read as an image");
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 1600 && bounds.outHeight / (sample * 2) >= 1600) sample *= 2;

            try (InputStream decodeIn = activity.getContentResolver().openInputStream(sourceUri)) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                decoded = BitmapFactory.decodeStream(decodeIn, null, opts);
                if (decoded == null) throw new IllegalStateException("Could not decode the picked photo");
            }
        }

        try (OutputStream out = new FileOutputStream(destination)) {
            decoded.compress(Bitmap.CompressFormat.JPEG, 90, out);
        }

        int[] colors = extractDominantColors(decoded);
        SettingsStore.setCustomPhotoDerivedPrimary(activity, colors[0]);
        SettingsStore.setCustomPhotoDerivedAccent(activity, colors[1]);
    }
}
