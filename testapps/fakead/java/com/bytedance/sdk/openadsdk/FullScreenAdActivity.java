package com.bytedance.sdk.openadsdk;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 类名伪装成穿山甲广告 SDK 的全屏 Activity，用来验证「插屏广告自动关闭」。
 * 注意：这只是测试代码，和字节跳动没有任何关系。
 */
public class FullScreenAdActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("FakeInterstitial", "OPENED");
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF3A1F1F);

        TextView text = new TextView(this);
        text.setText("插屏广告（测试）\n类名属于广告 SDK");
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        text.setGravity(Gravity.CENTER);
        root.addView(text, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        Log.i("FakeInterstitial", "CLOSED");
        super.onDestroy();
    }
}
