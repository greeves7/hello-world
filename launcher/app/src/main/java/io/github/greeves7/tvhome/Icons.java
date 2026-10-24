package io.github.greeves7.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Small line icons for the top-bar pills, drawn in code so there is nothing to download. */
final class Icons extends Drawable {
    static final int INPUT = 0, WIFI = 1, WIFI_OFF = 2, ETHERNET = 3, SETTINGS = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int type;

    Icons(int type) {
        this.type = type;
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void setType(int type) {
        this.type = type;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        float x = b.left + (b.width() - s) / 2f, y = b.top + (b.height() - s) / 2f;
        paint.setStrokeWidth(s * 0.09f);
        paint.setStyle(Paint.Style.STROKE);
        switch (type) {
            case INPUT: {
                c.drawRoundRect(new RectF(x + s * 0.08f, y + s * 0.18f, x + s * 0.92f, y + s * 0.72f),
                        s * 0.08f, s * 0.08f, paint);
                c.drawLine(x + s * 0.34f, y + s * 0.88f, x + s * 0.66f, y + s * 0.88f, paint);
                c.drawLine(x + s * 0.3f, y + s * 0.45f, x + s * 0.64f, y + s * 0.45f, paint);
                c.drawLine(x + s * 0.52f, y + s * 0.33f, x + s * 0.66f, y + s * 0.45f, paint);
                c.drawLine(x + s * 0.52f, y + s * 0.57f, x + s * 0.66f, y + s * 0.45f, paint);
                break;
            }
            case WIFI:
            case WIFI_OFF: {
                float cx = x + s / 2f, cy = y + s * 0.84f;
                for (float r : new float[]{s * 0.22f, s * 0.42f, s * 0.62f}) {
                    c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), -135, 90, false, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy, s * 0.07f, paint);
                if (type == WIFI_OFF) {
                    paint.setStyle(Paint.Style.STROKE);
                    c.drawLine(x + s * 0.15f, y + s * 0.12f, x + s * 0.85f, y + s * 0.9f, paint);
                }
                break;
            }
            case ETHERNET: {
                c.drawRoundRect(new RectF(x + s * 0.14f, y + s * 0.2f, x + s * 0.86f, y + s * 0.7f),
                        s * 0.06f, s * 0.06f, paint);
                c.drawLine(x + s * 0.38f, y + s * 0.7f, x + s * 0.38f, y + s * 0.88f, paint);
                c.drawLine(x + s * 0.62f, y + s * 0.7f, x + s * 0.62f, y + s * 0.88f, paint);
                c.drawLine(x + s * 0.32f, y + s * 0.36f, x + s * 0.32f, y + s * 0.48f, paint);
                c.drawLine(x + s * 0.5f, y + s * 0.36f, x + s * 0.5f, y + s * 0.48f, paint);
                c.drawLine(x + s * 0.68f, y + s * 0.36f, x + s * 0.68f, y + s * 0.48f, paint);
                break;
            }
            case SETTINGS: {
                float cx = x + s / 2f, cy = y + s / 2f;
                c.drawCircle(cx, cy, s * 0.14f, paint);
                c.drawCircle(cx, cy, s * 0.3f, paint);
                paint.setStrokeWidth(s * 0.13f);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                    c.drawLine(cx + cos * s * 0.36f, cy + sin * s * 0.36f,
                            cx + cos * s * 0.43f, cy + sin * s * 0.43f, paint);
                }
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
