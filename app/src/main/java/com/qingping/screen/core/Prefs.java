package com.qingping.screen.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 全部配置都保存在本机 SharedPreferences 中，不涉及网络与账号。
 */
public final class Prefs {

    public static final int REGION_TOP_RIGHT = 0;
    public static final int REGION_TOP = 1;
    public static final int REGION_FULL = 2;

    public static final int JUMP_OFF = 0;
    public static final int JUMP_STRICT = 1;
    public static final int JUMP_LOOSE = 2;

    private static final String FILE = "qingping";
    private static volatile Prefs instance;

    private final SharedPreferences sp;

    public static Prefs get(Context context) {
        if (instance == null) {
            synchronized (Prefs.class) {
                if (instance == null) {
                    instance = new Prefs(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private Prefs(Context context) {
        sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean enabled() {
        return sp.getBoolean("enabled", true);
    }

    public void setEnabled(boolean value) {
        sp.edit().putBoolean("enabled", value).apply();
    }

    public Set<String> disabledPackages() {
        Set<String> stored = sp.getStringSet("disabled_pkgs", null);
        if (stored == null) {
            return new HashSet<>();
        }
        return new HashSet<>(stored);
    }

    public boolean isGuarded(String pkg) {
        return enabled() && pkg != null && !disabledPackages().contains(pkg);
    }

    public void setGuarded(String pkg, boolean guarded) {
        Set<String> set = disabledPackages();
        if (guarded) {
            set.remove(pkg);
        } else {
            set.add(pkg);
        }
        sp.edit().putStringSet("disabled_pkgs", set).apply();
    }

    public void setAllGuarded(boolean guarded, List<String> allPackages) {
        if (guarded) {
            sp.edit().putStringSet("disabled_pkgs", new HashSet<>()).apply();
            return;
        }
        Set<String> set = new HashSet<>(allPackages);
        sp.edit().putStringSet("disabled_pkgs", set).apply();
    }

    /** 应用进入前台后，继续寻找「跳过」的时长。 */
    public int splashWindowMs() {
        return sp.getInt("splash_window_ms", 6000);
    }

    public void setSplashWindowMs(int ms) {
        sp.edit().putInt("splash_window_ms", ms).apply();
    }

    public int region() {
        return sp.getInt("region", REGION_TOP_RIGHT);
    }

    public void setRegion(int region) {
        sp.edit().putInt("region", region).apply();
    }

    /** 演练模式：只统计、不真的点击。 */
    public boolean dryRun() {
        return sp.getBoolean("dry_run", false);
    }

    public void setDryRun(boolean value) {
        sp.edit().putBoolean("dry_run", value).apply();
    }

    public int jumpMode() {
        return sp.getInt("jump_mode", JUMP_STRICT);
    }

    public void setJumpMode(int mode) {
        sp.edit().putInt("jump_mode", mode).apply();
    }

    /** 识别到广告 SDK 的全屏 Activity 时，自动按返回。 */
    public boolean closeInterstitial() {
        return sp.getBoolean("close_interstitial", true);
    }

    public void setCloseInterstitial(boolean value) {
        sp.edit().putBoolean("close_interstitial", value).apply();
    }

    /**
     * 识别屏幕右下角没有文字的 × 关闭按钮（需要广告上下文）。
     */
    public boolean cornerClose() {
        return sp.getBoolean("corner_close", true);
    }

    public void setCornerClose(boolean value) {
        sp.edit().putBoolean("corner_close", value).apply();
    }

    public boolean vibrate() {
        return sp.getBoolean("vibrate", true);
    }

    public void setVibrate(boolean value) {
        sp.edit().putBoolean("vibrate", value).apply();
    }

    public boolean toast() {
        return sp.getBoolean("toast", true);
    }

    public void setToast(boolean value) {
        sp.edit().putBoolean("toast", value).apply();
    }

    public String keywordsRaw() {
        return sp.getString("keywords", "");
    }

    public void setKeywordsRaw(String value) {
        sp.edit().putString("keywords", value == null ? "" : value).apply();
    }

    public String[] extraKeywords() {
        String raw = keywordsRaw().trim();
        if (raw.isEmpty()) {
            return new String[0];
        }
        String[] parts = raw.split("[,，、;；\\s]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String v = p.trim();
            if (!v.isEmpty() && v.length() <= 12) {
                out.add(v);
            }
        }
        return out.toArray(new String[0]);
    }

    public boolean firstRun() {
        return sp.getBoolean("first_run", true);
    }

    public void setFirstRun(boolean value) {
        sp.edit().putBoolean("first_run", value).apply();
    }

    /** 用户手动确认过厂商自启动设置（系统无法自动检测）。 */
    public boolean autostartConfirmed() {
        return sp.getBoolean("autostart_confirmed", false);
    }

    public void setAutostartConfirmed(boolean value) {
        sp.edit().putBoolean("autostart_confirmed", value).apply();
    }

    /** 服务掉线提醒的节流，避免反复打扰。 */
    public long lastOfflineWarn() {
        return sp.getLong("last_offline_warn", 0L);
    }

    public void markOfflineWarn(long ts) {
        sp.edit().putLong("last_offline_warn", ts).apply();
    }

    public Set<String> emptySet() {
        return Collections.emptySet();
    }
}
