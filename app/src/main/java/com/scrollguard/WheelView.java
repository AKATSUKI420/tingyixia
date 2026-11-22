package com.scrollguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * 五行的滚轮：上下各两行淡的，中间一行实心，后面一条圆角高亮带。
 * 拖动时整列跟着手指走，松手吸附到最近的一格；点哪行就选哪行。
 */
public class WheelView extends View {

    private static final int ROWS = 5;
    private static final int ROW_H_DP = 44;

    private final String[] labels;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bandRect = new RectF();
    private final Typeface medium;
    private final Typeface bold;
    private final int colorOn;
    private final int colorOff;
    private final int rowH;
    private final int halfH;
    private final int bandInset;
    private final float bandRadius;
    private final float sizeOn;
    private final float sizeOff;

    private int index;
    private int startIndex;
    private float downY;
    private float dragDy;
    private boolean dragging;

    public WheelView(Context c, int[] values, int initial) {
        super(c);
        labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = String.format(Locale.US, "%02d", values[i]);
        }
        index = Math.max(0, Math.min(values.length - 1, initial));
        startIndex = index;
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        bold = Typeface.create("sans-serif-medium", Typeface.BOLD);
        colorOn = Ui.col(c, R.color.text);
        colorOff = Ui.col(c, R.color.dim);
        band.setColor(Ui.col(c, R.color.card_pressed));

        float density = getResources().getDisplayMetrics().density;
        float scaled = getResources().getDisplayMetrics().scaledDensity;
        rowH = Math.round(ROW_H_DP * density);
        halfH = rowH * ROWS / 2;
        bandInset = Math.round(2 * density);
        bandRadius = 8 * density;
        sizeOn = 24 * scaled;
        sizeOff = 22 * scaled;

        setClickable(true);
        setFocusable(true);
    }

    public int index() {
        return index;
    }

    public void setIndex(int i) {
        index = Math.max(0, Math.min(labels.length - 1, i));
        startIndex = index;
        dragDy = 0;
        invalidate();
    }

    private int wrap(int i) {
        int n = labels.length;
        return ((i % n) + n) % n;
    }

    private float position() {
        return startIndex - dragDy / rowH;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                Math.max(rowH * ROWS, getSuggestedMinimumHeight()));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float cy = getHeight() / 2f;
        float pos = position();
        int center = Math.round(pos);

        bandRect.set(bandInset, cy - rowH / 2f, w - bandInset, cy + rowH / 2f);
        canvas.drawRoundRect(bandRect, bandRadius, bandRadius, band);

        text.setTextAlign(Paint.Align.CENTER);
        for (int k = -2; k <= 2; k++) {
            int i = center + k;
            boolean on = k == 0;
            text.setTextSize(on ? sizeOn : sizeOff);
            text.setColor(on ? colorOn : colorOff);
            text.setTypeface(on ? bold : medium);
            text.setAlpha(on ? 255 : (Math.abs(k) == 2 ? 110 : 200));
            float base = cy + (i - pos) * rowH
                    - (text.ascent() + text.descent()) / 2f;
            canvas.drawText(labels[wrap(i)], w / 2f, base, text);
        }
        text.setAlpha(255);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getY();
                startIndex = index;
                dragDy = 0f;
                dragging = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                dragDy = e.getY() - downY;
                if (Math.abs(dragDy) > rowH * 0.18f) {
                    dragging = true;
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    index = wrap(Math.round(position()));
                } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    int row = Math.round((e.getY() - getHeight() / 2f) / rowH);
                    index = wrap(index + row);
                }
                startIndex = index;
                dragDy = 0f;
                dragging = false;
                invalidate();
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    performClick();
                }
                return true;

            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
