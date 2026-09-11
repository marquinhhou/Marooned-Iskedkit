package dev.marquinhhou.crsscheduler.ui;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.core.content.res.ResourcesCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.model.ClassSession;
import dev.marquinhhou.crsscheduler.widget.WidgetRenderer;

/** Draws the week to a themed bitmap, saved to the gallery. Reuses WidgetRenderer's grid math. */
public final class ScheduleImageExporter {

    private static final int PADDING = 28;
    private static final int HEADER_BAR_HEIGHT = 60;
    private static final int HEADLINE_HEIGHT = 50;
    private static final int GRID_ROW_HEIGHT = 50;
    private static final int TIME_COL_WIDTH = 130;
    private static final int DAY_COL_WIDTH = 130;
    private static final int NOTE_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 36;

    private ScheduleImageExporter() {}

    /** Renders the full week as a table image. Returns null if there's nothing to draw. */
    public static Bitmap render(Context context, List<ClassSession> schedule) {
        if (schedule.isEmpty()) return null;

        List<Integer> breakpoints = WidgetRenderer.weekBreakpoints(schedule);
        List<Integer> activeDays = WidgetRenderer.activeWeekDays(schedule);
        List<Integer> occupiedRows = WidgetRenderer.occupiedRowIndices(schedule, breakpoints, activeDays);
        int rows = occupiedRows.size();
        int dayCols = activeDays.size();
        if (rows == 0 || dayCols == 0) return null;

        String noClassDays = WidgetRenderer.noClassDaysLabel(activeDays);
        int noteHeight = noClassDays.isEmpty() ? 0 : NOTE_HEIGHT;

        String profileLine = buildProfileLine(context);

        int colorBg = Theming.color(context, R.color.ge_bg, R.color.ne_bg, R.color.adaptive_bg);
        int colorSurface = Theming.color(context, R.color.ge_surface, R.color.ne_surface, R.color.adaptive_surface);
        int colorGridLine = Theming.color(context, R.color.ge_line, R.color.ne_line, R.color.adaptive_line);
        int colorInk = Theming.color(context, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink);
        int colorInkDim = Theming.color(context, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim);
        int colorAccent = Theming.color(context, R.color.ge_accent, R.color.ne_accent, R.color.adaptive_accent);

        boolean mono = Theming.usesMonoFont(context);
        Typeface regular = mono ? font(context, R.font.jetbrains_mono) : Typeface.DEFAULT;
        Typeface bold = mono ? font(context, R.font.jetbrains_mono_bold) : Typeface.DEFAULT_BOLD;

        int width = PADDING * 2 + TIME_COL_WIDTH + DAY_COL_WIDTH * dayCols;

        // profileLinePaint has to exist before we can measure/wrap profileLine against the
        // table's own width, so it's built here rather than down with the other paints --
        // profileLineHeight now comes from the line's REAL wrapped height instead of a fixed
        // single-line constant. Previously this was one canvas.drawText call with no width
        // constraint and no wrapping at all: switching on enough export fields (name, course,
        // year, student no, address, any of the links...) produced a profileLine longer than
        // the table, and everything past the table's own width was simply outside the bitmap
        // and never rendered -- it didn't visibly "overflow", it just vanished off the edge.
        TextPaint profileLineTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        profileLineTextPaint.setTextSize(15);
        profileLineTextPaint.setColor(colorInkDim);
        profileLineTextPaint.setTypeface(regular);
        Layout profileLineLayout = null;
        int profileLineHeight = 0;
        if (!profileLine.isEmpty()) {
            int availableWidth = width - 2 * PADDING;
            profileLineLayout = StaticLayout.Builder.obtain(profileLine, 0, profileLine.length(),
                            profileLineTextPaint, availableWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0, 1.15f)
                    .build();
            profileLineHeight = profileLineLayout.getHeight() + 14;
        }

        int height = PADDING * 2 + HEADER_BAR_HEIGHT + HEADLINE_HEIGHT + profileLineHeight
                + GRID_ROW_HEIGHT * (rows + 1) + noteHeight + FOOTER_HEIGHT;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(colorBg);

        Paint brandPaint = textPaint(20, colorInkDim, Paint.Align.LEFT, regular);
        Paint headlinePaint = textPaint(26, colorInk, Paint.Align.LEFT, bold);
        Paint dayLabelPaint = textPaint(20, colorInkDim, Paint.Align.CENTER, bold);
        Paint timeTextPaint = textPaint(16, colorInkDim, Paint.Align.CENTER, regular);
        Paint cellTextPaint = textPaint(17, colorInk, Paint.Align.CENTER, bold);
        Paint notePaint = textPaint(16, colorInkDim, Paint.Align.LEFT, regular);
        Paint footerPaint = textPaint(12, colorInkDim, Paint.Align.CENTER, regular);
        footerPaint.setAlpha(150);
        Paint gridPaint = new Paint();
        gridPaint.setColor(colorGridLine);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2f);
        Paint tablePaint = new Paint();
        tablePaint.setColor(colorSurface);
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(colorInkDim);
        dotPaint.setAlpha(90);

