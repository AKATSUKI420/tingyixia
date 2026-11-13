package com.scrollguard;

import android.app.Application;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * M5：设密码这一步——两次得一样、返回键不能溜、设完才存。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SetupActivityTest {

    private Application app;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        Prefs.wipe(app);
    }

    private SetupActivity open() {
        ActivityController<SetupActivity> c = Robolectric.buildActivity(SetupActivity.class);
        c.setup();
        return c.get();
    }

    private static PinPadView pad(SetupActivity a) {
        return a.findViewById(R.id.setup_pad);
    }

    @Test
    public void twoMatchingEntriesSaveThePin() {
        SetupActivity a = open();

        TestPad.type(pad(a), "123456");
        assertEquals("再输一遍",
                ((TextView) a.findViewById(R.id.setup_title)).getText().toString());

        TestPad.type(pad(a), "123456");

        assertTrue(Prefs.hasPin(app));
        assertTrue("存的密码得能用", Pin.check(app, "123456"));
        assertTrue("设完该关掉", a.isFinishing());
    }

    @Test
    public void mismatchStartsOverAndSavesNothing() {
        SetupActivity a = open();

        TestPad.type(pad(a), "123456");
        TestPad.type(pad(a), "654321");

        assertFalse("两次不一样不能存", Prefs.hasPin(app));
        assertFalse(a.isFinishing());
        assertEquals("两次不一样，重来", TestPad.message(pad(a)));
        assertEquals("先设个密码",
                ((TextView) a.findViewById(R.id.setup_title)).getText().toString());
    }

    @Test
    public void onlyFiveDigitsIsNotEnough() {
        SetupActivity a = open();

        TestPad.type(pad(a), "12345");

        assertFalse(Prefs.hasPin(app));
        assertFalse("没满六位不该进第二步", "再输一遍".contentEquals(
                ((TextView) a.findViewById(R.id.setup_title)).getText()));
    }

    @Test
    public void backKeyCannotSkipIt() {
        SetupActivity a = open();

        a.onBackPressed();

        assertFalse("设密码这一步不能溜", a.isFinishing());
    }
}
