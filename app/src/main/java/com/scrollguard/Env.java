package com.scrollguard;

import android.content.ComponentName;
import android.content.Context;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * 三个权限到底开没开。每次都现查，不缓存——用户可能刚去设置里改过。
 */
public final class Env {

    private Env() {
    }

    public static boolean a11y(Context c) {
        String flat = Settings.Secure.getString(
                c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (flat == null) {
            return false;
        }
        String want = new ComponentName(c, ShieldService.class).flattenToString();
        String[] parts = flat.split(":");
        for (String s : parts) {
            if (want.equalsIgnoreCase(s.trim())) {
                return true;
            }
        }
        return false;
    }

    public static boolean overlay(Context c) {
        return Settings.canDrawOverlays(c);
    }

    public static boolean battery(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }
}
