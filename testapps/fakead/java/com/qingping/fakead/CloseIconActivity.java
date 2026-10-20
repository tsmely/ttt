package com.qingping.fakead;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * 测试「右下角无文字 ×」：广告文案齐全，关闭按钮是一个没有文字、
 * 没有 contentDescription 的 ImageView，只能靠位置 + 尺寸识别。
 */
public class CloseIconActivity extends Activity {

    private static final String TAG = "FakeCloseIcon";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "OPENED");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF2A3A35);

        TextView ad = new TextView(this);
        ad.setText("QQ音乐\n\n广告 · 立即下载\n上滑或点击按钮查看详情");
        ad.setTextColor(Color.WHITE);
        ad.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        ad.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams adLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        adLp.gravity = Gravity.CENTER;
        root.addView(ad, adLp);

        ImageView close = new ImageView(this);
        close.setImageDrawable(new CloseDrawable());
        close.setClickable(true);
        close.setContentDescription(null);
        close.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        close.setOnClickListener(v -> {
            Log.i(TAG, "CLOSED_BY_CLICK");
            finish();
        });
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dp(40), dp(40));
        closeLp.gravity = Gravity.BOTTOM | Gravity.END;
        closeLp.bottomMargin = dp(28);
        closeLp.rightMargin = dp(24);
        root.addView(close, closeLp);

        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** 白圆底 + 深色 ×，不依赖文字或资源 id。 */
    private static final class CloseDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        public void draw(Canvas canvas) {
            float w = getBounds().width();
            float h = getBounds().height();
            float cx = getBounds().centerX();
            float cy = getBounds().centerY();
            paint.setColor(0xCCFFFFFF);
            canvas.drawCircle(cx, cy, Math.min(w, h) / 2f, paint);
            paint.setColor(0xFF333333);
            paint.setStrokeWidth(Math.max(2f, w * 0.08f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            float d = Math.min(w, h) * 0.22f;
            canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint);
            canvas.drawLine(cx + d, cy - d, cx - d, cy + d, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}
