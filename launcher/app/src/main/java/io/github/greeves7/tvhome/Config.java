package io.github.greeves7.tvhome;

import android.content.Context;
import android.content.res.Resources;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads res/values/config.xml. */
final class Config {
    final int columns;
    final List<String> dockApps;
    final Set<String> hiddenApps;
    final boolean includePhoneApps;
    final boolean showGreeting;
    final int wallpaperDimPercent;
    final Set<Integer> inputKeycodes;
    final Set<Integer> settingsKeycodes;
    final Set<Integer> settingsScancodes;
    final Map<Integer, String> appKeys;

    Config(Context context) {
        Resources res = context.getResources();
        columns = clamp(res.getInteger(R.integer.columns), 4, 7);
        dockApps = Arrays.asList(res.getStringArray(R.array.dock_apps));
        hiddenApps = new HashSet<>(Arrays.asList(res.getStringArray(R.array.hidden_apps)));
        includePhoneApps = res.getBoolean(R.bool.include_phone_apps);
        showGreeting = res.getBoolean(R.bool.show_greeting);
        wallpaperDimPercent = clamp(res.getInteger(R.integer.wallpaper_dim_percent), 0, 90);
        inputKeycodes = toSet(res.getIntArray(R.array.input_key_keycodes));
        settingsKeycodes = toSet(res.getIntArray(R.array.settings_key_keycodes));
        settingsScancodes = toSet(res.getIntArray(R.array.settings_key_scancodes));
        appKeys = new HashMap<>();
        for (String entry : res.getStringArray(R.array.app_keys)) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            try {
                appKeys.put(Integer.parseInt(entry.substring(0, eq).trim()), entry.substring(eq + 1).trim());
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static Set<Integer> toSet(int[] values) {
        Set<Integer> out = new HashSet<>();
        for (int v : values) out.add(v);
        return out;
    }
}
