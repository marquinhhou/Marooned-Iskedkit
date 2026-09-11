package dev.marquinhhou.crsscheduler.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import static android.text.StaticLayout.Builder;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.ScheduleStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore;

/**
 * v3.0.0 -- everything about YOU lives behind the circular avatar. The shareable card is a
 * full-bleed portrait tile: the photo fills the card under a soft frost that deepens toward
 * the bottom panel, where the name, identity line, and contact stats sit -- rendered once
 * by {@link #buildExportBitmap(int)} so the on-screen preview IS the exported PNG.
 */
public class ProfileCardActivity extends AppCompatActivity {

    private static final String PHOTO_FILE = "profile_photo.jpg";

    private LinearLayout cardPreviewHost;
    private LinearLayout editorHost;
    private ImageView editChevron;
    private boolean editorExpanded = false;
    /** Links and Details+Organizations each have their own grey-out budget, both using the
     * SAME pattern now: every currently-off toggle in the group visually greys (alpha only --
     * still tappable, not setEnabled(false)) the moment the cap is reached, un-greying the
     * moment something drops back off; AND tapping a toggle while already at the cap reverts
     * it and shows a warning Toast. Both lists rebuild fresh every buildEditor() call. */
    private static final int MAX_LINK_TOGGLES = 3;
    private final java.util.List<SwitchCompat> linkToggles = new ArrayList<>();
    private static final int MAX_DETAIL_TOGGLES = 5;
    private final java.util.List<SwitchCompat> detailToggles = new ArrayList<>();
    private ScrollView rootScroll;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest>
            profilePhotoPicker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        if (uri == null) return;
                        try {
                            int sizePx = Math.round(512 * getResources().getDisplayMetrics().density);
                            Bitmap decoded = decodeContentDownsampled(uri, sizePx);
                            File dst = new File(getFilesDir(), PHOTO_FILE);
                            try (java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
                                decoded.compress(Bitmap.CompressFormat.JPEG, 90, out);
                            }
                            SettingsStore.setProfilePhotoPath(this, dst.getAbsolutePath());
                            rebuildPreview();
                            Toast.makeText(this, "Photo saved.", Toast.LENGTH_SHORT).show();
                        } catch (Exception | OutOfMemoryError e) {
                            Toast.makeText(this, "Couldn't load that photo.", Toast.LENGTH_SHORT).show();
                        }
                    });

    private final androidx.activity.result.ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) renderAndSave();
                        else Toast.makeText(this,
                                "Storage permission is needed to save the image.", Toast.LENGTH_SHORT).show();
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theming.applyActivityTheme(this);

        float d = getResources().getDisplayMetrics().density;
        int inkDim = Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim);
        int ink = Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, Math.round(24 * d));
        setContentView(scroll);
        rootScroll = scroll;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Math.round(16 * d), Math.round(8 * d), Math.round(16 * d), 0);
        scroll.addView(root);

        // Header: back arrow + title, matching the other screens' headers.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(this);
        back.setImageResource(Theming.pick(this,
                R.drawable.ic_arrow_back_ge, R.drawable.ic_arrow_back_ne, R.drawable.ic_arrow_back_adaptive));
        int pad = Math.round(11 * d);
        back.setPadding(pad, pad, pad, pad);
        back.setOnClickListener(v -> finish());
        header.addView(back);
        TextView title = new TextView(this);
        title.setText("PROFILE");
        title.setTextColor(inkDim);
        title.setTextSize(12f);
        title.setLetterSpacing(0.2f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(Math.round(4 * d));
        title.setLayoutParams(titleLp);
        header.addView(title);
        root.addView(header);

        cardPreviewHost = new LinearLayout(this);
        cardPreviewHost.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewLp.topMargin = Math.round(8 * d);
        cardPreviewHost.setLayoutParams(previewLp);
        root.addView(cardPreviewHost);

        // Collapsible editor card -- restructured to match the settings dropdown pattern
        // exactly (header_maps/header_semester/etc. in ConfigureActivity): ONE outer
        // card carries the rounded background, holding the header row and body together
        // as one seamless surface. Previously the header alone carried its own card_bg
        // (plus taller 56dp/asymmetric padding), so it read as a separate floating pill
        // disconnected from the fields below it once expanded, instead of matching the
        // header-sits-inside-a-borderless-row-inside-a-bordered-card look used
        // everywhere else in the app.
        LinearLayout editCard = new LinearLayout(this);
        editCard.setOrientation(LinearLayout.VERTICAL);
        editCard.setBackgroundResource(Theming.pick(this,
                R.drawable.card_bg_ge, R.drawable.card_bg_ne, R.drawable.card_bg_adaptive));
        LinearLayout.LayoutParams editCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        editCardLp.topMargin = Math.round(14 * d);
        editCard.setLayoutParams(editCardLp);

        LinearLayout editHeader = new LinearLayout(this);
        editHeader.setOrientation(LinearLayout.HORIZONTAL);
        editHeader.setGravity(Gravity.CENTER_VERTICAL);
        editHeader.setClickable(true);
        editHeader.setFocusable(true);
        editHeader.setForeground(getDrawable(R.drawable.ripple_rounded_22dp));
        int hPad = Math.round(18 * d);
        editHeader.setPadding(hPad, hPad, hPad, hPad);
        editHeader.setMinimumHeight(Math.round(48 * d));
        TextView editLabel = new TextView(this);
        editLabel.setText("EDIT PROFILE & UNIVERSITY");
        editLabel.setTextColor(ink);
        editLabel.setTextSize(12f);
        editLabel.setLetterSpacing(0.15f);
        editLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        editLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        editHeader.addView(editLabel);
        editChevron = new ImageView(this);
        editChevron.setImageResource(Theming.pick(this,
                R.drawable.ic_chevron_down_ge, R.drawable.ic_chevron_down_ne, R.drawable.ic_chevron_down_adaptive));
        editChevron.setLayoutParams(new LinearLayout.LayoutParams(Math.round(20 * d), Math.round(20 * d)));
        editHeader.addView(editChevron);
        View.OnClickListener toggleEditor = v -> {
            ViewGroup transitionRoot = editCard.getParent() instanceof ViewGroup
                    ? (ViewGroup) editCard.getParent() : editCard;
            android.transition.TransitionSet transition = new android.transition.TransitionSet()
                    .addTransition(new android.transition.Fade(android.transition.Fade.IN | android.transition.Fade.OUT))
                    .addTransition(new android.transition.ChangeBounds())
                    .setOrdering(android.transition.TransitionSet.ORDERING_TOGETHER)
                    .setDuration(220)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f));
            android.transition.TransitionManager.beginDelayedTransition(transitionRoot, transition);
            editorExpanded = !editorExpanded;
            editorHost.setVisibility(editorExpanded ? View.VISIBLE : View.GONE);
            editChevron.setRotation(editorExpanded ? 180f : 0f);
        };
        editHeader.setOnClickListener(toggleEditor);
        editLabel.setOnClickListener(toggleEditor);
        editChevron.setClickable(false);
        editCard.addView(editHeader);

        // No top padding here -- editHeader's own bottom padding already provides the
        // gap, matching body_maps/body_semester/etc.'s paddingStart/End/Bottom-only.
        editorHost = new LinearLayout(this);
        editorHost.setOrientation(LinearLayout.VERTICAL);
        editorHost.setVisibility(View.GONE);
        editorHost.setPadding(hPad, 0, hPad, hPad);
        editCard.addView(editorHost);

        root.addView(editCard);

        buildEditor();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CustomThemeBackground.apply(this);
        rebuildPreview();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
    }

    // ---- Preview / export artwork ------------------------------------------------------------

    /** The card artwork -- drawn once, shown live, exported byte-for-byte as the PNG. */
    private Bitmap buildExportBitmap(int widthPx) {
        float d = getResources().getDisplayMetrics().density;
        int heightPx = Math.round(widthPx * 1.5f);
        float radius = 28 * d;

        Bitmap out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Path clip = new Path();
        RectF bounds = new RectF(0, 0, widthPx, heightPx);
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);

        // Layer 1 -- the portrait, center-cropped over the whole card.
        Bitmap photo = loadProfilePhotoBitmap(widthPx * 2, heightPx * 2);
        if (photo != null) {
            drawCenterCrop(canvas, photo, widthPx, heightPx);
        } else {
            canvas.drawColor(CustomThemeBackground.backgroundBaseColor(this));
            TextPaint ghost = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            ghost.setColor(0x33888888);
            ghost.setTextSize(heightPx / 6f);
            ghost.setTextAlign(Paint.Align.CENTER);
            ghost.setTypeface(android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD));
            canvas.drawText(initialsOf(SettingsStore.getProfileName(this)),
                    widthPx / 2f, heightPx / 2f - (ghost.descent() + ghost.ascent()) / 2f, ghost);
        }

        // Layer 1b -- progressive blur, faded in via a gradient alpha mask over a saveLayer.
        // This is the actual "photo fades" effect from the reference -- the darkening scrim
        // alone (below) reads as a dark overlay ON a sharp photo, which is what kept getting
        // reported back as "still no fade": color alone can't reproduce a soft focus falloff.
        // Technique: draw the fully blurred photo into an isolated layer, then punch a
        // transparent-to-opaque gradient into its alpha via DST_IN so only the bottom portion
        // of the blurred version actually shows, revealing the sharp Layer 1 underneath higher
        // up the card and blending smoothly into blur toward the bottom.
        if (photo != null) {
            // blurZoneTop..blurFullyAt is a SHORT ramp (0.15h to 0.45h) so the blur reaches
            // full strength while the scrim below is still fully transparent -- there's a
            // real "this part of the photo is now visibly blurred" band the person can
            // actually see. It used to ramp the entire way from 0.32h to heightPx, in lockstep
            // with the scrim's own 0.38h-to-heightPx darkening -- by the time the blur was
            // strong enough to notice, the scrim was already comparably dark on top of it, and
            // both saturated together near the bottom. Net effect: it read as "the bottom
            // just gets dark," not "the photo blurs," which is what got reported back as "the
            // blur is non-existent." Past blurFullyAt the mask CLAMPs to fully opaque, so the
            // blur stays maxed out for the rest of the card while the (separately timed) scrim
            // below does the actual darkening into the bottom panel.
            // Lowered further after that fix over-corrected the other way: starting the ramp
            // at 0.15h meant ANY blur was visible across 85% of the card's height (blurred
            // from 15% down to the very bottom), which read as "blurred for more than half
            // the picture." Blur now only starts past the halfway mark, so the top half of
            // the photo stays completely sharp no matter what.
            float blurZoneTop = heightPx * 0.50f;
            float blurFullyAt = heightPx * 0.68f;
            Bitmap blurSource = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
            drawCenterCrop(new Canvas(blurSource), photo, widthPx, heightPx);
            Bitmap blurred = CustomThemeBackground.applyFrostedBlur(blurSource, 16);
            int savedLayer = canvas.saveLayer(0, 0, widthPx, heightPx, null);
            canvas.drawBitmap(blurred, 0, 0, null);
            Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
            mask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
            mask.setShader(new LinearGradient(0, blurZoneTop, 0, blurFullyAt,
                    new int[]{0x00000000, 0xFFFFFFFF}, null, Shader.TileMode.CLAMP));
            // The mask must cover the FULL layer (0..heightPx), not just blurZoneTop..heightPx --
            // drawRect only applies DST_IN where it's actually drawn, so restricting it left
            // the region above blurZoneTop untouched by the mask entirely (fully opaque blur,
            // no fade) and, with the gradient's end now short of heightPx, the region below
            // blurFullyAt also needs covering so CLAMP can hold it at full opacity down to the
            // bottom edge.
            canvas.drawRect(0, 0, widthPx, heightPx, mask);
            canvas.restoreToCount(savedLayer);
        }

        // Layer 2 -- the fade. Starts right where the blur above finishes ramping in (so the
        // blur gets a clean, scrim-free band to actually be visible in -- see the comment on
        // blurZoneTop/blurFullyAt above) and ramps all the way to fully opaque black at the
        // bottom edge. Reference: the photo fades into a flat panel by the bottom of the
        // card, not just a partial darkening over it.
        Paint scrim = new Paint();
        float fadeStart = heightPx * 0.70f;
        scrim.setShader(new LinearGradient(0, fadeStart, 0, heightPx,
                new int[]{0x00000000, 0x14000000, 0x4D000000, 0x99000000, 0xE0000000, 0xFF000000},
                new float[]{0f, 0.22f, 0.46f, 0.68f, 0.86f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, fadeStart, widthPx, heightPx, scrim);

        float sidePad = widthPx * 0.055f;
        float contentBottom = heightPx - heightPx * 0.045f;

        // ---- Bottom text stack: a small layout engine --------------------------------------
        // Every block below (name, subtitle, each link row, each stat row) is measured for its
        // real height FIRST and collected in top-to-bottom order; stackBottomUp() then lays the
        // whole list out from one fixed bottom anchor with each block's own declared gap before
        // it, and draws them. Nothing here is positioned via a guessed offset from contentBottom
        // anymore -- that's what let rows overlap the subtitle, overlap each other, or clip
        // past the visible card edge (a second link row vanishing off the bottom) depending on
        // exactly how much text happened to land in each field. Adding another kind of row
        // later is just one more block appended to the list.
        List<CardTextBlock> blocks = new ArrayList<>();

        String nameText = SettingsStore.getProfileName(this);
        TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setColor(0xFFFFFFFF);
        namePaint.setTextSize(widthPx / 13f);
        namePaint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD));
        blocks.add(CardTextBlock.singleLine(nameText.isEmpty() ? "Your Name" : nameText, namePaint, 0f));

        String sub = subtitleLine();
        TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(0xE6FFFFFF);
        subPaint.setTextSize(widthPx / 26f);
        Builder subBuilder = Builder.obtain(sub, 0, sub.length(), subPaint, (int) (widthPx - 2 * sidePad))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0, 1.15f)
                .setMaxLines(2);
        Layout subLayout = subBuilder.build();
        blocks.add(CardTextBlock.multiLine(subLayout, widthPx / 45f));

        // Links (bold, with a custom vector link icon beside each -- only whichever fields
        // are both filled in AND left on via that field's own toggle) and contact stats
        // (quietest layer). Links are capped at 3 by the enable toggle itself (see
        // addFieldWithToggle's countEnabledLinks check) so links.size() is already <=3;
        // stats show all of Mail/Phone/Address that are filled in -- there are only ever 3
        // possible, and the layout engine above has no fixed-slot limit the way the old
        // manually-positioned version effectively did, so there's no reason to still
        // artificially cap either list at 2.
        List<String> links = linkRows();
        List<StatRow> stats = statRows();
        java.util.List<dev.marquinhhou.crsscheduler.model.Organization> orgs =
                SettingsStore.getProfileOrganizationsEnabled(this)
                        ? SettingsStore.getProfileOrganizations(this)
                        : java.util.Collections.emptyList();
        boolean anyRowYet = false;
        // Links previously stood out as the one BOLD, full-white row against Organizations'
        // and Stats' quieter, regular-weight treatment -- unified onto one shared style so
        // all four icon-row kinds (links, organizations, mail/phone/address/student-no) read
        // as the same family of content instead of links looking like a shouted headline.
        if (!links.isEmpty()) {
            TextPaint linkPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            linkPaint.setColor(0xE6FFFFFF);
            linkPaint.setTextSize(widthPx / 30f);
            for (String link : links) {
                float gapBefore = anyRowYet ? widthPx / 75f : widthPx / 40f;
                blocks.add(CardTextBlock.singleLineWithIcon(link, linkPaint, gapBefore,
                        ProfileCardActivity::drawLinkIcon));
                anyRowYet = true;
            }
        }
        // Organizations -- these were being collected and persisted by the editor (add/edit/
        // remove all worked) but never actually drawn anywhere on the card, so nothing the
        // person added there ever showed up. Sits between links and contact stats. Gated by
        // its own master toggle (Show organizations on card) in addition to the per-entry
        // cap of 3, so a long list doesn't take over the card.
        if (!orgs.isEmpty()) {
            TextPaint orgPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            orgPaint.setColor(0xE6FFFFFF);
            orgPaint.setTextSize(widthPx / 30f);
            int orgCount = Math.min(3, orgs.size());
            for (int i = 0; i < orgCount; i++) {
                dev.marquinhhou.crsscheduler.model.Organization org = orgs.get(i);
                String line = org.role.isEmpty() ? org.name : org.name + " \u2014 " + org.role;
                float gapBefore = anyRowYet ? widthPx / 75f : widthPx / 40f;
                blocks.add(CardTextBlock.singleLineWithIcon(line, orgPaint, gapBefore,
                        ProfileCardActivity::drawOrgIcon));
                anyRowYet = true;
            }
        }
        if (!stats.isEmpty()) {
            TextPaint statPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            // Matches links'/organizations' color now -- previously a dimmer 0xB8, which
            // made stats look like a visually separate, lower tier instead of the same
            // family of icon-rows.
            statPaint.setColor(0xE6FFFFFF);
            statPaint.setTextSize(widthPx / 30f);
            for (StatRow stat : stats) {
                float gapBefore = anyRowYet ? widthPx / 75f : widthPx / 40f;
                blocks.add(CardTextBlock.singleLineWithIcon(stat.value, statPaint, gapBefore, stat.icon));
                anyRowYet = true;
            }
        }

        stackBottomUp(canvas, blocks, sidePad, contentBottom);
        return out;
    }

    /**
     * Draws a simple two-link chain glyph as an actual vector path (two overlapping stroked
     * capsules on a diagonal) instead of the platform's emoji font -- the emoji glyph renders
     * as a distinctly two-tone, cartoon-ish blue chain that visually clashes with the card's
     * plain white text, unlike the much plainer envelope glyph used for Mail; this draws at a
     * consistent weight/color on every device instead of depending on whichever emoji font is
     * installed. left/top/size describe the square box the icon should fill.
     */
    /**
     * Two upright, side-by-side capsule rings with a slight overlap -- a simpler two-link
     * chain glyph than the previous version, which rotated the same shapes -40 degrees.
     * That diagonal design was the one icon that didn't sit upright like the others
     * (envelope, phone, house, briefcase, ID card are all drawn straight-on), and read as
     * visually "off"/wonky next to them, especially at small render sizes where the rotated
     * corners could look uneven. This keeps the same "two overlapping stroked capsules"
     * idea but upright, matching the others' orientation and simplicity.
     */
    private static void drawLinkIcon(Canvas canvas, float left, float top, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(color);
        p.setStrokeWidth(size * 0.13f);
        p.setStrokeCap(Paint.Cap.ROUND);

        float ringW = size * 0.40f;
        float ringH = size * 0.60f;
        float overlap = size * 0.10f;
        float cy = top + size / 2f;
        float leftCx = left + size / 2f - (ringW - overlap) / 2f;
        float rightCx = left + size / 2f + (ringW - overlap) / 2f;

        RectF leftRing = new RectF(leftCx - ringW / 2f, cy - ringH / 2f, leftCx + ringW / 2f, cy + ringH / 2f);
        RectF rightRing = new RectF(rightCx - ringW / 2f, cy - ringH / 2f, rightCx + ringW / 2f, cy + ringH / 2f);
        canvas.drawRoundRect(leftRing, ringW / 2f, ringW / 2f, p);
        canvas.drawRoundRect(rightRing, ringW / 2f, ringW / 2f, p);
    }

    /**
     * Envelope outline: a rounded rectangle with a V-shaped flap line -- replaces the plain
     * Unicode glyph that used to stand in for Mail. That glyph happened to render in
     * the platform's own plain text style rather than full-color emoji, which is exactly why
     * it read as more cohesive than Phone/Address next to it -- but it was still a font glyph,
     * not something this app actually drew, so its exact look (weight, proportions) was never
     * under this app's control and could still shift between devices/fonts. Drawn the same
     * way as the other three icons now, so all four are guaranteed to match.
     */
    private static void drawMailIcon(Canvas canvas, float left, float top, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(color);
        p.setStrokeWidth(size * 0.10f);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);

        float w = size * 0.86f;
        float h = size * 0.62f;
        float cx = left + size / 2f;
        float cy = top + size / 2f;
        RectF rect = new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        canvas.drawRoundRect(rect, size * 0.06f, size * 0.06f, p);

        Path flap = new Path();
        flap.moveTo(rect.left + size * 0.05f, rect.top + size * 0.04f);
        flap.lineTo(cx, cy + size * 0.05f);
        flap.lineTo(rect.right - size * 0.05f, rect.top + size * 0.04f);
        canvas.drawPath(flap, p);
    }

    /**
     * Smartphone silhouette: a rounded-rect body with a small home-button dot near the
     * bottom -- replaces the old telephone-emoji glyph, which rendered as a full-color
     * retro handset that clashed with the card's plain white text. A simple phone/mobile
     * shape reads clearly at small sizes without needing a precise old-handset silhouette.
     */
    private static void drawPhoneIcon(Canvas canvas, float left, float top, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(color);
        p.setStrokeWidth(size * 0.11f);

        float w = size * 0.52f;
        float h = size * 0.86f;
        float cx = left + size / 2f;
        float top2 = top + (size - h) / 2f;
        RectF body = new RectF(cx - w / 2f, top2, cx + w / 2f, top2 + h);
        canvas.drawRoundRect(body, size * 0.12f, size * 0.12f, p);

        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(color);
        dot.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, top2 + h - size * 0.10f, size * 0.045f, dot);
    }

    /**
     * House silhouette: a peaked roofline plus an open-topped rectangle for the walls --
     * replaces the old house-emoji glyph, which rendered as a full-color house
     * with a brown roof and red door that clashed with the card's plain white text.
     */
    private static void drawHomeIcon(Canvas canvas, float left, float top, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(color);
        p.setStrokeWidth(size * 0.11f);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);

        float w = size * 0.80f;
        float cx = left + size / 2f;
        float roofPeakY = top + size * 0.06f;
        float eaveY = top + size * 0.40f;
        float baseBottom = top + size * 0.92f;
        float halfW = w / 2f;

        Path roof = new Path();
        roof.moveTo(cx - halfW, eaveY);
        roof.lineTo(cx, roofPeakY);
        roof.lineTo(cx + halfW, eaveY);
        canvas.drawPath(roof, p);

        Path walls = new Path();
        walls.moveTo(cx - halfW, eaveY);
        walls.lineTo(cx - halfW, baseBottom);
        walls.lineTo(cx + halfW, baseBottom);
        walls.lineTo(cx + halfW, eaveY);
        canvas.drawPath(walls, p);
    }

    /**
     * Briefcase silhouette: a rounded-rect body, a small handle arc on top, and a short
     * clasp line across the middle -- used for Organizations, which used to be collected
     * and persisted by the editor but never actually drawn anywhere on the card at all.
     */
    private static void drawOrgIcon(Canvas canvas, float left, float top, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(color);
        p.setStrokeWidth(size * 0.10f);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);

        float w = size * 0.78f;
        float h = size * 0.52f;
        float cx = left + size / 2f;
        float bodyTop = top + size * 0.42f;
        RectF body = new RectF(cx - w / 2f, bodyTop, cx + w / 2f, bodyTop + h);
        canvas.drawRoundRect(body, size * 0.06f, size * 0.06f, p);

        float handleW = size * 0.32f;
        RectF handle = new RectF(cx - handleW / 2f, bodyTop - size * 0.18f, cx + handleW / 2f, bodyTop + size * 0.02f);
        canvas.drawRoundRect(handle, size * 0.06f, size * 0.06f, p);

        canvas.drawLine(cx - w * 0.14f, bodyTop + h * 0.5f, cx + w * 0.14f, bodyTop + h * 0.5f, p);
    }

    /**
     * ID-card silhouette: a rounded-rect card with a small circle (photo) in one corner and
     * two short lines (name/number placeholder) beside it -- used for Student Number, which
     * was only ever collected by the editor and never actually drawn anywhere on the card,
     * the same gap Organizations had before it got its own icon.
     */
    private static void drawIdIcon(Canvas canvas, float left, float top, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(color);
        p.setStrokeWidth(size * 0.09f);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);

        float w = size * 0.86f;
        float h = size * 0.60f;
        float cx = left + size / 2f;
        float cy = top + size / 2f;
        RectF card = new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        canvas.drawRoundRect(card, size * 0.08f, size * 0.08f, p);

        float circleR = h * 0.22f;
        float circleCx = card.left + h * 0.34f;
        float circleCy = card.top + h * 0.34f;
        canvas.drawCircle(circleCx, circleCy, circleR, p);

        float lineX1 = circleCx + circleR + size * 0.07f;
        float lineX2 = card.right - size * 0.08f;
        float lineY1 = card.top + h * 0.30f;
        float lineY2 = card.top + h * 0.54f;
        canvas.drawLine(lineX1, lineY1, lineX2, lineY1, p);
        canvas.drawLine(lineX1, lineY2, lineX1 + (lineX2 - lineX1) * 0.7f, lineY2, p);
    }

    /** Draws an icon into a left/top/size/color box, e.g. a vector glyph beside a text row. */
    private interface IconDrawer {
        void draw(Canvas canvas, float left, float top, float size, int color);
    }

    /** One row in the bottom text stack -- its own measured height, the gap that goes before it
     * (space between it and whatever block precedes it), and how to draw itself into [topY, topY+height]. */
    private static final class CardTextBlock {
        final float height;
        final float gapBefore;
        final java.util.function.BiConsumer<Canvas, Float> drawer;

        private CardTextBlock(float height, float gapBefore, java.util.function.BiConsumer<Canvas, Float> drawer) {
            this.height = height;
            this.gapBefore = gapBefore;
            this.drawer = drawer;
        }

        static CardTextBlock singleLine(String text, TextPaint paint, float gapBefore) {
            Paint.FontMetrics fm = paint.getFontMetrics();
            float height = fm.descent - fm.ascent;
            return new CardTextBlock(height, gapBefore, (canvas, topY) ->
                    canvas.drawText(text, 0, topY - fm.ascent, paint));
        }

        /** Same as singleLine, but with a vector icon drawn in its own square box before the text. */
        static CardTextBlock singleLineWithIcon(String text, TextPaint paint, float gapBefore, IconDrawer icon) {
            Paint.FontMetrics fm = paint.getFontMetrics();
            float height = fm.descent - fm.ascent;
            float iconSize = height * 0.62f;
            float iconGap = height * 0.35f;
            return new CardTextBlock(height, gapBefore, (canvas, topY) -> {
                float iconTop = topY + (height - iconSize) / 2f;
                icon.draw(canvas, 0, iconTop, iconSize, paint.getColor());
                canvas.drawText(text, iconSize + iconGap, topY - fm.ascent, paint);
            });
        }

        static CardTextBlock multiLine(Layout layout, float gapBefore) {
            return new CardTextBlock(layout.getHeight(), gapBefore, (canvas, topY) -> {
                canvas.save();
                canvas.translate(0, topY);
                layout.draw(canvas);
                canvas.restore();
            });
        }
    }

    /**
     * Lays out blocks in the top-to-bottom order they were added, bottom-anchored at
     * stackBottom: the LAST block's bottom edge sits exactly at stackBottom, and each earlier
     * block is placed directly above the one after it, separated by that later block's own
     * gapBefore. x is applied as a uniform left offset via canvas.translate before each draw
     * (drawers themselves draw at x=0). No block can ever overlap another -- every position is
     * derived from real measured heights and explicit gaps, never guessed independently.
     */
    private void stackBottomUp(Canvas canvas, List<CardTextBlock> blocks, float x, float stackBottom) {
        float cursor = stackBottom;
        for (int i = blocks.size() - 1; i >= 0; i--) {
            CardTextBlock block = blocks.get(i);
            float top = cursor - block.height;
            canvas.save();
            canvas.translate(x, 0);
            block.drawer.accept(canvas, top);
            canvas.restore();
            cursor = top - block.gapBefore;
        }
    }

    private void rebuildPreview() {
        cardPreviewHost.removeAllViews();

        float density = getResources().getDisplayMetrics().density;
        int widthPx = Math.round(getResources().getDisplayMetrics().widthPixels - 2 * 16 * density);
        Bitmap art = buildExportBitmap(widthPx);

        FrameLayout frame = new FrameLayout(this);
        ImageView artView = new ImageView(this);
        artView.setImageBitmap(art);
        artView.setAdjustViewBounds(true);
        frame.addView(artView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Download icon pinned to the card's top-right corner -- an affordance of this
        // screen, not part of the exported artwork.
        ImageView download = new ImageView(this);
        download.setImageResource(Theming.pick(this,
                R.drawable.ic_download_ge, R.drawable.ic_download_ne, R.drawable.ic_download_adaptive));
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(14 * density);
        chip.setColor(0x66000000);
        download.setBackground(chip);
        int ip = Math.round(8 * density);
        download.setPadding(ip, ip, ip, ip);
        download.setClickable(true);
        download.setFocusable(true);
        download.setForeground(getDrawable(R.drawable.ripple_rounded_10dp));
        download.setContentDescription("Save profile card as image");
        download.setOnClickListener(v -> saveToGallery());
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(
                Math.round(36 * density), Math.round(36 * density), Gravity.TOP | Gravity.END);
        int m = Math.round(12 * density);
        dl.setMargins(0, m, m, 0);
        frame.addView(download, dl);

        cardPreviewHost.addView(frame);
    }

    private void drawCenterCrop(Canvas canvas, Bitmap bmp, int w, int h) {
        float scale = Math.max((float) w / bmp.getWidth(), (float) h / bmp.getHeight());
        float sw = bmp.getWidth() * scale;
        float sh = bmp.getHeight() * scale;
        float left = (w - sw) / 2f;
        float top = (h - sh) / 2f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bmp, null, new RectF(left, top, left + sw, top + sh), paint);
    }

    /** Center-cropped portrait sized for the full-bleed card, biased toward the subject's face. */
    private Bitmap loadProfilePhotoBitmap(int targetW, int targetH) {
        String path = SettingsStore.getProfilePhotoPath(this);
        if (path == null) return null;
        try {
            Bitmap decoded = CustomThemeBackground.decodeFileDownsampled(path, targetW, targetH);
            float targetRatio = (float) targetW / targetH;
            int cropW = decoded.getWidth();
            int cropH = Math.round(cropW / targetRatio);
            if (cropH > decoded.getHeight()) {
                cropH = decoded.getHeight();
                cropW = Math.round(cropH * targetRatio);
            }
            int left = (decoded.getWidth() - cropW) / 2;
            int top = Math.max(0, (int) ((decoded.getHeight() - cropH) * 0.2f)); // bias upward
            Bitmap cropped = Bitmap.createBitmap(decoded, left, top, cropW, cropH);
            return Bitmap.createScaledBitmap(cropped, targetW, targetH, true);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    // ---- Editor ------------------------------------------------------------------------------

    private void buildEditor() {
        float d = getResources().getDisplayMetrics().density;
        int inkDim = Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim);
        int ink = Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink);
        // Fresh every rebuild (buildEditor can run again via rebuildEditorInPlace) -- stale
        // switch references here would both leak views and throw off the grey-out count.
        detailToggles.clear();
        linkToggles.clear();


        // University switcher -- post-setup home for changing affiliation/campus.
        editorHost.addView(makeHint("UNIVERSITY", inkDim, d, true));
        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams typeRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        typeRowLp.topMargin = Math.round(8 * d);
        typeRow.setLayoutParams(typeRowLp);
        SettingsStore.UniversityType currentType = SettingsStore.getUniversityType(this);
        TextView upChip = new TextView(this);
        upChip.setText("UP STUDENT");
        styleToggleChip(upChip, currentType == SettingsStore.UniversityType.UP);
        upChip.setOnClickListener(v -> confirmUniversityChange(SettingsStore.UniversityType.UP));
        LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        upLp.setMarginEnd(Math.round(6 * d));
        upChip.setLayoutParams(upLp);
        typeRow.addView(upChip);
        TextView otherChip = new TextView(this);
        otherChip.setText("OTHER SCHOOL");
        styleToggleChip(otherChip, currentType == SettingsStore.UniversityType.OTHER);
        otherChip.setOnClickListener(v -> confirmUniversityChange(SettingsStore.UniversityType.OTHER));
        LinearLayout.LayoutParams otherLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        otherChip.setLayoutParams(otherLp);
        typeRow.addView(otherChip);
        editorHost.addView(typeRow);

        if (currentType == SettingsStore.UniversityType.UP) {
            TextView campusBtn = new TextView(this);
            campusBtn.setText(SettingsStore.getUpCampus(this).displayName + "  \u00b7  TAP TO CHANGE CAMPUS");
            campusBtn.setTextColor(ink); // follows light/dark like every other label
            campusBtn.setTextSize(11f);
            campusBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            campusBtn.setAllCaps(true);
            campusBtn.setGravity(Gravity.CENTER);
            campusBtn.setBackgroundResource(Theming.pick(this,
                    R.drawable.row_bg_ge, R.drawable.row_bg_ne, R.drawable.row_bg_adaptive));
            campusBtn.setMinimumHeight(Math.round(44 * d));
            LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cbLp.topMargin = Math.round(8 * d);
            campusBtn.setLayoutParams(cbLp);
            campusBtn.setOnClickListener(v -> showCampusPickerDialog());
            editorHost.addView(campusBtn);
        } else if (currentType == SettingsStore.UniversityType.OTHER) {
            addField("Your university / college", SettingsStore.getManualUniversityName(this),
                    v -> SettingsStore.setManualUniversityName(this, v));
        }

        // Personal details.
        editorHost.addView(makeHint("DETAILS", inkDim, d, true));
        CustomThemeBackground.styleControl(this,
                makeButton(editorHost, "CHOOSE PROFILE PHOTO",
                        v -> profilePhotoPicker.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                                .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build())),
                CustomThemeBackground.ControlTier.PRIMARY);
        addField("Full name", SettingsStore.getProfileName(this), v -> SettingsStore.setProfileName(this, v));
        addFieldWithGroupToggle("Email (UP Mail for UP students)", SettingsStore.getProfileMail(this), SettingsStore.getProfileMailEnabled(this),
                v -> SettingsStore.setProfileMail(this, v),
                (ctx, checked) -> SettingsStore.setProfileMailEnabled(ctx, checked));
        addFieldWithGroupToggle("Phone", SettingsStore.getProfilePhone(this), SettingsStore.getProfilePhoneEnabled(this),
                v -> SettingsStore.setProfilePhone(this, v),
                (ctx, checked) -> SettingsStore.setProfilePhoneEnabled(ctx, checked));
        addFieldWithGroupToggle("Dorm / address", SettingsStore.getProfileAddress(this), SettingsStore.getProfileAddressEnabled(this),
                v -> SettingsStore.setProfileAddress(this, v),
                (ctx, checked) -> SettingsStore.setProfileAddressEnabled(ctx, checked));
        addFieldWithGroupToggle("Student number", SettingsStore.getProfileStudentNo(this), SettingsStore.getProfileStudentNoEnabled(this),
                v -> SettingsStore.setProfileStudentNo(this, v),
                (ctx, checked) -> SettingsStore.setProfileStudentNoEnabled(ctx, checked));
        addFieldWithGroupToggle("Degree program", SettingsStore.getProfileCourse(this), SettingsStore.getProfileCourseEnabled(this),
                v -> SettingsStore.setProfileCourse(this, v),
                (ctx, checked) -> SettingsStore.setProfileCourseEnabled(ctx, checked));
        addFieldWithGroupToggle("Year & standing", SettingsStore.getProfileYearStanding(this), SettingsStore.getProfileYearStandingEnabled(this),
                v -> SettingsStore.setProfileYearStanding(this, v),
                (ctx, checked) -> SettingsStore.setProfileYearStandingEnabled(ctx, checked));

        editorHost.addView(makeHint("LINKS & MORE", inkDim, d, true));
        editorHost.addView(makeHint("Choose up to 3 links to show on your card.", inkDim, d, false));
        addFieldWithToggle("Facebook", SettingsStore.getProfileFacebook(this), SettingsStore.getProfileFacebookEnabled(this),
                v -> SettingsStore.setProfileFacebook(this, v),
                (ctx, checked) -> SettingsStore.setProfileFacebookEnabled(ctx, checked));
        addFieldWithToggle("Instagram", SettingsStore.getProfileInstagram(this), SettingsStore.getProfileInstagramEnabled(this),
                v -> SettingsStore.setProfileInstagram(this, v),
                (ctx, checked) -> SettingsStore.setProfileInstagramEnabled(ctx, checked));
        addFieldWithToggle("Twitter / X", SettingsStore.getProfileTwitter(this), SettingsStore.getProfileTwitterEnabled(this),
                v -> SettingsStore.setProfileTwitter(this, v),
                (ctx, checked) -> SettingsStore.setProfileTwitterEnabled(ctx, checked));
        addFieldWithToggle("LinkedIn", SettingsStore.getProfileLinkedin(this), SettingsStore.getProfileLinkedinEnabled(this),
                v -> SettingsStore.setProfileLinkedin(this, v),
                (ctx, checked) -> SettingsStore.setProfileLinkedinEnabled(ctx, checked));
        addFieldWithToggle("Website / portfolio", SettingsStore.getProfileWebsite(this), SettingsStore.getProfileWebsiteEnabled(this),
                v -> SettingsStore.setProfileWebsite(this, v),
                (ctx, checked) -> SettingsStore.setProfileWebsiteEnabled(ctx, checked));
        // Sets the correct initial grey-out state from whatever's already persisted -- same
        // reasoning as refreshDetailToggleGreyOut below.
        refreshLinkToggleGreyOut();

        buildOrganizationsSection(inkDim, d);
        // Sets the correct initial grey-out state from whatever's already persisted --
        // without this, a person who'd already enabled 5+ of these in a prior session
        // wouldn't see the remaining toggles greyed out until they changed one first.
        refreshDetailToggleGreyOut();

        // Every field above already persists on every keystroke (see addField's
        // TextWatcher) -- this button's job isn't to write anything new. It gives
        // editing a clear endpoint: refresh the card preview above (which otherwise
        // only re-renders in onResume, so typed changes wouldn't show until you left
        // and came back), collapse the editor, and confirm the save.
        CustomThemeBackground.styleControl(this,
                makeButton(editorHost, "SAVE PROFILE", v -> {
                    rebuildPreview();
                    editorExpanded = false;
                    editorHost.setVisibility(View.GONE);
                    if (editChevron != null) editChevron.setRotation(0f);
                    // With ~13 fields above it, SAVE is usually tapped while scrolled well
                    // past the card preview -- collapsing the editor in place left the person
                    // looking at whatever now-empty space was left behind, with the actually-
                    // updated card scrolled out of view above. Scrolling back up is what makes
                    // the save visibly DO something instead of just showing a toast.
                    if (rootScroll != null) rootScroll.post(() -> rootScroll.smoothScrollTo(0, 0));
                    Toast.makeText(this, "Profile saved.", Toast.LENGTH_SHORT).show();
                }),
                CustomThemeBackground.ControlTier.PRIMARY);
    }

    private void styleToggleChip(TextView chip, boolean selected) {
        float d = getResources().getDisplayMetrics().density;
        chip.setGravity(Gravity.CENTER);
        chip.setAllCaps(true);
        chip.setTextSize(10.5f);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setMinimumHeight(Math.round(44 * d));
        chip.setPadding(Math.round(8 * d), Math.round(8 * d), Math.round(8 * d), Math.round(8 * d));
        // This was the "doesn't function as a button" bug: no background, no ripple, no text
        // color, and no selected-state at all, unlike every other chip in the app (see
        // ConfigureActivity.bindThemeChip). Filled = accent fill for the current selection,
        // outline = everything else, same as the theme-family chips.
        int filledRes = Theming.pick(this,
                R.drawable.chip_filled_bg_ge, R.drawable.chip_filled_bg_ne, R.drawable.chip_filled_bg_adaptive);
        int outlineRes = Theming.pick(this,
                R.drawable.chip_outline_bg_ge, R.drawable.chip_outline_bg_ne, R.drawable.chip_outline_bg_adaptive);
        chip.setBackgroundResource(selected ? filledRes : outlineRes);
        int accent = Theming.color(this, R.color.ge_accent, R.color.ne_accent, R.color.adaptive_accent);
        int inkDim = Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim);
        int labelOnAccent = androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.55
                ? 0xFF000000 : 0xFFFFFFFF;
        chip.setTextColor(selected ? labelOnAccent : inkDim);
        CustomThemeBackground.tintChip(this, chip, selected);
    }

    private void confirmUniversityChange(SettingsStore.UniversityType tapped) {
        SettingsStore.UniversityType current = SettingsStore.getUniversityType(this);
        if (current == tapped) return;
        Runnable apply = () -> {
            SettingsStore.setUniversityType(this, tapped);
            rebuildEditorInPlace();
        };
        if (!ScheduleStore.load(this).isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Change university?")
                    .setMessage("Your schedules, notes, and settings will be preserved."
                            + " Continue?")
                    .setPositiveButton("CONTINUE", (dlg, w) -> apply.run())
                    .setNegativeButton("CANCEL", null)
                    .show();
        } else {
            apply.run();
        }
    }

    private void showCampusPickerDialog() {
        SettingsStore.UpCampus[] campuses = SettingsStore.UpCampus.values();
        String[] labels = new String[campuses.length];
        for (int i = 0; i < campuses.length; i++) {
            labels[i] = campuses[i].displayName + "  (" + campuses[i].location + ")";
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Which campus?")
                .setSingleChoiceItems(labels, SettingsStore.getUpCampus(this).ordinal(), (dialog, which) -> {
                    dialog.dismiss();
                    SettingsStore.setUpCampus(this, campuses[which]);
                    rebuildEditorInPlace();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void rebuildEditorInPlace() {
        editorHost.removeAllViews();
        buildEditor();
        // Every field/button/switch buildEditor() just (re)created is still wearing its
        // default row_bg_* drawable -- the initial buildEditor() call in onCreate gets
        // retinted afterward by onResume()'s CustomThemeBackground.apply(this), but THIS
        // rebuild (triggered by switching university type/campus) has no equivalent
        // follow-up, so the fields came back permanently solid/opaque instead of the
        // glass look Custom theme uses everywhere else. Retint just this subtree, same as
        // the fix for onResume's own initial pass.
        CustomThemeBackground.applySubtree(this, editorHost);
        editorHost.setVisibility(editorExpanded ? View.VISIBLE : View.GONE);
        if (editChevron != null) editChevron.setRotation(editorExpanded ? 180f : 0f);
    }

    /** Bare label+switch row (no textbox) -- currently only used for the Organizations
     * master toggle, so it participates in the same Details+Organizations grey-out group. */
    private void addSwitch(String label, boolean checked, java.util.function.BiConsumer<Context, Boolean> setter) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(Theming.pick(this,
                R.drawable.row_bg_ge, R.drawable.row_bg_ne, R.drawable.row_bg_adaptive));
        row.setPadding(Math.round(12 * d), Math.round(10 * d), Math.round(12 * d), Math.round(10 * d));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Math.round(8 * d);
        row.setLayoutParams(rowLp);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink));
        tv.setTextSize(13f);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tv);
        SwitchCompat sw = new SwitchCompat(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, checked1) -> {
            // Same reactive-revert-plus-Toast enforcement as the Details fields below --
            // see addFieldWithGroupToggle for why this needs to run here too, not just the
            // grey-out. ">" not ">=": by the time this listener runs, b.isChecked() (and so
            // countEnabledDetailToggles(), which reads every switch's live isChecked()) has
            // ALREADY updated to include THIS switch's new checked=true state -- Android
            // updates a CompoundButton's internal checked state before invoking its listener.
            // So going from 4 enabled to 5 makes the count read 5, not 4 -- using ">=" here
            // rejected the very toggle that would reach the cap, capping the real effective
            // limit at MAX_DETAIL_TOGGLES-1 instead of MAX_DETAIL_TOGGLES. Only reject once
            // the count would exceed the cap, not merely reach it.
            if (checked1 && countEnabledDetailToggles() > MAX_DETAIL_TOGGLES) {
                b.setChecked(false);
                Toast.makeText(this, "You can only show up to " + MAX_DETAIL_TOGGLES + " of these on your card.", Toast.LENGTH_SHORT).show();
                return;
            }
            setter.accept(this, checked1);
            refreshDetailToggleGreyOut();
        });
        row.addView(sw);
        detailToggles.add(sw);
        editorHost.addView(row);
    }

    private TextView makeHint(String text, int color, float d, boolean bold) {
        TextView hint = new TextView(this);
        hint.setText(text);
        hint.setTextColor(color);
        hint.setTextSize(bold ? 12f : 11f);
        if (bold) {
            hint.setTypeface(null, android.graphics.Typeface.BOLD);
            hint.setLetterSpacing(0.15f);
        }
        hint.setPadding(0, Math.round(12 * d), 0, 0);
        return hint;
    }

    private void addField(String hint, String initial, java.util.function.Consumer<String> setter) {
        float d = getResources().getDisplayMetrics().density;
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(initial);
        input.setTextColor(Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink));
        input.setHintTextColor(Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim));
        input.setTextSize(13f);
        input.setBackgroundResource(Theming.pick(this,
                R.drawable.row_bg_ge, R.drawable.row_bg_ne, R.drawable.row_bg_adaptive));
        int pad = Math.round(12 * d);
        input.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(8 * d);
        input.setLayoutParams(lp);
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                setter.accept(s.toString());
            }
        });
        editorHost.addView(input);
    }

    /**
     * Same field as addField, but with its own show-on-card toggle beside it (rather than one
     * master switch for every link field) -- so e.g. Instagram can be on while Facebook is off.
     */
    private void addFieldWithToggle(String hint, String initialText, boolean initialToggle,
            java.util.function.Consumer<String> textSetter,
            java.util.function.BiConsumer<Context, Boolean> toggleSetter) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Math.round(8 * d);
        row.setLayoutParams(rowLp);

        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(initialText);
        input.setTextColor(Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink));
        input.setHintTextColor(Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim));
        input.setTextSize(13f);
        input.setBackgroundResource(Theming.pick(this,
                R.drawable.row_bg_ge, R.drawable.row_bg_ne, R.drawable.row_bg_adaptive));
        int pad = Math.round(12 * d);
        input.setPadding(pad, pad, pad, pad);
        input.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                textSetter.accept(s.toString());
            }
        });
        row.addView(input);

        SwitchCompat sw = new SwitchCompat(this);
        sw.setChecked(initialToggle);
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.setMarginStart(Math.round(10 * d));
        sw.setLayoutParams(swLp);
        sw.setOnCheckedChangeListener((btn, checked) -> {
            // Turning a 4th link on gets reverted immediately -- setChecked(false) here
            // re-fires this same listener with checked=false, which persists the reverted
            // state through the normal toggleSetter path below, so this return skips
            // double-processing rather than skipping the persist entirely.
            // ">=" is correct here (NOT ">" -- unlike the Details/Organizations group
            // below): countEnabledLinks() reads persisted SettingsStore values, and this
            // check runs BEFORE toggleSetter.accept() below writes this switch's own new
            // state -- so at check-time the count does NOT yet include this switch's
            // pending change. Going from 2 enabled to a 3rd (reaching cap) reads count=2,
            // correctly allowed by ">="; going from 3 (already at cap) to a 4th reads
            // count=3, correctly rejected by ">=". Using ">" here would have let a 4th
            // link through, since 3 > 3 is false.
            if (checked && countEnabledLinks() >= MAX_LINK_TOGGLES) {
                btn.setChecked(false);
                Toast.makeText(this, "You can only show up to " + MAX_LINK_TOGGLES + " links on your card.", Toast.LENGTH_SHORT).show();
                return;
            }
            toggleSetter.accept(this, checked);
            refreshLinkToggleGreyOut();
        });
        row.addView(sw);
        linkToggles.add(sw);

        editorHost.addView(row);
    }

    /** How many of Facebook/Instagram/Twitter/LinkedIn/Website are currently toggled on -- capped at 3, see addFieldWithToggle. */
    private int countEnabledLinks() {
        int count = 0;
        if (SettingsStore.getProfileFacebookEnabled(this)) count++;
        if (SettingsStore.getProfileInstagramEnabled(this)) count++;
        if (SettingsStore.getProfileTwitterEnabled(this)) count++;
        if (SettingsStore.getProfileLinkedinEnabled(this)) count++;
        if (SettingsStore.getProfileWebsiteEnabled(this)) count++;
        return count;
    }

    /** Visual counterpart to the reactive revert+Toast above: greys (alpha only, still
     * tappable so the revert logic still runs on tap) every currently-off link toggle once
     * MAX_LINK_TOGGLES are on, un-greying them the moment something drops back off. */
    private void refreshLinkToggleGreyOut() {
        boolean atCap = countEnabledLinks() >= MAX_LINK_TOGGLES;
        for (SwitchCompat sw : linkToggles) {
            sw.setAlpha(atCap && !sw.isChecked() ? 0.4f : 1f);
        }
    }

    /**
     * Same shape as addFieldWithToggle (links), and now the same enforcement too: reactively
     * reverts a tap that would exceed MAX_DETAIL_TOGGLES with a warning Toast, AND greys out
     * every currently-off toggle the moment the cap is reached via refreshDetailToggleGreyOut.
     */
    private void addFieldWithGroupToggle(String hint, String initialText, boolean initialToggle,
            java.util.function.Consumer<String> textSetter,
            java.util.function.BiConsumer<Context, Boolean> toggleSetter) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Math.round(8 * d);
        row.setLayoutParams(rowLp);

        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(initialText);
        input.setTextColor(Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink));
        input.setHintTextColor(Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim));
        input.setTextSize(13f);
        input.setBackgroundResource(Theming.pick(this,
                R.drawable.row_bg_ge, R.drawable.row_bg_ne, R.drawable.row_bg_adaptive));
        int pad = Math.round(12 * d);
        input.setPadding(pad, pad, pad, pad);
        input.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                textSetter.accept(s.toString());
            }
        });
        row.addView(input);

        SwitchCompat sw = new SwitchCompat(this);
        sw.setChecked(initialToggle);
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.setMarginStart(Math.round(10 * d));
        sw.setLayoutParams(swLp);
        sw.setOnCheckedChangeListener((btn, checked) -> {
            // Same reactive-revert-plus-Toast shape as links: setChecked(false) here re-fires
            // this listener with checked=false, which persists the reverted state through
            // the normal toggleSetter path below (skipping it via return, not double-running
            // it) -- greying alone (below) only signals the limit visually; without this,
            // the switch was still fully checkable and would just silently accept a 6th
            // field with no feedback at all once it was still enabled (as it briefly was,
            // before greying switched from setEnabled(false) to alpha-only so this reactive
            // check has something to run against).
            // ">" not ">=": btn.isChecked() -- and so countEnabledDetailToggles(), which
            // reads every switch's live isChecked() -- already reflects THIS switch's new
            // checked=true by the time this listener runs (Android updates a
            // CompoundButton's internal checked state before invoking its listener). Going
            // from 4 enabled to a 5th (reaching the cap) already reads count=5, so ">="
            // rejected the very toggle that would reach the cap, capping the real limit at
            // MAX_DETAIL_TOGGLES-1 instead of MAX_DETAIL_TOGGLES.
            if (checked && countEnabledDetailToggles() > MAX_DETAIL_TOGGLES) {
                btn.setChecked(false);
                Toast.makeText(this, "You can only show up to " + MAX_DETAIL_TOGGLES + " of these on your card.", Toast.LENGTH_SHORT).show();
                return;
            }
            toggleSetter.accept(this, checked);
            refreshDetailToggleGreyOut();
        });
        row.addView(sw);
        detailToggles.add(sw);

        editorHost.addView(row);
    }

    /** How many of the Details+Organizations group are currently on -- capped at MAX_DETAIL_TOGGLES. */
    private int countEnabledDetailToggles() {
        int count = 0;
        for (SwitchCompat sw : detailToggles) if (sw.isChecked()) count++;
        return count;
    }

    /** Visual counterpart to the reactive revert+Toast above: greys (alpha only, still
     * tappable so the revert logic still runs on tap) every currently-off toggle in this
     * group once MAX_DETAIL_TOGGLES are on, un-greying them the moment something drops off. */
    private void refreshDetailToggleGreyOut() {
        boolean atCap = countEnabledDetailToggles() >= MAX_DETAIL_TOGGLES;
        for (SwitchCompat sw : detailToggles) {
            sw.setAlpha(atCap && !sw.isChecked() ? 0.4f : 1f);
        }
    }

    /**
     * ORGANIZATIONS: a repeatable list -- one org/role entry per row, an inline "REMOVE" per
     * entry, and an "+ ADD ORGANIZATION" button that appends another blank one every time
     * it's tapped. Each entry persists to SettingsStore.setProfileOrganizations on every
     * keystroke, same as every other field in this editor; add/remove re-render the whole
     * section in place since the list itself (not just one field's text) changed.
     */
    private void buildOrganizationsSection(int inkDim, float d) {
        editorHost.addView(makeHint("ORGANIZATIONS", inkDim, d, true));
        addSwitch("Show organizations on card", SettingsStore.getProfileOrganizationsEnabled(this),
                (ctx, checked) -> SettingsStore.setProfileOrganizationsEnabled(ctx, checked));
        java.util.List<dev.marquinhhou.crsscheduler.model.Organization> orgs = SettingsStore.getProfileOrganizations(this);
        for (int i = 0; i < orgs.size(); i++) {
            addOrganizationRow(orgs, i, d);
        }
        CustomThemeBackground.styleControl(this,
                makeButton(editorHost, "+ ADD ORGANIZATION", v -> {
                    java.util.List<dev.marquinhhou.crsscheduler.model.Organization> current =
                            new ArrayList<>(SettingsStore.getProfileOrganizations(this));
                    current.add(new dev.marquinhhou.crsscheduler.model.Organization("", ""));
                    SettingsStore.setProfileOrganizations(this, current);
                    rebuildEditorInPlace();
                }),
                CustomThemeBackground.ControlTier.SECONDARY);
    }

    private void addOrganizationRow(java.util.List<dev.marquinhhou.crsscheduler.model.Organization> orgs, int index, float d) {
        dev.marquinhhou.crsscheduler.model.Organization org = orgs.get(index);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(Theming.pick(this,
                R.drawable.row_bg_ge, R.drawable.row_bg_ne, R.drawable.row_bg_adaptive));
        int pad = Math.round(12 * d);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = Math.round(8 * d);
        card.setLayoutParams(cardLp);

        // Name + remove sit side by side -- remove used to be a bare TextView added directly
        // to this (VERTICAL) LinearLayout with no LayoutParams of its own, which meant it
        // inherited LinearLayout's own default for a vertical parent: MATCH_PARENT width, not
        // WRAP_CONTENT. Styled DESTRUCTIVE on top of that, it rendered as a full-width red bar
        // with "REMOVE" jammed in the corner instead of a small button -- the actual source of
        // "add organization is ugly". A horizontal row with the name field taking the
        // remaining space and a small fixed-size "X" button is the standard list-item-with-
        // delete pattern and keeps remove from dominating the card.
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText nameInput = new EditText(this);
        nameInput.setHint("Organization");
        nameInput.setText(org.name);
        nameInput.setTextColor(Theming.color(this, R.color.ge_ink, R.color.ne_ink, R.color.adaptive_ink));
        nameInput.setHintTextColor(Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim));
        nameInput.setTextSize(14f);
        nameInput.setTypeface(null, android.graphics.Typeface.BOLD);
        nameInput.setBackground(null);
        nameInput.setPadding(0, 0, 0, 0);
        nameInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nameInput.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                updateOrganizationField(index, s.toString(), null);
            }
        });
        topRow.addView(nameInput);

        TextView remove = new TextView(this);
        remove.setText("\u2715");
        remove.setTextSize(11f);
        remove.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        removeLp.setMarginStart(Math.round(10 * d));
        remove.setLayoutParams(removeLp);
        remove.setOnClickListener(v -> {
            java.util.List<dev.marquinhhou.crsscheduler.model.Organization> current =
                    new ArrayList<>(SettingsStore.getProfileOrganizations(this));
            if (index >= 0 && index < current.size()) current.remove(index);
            SettingsStore.setProfileOrganizations(this, current);
            rebuildEditorInPlace();
        });
        topRow.addView(remove);
        CustomThemeBackground.styleControl(this, remove, CustomThemeBackground.ControlTier.DESTRUCTIVE);
        // styleControl sizes every control for a comfortable 48dp touch target, right for a
        // primary action but oversized for this single-glyph "remove one organization"
        // affordance sitting inline with the name field -- shrink it back down afterward
        // while keeping the red DESTRUCTIVE tint/border it just applied.
        int removePad = Math.round(8 * d);
        remove.setPadding(removePad, removePad, removePad, removePad);
        remove.setMinimumWidth(Math.round(30 * d));
        remove.setMinimumHeight(Math.round(30 * d));

        card.addView(topRow);

        // A clear line between the org name and its role -- these used to just be two
        // EditTexts stacked with a small top-padding gap and no visual separation at all,
        // which read as one ambiguous block of text rather than two distinct fields.
        View divider = new View(this);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(1 * d));
        dividerLp.topMargin = Math.round(8 * d);
        dividerLp.bottomMargin = Math.round(6 * d);
        divider.setLayoutParams(dividerLp);
        divider.setBackgroundColor(Theming.color(this, R.color.ge_line, R.color.ne_line, R.color.adaptive_line));
        card.addView(divider);

        EditText roleInput = new EditText(this);
        roleInput.setHint("Role / position (optional)");
        roleInput.setText(org.role);
        roleInput.setTextColor(Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim));
        roleInput.setHintTextColor(Theming.color(this, R.color.ge_ink_dim, R.color.ne_ink_dim, R.color.adaptive_ink_dim));
        roleInput.setTextSize(12f);
        roleInput.setBackground(null);
        roleInput.setPadding(0, Math.round(6 * d), 0, 0);
        roleInput.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        roleInput.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                updateOrganizationField(index, null, s.toString());
            }
        });
        card.addView(roleInput);

        editorHost.addView(card);
    }

    /** Writes one field of one Organization entry back to the persisted list; null leaves that field unchanged. */
    private void updateOrganizationField(int index, String name, String role) {
        java.util.List<dev.marquinhhou.crsscheduler.model.Organization> current =
                new ArrayList<>(SettingsStore.getProfileOrganizations(this));
        if (index < 0 || index >= current.size()) return;
        dev.marquinhhou.crsscheduler.model.Organization existing = current.get(index);
        current.set(index, new dev.marquinhhou.crsscheduler.model.Organization(
                name != null ? name : existing.name,
                role != null ? role : existing.role));
        SettingsStore.setProfileOrganizations(this, current);
    }

    /** Decodes a picked image from its content:// URI, downsampled toward {@code target} on the long edge. */
    private Bitmap decodeContentDownsampled(Uri uri, int target) throws Exception {
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            android.graphics.BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IllegalStateException("Not an image");
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2;
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            bmp = android.graphics.BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new IllegalStateException("Couldn't decode image");
        return bmp;
    }

    // ---- Export ------------------------------------------------------------------------------

    private void saveToGallery() {
        // Pre-Q needs WRITE_EXTERNAL_STORAGE at runtime; 10+ scoped storage needs nothing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                || androidx.core.content.ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            renderAndSave();
        } else {
            storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    private void renderAndSave() {
        float density = getResources().getDisplayMetrics().density;
        Bitmap bmp = buildExportBitmap(Math.round(720 * density));
        ioExecutor.execute(() -> {
            String message;
            try {
                ScheduleImageExporter.saveToGallery(this, bmp);
                message = "Saved to Gallery.";
            } catch (Exception e) {
                message = "Couldn't save image: " + e.getMessage();
            }
            String finalMessage = message;
            runOnUiThread(() -> Toast.makeText(this, finalMessage, Toast.LENGTH_LONG).show());
        });
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private String schoolLine() {
        switch (SettingsStore.getUniversityType(this)) {
            case UP: {
                SettingsStore.UpCampus campus = SettingsStore.getUpCampus(this);
                return campus.displayName + " \u00b7 " + campus.location;
            }
            case OTHER: {
                String name = SettingsStore.getManualUniversityName(this);
                return name.isEmpty() ? "Your University" : name;
            }
            default:
                return "";
        }
    }

    /** "BS Statistics · Year & standing · School" -- whichever parts exist. */
    private String subtitleLine() {
        List<String> parts = new ArrayList<>();
        if (SettingsStore.getProfileCourseEnabled(this) && !TextUtils.isEmpty(SettingsStore.getProfileCourse(this))) {
            parts.add(SettingsStore.getProfileCourse(this));
        }
        if (SettingsStore.getProfileYearStandingEnabled(this) && !TextUtils.isEmpty(SettingsStore.getProfileYearStanding(this))) {
            parts.add(SettingsStore.getProfileYearStanding(this));
        }
        String school = schoolLine();
        if (!school.isEmpty()) parts.add(school);
        if (parts.isEmpty()) return "Tap EDIT PROFILE & UNIVERSITY to introduce yourself.";
        return TextUtils.join(" \u00b7 ", parts);
    }

    private List<StatRow> statRows() {
        List<StatRow> rows = new ArrayList<>();
        // Student number was only ever collected by the editor -- never actually drawn
        // anywhere on the card, the exact same gap Organizations had. Added here as its own
        // stat row now that it has a toggle controlling it.
        if (SettingsStore.getProfileStudentNoEnabled(this)) {
            addRow(rows, ProfileCardActivity::drawIdIcon, SettingsStore.getProfileStudentNo(this));
        }
        if (SettingsStore.getProfileMailEnabled(this)) {
            addRow(rows, ProfileCardActivity::drawMailIcon, SettingsStore.getProfileMail(this));
        }
        if (SettingsStore.getProfilePhoneEnabled(this)) {
            addRow(rows, ProfileCardActivity::drawPhoneIcon, SettingsStore.getProfilePhone(this));
        }
        if (SettingsStore.getProfileAddressEnabled(this)) {
            addRow(rows, ProfileCardActivity::drawHomeIcon, SettingsStore.getProfileAddress(this));
        }
        return rows;
    }

    /** Facebook/Instagram/Twitter/LinkedIn/Website, in that order -- filled in AND left enabled via that field's own toggle. */
    private List<String> linkRows() {
        List<String> links = new ArrayList<>();
        if (SettingsStore.getProfileFacebookEnabled(this)) addLink(links, SettingsStore.getProfileFacebook(this));
        if (SettingsStore.getProfileInstagramEnabled(this)) addLink(links, SettingsStore.getProfileInstagram(this));
        if (SettingsStore.getProfileTwitterEnabled(this)) addLink(links, SettingsStore.getProfileTwitter(this));
        if (SettingsStore.getProfileLinkedinEnabled(this)) addLink(links, SettingsStore.getProfileLinkedin(this));
        if (SettingsStore.getProfileWebsiteEnabled(this)) addLink(links, SettingsStore.getProfileWebsite(this));
        return links;
    }

    private void addLink(List<String> links, String value) {
        if (!TextUtils.isEmpty(value)) links.add(value);
    }

    /** One contact-stat row: which vector icon to draw beside it, and its value. Replaces the
     * old [glyph, value] String[] pairing now that the glyph is a drawn icon, not a character. */
    private static final class StatRow {
        final IconDrawer icon;
        final String value;
        StatRow(IconDrawer icon, String value) {
            this.icon = icon;
            this.value = value;
        }
    }

    private void addRow(List<StatRow> rows, IconDrawer icon, String value) {
        if (!TextUtils.isEmpty(value)) rows.add(new StatRow(icon, value));
    }

    private String initialsOf(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return "?";
        String[] parts = trimmed.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() >= 2) break;
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0)));
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    private TextView makeButton(LinearLayout host, String label, View.OnClickListener listener) {
        float d = getResources().getDisplayMetrics().density;
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setAllCaps(true);
        btn.setGravity(Gravity.CENTER);
        btn.setTextSize(11f);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setLetterSpacing(0.1f);
        btn.setMinimumHeight(Math.round(48 * d));
        btn.setBackgroundResource(Theming.pick(this,
                R.drawable.row_bg_ge, R.drawable.row_bg_ne, R.drawable.row_bg_adaptive));
        btn.setForeground(getDrawable(R.drawable.ripple_rounded_14dp));
        btn.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(14 * d);
        btn.setLayoutParams(lp);
        host.addView(btn);
        return btn;
    }
}
