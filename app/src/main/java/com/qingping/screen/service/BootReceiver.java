package com.qingping.screen.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.qingping.screen.core.Prefs;

/**
 * 开机 / 应用更新后拉起常驻通知服务。
 *
 * 注意：无障碍服务由系统托管，只要用户在系统设置里保持开启，开机后系统会自行重连，
 * 这个广播只是把前台服务（通知与保活）一起拉起来。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if (!boot) {
            return;
        }
        if (Prefs.get(context).enabled()) {
            GuardForegroundService.startSafe(context);
        }
        KeepAliveJobService.schedule(context);
    }
}
