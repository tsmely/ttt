package com.qingping.screen.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.qingping.screen.R;
import com.qingping.screen.core.AppInventory;
import com.qingping.screen.core.Prefs;
import com.qingping.screen.core.StatsDb;
import com.qingping.screen.service.GuardAccessibilityService;
import com.qingping.screen.service.GuardForegroundService;
import com.qingping.screen.service.KeepAliveJobService;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity implements AppAdapter.Listener {

    private static final int REQ_NOTIFICATIONS = 101;

    private Prefs prefs;
    private StatsDb db;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private FrameLayout container;
    private View pageOverview;
    private View pageApps;
    private View pageSettings;
    private int tab = 0;

    private Switch masterSwitch;
    private TextView heroState;
    private TextView todayCount;
    private TextView heroTotal;
    private TextView heroApps;
    private TextView heroLast;
    private LinearLayout checklist;
    private TextView checkSummary;
    private BarChartView chart;
    private TextView weekTotal;
    private LinearLayout topApps;
    private LinearLayout recentList;
    private TextView recentEmpty;

    private ListView appList;
    private EditText searchInput;
    private TextView appsCount;
    private TextView appsEmpty;
    private AppAdapter adapter;
    private final List<AppInventory.AppEntry> allApps = new ArrayList<>();
    private Map<String, StatsDb.AppStat> counts = new java.util.HashMap<>();
    private String query = "";
    private boolean appsLoaded = false;

    private LinearLayout rulesBox;
    private LinearLayout dataBox;
    private LinearLayout systemBox;
    private TextView setWindowValue;
    private TextView setRegionValue;
    private TextView setJumpValue;
    private TextView setKeywordsValue;

    private boolean suppressMaster;
    private String lastChecklistSignature = "";
    private int lastStatsRevision = -1;
    private int lastCountsRevision = -1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshOverview();
            renderChecklist(false);
            if (tab == 0) {
                refreshStats(false);
            } else if (tab == 1) {
                refreshCounts(false);
            }
            ui.postDelayed(this, 1600);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        db = StatsDb.get(this);

        setContentView(R.layout.activity_main);
        setupWindow();

        container = findViewById(R.id.page_container);
        LayoutInflater inflater = LayoutInflater.from(this);
        pageOverview = inflater.inflate(R.layout.page_overview, container, false);
        pageApps = inflater.inflate(R.layout.page_apps, container, false);
        pageSettings = inflater.inflate(R.layout.page_settings, container, false);
        container.addView(pageOverview);
        container.addView(pageApps);
        container.addView(pageSettings);

        bindOverview();
        bindApps();
        setupNav();
        selectTab(0);
        buildSettings();
        loadAppsAsync();
        renderAll();

        ui.postDelayed(this::maybeWelcome, 500);
    }

    private void setupWindow() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.bg));
        window.setNavigationBarColor(getColor(R.color.bg));
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    // ---------------------------------------------------------------- 概览页

    private void bindOverview() {
        masterSwitch = pageOverview.findViewById(R.id.master_switch);
        heroState = pageOverview.findViewById(R.id.hero_state);
        todayCount = pageOverview.findViewById(R.id.today_count);
        heroTotal = pageOverview.findViewById(R.id.hero_total);
        heroApps = pageOverview.findViewById(R.id.hero_apps);
        heroLast = pageOverview.findViewById(R.id.hero_last);
        checklist = pageOverview.findViewById(R.id.checklist);
        checkSummary = pageOverview.findViewById(R.id.check_summary);
        chart = pageOverview.findViewById(R.id.chart);
        weekTotal = pageOverview.findViewById(R.id.week_total);
        topApps = pageOverview.findViewById(R.id.top_apps);
        recentList = pageOverview.findViewById(R.id.recent_list);
        recentEmpty = pageOverview.findViewById(R.id.recent_empty);

        TextView version = pageOverview.findViewById(R.id.version_chip);
        version.setText("v1.0.0");

        suppressMaster = true;
        masterSwitch.setChecked(prefs.enabled());
        suppressMaster = false;
        masterSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressMaster) {
                return;
            }
            prefs.setEnabled(isChecked);
            if (isChecked) {
                GuardForegroundService.startSafe(this);
                if (!isAccessibilityEnabled()) {
                    showAccessibilityDialog();
                }
            } else {
                GuardForegroundService.stopSafe(this);
            }
            renderAll();
        });

        pageOverview.findViewById(R.id.open_apps).setOnClickListener(v -> selectTab(1));
        renderChecklist(true);
    }

    private void refreshOverview() {
        long today = db.todayCount();
        long total = db.totalCount();
        long last = db.lastBlockTs();
        todayCount.setText(formatNumber(today));
        heroTotal.setText(formatNumber(total));
        heroApps.setText(String.valueOf(guardedCount()));
        heroLast.setText(last > 0 ? timeShort(last) : "—");

        boolean on = prefs.enabled() && isAccessibilityEnabled();
        if (on) {
            heroState.setText("●  守护中");
            heroState.setTextColor(0xFFFFFFFF);
        } else if (prefs.enabled()) {
            heroState.setText("●  待开启");
            heroState.setTextColor(0xE6FFFFFF);
        } else {
            heroState.setText("●  已暂停");
            heroState.setTextColor(0xB3FFFFFF);
        }
    }

    private void refreshStats(boolean force) {
        int revision = db.revision();
        if (!force && revision == lastStatsRevision) {
            return;
        }
        lastStatsRevision = revision;

        List<StatsDb.DayStat> days = db.lastDays(7);
        chart.setData(days);
        long week = 0;
        for (StatsDb.DayStat day : days) {
            week += day.count;
        }
        weekTotal.setText("共 " + formatNumber(week) + " 次");

        renderRanking(db.topApps(5));
        renderRecent(db.recentEvents(6));
        refreshCounts(true);
    }

    private void renderRanking(List<StatsDb.AppStat> stats) {
        topApps.removeAllViews();
        if (stats.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有数据，去打开几个 App 试试");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            empty.setTextColor(getColor(R.color.faint));
            empty.setPadding(dp(16), dp(8), dp(16), dp(14));
            topApps.addView(empty);
            return;
        }
        long max = 1;
        for (StatsDb.AppStat stat : stats) {
            if (stat.count > max) {
                max = stat.count;
            }
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (StatsDb.AppStat stat : stats) {
            View row = inflater.inflate(R.layout.item_rank, topApps, false);
            ImageView icon = row.findViewById(R.id.rank_icon);
            TextView name = row.findViewById(R.id.rank_name);
            TextView count = row.findViewById(R.id.rank_count);
            android.widget.ProgressBar bar = row.findViewById(R.id.rank_bar);

            Drawable drawable = AppInventory.iconOf(this, stat.pkg);
            icon.setImageDrawable(drawable != null
                    ? drawable : getPackageManager().getDefaultActivityIcon());
            name.setText(AppInventory.labelOf(this, stat.pkg));
            count.setText(stat.count + " 次");
            int progress = (int) Math.max(3, Math.round(stat.count * 100.0 / max));
            bar.setProgress(progress);
            topApps.addView(row);
        }
    }

    private void renderRecent(List<StatsDb.EventItem> events) {
        recentList.removeAllViews();
        boolean empty = events.isEmpty();
        recentEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (StatsDb.EventItem event : events) {
            View row = inflater.inflate(R.layout.item_event, recentList, false);
            View dot = row.findViewById(R.id.event_dot);
            TextView text = row.findViewById(R.id.event_text);
            TextView time = row.findViewById(R.id.event_time);

            if (GuardAccessibilityService.KIND_JUMP.equals(event.kind)) {
                dot.setBackgroundResource(R.drawable.bg_dot_warn);
                text.setText("「" + AppInventory.labelOf(this, event.pkg) + "」误触跳转已拦截");
            } else if (GuardAccessibilityService.KIND_INTERSTITIAL.equals(event.kind)) {
                dot.setBackgroundResource(R.drawable.bg_dot_info);
                text.setText("「" + AppInventory.labelOf(this, event.pkg) + "」插屏广告已关闭");
            } else {
                dot.setBackgroundResource(R.drawable.bg_dot);
                text.setText("「" + AppInventory.labelOf(this, event.pkg) + "」开屏广告已跳过");
            }
            time.setText(timeShort(event.ts));
            recentList.addView(row);
        }
    }

    private void renderChecklist(boolean force) {
        boolean a11y = isAccessibilityEnabled();
        boolean battery = isBatteryIgnored();
        boolean notify = notificationsEnabled();
        boolean autostart = prefs.autostartConfirmed();

        String signature = a11y + "|" + battery + "|" + notify + "|" + autostart;
        if (!force && signature.equals(lastChecklistSignature)) {
            return;
        }
        lastChecklistSignature = signature;

        checklist.removeAllViews();
        addCheckRow(checklist, a11y, "无障碍服务", "读取界面文字，自动点「跳过」",
                "去开启", this::showAccessibilityDialog);
        addCheckRow(checklist, battery, "电池优化白名单", "避免被系统省电策略休眠",
                "去设置", this::requestBatteryWhitelist);
        addCheckRow(checklist, autostart, "允许自启动 / 后台运行", "不同品牌需在系统设置里手动打开",
                "查看方法", this::showAutostartDialog);
        addCheckRow(checklist, notify, "通知权限", "显示守护状态与拦截提醒",
                "去开启", this::requestNotifications);

        if (!a11y) {
            checkSummary.setText("还差 1 项关键设置：打开无障碍服务");
        } else if (!battery) {
            checkSummary.setText("建议加入电池白名单，守护会更稳");
        } else if (!autostart) {
            checkSummary.setText("再到系统里允许自启动，开机后也能自动守护");
        } else {
            checkSummary.setText("一切就绪，清屏会一直在后台守着");
        }
    }

    private void addCheckRow(LinearLayout parent, boolean done, String title, String subtitle,
                             String actionText, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));

        ImageView icon = new ImageView(this);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        icon.setLayoutParams(iconLp);
        icon.setPadding(dp(6), dp(6), dp(6), dp(6));
        icon.setBackgroundResource(done ? R.drawable.bg_circle_ok : R.drawable.bg_circle_warn);
        icon.setImageResource(done ? R.drawable.ic_check : R.drawable.ic_alert);
        icon.setColorFilter(getColor(done ? R.color.primary : R.color.warn));
        row.addView(icon);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.leftMargin = dp(10);
        texts.setLayoutParams(textLp);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        titleView.setTextColor(getColor(R.color.ink));
        titleView.setIncludeFontPadding(false);
        texts.addView(titleView);

        TextView subView = new TextView(this);
        subView.setText(subtitle);
        subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        subView.setTextColor(getColor(R.color.sub));
        subView.setPadding(0, dp(2), 0, 0);
        texts.addView(subView);
        row.addView(texts);

        TextView actionView = new TextView(this);
        actionView.setText(done ? "已完成" : actionText);
        actionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        actionView.setTextColor(getColor(done ? R.color.faint : R.color.primary));
        actionView.setPadding(dp(8), dp(6), dp(2), dp(6));
        if (!done) {
            actionView.setOnClickListener(v -> action.run());
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            actionView.setBackgroundResource(tv.resourceId);
        }
        row.addView(actionView);
        parent.addView(row);
    }

    // ---------------------------------------------------------------- 应用页

    private void bindApps() {
        searchInput = pageApps.findViewById(R.id.search_input);
        appList = pageApps.findViewById(R.id.app_list);
        appsCount = pageApps.findViewById(R.id.apps_count);
        appsEmpty = pageApps.findViewById(R.id.apps_empty);

        adapter = new AppAdapter(this, prefs, this);
        appList.setAdapter(adapter);
        appList.setEmptyView(appsEmpty);
        appsEmpty.setText("正在读取应用列表…");

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s == null ? "" : s.toString();
                applyFilter();
            }
        });

        pageApps.findViewById(R.id.btn_select_all).setOnClickListener(v -> {
            List<String> packages = new ArrayList<>();
            for (AppInventory.AppEntry entry : allApps) {
                packages.add(entry.pkg);
            }
            prefs.setAllGuarded(true, packages);
            adapter.notifyDataSetChanged();
            refreshAppsHeader();
            refreshOverview();
        });

        pageApps.findViewById(R.id.btn_select_none).setOnClickListener(v -> {
            List<String> packages = new ArrayList<>();
            for (AppInventory.AppEntry entry : allApps) {
                packages.add(entry.pkg);
            }
            prefs.setAllGuarded(false, packages);
            adapter.notifyDataSetChanged();
            refreshAppsHeader();
            refreshOverview();
        });
    }

    private void loadAppsAsync() {
        final Context appContext = getApplicationContext();
        new Thread(() -> {
            List<AppInventory.AppEntry> list;
            try {
                list = AppInventory.load(appContext);
            } catch (Throwable t) {
                list = new ArrayList<>();
            }
            final List<AppInventory.AppEntry> loaded = list;
            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(loaded);
                appsLoaded = true;
                applyFilter();
                refreshAppsHeader();
                refreshOverview();
            });
        }, "qingping-app-list").start();
    }

    private void applyFilter() {
        if (adapter == null) {
            return;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<AppInventory.AppEntry> filtered = new ArrayList<>();
        for (AppInventory.AppEntry entry : allApps) {
            if (q.isEmpty()
                    || entry.label.toLowerCase(Locale.ROOT).contains(q)
                    || entry.pkg.toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(entry);
            }
        }
        adapter.setItems(filtered);
        adapter.setCounts(counts);
        if (!appsLoaded) {
            appsEmpty.setText("正在读取应用列表…");
        } else if (filtered.isEmpty()) {
            appsEmpty.setText(q.isEmpty() ? "没有找到应用" : "没有匹配的应用");
        }
    }

    private void refreshAppsHeader() {
        if (appsCount == null) {
            return;
        }
        if (!appsLoaded) {
            appsCount.setText("正在读取…");
            return;
        }
        appsCount.setText("已开启 " + guardedCount() + " 个");
    }

    private void refreshCounts(boolean force) {
        int revision = db.revision();
        if (!force && revision == lastCountsRevision) {
            return;
        }
        lastCountsRevision = revision;
        counts = db.countsByApp();
        if (adapter != null) {
            adapter.setCounts(counts);
        }
    }

    @Override
    public void onGuardChanged() {
        refreshOverview();
        refreshAppsHeader();
    }

    @Override
    public void onRowClick(AppInventory.AppEntry entry) {
        StatsDb.AppStat stat = counts.get(entry.pkg);
        StringBuilder message = new StringBuilder();
        message.append("包名：").append(entry.pkg).append('\n');
        message.append("累计拦截：").append(stat == null ? 0 : stat.count).append(" 次");
        if (stat != null && stat.lastTs > 0) {
            message.append('\n').append("最近拦截：").append(timeLong(stat.lastTs));
        }
        final boolean guarded = prefs.isGuarded(entry.pkg);

        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle(entry.label)
                .setMessage(message.toString())
                .setPositiveButton("应用信息", (dialog, which) -> openAppInfo(entry.pkg))
                .setNeutralButton(guarded ? "关闭守护" : "开启守护", (dialog, which) -> {
                    prefs.setGuarded(entry.pkg, !guarded);
                    adapter.notifyDataSetChanged();
                    refreshAppsHeader();
                    refreshOverview();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    // ---------------------------------------------------------------- 设置页

    private void buildSettings() {
        if (rulesBox == null) {
            rulesBox = pageSettings.findViewById(R.id.settings_rules);
            dataBox = pageSettings.findViewById(R.id.settings_data);
            systemBox = pageSettings.findViewById(R.id.settings_system);
            pageSettings.findViewById(R.id.about_principle)
                    .setOnClickListener(v -> showPrincipleDialog());
        }
        rulesBox.removeAllViews();
        dataBox.removeAllViews();
        systemBox.removeAllViews();

        setWindowValue = addNavRow(rulesBox, "检测时长",
                "应用启动后，在这段时间内寻找「跳过」",
                (prefs.splashWindowMs() / 1000) + " 秒", this::showWindowDialog);
        addDivider(rulesBox);
        setRegionValue = addNavRow(rulesBox, "点击区域",
                "只点击这个区域里的跳过按钮，减少误点",
                regionLabel(), this::showRegionDialog);
        addDivider(rulesBox);
        setJumpValue = addNavRow(rulesBox, "防误触跳转",
                "开屏时被带去浏览器或应用商店，自动返回",
                jumpLabel(), this::showJumpDialog);
        addDivider(rulesBox);
        addSwitchRow(rulesBox, "插屏广告自动关闭",
                "识别到广告 SDK 的全屏页面时自动返回",
                prefs.closeInterstitial(), value -> {
                    prefs.setCloseInterstitial(value);
                    refreshOverview();
                });
        addDivider(rulesBox);
        addSwitchRow(rulesBox, "演练模式",
                "只提示、不点击、不计数，用来观察是否误判",
                prefs.dryRun(), prefs::setDryRun);
        addDivider(rulesBox);
        setKeywordsValue = addNavRow(rulesBox, "自定义跳过关键词",
                "内置规则认不出时，用「、」或逗号补充",
                keywordSummary(), this::showKeywordsDialog);

        addNavRow(dataBox, "导出统计", "生成 CSV，可复制或分享", "", this::exportStats);
        addDivider(dataBox);
        addNavRow(dataBox, "清空统计", "删除本机全部拦截记录", "", this::confirmClearStats);

        addNavRow(systemBox, "无障碍服务", "清屏的核心权限",
                isAccessibilityEnabled() ? "已开启" : "未开启", this::showAccessibilityDialog);
        addDivider(systemBox);
        addNavRow(systemBox, "电池优化", "加入白名单后更不容易被清理",
                isBatteryIgnored() ? "已加入" : "未加入", this::requestBatteryWhitelist);
        addDivider(systemBox);
        addNavRow(systemBox, "自启动设置", "按机型手动允许后台运行",
                prefs.autostartConfirmed() ? "已确认" : "建议设置", this::showAutostartDialog);
        addDivider(systemBox);
        addNavRow(systemBox, "通知权限", "守护状态与提醒",
                notificationsEnabled() ? "已允许" : "已关闭", this::requestNotifications);
    }

    private TextView addNavRow(LinearLayout parent, String title, String subtitle,
                               String value, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(11), 0, dp(11));
        applyRipple(row);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        titleView.setTextColor(getColor(R.color.ink));
        titleView.setIncludeFontPadding(false);
        texts.addView(titleView);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subView = new TextView(this);
            subView.setText(subtitle);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            subView.setTextColor(getColor(R.color.sub));
            subView.setPadding(0, dp(2), 0, 0);
            texts.addView(subView);
        }
        row.addView(texts);

        TextView valueView = new TextView(this);
        valueView.setText(value == null ? "" : value);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        valueView.setTextColor(getColor(R.color.sub));
        valueView.setPadding(dp(6), 0, 0, 0);
        row.addView(valueView);

        ImageView chevron = new ImageView(this);
        LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(dp(15), dp(15));
        chevronLp.leftMargin = dp(2);
        chevron.setLayoutParams(chevronLp);
        chevron.setImageResource(R.drawable.ic_chevron);
        chevron.setColorFilter(getColor(R.color.faint));
        row.addView(chevron);

        row.setOnClickListener(v -> action.run());
        parent.addView(row);
        return valueView;
    }

    private void addSwitchRow(LinearLayout parent, String title, String subtitle,
                              boolean checked, BoolConsumer action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        applyRipple(row);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        titleView.setTextColor(getColor(R.color.ink));
        titleView.setIncludeFontPadding(false);
        texts.addView(titleView);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subView = new TextView(this);
            subView.setText(subtitle);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            subView.setTextColor(getColor(R.color.sub));
            subView.setPadding(0, dp(2), 0, 0);
            texts.addView(subView);
        }
        row.addView(texts);

        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> action.accept(isChecked));
        row.addView(toggle);
        row.setOnClickListener(v -> toggle.toggle());
        parent.addView(row);
    }

    private void addDivider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(getColor(R.color.line));
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        parent.addView(line, lp);
    }

    private void applyRipple(View view) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        view.setBackgroundResource(tv.resourceId);
    }

    private String regionLabel() {
        switch (prefs.region()) {
            case Prefs.REGION_TOP:
                return "屏幕顶部";
            case Prefs.REGION_FULL:
                return "整屏";
            default:
                return "右上角";
        }
    }

    private String jumpLabel() {
        switch (prefs.jumpMode()) {
            case Prefs.JUMP_OFF:
                return "关闭";
            case Prefs.JUMP_LOOSE:
                return "宽松（60 秒内）";
            default:
                return "严格（12 秒内）";
        }
    }

    private String keywordSummary() {
        String[] extras = prefs.extraKeywords();
        return extras.length == 0 ? "未设置" : extras.length + " 个";
    }

    private void showWindowDialog() {
        final int[] seconds = {3, 5, 6, 8, 10};
        int current = prefs.splashWindowMs() / 1000;
        int checked = 2;
        for (int i = 0; i < seconds.length; i++) {
            if (seconds[i] == current) {
                checked = i;
            }
        }
        String[] items = {"3 秒", "5 秒", "6 秒（推荐）", "8 秒", "10 秒"};
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle("检测时长")
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    prefs.setSplashWindowMs(seconds[which] * 1000);
                    if (setWindowValue != null) {
                        setWindowValue.setText(seconds[which] + " 秒");
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRegionDialog() {
        String[] items = {"右上角（推荐）", "屏幕顶部", "整屏（可能误点）"};
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle("点击区域")
                .setSingleChoiceItems(items, prefs.region(), (dialog, which) -> {
                    prefs.setRegion(which);
                    if (setRegionValue != null) {
                        setRegionValue.setText(regionLabel());
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showJumpDialog() {
        String[] items = {
                "关闭",
                "严格：开屏 12 秒内（推荐）",
                "宽松：开屏 60 秒内（更容易误伤正常链接）"
        };
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle("防误触跳转")
                .setSingleChoiceItems(items, prefs.jumpMode(), (dialog, which) -> {
                    prefs.setJumpMode(which);
                    if (setJumpValue != null) {
                        setJumpValue.setText(jumpLabel());
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showKeywordsDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(prefs.keywordsRaw());
        input.setHint("例如：跳过、关闭广告、skip");
        input.setSingleLine(false);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(dp(20), dp(6), dp(20), 0);
        wrapper.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle("自定义跳过关键词")
                .setMessage("多个关键词用「、」或逗号分隔，会和内置规则一起使用。")
                .setView(wrapper)
                .setPositiveButton("保存", (dialog, which) -> {
                    prefs.setKeywordsRaw(input.getText().toString());
                    if (setKeywordsValue != null) {
                        setKeywordsValue.setText(keywordSummary());
                    }
                    Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void exportStats() {
        String csv = db.exportCsv();
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            dir = getFilesDir();
        }
        final File file = new File(dir, "qingping-stats.csv");
        try {
            FileOutputStream out = new FileOutputStream(file);
            out.write(csv.getBytes("UTF-8"));
            out.close();
        } catch (Throwable t) {
            Toast.makeText(this, "写入文件失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle("导出统计")
                .setMessage("已保存到：\n" + file.getAbsolutePath()
                        + "\n\n也可以直接分享这段 CSV。")
                .setPositiveButton("分享", (dialog, which) -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_SUBJECT, "清屏拦截统计");
                    send.putExtra(Intent.EXTRA_TEXT, csv);
                    try {
                        startActivity(Intent.createChooser(send, "导出统计"));
                    } catch (Throwable ignored) {
                    }
                })
                .setNeutralButton("复制", (dialog, which) -> {
                    ClipboardManager cm =
                            (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("清屏统计", csv));
                        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void confirmClearStats() {
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle("清空统计？")
                .setMessage("这会删除本机保存的所有拦截记录，无法恢复。")
                .setPositiveButton("清空", (dialog, which) -> {
                    db.clear();
                    lastStatsRevision = -1;
                    lastCountsRevision = -1;
                    renderAll();
                    Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------------------------------------------------------- 导航

    private void setupNav() {
        View navOverview = findViewById(R.id.nav_overview);
        View navApps = findViewById(R.id.nav_apps);
        View navSettings = findViewById(R.id.nav_settings);
        navOverview.setOnClickListener(v -> selectTab(0));
        navApps.setOnClickListener(v -> selectTab(1));
        navSettings.setOnClickListener(v -> selectTab(2));
    }

    private void selectTab(int target) {
        tab = target;
        pageOverview.setVisibility(target == 0 ? View.VISIBLE : View.GONE);
        pageApps.setVisibility(target == 1 ? View.VISIBLE : View.GONE);
        pageSettings.setVisibility(target == 2 ? View.VISIBLE : View.GONE);

        setNavState(R.id.nav_overview_icon, R.id.nav_overview_label, target == 0);
        setNavState(R.id.nav_apps_icon, R.id.nav_apps_label, target == 1);
        setNavState(R.id.nav_settings_icon, R.id.nav_settings_label, target == 2);

        if (target == 0) {
            refreshStats(false);
        } else if (target == 1) {
            refreshCounts(false);
            refreshAppsHeader();
        } else {
            buildSettings();
        }
    }

    private void setNavState(int iconId, int labelId, boolean selected) {
        ImageView icon = findViewById(iconId);
        TextView label = findViewById(labelId);
        int color = getColor(selected ? R.color.primary : R.color.faint);
        icon.setColorFilter(color);
        label.setTextColor(color);
        label.setTypeface(null, selected ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
    }

    // ---------------------------------------------------------------- 权限与引导

    private boolean isAccessibilityEnabled() {
        if (GuardAccessibilityService.isConnected()) {
            return true;
        }
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) {
                return false;
            }
            ComponentName component =
                    new ComponentName(this, GuardAccessibilityService.class);
            return enabled.contains(component.flattenToString())
                    || enabled.contains(component.flattenToShortString());
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isBatteryIgnored() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean notificationsEnabled() {
        try {
            NotificationManager nm =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            return nm == null || nm.areNotificationsEnabled();
        } catch (Throwable t) {
            return true;
        }
    }

    private void showAccessibilityDialog() {
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle("开启无障碍服务")
                .setMessage("在系统列表里找到「清屏 · 开屏广告跳过」，打开开关。\n\n"
                        + "Android 13 及以上，如果开关是灰色的：\n"
                        + "先在「设置 → 应用 → 清屏 → 右上角 ⋮」里点「允许受限制的设置」，再回来开启。\n\n"
                        + "清屏只用它读取界面文字和点「跳过」，不会读取你的输入内容。")
                .setPositiveButton("打开设置", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Throwable t) {
                        Toast.makeText(this, "无法打开系统设置", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("应用信息", (dialog, which) -> openAppInfo(getPackageName()))
                .setNegativeButton("稍后", null)
                .show();
    }

    private void requestBatteryWhitelist() {
        if (isBatteryIgnored()) {
            Toast.makeText(this, "已在电池白名单中", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable t2) {
                Toast.makeText(this, "请到系统电池设置里手动允许后台运行", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showAutostartDialog() {
        String message = "不同品牌的限制不一样，这一步只能手动改：\n\n"
                + "小米 / 红米：安全中心 → 应用管理 → 权限 → 自启动\n"
                + "华为 / 荣耀：手机管家 → 应用启动管理 → 清屏 → 手动管理\n"
                + "OPPO / 一加：设置 → 电池 → 应用耗电管理 → 允许后台运行\n"
                + "vivo / iQOO：i管家 → 应用管理 → 权限管理 → 自启动\n"
                + "三星：设置 → 电池 → 后台使用限制 → 移出休眠应用\n"
                + "原生 Android：设置 → 应用 → 清屏 → 电池 → 不受限制";
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle("允许自启动 / 后台运行")
                .setMessage(message)
                .setPositiveButton("尝试打开设置", (dialog, which) -> openAutostartSettings())
                .setNeutralButton("我已设置", (dialog, which) -> {
                    prefs.setAutostartConfirmed(true);
                    renderChecklist(true);
                    buildSettings();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openAutostartSettings() {
        String[][] targets = {
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
                {"com.samsung.android.lool", "com.samsung.android.lool.activity.SleepingAppsActivity"},
                {"com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"}
        };
        for (String[] target : targets) {
            try {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(target[0], target[1]));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, "请到系统设置中手动允许自启动", Toast.LENGTH_LONG).show();
        }
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},
                    REQ_NOTIFICATIONS);
        } else {
            try {
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(intent);
            } catch (Throwable t) {
                Toast.makeText(this, "请到系统设置里允许通知", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openAppInfo(String pkg) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + pkg));
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开应用信息", Toast.LENGTH_SHORT).show();
        }
    }

    private void maybeWelcome() {
        if (!prefs.firstRun()) {
            return;
        }
        prefs.setFirstRun(false);
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle("欢迎使用清屏")
                .setMessage("它会做三件事：\n"
                        + "1. 打开别的 App 时，替你点掉开屏广告的「跳过」；\n"
                        + "2. 开屏时误触跳到浏览器 / 应用商店，自动按返回；\n"
                        + "3. 按应用统计拦截次数，全部只存在本机。\n\n"
                        + "先开启无障碍服务，清屏才能真正开始工作。")
                .setPositiveButton("去开启", (dialog, which) -> showAccessibilityDialog())
                .setNegativeButton("先看看", null)
                .show();
    }

    private void showPrincipleDialog() {
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle("清屏是怎么工作的")
                .setMessage("清屏使用 Android 的无障碍服务（AccessibilityService）：\n\n"
                        + "· 应用进入前台后的一小段时间内，读取当前窗口的文字和控件 id；\n"
                        + "· 只在屏幕右上角（可调）寻找「跳过 / 关闭广告 / skip」这类按钮；\n"
                        + "· 找到后模拟一次点击，并记下应用名与次数；\n"
                        + "· 如果开屏时被带到浏览器或应用商店，就自动按返回键。\n\n"
                        + "它不会修改其他应用，不能读取输入框内容，也没有联网权限。\n\n"
                        + "受限之处：纯图片、Canvas 绘制、部分 WebView 广告没有可读文字，"
                        + "这类广告无法识别；「摇一摇」传感器广告也无法拦截。")
                .setPositiveButton("知道了", null)
                .show();
    }

    // ---------------------------------------------------------------- 生命周期

    @Override
    protected void onResume() {
        super.onResume();
        renderAll();
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            renderChecklist(true);
            buildSettings();
        }
    }

    @Override
    public void onBackPressed() {
        if (tab != 0) {
            selectTab(0);
        } else {
            super.onBackPressed();
        }
    }

    private void renderAll() {
        if (container == null) {
            return;
        }
        suppressMaster = true;
        masterSwitch.setChecked(prefs.enabled());
        suppressMaster = false;
        refreshOverview();
        renderChecklist(true);
        refreshStats(true);
        refreshAppsHeader();
        if (tab == 2) {
            buildSettings();
        }
    }

    // ---------------------------------------------------------------- 工具

    private int guardedCount() {
        if (!appsLoaded) {
            return 0;
        }
        int count = 0;
        for (AppInventory.AppEntry entry : allApps) {
            if (prefs.isGuarded(entry.pkg)) {
                count++;
            }
        }
        return count;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String formatNumber(long value) {
        return String.format(Locale.CHINA, "%,d", value);
    }

    private static String timeShort(long ts) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(ts);
        boolean sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR);
        if (sameDay) {
            return String.format(Locale.CHINA, "%02d:%02d",
                    then.get(Calendar.HOUR_OF_DAY), then.get(Calendar.MINUTE));
        }
        return String.format(Locale.CHINA, "%d月%d日",
                then.get(Calendar.MONTH) + 1, then.get(Calendar.DAY_OF_MONTH));
    }

    private static String timeLong(long ts) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date(ts));
    }

    private interface BoolConsumer {
        void accept(boolean value);
    }
}
