package com.korabel.schedule;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * The app's look: one palette that flips with the system's dark mode, plus the
 * few view builders used by every screen.
 *
 * Colours live in code rather than in XML themes because the whole UI is built
 * programmatically — one place to change, and it stays correct on Android 5,
 * which has no night resources of its own.
 */
public final class Ui {

    // light
    private static final int L_BG        = 0xFFEEF1F4;
    private static final int L_CARD      = 0xFFFFFFFF;
    private static final int L_TEXT      = 0xFF1B1B1F;
    private static final int L_MUTED     = 0xFF6B7280;
    private static final int L_HEADER    = 0xFFE3E7EE;
    private static final int L_ACCENT    = 0xFF1A3E8C;
    private static final int L_DIVIDER   = 0x14000000;

    // dark
    private static final int D_BG        = 0xFF101318;
    private static final int D_CARD      = 0xFF1A1F27;
    private static final int D_TEXT      = 0xFFE6E9EF;
    private static final int D_MUTED     = 0xFF9AA3B2;
    private static final int D_HEADER    = 0xFF20262F;
    private static final int D_ACCENT    = 0xFF9DB4FF;
    private static final int D_DIVIDER   = 0x1FFFFFFF;

    // lesson types, same hue in both themes
    private static final int LECTURE     = 0xFF3D6BE5;
    private static final int PRACTICE    = 0xFF1E9E6A;
    private static final int LAB         = 0xFFE08A1E;
    private static final int EXAM        = 0xFFD2453C;
    private static final int OTHER       = 0xFF8A8F98;

    public final boolean dark;

    public final int bg, card, text, muted, headerBg, accent, divider;
    /** верхняя / нижняя week badge. */
    public final int upperBadge = 0xFF2E4FA8, lowerBadge = 0xFF00695C;

    public Ui(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        dark = mode == Configuration.UI_MODE_NIGHT_YES;
        bg       = dark ? D_BG : L_BG;
        card     = dark ? D_CARD : L_CARD;
        text     = dark ? D_TEXT : L_TEXT;
        muted    = dark ? D_MUTED : L_MUTED;
        headerBg = dark ? D_HEADER : L_HEADER;
        accent   = dark ? D_ACCENT : L_ACCENT;
        divider  = dark ? D_DIVIDER : L_DIVIDER;
    }

    /** Colour code for a lesson type ("Лекция", "Лабораторная работа", …). */
    public static int typeColor(String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.startsWith("лекц")) return LECTURE;
        if (t.startsWith("практ") || t.startsWith("семинар")) return PRACTICE;
        if (t.startsWith("лаб")) return LAB;
        if (t.contains("экзамен") || t.contains("зачет") || t.contains("зачёт")
                || t.contains("консультац")) return EXAM;
        return OTHER;
    }

    // ------------------------------------------------------------- builders

    public static int dp(Context ctx, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                ctx.getResources().getDisplayMetrics()));
    }

    /** A rounded solid background, optionally with a border. */
    public static GradientDrawable rounded(int color, int radiusDp, Context ctx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(ctx, radiusDp));
        return d;
    }

    /** Flat, borderless, tappable text button used across the header. */
    public TextView iconButton(Context ctx, String glyph, float size) {
        TextView b = new TextView(ctx);
        b.setText(glyph);
        b.setTextSize(size);
        b.setTextColor(text);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setFocusable(true);
        b.setBackground(rounded(dark ? 0x14FFFFFF : 0x0F000000, 8, ctx));
        return b;
    }

    /** Small pill label ("ВЕРХНЯЯ НЕДЕЛЯ", lesson type). */
    public static TextView pill(Context ctx, String label, int color, int textColor) {
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setTextSize(11);
        t.setTextColor(textColor);
        t.setBackground(rounded(color, 10, ctx));
        t.setPadding(dp(ctx, 8), dp(ctx, 3), dp(ctx, 8), dp(ctx, 3));
        return t;
    }

    public static LinearLayout row(Context ctx) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout column(Context ctx) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    /** Blend `overlay` on top of `base` at the given alpha (0..1). */
    public static int blend(int base, int overlay, float alpha) {
        return Color.rgb(
                (int) (Color.red(base) * (1 - alpha) + Color.red(overlay) * alpha),
                (int) (Color.green(base) * (1 - alpha) + Color.green(overlay) * alpha),
                (int) (Color.blue(base) * (1 - alpha) + Color.blue(overlay) * alpha));
    }
}
