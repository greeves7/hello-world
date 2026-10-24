package io.github.greeves7.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compact glass dropdown under the Inputs pill: HDMI ports, AV, then Live TV. */
final class InputsMenu {
    private static final String[] TV_APPS = {
            "com.mediatek.wwtv.tvcenter", "com.android.tv", "com.google.android.tv", "com.hisense.tv"};

    private static final class Item {
        final String label;
        final String inputId; // null = generic "open the TV app"
        final boolean passthrough;
        final int order;

        Item(String label, String inputId, boolean passthrough, int order) {
            this.label = label;
            this.inputId = inputId;
            this.passthrough = passthrough;
            this.order = order;
        }
    }

    private final Activity activity;
    private final FrameLayout host;
    private final Typeface font;
    private final List<Item> items = new ArrayList<>();
    private final List<TextView> rows = new ArrayList<>();
    private LinearLayout panel;
    private int index;

    InputsMenu(Activity activity, FrameLayout host, Typeface font) {
        this.activity = activity;
        this.host = host;
        this.font = font;
    }

    boolean isOpen() {
        return panel != null;
    }

    /** Opens the menu with its top-right corner at (right, top) in host coordinates. */
    void open(int right, int top) {
        close();
        loadItems();
        float d = activity.getResources().getDisplayMetrics().density;
        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(8 * d);
        panel.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(18 * d);
        bg.setColor(0xF02A2A2E);
        bg.setStroke(Math.max(1, Math.round(d)), 0x26FFFFFF);
        panel.setBackground(bg);

        rows.clear();
        if (items.isEmpty()) {
            items.add(new Item(activity.getString(R.string.live_tv), null, false, 0));
        }
        for (Item item : items) {
            TextView row = new TextView(activity);
            row.setText(item.label);
            row.setTextColor(0xFFFFFFFF);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            row.setTypeface(font);
            row.setSingleLine(true);
            row.setEllipsize(TextUtils.TruncateAt.END);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Math.round(14 * d), 0, Math.round(14 * d), 0);
            panel.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    Math.round(40 * d)));
            rows.add(row);
        }
        int width = Math.round(240 * d);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = Math.max(0, right - width);
        lp.topMargin = top;
        panel.setAlpha(0f);
        panel.setTranslationY(-8 * d);
        host.addView(panel, lp);
        panel.animate().alpha(1f).translationY(0f).setDuration(150).start();
        index = 0;
        highlight();
    }

    void close() {
        if (panel != null) {
            host.removeView(panel);
            panel = null;
        }
    }

    /** Returns true when the key was used by the menu. */
    boolean onKeyDown(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (index > 0) index--;
                highlight();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (index < rows.size() - 1) index++;
                highlight();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_TV_INPUT:
                close();
                return true;
            default:
                return false;
        }
    }

    void activate() {
        if (index < 0 || index >= items.size()) return;
        Item item = items.get(index);
        close();
        switchTo(item);
    }

    private void highlight() {
        float d = activity.getResources().getDisplayMetrics().density;
        for (int i = 0; i < rows.size(); i++) {
            TextView row = rows.get(i);
            if (i == index) {
                GradientDrawable sel = new GradientDrawable();
                sel.setCornerRadius(11 * d);
                sel.setColor(0x33FFFFFF);
                row.setBackground(sel);
                row.setTextColor(0xFFFFFFFF);
            } else {
                row.setBackground(null);
                row.setTextColor(0xB3FFFFFF);
            }
        }
    }

    private void loadItems() {
        items.clear();
        TvInputManager tim = (TvInputManager) activity.getSystemService(Activity.TV_INPUT_SERVICE);
        if (tim == null) return;
        List<TvInputInfo> inputs;
        try {
            inputs = tim.getTvInputList();
        } catch (RuntimeException e) {
            return;
        }
        boolean haveTuner = false;
        String tunerId = null;
        for (TvInputInfo info : inputs) {
            if (info.getParentId() != null) continue; // HDMI-CEC devices behind a port
            if (info.isHidden(activity)) continue;
            int type = info.getType();
            if (type == TvInputInfo.TYPE_TUNER) {
                if (!haveTuner) tunerId = info.getId();
                haveTuner = true;
                continue;
            }
            CharSequence custom = info.loadCustomLabel(activity);
            CharSequence label = !TextUtils.isEmpty(custom) ? custom : info.loadLabel(activity);
            String text = label == null ? info.getId() : label.toString();
            int order;
            switch (type) {
                case TvInputInfo.TYPE_HDMI:
                    order = 100;
                    break;
                case TvInputInfo.TYPE_COMPOSITE:
                case TvInputInfo.TYPE_COMPONENT:
                case TvInputInfo.TYPE_SVIDEO:
                case TvInputInfo.TYPE_SCART:
                case TvInputInfo.TYPE_VGA:
                    order = 200;
                    break;
                default:
                    order = 300;
                    break;
            }
            items.add(new Item(text, info.getId(), info.isPassthroughInput(), order));
        }
        Collections.sort(items, (a, b) -> a.order != b.order
                ? Integer.compare(a.order, b.order) : a.label.compareToIgnoreCase(b.label));
        items.add(new Item(activity.getString(R.string.live_tv), tunerId, false, 400));
    }

    private void switchTo(Item item) {
        if (item.inputId != null && item.passthrough) {
            if (tryStart(new Intent(Intent.ACTION_VIEW,
                    TvContract.buildChannelUriForPassthroughInput(item.inputId)))) return;
        }
        if (item.inputId != null && !item.passthrough && !item.label.equals(activity.getString(R.string.live_tv))) {
            // A non-passthrough input from a TV app (an IPTV or tuner-like service).
            if (tryStart(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(item.inputId)))) return;
        }
        // Live TV: the channel list, handled by whichever app plays the tuner.
        if (tryStart(new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI))) return;
        // Last resort: launch the TV app itself. Find it by who handles a passthrough URI.
        String tvApp = null;
        ResolveInfo ri = activity.getPackageManager().resolveActivity(new Intent(Intent.ACTION_VIEW,
                TvContract.buildChannelUriForPassthroughInput("probe")), 0);
        if (ri != null && ri.activityInfo != null) tvApp = ri.activityInfo.packageName;
        if (tvApp != null && launchPackage(tvApp)) return;
        for (String pkg : TV_APPS) {
            if (launchPackage(pkg)) return;
        }
        Toast.makeText(activity, R.string.no_inputs, Toast.LENGTH_SHORT).show();
    }

    private boolean launchPackage(String pkg) {
        Intent i = activity.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = activity.getPackageManager().getLaunchIntentForPackage(pkg);
        return i != null && tryStart(i);
    }

    private boolean tryStart(Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
