package com.scrollguard;

import android.app.Application;
import android.app.Activity;
import android.app.AlertDialog;
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
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * M1/M5：首页——没密码先设密码，有密码才给看状态；状态行得说实话。
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
        assertTrue("首页没搭起来", hasText(a.findViewById(android.R.id.content), null));
    }

    @Test
    public void offStateSaysSo() {
        Pin.save(app, "123456");
        MainActivity a = open();

        assertTrue(hasText(a.findViewById(android.R.id.content), "现在关着，什么都不管。"));
    }

    @Test
    public void onWithoutAccessibilitySaysItCannotBlock() {
        Pin.save(app, "123456");
        Prefs.setEnabled(app, true);
        MainActivity a = open();

        assertTrue("得提醒无障碍没开",
                hasText(a.findViewById(android.R.id.content), "无障碍没开 —— 现在其实拦不住。"));
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
                hasText(a.findViewById(android.R.id.content), "悬浮窗没开 —— 到点了也盖不住。"));
    }

    /** 时长是"自己填"，不是从五个选项里挑：点开、敲 7、存进去 */
    @Test
    public void limitIsTypedInMinutes() {
        Pin.save(app, "123456");
        MainActivity a = open();

        View row = findClickableRow(a.findViewById(android.R.id.content), "连着刷多久就拦");
        assertNotNull("规则里找不到改时长的那一行", row);
        row.performClick();
        ShadowLooper.shadowMainLooper().idle();   // 让 onShow 里的挂载先跑完

        AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("点开没弹框", d);
        EditText et = findEditText(d.getWindow().getDecorView());
        assertNotNull("框里没有输入的地方", et);
        assertEquals("框里该是现在的值", "10", et.getText().toString());

        et.setText("7");
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

        assertEquals("填 7 就该存 7", 7L, Prefs.limitMin(app));
        assertFalse("存下了框该关", d.isShowing());
    }

    /** 填了 0 或者乱敲，不许把设置改坏，也不许把框关掉 */
    @Test
    public void badLimitInputIsRejected() {
        Pin.save(app, "123456");
        MainActivity a = open();

        findClickableRow(a.findViewById(android.R.id.content), "连着刷多久就拦").performClick();
        ShadowLooper.shadowMainLooper().idle();
        AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        EditText et = findEditText(d.getWindow().getDecorView());

        et.setText("0");
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

        assertEquals("不该改动", 10L, Prefs.limitMin(app));
        assertNotNull("得提示填错了", et.getError());
        assertTrue("填错了不该关掉框", d.isShowing());

        et.setText("601");
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals("超过上限也不该改", 10L, Prefs.limitMin(app));
    }

    // ---------- 问题3：锁屏后多久自动解开，也是自己填 ----------

    @Test
    public void autoUnlockStartsOffAndIsTypedIn() {
        Pin.save(app, "123456");
        MainActivity a = open();

        assertTrue("默认该写着不自动解开",
                hasText(a.findViewById(android.R.id.content), "不自动解开"));

        View row = findClickableRow(a.findViewById(android.R.id.content), "锁屏后多久自动解开");
        assertNotNull("规则里找不到自动解开那一行", row);
        row.performClick();
        ShadowLooper.shadowMainLooper().idle();

        AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("点开没弹框", d);
        EditText et = findEditText(d.getWindow().getDecorView());
        assertEquals("框里该是现在的值", "0", et.getText().toString());

        et.setText("5");
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

        assertEquals("填 5 就该存 5", 5, Prefs.autoUnlockMin(app));
        assertFalse("存下了框该关", d.isShowing());
        assertTrue("首页该跟着变成 5 分钟",
                hasText(a.findViewById(android.R.id.content), "5 分钟后自动解开"));
    }

    @Test
    public void autoUnlockCanBeTurnedBackOff() {
        Pin.save(app, "123456");
        Prefs.setAutoUnlockMin(app, 7L);
        MainActivity a = open();

        findClickableRow(a.findViewById(android.R.id.content), "锁屏后多久自动解开").performClick();
        ShadowLooper.shadowMainLooper().idle();
        AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        EditText et = findEditText(d.getWindow().getDecorView());
        assertEquals("7", et.getText().toString());

        et.setText("0");
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

        assertEquals("填 0 就是关掉", 0, Prefs.autoUnlockMin(app));
        assertTrue(hasText(a.findViewById(android.R.id.content), "不自动解开"));
    }

    /** 自动解开的输入同样不能乱填 */
    @Test
    public void badAutoUnlockInputIsRejected() {
        Pin.save(app, "123456");
        MainActivity a = open();

        findClickableRow(a.findViewById(android.R.id.content), "锁屏后多久自动解开").performClick();
        ShadowLooper.shadowMainLooper().idle();
        AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        EditText et = findEditText(d.getWindow().getDecorView());

        et.setText("601");
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

        assertEquals("超过 600 不该改", 0, Prefs.autoUnlockMin(app));
        assertNotNull("得提示填错了", et.getError());
        assertTrue("填错了不该关掉框", d.isShowing());
    }

    // ---------- 问题2：只遮罩 App / 全局遮罩 ----------

    @Test
    public void scopeDefaultsToAppOnlyWithTargetSwitches() {
        Pin.save(app, "123456");
        MainActivity a = open();

        assertTrue("规则里该有遮罩范围", hasText(a.findViewById(android.R.id.content), "遮罩范围"));
        assertTrue(hasText(a.findViewById(android.R.id.content), Scope.LABEL_APP));
        assertTrue("只遮罩 App 要说清楚切走会收起",
                hasText(a.findViewById(android.R.id.content), "只算抖音 / B 站；切走就收起"));
        assertTrue(hasText(a.findViewById(android.R.id.content), "抖音"));
        assertTrue(hasText(a.findViewById(android.R.id.content), "哔哩哔哩"));
    }

    @Test
    public void scopeCanSwitchToTheWholePhone() {
        Pin.save(app, "123456");
        MainActivity a = open();

        View row = findClickableRow(a.findViewById(android.R.id.content), "遮罩范围");
        assertNotNull("规则里找不到遮罩范围那一行", row);
        row.performClick();
        ShadowLooper.shadowMainLooper().idle();

        AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("点开没弹框", d);
        d.getListView().performItemClick(null, 1, 1);
        ShadowLooper.shadowMainLooper().idle();

        assertEquals("选了全局遮罩", Scope.GLOBAL, Prefs.scope(app));
        assertFalse("选完该关框", d.isShowing());
        assertTrue(hasText(a.findViewById(android.R.id.content), Scope.LABEL_GLOBAL));
        assertTrue("全局模式该说清楚按 Home 也盖着",
                hasText(a.findViewById(android.R.id.content), "整台手机都算；按 Home 也盖着"));
        assertTrue("全局模式该有一句说明",
                hasText(a.findViewById(android.R.id.content), "不管哪个应用"));
        assertFalse("全局模式不该再显示单个 App 开关",
                hasText(a.findViewById(android.R.id.content), "抖音"));
        assertFalse(hasText(a.findViewById(android.R.id.content), "哔哩哔哩"));
    }

    @Test
    public void globalScopeIsRememberedWhenReopening() {
        Pin.save(app, "123456");
        Prefs.setScope(app, Scope.GLOBAL);
        MainActivity a = open();

        assertTrue(hasText(a.findViewById(android.R.id.content), Scope.LABEL_GLOBAL));
        assertTrue(hasText(a.findViewById(android.R.id.content), "不管哪个应用"));
        assertFalse(hasText(a.findViewById(android.R.id.content), "抖音"));
    }

    private static View findClickableRow(View v, String title) {        if (v instanceof ViewGroup && !(v instanceof TextView)) {
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

    private static boolean hasText(View v, String what) {
        if (v == null) {
            return false;
        }
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (what == null) {
                return t != null && t.length() > 0;
            }
            return what.contentEquals(t);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (hasText(g.getChildAt(i), what)) {
                    return true;
                }
            }
        }
        return false;
    }
}
