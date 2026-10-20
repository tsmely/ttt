package com.qingping.screen.core;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 已安装应用清单（只收录有启动入口的应用），用于应用列表与统计展示。
 */
public final class AppInventory {

    private AppInventory() {
    }

    public static final class AppEntry {
        public final String pkg;
        public final String label;
        public final Drawable icon;

        public AppEntry(String pkg, String label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    private static volatile List<AppEntry> cached = Collections.emptyList();
    private static final Map<String, String> LABELS = new HashMap<>();
    private static final Map<String, Drawable> ICONS = new HashMap<>();

    public static List<AppEntry> cached() {
        return cached;
    }

    public static List<AppEntry> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);

        Map<String, ResolveInfo> unique = new LinkedHashMap<>();
        for (ResolveInfo ri : resolveInfos) {
            if (ri.activityInfo == null || ri.activityInfo.packageName == null) {
                continue;
            }
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(context.getPackageName())) {
                continue;
            }
            if (!unique.containsKey(pkg)) {
                unique.put(pkg, ri);
            }
        }

        List<AppEntry> out = new ArrayList<>(unique.size());
        for (Map.Entry<String, ResolveInfo> entry : unique.entrySet()) {
            String pkg = entry.getKey();
            ResolveInfo ri = entry.getValue();
            CharSequence labelChar = ri.loadLabel(pm);
            String label = labelChar == null ? pkg : labelChar.toString();
            Drawable icon;
            try {
                icon = ri.loadIcon(pm);
            } catch (Throwable t) {
                icon = pm.getDefaultActivityIcon();
            }
            out.add(new AppEntry(pkg, label, icon));
            synchronized (LABELS) {
                LABELS.put(pkg, label);
                ICONS.put(pkg, icon);
            }
        }

        final Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(out, (a, b) -> collator.compare(a.label, b.label));
        cached = out;
        return out;
    }

    public static String labelOf(Context context, String pkg) {
        if (pkg == null) {
            return "";
        }
        synchronized (LABELS) {
            String cachedLabel = LABELS.get(pkg);
            if (cachedLabel != null) {
                return cachedLabel;
            }
        }
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            String label = pm.getApplicationLabel(info).toString();
            synchronized (LABELS) {
                LABELS.put(pkg, label);
            }
            return label;
        } catch (Throwable t) {
            return pkg;
        }
    }

    public static Drawable iconOf(Context context, String pkg) {
        if (pkg == null) {
            return null;
        }
        synchronized (LABELS) {
            Drawable cachedIcon = ICONS.get(pkg);
            if (cachedIcon != null) {
                return cachedIcon;
            }
        }
        try {
            Drawable icon = context.getPackageManager().getApplicationIcon(pkg);
            synchronized (LABELS) {
                ICONS.put(pkg, icon);
            }
            return icon;
        } catch (Throwable t) {
            return null;
        }
    }
}
