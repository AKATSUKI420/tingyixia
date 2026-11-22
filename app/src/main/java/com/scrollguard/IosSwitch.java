package com.scrollguard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;

/**
 * 胶囊开关：51x31dp，白钮在轨道上滑。
 * 关着是浅灰轨道，打开是品牌蓝；滑动时纽扣会轻微拉长，像被带了一下。
 */
public class IosSwitch extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(IosSwitch view, boolean checked);
    }

    private static final int TRACK_W_DP = 51;
    private static final int TRACK_H_DP = 31;
    private static final float THUMB_DP = 27f;
    private static final float STRETCH_DP = 3.2f;
    private static final int TRACK_OFF = 0xFFEFEFED;
    private static final int TRACK_STROKE = 0xFFD4D4D8;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF thumbRect = new RectF();

    private final int trackOn;

    private boolean checked;
    private float fraction;
    private float stretch;
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
        trackOn = Ui.col(context, R.color.switch_on);
        shadowPaint.setColor(Color.TRANSPARENT);
        shadowPaint.setShadowLayer(dp(2), 0, dp(1), 0x33000000);
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
            stretch = 0f;
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
        final float from = fraction;
        final float span = Math.abs(target - from);
        animator = ValueAnimator.ofFloat(from, target);
        animator.setDuration(span < 0.01f ? 0 : 220);
        animator.setInterpolator(new DecelerateInterpolator(1.8f));
        animator.addUpdateListener(a -> {
            fraction = (float) a.getAnimatedValue();
            // 中间快、两头慢的那一下：纽扣拉长，松手回圆
            float progress = span < 0.01f ? 0f : Math.abs(fraction - from) / span;
            stretch = (float) Math.sin(progress * Math.PI) * dp(STRETCH_DP);
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

        trackPaint.setColor(blend(TRACK_OFF, trackOn, fraction));
        canvas.drawRoundRect(0, 0, w, h, radius, radius, trackPaint);

        // 0206 那版的细线：关着时描一圈，打开后淡出
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1));
        strokePaint.setColor(TRACK_STROKE);
        strokePaint.setAlpha((int) (255 * (1f - fraction) * (1f - fraction)));
        float inset = dp(0.5f);
        canvas.drawRoundRect(inset, inset, w - inset, h - inset, radius, radius, strokePaint);



        float thumbDiameter = dp(THUMB_DP);
        float thumbHeight = thumbDiameter;
        float thumbWidth = thumbDiameter + stretch;
        float margin = (h - thumbDiameter) / 2f;
        float travel = w - 2 * margin - thumbWidth;
        float left = margin + fraction * travel;
        float top = (h - thumbHeight) / 2f;
        float radiusThumb = thumbHeight / 2f;

        thumbRect.set(left, top, left + thumbWidth, top + thumbHeight);
        canvas.drawRoundRect(thumbRect, radiusThumb, radiusThumb, shadowPaint);
        thumbPaint.setColor(Color.WHITE);
        thumbRect.offset(0, -dp(0.5f));
        canvas.drawRoundRect(thumbRect, radiusThumb, radiusThumb, thumbPaint);
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
