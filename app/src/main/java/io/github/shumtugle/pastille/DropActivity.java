package io.github.shumtugle.pastille;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The drop-down under the widget's arrow: the phrases that did not fit on the widget,
 * as pills on the same glass. A tap copies, the pill flashes, the panel folds away.
 * A tap outside folds it without copying.
 */
public class DropActivity extends Activity {
    private static final long FOLD_AFTER_MS = 450;
    private static final int GLASS_FILL = 0xEB0E1014;
    private static final int EDGE = 0x3DFFFFFF;
    private static final int PILL_FILL = 0x14FFFFFF;

    private int widgetId;
    private boolean done;

    static PendingIntent pending(Context c, int widgetId) {
        Intent i = new Intent(c, DropActivity.class)
                .setData(Uri.parse("pastille://drop/" + widgetId))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        return PendingIntent.getActivity(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (!EditorActivity.isOurs(this, widgetId)) {
            finish();
            return;
        }

        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(0x00000000);
        w.setNavigationBarColor(0x00000000);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x59000000);
        root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { fold(); }
        });
        setContentView(root);

        final LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable glass = new GradientDrawable();
        glass.setColor(GLASS_FILL);
        glass.setStroke(Ui.dp(this, 1), EDGE);
        glass.setCornerRadius(Ui.dp(this, 30));
        panel.setBackground(glass);
        int pad = Ui.dp(this, 7);
        panel.setPadding(pad, pad, pad, Ui.dp(this, 4));
        panel.setClickable(true);
        panel.setElevation(Ui.dp(this, 12));
        panel.setVisibility(View.INVISIBLE);

        final ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        int from = PillStore.visible(this, widgetId);
        int last = PillStore.lastFilled(this, widgetId);
        LinearLayout row = null;
        int inRow = 0;
        for (int i = from; i <= last; i++) {
            if (PillStore.text(this, widgetId, i).isEmpty()) continue;
            if (row == null || inRow == 2) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
                inRow = 0;
            }
            row.addView(pill(i), new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1f));
            inRow++;
        }
        if (row != null && inRow == 1) {
            // Keep the last pill its width instead of stretching it across the row.
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        }
        if (grid.getChildCount() == 0) {
            TextView none = Ui.text(this, getString(R.string.drop_empty), 15, Ui.MUTED, false);
            int p = Ui.dp(this, 14);
            none.setPadding(p, p, p, Ui.dp(this, 6));
            grid.addView(none);
        }

        panel.addView(footer(from), new LinearLayout.LayoutParams(-1, Ui.dp(this, 44)));

        final int side = Ui.dp(this, 4);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        plp.leftMargin = side;
        plp.rightMargin = side;
        root.addView(panel, plp);

        // Place the panel just under the arrow that opened it, or above if there is no room below.
        final Rect from_ = getIntent().getSourceBounds();
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (root.getHeight() == 0 || panel.getHeight() == 0) return;
                root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                place(root, panel, scroll, from_);
            }
        });
    }

    private void place(FrameLayout root, LinearLayout panel, ScrollView scroll, Rect anchor) {
        int[] at = new int[2];
        root.getLocationOnScreen(at);
        int top = insetTop(root);
        int bottom = root.getHeight() - insetBottom(root);
        int gap = Ui.dp(this, 14);
        int h = panel.getHeight();

        int y;
        if (anchor == null) {
            y = top + Ui.dp(this, 24);
        } else {
            int below = anchor.bottom - at[1] + gap;
            int above = anchor.top - at[1] - gap;
            if (below + h <= bottom || bottom - below >= above - top) {
                y = below;
                h = Math.min(h, bottom - below - Ui.dp(this, 8));
            } else {
                h = Math.min(h, above - top);
                y = above - h;
            }
        }
        if (h < panel.getHeight()) {
            // Too many phrases for the room left: the grid scrolls inside the glass.
            int fixed = panel.getHeight() - scroll.getHeight();
            LinearLayout.LayoutParams sp = (LinearLayout.LayoutParams) scroll.getLayoutParams();
            sp.height = Math.max(Ui.dp(this, 60), h - fixed);
            scroll.setLayoutParams(sp);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        lp.topMargin = Math.max(top, y);
        panel.setLayoutParams(lp);
        panel.setAlpha(0f);
        panel.setTranslationY(-Ui.dp(this, 8));
        panel.setVisibility(View.VISIBLE);
        panel.animate().alpha(1f).translationY(0).setDuration(160).start();
    }

    private int insetTop(View v) {
        WindowInsets in = v.getRootWindowInsets();
        if (in == null) return 0;
        if (Build.VERSION.SDK_INT >= 30) return in.getInsets(WindowInsets.Type.systemBars()).top;
        return in.getSystemWindowInsetTop();
    }

    private int insetBottom(View v) {
        WindowInsets in = v.getRootWindowInsets();
        if (in == null) return 0;
        if (Build.VERSION.SDK_INT >= 30) return in.getInsets(WindowInsets.Type.systemBars()).bottom;
        return in.getSystemWindowInsetBottom();
    }

    private View pill(final int slot) {
        final int hue = Ui.HUES[slot % PillStore.HUES];
        final LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 12), 0);
        p.setBackground(Ui.pressable(this, PILL_FILL, 28, EDGE));
        p.setClickable(true);
        p.setFocusable(true);
        final String shown = PillStore.shown(this, widgetId, slot);
        p.setContentDescription(getString(R.string.a11y_copy, shown));

        final View dot = new View(this);
        dot.setBackground(Ui.dot(this, hue, 9));
        p.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 9), Ui.dp(this, 9)));

        final TextView label = Ui.text(this, shown, 14, Ui.INK, true);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setIncludeFontPadding(false);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, -2, 1f);
        llp.leftMargin = Ui.dp(this, 9);
        p.addView(label, llp);

        FrameLayout.LayoutParams outer = new FrameLayout.LayoutParams(-1, -1);
        int m = Ui.dp(this, 3);
        outer.setMargins(m, m, m, m);
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(p, outer);

        p.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (done) return;
                // Read at tap time, so a phrase edited a moment ago is the one copied.
                String text = PillStore.text(DropActivity.this, widgetId, slot);
                if (text.isEmpty()) return;
                try {
                    ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cb == null) return;
                    cb.setPrimaryClip(ClipData.newPlainText(shown, text));
                } catch (RuntimeException e) {
                    return;
                }
                done = true;
                p.setBackground(Ui.shape(DropActivity.this, hue, 28, 0));
                dot.setVisibility(View.GONE);
                label.setText(R.string.pill_copied);
                label.setTextColor(0xFF0E1014);
                label.setGravity(Gravity.CENTER);
                p.postDelayed(new Runnable() {
                    @Override public void run() { fold(); }
                }, FOLD_AFTER_MS);
            }
        });
        return wrap;
    }

    private View footer(final int from) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.HORIZONTAL);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);

        final int free = PillStore.firstEmpty(this, widgetId, from);
        if (free >= 0) {
            TextView add = small(getString(R.string.drop_add));
            add.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openEditor(free); }
            });
            f.addView(add);
        }
        f.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView edit = small(getString(R.string.edit_short));
        edit.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_edit, 0, 0, 0);
        edit.setCompoundDrawablePadding(Ui.dp(this, 6));
        edit.setContentDescription(getString(R.string.edit_phrases));
        edit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openEditor(-1); }
        });
        f.addView(edit);
        return f;
    }

    private TextView small(String s) {
        TextView t = Ui.text(this, s, 13, 0xA6F5F1E8, true);
        t.setTypeface(Ui.MEDIUM, Typeface.NORMAL);
        t.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 10);
        t.setPadding(p, 0, p, 0);
        t.setMinHeight(Ui.dp(this, 40));
        t.setBackground(Ui.pressable(this, 0x00000000, 20, 0));
        t.setClickable(true);
        return t;
    }

    private void openEditor(int slot) {
        startActivity(EditorActivity.intent(this, widgetId, slot).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
        overridePendingTransition(0, 0);
    }

    private void fold() {
        finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    @Override
    public void onBackPressed() {
        fold();
    }
}
