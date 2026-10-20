package com.qingping.screen.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 纯 Java 的识别规则，不依赖 Android，方便单独测试。
 *
 * 设计原则：宁可漏点，不可乱点。所有规则都只匹配「跳过 / 关闭广告 / skip」这类
 * 明确的按钮文案，再叠加时间窗与屏幕区域限制。
 */
public final class SkipRules {

    private SkipRules() {
    }

    /** 跳过 / 点击跳过 / 跳过广告 3s / 跳過廣告 5 */
    private static final Pattern CN_SKIP = Pattern.compile(
            "^(点击|點擊)?\\s*(跳过|跳過|略过|略過)\\s*(广告|廣告)?"
                    + "[\\s·•|:：\\-，,]*\\d{0,2}\\s*(s|秒)?[\\s·•|:：>》›»\\-，,]*$");

    /** 3 跳过 / 5s 跳过 / 3秒后跳过 / 5s | 跳过 */
    private static final Pattern CN_COUNT_FIRST = Pattern.compile(
            "^\\d{1,2}\\s*(s|秒)?\\s*[|·•\\-，,]?\\s*(后|後)?\\s*(跳过|跳過)$");

    /** 关闭广告 / 关闭插屏 */
    private static final Pattern CN_CLOSE_AD = Pattern.compile(
            "^(关闭|關閉)\\s*(广告|廣告|插屏)$");

    /** skip / skip ad / close ad / close this ad */
    private static final Pattern EN_SKIP = Pattern.compile(
            "^(skip|skip\\s+ad|skip\\s+ads|skip\\s+advert|skip\\s+advertisement|close\\s+ad|close\\s+ads|close\\s+this\\s+ad)$");

    /** skip 3 / skip in 5s */
    private static final Pattern EN_SKIP_COUNT = Pattern.compile(
            "^(skip|close)\\s*(in\\s*)?\\d{1,2}\\s*(s|sec|secs|seconds)?$");

    private static final Set<String> BROWSERS = new HashSet<>(Arrays.asList(
            "com.android.chrome",
            "com.android.browser",
            "com.tencent.mtt",
            "com.tencent.mtt.x86",
            "com.ucmobile",
            "com.ucmobile.intl",
            "com.qihoo.browser",
            "com.baidu.searchbox",
            "com.miui.browser",
            "com.heytap.browser",
            "com.coloros.browser",
            "com.vivo.browser",
            "com.huawei.browser",
            "com.sec.android.app.sbrowser",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.quark.browser",
            "com.aliyun.mobile.browser",
            "com.android.htmlviewer"
    ));

    private static final Set<String> MARKETS = new HashSet<>(Arrays.asList(
            "com.android.vending",
            "com.xiaomi.market",
            "com.huawei.appmarket",
            "com.oppo.market",
            "com.heytap.market",
            "com.bbk.appstore",
            "com.tencent.android.qqdownloader",
            "com.360.appstore",
            "com.qihoo.appstore",
            "com.baidu.appsearch",
            "com.samsung.android.galaxyapps",
            "com.sec.android.app.samsungapps",
            "com.lenovo.leos.appstore",
            "com.meizu.mstore",
            "com.coolapk.market"
    ));

    private static final String[] AD_SDK_PREFIXES = {
            "com.bytedance.sdk.openadsdk",
            "com.bytedance.sdk",
            "com.pangle",
            "com.qq.e",
            "com.tencent.gdt",
            "com.baidu.mobads",
            "com.kwad",
            "com.sigmob",
            "com.mbridge",
            "com.mintegral",
            "com.unity3d.ads",
            "com.unity3d.services.ads",
            "com.google.android.gms.ads",
            "com.applovin",
            "com.facebook.ads",
            "com.vungle",
            "com.chartboost",
            "com.inmobi",
            "com.mopub",
            "com.anythink",
            "com.tapjoy",
            "com.ironsource"
    };

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\u200b' || c == '\ufeff' || c == '\u00a0' || c == '\u3000') {
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
            } else {
                sb.append(c);
            }
        }
        String out = sb.toString().trim().toLowerCase(Locale.ROOT);
        out = out.replaceAll("^[\\s>》›»|:：]+", "");
        out = out.replaceAll("[\\s<《‹«|:：]+$", "");
        return out;
    }

    /**
     * 是否为「跳过」类按钮文案。
     *
     * @param text   控件文字或 contentDescription
     * @param extras 用户自定义关键词（可为 null）
     */
    public static boolean isSkipLabel(String text, String[] extras) {
        String t = normalize(text);
        if (t.isEmpty() || t.length() > 20) {
            return false;
        }
        if (CN_SKIP.matcher(t).matches()
                || CN_COUNT_FIRST.matcher(t).matches()
                || CN_CLOSE_AD.matcher(t).matches()) {
            return true;
        }
        if (EN_SKIP.matcher(t).matches() || EN_SKIP_COUNT.matcher(t).matches()) {
            return true;
        }
        if (extras != null) {
            for (String extra : extras) {
                String e = normalize(extra);
                if (!e.isEmpty() && e.equals(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 通过 View 的资源 id 识别广告关闭按钮，例如 tt_splash_skip_btn。 */
    public static boolean isSkipViewId(String viewId) {
        if (viewId == null || viewId.isEmpty()) {
            return false;
        }
        String v = viewId.toLowerCase(Locale.ROOT);
        if (v.contains("skip")) {
            return true;
        }
        if (v.contains("tt_splash") || v.contains("splash_skip")
                || v.contains("ksad_skip") || v.contains("gdt_skip")) {
            return true;
        }
        if (v.contains("close")) {
            return v.contains("_ad") || v.contains("ad_") || v.contains("advert")
                    || v.contains("splash") || v.contains("interstitial");
        }
        return false;
    }

    /** 当前窗口是否属于已知广告 SDK（用于「插屏自动关闭」与置信度判断）。 */
    public static boolean isAdSdkClass(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        String c = className.toLowerCase(Locale.ROOT);
        for (String prefix : AD_SDK_PREFIXES) {
            if (c.startsWith(prefix) || c.contains("." + prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 是否是常见的「跳转落地页」：浏览器或应用商店。 */
    public static boolean isJumpTarget(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        String p = pkg.toLowerCase(Locale.ROOT);
        if (BROWSERS.contains(p) || MARKETS.contains(p)) {
            return true;
        }
        if (p.contains("browser") || p.contains("appstore") || p.contains("appmarket")
                || p.endsWith(".market") || p.contains("qqdownloader")) {
            return true;
        }
        return false;
    }

    public static boolean isBrowserPackage(String pkg) {
        if (pkg == null) {
            return false;
        }
        String p = pkg.toLowerCase(Locale.ROOT);
        return BROWSERS.contains(p) || p.contains("browser");
    }

    public static boolean isMarketPackage(String pkg) {
        if (pkg == null) {
            return false;
        }
        String p = pkg.toLowerCase(Locale.ROOT);
        return MARKETS.contains(p) || p.contains("appstore") || p.contains("appmarket")
                || p.endsWith(".market");
    }
}