        double totalUnits = 0;
        for (ClassSession c : schedule) if (!c.creditsExcluded) totalUnits += c.credits;

        int y = PADDING;

        Paint brandDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        brandDotPaint.setColor(colorAccent);
        canvas.drawCircle(PADDING + 6, y + HEADER_BAR_HEIGHT / 2f - 6, 6, brandDotPaint);
        canvas.drawText("WEEKLY", PADDING + 20, y + HEADER_BAR_HEIGHT / 2f, brandPaint);
        y += HEADER_BAR_HEIGHT;

        String headline = schedule.size() + (schedule.size() == 1 ? " class" : " classes")
                + " \u00B7 " + String.format(Locale.US, "%.1f", totalUnits) + " units";
        canvas.drawText(headline, PADDING, y + HEADLINE_HEIGHT / 2f + 9, headlinePaint);
        y += HEADLINE_HEIGHT;

        if (profileLineLayout != null) {
            canvas.save();
            canvas.translate(PADDING, y + 8);
            profileLineLayout.draw(canvas);
            canvas.restore();
            y += profileLineHeight;
        }

        int tableTop = y;
        int tableBottom = y + GRID_ROW_HEIGHT * (rows + 1);
        canvas.drawRoundRect(PADDING, tableTop, PADDING + TIME_COL_WIDTH + DAY_COL_WIDTH * dayCols, tableBottom,
                24f, 24f, tablePaint);

        for (int i = 0; i < dayCols; i++) {
            int dayIdx = activeDays.get(i);
            float cx = PADDING + TIME_COL_WIDTH + i * DAY_COL_WIDTH + DAY_COL_WIDTH / 2f;
            float cy = y + GRID_ROW_HEIGHT / 2f + 7;
            canvas.drawText(WidgetRenderer.DAY_LABELS[dayIdx].substring(0, 1), cx, cy, dayLabelPaint);
        }
        y += GRID_ROW_HEIGHT;

        for (int i = 0; i < rows; i++) {
            int r = occupiedRows.get(i);
            int slotStart = breakpoints.get(r);
            int slotEnd = breakpoints.get(r + 1);
            float rowTop = y + i * GRID_ROW_HEIGHT;
            float rowMidY = rowTop + GRID_ROW_HEIGHT / 2f;

            canvas.drawLine(PADDING, rowTop, PADDING + TIME_COL_WIDTH + DAY_COL_WIDTH * dayCols, rowTop, gridPaint);
            drawFittedText(canvas, WidgetRenderer.compactRangeLabel(slotStart, slotEnd),
                    PADDING + TIME_COL_WIDTH / 2f, rowMidY + 6, TIME_COL_WIDTH - 12, timeTextPaint);

            for (int c = 0; c < dayCols; c++) {
                int dayIdx = activeDays.get(c);
                float cx = PADDING + TIME_COL_WIDTH + c * DAY_COL_WIDTH + DAY_COL_WIDTH / 2f;
                ClassSession match = WidgetRenderer.findClassInSlot(schedule, dayIdx, slotStart, slotEnd);

                if (match != null) {
                    String label = WidgetRenderer.abbreviateName(match.name);
                    drawFittedText(canvas, label, cx, rowMidY + 6, DAY_COL_WIDTH - 10, cellTextPaint);
                } else {
                    canvas.drawCircle(cx, rowMidY, 4, dotPaint);
                }
            }
        }

