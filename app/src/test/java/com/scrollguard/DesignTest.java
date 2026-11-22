package com.scrollguard;

import android.app.Application;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 把极简白的几个硬指标钉住，免得以后改样式又滑回 Material 默认。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DesignTest {

    private Application app;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        Prefs.wipe(app);
        Pin.save(app, "123456");
    }

    @Test
    public void paletteIsMinimalWhite() {
        assertEquals(0xFFFAFAF9, app.getColor(R.color.bg));
        assertEquals(0xFFFFFFFF, app.getColor(R.color.card));
        assertEquals(0xFFF4F4F2, app.getColor(R.color.card_pressed));
        assertEquals(0xFF18181B, app.getColor(R.color.text));
        assertEquals(0xFF6B6B73, app.getColor(R.color.dim));
        assertEquals(0xFF8A8A92, app.getColor(R.color.dim2));
        assertEquals(0xFFE4E4E7, app.getColor(R.color.separator));
        assertEquals(0xFF2563EB, app.getColor(R.color.accent));
        assertEquals(0xFFDC2626, app.getColor(R.color.bad));
        assertEquals(0xFFB45309, app.getColor(R.color.warn));
    }

    @Test
    public void cardsAreTenDp() {
        LinearLayout card = Ui.card(app);
        GradientDrawable bg = (GradientDrawable) card.getBackground();
        assertEquals(Ui.dp(app, 10), bg.getCornerRadius(), 1f);
        assertEquals(0xFFFFFFFF, app.getColor(R.color.card));
    }

    @Test
    public void rowsAreFiftyEightDpWithFifteenSpTitle() {
        LinearLayout row = Ui.row(app, R.drawable.ic_lock, "自动解锁", "只能输密码",
                Ui.value(app, "不自动"));
        assertEquals(Ui.dp(app, 58), row.getMinimumHeight());

        FrameLayout box = (FrameLayout) row.getChildAt(0);
        assertEquals("图标格 32dp", Ui.dp(app, 32),
                box.getLayoutParams().width);

        LinearLayout col = (LinearLayout) row.getChildAt(1);
        TextView title = (TextView) col.getChildAt(0);
        TextView sub = (TextView) col.getChildAt(1);
        assertEquals(Ui.sp(app, 15), title.getTextSize(), 1f);
        assertEquals(Ui.sp(app, 13), sub.getTextSize(), 1f);
        assertEquals("标题该是正文黑", app.getColor(R.color.text), title.getCurrentTextColor());
        assertEquals("小字该是次级灰", app.getColor(R.color.dim), sub.getCurrentTextColor());
    }

    @Test
    public void buttonsAreFiftyTwoDp() {
        TextView primary = Ui.button(app, "开始", Ui.PRIMARY);
        assertEquals(Ui.dp(app, 52), primary.getMinHeight());
        assertEquals(Ui.sp(app, 15), primary.getTextSize(), 1f);
        assertEquals("实心按钮上的字要是白的", 0xFFFFFFFF, primary.getCurrentTextColor());
        assertTrue("按钮该有按压波纹", primary.getBackground() instanceof RippleDrawable);

        TextView danger = Ui.button(app, "关闭", Ui.DANGER);
        assertEquals(0xFFFFFFFF, danger.getCurrentTextColor());

        TextView ghost = Ui.button(app, "换密码", Ui.GHOST);
        assertEquals("白底按钮的字是正文黑", app.getColor(R.color.text),
                ghost.getCurrentTextColor());
        assertEquals(Ui.dp(app, 52), ghost.getMinHeight());
        assertEquals(Ui.sp(app, 15), ghost.getTextSize(), 1f);
    }

    @Test
    public void switchIsIOSSizeAndToggles() {
        IosSwitch sw = new IosSwitch(app);
        sw.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        assertEquals(Ui.dp(app, 51), sw.getMeasuredWidth());
        assertEquals(Ui.dp(app, 31), sw.getMeasuredHeight());

        final boolean[] changed = {false};
        sw.setOnCheckedChangeListener((v, checked) -> changed[0] = checked);
        sw.performClick();
        assertTrue(sw.isChecked());
        assertTrue("开关回调没跑", changed[0]);
        sw.performClick();
        assertFalse(sw.isChecked());
    }

    @Test
    public void dotsAreEightDp() {
        GradientDrawable dot = Ui.dot(app, 0xFF000000);
        assertEquals(Ui.dp(app, 8), dot.getIntrinsicWidth());
        assertEquals(Ui.dp(app, 8), dot.getIntrinsicHeight());

        GradientDrawable ring = Ui.ring(app, 0xFF000000);
        assertEquals(Ui.dp(app, 8), ring.getIntrinsicWidth());
    }

    @Test
    public void mainPageUsesHeroTitleAndStartButton() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();

        TextView title = a.findViewById(R.id.page_title);
        assertNotNull("大标题没挂上", title);
        assertEquals(Ui.sp(app, 44), title.getTextSize(), 1f);
        assertEquals("页面大标题该是正文黑", app.getColor(R.color.text),
                title.getCurrentTextColor());

        TextView status = a.findViewById(R.id.status_line);
        assertNotNull("状态行没挂上", status);
        assertEquals(Ui.sp(app, 13), status.getTextSize(), 1f);
        assertEquals("状态行该是次级灰", app.getColor(R.color.dim),
                status.getCurrentTextColor());

        TextView btn = a.findViewById(R.id.primary_button);
        assertNotNull("主按钮没挂上", btn);
        assertEquals("开始", btn.getText().toString());
        assertEquals(Ui.dp(app, 52), btn.getMinHeight());
    }

    @Test
    public void rulesPageHasThreeGhostButtons() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        click(a.findViewById(R.id.tabbar), "规则");

        View root = a.findViewById(android.R.id.content);
        assertTrue(hasText(root, "解锁"));
        assertTrue(hasText(root, "动画"));
        assertTrue(hasText(root, "目标 App"));
        assertTrue(hasText(root, "权限"));
        assertTrue(hasText(root, "关于停一下"));
        assertTrue(hasText(root, "试一下拦截"));
        assertTrue(hasText(root, "换密码"));
        assertTrue(hasText(root, "忘记密码"));
        assertFalse("首页的仪表盘不该在规则页", hasText(root, "今天刷了"));
    }

    private static void click(View v, String label) {
        TextView t = find(v, label);
        assertNotNull("找不到 " + label, t);
        View target = t;
        while (target != null && !target.isClickable()) {
            target = target.getParent() instanceof View ? (View) target.getParent() : null;
        }
        (target == null ? t : target).performClick();
        org.robolectric.shadows.ShadowLooper.shadowMainLooper().idle();
    }

    private static TextView find(View v, String label) {
        if (v instanceof TextView && label.contentEquals(((TextView) v).getText())) {
            return (TextView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView t = find(g.getChildAt(i), label);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    private static boolean hasText(View v, String what) {
        return find(v, what) != null;
    }
}
