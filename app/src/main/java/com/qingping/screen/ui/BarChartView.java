package com.qingping.screen.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.qingping.screen.R;
import com.qingping.screen.core.StatsDb;

import java.util.Calendar;
import java.util.List;

/**
 * 极简 7 日柱状图，纯 Canvas 绘制，不依赖任何图表库。
 */
public class BarChartView extends View {

    private static final String[] WEEK = {"日", "一", "二", "三", "四", "五", "六"};

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private long[] values = new long[0];
    private String[] labels = new String[0];
    private float density;

    public BarChartView(Context context) {
        super(context);
        init();
    }

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BarChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        int primary = getResources().getColor(R.color.primary);
        int primaryDark = getResources().getColor(R.color.primary_dark);
        int track = getResources().getColor(R.color.track);
        int sub = getResources().getColor(R.color.sub);
        int ink = getResources().getColor(R.color.ink);

        barPaint.setColor(primary);
        todayPaint.setColor(primaryDark);
        trackPaint.setColor(track);
        labelPaint.setColor(sub);
        labelPaint.setTextSize(sp(10.5f));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setColor(ink);
        valuePaint.setTextSize(sp(9.5f));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setFakeBoldText(true);
    }

    private float dp(float v) {
        return v * density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }

    public void setData(List<StatsDb.DayStat> days) {
        if (days == null || days.isEmpty()) {
            values = new long[0];
            labels = new String[0];
            invalidate();
            return;
        }
        int n = days.size();
        values = new long[n];
        labels = new String[n];
        Calendar calendar = Calendar.getInstance();
        for (int i = 0; i < n; i++) {
            StatsDb.DayStat day = days.get(i);
            values[i] = day.count;
            if (i == n - 1) {
                labels[i] = "今天";
            } else {
                try {
                    String[] parts = day.day.split("-");
                    calendar.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1,
                            Integer.parseInt(parts[2]));
                    labels[i] = "周" + WEEK[calendar.get(Calendar.DAY_OF_WEEK) - 1];
                } catch (Throwable t) {
                    labels[i] = "";
                }
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = values.length;
        if (n == 0) {
            return;
        }
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        float labelArea = dp(22);
        float valueArea = dp(14);
        float top = valueArea;
        float bottom = height - labelArea;
        float slot = width / n;
        float barWidth = Math.min(slot * 0.46f, dp(28));

        long max = 1;
        for (long v : values) {
            if (v > max) {
                max = v;
            }
        }

        RectF rect = new RectF();
        for (int i = 0; i < n; i++) {
            float centerX = slot * i + slot / 2f;
            float left = centerX - barWidth / 2f;
            float right = centerX + barWidth / 2f;

            rect.set(left, top, right, bottom);
            canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, trackPaint);

            if (values[i] > 0) {
                float barHeight = (bottom - top) * (values[i] / (float) max);
                barHeight = Math.max(barHeight, dp(4));
                float barTop = bottom - barHeight;
                rect.set(left, barTop, right, bottom);
                canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f,
                        i == n - 1 ? todayPaint : barPaint);

                String valueText = String.valueOf(values[i]);
                canvas.drawText(valueText, centerX, barTop - dp(4), valuePaint);
            }

            if (labels[i] != null && !labels[i].isEmpty()) {
                labelPaint.setColor(i == n - 1
                        ? getResources().getColor(R.color.primary)
                        : getResources().getColor(R.color.sub));
                labelPaint.setFakeBoldText(i == n - 1);
                canvas.drawText(labels[i], centerX, height - dp(6), labelPaint);
            }
        }
    }
}
