package io.github.shumtugle.hark;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;

public final class Ui {

    public static final int BG        = Color.parseColor("#171512");
    public static final int SURFACE   = Color.parseColor("#1F1D19");
    public static final int PRESSED   = Color.parseColor("#2A2723");
    public static final int TEXT      = Color.parseColor("#E8E2D6");
    public static final int TEXT_2    = Color.parseColor("#8C877C");
    public static final int TEXT_OFF  = Color.parseColor("#57534B");
    public static final int LINE      = Color.parseColor("#33302B");
    public static final int ACCENT    = Color.parseColor("#D49A3A");
    public static final int ACCENT_DIM= Color.parseColor("#6B5426");
    public static final int ALARM     = Color.parseColor("#A85D4A");

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static String time(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, ss = s % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, ss);
        return String.format("%d:%02d", m, ss);
    }

    public static String bare(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return (i > 0) ? name.substring(0, i) : name;
    }
}
