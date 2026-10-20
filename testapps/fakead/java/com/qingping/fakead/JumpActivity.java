package com.qingping.fakead;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 没有「跳过」按钮的普通页面，用来单独测试「误触跳转拦截」。
 */
public class JumpActivity extends Activity {

    private static final String TAG = "FakeJump";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "OPENED");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF1B2430);

        TextView text = new TextView(this);
        text.setText("普通页面\n（这里没有跳过按钮）");
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        text.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.gravity = Gravity.CENTER;
        root.addView(text, textLp);

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
                Toast.makeText(this, "未安装测试浏览器", Toast.LENGTH_SHORT).show();
            }
        });
        FrameLayout.LayoutParams jumpLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        jumpLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        jumpLp.bottomMargin = dp(80);
        root.addView(jump, jumpLp);

        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
