package com.scrollguard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 界面小零件的拼装工具。没有用任何第三方库，全部是系统控件。
 * 尺寸和颜色按 iOS 深色模式的分组列表来做：纯黑底、22dp 卡片、54dp 行高、胶囊按钮。
 */
public final class Ui {

    public static final float CARD_RADIUS_DP = 22f;
    public static final int ROW_MIN_HEIGHT_DP = 54;
    public static final int BUTTON_HEIGHT_DP = 52;
    public static final float BUTTON_RADIUS_DP = 26f;
    public static final int TITLE_SP = 34;
    public static final int ROW_TITLE_SP = 17;
    public static final int ROW_SUB_SP = 13;
    public static final int HEADER_SP = 13;

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

    /** 一张圆角卡片，自带上方间距。 */
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(c.getDrawable(R.drawable.card_bg));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8);
        l.setLayoutParams(lp);
        return l;
    }

    /** 分组标题：小号、灰色、贴着卡片左边。 */
    public static void header(LinearLayout parent, String text) {
        Context c = parent.getContext();
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(HEADER_SP);
        t.setTextColor(col(c, R.color.dim));
        t.setTypeface(medium(c));
        t.setLetterSpacing(0.02f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(c, 16);
        lp.topMargin = dp(c, 24);
        parent.addView(t, lp);
    }

    /** iOS 的发丝分隔线：1px 高，左边缩进 16dp，右边贴到卡片边缘。 */
    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(col(c, R.color.separator));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.5f)));
        lp.leftMargin = dp(c, 16);
        lp.rightMargin = 0;
        v.setLayoutParams(lp);
        return v;
    }

    /** 一行：左边标题（可带小字），右边一个控件。 */
    public static LinearLayout row(Context c, String title, String sub, View right) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(c, ROW_MIN_HEIGHT_DP));
        r.setPadding(dp(c, 16), dp(c, 11), dp(c, 16), dp(c, 11));
        r.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(c);
        t.setText(title);
        t.setTextSize(ROW_TITLE_SP);
        t.setTextColor(col(c, R.color.text));
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
            lp.topMargin = dp(c, 3);
            col.addView(s, lp);
        }

        r.addView(col, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (right != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(c, 12);
            r.addView(right, lp);
        }
        return r;
    }

    public static TextView value(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(ROW_TITLE_SP);
        t.setTextColor(col(c, R.color.dim));
        t.setIncludeFontPadding(false);
        return t;
    }

    /** iOS 风格的状态胶囊：已开是低饱和绿，未开是低饱和红。 */
    public static TextView chip(Context c, String text, boolean ok) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(medium(c));
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 10), dp(c, 5), dp(c, 10), dp(c, 5));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 100));
        if (ok) {
            g.setColor(0x1F30D158);
            t.setTextColor(col(c, R.color.good));
        } else {
            g.setColor(0x1FFF453A);
            t.setTextColor(col(c, R.color.bad));
        }
        t.setBackground(g);
        return t;
    }

    /** 大按钮：52dp 高、胶囊形、白字，用白色波纹做按压反馈。 */
    public static TextView button(Context c, String text, boolean danger) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(17);
        t.setTypeface(medium(c));
        t.setTextColor(col(c, R.color.text));
        t.setIncludeFontPadding(false);
        t.setMinHeight(dp(c, BUTTON_HEIGHT_DP));
        t.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        t.setClickable(true);
        t.setFocusable(true);

        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(dp(c, BUTTON_RADIUS_DP));
        fill.setColor(danger ? col(c, R.color.bad) : col(c, R.color.accent));

        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(c, BUTTON_RADIUS_DP));
        mask.setColor(0xFFFFFFFF);

        t.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF), fill, mask));
        return t;
    }

    /** 状态行前面那个 8dp 的小圆点。 */
    public static GradientDrawable dot(Context c, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setSize(dp(c, 8), dp(c, 8));
        return g;
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
        if (res != 0) {
            v.setForeground(c.getDrawable(res));
        }
        v.setOnClickListener(x -> action.run());
    }

    public static TextView plain(Context c, String text, int sizeSp, int colorRes) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(col(c, colorRes));
        return t;
    }
}
