package com.qingping.fakebrowser;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 测试用「浏览器落地页」。包名里带 browser，会被误触跳转规则识别为跳转目标。
 */
public class BrowserActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("FakeBrowser", "OPENED");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFF2F5F4);

        TextView text = new TextView(this);
        text.setText("广告落地页（模拟浏览器）\n如果看到这个页面，说明跳转没有被拦截");
        text.setTextColor(Color.DKGRAY);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        text.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        root.addView(text, lp);
        setContentView(root);
    }
}
