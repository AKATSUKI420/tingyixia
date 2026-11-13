package com.scrollguard;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * 每 15 分钟确认一次计时服务还活着。被系统清掉过就拉起来。
 */
public class ReviveJob extends JobService {

    private static final int JOB_ID = 1001;

    public static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) {
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, ReviveJob.class))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build();
        try {
            js.schedule(job);
        } catch (Throwable ignored) {
            // 某些系统不允许周期任务，那就靠开机广播和手动打开
        }
    }

    public static void cancel(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) {
            js.cancel(JOB_ID);
        }
    }

    static void startGuard(Context c) {
        try {
            Intent i = new Intent(c, GuardService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable ignored) {
            // 后台启动被系统挡住，下次再试
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (Prefs.enabled(this) && !GuardState.get().serviceRunning) {
            startGuard(this);
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
