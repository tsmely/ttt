package com.qingping.screen.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import com.qingping.screen.R;
import com.qingping.screen.core.Prefs;
import com.qingping.screen.core.StatsDb;
import com.qingping.screen.ui.MainActivity;

/**
 * 常驻通知服务。它本身不做事，作用有两个：
 * 1) 让系统把进程视为「正在运行的前台服务」，降低被省电策略清掉的概率；
 * 2) 给用户一个随时可见的状态入口。
 */
public class GuardForegroundService extends Service {

    public static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "guard";
    private static volatile GuardForegroundService instance;

    public static void startSafe(Context context) {
        try {
            Intent intent = new Intent(context, GuardForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable ignored) {
            // Android 12+ 在后台启动前台服务可能被拒绝，无障碍服务本身仍可工作
        }
    }

    public static void stopSafe(Context context) {
        try {
            context.stopService(new Intent(context, GuardForegroundService.class));
        } catch (Throwable ignored) {
        }
    }

    public static void refresh(Context context) {
        GuardForegroundService service = instance;
        if (service != null) {
            service.updateNotification();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel();
        try {
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Throwable t) {
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateNotification();
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // 从最近任务划掉后不强行拉起：Android 12+ 限制后台启动前台服务，
        // 强行拉起既不可靠也容易触发系统限制。无障碍服务本身由系统托管。
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        try {
            stopForeground(true);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "守护状态", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("显示清屏是否在守护，以及今日拦截次数");
        channel.setShowBadge(false);
        channel.enableVibration(false);
        channel.setSound(null, null);
        nm.createNotificationChannel(channel);
    }

    private void updateNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, buildNotification());
            }
        } catch (Throwable ignored) {
        }
    }

    private Notification buildNotification() {
        boolean enabled = Prefs.get(this).enabled();
        boolean connected = GuardAccessibilityService.isConnected();
        long today = StatsDb.get(this).todayCount();

        String title;
        String text;
        if (!enabled) {
            title = "清屏已暂停";
            text = "点此重新开启守护";
        } else if (!connected) {
            title = "清屏等待无障碍服务";
            text = "点此完成开启，才能自动跳过广告";
        } else if (today > 0) {
            title = "清屏正在守护";
            text = "今日已拦截 " + today + " 次 · 数据只在本机";
        } else {
            title = "清屏正在守护";
            text = "等待广告出现 · 数据只在本机";
        }

        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 1000, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PRIVATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setColor(0xFF0E9E79);
        }
        return builder.build();
    }
}
