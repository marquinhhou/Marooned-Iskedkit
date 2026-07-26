package dev.marquinhhou.crsscheduler.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/** Countdown ring as a Bitmap (RemoteViews can't render SVG): dot glyph (NE) or stroked arc (GE/Adaptive). */
public final class RingBitmapFactory {

    private static final int DOT_COUNT = 28;

    private RingBitmapFactory() {}

    public static Bitmap buildDotRing(float progress, int sizePx, int litColor) {
        float p = Math.max(0f, Math.min(1f, progress));
        int litCount = Math.round(p * DOT_COUNT);

        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        float cx = sizePx / 2f;
        float cy = sizePx / 2f;
        float r = sizePx * 0.42f;
        float dotR = sizePx * 0.033f;

        Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
        dim.setColor(Color.argb(31, 255, 255, 255));

        Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
        lit.setColor(litColor);
        lit.setShadowLayer(sizePx * 0.06f, 0, 0, litColor);

        for (int i = 0; i < DOT_COUNT; i++) {
            double a = (i / (double) DOT_COUNT) * Math.PI * 2 - Math.PI / 2;
            float x = (float) (cx + r * Math.cos(a));
            float y = (float) (cy + r * Math.sin(a));
            canvas.drawCircle(x, y, dotR, i < litCount ? lit : dim);
        }

        return bmp;
    }

    /** @param trackColor color of the unfilled track behind the progress arc. */
    public static Bitmap buildArcRing(float progress, int sizePx, int litColor, int trackColor) {
        float p = Math.max(0f, Math.min(1f, progress));

        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        float strokeWidth = sizePx * 0.075f;
        float inset = strokeWidth / 2f + sizePx * 0.02f;
        RectF bounds = new RectF(inset, inset, sizePx - inset, sizePx - inset);

        Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(strokeWidth);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(trackColor);
        canvas.drawOval(bounds, track);

        if (p > 0f) {
            Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(strokeWidth);
            arc.setStrokeCap(Paint.Cap.ROUND);
            arc.setColor(litColor);
            canvas.drawArc(bounds, -90f, 360f * p, false, arc);
        }

        return bmp;
    }
}
