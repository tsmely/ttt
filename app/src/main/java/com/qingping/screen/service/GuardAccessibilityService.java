package com.qingping.screen.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.qingping.screen.core.AppInventory;
import com.qingping.screen.core.JumpPolicy;
import com.qingping.screen.core.Prefs;
import com.qingping.screen.core.SkipRules;
import com.qingping.screen.core.StatsDb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 核心无障碍服务：
 * 1) 应用进入前台后的一段窗口期内，在屏幕指定区域寻找「跳过」按钮并点击；
 * 2) 识别广告 SDK 的全屏 Activity，自动返回；
 * 3) 开屏阶段被带到浏览器/应用商店时，自动返回，记为一次「误触跳转」。
 *
 * 只读取界面文字与控件 id，不读取输入内容，不做任何网络请求。
 */
public class GuardAccessibilityService extends AccessibilityService {

    public static final String TAG = "QingPing";

    public static final String KIND_SPLASH = "splash";
    public static final String KIND_JUMP = "jump";
    public static final String KIND_INTERSTITIAL = "interstitial";

    private static volatile GuardAccessibilityService instance;

    public static boolean isConnected() {
        return instance != null;
    }

    public static GuardAccessibilityService getInstance() {
        return instance;
    }

    private Prefs prefs;
    private StatsDb db;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String fgPkg;
    private String priorPkg;
    private long fgSince;
    private long lastScanTs;
    private int scanCount;
    private String clickedPkg;
    private int clickCount;
    private long lastClickTs;
    private long lastJumpTs;
    private long lastBackTs;
    private int screenW;
    private int screenH;
    private boolean screenInteractive = true;

