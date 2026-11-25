package io.github.shumtugle.pastille;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * One note on its own screen: a title and the text, nothing else.
 * Leaving in any way saves it; deleting takes a second tap.
 */
public class NoteActivity extends Activity {
    private static final String EXTRA_SLOT = "slot";
    private static final long CONFIRM_MS = 3000;

    private int widgetId;
    private int slot;
    private EditText title;
    private EditText body;
    private boolean deleted;
    private boolean confirming;

    static Intent intent(Context c, int widgetId, int slot) {
        return new Intent(c, NoteActivity.class)
                .setData(Uri.parse("pastille://note/" + widgetId + "/" + slot))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(EXTRA_SLOT, slot);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        slot = getIntent().getIntExtra(EXTRA_SLOT, -1);
        if (!EditorActivity.isOurs(this, widgetId) || slot < 0 || slot >= PillStore.MAX) {
            finish();
            return;
        }
        boolean fresh = PillStore.text(this, widgetId, slot).isEmpty();
        getWindow().setSoftInputMode((fresh ? WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                : WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        LinearLayout root = Ui.root(this);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        View back = Ui.back(this);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        bar.addView(back, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));
        View dot = new View(this);
        dot.setBackground(Ui.dot(this, Ui.HUES[slot % PillStore.HUES], 10));
        LinearLayout.LayoutParams dp = Ui.lp(Ui.dp(this, 10), Ui.dp(this, 10));
        dp.leftMargin = Ui.dp(this, 16);
        bar.addView(dot, dp);
        TextView where = Ui.text(this, getString(slot < PillStore.visible(this, widgetId)
                ? R.string.note_on_widget : R.string.note_under_arrow), 13, Ui.MUTED, true);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
        wp.leftMargin = Ui.dp(this, 8);
        bar.addView(where, wp);
        final TextView delete = Ui.link(this, getString(R.string.note_delete));
        delete.setTextSize(14);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!confirming) {
                    confirming = true;
                    delete.setText(R.string.note_delete_sure);
                    delete.postDelayed(new Runnable() {
                        @Override public void run() {
                            confirming = false;
                            delete.setText(R.string.note_delete);
                        }
                    }, CONFIRM_MS);
                    return;
                }
                deleted = true;
                PillStore.saveOne(NoteActivity.this, widgetId, slot, "", "");
                PastilleWidget.refresh(NoteActivity.this, widgetId);
                finish();
            }
        });
        bar.addView(delete);
        root.addView(bar, Ui.lp(-1, -2, Ui.dp(this, 16)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 24));
        scroll.addView(col, new ScrollView.LayoutParams(-1, -1));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        title = bare(24, true);
        title.setId(3001);
        title.setHint(R.string.note_title_hint);
        title.setSingleLine(true);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(PillStore.MAX_LABEL)});
        title.setText(PillStore.label(this, widgetId, slot));
        col.addView(title, Ui.lp(-1, -2));

        body = bare(17, false);
        body.setId(3002);
        body.setHint(R.string.note_text_hint);
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setLineSpacing(Ui.dp(this, 4), 1f);
        body.setFilters(new InputFilter[]{new InputFilter.LengthFilter(PillStore.MAX_TEXT)});
        body.setText(PillStore.text(this, widgetId, slot));
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        ((LinearLayout.LayoutParams) body.getLayoutParams()).topMargin = Ui.dp(this, 12);

        if (fresh) body.requestFocus();
    }

    private EditText bare(float sp, boolean medium) {
        EditText e = new EditText(this);
        e.setBackground(null);
        e.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        e.setTextSize(sp);
        e.setTextColor(Ui.INK);
        e.setHintTextColor(Ui.HINT);
        e.setTypeface(medium ? Ui.MEDIUM : Ui.REGULAR);
        return e;
    }

    private void save() {
        if (deleted || title == null || !EditorActivity.isOurs(this, widgetId)) return;
        String l = title.getText().toString().trim();
        // The note is kept exactly as typed: spaces, line breaks, punctuation.
        String t = body.getText().toString();
        if (l.equals(PillStore.label(this, widgetId, slot)) && t.equals(PillStore.text(this, widgetId, slot))) return;
        if (t.isEmpty() && !l.isEmpty()) t = l;
        PillStore.saveOne(this, widgetId, slot, l, t);
        PastilleWidget.refresh(this, widgetId);
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }
}