        if (!noClassDays.isEmpty()) {
            canvas.drawText(noClassDays, PADDING, tableBottom + NOTE_HEIGHT / 2f + 6, notePaint);
        }

        int footerY = tableBottom + noteHeight + FOOTER_HEIGHT / 2 + 5;
        canvas.drawText("Marooned IskedKit by marquinhhou on GitHub", width / 2f, footerY, footerPaint);

        return bitmap;
    }

    /** Joins whichever profile fields the person has switched on for the export, "" if none/empty. */
    private static String buildProfileLine(Context context) {
        StringBuilder sb = new StringBuilder();
        if (SettingsStore.isExportShowNameEnabled(context)) appendIfPresent(sb, SettingsStore.getProfileName(context));
        if (SettingsStore.isExportShowCourseEnabled(context)) appendIfPresent(sb, SettingsStore.getProfileCourse(context));
        if (SettingsStore.isExportShowYearStandingEnabled(context)) appendIfPresent(sb, SettingsStore.getProfileYearStanding(context));
        if (SettingsStore.isExportShowStudentNoEnabled(context)) appendIfPresent(sb, SettingsStore.getProfileStudentNo(context));
        if (SettingsStore.isExportShowAddressEnabled(context)) appendIfPresent(sb, SettingsStore.getProfileAddress(context));
        if (SettingsStore.isExportShowFacebookEnabled(context)) appendIfPresent(sb, SettingsStore.getProfileFacebook(context));
        if (SettingsStore.isExportShowInstagramEnabled(context)) appendIfPresent(sb, SettingsStore.getProfileInstagram(context));
        if (SettingsStore.isExportShowTwitterEnabled(context)) appendIfPresent(sb, SettingsStore.getProfileTwitter(context));
        if (SettingsStore.isExportShowLinkedinEnabled(context)) appendIfPresent(sb, SettingsStore.getProfileLinkedin(context));
        if (SettingsStore.isExportShowWebsiteEnabled(context)) appendIfPresent(sb, SettingsStore.getProfileWebsite(context));
        return sb.toString();
    }

    private static void appendIfPresent(StringBuilder sb, String value) {
        if (value == null) return;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return;
        if (sb.length() > 0) sb.append("  \u00B7  ");
        sb.append(trimmed);
    }

    /** Draws centered text, shrinking the font just enough to fit maxWidth if it'd otherwise overflow the cell. */
    private static void drawFittedText(Canvas canvas, String text, float cx, float baselineY, float maxWidth, Paint basePaint) {
        Paint p = new Paint(basePaint);
        float w = p.measureText(text);
        if (w > maxWidth && w > 0) {
            p.setTextSize(basePaint.getTextSize() * (maxWidth / w));
        }
        canvas.drawText(text, cx, baselineY, p);
    }

    private static Typeface font(Context context, int fontRes) {
        Typeface t = ResourcesCompat.getFont(context, fontRes);
        return t != null ? t : Typeface.MONOSPACE;
    }

    private static Paint textPaint(float textSize, int color, Paint.Align align, Typeface typeface) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(textSize);
        p.setColor(color);
        p.setTextAlign(align);
        p.setTypeface(typeface);
        return p;
    }

    /** Saves to Pictures/CRSScheduler via MediaStore (no permission needed on Android 10+). */
    public static Uri saveToGallery(Context context, Bitmap bitmap) throws IOException {
        String displayName = "CRS_Schedule_" + System.currentTimeMillis() + ".png";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CRSScheduler");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        ContentResolver resolver = context.getContentResolver();
        Uri item = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (item == null) throw new IOException("MediaStore did not return a Uri to write to.");

        try (OutputStream out = resolver.openOutputStream(item)) {
            if (out == null) throw new IOException("Could not open an output stream for the saved image.");
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("Bitmap compression failed.");
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(item, done, null, null);
        }

        return item;
    }
}
