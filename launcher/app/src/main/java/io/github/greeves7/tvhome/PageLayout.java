package io.github.greeves7.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

/**
 * The scrolling page. Children are placed with absolute margins; on top of them it draws the
 * single glass highlight that springs from whatever has focus to the next thing.
 */
final class PageLayout extends FrameLayout {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF from = new RectF(), to = new RectF(), now = new RectF();
    private float fromRadius, toRadius, nowRadius;
    private boolean visible;
    private ValueAnimator anim;

    PageLayout(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        float d = getResources().getDisplayMetrics().density;
        fill.setColor(0x1FFFFFFF);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.5f * d);
        stroke.setColor(0x73FFFFFF);
    }

    void moveHighlight(RectF target, float radius, boolean animate) {
        if (anim != null) anim.cancel();
        if (!visible || !animate) {
            now.set(target);
            nowRadius = radius;
            visible = true;
            invalidate();
            return;
        }
        from.set(now);
        fromRadius = nowRadius;
        to.set(target);
        toRadius = radius;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(320);
        anim.setInterpolator(new OvershootInterpolator(1.3f));
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            now.set(lerp(from.left, to.left, t), lerp(from.top, to.top, t),
                    lerp(from.right, to.right, t), lerp(from.bottom, to.bottom, t));
            nowRadius = Math.max(0f, lerp(fromRadius, toRadius, t));
            invalidate();
        });
        anim.start();
    }

    void hideHighlight() {
        if (anim != null) anim.cancel();
        visible = false;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!visible || now.isEmpty()) return;
        canvas.drawRoundRect(now, nowRadius, nowRadius, fill);
        sheen.setShader(new LinearGradient(0, now.top, 0, now.top + now.height() * 0.55f,
                0x47FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(now, nowRadius, nowRadius, sheen);
        canvas.drawRoundRect(now, nowRadius, nowRadius, stroke);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
