package com.scrollguard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;

/**
 * iOS 那种胶囊开关：51x31dp，轨道从灰到绿，白色圆钮滑过去。
 * 自己画是为了不依赖 AndroidX 的 SwitchCompat，也和系统 Material 开关拉开差距。
 */
public class IosSwitch extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(IosSwitch view, boolean checked);
    }

    private static final int TRACK_W_DP = 51;
    private static final int TRACK_H_DP = 31;
    private static final float THUMB_DP = 27f;
    private static final int TRACK_OFF = 0xFFE9E9EA;
    private static final int TRACK_ON = 0xFF34C759;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean checked;
    private float fraction;
    private ValueAnimator animator;
    private OnCheckedChangeListener listener;

    public IosSwitch(Context context) {
        this(context, null);
    }

    public IosSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setFocusable(true);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setContentDescription("开关");
        shadowPaint.setColor(Color.TRANSPARENT);
        shadowPaint.setShadowLayer(dp(2), 0, dp(1), 0x40000000);
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean value) {
        if (checked == value) {
            return;
        }
        checked = value;
        float target = value ? 1f : 0f;
        if (!isAttachedToWindow()) {
            fraction = target;
            invalidate();
        } else {
            animateTo(target);
        }
        announceState();
        if (listener != null) {
            listener.onCheckedChanged(this, checked);
        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener l) {
        listener = l;
    }

    private void animateTo(float target) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(fraction, target);
        animator.setDuration(180);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            fraction = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void announceState() {
        setSelected(checked);
        setContentDescription(checked ? "已打开" : "已关闭");
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(dp(TRACK_W_DP), dp(TRACK_H_DP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float radius = h / 2f;

        trackPaint.setColor(blend(TRACK_OFF, TRACK_ON, fraction));
        canvas.drawRoundRect(0, 0, w, h, radius, radius, trackPaint);

        float thumbDiameter = dp(THUMB_DP);
        float thumbRadius = thumbDiameter / 2f;
        float margin = (h - thumbDiameter) / 2f;
        float cx = margin + thumbRadius + fraction * (w - 2 * margin - 2 * thumbRadius);
        float cy = h / 2f;

        canvas.drawCircle(cx, cy + dp(1), thumbRadius, shadowPaint);
        thumbPaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, thumbRadius, thumbPaint);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        setChecked(!checked);
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return "android.widget.Switch";
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checked);
    }

    private int dp(float v) {
        return Ui.dp(getContext(), v);
    }

    private static int blend(int from, int to, float f) {
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * f);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * f);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f);
        return Color.rgb(r, g, b);
    }
}
