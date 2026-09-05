package io.github.shumtugle.hark;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class Bar extends View {

    private final Paint bed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float frac = 0f;

    public Bar(Context c) {
        super(c);
        bed.setColor(Ui.ACCENT_DIM);
        fill.setColor(Ui.ACCENT);
    }

    public void bedColor(int c) { bed.setColor(c); invalidate(); }

    public void set(float f) {
        if (f < 0) f = 0;
        if (f > 1) f = 1;
        if (Math.abs(f - frac) < 0.0005f) return;
        frac = f;
        invalidate();
    }

    public float get() { return frac; }

    private boolean knob = false;

    public void knob(boolean on) { knob = on; invalidate(); }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float y = h / 2f;
        cv.drawRect(0, 0, w, h, bed);
        if (frac > 0) cv.drawRect(0, 0, w * frac, h, fill);
        if (knob) {
            float r = Math.max(h * 1.9f, 7f);
            cv.drawCircle(Math.max(r, Math.min(w - r, w * frac)), y, r, fill);
        }
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        super.onMeasure(wSpec, hSpec);
        if (knob) setMeasuredDimension(getMeasuredWidth(),
                Math.max(getMeasuredHeight(), (int) (getMeasuredHeight() * 4f)));
    }
}
