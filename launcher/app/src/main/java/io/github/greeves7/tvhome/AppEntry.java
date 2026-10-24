package io.github.greeves7.tvhome;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;

/** One launchable app. Drawables and the accent colour are loaded off the main thread. */
final class AppEntry {
    final String packageName;
    final ComponentName component;
    final String label;
    final Drawable banner; // null when the app has no TV banner
    final Drawable icon;
    final int accent;
    final boolean leanback;

    AppEntry(ComponentName component, String label, Drawable banner, Drawable icon, int accent, boolean leanback) {
        this.packageName = component.getPackageName();
        this.component = component;
        this.label = label;
        this.banner = banner;
        this.icon = icon;
        this.accent = accent;
        this.leanback = leanback;
    }

    String key() {
        return component.flattenToShortString();
    }

    Intent launchIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }
}
