package com.scrollguard;

import android.content.Context;

/**
 * 「密码守住开关」的唯一实现处。
 *
 * <p>规则只有三条：开不用密码、关必须验密码、换密码必须验旧密码。
 * 都写在这里，MainActivity 只负责弹框，测试也就只能在这一层验。
 */
public final class Gate {

    private Gate() {
    }

    /** 打开防护不需要密码 */
    public static void enable(Context c) {
        Prefs.setEnabled(c, true);
    }

    /** 关掉防护要密码；密码不对就什么都不改 */
    public static boolean disable(Context c, String pin) {
        if (!check(c, pin)) {
            return false;
        }
        Prefs.setEnabled(c, false);
        return true;
    }

    /** 换密码：旧密码不对或新密码不合规，都不改 */
    public static boolean changePin(Context c, String oldPin, String newPin) {
        if (!check(c, oldPin)) {
            return false;
        }
        if (!Pin.wellFormed(newPin)) {
            return false;
        }
        Pin.save(c, newPin);
        return true;
    }

    public static boolean check(Context c, String pin) {
        return Pin.check(c, pin);
    }

    /** 忘掉密码：清掉全部本地数据，防护随之关闭 */
    public static void wipe(Context c) {
        Prefs.wipe(c);
    }
}
