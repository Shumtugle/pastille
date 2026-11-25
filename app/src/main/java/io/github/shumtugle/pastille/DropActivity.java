package io.github.shumtugle.pastille;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
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
 * The drop-down under the widget's arrow: every note as a card on the same glass, the ones on
 * the widget first, then the rest. A tap copies, the card flashes, the panel folds away; a long
 * press opens the note. A tap outside folds it without copying.
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

        int visible = PillStore.visible(this, widgetId);
        int last = PillStore.lastFilled(this, widgetId);
        boolean any = false;
        TextView hint = Ui.text(this, getString(R.string.drop_hint), 12, Ui.HINT, false);
        hint.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), 0);
        grid.addView(hint);
        any |= section(grid, R.string.drop_on_widget, 0, Math.min(visible, last + 1));
        any |= section(grid, R.string.drop_others, visible, last + 1);
        if (!any) {
            grid.removeView(hint);
            TextView none = Ui.text(this, getString(R.string.drop_empty), 15, Ui.MUTED, false);
            int p = Ui.dp(this, 14);
            none.setPadding(p, p, p, Ui.dp(this, 6));
            grid.addView(none);
        }

        panel.addView(footer(visible), new LinearLayout.LayoutParams(-1, Ui.dp(this, 60)));

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

    /**
     * A heading and the notes of slots [from, to) laid out in two columns that fill unevenly,
     * each card as tall as its text: a new card goes under the shorter column.
     */
    private boolean section(LinearLayout grid, int heading, int from, int to) {
        LinearLayout left = column(), right = column();
        int hl = 0, hr = 0;
        boolean any = false;
        for (int i = from; i < to; i++) {
            if (PillStore.text(this, widgetId, i).isEmpty()) continue;
            int h = estimate(i);
            if (hl <= hr) { left.addView(card(i)); hl += h; } else { right.addView(card(i)); hr += h; }
            any = true;
        }
        if (!any) return false;
        TextView head = Ui.text(this, getString(heading), 13, Ui.MUTED, true);
        head.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), 0, Ui.dp(this, 6));
        grid.addView(head);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1f));
        grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return true;
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    /** Rough height of a card in lines, only to keep the two columns level. */
    private int estimate(int slot) {
        int lines = 2;
        if (!PillStore.label(this, widgetId, slot).trim().isEmpty()) lines += 2;
        int body = 0;
        for (String par : PillStore.text(this, widgetId, slot).split("\n", -1)) {
            body += Math.max(1, (par.length() + 19) / 20);
        }
        return lines + Math.min(body, CARD_LINES);
    }

    private static final int CARD_LINES = 9;

    private View card(final int slot) {
        final int hue = Ui.HUES[slot % PillStore.HUES];
        final String label = PillStore.label(this, widgetId, slot).trim();
        final String text = PillStore.text(this, widgetId, slot);
        final String shown = PillStore.shown(this, widgetId, slot);

        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int ph = Ui.dp(this, 16), pv = Ui.dp(this, 14);
        card.setPadding(ph, pv, ph, pv);
        card.setBackground(Ui.pressable(this, PILL_FILL, 22, EDGE));
        card.setClickable(true);
        card.setLongClickable(true);
        card.setFocusable(true);
        card.setContentDescription(getString(R.string.a11y_copy, shown));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);
        final View dot = new View(this);
        dot.setBackground(Ui.dot(this, hue, 9));
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(this, 9), Ui.dp(this, 9));
        dp.topMargin = Ui.dp(this, 6);
        top.addView(dot, dp);

        boolean titled = !label.isEmpty();
        final TextView head = Ui.text(this, titled ? label : text.trim(), titled ? 16 : 14,
                titled ? Ui.INK : Ui.MUTED, titled);
        head.setMaxLines(titled ? 2 : CARD_LINES);
        head.setEllipsize(TextUtils.TruncateAt.END);
        head.setLineSpacing(Ui.dp(this, 2), 1f);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, -2, 1f);
        hp.leftMargin = Ui.dp(this, 9);
        top.addView(head, hp);
        card.addView(top);

        TextView bodyView = null;
        if (titled && !text.trim().equals(label)) {
            bodyView = Ui.text(this, text.trim(), 14, Ui.MUTED, false);
            bodyView.setMaxLines(CARD_LINES - 2);
            bodyView.setEllipsize(TextUtils.TruncateAt.END);
            bodyView.setLineSpacing(Ui.dp(this, 2), 1f);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
            bp.topMargin = Ui.dp(this, 8);
            card.addView(bodyView, bp);
        }
        final TextView body = bodyView;

        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
        int m = Ui.dp(this, 4);
        outer.setMargins(m, m, m, m);
        card.setLayoutParams(outer);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (done) return;
                // Read at tap time, so a note edited a moment ago is the one copied.
                String now = PillStore.text(DropActivity.this, widgetId, slot);
                if (now.isEmpty()) return;
                try {
                    ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cb == null) return;
                    cb.setPrimaryClip(ClipData.newPlainText(shown, now));
                } catch (RuntimeException e) {
                    return;
                }
                done = true;
                card.setBackground(Ui.shape(DropActivity.this, hue, 22, 0));
                dot.setVisibility(View.GONE);
                if (body != null) body.setVisibility(View.GONE);
                head.setText(R.string.pill_copied);
                head.setTextColor(0xFF0E1014);
                head.setTypeface(Ui.MEDIUM);
                head.setTextSize(16);
                card.postDelayed(new Runnable() {
                    @Override public void run() { fold(); }
                }, FOLD_AFTER_MS);
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (done) return true;
                openNote(slot);
                return true;
            }
        });
        return card;
    }

    private View footer(final int visible) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.HORIZONTAL);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);

        TextView all = small(getString(R.string.drop_edit_all));
        all.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_edit, 0, 0, 0);
        all.setCompoundDrawablePadding(Ui.dp(this, 6));
        all.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openEditor(-1); }
        });
        f.addView(all);
        f.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        int free = PillStore.firstEmpty(this, widgetId, visible);
        if (free < 0) free = PillStore.firstEmpty(this, widgetId, 0);
        final int slot = free;
        if (slot >= 0) {
            TextView add = Ui.text(this, "+", 26, Ui.BG, false);
            add.setGravity(Gravity.CENTER);
            add.setIncludeFontPadding(false);
            add.setBackground(Ui.pressable(this, Ui.INK, 18, 0));
            add.setClickable(true);
            add.setContentDescription(getString(R.string.drop_new));
            add.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openNote(slot); }
            });
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52));
            ap.rightMargin = Ui.dp(this, 2);
            f.addView(add, ap);
        }
        return f;
    }

    private TextView small(String s) {
        TextView t = Ui.text(this, s, 13, 0xA6F5F1E8, true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 10);
        t.setPadding(p, 0, p, 0);
        t.setMinHeight(Ui.dp(this, 44));
        t.setBackground(Ui.pressable(this, 0x00000000, 20, 0));
        t.setClickable(true);
        return t;
    }

    private void openNote(int slot) {
        startActivity(NoteActivity.intent(this, widgetId, slot).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
        overridePendingTransition(0, 0);
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
