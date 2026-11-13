package com.scrollguard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 六位数字键盘 + 六个点。设置密码、解锁都用它。
 */
public class PinPadView extends LinearLayout {

    public interface Listener {
        void onComplete(String pin);
    }

    private final TextView msg;
    private final LinearLayout dots;
    private final StringBuilder buf = new StringBuilder(6);
    private final int dotSize;
    private final int dotGap;
    private final int normalColor;
    private final int errorColor;

    private Listener listener;
    private boolean showingError = false;

    public PinPadView(Context c) {
        this(c, null);
    }

    public PinPadView(Context c, AttributeSet a) {
        super(c, a);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        LayoutInflater.from(c).inflate(R.layout.view_pinpad, this, true);

        msg = findViewById(R.id.pin_msg);
        dots = findViewById(R.id.pin_dots);
        dots.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        dotSize = Ui.dp(c, 11);
        dotGap = Ui.dp(c, 13);
        normalColor = Ui.col(c, R.color.dim);
        errorColor = Ui.col(c, R.color.bad);

        int[] ids = {
                R.id.k1, R.id.k2, R.id.k3, R.id.k4, R.id.k5, R.id.k6,
                R.id.k7, R.id.k8, R.id.k9, R.id.kc, R.id.k0, R.id.kd
        };
        for (int id : ids) {
            Button b = findViewById(id);
            if (b != null) {
                b.setOnClickListener(v -> onKey(b.getText().toString()));
            }
        }
        redrawDots();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setMessage(String text) {
        showingError = false;
        msg.setTextColor(normalColor);
        msg.setText(text == null ? "" : text);
    }

    public void error(String text) {
        showingError = true;
        buf.setLength(0);
        msg.setTextColor(errorColor);
        msg.setText(text);
        redrawDots();
    }

    public void clear() {
        buf.setLength(0);
        redrawDots();
    }

    private void onKey(String key) {
        if (showingError) {
            showingError = false;
            msg.setTextColor(normalColor);
            msg.setText("");
        }
        if ("清空".equals(key)) {
            buf.setLength(0);
            redrawDots();
            return;
        }
        if ("退格".equals(key)) {
            if (buf.length() > 0) {
                buf.deleteCharAt(buf.length() - 1);
            }
            redrawDots();
            return;
        }
        if (buf.length() >= 6) {
            return;
        }
        buf.append(key);
        redrawDots();

        if (buf.length() == 6) {
            final String pin = buf.toString();
            buf.setLength(0);
            // 让第六个点先画出来，再交出去
            postDelayed(() -> {
                if (listener != null) {
                    listener.onComplete(pin);
                } else {
                    redrawDots();
                }
            }, 90);
        }
    }

    private void redrawDots() {
        dots.removeAllViews();
        int filled = buf.length();
        for (int i = 0; i < 6; i++) {
            View v = new View(getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSize, dotSize);
            lp.leftMargin = dotGap / 2;
            lp.rightMargin = dotGap / 2;
            v.setLayoutParams(lp);
            v.setBackgroundResource(i < filled ? R.drawable.dot_on : R.drawable.dot_off);
            dots.addView(v);
        }
    }
}
