package io.github.shumtugle.pastille;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Phrases per widget instance, each widget in its own preferences file.
 * The first ones sit on the widget itself; the rest wait in the drop-down under the arrow.
 */
final class PillStore {
    /** Phrases a widget can hold in all. */
    static final int MAX = 24;
    /** Pills the widget itself can draw; the rest only ever live in the drop-down. */
    static final int ON_WIDGET = 6;
    /** Hues repeat every six places. */
    static final int HUES = 6;
    static final int MAX_LABEL = 40;
    static final int MAX_TEXT = 10000;
    /** Glass dimming steps, in percent of the card's darkness. */
    static final int[] GLASS = {10, 40, 70, 90};
    static final int GLASS_DEFAULT = 40;
    /** Pills shown on the widget before its size is first reported. */
    static final int VISIBLE_DEFAULT = 4;

    private PillStore() {}

    private static String file(int widgetId) {
        return "widget_" + widgetId;
    }

    private static SharedPreferences prefs(Context c, int widgetId) {
        return c.getSharedPreferences(file(widgetId), Context.MODE_PRIVATE);
    }

    static String text(Context c, int widgetId, int slot) {
        return prefs(c, widgetId).getString("text_" + slot, "");
    }

    static String label(Context c, int widgetId, int slot) {
        return prefs(c, widgetId).getString("label_" + slot, "");
    }

    /** What the pill shows: the label, or the first line of the text when the label is blank. */
    static String shown(Context c, int widgetId, int slot) {
        String l = label(c, widgetId, slot).trim();
        if (!l.isEmpty()) return l;
        String t = text(c, widgetId, slot).trim();
        int nl = t.indexOf('\n');
        if (nl >= 0) t = t.substring(0, nl);
        t = t.replaceAll("\\s+", " ").trim();
        return t.length() > MAX_LABEL ? t.substring(0, MAX_LABEL).trim() + "…" : t;
    }

    static int glass(Context c, int widgetId) {
        int g = prefs(c, widgetId).getInt("glass", GLASS_DEFAULT);
        for (int step : GLASS) if (step == g) return g;
        return GLASS_DEFAULT;
    }

    /** How many pills the widget drew last time; everything from there on belongs to the drop-down. */
    static int visible(Context c, int widgetId) {
        int v = prefs(c, widgetId).getInt("visible", VISIBLE_DEFAULT);
        return Math.max(1, Math.min(ON_WIDGET, v));
    }

    static void setVisible(Context c, int widgetId, int visible) {
        SharedPreferences p = prefs(c, widgetId);
        if (p.getInt("visible", -1) != visible) p.edit().putInt("visible", visible).apply();
    }

    /** Index of the last slot that holds a phrase, or -1. */
    static int lastFilled(Context c, int widgetId) {
        for (int i = MAX - 1; i >= 0; i--) {
            if (!text(c, widgetId, i).isEmpty()) return i;
        }
        return -1;
    }

    /** The first empty slot at or after {@code from}, or -1 when every slot is taken. */
    static int firstEmpty(Context c, int widgetId, int from) {
        for (int i = Math.max(0, from); i < MAX; i++) {
            if (text(c, widgetId, i).isEmpty()) return i;
        }
        return -1;
    }

    /** Saves all slots; a null or missing entry is stored as empty. */
    static boolean save(Context c, int widgetId, String[] labels, String[] texts, int glass) {
        SharedPreferences.Editor e = prefs(c, widgetId).edit();
        e.putInt("glass", glass);
        for (int i = 0; i < MAX; i++) {
            String l = i < labels.length && labels[i] != null ? labels[i] : "";
            String t = i < texts.length && texts[i] != null ? texts[i] : "";
            e.putString("label_" + i, l);
            e.putString("text_" + i, t);
        }
        return e.commit();
    }

    static void delete(Context c, int widgetId) {
        c.deleteSharedPreferences(file(widgetId));
    }

    /** Carries phrases over when the host hands out new ids after a restore. */
    static void move(Context c, int fromId, int toId) {
        if (fromId == toId) return;
        String[] labels = new String[MAX];
        String[] texts = new String[MAX];
        for (int i = 0; i < MAX; i++) {
            labels[i] = label(c, fromId, i);
            texts[i] = text(c, fromId, i);
        }
        int visible = visible(c, fromId);
        if (save(c, toId, labels, texts, glass(c, fromId))) {
            setVisible(c, toId, visible);
            delete(c, fromId);
        }
    }
}
