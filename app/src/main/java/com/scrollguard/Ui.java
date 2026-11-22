package com.scrollguard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 界面小零件的拼装工具。没有用任何第三方库，全部是系统控件。
 * 极简白：大留白、1px 细线、黑白加一点蓝；靠字号和间距分层，不靠色块。
 * 尺寸和 design/scrollguard-motion/app.css 对齐。
 */
public final class Ui {

    public static final float CARD_RADIUS_DP = 10f;
    public static final int ROW_MIN_HEIGHT_DP = 58;
    public static final int BUTTON_HEIGHT_DP = 52;
    public static final float BUTTON_RADIUS_DP = 10f;
    public static final int TITLE_SP = 44;
    public static final int ROW_TITLE_SP = 15;
    public static final int ROW_SUB_SP = 13;
    public static final int HEADER_SP = 11;
    public static final int VALUE_SP = 15;
    public static final int ICON_BOX_DP = 32;
    public static final int ICON_DP = 18;

    public static final int PRIMARY = 0;
    public static final int DANGER = 1;
    public static final int GHOST = 2;

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static int sp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics()));
    }

    public static int col(Context c, int res) {
        return c.getColor(res);
    }

    public static Typeface medium(Context c) {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    public static Typeface bold(Context c) {
        return Typeface.create("sans-serif", Typeface.BOLD);
    }

    public static void tabular(TextView t) {
        t.setFontFeatureSettings("tnum");
    }

    /** 全大写的目录小标题的字距。 */
    public static void tracking(TextView t) {
        t.setLetterSpacing(0.14f);
    }

    /** 一张白卡片：10dp 圆角 + 1px 细线。 */
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(c.getDrawable(R.drawable.card_bg));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 10);
        l.setLayoutParams(lp);
        return l;
    }

    /** 分组标题：11sp、灰、字距 .14em，贴着左边。 */
    public static TextView header(LinearLayout parent, String text) {
        Context c = parent.getContext();
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(HEADER_SP);
        t.setTextColor(col(c, R.color.dim));
        t.setTypeface(medium(c));
        tracking(t);
        t.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(c, 2);
        lp.topMargin = dp(c, 22);
        parent.addView(t, lp);
        return t;
    }

    /** 1px 细线，左边缩进 14dp，右边贴到卡片边缘。 */
    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(col(c, R.color.line));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f)));
        lp.leftMargin = dp(c, 14);
        v.setLayoutParams(lp);
        return v;
    }

    /** 32dp 的图标格：透明底 + 1px 细线 + 18dp 线性图标。 */
    public static FrameLayout iconBox(Context c, int iconRes) {
        FrameLayout box = new FrameLayout(c);
        box.setBackground(c.getDrawable(R.drawable.icon_box_bg));
        ImageView iv = new ImageView(c);
        iv.setImageResource(iconRes);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(
                dp(c, ICON_DP), dp(c, ICON_DP), Gravity.CENTER);
        box.addView(iv, ip);
        return box;
    }

    /** 一行：图标格 + 标题（可带小字）+ 右侧控件。 */
    public static LinearLayout row(Context c, int iconRes, String title, String sub, View right) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(c, ROW_MIN_HEIGHT_DP));
        r.setPadding(dp(c, 12), dp(c, 11), dp(c, 13), dp(c, 11));
        r.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        if (iconRes != 0) {
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                    dp(c, ICON_BOX_DP), dp(c, ICON_BOX_DP));
            ip.rightMargin = dp(c, 12);
            r.addView(iconBox(c, iconRes), ip);
        }

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(c);
        t.setText(title);
        t.setTextSize(ROW_TITLE_SP);
        t.setTextColor(col(c, R.color.text));
        t.setTypeface(medium(c));
        t.setIncludeFontPadding(false);
        col.addView(t);

        if (sub != null && sub.length() > 0) {
            TextView s = new TextView(c);
            s.setText(sub);
            s.setTextSize(ROW_SUB_SP);
            s.setTextColor(col(c, R.color.dim));
            s.setIncludeFontPadding(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(c, 2);
            col.addView(s, lp);
        }

        r.addView(col, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (right != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(c, 10);
            r.addView(right, lp);
            if (right instanceof TextView) {
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                cp.leftMargin = dp(c, 6);
                r.addView(chevron(c), cp);
            }
        }
        return r;
    }

    public static LinearLayout row(Context c, int iconRes, String title, String sub) {
        return row(c, iconRes, title, sub, chevron(c));
    }

    public static TextView value(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(VALUE_SP);
        t.setTextColor(col(c, R.color.dim));
        t.setIncludeFontPadding(false);
        tabular(t);
        return t;
    }

    /** 行尾的强调值（比如「中心放大」），蓝字。 */
    public static TextView valueAccent(Context c, String text) {
        TextView t = value(c, text);
        t.setTextColor(col(c, R.color.accent));
        return t;
    }

    /** 细线胶囊：例如「被拦 3 次」。 */
    public static TextView pill(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(col(c, R.color.dim));
        t.setTypeface(medium(c));
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 9), dp(c, 4), dp(c, 9), dp(c, 4));
        t.setBackground(c.getDrawable(R.drawable.pill_bg));
        return t;
    }

    /** 权限那一列：小圆点 + 灰字。 */
    public static LinearLayout state(Context c, String text, boolean on) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? col(c, R.color.accent) : col(c, R.color.dim2));
        dot.setBackground(g);
        LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(dp(c, 6), dp(c, 6));
        dp1.rightMargin = dp(c, 7);
        l.addView(dot, dp1);
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(col(c, R.color.dim));
        t.setIncludeFontPadding(false);
        l.addView(t);
        return l;
    }

    /** 大按钮：52dp 高、10dp 圆角、白字；主色黑、危险红、幽灵白底细线。 */
    public static TextView button(Context c, String text, int kind) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(15);
        t.setTypeface(medium(c));
        t.setLetterSpacing(0.04f);
        t.setIncludeFontPadding(false);
        t.setMinHeight(dp(c, BUTTON_HEIGHT_DP));
        t.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        t.setClickable(true);
        t.setFocusable(true);

        int ripple = 0x33FFFFFF;
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(dp(c, BUTTON_RADIUS_DP));
        if (kind == GHOST) {
            fill.setColor(col(c, R.color.card));
            fill.setStroke(Math.max(1, dp(c, 1f)), col(c, R.color.line));
            t.setTextColor(col(c, R.color.text));
            ripple = 0x14000000;
        } else {
            fill.setColor(col(c, kind == DANGER ? R.color.bad : R.color.text));
            t.setTextColor(Color.WHITE);
        }
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), fill, fill));
        return t;
    }

    /** 状态行前面那个小圆点。 */
    public static GradientDrawable dot(Context c, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setSize(dp(c, 8), dp(c, 8));
        return g;
    }

    /** 空心的小圆环：关着的时候用。 */
    public static GradientDrawable ring(Context c, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(android.graphics.Color.TRANSPARENT);
        g.setStroke(dp(c, 1), color);
        g.setSize(dp(c, 8), dp(c, 8));
        return g;
    }

    public static ImageView chevron(Context c) {
        ImageView v = new ImageView(c);
        v.setImageResource(R.drawable.ic_chevron);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 14), dp(c, 14)));
        return v;
    }

    /** 4dp 高的进度条：轨道 + 蓝色填充，按比例画。 */
    public static Bar bar(Context c, float fraction) {
        Bar b = new Bar(c);
        b.setFraction(fraction);
        return b;
    }

    public static final class Bar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float fraction;

        Bar(Context c) {
            super(c);
            track.setColor(Ui.col(c, R.color.line));
            fill.setColor(Ui.col(c, R.color.accent));
            setBackground(null);
        }

        public void setFraction(float f) {
            fraction = Math.max(0f, Math.min(1f, f));
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, h);
            setMeasuredDimension(getMeasuredWidth(), Ui.dp(getContext(), 4));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float h = getHeight();
            float radius = h / 2f;
            r.set(0, 0, getWidth(), h);
            canvas.drawRoundRect(r, radius, radius, track);
            if (fraction > 0f) {
                r.set(0, 0, getWidth() * fraction, h);
                canvas.drawRoundRect(r, radius, radius, fill);
            }
        }
    }

    /** 给整行加点击和系统水波纹，卡片背景仍由父容器负责。 */
    public static void tap(View v, Runnable action) {
        v.setClickable(true);
        v.setFocusable(true);
        Context c = v.getContext();
        TypedArray a = c.obtainStyledAttributes(
                new int[]{android.R.attr.selectableItemBackground});
        int res = a.getResourceId(0, 0);
        a.recycle();
        if (res != 0 && v.getForeground() == null) {
            v.setForeground(c.getDrawable(res));
        }
        v.setOnClickListener(x -> action.run());
    }

    public static TextView plain(Context c, String text, int sizeSp, int colorRes) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(col(c, colorRes));
        t.setIncludeFontPadding(false);
        return t;
    }

    public static LinearLayout.LayoutParams wide(int heightDp, Context c) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, heightDp));
    }

    public static void gap(View v, int topDp) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).topMargin = dp(v.getContext(), topDp);
        }
    }
}
