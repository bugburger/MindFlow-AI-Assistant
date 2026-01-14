package com.example.mindflow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import java.util.LinkedList;

public class VoiceLineView extends View {
    private Paint paint;
    private LinkedList<Integer> volumeList = new LinkedList<>();
    private int maxVolumeCount = 30; // 屏幕上保留多少个条
    private int rectWidth = 10;      // 每个条的宽度
    private int space = 8;           // 条之间的间距

    public VoiceLineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.parseColor("#6200EE")); // 波纹颜色 (深紫)
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.ROUND); // 圆角线条
    }

    // 接收音量 (0-100)
    public void addVolume(int volume) {
        volumeList.add(volume);
        if (volumeList.size() > maxVolumeCount) {
            volumeList.removeFirst();
        }
        invalidate(); // 刷新界面，触发 onDraw
    }

    public void clear() {
        volumeList.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int totalItemWidth = rectWidth + space;

        // 从右向左画
        int currentX = width - totalItemWidth;

        // 倒序遍历，让最新的音量显示在最右边
        for (int i = volumeList.size() - 1; i >= 0; i--) {
            int volume = volumeList.get(i);
            // 限制高度，防止爆出屏幕
            int barHeight = (int) ((float) volume / 100 * height * 0.8f);
            if (barHeight < 10) barHeight = 10; // 最小高度

            int top = (height - barHeight) / 2;
            int bottom = top + barHeight;

            RectF rect = new RectF(currentX, top, currentX + rectWidth, bottom);
            canvas.drawRoundRect(rect, 10, 10, paint);

            currentX -= totalItemWidth;
            if (currentX < 0) break;
        }
    }
}