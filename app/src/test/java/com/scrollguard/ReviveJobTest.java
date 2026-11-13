package com.scrollguard;

import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * M4：定期任务——service 被系统清掉了要能自己拉起来。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ReviveJobTest {

    private Application app;
    private JobScheduler js;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        Prefs.wipe(app);
        js = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        js.cancelAll();
        GuardState.get().serviceRunning = false;
    }

    @Test
    public void schedulesAPeriodicJob() {
        ReviveJob.schedule(app);

        List<JobInfo> jobs = js.getAllPendingJobs();
        assertEquals(1, jobs.size());
        JobInfo job = jobs.get(0);
        assertEquals(1001, job.getId());
        assertTrue("必须是周期任务", job.isPeriodic());
        assertEquals("15 分钟一次", 15 * 60 * 1000L, job.getIntervalMillis());
    }

    @Test
    public void cancelTakesItAway() {
        ReviveJob.schedule(app);
        ReviveJob.cancel(app);

        assertEquals(0, js.getAllPendingJobs().size());
    }

    @Test
    public void revivesOnlyWhenArmedAndNotAlreadyRunning() {
        Prefs.setEnabled(app, false);
        assertFalse(ReviveJob.shouldRevive(app));

        Prefs.setEnabled(app, true);
        GuardState.get().serviceRunning = false;
        assertTrue(ReviveJob.shouldRevive(app));

        GuardState.get().serviceRunning = true;
        assertFalse("已经在跑就别再起一个", ReviveJob.shouldRevive(app));
    }

    @Test
    public void startGuardStartsTheForegroundService() {
        ReviveJob.startGuard(app);

        Intent started = shadowOf(app).getNextStartedService();
        assertNotNull(started);
        assertEquals(GuardService.class.getName(), started.getComponent().getClassName());
    }
}
