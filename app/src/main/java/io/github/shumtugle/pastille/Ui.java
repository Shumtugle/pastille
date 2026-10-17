package io.github.shumtugle.pastille;

import android.app.Activity;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared palette and small view builders; the screens are built in code, no layout files. */
final class Ui {
    static final int BG = 0xFF0E1014;
    static final int SURFACE = 0xFF171A20;
    static final int LINE = 0xFF34373E;
    static final int RULE = 0x22F5F1E8;
    static final int INK = 0xFFF5F1E8;
    static final int MUTED = 0xFFC9C5BC;
    static final int HINT = 0xFF8F8C86;
    /** The one filled button per screen: cream glass on the dark ground. */
    static final int ACCENT = 0xFFF5F1E8;
    static final int ON_ACCENT = 0xFF0E1014;
    static final int ACCENT_TEXT = 0xFFF5F1E8;
    static final int[] HUES = {0xFFE0A84B, 0xFFD98A6E, 0xFF7FB0C4, 0xFFA8C08F, 0xFFB3A5E0, 0xFFD99AB4};

    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable shape(Context c, int fill, float radiusDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) d.setStroke(dp(c, 1), stroke);
        return d;
    }

    static RippleDrawable pressable(Context c, int fill, float radiusDp, int stroke) {
        return new RippleDrawable(ColorStateList.valueOf(0x33F5F1E8), shape(c, fill, radiusDp, stroke), null);
    }

    static GradientDrawable dot(Context c, int color, int sizeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setSize(dp(c, sizeDp), dp(c, sizeDp));
        return d;
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(medium ? MEDIUM : REGULAR);
        return t;
    }

    static TextView title(Context c, CharSequence s) {
        TextView t = text(c, s, 34, INK, true);
        t.setLetterSpacing(-0.01f);
        return t;
    }

    static TextView section(Context c, CharSequence s) {
        return text(c, s, 13, MUTED, true);
    }

    static TextView link(Context c, CharSequence s) {
        TextView t = text(c, s, 14, ACCENT_TEXT, true);
        int p = dp(c, 8);
        t.setPadding(p, p, p, p);
        t.setBackground(pressable(c, 0x00000000, 16, 0));
        return t;
    }

    static TextView primary(Context c, CharSequence s) {
        TextView b = text(c, s, 16, ON_ACCENT, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(pressable(c, ACCENT, 28, 0));
        b.setClickable(true);
        b.setFocusable(true);
        b.setMinHeight(dp(c, 56));
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        return b;
    }

    static ImageButton back(Activity a) {
        ImageButton b = new ImageButton(a);
        b.setImageResource(R.drawable.ic_back);
        b.setBackground(pressable(a, SURFACE, 22, LINE));
        b.setContentDescription(a.getString(R.string.a11y_back));
        return b;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(int w, int h, int topMarginPx) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = topMarginPx;
        return p;
    }

    /**
     * A vertical root that keeps its content clear of system bars, cutouts and the keyboard.
     * The app draws edge to edge, so the padding comes from the window insets.
     */
    static LinearLayout root(final Activity a) {
        final LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        final int side = dp(a, 24);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                int l, t, r, b;
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets i = in.getInsets(WindowInsets.Type.systemBars()
                            | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
                    l = i.left; t = i.top; r = i.right; b = i.bottom;
                } else {
                    l = in.getSystemWindowInsetLeft(); t = in.getSystemWindowInsetTop();
                    r = in.getSystemWindowInsetRight(); b = in.getSystemWindowInsetBottom();
                }
                v.setPadding(side + l, t, side + r, b);
                return in;
            }
        });
        a.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        a.setContentView(root);
        root.requestApplyInsets();
        return root;
    }
}
