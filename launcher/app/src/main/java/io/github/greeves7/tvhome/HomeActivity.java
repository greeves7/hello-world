package io.github.greeves7.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The home screen. Focus is handled here rather than by Android's focus search, so D-pad moves
 * are deterministic: Up/Down move exactly one row, Up from the dock goes to the top bar, and the
 * ends of each row stop instead of wrapping or falling through.
 */
public class HomeActivity extends Activity {
    static final String EXTRA_OPEN_INPUTS = "io.github.greeves7.tvhome.OPEN_INPUTS";

    private static final int TOP = 0, DOCK = 1, GRID = 2;
    private static final int PILL_INPUTS = 0, PILL_WIFI = 1, PILL_SETTINGS = 2;
    private static final String PREFS = "home", PREF_LAST_TILE = "last_tile";
    private static final int PILL_COLOR = 0x24FFFFFF, PILL_FOCUSED_COLOR = 0x40FFFFFF;
    private static final int RECEIVER_NOT_EXPORTED = 0x4; // Context.RECEIVER_NOT_EXPORTED, API 33

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Runnable reloadRunnable = this::reloadApps;

    private Config config;
    private Typeface thin, light, regular, medium;
    private float density;
    private int screenW, screenH;
    private int marginX, bandPad, gap, tileW, tileH, labelH, tileRadius;
    private int bandTop, bandH, gridTop, rowPitch;

    private FrameLayout root;
    private PageLayout page;
    private TextView greeting, heroTitle;
    private GradientDrawable glowDrawable;
    private int glowColor = Colors.NEUTRAL;
    private ValueAnimator glowAnim;
    private final List<LinearLayout> pills = new ArrayList<>();
    private TextView wifiText;
    private Icons wifiIcon;
    private InputsMenu inputsMenu;

    private final List<TileView> dockTiles = new ArrayList<>();
    private final List<TileView> gridTiles = new ArrayList<>();
    private final List<View> appViews = new ArrayList<>();
    private boolean appsShown;

    private int section = DOCK, topIndex, dockIndex, gridIndex;
    private TileView focusedTile;
    private String lastTileKey;
    private boolean resumed, centerDown, longPressFired, pendingOpenInputs;

