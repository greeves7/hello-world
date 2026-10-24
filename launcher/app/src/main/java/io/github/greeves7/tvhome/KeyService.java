package io.github.greeves7.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional helper for remote buttons that the stock launcher used to answer (Input, Settings,
 * app buttons). Off by default; tools/keys.sh turns it on.
 *
 * Trade-off: while any key-filtering service is bound, some firmware delivers held keys as single
 * presses (a held volume button steps once). Only enable it if a button you need does nothing.
 */
public class KeyService extends AccessibilityService {
    private Config config;
    private int consumedKey = -1;

    @Override
    protected void onServiceConnected() {
        config = new Config(this);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (config == null) return false;
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_UP) {
            // Swallow the release of a key we handled, nothing else.
            if (code == consumedKey) {
                consumedKey = -1;
                return true;
            }
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (event.getRepeatCount() > 0) return code == consumedKey;

        Intent intent = null;
        if (config.inputKeycodes.contains(code)) {
            intent = new Intent(this, HomeActivity.class).putExtra(HomeActivity.EXTRA_OPEN_INPUTS, true);
        } else if (config.settingsKeycodes.contains(code) || config.settingsScancodes.contains(event.getScanCode())) {
            intent = new Intent(Settings.ACTION_SETTINGS);
        } else if (config.appKeys.containsKey(code)) {
            String pkg = config.appKeys.get(code);
            intent = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(pkg);
        }
        if (intent == null) return false;
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
        consumedKey = code;
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
