package com.scrollguard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 六位数字键盘 + 六个点。设置密码（带框的键）、解锁（落在玻璃上的无框数字）都用它。
 * 全部代码拼，不 inflate，方便两套样子切换。
 */
public class PinPadView extends LinearLayout {

    public interface Listener {
        void onComplete(String pin);
    }

    private static final String[] KEYS = {
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "清空", "0", "退格"
    };
    private static final int[] IDS = {
            R.id.k1, R.id.k2, R.id.k3, R.id.k4, R.id.k5, R.id.k6,
            R.id.k7, R.id.k8, R.id.k9, R.id.kc, R.id.k0, R.id.kd
    };

    private final TextView msg;
    private final LinearLayout dots;
    private final GridLayout grid;
    private final StringBuilder buf = new StringBuilder(6);
    private final int dotSize;
    private final int dotGap;
    private final int normalColor;
    private final int errorColor;

    private Listener listener;
    private boolean showingError = false;
    private boolean bare = false;

    public PinPadView(Context c) {
        this(c, null);
    }

    public PinPadView(Context c, AttributeSet a) {
        super(c, a);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);

        dotSize = Ui.dp(c, 10);
        dotGap = Ui.dp(c, 14);
        normalColor = Ui.col(c, R.color.dim);
        errorColor = Ui.col(c, R.color.bad);

        msg = new TextView(c);
        msg.setId(R.id.pin_msg);
        msg.setTextSize(13);
        msg.setTextColor(normalColor);
        msg.setGravity(Gravity.CENTER);
        msg.setIncludeFontPadding(false);
        msg.setMinHeight(Ui.dp(c, 18));
        msg.setVisibility(INVISIBLE);
        addView(msg, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        dots = new LinearLayout(c);
        dots.setId(R.id.pin_dots);
        dots.setOrientation(HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        dots.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dl.topMargin = Ui.dp(c, 22);
        addView(dots, dl);

        grid = new GridLayout(c);
        grid.setId(R.id.pin_keys);
        grid.setColumnCount(3);
        grid.setRowCount(4);
        grid.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        grid.setUseDefaultMargins(false);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        gl.topMargin = Ui.dp(c, 26);
        addView(grid, gl);

        for (int i = 0; i < KEYS.length; i++) {
            Button b = new Button(c);
            b.setId(IDS[i]);
            b.setText(KEYS[i]);
            b.setAllCaps(false);
            b.setIncludeFontPadding(false);
            b.setGravity(Gravity.CENTER);
            b.setTypeface(Ui.medium(c));
            b.setStateListAnimator(null);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setPadding(0, 0, 0, 0);
            b.setOnClickListener(v -> onKey(b.getText().toString()));
            // 三列等分：宽度交给权重，格子本身只定高
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 3), GridLayout.spec(i % 3, 1f));
            lp.width = 0;
            b.setLayoutParams(lp);
            grid.addView(b);
        }

        setBare(false);
        redrawDots();
    }

    /** 锁屏用：去掉按键的框，数字直接落在玻璃上。 */
    public void setBare(boolean on) {
        bare = on;
        Context c = getContext();
        int keyH = Ui.dp(c, on ? 66 : 56);
        int margin = Ui.dp(c, on ? 0 : 3);
        grid.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams gl = (LinearLayout.LayoutParams) grid.getLayoutParams();
        gl.topMargin = Ui.dp(c, on ? 14 : 14);
        grid.setLayoutParams(gl);

        for (int i = 0; i < grid.getChildCount(); i++) {
            Button b = (Button) grid.getChildAt(i);
            boolean label = b.getText().length() > 1;
            GridLayout.LayoutParams lp = (GridLayout.LayoutParams) b.getLayoutParams();
            lp.width = 0;
            lp.height = keyH;
            lp.setMargins(margin, margin, margin, margin);
            b.setLayoutParams(lp);

            if (on) {
                b.setBackground(null);
                b.setTextSize(label ? 13 : 26);
                b.setTextColor(label ? Ui.col(c, R.color.dim) : Ui.col(c, R.color.text));
                b.setTypeface(label ? Ui.medium(c) : android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
            } else {
                b.setBackgroundResource(R.drawable.key_bg);
                b.setTextSize(label ? 13 : 20);
                b.setTextColor(label ? Ui.col(c, R.color.dim) : Ui.col(c, R.color.text));
                b.setTypeface(Ui.medium(c));
            }
        }
        dots.setVisibility(on ? VISIBLE : VISIBLE);
        msg.setVisibility(showingError || (msg.getText() != null && msg.getText().length() > 0)
                ? VISIBLE : (on ? GONE : INVISIBLE));
        redrawDots();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setMessage(String text) {
        showingError = false;
        msg.setTextColor(normalColor);
        msg.setText(text == null ? "" : text);
        msg.setVisibility(text == null || text.isEmpty() ? (bare ? GONE : INVISIBLE) : VISIBLE);
    }

    public void error(String text) {
        showingError = true;
        buf.setLength(0);
        msg.setTextColor(errorColor);
        msg.setText(text);
        msg.setVisibility(VISIBLE);
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
            msg.setVisibility(bare ? GONE : INVISIBLE);
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
