package com.qingping.screen;

import android.app.Application;

import com.qingping.screen.core.Prefs;
import com.qingping.screen.core.StatsDb;
import com.qingping.screen.service.GuardForegroundService;
import com.qingping.screen.service.KeepAliveJobService;

public class QingPingApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.get(this);
        StatsDb.get(this);
        KeepAliveJobService.schedule(this);
        if (Prefs.get(this).enabled()) {
            GuardForegroundService.startSafe(this);
        }
    }
}
