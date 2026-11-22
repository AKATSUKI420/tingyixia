package com.scrollguard;

import android.app.Application;
import android.app.Dialog;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * 首页和规则页：没密码先去设；状态行说真话；两页靠底栏切换；
 * 拦截时长用滚轮、自动解开和动效用弹窗，改完得存对。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityTest {

    private Application app;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        Prefs.wipe(app);
        GuardState st = GuardState.get();
        st.serviceRunning = false;
        st.countedMs = 0L;
        st.locked = false;
    }

    private MainActivity open() {
        return Robolectric.buildActivity(MainActivity.class).setup().get();
    }

    private MainActivity openRules() {
        Pin.save(app, "123456");
        MainActivity a = open();
        click(a.findViewById(R.id.tabbar), "规则");
        return a;
    }

    @Test
    public void withoutAPinItSendsYouToSetup() {
        open();

        Intent next = shadowOf(app).getNextStartedActivity();
        assertNotNull("没密码却让进首页", next);
        assertEquals(SetupActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void withAPinThePageOpens() {
        Pin.save(app, "123456");
        MainActivity a = open();

        assertNotNull(a);
        assertTrue("首页没搭起来", hasText(a.findViewById(android.R.id.content), "今天刷了"));
    }

    @Test
    public void offStateSaysSo() {
        Pin.save(app, "123456");
        MainActivity a = open();

        assertTrue(hasText(a.findViewById(android.R.id.content), "关闭状态"));
        assertTrue(hasText(a.findViewById(android.R.id.content), "OFF"));
        assertTrue(hasText(a.findViewById(android.R.id.content), "开始"));
    }

    @Test
    public void onWithoutAccessibilitySaysItCannotBlock() {
        Pin.save(app, "123456");
        Prefs.setEnabled(app, true);
        MainActivity a = open();

        assertTrue("得提醒无障碍没开",
                hasText(a.findViewById(android.R.id.content), "无障碍没开 · 拦不住"));
        assertTrue(hasText(a.findViewById(android.R.id.content), "关闭"));
    }

    @Test
    public void onWithoutOverlayPermissionSaysItCannotCover() {
        Pin.save(app, "123456");
        Prefs.setEnabled(app, true);
        android.provider.Settings.Secure.putString(
                app.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                new android.content.ComponentName(app, ShieldService.class).flattenToString());

        MainActivity a = open();

        assertTrue("得提醒悬浮窗没开",
                hasText(a.findViewById(android.R.id.content), "悬浮窗没开 · 盖不住"));
    }

    @Test
    public void rulesTabShowsEverySection() {
        MainActivity a = openRules();
        View root = a.findViewById(android.R.id.content);

        for (String s : new String[]{"解锁", "自动解锁", "动画", "遮罩动画", "动画速度",
                "目标 App", "抖音", "哔哩哔哩", "播放时才计时", "后台隐藏", "从最近任务里看不到",
                "权限", "检查更新", "当前版本 1.0.2",
                "试一下拦截", "换密码", "忘记密码", "本地数据 · 仅更新时联网"}) {
            assertTrue("规则页少了 " + s, hasText(root, s) || hasTextContains(root, s));
        }
        assertFalse("关于停一下 那一行删掉了", hasText(root, "关于停一下"));
        assertFalse("规则页不该有首页的仪表盘", hasText(root, "今天刷了"));
    }

    @Test
    public void todayTabComesBack() {
        MainActivity a = openRules();
        click(a.findViewById(R.id.tabbar), "今天");
        assertTrue("切不回来", hasText(a.findViewById(android.R.id.content), "今天刷了"));
    }

    @Test
    public void hideFromRecentsHasItsSwitch() {
        MainActivity a = openRules();
        View root = a.findViewById(android.R.id.content);

        assertTrue(hasText(root, "后台隐藏"));
        assertTrue(hasText(root, "从最近任务里看不到"));
        IosSwitch sw = switchNear(root, "后台隐藏");
        assertNotNull("找不到后台隐藏的开关", sw);
        assertFalse("默认应该是关的", sw.isChecked());

        sw.performClick();

        assertTrue("开关没写进 Prefs", Prefs.hideRecents(app));
        assertTrue("开关没反映到界面上", sw.isChecked());
    }

    @Test
    public void leavingFinishesTheTaskOnlyWhenHidden() {
        MainActivity a = openRules();
        a.onUserLeaveHint();
        assertFalse("关着就不该动任务", a.isFinishing());

        Prefs.setHideRecents(app, true);
        a.onUserLeaveHint();
        assertTrue("开着时离开应该把任务收掉", a.isFinishing());
    }

    /** 在「后台隐藏」那一行里找开关：从文字往上找，直到碰见 IosSwitch。 */
    private static IosSwitch switchNear(View root, String label) {
        for (TextView t : descendants(root, TextView.class)) {
            if (t.getText() != null && t.getText().toString().equals(label)) {
                View p = t;
                for (int i = 0; i < 6 && p != null; i++) {
                    IosSwitch s = firstChild(p, IosSwitch.class);
                    if (s != null) {
                        return s;
                    }
                    p = p.getParent() instanceof View ? (View) p.getParent() : null;
                }
            }
        }
        return null;
    }

    private static <T extends View> java.util.List<T> descendants(View root, Class<T> type) {
        java.util.List<T> out = new java.util.ArrayList<>();
        if (root == null) {
            return out;
        }
        if (type.isInstance(root)) {
            out.add(type.cast(root));
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                out.addAll(descendants(g.getChildAt(i), type));
            }
        }
        return out;
    }

    private static <T extends View> T firstChild(View root, Class<T> type) {
        for (T t : descendants(root, type)) {
            if (t != root) {
                return t;
            }
        }
        return null;
    }

    @Test
    public void limitIsPickedOnTheWheel() {
        Pin.save(app, "123456");
        MainActivity a = open();

        View row = findClickableRow(a.findViewById(android.R.id.content), "拦截时间");
        assertNotNull("首页找不到改时长那一行", row);
        row.performClick();
        ShadowLooper.shadowMainLooper().idle();

        Dialog d = ShadowDialog.getLatestDialog();
        assertNotNull("点开没弹窗", d);
        List<WheelView> wheels = wheels(d.getWindow().getDecorView());
        assertEquals("该有两个滚轮：时、分", 2, wheels.size());
        assertFalse("拦截时长没有 00:00 那句说明",
                hasText(d.getWindow().getDecorView(), "调到 00:00 就是不自动解锁"));

        wheels.get(0).setIndex(1);   // 1 小时
        wheels.get(1).setIndex(10);  // 分钟精确到 1 分钟
        click(d.getWindow().getDecorView(), "完成");

        assertEquals("1 小时 10 分", 70L, Prefs.limitMin(app));
        assertFalse("存下了该关窗", d.isShowing());
        assertTrue("首页该跟着变", hasText(a.findViewById(android.R.id.content), "1 小时 10 分"));
    }

    @Test
    public void limitNeverGoesBelowOneMinute() {
        Pin.save(app, "123456");
        MainActivity a = open();

        findClickableRow(a.findViewById(android.R.id.content), "拦截时间").performClick();
        ShadowLooper.shadowMainLooper().idle();
        Dialog d = ShadowDialog.getLatestDialog();
        List<WheelView> wheels = wheels(d.getWindow().getDecorView());
        wheels.get(0).setIndex(0);
        wheels.get(1).setIndex(0);
        click(d.getWindow().getDecorView(), "完成");

        assertEquals("最小就是 1 分钟", (long) Limits.MIN, Prefs.limitMin(app));
    }

    @Test
    public void autoUnlockUsesTheSameWheelWindow() {
        MainActivity a = openRules();

        assertTrue("默认该写着不自动", hasText(a.findViewById(android.R.id.content), "不自动"));

        View row = findClickableRow(a.findViewById(android.R.id.content), "自动解锁");
        assertNotNull("规则里找不到自动解锁那一行", row);
        row.performClick();
        ShadowLooper.shadowMainLooper().idle();

        Dialog d = ShadowDialog.getLatestDialog();
        assertNotNull("点开没弹窗", d);
        List<WheelView> wheels = wheels(d.getWindow().getDecorView());
        assertEquals("自动解锁也是时、分两个轮", 2, wheels.size());
        assertTrue("得有那句 00:00 的说明",
                hasText(d.getWindow().getDecorView(), "调到 00:00 就是不自动解锁"));

        wheels.get(0).setIndex(0);
        wheels.get(1).setIndex(5);
        click(d.getWindow().getDecorView(), "完成");

        assertEquals("选 5 分钟就该存 5", 5, Prefs.autoUnlockMin(app));
        assertTrue("行尾该跟着变成 5 分钟",
                hasText(a.findViewById(android.R.id.content), "5 分钟后自动解开"));
    }

    @Test
    public void autoUnlockZeroMeansOffAndClampsToItsMax() {
        MainActivity a = openRules();

        findClickableRow(a.findViewById(android.R.id.content), "自动解锁").performClick();
        ShadowLooper.shadowMainLooper().idle();
        Dialog d = ShadowDialog.getLatestDialog();
        List<WheelView> wheels = wheels(d.getWindow().getDecorView());
        wheels.get(0).setIndex(0);
        wheels.get(1).setIndex(0);
        click(d.getWindow().getDecorView(), "完成");
        assertEquals("0:00 就是不自动", 0, Prefs.autoUnlockMin(app));

        findClickableRow(a.findViewById(android.R.id.content), "自动解锁").performClick();
        ShadowLooper.shadowMainLooper().idle();
        d = ShadowDialog.getLatestDialog();
        wheels = wheels(d.getWindow().getDecorView());
        wheels.get(0).setIndex(12);
        wheels.get(1).setIndex(59);
        click(d.getWindow().getDecorView(), "完成");
        assertEquals("超过上限夹到 720", AutoUnlock.MAX, Prefs.autoUnlockMin(app));
    }

    @Test
    public void motionDefaultsToCenter() {
        MainActivity a = openRules();

        assertEquals(Motion.CENTER, Prefs.motion(app));
        assertTrue(hasText(a.findViewById(android.R.id.content), Motion.LABEL_CENTER));
    }

    @Test
    public void motionCanSwitchToSpring() {
        MainActivity a = openRules();

        View row = findClickableRow(a.findViewById(android.R.id.content), "遮罩动画");
        assertNotNull("规则里找不到遮罩动画那一行", row);
        row.performClick();
        ShadowLooper.shadowMainLooper().idle();

        android.app.AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("点开没弹框", d);
        d.getListView().performItemClick(null, 3, 3);
        ShadowLooper.shadowMainLooper().idle();

        assertEquals("选了轻弹", Motion.SPRING, Prefs.motion(app));
        assertTrue(hasText(a.findViewById(android.R.id.content), Motion.LABEL_SPRING));
    }

    @Test
    public void speedCanSwitchToFast() {
        MainActivity a = openRules();

        View row = findClickableRow(a.findViewById(android.R.id.content), "动画速度");
        assertNotNull("规则里找不到动画速度那一行", row);
        row.performClick();
        ShadowLooper.shadowMainLooper().idle();

        android.app.AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("点开没弹框", d);
        d.getListView().performItemClick(null, 2, 2);
        ShadowLooper.shadowMainLooper().idle();

        assertEquals("选了快 1.5×", 1.5f, Prefs.motionSpeed(app), 0.001f);
        assertTrue(hasText(a.findViewById(android.R.id.content), MotionSpeed.label(1.5f)));
    }

    @Test
    public void onlyAppMaskIsLeftWithoutAGlobalChoice() {
        MainActivity a = openRules();

        assertFalse("不该再有遮罩范围选项",
                hasText(a.findViewById(android.R.id.content), "遮罩范围"));
        assertFalse("不该再出现全局遮罩",
                hasText(a.findViewById(android.R.id.content), "全局遮罩"));
    }

    // ---------- helpers ----------

    private static void click(View v, String label) {
        View target = find(v, label);
        assertNotNull("找不到 " + label, target);
        View c = target;
        while (c != null && !c.isClickable()) {
            c = c.getParent() instanceof View ? (View) c.getParent() : null;
        }
        (c == null ? target : c).performClick();
        ShadowLooper.shadowMainLooper().idle();
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

    private static View findClickableRow(View v, String title) {
        if (v instanceof ViewGroup && !(v instanceof TextView)) {
            ViewGroup g = (ViewGroup) v;
            if (g.isClickable() && hasText(g, title)) {
                return g;
            }
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findClickableRow(g.getChildAt(i), title);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private static EditText findEditText(View v) {
        if (v instanceof EditText) {
            return (EditText) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                EditText e = findEditText(g.getChildAt(i));
                if (e != null) {
                    return e;
                }
            }
        }
        return null;
    }

    private static List<WheelView> wheels(View v) {
        List<WheelView> out = new ArrayList<>();
        collectWheels(v, out);
        return out;
    }

    private static void collectWheels(View v, List<WheelView> out) {
        if (v instanceof WheelView) {
            out.add((WheelView) v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectWheels(g.getChildAt(i), out);
            }
        }
    }

    private static boolean hasText(View v, String what) {
        return find(v, what) != null;
    }

    private static boolean hasTextContains(View v, String part) {
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            return t != null && t.toString().contains(part);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (hasTextContains(g.getChildAt(i), part)) {
                    return true;
                }
            }
        }
        return false;
    }
}
