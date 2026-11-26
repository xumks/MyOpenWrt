package com.myopenwrt.app.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.myopenwrt.app.R;

/**
 * 页面背景：浅色/暗色渐变 + 三处柔和光斑，用于营造极光玻璃质感。
 */
public class AuroraBackgroundView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int startColor;
    private int midColor;
    private int endColor;
    private int blobBlue;
    private int blobTeal;
    private int blobPurple;

    public AuroraBackgroundView(Context context) {
        this(context, null);
    }

    public AuroraBackgroundView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        startColor = ContextCompat.getColor(getContext(), R.color.aurora_start);
        midColor = ContextCompat.getColor(getContext(), R.color.aurora_mid);
        endColor = ContextCompat.getColor(getContext(), R.color.aurora_end);
        blobBlue = ContextCompat.getColor(getContext(), R.color.blob_blue);
        blobTeal = ContextCompat.getColor(getContext(), R.color.blob_teal);
        blobPurple = ContextCompat.getColor(getContext(), R.color.blob_purple);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        bgPaint.setShader(new LinearGradient(0, 0, w, h,
                new int[]{startColor, midColor, endColor},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, bgPaint);

        float base = Math.max(w, h);
        drawBlob(canvas, w * 0.92f, h * 0.04f, base * 0.55f, blobBlue);
        drawBlob(canvas, -w * 0.10f, h * 0.30f, base * 0.46f, blobTeal);
        drawBlob(canvas, w * 0.16f, h * 0.96f, base * 0.55f, blobPurple);
    }

    private void drawBlob(Canvas canvas, float cx, float cy, float radius, int color) {
        int transparent = color & 0x00FFFFFF;
        blobPaint.setShader(new RadialGradient(cx, cy, radius, color, transparent,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, blobPaint);
    }
}
