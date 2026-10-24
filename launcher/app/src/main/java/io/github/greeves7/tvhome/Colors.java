package io.github.greeves7.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

/** Samples a glow colour from an app banner: the most vivid pixels of a tiny thumbnail. */
final class Colors {
    static final int NEUTRAL = 0xFF8FA2C8;

    static int accentOf(Drawable d) {
        if (d == null) return NEUTRAL;
        final int w = 32, h = 18;
        Bitmap bmp;
        try {
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            Drawable copy = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
            copy.setBounds(0, 0, w, h);
            copy.draw(c);
        } catch (RuntimeException e) {
            return NEUTRAL;
        }
        float[] hsv = new float[3];
        float bestScore = 0f;
        float sumR = 0, sumG = 0, sumB = 0, weight = 0;
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        bmp.recycle();
        for (int p : px) {
            if (Color.alpha(p) < 128) continue;
            Color.colorToHSV(p, hsv);
            float score = hsv[1] * hsv[2];
            if (hsv[2] < 0.2f) continue;
            if (score > bestScore) bestScore = score;
        }
        if (bestScore < 0.12f) return NEUTRAL; // greyscale banner
        for (int p : px) {
            if (Color.alpha(p) < 128) continue;
            Color.colorToHSV(p, hsv);
            float score = hsv[1] * hsv[2];
            if (score < bestScore * 0.7f) continue;
            sumR += Color.red(p) * score;
            sumG += Color.green(p) * score;
            sumB += Color.blue(p) * score;
            weight += score;
        }
        int avg = Color.rgb((int) (sumR / weight), (int) (sumG / weight), (int) (sumB / weight));
        Color.colorToHSV(avg, hsv);
        hsv[1] = Math.max(0.45f, Math.min(0.85f, hsv[1]));
        hsv[2] = 0.95f;
        return Color.HSVToColor(hsv);
    }

    static int darken(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= factor;
        return Color.HSVToColor(hsv);
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private Colors() {
    }
}
