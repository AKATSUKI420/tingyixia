package com.scrollguard;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 界面小零件的拼装工具。没有用任何第三方库，全部是系统控件。
 */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static int col(Context c, int res) {
        return c.getColor(res);
    }

    /** 一张圆角卡片，自带上方间距 */
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

    public static void header(LinearLayout parent, String text) {
        Context c = parent.getContext();
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(col(c, R.color.dim));
        t.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(c, 6);
        lp.topMargin = dp(c, 26);
        parent.addView(t, lp);
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(0x14FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f)));
        lp.leftMargin = dp(c, 16);
        lp.rightMargin = dp(c, 16);
        v.setLayoutParams(lp);
        return v;
    }

    /** 一行：左边标题（可带小字），右边一个控件 */
    public static LinearLayout row(Context c, String title, String sub, View right) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(c, 16), dp(c, 15), dp(c, 16), dp(c, 15));
        r.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(c);
        t.setText(title);
        t.setTextSize(15);
        t.setTextColor(col(c, R.color.text));
        col.addView(t);

        if (sub != null && sub.length() > 0) {
            TextView s = new TextView(c);
            s.setText(sub);
            s.setTextSize(12);
            s.setTextColor(col(c, R.color.dim));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(c, 4);
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
        t.setTextSize(14);
        t.setTextColor(col(c, R.color.dim));
        return t;
    }

    public static TextView chip(Context c, String text, boolean ok) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(12);
        t.setPadding(dp(c, 11), dp(c, 5), dp(c, 11), dp(c, 5));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 20));
        if (ok) {
            g.setColor(0x1F4ADE80);
            g.setStroke(dp(c, 1), 0x554ADE80);
            t.setTextColor(col(c, R.color.good));
        } else {
            g.setColor(0x1FFF6B57);
            g.setStroke(dp(c, 1), 0x55FF6B57);
            t.setTextColor(col(c, R.color.bad));
        }
        t.setBackground(g);
        return t;
    }

    /** 大按钮样式的 TextView（比 Button 好控样式） */
    public static TextView button(Context c, String text, boolean danger) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(c, 16), 0, dp(c, 16));
        t.setClickable(true);
        t.setFocusable(true);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 14));
        if (danger) {
            g.setColor(0x14FF6B57);
            g.setStroke(dp(c, 1), 0x66FF6B57);
            t.setTextColor(col(c, R.color.bad));
        } else {
            g.setColor(0x1F4ADE80);
            g.setStroke(dp(c, 1), 0x664ADE80);
            t.setTextColor(col(c, R.color.good));
        }
        t.setBackground(g);
        return t;
    }

    public static TextView plain(Context c, String text, int sizeSp, int colorRes) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(col(c, colorRes));
        return t;
    }
}
