package io.github.shumtugle.pastille;

import android.content.Context;
import android.content.SharedPreferences;

/** Six phrases per widget instance, each in its own preferences file. */
final class PillStore {
    static final int COUNT = 6;
    static final int MAX_LABEL = 40;
    static final int MAX_TEXT = 10000;
    /** Glass dimming steps, in percent of the card's darkness. */
    static final int[] GLASS = {10, 40, 70, 90};
    static final int GLASS_DEFAULT = 40;

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

    static boolean isEmpty(Context c, int widgetId) {
        for (int i = 0; i < COUNT; i++) {
            if (!text(c, widgetId, i).isEmpty()) return false;
        }
        return true;
    }

    static boolean save(Context c, int widgetId, String[] labels, String[] texts, int glass) {
        SharedPreferences.Editor e = prefs(c, widgetId).edit();
        e.putInt("glass", glass);
        for (int i = 0; i < COUNT; i++) {
            e.putString("label_" + i, labels[i]);
            e.putString("text_" + i, texts[i]);
        }
        return e.commit();
    }

    static void delete(Context c, int widgetId) {
        c.deleteSharedPreferences(file(widgetId));
    }

    /** Carries phrases over when the host hands out new ids after a restore. */
    static void move(Context c, int fromId, int toId) {
        if (fromId == toId) return;
        String[] labels = new String[COUNT];
        String[] texts = new String[COUNT];
        for (int i = 0; i < COUNT; i++) {
            labels[i] = label(c, fromId, i);
            texts[i] = text(c, fromId, i);
        }
        if (save(c, toId, labels, texts, glass(c, fromId))) delete(c, fromId);
    }
}
