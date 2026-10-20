package com.qingping.screen.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.qingping.screen.R;
import com.qingping.screen.core.Prefs;
import com.qingping.screen.ui.MainActivity;

/**
 * 15 分钟一次的轻量巡检：如果无障碍服务掉线，就提醒用户重新打开。
 * 巡检本身不联网、不扫描界面。
 */
public class KeepAliveJobService extends JobService {

    private static final int JOB_ID = 2001;
    private static final String ALERT_CHANNEL = "alert";
    private static final long WARN_INTERVAL_MS = 12 * 60 * 60 * 1000L;

    public static void schedule(Context context) {
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
            if (scheduler == null) {
                return;
            }
            for (JobInfo info : scheduler.getAllPendingJobs()) {
                if (info.getId() == JOB_ID) {
                    return;
                }
            }
            JobInfo job = new JobInfo.Builder(JOB_ID,
                    new ComponentName(context, KeepAliveJobService.class))
                    .setPeriodic(15 * 60 * 1000L)
                    .setPersisted(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .build();
            scheduler.schedule(job);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Prefs prefs = Prefs.get(this);
        if (prefs.enabled()) {
            GuardForegroundService.startSafe(this);
            if (!GuardAccessibilityService.isConnected()) {
                long now = System.currentTimeMillis();
                if (now - prefs.lastOfflineWarn() > WARN_INTERVAL_MS) {
                    prefs.markOfflineWarn(now);
                    notifyOffline();
                }
            }
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    private void notifyOffline() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        ALERT_CHANNEL, "守护提醒", NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("无障碍服务掉线时提醒重新开启");
                nm.createNotificationChannel(channel);
            }
            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 2002, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(this, ALERT_CHANNEL);
            } else {
                builder = new Notification.Builder(this);
            }
            builder.setSmallIcon(R.drawable.ic_shield)
                    .setContentTitle("清屏的守护暂停了")
                    .setContentText("无障碍服务被系统关闭，点此重新开启")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true);
            nm.notify(2003, builder.build());
        } catch (Throwable ignored) {
        }
    }
}
