package io.github.shumtugle.pastille;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
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
import android.widget.Toast;

/**
 * One screen for the six phrases. It is both the widget's configuration step and the
 * editor opened later. Leaving it in any way saves: there is no unsaved state to lose.
 */
public class EditorActivity extends Activity {
    private static final String EXTRA_SLOT = "slot";
    private static final int ID_LABEL = 1000;
    private static final int ID_TEXT = 2000;

    private int widgetId;
    private int glass;
    private final TextView[] glassChips = new TextView[PillStore.GLASS.length];
    private final EditText[] labels = new EditText[PillStore.COUNT];
    private final EditText[] texts = new EditText[PillStore.COUNT];
    private final View[] heads = new View[PillStore.COUNT];

    static Intent intent(Context c, int widgetId, int slot) {
        return new Intent(c, EditorActivity.class)
                .setData(Uri.parse("pastille://edit/" + widgetId + "/" + slot))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(EXTRA_SLOT, slot);
    }

    static PendingIntent pending(Context c, int widgetId, int slot) {
        Intent i = intent(c, widgetId, slot)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return PendingIntent.getActivity(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean isOurs(Context c, int widgetId) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false;
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(c).getAppWidgetInfo(widgetId);
        return info != null && new ComponentName(c, PastilleWidget.class).equals(info.provider);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (!isOurs(this, widgetId)) {
            finish();
            return;
        }
        final int focus = getIntent().getIntExtra(EXTRA_SLOT, -1);

        LinearLayout root = Ui.root(this);

        View back = Ui.back(this);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveAndClose(); }
        });
        LinearLayout.LayoutParams bp = Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44), Ui.dp(this, 16));
        root.addView(back, bp);

        root.addView(Ui.title(this, getString(R.string.editor_title)),
                Ui.lp(-1, -2, Ui.dp(this, 16)));
        TextView intro = Ui.text(this, getString(R.string.editor_intro), 15, Ui.MUTED, false);
        intro.setLineSpacing(Ui.dp(this, 3), 1f);
        root.addView(intro, Ui.lp(-1, -2, Ui.dp(this, 6)));

        glass = state != null ? state.getInt("glass", PillStore.glass(this, widgetId)) : PillStore.glass(this, widgetId);
        root.addView(Ui.section(this, getString(R.string.editor_glass)), Ui.lp(-1, -2, Ui.dp(this, 20)));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        for (int g = 0; g < PillStore.GLASS.length; g++) {
            final int value = PillStore.GLASS[g];
            TextView chip = Ui.text(this, value + " %", 14, Ui.INK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setClickable(true);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { glass = value; paintGlass(); }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
            if (g > 0) cp.leftMargin = Ui.dp(this, 8);
            chips.addView(chip, cp);
            glassChips[g] = chip;
        }
        root.addView(chips, Ui.lp(-1, -2, Ui.dp(this, 8)));
        paintGlass();

        final ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 24));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        for (int i = 0; i < PillStore.COUNT; i++) addSlot(list, i);

        View rule = new View(this);
        rule.setBackgroundColor(Ui.RULE);
        root.addView(rule, Ui.lp(-1, Ui.dp(this, 1)));
        TextView done = Ui.primary(this, getString(R.string.editor_done));
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveAndClose(); }
        });
        LinearLayout.LayoutParams dp = Ui.lp(-1, Ui.dp(this, 56), Ui.dp(this, 12));
        dp.bottomMargin = Ui.dp(this, 16);
        root.addView(done, dp);

        if (focus >= 0 && focus < PillStore.COUNT) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            final EditText target = texts[focus].getText().length() == 0 ? texts[focus] : labels[focus];
            target.requestFocus();
            scroll.post(new Runnable() {
                @Override public void run() { scroll.smoothScrollTo(0, heads[focus].getTop()); }
            });
        } else {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void paintGlass() {
        for (int g = 0; g < glassChips.length; g++) {
            boolean on = PillStore.GLASS[g] == glass;
            glassChips[g].setBackground(on ? Ui.pressable(this, Ui.INK, 22, 0) : Ui.pressable(this, Ui.SURFACE, 22, Ui.LINE));
            glassChips[g].setTextColor(on ? Ui.BG : Ui.INK);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("glass", glass);
    }

    private void addSlot(LinearLayout list, final int i) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(Ui.dot(this, Ui.HUES[i], 10));
        head.addView(dot, Ui.lp(Ui.dp(this, 10), Ui.dp(this, 10)));
        TextView name = Ui.section(this, getString(R.string.editor_slot, i + 1));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
        np.leftMargin = Ui.dp(this, 8);
        head.addView(name, np);
        TextView clear = Ui.link(this, getString(R.string.editor_clear));
        clear.setTextSize(13);
        head.addView(clear);
        heads[i] = head;
        list.addView(head, Ui.lp(-1, -2, Ui.dp(this, i == 0 ? 12 : 28)));

        LinearLayout plate = new LinearLayout(this);
        plate.setOrientation(LinearLayout.VERTICAL);
        plate.setBackground(Ui.shape(this, Ui.SURFACE, 24, Ui.LINE));
        int p = Ui.dp(this, 12);
        plate.setPadding(p, p, p, p);

        plate.addView(caption(R.string.editor_label));
        labels[i] = field(false);
        labels[i].setId(ID_LABEL + i);
        labels[i].setHint(R.string.editor_label_hint);
        labels[i].setFilters(new InputFilter[]{new InputFilter.LengthFilter(PillStore.MAX_LABEL)});
        labels[i].setText(PillStore.label(this, widgetId, i));
        plate.addView(labels[i], Ui.lp(-1, Ui.dp(this, 48), Ui.dp(this, 4)));

        TextView tc = caption(R.string.editor_text);
        plate.addView(tc, Ui.lp(-1, -2, Ui.dp(this, 10)));
        texts[i] = field(true);
        texts[i].setId(ID_TEXT + i);
        texts[i].setHint(R.string.editor_text_hint);
        texts[i].setFilters(new InputFilter[]{new InputFilter.LengthFilter(PillStore.MAX_TEXT)});
        texts[i].setText(PillStore.text(this, widgetId, i));
        plate.addView(texts[i], Ui.lp(-1, -2, Ui.dp(this, 4)));

        list.addView(plate, Ui.lp(-1, -2, Ui.dp(this, 8)));

        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                labels[i].setText("");
                texts[i].setText("");
                texts[i].requestFocus();
            }
        });
    }

    private TextView caption(int res) {
        TextView t = Ui.text(this, getString(res), 11, Ui.HINT, true);
        t.setPadding(Ui.dp(this, 14), 0, 0, 0);
        return t;
    }

    private EditText field(boolean multiline) {
        EditText e = new EditText(this);
        e.setTextSize(16);
        e.setTextColor(Ui.INK);
        e.setHintTextColor(Ui.HINT);
        e.setTypeface(Ui.REGULAR);
        int h = Ui.dp(this, 16);
        if (multiline) {
            e.setBackground(Ui.shape(this, Ui.BG, 18, 0));
            e.setPadding(h, Ui.dp(this, 12), h, Ui.dp(this, 12));
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setMinLines(3);
            e.setMaxLines(10);
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setLineSpacing(Ui.dp(this, 2), 1f);
        } else {
            e.setBackground(Ui.shape(this, Ui.BG, 24, 0));
            e.setPadding(h, 0, h, 0);
            e.setSingleLine(true);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        }
        return e;
    }

    private boolean save() {
        if (!isOurs(this, widgetId)) return false;
        String[] l = new String[PillStore.COUNT];
        String[] t = new String[PillStore.COUNT];
        for (int i = 0; i < PillStore.COUNT; i++) {
            l[i] = labels[i].getText().toString().trim();
            // The phrase is kept exactly as typed: spaces, line breaks, punctuation.
            t[i] = texts[i].getText().toString();
        }
        if (!PillStore.save(this, widgetId, l, t, glass)) return false;
        PastilleWidget.refresh(this, widgetId);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        return true;
    }

    private void saveAndClose() {
        if (save() || !isOurs(this, widgetId)) {
            finish();
        } else {
            Toast.makeText(this, R.string.editor_save_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        saveAndClose();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Swiping the app away or pressing Home also keeps what was typed.
        if (!isFinishing() && labels[0] != null) save();
    }
}