    private final Set<String> homePackages = new HashSet<>();
    private final Set<String> imePackages = new HashSet<>();

    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            maybeScan();
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? null : intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                screenInteractive = false;
                fgPkg = null;
                priorPkg = null;
                handler.removeCallbacks(scanRunnable);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                screenInteractive = true;
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                screenInteractive = true;
                fgPkg = null;
                priorPkg = null;
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        prefs = Prefs.get(this);
        db = StatsDb.get(this);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        collectHomePackages();
        collectImePackages();

        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            registerReceiver(screenReceiver, filter);
        } catch (Throwable t) {
            Log.w(TAG, "register screen receiver failed", t);
        }

        GuardForegroundService.startSafe(this);
        handler.postDelayed(() -> GuardForegroundService.refresh(this), 900);

        Log.i(TAG, "accessibility service connected, screen=" + screenW + "x" + screenH);
        handler.postDelayed(scanRunnable, 800);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "accessibility service destroyed");
        instance = null;
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(screenReceiver);
        } catch (Throwable ignored) {
        }
        GuardForegroundService.refresh(this);
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {
        // 系统要求实现，无需处理
    }

    private void collectHomePackages() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent,
                    PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo ri : list) {
                if (ri.activityInfo != null) {
                    homePackages.add(ri.activityInfo.packageName);
                }
            }
        } catch (Throwable ignored) {
        }
        homePackages.add("com.android.launcher");
        homePackages.add("com.android.launcher3");
        homePackages.add("com.miui.home");
        homePackages.add("com.huawei.android.launcher");
        homePackages.add("com.oppo.launcher");
        homePackages.add("com.bbk.launcher2");
        homePackages.add("com.sec.android.app.launcher");
    }

    private void collectImePackages() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                    imePackages.add(info.getPackageName());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || prefs == null) {
            return;
        }
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }

        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            maybeScan();
            return;
        }

        CharSequence pkgChar = event.getPackageName();
        if (pkgChar == null) {
            return;
        }
        String pkg = pkgChar.toString();

        if (pkg.equals(getPackageName()) || homePackages.contains(pkg)) {
            // 回到桌面 / 进入清屏自身时，结束当前应用的守护窗口，
            // 避免把「桌面 -> 应用」误判成应用之间的跳转。
            fgPkg = null;
            priorPkg = null;
            handler.removeCallbacks(scanRunnable);
            return;
        }
        if (pkg.equals("com.android.systemui") || pkg.equals("android")
                || imePackages.contains(pkg)) {
            // 状态栏、系统弹窗、输入法只是临时盖在上面，不打断当前应用的守护窗口。
            return;
        }

        long now = SystemClock.elapsedRealtime();

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence clsChar = event.getClassName();
            String className = clsChar == null ? "" : clsChar.toString();
            handleWindowChanged(pkg, className, now);
        }

        if (pkg.equals(fgPkg)) {
            maybeScan();
        }
    }

    private void handleWindowChanged(String pkg, String className, long now) {
        if (!pkg.equals(fgPkg)) {
            String previous = fgPkg;
            String priorBeforePrevious = priorPkg;
            long sincePreviousLaunch = fgSince > 0 ? now - fgSince : -1L;

            fgPkg = pkg;
            priorPkg = previous;
            fgSince = now;
            scanCount = 0;
            clickedPkg = null;
            clickCount = 0;

            // 如果目标正是刚刚所在的应用（例如 浏览器 -> 应用 -> 浏览器），
            // 且切换发生在 4 秒内，说明用户是在返回，不是误触，跳过。
            boolean returning = previous != null && pkg.equals(priorBeforePrevious)
                    && sincePreviousLaunch >= 0 && sincePreviousLaunch < 4000;

            if (!returning && prefs.enabled() && previous != null
                    && JumpPolicy.shouldBlock(previous, pkg, sincePreviousLaunch,
                    prefs.jumpMode(), prefs.isGuarded(previous))
                    && now - lastJumpTs > 1500) {
                lastJumpTs = now;
                handler.postDelayed(() -> jumpBack(previous, pkg), 260);
            }

            if (prefs.enabled() && prefs.isGuarded(pkg)) {
                scheduleProbes();
            }
        }

        if (prefs.enabled() && prefs.closeInterstitial()
                && SkipRules.isAdSdkClass(className) && prefs.isGuarded(pkg)) {
            closeInterstitial(pkg, className);
        }
    }

    private void scheduleProbes() {
        handler.removeCallbacks(scanRunnable);
        handler.postDelayed(scanRunnable, 120);
        handler.postDelayed(scanRunnable, 480);
        handler.postDelayed(scanRunnable, 1100);
        handler.postDelayed(scanRunnable, 2200);
        handler.postDelayed(scanRunnable, 3800);
    }

    private void maybeScan() {
        if (!prefs.enabled() || !screenInteractive || fgPkg == null) {
            return;
        }
        if (!prefs.isGuarded(fgPkg)) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - fgSince > prefs.splashWindowMs()) {
            return;
        }
        if (now - lastScanTs < 120 || scanCount > 120) {
            return;
        }
        if (clickedPkg != null && clickedPkg.equals(fgPkg)) {
            if (clickCount >= 2 && now - lastClickTs < 6000) {
                return;
            }
            if (clickCount >= 1 && now - lastClickTs < 1000) {
                return;
            }
        }
        lastScanTs = now;
        scanCount++;
        scan(fgPkg);
    }

    private void scan(String pkg) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }
        List<AccessibilityNodeInfo> hits = new ArrayList<>(4);
        collectSkipNodes(root, hits, 0, new int[]{0});

        for (AccessibilityNodeInfo node : hits) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                continue;
            }
            if (!inRegion(bounds)) {
                continue;
            }
            if (bounds.width() > screenW * 0.86f && bounds.height() > screenH * 0.55f) {
                // 整屏广告容器，不是按钮
                continue;
            }
            if (node.isEditable()) {
                continue;
            }

            String label = labelOf(node);

            if (prefs.dryRun()) {
                clickedPkg = pkg;
                clickCount++;
                lastClickTs = SystemClock.elapsedRealtime();
                Log.i(TAG, "dry-run candidate pkg=" + pkg + " label=" + label);
                showToast("演练：本可跳过「" + AppInventory.labelOf(this, pkg)
                        + "」的「" + label + "」");
                break;
            }

            boolean clicked = false;
            if (node.isClickable()) {
                clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            if (!clicked) {
                clicked = tap(bounds.centerX(), bounds.centerY());
            }
            if (clicked) {
                onBlocked(pkg, label);
                break;
            }
        }
    }

    private void collectSkipNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out,
                                  int depth, int[] visited) {
        if (node == null || depth > 40 || out.size() >= 8 || visited[0] > 800) {
            return;
        }
        visited[0]++;
        if (!node.isVisibleToUser()) {
            return;
        }

        String text = null;
        CharSequence t = node.getText();
        if (t != null) {
            text = t.toString();
        }
        String desc = null;
        CharSequence d = node.getContentDescription();
        if (d != null) {
            desc = d.toString();
        }

        boolean hit = SkipRules.isSkipLabel(text, prefs.extraKeywords())
                || SkipRules.isSkipLabel(desc, prefs.extraKeywords());

        if (!hit) {
            try {
                String viewId = node.getViewIdResourceName();
                if (viewId != null && SkipRules.isSkipViewId(viewId)) {
                    hit = true;
                }
            } catch (Throwable ignored) {
            }
        }

        if (hit) {
            out.add(node);
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                if (child != null) {
                    collectSkipNodes(child, out, depth + 1, visited);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean inRegion(Rect r) {
        int cx = r.centerX();
        int cy = r.centerY();
        switch (prefs.region()) {
            case Prefs.REGION_TOP_RIGHT:
                return cx > screenW * 0.55f && cy < screenH * 0.32f;
            case Prefs.REGION_TOP:
                return cy < screenH * 0.40f;
            case Prefs.REGION_FULL:
            default:
                return true;
        }
    }

    private boolean tap(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, 60);
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(stroke)
                    .build();
            return dispatchGesture(gesture, null, null);
        } catch (Throwable t) {
            Log.w(TAG, "dispatchGesture failed", t);
            return false;
        }
    }

    private String labelOf(AccessibilityNodeInfo node) {
        CharSequence t = node.getText();
        if (t != null && t.length() > 0) {
            return clip(t.toString());
        }
        CharSequence d = node.getContentDescription();
        if (d != null && d.length() > 0) {
            return clip(d.toString());
        }
        try {
            String id = node.getViewIdResourceName();
            if (id != null) {
                int slash = id.lastIndexOf('/');
                return clip(slash >= 0 ? id.substring(slash + 1) : id);
            }
        } catch (Throwable ignored) {
        }
        return "跳过";
    }

    private static String clip(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        return v.length() > 40 ? v.substring(0, 40) : v;
    }

    private void onBlocked(String pkg, String label) {
        clickedPkg = pkg;
        clickCount++;
        lastClickTs = SystemClock.elapsedRealtime();

        String appName = AppInventory.labelOf(this, pkg);
        Log.i(TAG, "block splash pkg=" + pkg + " label=" + label + " attempt=" + clickCount);

        if (clickCount > 1) {
            // 同一次启动里，如果广告没关掉、又点了一次，只记录第一次，避免统计虚高。
            return;
        }

        db.bump(pkg, KIND_SPLASH, label);
        if (prefs.vibrate()) {
            vibrate();
        }
        if (prefs.toast()) {
            showToast("已跳过「" + appName + "」的开屏广告");
        }
        GuardForegroundService.refresh(this);
    }

    private void jumpBack(String fromPkg, String toPkg) {
        if (!toPkg.equals(fgPkg)) {
            return;
        }
        if (prefs.dryRun()) {
            Log.i(TAG, "dry-run jump " + fromPkg + " -> " + toPkg);
            showToast("演练：本可拦下「" + AppInventory.labelOf(this, fromPkg)
                    + "」的一次误触跳转");
            return;
        }
        boolean ok = performGlobalAction(GLOBAL_ACTION_BACK);
        if (!ok) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        // 返回成功后立刻把前台标记回上一个应用，并让守护窗口立即过期，
        // 避免把它当成一次新的启动而重复扫描。
        fgPkg = fromPkg;
        fgSince = now - prefs.splashWindowMs() - 1L;
        String fromName = AppInventory.labelOf(this, fromPkg);
        db.bump(fromPkg, KIND_JUMP, "→ " + toPkg);
        if (prefs.vibrate()) {
            vibrate();
        }
        if (prefs.toast()) {
            showToast("已拦下「" + fromName + "」的一次误触跳转");
        }
        Log.i(TAG, "block jump " + fromPkg + " -> " + toPkg);
        GuardForegroundService.refresh(this);
    }

    private void closeInterstitial(String pkg, String className) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastBackTs < 1500) {
            return;
        }
        lastBackTs = now;
        handler.postDelayed(() -> {
            if (!pkg.equals(fgPkg)) {
                return;
            }
            if (prefs.dryRun()) {
                Log.i(TAG, "dry-run interstitial " + pkg + " " + className);
                showToast("演练：本可关闭「" + AppInventory.labelOf(this, pkg) + "」的插屏广告");
                return;
            }
            if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
                return;
            }
            db.bump(pkg, KIND_INTERSTITIAL, className);
            if (prefs.toast()) {
                showToast("已关闭「" + AppInventory.labelOf(this, pkg) + "」的插屏广告");
            }
            Log.i(TAG, "close interstitial " + pkg + " " + className);
            GuardForegroundService.refresh(this);
        }, 320);
    }

    private void vibrate() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(18,
                        VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(18);
            }
        } catch (Throwable ignored) {
        }
    }

    private void showToast(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
