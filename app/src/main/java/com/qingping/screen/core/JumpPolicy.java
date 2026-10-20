package com.qingping.screen.core;

/**
 * 误触跳转的判定。纯逻辑，便于单元测试。
 */
public final class JumpPolicy {

    private JumpPolicy() {
    }

    public static final long STRICT_WINDOW_MS = 12_000L;
    public static final long LOOSE_WINDOW_MS = 60_000L;

    public static long windowMs(int mode) {
        return mode == Prefs.JUMP_LOOSE ? LOOSE_WINDOW_MS : STRICT_WINDOW_MS;
    }

    /**
     * @param fromPkg           跳转前的应用
     * @param toPkg             跳转后的应用
     * @param sinceFromLaunchMs 跳转前应用进入前台后经过的毫秒数
     * @param mode              Prefs.JUMP_*
     * @param fromGuarded       跳转前的应用是否在守护列表中
     */
    public static boolean shouldBlock(String fromPkg, String toPkg, long sinceFromLaunchMs,
                                      int mode, boolean fromGuarded) {
        if (mode == Prefs.JUMP_OFF || !fromGuarded) {
            return false;
        }
        if (fromPkg == null || toPkg == null || fromPkg.equals(toPkg)) {
            return false;
        }
        if (!SkipRules.isJumpTarget(toPkg)) {
            return false;
        }
        if (sinceFromLaunchMs < 0) {
            return false;
        }
        return sinceFromLaunchMs <= windowMs(mode);
    }
}
