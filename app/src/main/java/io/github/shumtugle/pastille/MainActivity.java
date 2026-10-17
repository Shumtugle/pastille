package io.github.shumtugle.pastille;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The launcher screen: what the widget is, which copies are placed, and how to place one. */
public class MainActivity extends Activity {
    private LinearLayout rows;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.root(this);

        root.addView(Ui.title(this, getString(R.string.app_name)), Ui.lp(-1, -2, Ui.dp(this, 76)));
        TextView intro = Ui.text(this, getString(R.string.main_intro), 15, Ui.MUTED, false);
        intro.setLineSpacing(Ui.dp(this, 3), 1f);
        root.addView(intro, Ui.lp(-1, -2, Ui.dp(this, 8)));

        root.addView(Ui.section(this, getString(R.string.main_section)), Ui.lp(-1, -2, Ui.dp(this, 36)));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setBackground(Ui.shape(this, Ui.SURFACE, 24, Ui.LINE));
        int p = Ui.dp(this, 4);
        rows.setPadding(0, p, 0, p);
        scroll.addView(rows);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = Ui.dp(this, 8);
        root.addView(scroll, sp);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));

        final AppWidgetManager m = AppWidgetManager.getInstance(this);
        if (m.isRequestPinAppWidgetSupported()) {
            TextView add = Ui.primary(this, getString(R.string.main_add));
            add.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    m.requestPinAppWidget(new ComponentName(MainActivity.this, PastilleWidget.class), null, null);
                }
            });
            LinearLayout.LayoutParams ap = Ui.lp(-1, Ui.dp(this, 56));
            ap.bottomMargin = Ui.dp(this, 16);
            root.addView(add, ap);
        } else {
            TextView hint = Ui.text(this, getString(R.string.main_add_manual), 15, Ui.MUTED, false);
            hint.setLineSpacing(Ui.dp(this, 3), 1f);
            LinearLayout.LayoutParams hp = Ui.lp(-1, -2);
            hp.bottomMargin = Ui.dp(this, 24);
            root.addView(hint, hp);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        fill();
    }

    private void fill() {
        rows.removeAllViews();
        int[] ids = AppWidgetManager.getInstance(this)
                .getAppWidgetIds(new ComponentName(this, PastilleWidget.class));
        if (ids.length == 0) {
            TextView none = Ui.text(this, getString(R.string.main_none), 15, Ui.MUTED, false);
            int p = Ui.dp(this, 16);
            none.setPadding(p, p, p, p);
            rows.addView(none);
            return;
        }
        for (int n = 0; n < ids.length; n++) rows.addView(row(ids[n], n + 1));
    }

    private View row(final int widgetId, int number) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 64));
        int p = Ui.dp(this, 16);
        row.setPadding(p, Ui.dp(this, 10), p, Ui.dp(this, 10));
        row.setBackground(Ui.pressable(this, 0x00000000, 20, 0));
        row.setClickable(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, getString(R.string.main_widget, number), 17, Ui.INK, false));
        TextView sub = Ui.text(this, summary(widgetId), 13, Ui.MUTED, false);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(sub);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(Ui.text(this, "›", 22, Ui.MUTED, false));

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(EditorActivity.intent(MainActivity.this, widgetId, -1));
            }
        });
        return row;
    }

    private String summary(int widgetId) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < PillStore.COUNT; i++) {
            String s = PillStore.shown(this, widgetId, i);
            if (s.isEmpty()) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(s);
        }
        return b.length() == 0 ? getString(R.string.main_widget_empty) : b.toString();
    }
}
