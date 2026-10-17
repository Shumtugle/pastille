package io.github.shumtugle.pastille;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

/**
 * The widget itself. A tap goes straight to this receiver: the phrase is written
 * to the clipboard without opening any window, and the pill answers by flooding
 * with its own colour for a moment.
 */
public class PastilleWidget extends AppWidgetProvider {
    static final String ACTION_COPY = "io.github.shumtugle.pastille.COPY";
    static final String EXTRA_SLOT = "slot";
    private static final long FLASH_MS = 1200;

    private static final int[] PILL = {R.id.pill0, R.id.pill1, R.id.pill2, R.id.pill3, R.id.pill4, R.id.pill5};
    private static final int[] ICON = {R.id.icon0, R.id.icon1, R.id.icon2, R.id.icon3, R.id.icon4, R.id.icon5};
    private static final int[] LABEL = {R.id.label0, R.id.label1, R.id.label2, R.id.label3, R.id.label4, R.id.label5};
    private static final int[] DOT = {R.drawable.dot_0, R.drawable.dot_1, R.drawable.dot_2,
            R.drawable.dot_3, R.drawable.dot_4, R.drawable.dot_5};
    private static final int[] FLASH = {R.drawable.pill_flash_0, R.drawable.pill_flash_1, R.drawable.pill_flash_2,
            R.drawable.pill_flash_3, R.drawable.pill_flash_4, R.drawable.pill_flash_5};

    private static final int INK = 0xFFF5F1E8;
    private static final int HINT = 0xA6F5F1E8;
    private static final int ON_HUE = 0xFF0E1014;

    /** Heights (dp) at which the pills leave the single line for two, then three rows. */
    private static final int TWO_ROWS_FROM = 100;
    private static final int THREE_ROWS_FROM = 175;
    /** Line mode: the edit circle and side padding (dp), and the narrowest pill worth showing. */
    private static final int LINE_CHROME = 66;
    private static final int LINE_PILL_MIN = 78;

    /** Bumped on every tap so that only the latest flash is allowed to fade out. */
    private static final SparseIntArray FLASH_GEN = new SparseIntArray();

    static void refresh(Context c, int widgetId) {
        render(c, AppWidgetManager.getInstance(c), widgetId, -1);
    }

    private static int glassDrawable(int percent) {
        switch (percent) {
            case 10: return R.drawable.glass_10;
            case 70: return R.drawable.glass_70;
            case 90: return R.drawable.glass_90;
            default: return R.drawable.glass_40;
        }
    }

    /**
     * Width from MIN_WIDTH and height from MAX_HEIGHT: in a stock launcher that pair is the
     * portrait cell, and a launcher that reports min equal to max gives the exact cell either way.
     */
    private static void render(Context c, AppWidgetManager m, int widgetId, int flashSlot) {
        Bundle o = m.getAppWidgetOptions(widgetId);
        int w = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int h = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);

        int layout;
        int visible = PillStore.COUNT;
        boolean line = h < TWO_ROWS_FROM;
        if (line) {
            layout = R.layout.widget_line;
            visible = w <= 0 ? 4 : Math.max(1, Math.min(PillStore.COUNT, (w - LINE_CHROME) / LINE_PILL_MIN));
        } else {
            layout = h < THREE_ROWS_FROM ? R.layout.widget_rows2 : R.layout.widget_rows3;
        }
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        v.setInt(android.R.id.background, "setBackgroundResource", glassDrawable(PillStore.glass(c, widgetId)));
        // Line pills are tight: a smaller face leaves a short label its whole width.
        float sp = line ? 13f : 14f;

        for (int i = 0; i < PillStore.COUNT; i++) {
            if (i >= visible) {
                v.setViewVisibility(PILL[i], View.GONE);
                continue;
            }
            v.setViewVisibility(PILL[i], View.VISIBLE);
            v.setTextViewTextSize(LABEL[i], TypedValue.COMPLEX_UNIT_SP, sp);
            boolean empty = PillStore.text(c, widgetId, i).isEmpty();
            if (empty) {
                v.setInt(PILL[i], "setBackgroundResource", R.drawable.pill_empty);
                v.setImageViewResource(ICON[i], R.drawable.ic_plus);
                v.setTextViewText(LABEL[i], c.getString(line ? R.string.pill_empty_short : R.string.pill_empty));
                v.setTextColor(LABEL[i], HINT);
                v.setContentDescription(PILL[i], c.getString(R.string.a11y_empty, i + 1));
                v.setOnClickPendingIntent(PILL[i], EditorActivity.pending(c, widgetId, i));
                continue;
            }
            String shown = PillStore.shown(c, widgetId, i);
            if (i == flashSlot) {
                v.setInt(PILL[i], "setBackgroundResource", FLASH[i]);
                v.setImageViewResource(ICON[i], R.drawable.ic_check);
                v.setTextViewText(LABEL[i], c.getString(R.string.pill_copied));
                v.setTextColor(LABEL[i], ON_HUE);
            } else {
                v.setInt(PILL[i], "setBackgroundResource", R.drawable.pill_idle);
                v.setImageViewResource(ICON[i], DOT[i]);
                v.setTextViewText(LABEL[i], shown);
                v.setTextColor(LABEL[i], INK);
            }
            v.setContentDescription(PILL[i], c.getString(R.string.a11y_copy, shown));
            v.setOnClickPendingIntent(PILL[i], copyIntent(c, widgetId, i));
        }
        v.setOnClickPendingIntent(R.id.edit, EditorActivity.pending(c, widgetId, -1));
        m.updateAppWidget(widgetId, v);
    }

    private static PendingIntent copyIntent(Context c, int widgetId, int slot) {
        Intent i = new Intent(c, PastilleWidget.class)
                .setAction(ACTION_COPY)
                // A distinct data URI keeps every widget/slot pair its own PendingIntent.
                .setData(Uri.parse("pastille://copy/" + widgetId + "/" + slot))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(EXTRA_SLOT, slot);
        return PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_COPY.equals(intent.getAction())) {
            copy(context, intent);
            return;
        }
        super.onReceive(context, intent);
    }

    private void copy(Context context, Intent intent) {
        final Context c = context.getApplicationContext();
        final int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        final int slot = intent.getIntExtra(EXTRA_SLOT, -1);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || slot < 0 || slot >= PillStore.COUNT) return;

        // The text is read at tap time, so an edit never leaves a stale phrase in a PendingIntent.
        String text = PillStore.text(c, id, slot);
        final AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (text.isEmpty()) {
            render(c, m, id, -1);
            return;
        }
        try {
            ClipboardManager cb = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb == null) return;
            cb.setPrimaryClip(ClipData.newPlainText(PillStore.shown(c, id, slot), text));
        } catch (RuntimeException e) {
            return;
        }

        final int gen;
        synchronized (FLASH_GEN) {
            gen = FLASH_GEN.get(id) + 1;
            FLASH_GEN.put(id, gen);
        }
        render(c, m, id, slot);

        final PendingResult pending = goAsync();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean latest;
                    synchronized (FLASH_GEN) {
                        latest = FLASH_GEN.get(id) == gen;
                    }
                    if (latest) render(c, m, id, -1);
                } finally {
                    pending.finish();
                }
            }
        }, FLASH_MS);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) render(c, m, id, -1);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) {
        render(c, m, id, -1);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) PillStore.delete(c, id);
    }

    @Override
    public void onRestored(Context c, int[] oldIds, int[] newIds) {
        for (int i = 0; i < oldIds.length && i < newIds.length; i++) PillStore.move(c, oldIds[i], newIds[i]);
    }
}
