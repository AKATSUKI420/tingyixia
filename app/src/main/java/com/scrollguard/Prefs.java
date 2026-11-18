package com.scrollguard;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 所有本地状态。不联网，不上传，只在这台手机的 SharedPreferences 里。
 */
public final class Prefs {

    public static final String DOUYIN = "com.ss.android.ugc.aweme";
    public static final String BILI = "tv.danmaku.bili";

    private static final String NAME = "guard";

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    // ---------- 密码 ----------

    public static boolean hasPin(Context c) {
        return sp(c).getBoolean("has_pin", false);
    }

    public static void setPin(Context c, String hash, String salt) {
        sp(c).edit()
                .putBoolean("has_pin", true)
                .putString("pin_hash", hash)
                .putString("pin_salt", salt)
                .apply();
    }

    public static String pinHash(Context c) {
        return sp(c).getString("pin_hash", "");
    }

    public static String pinSalt(Context c) {
        return sp(c).getString("pin_salt", "");
    }

    public static void wipe(Context c) {
        sp(c).edit().clear().apply();
    }

    // ---------- 设置 ----------

    public static long limitMin(Context c) {
        return Limits.clamp(sp(c).getLong("limit_min", Limits.DEFAULT), Limits.MIN, Limits.MAX);
    }

    public static void setLimitMin(Context c, long v) {
        sp(c).edit().putLong("limit_min", Limits.clamp(v, Limits.MIN, Limits.MAX)).apply();
    }

    public static int autoUnlockMin(Context c) {
        long d = sp(c).getLong("auto_unlock_min", AutoUnlock.OFF);
        return (int) Limits.clamp(d, AutoUnlock.MIN, AutoUnlock.MAX);
    }

    public static void setAutoUnlockMin(Context c, long v) {
        sp(c).edit().putLong("auto_unlock_min", Limits.clamp(v, AutoUnlock.MIN, AutoUnlock.MAX)).apply();
    }

    public static boolean enabled(Context c) {
        return sp(c).getBoolean("enabled", false);
    }

    public static void setEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean("enabled", v).apply();
    }

    public static boolean strict(Context c) {
        return sp(c).getBoolean("strict", false);
    }

    public static void setStrict(Context c, boolean v) {
        sp(c).edit().putBoolean("strict", v).apply();
    }

    /** 遮罩范围：只遮罩 App（默认），还是全局遮罩 */
    public static String scope(Context c) {
        return Scope.normalize(sp(c).getString("scope", Scope.APP));
    }

    public static void setScope(Context c, String v) {
        sp(c).edit().putString("scope", Scope.normalize(v)).apply();
    }

    /** 遮罩出现、消失的动效；默认轻推淡入。 */
    public static String motion(Context c) {
        return Motion.normalize(sp(c).getString("motion", Motion.DEFAULT));
    }

    public static void setMotion(Context c, String v) {
        sp(c).edit().putString("motion", Motion.normalize(v)).apply();
    }

    /** 遮罩动画快慢倍率；默认标准 1×。 */
    public static float motionSpeed(Context c) {
        return MotionSpeed.normalize(
                sp(c).getFloat("motion_speed", MotionSpeed.DEFAULT));
    }

    public static void setMotionSpeed(Context c, float v) {
        sp(c).edit().putFloat("motion_speed", MotionSpeed.normalize(v)).apply();
    }

    public static Set<String> targets(Context c) {
        Set<String> def = new HashSet<>();
        def.add(DOUYIN);
        def.add(BILI);
        Set<String> cur = sp(c).getStringSet("targets", null);
        if (cur == null) {
            return def;
        }
        return new HashSet<>(cur);
    }

    public static void setTarget(Context c, String pkg, boolean on) {
        Set<String> cur = targets(c);
        if (on) {
            cur.add(pkg);
        } else {
            cur.remove(pkg);
        }
        sp(c).edit().putStringSet("targets", cur).apply();
    }

    // ---------- 今天 ----------

    /** 统计用的日键。抽成纯函数，跨天那一页才能在测试里验。 */
    public static String dayKey(long epochMs) {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(epochMs));
    }

    private static String day() {
        return dayKey(System.currentTimeMillis());
    }

    public static long todayMs(Context c) {
        return sp(c).getLong("d" + day() + "_ms", 0L);
    }

    public static void addTodayMs(Context c, long ms) {
        if (ms <= 0) {
            return;
        }
        SharedPreferences p = sp(c);
        String k = "d" + day() + "_ms";
        p.edit().putLong(k, p.getLong(k, 0L) + ms).apply();
    }

    public static int todayLocks(Context c) {
        return sp(c).getInt("d" + day() + "_lk", 0);
    }

    public static void bumpTodayLocks(Context c) {
        SharedPreferences p = sp(c);
        String k = "d" + day() + "_lk";
        p.edit().putInt(k, p.getInt(k, 0) + 1).apply();
    }
}
