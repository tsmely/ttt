import com.qingping.screen.core.JumpPolicy;
import com.qingping.screen.core.Prefs;
import com.qingping.screen.core.SkipRules;

public class RuleTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // 文案识别
        expectSkip("跳过", true);
        expectSkip("跳过广告", true);
        expectSkip("跳过 3", true);
        expectSkip("跳过3s", true);
        expectSkip("跳过广告 5s", true);
        expectSkip("3s 跳过", true);
        expectSkip("3秒后跳过", true);
        expectSkip("5s | 跳过", true);
        expectSkip("点击跳过", true);
        expectSkip("跳過廣告 5", true);
        expectSkip("关闭广告", true);
        expectSkip("Skip Ad", true);
        expectSkip("skip in 5s", true);
        expectSkip("  跳过  ", true);
        expectSkip("\u200b跳过", true);
        expectSkip("跳过广告，了解更多", false);
        expectSkip("关闭", false);
        expectSkip("跳过历史记录", false);
        expectSkip("广告", false);
        expectSkip("skip intro", false);
        expectSkip("", false);
        expectSkip(null, false);

        // 自定义关键词
        expectSkipWithExtras("略过吧", new String[]{"略过吧"}, true);
        expectSkipWithExtras("关闭它", new String[]{"关闭它"}, true);

        // View id
        expect(SkipRules.isSkipViewId("com.bytedance.sdk.openadsdk:id/tt_splash_skip_btn"), true, "id tt_splash_skip");
        expect(SkipRules.isSkipViewId("com.qq.e.ads:id/ad_close"), true, "id ad_close");
        expect(SkipRules.isSkipViewId("com.x:id/close_btn"), false, "id close_btn");
        expect(SkipRules.isSkipViewId("com.x:id/settings"), false, "id settings");

        // 广告 SDK Activity
        expect(SkipRules.isAdSdkClass("com.bytedance.sdk.openadsdk.activity.TTFullScreenExpressVideoActivity"), true, "pangle activity");
        expect(SkipRules.isAdSdkClass("com.qq.e.ads.PortraitADActivity"), true, "gdt activity");
        expect(SkipRules.isAdSdkClass("com.example.app.MainActivity"), false, "normal activity");

        // 跳转目标
        expect(SkipRules.isJumpTarget("com.android.chrome"), true, "chrome");
        expect(SkipRules.isJumpTarget("com.android.vending"), true, "play store");
        expect(SkipRules.isJumpTarget("com.tencent.mm"), false, "wechat");
        expect(SkipRules.isJumpTarget("com.miui.browser"), true, "miui browser");

        // 误触跳转策略
        expect(JumpPolicy.shouldBlock("com.a", "com.android.chrome", 5000,
                Prefs.JUMP_STRICT, true), true, "strict 5s");
        expect(JumpPolicy.shouldBlock("com.a", "com.android.chrome", 20000,
                Prefs.JUMP_STRICT, true), false, "strict 20s");
        expect(JumpPolicy.shouldBlock("com.a", "com.android.chrome", 20000,
                Prefs.JUMP_LOOSE, true), true, "loose 20s");
        expect(JumpPolicy.shouldBlock("com.a", "com.android.chrome", 70000,
                Prefs.JUMP_LOOSE, true), false, "loose 70s");
        expect(JumpPolicy.shouldBlock("com.a", "com.android.chrome", 3000,
                Prefs.JUMP_OFF, true), false, "off");
        expect(JumpPolicy.shouldBlock("com.a", "com.tencent.mm", 3000,
                Prefs.JUMP_STRICT, true), false, "not a jump target");
        expect(JumpPolicy.shouldBlock("com.a", "com.android.chrome", 3000,
                Prefs.JUMP_STRICT, false), false, "not guarded");
        expect(JumpPolicy.shouldBlock("com.a", "com.a", 3000,
                Prefs.JUMP_STRICT, true), false, "same package");

        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void expectSkip(String text, boolean wanted) {
        expect(SkipRules.isSkipLabel(text, null), wanted, "label [" + text + "]");
    }

    private static void expectSkipWithExtras(String text, String[] extras, boolean wanted) {
        expect(SkipRules.isSkipLabel(text, extras), wanted, "label+extras [" + text + "]");
    }

    private static void expect(boolean actual, boolean wanted, String what) {
        if (actual == wanted) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + what + " expected=" + wanted + " actual=" + actual);
        }
    }
}