    private ConnectivityManager.NetworkCallback netCallback;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Installs often arrive as several broadcasts in a row; reload once.
            handler.removeCallbacks(reloadRunnable);
            handler.postDelayed(reloadRunnable, 600);
        }
    };

    private final BroadcastReceiver tickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateGreeting();
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        config = new Config(this);
        Resources res = getResources();
        density = res.getDisplayMetrics().density;
        thin = res.getFont(R.font.outfit_thin);
        light = res.getFont(R.font.outfit_light);
        regular = res.getFont(R.font.outfit_regular);
        medium = res.getFont(R.font.outfit_medium);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        screenW = Math.max(dm.widthPixels, dm.heightPixels);
        screenH = Math.min(dm.widthPixels, dm.heightPixels);
        computeGeometry();
        buildChrome();
        setContentView(root);
        inputsMenu = new InputsMenu(this, root, regular);

        lastTileKey = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_LAST_TILE, null);
        pendingOpenInputs = getIntent().getBooleanExtra(EXTRA_OPEN_INPUTS, false);

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        register(packageReceiver, pf);
        registerNetworkCallback();

        reloadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        register(tickReceiver, tf);
        updateGreeting();
        updateNetwork();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        try {
            unregisterReceiver(tickReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        inputsMenu.close();
        if (lastTileKey != null) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_LAST_TILE, lastTileKey).apply();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_OPEN_INPUTS, false)) {
            if (appsShown) root.post(this::openInputs);
            else pendingOpenInputs = true;
            return;
        }
        // Home pressed while already on Home: back to the dock. Coming back from an app
        // (not resumed yet) keeps the last focused tile.
        if (resumed && appsShown) {
            inputsMenu.close();
            if (!dockTiles.isEmpty()) focusDock(dockIndex, true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        loader.shutdownNow();
        try {
            unregisterReceiver(packageReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (netCallback != null) {
            try {
                getSystemService(ConnectivityManager.class).unregisterNetworkCallback(netCallback);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void register(BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
    }

    // ---------------------------------------------------------------- layout

    private int dp(float v) {
        return Math.round(v * density);
    }

    private void computeGeometry() {
        int cols = config.columns;
        marginX = dp(64);
        bandPad = dp(24);
        gap = cols >= 6 ? dp(20) : dp(28); // tighter gaps keep 6-7 tiles a usable size
        tileW = (screenW - 2 * marginX - 2 * bandPad - (cols - 1) * gap) / cols;
        tileH = tileW * 9 / 16;
        labelH = dp(30);
        tileRadius = dp(12);
        bandH = dp(20) + tileH + labelH + dp(10);
        int bandBottom = screenH - dp(44);
        bandTop = bandBottom - bandH;
        gridTop = screenH - dp(22); // the first grid row peeks in at the bottom edge
        rowPitch = tileH + labelH + dp(18);
    }

    private int colX(int col) {
        return marginX + bandPad + col * (tileW + gap);
    }

    private static FrameLayout.LayoutParams at(int w, int h, int left, int top) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = left;
        lp.topMargin = top;
        return lp;
    }

    private void buildChrome() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        @SuppressWarnings("DiscouragedApi")
        int wallpaper = getResources().getIdentifier("wallpaper", "drawable", getPackageName());
        if (wallpaper != 0) {
            ImageView wp = new ImageView(this);
            wp.setScaleType(ImageView.ScaleType.CENTER_CROP);
            wp.setImageResource(wallpaper);
            root.addView(wp, new FrameLayout.LayoutParams(-1, -1));
            View dim = new View(this);
            dim.setBackgroundColor(Colors.withAlpha(0xFF000000, config.wallpaperDimPercent * 255 / 100));
            root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        }

        page = new PageLayout(this);
        root.addView(page, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, screenH));

        // Greeting, clock and date, top left.
        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        greeting = text(17, light, 0xB3FFFFFF);
        greeting.setVisibility(config.showGreeting ? View.VISIBLE : View.GONE);
        clockBox.addView(greeting);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 76);
        clock.setTypeface(thin);
        clock.setTextColor(0xFFFFFFFF);
        clock.setIncludeFontPadding(false);
        clock.setLetterSpacing(-0.02f);
        clockBox.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        date.setTypeface(light);
        date.setTextColor(0xA6FFFFFF);
        clockBox.addView(date);
        page.addView(clockBox, at(-2, -2, marginX, dp(34)));

        // Frosted pills, top right: Inputs, Wi-Fi, Settings.
        LinearLayout pillRow = new LinearLayout(this);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pillRow.addView(pill(new Icons(Icons.INPUT), getString(R.string.inputs)));
        wifiIcon = new Icons(Icons.WIFI);
        LinearLayout wifi = pill(wifiIcon, getString(R.string.wifi));
        wifiText = (TextView) wifi.getChildAt(1);
        pillRow.addView(wifi);
        pillRow.addView(pill(new Icons(Icons.SETTINGS), null));
        for (int i = 1; i < pillRow.getChildCount(); i++) {
            ((LinearLayout.LayoutParams) pillRow.getChildAt(i).getLayoutParams()).leftMargin = dp(12);
        }
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-2, -2);
        plp.gravity = Gravity.TOP | Gravity.RIGHT;
        plp.rightMargin = marginX;
        plp.topMargin = dp(40);
        page.addView(pillRow, plp);

        // Ambient glow and the focused dock app's name, just above the dock.
        int glow = dp(300);
        glowDrawable = new GradientDrawable();
        glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowDrawable.setGradientRadius(glow / 2f);
        glowDrawable.setColors(new int[]{Colors.withAlpha(glowColor, 0x55), Colors.withAlpha(glowColor, 0)});
        View heroGlow = new View(this);
        heroGlow.setBackground(glowDrawable);
        heroGlow.setScaleX(2.2f);
        int glowCx = marginX + bandPad + dp(120), glowCy = bandTop - dp(40);
        page.addView(heroGlow, at(glow, glow, glowCx - glow / 2, glowCy - glow / 2));

        heroTitle = text(44, light, 0xFFFFFFFF);
        heroTitle.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        int heroH = dp(64);
        page.addView(heroTitle, at(screenW - 2 * (marginX + bandPad), heroH, marginX + bandPad,
                bandTop - dp(12) - heroH));

        // The dock band. Added before the tiles so they draw on top of it; it hides the lower
        // half of the glow, so the colour seems to rise out of the dock.
        View band = new View(this);
        GradientDrawable bandBg = new GradientDrawable();
        bandBg.setCornerRadius(dp(28));
        bandBg.setColor(0xFF161618);
        bandBg.setStroke(Math.max(1, dp(1)), 0x0FFFFFFF);
        band.setBackground(bandBg);
        page.addView(band, at(screenW - 2 * marginX, bandH, marginX, bandTop));
    }

    private TextView text(float sp, Typeface face, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(face);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout pill(Drawable icon, String label) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        int side = label == null ? dp(11) : dp(16);
        p.setPadding(side, 0, side, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(PILL_COLOR);
        p.setBackground(bg);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        p.addView(iv, new LinearLayout.LayoutParams(dp(18), dp(18)));
        if (label != null) {
            TextView t = text(15, medium, 0xFFFFFFFF);
            t.setText(label);
            t.setMaxWidth(dp(190));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
            tlp.leftMargin = dp(8);
            p.addView(t, tlp);
        }
        p.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(40)));
        pills.add(p);
        return p;
    }

    // ---------------------------------------------------------------- apps

    private void reloadApps() {
        handler.removeCallbacks(reloadRunnable);
        final Context app = getApplicationContext();
        try {
            loader.execute(() -> {
                AppRepository.Apps apps = AppRepository.load(app, config);
                handler.post(() -> {
                    if (!isDestroyed()) showApps(apps);
                });
            });
        } catch (RuntimeException ignored) {
            // executor already shut down
        }
    }

    private void showApps(AppRepository.Apps apps) {
        for (View v : appViews) page.removeView(v);
        appViews.clear();
        dockTiles.clear();
        gridTiles.clear();
        focusedTile = null;

        int dockY = bandTop + dp(20);
        for (int i = 0; i < apps.dock.size(); i++) {
            dockTiles.add(addTile(apps.dock.get(i), colX(i), dockY));
        }
        int cols = config.columns;
        for (int i = 0; i < apps.grid.size(); i++) {
            gridTiles.add(addTile(apps.grid.get(i), colX(i % cols), gridTop + (i / cols) * rowPitch));
        }
        int rows = (apps.grid.size() + cols - 1) / cols;
        page.getLayoutParams().height = Math.max(screenH, gridTop + rows * rowPitch + screenH);
        page.requestLayout();
        appsShown = true;

        // Restore the remembered tile (by app, since indexes shift when apps come and go).
        int restoreSection = -1, restoreIndex = 0;
        for (int i = 0; i < dockTiles.size() && restoreSection < 0; i++) {
            if (dockTiles.get(i).app.key().equals(lastTileKey)) {
                restoreSection = DOCK;
                restoreIndex = i;
            }
        }
        for (int i = 0; i < gridTiles.size() && restoreSection < 0; i++) {
            if (gridTiles.get(i).app.key().equals(lastTileKey)) {
                restoreSection = GRID;
                restoreIndex = i;
            }
        }
        dockIndex = Math.max(0, Math.min(dockIndex, dockTiles.size() - 1));
        gridIndex = Math.max(0, Math.min(gridIndex, gridTiles.size() - 1));
        if (restoreSection == DOCK) dockIndex = restoreIndex;
        if (restoreSection == GRID) gridIndex = restoreIndex;
        if (!dockTiles.isEmpty()) setHero(dockTiles.get(dockIndex).app, false);
        else heroTitle.setText(gridTiles.isEmpty() ? getString(R.string.no_apps) : "");

        if (section == TOP) {
            root.post(() -> focusTop(topIndex, false));
        } else if (restoreSection == GRID || (section == GRID && !gridTiles.isEmpty())) {
            focusGrid(gridIndex, false);
        } else if (!dockTiles.isEmpty()) {
            focusDock(dockIndex, false);
        } else if (!gridTiles.isEmpty()) {
            focusGrid(gridIndex, false);
        } else {
            root.post(() -> focusTop(PILL_INPUTS, false));
        }

        if (pendingOpenInputs) {
            pendingOpenInputs = false;
            root.post(this::openInputs);
        }
    }

    private TileView addTile(AppEntry app, int x, int y) {
        TileView t = new TileView(this, app, tileRadius, regular);
        page.addView(t, at(tileW, tileH, x, y));
        TextView label = text(14, medium, 0xE6FFFFFF);
        label.setText(app.label);
        label.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        label.setAlpha(0f);
        int grow = Math.round(tileH * (TileView.FOCUS_SCALE - 1f) / 2f);
        page.addView(label, at(tileW + gap, labelH, x - gap / 2, y + tileH + grow + dp(6)));
        t.label = label;
        appViews.add(t);
        appViews.add(label);
        return t;
    }

    // ---------------------------------------------------------------- focus

    private void focusDock(int i, boolean animate) {
        section = DOCK;
        dockIndex = i;
        setPillFocus(-1);
        focusTile(dockTiles.get(i), animate);
        setHero(dockTiles.get(i).app, animate);
        heroTitle.animate().alpha(1f).setDuration(150).start();
        scrollTo(0, animate);
    }

    private void focusGrid(int i, boolean animate) {
        section = GRID;
        gridIndex = i;
        setPillFocus(-1);
        focusTile(gridTiles.get(i), animate);
        int row = i / config.columns;
        scrollTo(-(gridTop + row * rowPitch - dp(84)), animate);
    }

    private void focusTop(int i, boolean animate) {
        section = TOP;
        topIndex = i;
        if (focusedTile != null) {
            focusedTile.setFocusedState(false, animate);
            focusedTile = null;
        }
        setPillFocus(i);
        heroTitle.animate().alpha(0.55f).setDuration(150).start();
        scrollTo(0, animate);
        LinearLayout p = pills.get(i);
        if (p.getWidth() == 0) {
            page.hideHighlight();
            return;
        }
        Rect r = new Rect(0, 0, p.getWidth(), p.getHeight());
        page.offsetDescendantRectToMyCoords(p, r);
        page.moveHighlight(new RectF(r), r.height() / 2f, animate);
    }

    private void focusTile(TileView t, boolean animate) {
        if (focusedTile != null && focusedTile != t) focusedTile.setFocusedState(false, animate);
        focusedTile = t;
        lastTileKey = t.app.key();
        t.setFocusedState(true, animate);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) t.getLayoutParams();
        float dw = tileW * (TileView.FOCUS_SCALE - 1f) / 2f, dh = tileH * (TileView.FOCUS_SCALE - 1f) / 2f;
        page.moveHighlight(new RectF(lp.leftMargin - dw, lp.topMargin - dh,
                lp.leftMargin + tileW + dw, lp.topMargin + tileH + dh), tileRadius * TileView.FOCUS_SCALE, animate);
    }

    private void setPillFocus(int focused) {
        for (int i = 0; i < pills.size(); i++) {
            ((GradientDrawable) pills.get(i).getBackground()).setColor(i == focused ? PILL_FOCUSED_COLOR : PILL_COLOR);
        }
    }

    private void scrollTo(float y, boolean animate) {
        float target = Math.min(0f, y);
        page.animate().cancel();
        if (animate) {
            page.animate().translationY(target).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
        } else {
            page.setTranslationY(target);
        }
    }

    private void setHero(AppEntry app, boolean animate) {
        heroTitle.setText(app.label);
        int target = app.accent;
        if (glowAnim != null) glowAnim.cancel();
        if (!animate) {
            applyGlow(target);
            return;
        }
        glowAnim = ValueAnimator.ofArgb(glowColor, target);
        glowAnim.setDuration(300);
        glowAnim.addUpdateListener(a -> applyGlow((int) a.getAnimatedValue()));
        glowAnim.start();
    }

    private void applyGlow(int color) {
        glowColor = color;
        glowDrawable.setColors(new int[]{Colors.withAlpha(color, 0x55), Colors.withAlpha(color, 0)});
        heroTitle.setShadowLayer(dp(16), 0, 0, Colors.withAlpha(color, 0x8C));
    }

    // ---------------------------------------------------------------- keys

    private static boolean isCenter(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
    }

    private static boolean isDirection(int code) {
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;

        if (inputsMenu.isOpen()) {
            if (isCenter(code)) {
                if (down && e.getRepeatCount() == 0) centerDown = true;
                if (!down && centerDown) {
                    centerDown = false;
                    inputsMenu.activate();
                }
                return true;
            }
            if (isDirection(code) || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_TV_INPUT) {
                if (down) inputsMenu.onKeyDown(code);
                return true;
            }
            return super.dispatchKeyEvent(e);
        }

        if (isDirection(code)) {
            if (down) move(code);
            return true;
        }
        if (isCenter(code)) {
            if (down) {
                if (e.getRepeatCount() == 0) {
                    centerDown = true;
                    longPressFired = false;
                } else if (centerDown && !longPressFired && e.isLongPress()) {
                    longPressFired = true;
                    onLongPress();
                }
            } else {
                if (centerDown && !longPressFired) activate();
                centerDown = false;
            }
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_BACK:
                // Home has nowhere to go back to: return to the dock instead.
                if (!down && section != DOCK && !dockTiles.isEmpty()) focusDock(dockIndex, true);
                return true;
            case KeyEvent.KEYCODE_TV_INPUT:
                if (down && e.getRepeatCount() == 0) openInputs();
                return true;
            case KeyEvent.KEYCODE_SETTINGS:
                if (down && e.getRepeatCount() == 0) startSafely(new Intent(Settings.ACTION_SETTINGS));
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    private void move(int key) {
        int cols = config.columns;
        switch (section) {
            case TOP:
                if (key == KeyEvent.KEYCODE_DPAD_LEFT && topIndex > 0) focusTop(topIndex - 1, true);
                else if (key == KeyEvent.KEYCODE_DPAD_RIGHT && topIndex < pills.size() - 1) focusTop(topIndex + 1, true);
                else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (!dockTiles.isEmpty()) focusDock(dockIndex, true);
                    else if (!gridTiles.isEmpty()) focusGrid(Math.min(gridIndex, gridTiles.size() - 1), true);
                }
                break;
            case DOCK:
                if (key == KeyEvent.KEYCODE_DPAD_LEFT && dockIndex > 0) focusDock(dockIndex - 1, true);
                else if (key == KeyEvent.KEYCODE_DPAD_RIGHT && dockIndex < dockTiles.size() - 1) focusDock(dockIndex + 1, true);
                else if (key == KeyEvent.KEYCODE_DPAD_UP) focusTop(topIndex, true);
                else if (key == KeyEvent.KEYCODE_DPAD_DOWN && !gridTiles.isEmpty()) {
                    focusGrid(Math.min(dockIndex, Math.min(cols, gridTiles.size()) - 1), true);
                }
                break;
            case GRID: {
                int row = gridIndex / cols, col = gridIndex % cols;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT && col > 0) focusGrid(gridIndex - 1, true);
                else if (key == KeyEvent.KEYCODE_DPAD_RIGHT && col < cols - 1 && gridIndex + 1 < gridTiles.size()) {
                    focusGrid(gridIndex + 1, true);
                } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    if (row > 0) focusGrid(gridIndex - cols, true);
                    else if (!dockTiles.isEmpty()) focusDock(Math.min(col, dockTiles.size() - 1), true);
                    else focusTop(topIndex, true);
                } else if (key == KeyEvent.KEYCODE_DPAD_DOWN && (row + 1) * cols < gridTiles.size()) {
                    focusGrid(Math.min(gridIndex + cols, gridTiles.size() - 1), true);
                }
                break;
            }
            default:
                break;
        }
    }

    private void activate() {
        if (section == TOP) {
            switch (topIndex) {
                case PILL_INPUTS:
                    openInputs();
                    break;
                case PILL_WIFI:
                    if (!startSafely(new Intent(Settings.ACTION_WIFI_SETTINGS))) {
                        startSafely(new Intent(Settings.ACTION_SETTINGS));
                    }
                    break;
                case PILL_SETTINGS:
                    startSafely(new Intent(Settings.ACTION_SETTINGS));
                    break;
                default:
                    break;
            }
            return;
        }
        if (focusedTile == null) return;
        if (!startSafely(focusedTile.app.launchIntent())) {
            Toast.makeText(this, focusedTile.app.label, Toast.LENGTH_SHORT).show();
            reloadApps();
        }
    }

    /** Long-press OK on a tile opens its App info page (to disable, clear data or uninstall). */
    private void onLongPress() {
        if (section == TOP || focusedTile == null) return;
        startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", focusedTile.app.packageName, null)));
    }

    private void openInputs() {
        if (inputsMenu.isOpen()) return;
        focusTop(PILL_INPUTS, true);
        LinearLayout p = pills.get(PILL_INPUTS);
        Rect r = new Rect(0, 0, p.getWidth(), p.getHeight());
        page.offsetDescendantRectToMyCoords(p, r);
        // The page is scrolling back to 0, so layout coordinates are where the pill will be.
        inputsMenu.open(r.right, r.bottom + dp(10));
    }

    private boolean startSafely(Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- status

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res = hour >= 5 && hour < 12 ? R.string.good_morning
                : hour >= 12 && hour < 18 ? R.string.good_afternoon : R.string.good_evening;
        greeting.setText(res);
    }

    private void registerNetworkCallback() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null) return;
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                handler.post(HomeActivity.this::updateNetwork);
            }

            @Override
            public void onLost(Network network) {
                handler.post(HomeActivity.this::updateNetwork);
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                handler.post(HomeActivity.this::updateNetwork);
            }
        };
        try {
            cm.registerDefaultNetworkCallback(netCallback);
        } catch (RuntimeException e) {
            netCallback = null;
        }
    }

    @SuppressWarnings("deprecation")
    private void updateNetwork() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network n = cm == null ? null : cm.getActiveNetwork();
        NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
        String label;
        int icon;
        if (nc == null) {
            label = getString(R.string.offline);
            icon = Icons.WIFI_OFF;
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            label = getString(R.string.ethernet);
            icon = Icons.ETHERNET;
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            icon = Icons.WIFI;
            label = getString(R.string.wifi);
            WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
            WifiInfo info = wm == null ? null : wm.getConnectionInfo();
            String ssid = info == null ? null : info.getSSID();
            if (ssid != null) {
                if (ssid.length() > 1 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid = ssid.substring(1, ssid.length() - 1);
                }
                // Without the location permission Android reports "<unknown ssid>".
                if (!ssid.isEmpty() && !ssid.equals("<unknown ssid>")) label = ssid;
            }
        } else {
            label = getString(R.string.connected);
            icon = Icons.WIFI;
        }
        wifiIcon.setType(icon);
        if (!label.contentEquals(wifiText.getText())) {
            wifiText.setText(label);
            // The pill changes width; move the highlight once it has been laid out again.
            if (section == TOP) root.post(() -> {
                if (section == TOP && !inputsMenu.isOpen()) focusTop(topIndex, false);
            });
        }
    }
}
