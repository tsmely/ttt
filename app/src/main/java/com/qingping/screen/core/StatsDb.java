package com.qingping.screen.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本机统计库：按「应用 + 日期」累计次数，并保留最近 1500 条事件明细。
 * 没有任何网络上报。
 */
public final class StatsDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "qingping.db";
    private static final int DB_VERSION = 1;

    private static volatile StatsDb instance;

    private final AtomicInteger revision = new AtomicInteger();
    private int trimCounter;

    public static StatsDb get(Context context) {
        if (instance == null) {
            synchronized (StatsDb.class) {
                if (instance == null) {
                    instance = new StatsDb(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private StatsDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE stats ("
                + "pkg TEXT NOT NULL,"
                + "day TEXT NOT NULL,"
                + "cnt INTEGER NOT NULL DEFAULT 0,"
                + "last_ts INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (pkg, day))");
        db.execSQL("CREATE TABLE events ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "pkg TEXT NOT NULL,"
                + "ts INTEGER NOT NULL,"
                + "kind TEXT NOT NULL,"
                + "detail TEXT)");
        db.execSQL("CREATE INDEX idx_events_ts ON events(ts)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 目前只有 v1，保留升级入口
    }

    public int revision() {
        return revision.get();
    }

    private static String dayKey(long ts) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(ts));
    }

    public static String todayKey() {
        return dayKey(System.currentTimeMillis());
    }

    private static String dayKeyBefore(int daysAgo) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -daysAgo);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    /** 记一次拦截。 */
    public synchronized void bump(String pkg, String kind, String detail) {
        long now = System.currentTimeMillis();
        String day = dayKey(now);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("INSERT OR IGNORE INTO stats(pkg, day, cnt, last_ts) VALUES(?,?,0,0)",
                    new Object[]{pkg, day});
            db.execSQL("UPDATE stats SET cnt = cnt + 1, last_ts = ? WHERE pkg = ? AND day = ?",
                    new Object[]{now, pkg, day});
            ContentValues cv = new ContentValues();
            cv.put("pkg", pkg);
            cv.put("ts", now);
            cv.put("kind", kind);
            cv.put("detail", detail);
            db.insert("events", null, cv);
            if ((++trimCounter % 64) == 0) {
                db.execSQL("DELETE FROM events WHERE id <= (SELECT MAX(id) - 1500 FROM events)");
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        revision.incrementAndGet();
    }

    public synchronized void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("stats", null, null);
            db.delete("events", null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        revision.incrementAndGet();
    }

    public long todayCount() {
        return sum("SELECT COALESCE(SUM(cnt),0) FROM stats WHERE day = ?",
                new String[]{todayKey()});
    }

    public long totalCount() {
        return sum("SELECT COALESCE(SUM(cnt),0) FROM stats", null);
    }

    public long countLastDays(int days) {
        return sum("SELECT COALESCE(SUM(cnt),0) FROM stats WHERE day >= ?",
                new String[]{dayKeyBefore(days - 1)});
    }

    private long sum(String sql, String[] args) {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(sql, args);
            return c.moveToFirst() ? c.getLong(0) : 0L;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    public long lastBlockTs() {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery("SELECT COALESCE(MAX(ts),0) FROM events", null);
            return c.moveToFirst() ? c.getLong(0) : 0L;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /** 按应用聚合，次数从高到低。 */
    public List<AppStat> topApps(int limit) {
        List<AppStat> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT pkg, SUM(cnt) AS c, MAX(last_ts) AS l FROM stats "
                            + "GROUP BY pkg ORDER BY c DESC, l DESC LIMIT ?",
                    new String[]{String.valueOf(limit)});
            while (c.moveToNext()) {
                out.add(new AppStat(c.getString(0), c.getLong(1), c.getLong(2)));
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return out;
    }

    /** 所有应用的累计次数，供应用列表展示。 */
    public Map<String, AppStat> countsByApp() {
        Map<String, AppStat> out = new HashMap<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT pkg, SUM(cnt) AS c, MAX(last_ts) AS l FROM stats GROUP BY pkg", null);
            while (c.moveToNext()) {
                out.put(c.getString(0), new AppStat(c.getString(0), c.getLong(1), c.getLong(2)));
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return out;
    }

    /** 最近 N 天（含今天）的逐日数据，缺失的日期补 0。 */
    public List<DayStat> lastDays(int days) {
        Map<String, Long> map = new HashMap<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT day, SUM(cnt) FROM stats WHERE day >= ? GROUP BY day",
                    new String[]{dayKeyBefore(days - 1)});
            while (c.moveToNext()) {
                map.put(c.getString(0), c.getLong(1));
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        List<DayStat> out = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            String key = dayKeyBefore(i);
            out.add(new DayStat(key, map.containsKey(key) ? map.get(key) : 0L));
        }
        return out;
    }

    public List<EventItem> recentEvents(int limit) {
        List<EventItem> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT id, pkg, ts, kind, detail FROM events ORDER BY id DESC LIMIT ?",
                    new String[]{String.valueOf(limit)});
            while (c.moveToNext()) {
                out.add(new EventItem(c.getLong(0), c.getString(1), c.getLong(2),
                        c.getString(3), c.getString(4)));
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return out;
    }

    /** 导出为 CSV 文本，方便用户自己留存。 */
    public String exportCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("\uFEFF").append("应用包名,日期,拦截次数,最后拦截时间\n");
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT pkg, day, cnt, last_ts FROM stats ORDER BY day DESC, cnt DESC", null);
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            while (c.moveToNext()) {
                String pkg = c.getString(0);
                String day = c.getString(1);
                long cnt = c.getLong(2);
                long ts = c.getLong(3);
                sb.append(escape(pkg)).append(',')
                        .append(day).append(',')
                        .append(cnt).append(',')
                        .append(ts > 0 ? fmt.format(new Date(ts)) : "")
                        .append('\n');
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return sb.toString();
    }

    private static String escape(String v) {
        if (v == null) {
            return "";
        }
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    public static final class AppStat {
        public final String pkg;
        public final long count;
        public final long lastTs;

        public AppStat(String pkg, long count, long lastTs) {
            this.pkg = pkg;
            this.count = count;
            this.lastTs = lastTs;
        }
    }

    public static final class DayStat {
        public final String day;
        public final long count;

        public DayStat(String day, long count) {
            this.day = day;
            this.count = count;
        }
    }

    public static final class EventItem {
        public final long id;
        public final String pkg;
        public final long ts;
        public final String kind;
        public final String detail;

        public EventItem(long id, String pkg, long ts, String kind, String detail) {
            this.id = id;
            this.pkg = pkg;
            this.ts = ts;
            this.kind = kind;
            this.detail = detail;
        }
    }
}
