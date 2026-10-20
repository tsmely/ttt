package com.qingping.fakead;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 测试用「开屏广告」：右上角有一个每秒递减的「跳过 5」按钮，
 * 无障碍服务如果识别正确，会替我们点掉它，并打印 FakeAd/SKIPPED。
 */
public class AdActivity extends Activity {

    private static final String TAG = "FakeAd";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView skipView;
    private int remain = 6;

    private final Runnable countdown = new Runnable() {
        @Override
        public void run() {
            remain--;
            if (remain <= 0) {
                Log.i(TAG, "AUTO_CLOSE");
                finish();
                return;
            }
            if (skipView != null) {
                skipView.setText("跳过 " + remain);
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF101820);

        TextView title = new TextView(this);
        title.setText("全屏开屏广告\n（测试用）");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.CENTER;
        root.addView(title, titleLp);

        skipView = new TextView(this);
        skipView.setText("跳过 " + remain);
        skipView.setTextColor(Color.WHITE);
        skipView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        skipView.setGravity(Gravity.CENTER);
        skipView.setPadding(dp(18), dp(9), dp(18), dp(9));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x55FFFFFF);
        pill.setCornerRadius(dp(20));
        skipView.setBackground(pill);
        skipView.setClickable(true);
        skipView.setOnClickListener(v -> {
            Log.i(TAG, "SKIPPED");
            Toast.makeText(this, "广告已跳过", Toast.LENGTH_SHORT).show();
            finish();
        });
        FrameLayout.LayoutParams skipLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        skipLp.gravity = Gravity.TOP | Gravity.END;
        skipLp.topMargin = dp(48);
        skipLp.rightMargin = dp(20);
        root.addView(skipView, skipLp);

        Button jump = new Button(this);
        jump.setText("模拟误触跳转（打开浏览器）");
        jump.setAllCaps(false);
        jump.setOnClickListener(v -> {
            Log.i(TAG, "JUMP");
            try {
                Intent intent = new Intent();
                intent.setClassName("com.qingping.fakebrowser",
                        "com.qingping.fakebrowser.BrowserActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Throwable t) {
                Log.e(TAG, "jump failed", t);
                Toast.makeText(this, "未安装测试浏览器", Toast.LENGTH_SHORT).show();
            }
        });
        FrameLayout.LayoutParams jumpLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        jumpLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        jumpLp.bottomMargin = dp(60);
        root.addView(jump, jumpLp);

        setContentView(root);
        handler.postDelayed(countdown, 1000);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
