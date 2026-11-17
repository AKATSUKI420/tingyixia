package com.scrollguard;

import android.app.Application;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
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
 * 问题3：把 iOS 深色模式的几个硬指标钉住，免得以后改样式又滑回 Material 默认。
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
    public void paletteMatchesIOSDark() {
        assertEquals(0xFF000000, app.getColor(R.color.bg));
        assertEquals(0xFF1C1C1E, app.getColor(R.color.card));
        assertEquals(0xFFFFFFFF, app.getColor(R.color.text));
        assertEquals(0xFF8E8E93, app.getColor(R.color.dim));
        assertEquals(0xFF38383A, app.getColor(R.color.separator));
        assertEquals(0xFF0A84FF, app.getColor(R.color.accent));
        assertEquals(0xFF30D158, app.getColor(R.color.good));
        assertEquals(0xFFFF453A, app.getColor(R.color.bad));
    }

    @Test
    public void cardsUseTwentyTwoDpCorners() {
        LinearLayout card = Ui.card(app);
        GradientDrawable bg = (GradientDrawable) card.getBackground();
        assertEquals(Ui.dp(app, 22), bg.getCornerRadius(), 1f);
    }

    @Test
    public void rowsAreFiftyFourDpWithSeventeenSpTitle() {
        LinearLayout row = Ui.row(app, "拦截范围", "只算抖音 / B 站里的竖屏滑动", null);
        assertEquals(Ui.dp(app, 54), row.getMinimumHeight());

        LinearLayout col = (LinearLayout) row.getChildAt(0);
        TextView title = (TextView) col.getChildAt(0);
        TextView sub = (TextView) col.getChildAt(1);
        assertEquals(Ui.sp(app, 17), title.getTextSize(), 1f);
        assertEquals(Ui.sp(app, 13), sub.getTextSize(), 1f);
    }

    @Test
    public void buttonsAreCapsules() {
        TextView b = Ui.button(app, "开起来", false);
        assertEquals(Ui.dp(app, 52), b.getMinHeight());
        assertEquals(Ui.sp(app, 17), b.getTextSize(), 1f);
        assertTrue("按钮该有按压波纹", b.getBackground() instanceof RippleDrawable);
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
    public void statusDotIsEightDp() {
        GradientDrawable dot = Ui.dot(app, 0xFFFF0000);
        assertEquals(Ui.dp(app, 8), dot.getIntrinsicWidth());
        assertEquals(Ui.dp(app, 8), dot.getIntrinsicHeight());
    }

    @Test
    public void mainPageUsesLargeTitleAndCapsuleButton() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();

        TextView title = a.findViewById(R.id.page_title);
        assertNotNull("大标题没挂上", title);
        assertEquals(Ui.sp(app, 34), title.getTextSize(), 1f);

        TextView status = a.findViewById(R.id.status_line);
        assertNotNull("状态行没挂上", status);
        assertEquals(Ui.sp(app, 15), status.getTextSize(), 1f);
        assertNotNull("状态行前面该有个小圆点", status.getCompoundDrawablesRelative()[0]);

        TextView btn = a.findViewById(R.id.primary_button);
        assertNotNull("主按钮没挂上", btn);
        assertEquals("开起来", btn.getText().toString());
        assertEquals(Ui.dp(app, 52), btn.getMinHeight());
    }
}
