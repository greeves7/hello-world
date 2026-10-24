package io.github.greeves7.tvhome;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** A rounded app card: the banner, or the icon on a tinted card when there is no banner. */
final class TileView extends FrameLayout {
    static final float FOCUS_SCALE = 1.08f;

    final AppEntry app;
    /** Name shown under the tile while it has focus; lives in the page, not in the tile. */
    TextView label;

    TileView(Context context, AppEntry app, float radius, Typeface font) {
        super(context);
        this.app = app;
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);

        if (app.banner != null) {
            setBackgroundColor(0xFF000000);
            ImageView banner = new ImageView(context);
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            banner.setImageDrawable(app.banner);
            addView(banner, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } else {
            setBackgroundColor(Colors.darken(app.accent, 0.35f));
            ImageView icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageDrawable(app.icon);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            lp.topMargin = dp(10);
            lp.bottomMargin = dp(26);
            addView(icon, lp);
            TextView name = new TextView(context);
            name.setText(app.label);
            name.setTextColor(0xE6FFFFFF);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            name.setTypeface(font);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            LayoutParams nlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM);
            nlp.bottomMargin = dp(6);
            nlp.leftMargin = nlp.rightMargin = dp(6);
            addView(name, nlp);
        }
    }

    void setFocusedState(boolean focused, boolean animate) {
        float s = focused ? FOCUS_SCALE : 1f;
        float a = focused ? 1f : 0f;
        if (animate) {
            animate().scaleX(s).scaleY(s).setDuration(180).start();
            if (label != null) label.animate().alpha(a).setDuration(180).start();
        } else {
            setScaleX(s);
            setScaleY(s);
            if (label != null) label.setAlpha(a);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
