package io.github.greeves7.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lists launchable apps and splits them into dock and grid. Call off the main thread. */
final class AppRepository {

    static final class Apps {
        final List<AppEntry> dock;
        final List<AppEntry> grid;

        Apps(List<AppEntry> dock, List<AppEntry> grid) {
            this.dock = dock;
            this.grid = grid;
        }
    }

    static Apps load(Context context, Config config) {
        PackageManager pm = context.getPackageManager();
        String self = context.getPackageName();
        Map<String, AppEntry> byPackage = new LinkedHashMap<>();

        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, self, config, byPackage);
        if (config.includePhoneApps) {
            collect(pm, Intent.CATEGORY_LAUNCHER, false, self, config, byPackage);
        }

        List<AppEntry> all = new ArrayList<>(byPackage.values());
        Collator collator = Collator.getInstance();
        Collections.sort(all, (a, b) -> collator.compare(a.label, b.label));

        List<AppEntry> dock = new ArrayList<>();
        for (String pkg : config.dockApps) {
            AppEntry e = byPackage.get(pkg.trim());
            if (e != null && !dock.contains(e) && dock.size() < config.columns) dock.add(e);
        }
        if (dock.isEmpty()) {
            // None of the configured dock apps are installed: use the first apps instead.
            for (int i = 0; i < all.size() && i < config.columns; i++) dock.add(all.get(i));
        }
        List<AppEntry> grid = new ArrayList<>(all);
        grid.removeAll(dock);
        return new Apps(dock, grid);
    }

    private static void collect(PackageManager pm, String category, boolean leanback, String self,
                                Config config, Map<String, AppEntry> out) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> infos = pm.queryIntentActivities(query, 0);
        for (ResolveInfo ri : infos) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(self) || config.hiddenApps.contains(pkg) || out.containsKey(pkg)) continue;
            if (!ri.activityInfo.enabled || !ri.activityInfo.applicationInfo.enabled) continue;
            ComponentName cn = new ComponentName(pkg, ri.activityInfo.name);
            CharSequence label = ri.loadLabel(pm);
            Drawable banner = leanback ? safeBanner(pm, ri) : null;
            Drawable icon = ri.loadIcon(pm);
            int accent = Colors.accentOf(banner != null ? banner : icon);
            out.put(pkg, new AppEntry(cn, label == null ? pkg : label.toString(), banner, icon, accent, leanback));
        }
    }

    private static Drawable safeBanner(PackageManager pm, ResolveInfo ri) {
        try {
            // Falls back to the application banner when the activity has none.
            return ri.activityInfo.loadBanner(pm);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private AppRepository() {
    }
}
