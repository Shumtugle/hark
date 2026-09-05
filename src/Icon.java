package io.github.shumtugle.hark;

import android.graphics.*;
import android.graphics.drawable.Drawable;

public class Icon extends Drawable {

    public static final int PLAY = 0, PAUSE = 1, BACK = 2, FWD = 3,
            UP = 4, FOLDER = 5, SPEED = 6, SLEEP = 7, DOT = 8, LOGO = 9, HEADSET = 10;

    private final int kind;
    private final String label;
    private int color;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path p = new Path();

    public Icon(int kind, int color) { this(kind, color, null); }

    public Icon(int kind, int color, String label) {
        this.kind = kind; this.color = color; this.label = label;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        tint(color);
    }

    public Icon tint(int c) {
        color = c; stroke.setColor(c); fill.setColor(c); text.setColor(c);
        invalidateSelf(); return this;
    }

    @Override public void draw(Canvas cv) {
        Rect b = getBounds();
        float w = b.width(), h = b.height();
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        float u = Math.min(w, h);
        stroke.setStrokeWidth(u * 0.085f);
        p.reset();

        switch (kind) {
            case PLAY: {
                float s = u * 0.30f;
                p.moveTo(cx - s * 0.75f, cy - s);
                p.lineTo(cx + s, cy);
                p.lineTo(cx - s * 0.75f, cy + s);
                p.close();
                Paint round = new Paint(fill);
                round.setStrokeWidth(u * 0.10f);
                round.setStyle(Paint.Style.FILL_AND_STROKE);
                round.setStrokeJoin(Paint.Join.ROUND);
                round.setAntiAlias(true);
                cv.drawPath(p, round);
                break;
            }
            case PAUSE: {
                float bw = u * 0.13f, gap = u * 0.12f, bh = u * 0.30f;
                float r = bw / 2f;
                cv.drawRoundRect(new RectF(cx - gap - bw, cy - bh, cx - gap, cy + bh), r, r, fill);
                cv.drawRoundRect(new RectF(cx + gap, cy - bh, cx + gap + bw, cy + bh), r, r, fill);
                break;
            }
            case BACK:
            case FWD: {
                float r = u * 0.30f;
                RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);

                float start = (kind == BACK) ? -60f : -120f;
                cv.drawArc(o, start, 300f, false, stroke);

                float ang = (float) Math.toRadians(start);
                float tipX = cx + r * (float) Math.cos(ang);
                float tipY = cy + r * (float) Math.sin(ang);
                float d = u * 0.13f;
                int dir = (kind == BACK) ? 1 : -1;
                p.moveTo(tipX + dir * d * 0.2f, tipY - d);
                p.lineTo(tipX, tipY);
                p.lineTo(tipX - dir * d, tipY - d * 0.15f);
                cv.drawPath(p, stroke);
                if (label != null) {
                    text.setTextSize(u * 0.30f);
                    Paint.FontMetrics fm = text.getFontMetrics();
                    cv.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, text);
                }
                break;
            }
            case UP: {
                float s = u * 0.20f;
                p.moveTo(cx + s * 0.6f, cy - s);
                p.lineTo(cx - s * 0.6f, cy);
                p.lineTo(cx + s * 0.6f, cy + s);
                cv.drawPath(p, stroke);
                break;
            }
            case FOLDER: {
                float wq = u * 0.32f, hq = u * 0.24f, step = u * 0.09f;
                float l = cx - wq, r2 = cx + wq, t = cy - hq, bt = cy + hq;
                p.moveTo(l, bt);
                p.lineTo(l, t);
                p.lineTo(l + wq * 0.7f, t);
                p.lineTo(l + wq * 0.95f, t + step);
                p.lineTo(r2, t + step);
                p.lineTo(r2, bt);
                p.close();
                cv.drawPath(p, stroke);
                break;
            }
            case SPEED: {
                float r = u * 0.30f;
                RectF o = new RectF(cx - r, cy - r * 0.85f, cx + r, cy + r * 1.15f);
                cv.drawArc(o, 180f, 180f, false, stroke);
                cv.drawLine(cx, cy + r * 0.15f,
                        cx + r * 0.62f, cy - r * 0.42f, stroke);
                break;
            }
            case SLEEP: {
                float r = u * 0.30f;
                Path outer = new Path();
                outer.addCircle(cx - r * 0.12f, cy, r, Path.Direction.CW);
                Path cut = new Path();
                cut.addCircle(cx + r * 0.42f, cy - r * 0.28f, r * 0.92f, Path.Direction.CW);
                outer.op(cut, Path.Op.DIFFERENCE);
                cv.drawPath(outer, fill);
                break;
            }
            case DOT: {
                cv.drawCircle(cx, cy, u * 0.14f, fill);
                break;
            }
            case HEADSET: {

                float r = u * 0.28f;
                RectF o2 = new RectF(cx - r, cy - r * 0.95f, cx + r, cy + r * 0.75f);
                cv.drawArc(o2, 190f, 160f, false, stroke);
                float bw = u * 0.11f, bh = u * 0.20f, rr = bw * 0.45f;
                cv.drawRoundRect(new RectF(cx - r - bw * 0.35f, cy - r * 0.05f,
                        cx - r + bw * 0.65f, cy - r * 0.05f + bh), rr, rr, fill);
                cv.drawRoundRect(new RectF(cx + r - bw * 0.65f, cy - r * 0.05f,
                        cx + r + bw * 0.35f, cy - r * 0.05f + bh), rr, rr, fill);
                break;
            }
            case LOGO: {

                float W = u * 0.62f, bw = W / 5f;
                float[] hs = {0.42f, 0.78f, 0.58f};
                float x = cx - W / 2f;
                for (float hh : hs) {
                    float half = u * hh / 2f;
                    cv.drawRoundRect(new RectF(x, cy - half, x + bw, cy + half),
                            bw / 2f, bw / 2f, fill);
                    x += 2 * bw;
                }
                break;
            }
        }
    }

    @Override public void setAlpha(int a) { stroke.setAlpha(a); fill.setAlpha(a); text.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { stroke.setColorFilter(cf); fill.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
