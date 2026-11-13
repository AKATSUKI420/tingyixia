package com.scrollguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机后把防护接着跑起来。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent == null) {
            return;
        }
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(a)) {
            return;
        }
        if (!Prefs.enabled(c) || !Prefs.hasPin(c)) {
            return;
        }
        ReviveJob.startGuard(c);
        ReviveJob.schedule(c);
    }
}
