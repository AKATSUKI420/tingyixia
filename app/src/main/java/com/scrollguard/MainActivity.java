package com.scrollguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

/**
 * 主界面。全部用代码拼，XML 只留一个空容器。
 */
public class MainActivity extends Activity {

    private LinearLayout root;
    private ScrollView scroll;
    private Handler handler;
    private TextView statusLine;
    private boolean askedNoti;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            if (handler != null) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.content);
        ViewParent vp = root.getParent();
        if (vp instanceof ScrollView) {
            scroll = (ScrollView) vp;
        }
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Prefs.hasPin(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            return;
        }
        int keep = scroll == null ? 0 : scroll.getScrollY();
        build();
        if (scroll != null) {
            scroll.post(() -> scroll.scrollTo(0, keep));
        }
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null) {
            handler.removeCallbacks(ticker);
        }
    }

    // ------------------------------------------------------------------ 拼界面

    private void build() {
        root.removeAllViews();

        TextView title = new TextView(this);
        title.setId(R.id.page_title);
        title.setText("停一下");
        title.setTextSize(Ui.TITLE_SP);
        title.setTypeface(Ui.bold(this));
        title.setLetterSpacing(-0.02f);
        title.setTextColor(Ui.col(this, R.color.text));
        title.setIncludeFontPadding(false);
        root.addView(title);

        statusLine = new TextView(this);
        statusLine.setId(R.id.status_line);
        statusLine.setTextSize(15);
        statusLine.setTextColor(Ui.col(this, R.color.dim));
        statusLine.setIncludeFontPadding(false);
        statusLine.setLineSpacing(Ui.dp(this, 2), 1f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(this, 8);
        root.addView(statusLine, slp);
        updateStatus();

        // 今天
        Ui.header(root, "今天");
        LinearLayout today = Ui.card(this);
        today.addView(Ui.row(this, "看了多久", null, Ui.value(this, Text.dur(Prefs.todayMs(this)))));
        today.addView(Ui.divider(this));
        today.addView(Ui.row(this, "被拦了", null, Ui.value(this, Prefs.todayLocks(this) + " 次")));
        root.addView(today);

        // 规则
        Ui.header(root, "规则");
        LinearLayout rules = Ui.card(this);

        TextView scopeVal = Ui.value(this, Scope.label(this));
        scopeVal.setTextColor(Ui.col(this, R.color.text));
        LinearLayout scopeRow = Ui.row(this, "遮罩范围",
                Scope.isGlobal(this) ? "整台手机都算；按 Home 也盖着" : "只算抖音 / B 站；切走就收起",
                scopeVal);
        Ui.tap(scopeRow, this::pickScope);
        rules.addView(scopeRow);
        rules.addView(Ui.divider(this));

        TextView dur = Ui.value(this, Text.limit(Prefs.limitMin(this)));
        dur.setTextColor(Ui.col(this, R.color.text));
        LinearLayout r1 = Ui.row(this, "连着刷多久就拦", null, dur);
        Ui.tap(r1, this::pickLimit);
        rules.addView(r1);
        rules.addView(Ui.divider(this));

        TextView autoVal = Ui.value(this, AutoUnlock.label(Prefs.autoUnlockMin(this)));
        autoVal.setTextColor(Ui.col(this, R.color.text));
        LinearLayout r2 = Ui.row(this, "锁屏后多久自动解开", null, autoVal);
        Ui.tap(r2, this::pickAutoUnlock);
        rules.addView(r2);
        rules.addView(Ui.divider(this));

        TextView motionVal = Ui.value(this, Motion.label(Prefs.motion(this)));
        motionVal.setTextColor(Ui.col(this, R.color.text));
        LinearLayout r3 = Ui.row(this, "遮罩动画", "出现和消失时怎么动", motionVal);
        Ui.tap(r3, this::pickMotion);
        rules.addView(r3);
        rules.addView(Ui.divider(this));

        TextView speedVal = Ui.value(this, MotionSpeed.label(Prefs.motionSpeed(this)));
        speedVal.setTextColor(Ui.col(this, R.color.text));
        LinearLayout r3b = Ui.row(this, "动画速度", "遮罩出现和消失的快慢", speedVal);
        Ui.tap(r3b, this::pickSpeed);
        rules.addView(r3b);
        rules.addView(Ui.divider(this));

        if (Scope.isGlobal(this)) {
            rules.addView(Ui.row(this, "不管哪个应用", "横竖屏都算，攒的是同一个时长", null));
        } else {
            rules.addView(Ui.row(this, "抖音", null, switchTarget(Prefs.DOUYIN)));
            rules.addView(Ui.divider(this));
            rules.addView(Ui.row(this, "哔哩哔哩", null, switchTarget(Prefs.BILI)));
        }
        rules.addView(Ui.divider(this));
        rules.addView(Ui.row(this, "只在真有动静时算",
                "开着更接近\"真的在刷\"，但可能漏算", strictSwitch()));
        root.addView(rules);

        TextView note = new TextView(this);
        if (Scope.isGlobal(this)) {
            note.setText("屏幕亮着、手机在用就算，横竖屏都算；桌面、系统界面和本应用不算。"
                    + "按 Home 遮罩也不会消失，直到输密码或自动解开；攒下的时间不清零。");
        } else {
            note.setText("竖着拿、在抖音或 B 站里、连着看才算。切走、熄屏、横过来只是暂停计时，"
                    + "攒下的时间不清零；切走时遮罩自动收起，回来会再盖住。只有输对密码才从零开始。");
        }
        note.setTextSize(13);
        note.setTextColor(Ui.col(this, R.color.dim));
        note.setLineSpacing(Ui.dp(this, 3), 1f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.leftMargin = Ui.dp(this, 16);
        nlp.rightMargin = Ui.dp(this, 16);
        nlp.topMargin = Ui.dp(this, 8);
        root.addView(note, nlp);

        // 权限
        Ui.header(root, "能不能拦得住");
        boolean a11y = Env.a11y(this);
        boolean over = Env.overlay(this);
        boolean bat = Env.battery(this);

        LinearLayout perm = Ui.card(this);

        LinearLayout a11yRow = tapRow(Ui.row(this, "无障碍", a11y ? null : "不开就认不出你在刷什么",
                Ui.chip(this, a11y ? "已开" : "去开", a11y)), () -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Throwable t) {
                toast("打不开设置，手动去系统设置里找");
            }
        });
        perm.addView(a11yRow);
        perm.addView(Ui.divider(this));

        LinearLayout overRow = tapRow(Ui.row(this, "悬浮窗", over ? null : "不开就盖不住屏幕",
                Ui.chip(this, over ? "已开" : "去开", over)), () -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivity(i);
            } catch (Throwable t) {
                toast("打不开设置，手动去系统设置里找");
            }
        });
        perm.addView(overRow);
        perm.addView(Ui.divider(this));

        LinearLayout batRow = tapRow(Ui.row(this, "不管电池优化", bat ? null : "设一下，免得被系统悄悄清掉",
                Ui.chip(this, bat ? "已设" : "去设", bat)), this::askBattery);
        perm.addView(batRow);
        root.addView(perm);

        boolean on = Prefs.enabled(this);
        TextView btn = Ui.button(this, on ? "关掉防护" : "开起来", on);
        btn.setId(R.id.primary_button);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 24);
        root.addView(btn, blp);
        btn.setOnClickListener(v -> toggle());

        // 别的
        Ui.header(root, "别的");
        LinearLayout other = Ui.card(this);

        other.addView(tapRow(Ui.row(this, "试一下拦截", "看看盖上去是什么样", chevron()),
                this::testLock));
        other.addView(Ui.divider(this));
        other.addView(tapRow(Ui.row(this, "换密码", null, chevron()),
                () -> requirePin("先输现在的密码", pin -> {
                    if (!Gate.check(this, pin)) {
                        return false;
                    }
                    Intent i = new Intent(this, SetupActivity.class);
                    i.putExtra(SetupActivity.EXTRA_CHANGE, true);
                    startActivity(i);
                    return true;
                })));
        other.addView(Ui.divider(this));
        other.addView(tapRow(Ui.row(this, "忘掉密码", "清掉全部数据，从头再设", chevron()),
                this::wipeConfirm));
        root.addView(other);

        TextView foot = new TextView(this);
        foot.setText("数据都在这台手机上 · 不联网 · 不上传");
        foot.setTextSize(12);
        foot.setTextColor(Ui.col(this, R.color.dim2));
        foot.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Ui.dp(this, 24);
        root.addView(foot, flp);
    }

    private LinearLayout tapRow(LinearLayout row, Runnable action) {
        Ui.tap(row, action);
        return row;
    }

    private TextView chevron() {
        TextView t = Ui.plain(this, "›", 20, R.color.dim2);
        t.setIncludeFontPadding(false);
        return t;
    }

    private IosSwitch switchTarget(final String pkg) {
        IosSwitch sw = new IosSwitch(this);
        sw.setChecked(Prefs.targets(this).contains(pkg));
        sw.setOnCheckedChangeListener((v, checked) -> {
            Prefs.setTarget(this, pkg, checked);
            Set<String> now = Prefs.targets(this);
            if (now.isEmpty()) {
                toast("全关了就等于没有对象，防护还开着但不会拦");
            }
        });
        return sw;
    }

    private IosSwitch strictSwitch() {
        IosSwitch sw = new IosSwitch(this);
        sw.setChecked(Prefs.strict(this));
        sw.setOnCheckedChangeListener((v, checked) -> Prefs.setStrict(this, checked));
        return sw;
    }

    private void updateStatus() {
        if (statusLine == null) {
            return;
        }
        if (!Prefs.enabled(this)) {
            setStatus("现在关着，什么都不管。", R.color.dim);
            return;
        }
        if (!Env.a11y(this)) {
            setStatus("无障碍没开 —— 现在其实拦不住。", R.color.bad);
            return;
        }
        if (!Env.overlay(this)) {
            setStatus("悬浮窗没开 —— 到点了也盖不住。", R.color.bad);
            return;
        }
        GuardState st = GuardState.get();
        if (!st.serviceRunning) {
            setStatus("计时服务不在，点下面的按钮重新开一下。", R.color.warn);
            return;
        }
        if (st.countedMs >= 1000L) {
            setStatus("正在计时 " + Text.mmss(st.countedMs)
                    + " / " + Text.limit(Prefs.limitMin(this)), R.color.good);
        } else {
            setStatus(Scope.isGlobal(this) ? "在看着 · 现在没在用" : "在看着 · 现在没在刷",
                    R.color.accent);
        }
    }

    private void setStatus(String text, int dotColor) {
        statusLine.setText(text);
        statusLine.setCompoundDrawablesRelativeWithIntrinsicBounds(
                Ui.dot(this, Ui.col(this, dotColor)), null, null, null);
        statusLine.setCompoundDrawablePadding(Ui.dp(this, 8));
    }

    // ------------------------------------------------------------------ 动作

    private void pickLimit() {
        askNumber("连着刷多久就拦", "按分钟填，1 分钟起",
                (int) Prefs.limitMin(this), Limits.MIN, Limits.MAX, v -> {
                    Prefs.setLimitMin(this, v);
                    if (Prefs.enabled(this)) {
                        stopService(new Intent(this, GuardService.class));
                        ReviveJob.startGuard(this);
                    }
                    build();
                });
    }

    private void pickAutoUnlock() {
        askNumber("锁屏后多久自动解开", "填 0 就要输密码才解开",
                Prefs.autoUnlockMin(this), AutoUnlock.MIN, AutoUnlock.MAX, v -> {
                    Prefs.setAutoUnlockMin(this, v);
                    build();
                });
    }

    private void pickScope() {
        final String[] labels = {Scope.LABEL_APP, Scope.LABEL_GLOBAL};
        final int checked = Scope.isGlobal(this) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("遮罩范围")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    Prefs.setScope(this, which == 0 ? Scope.APP : Scope.GLOBAL);
                    d.dismiss();
                    build();
                    updateStatus();
                })
                .setNegativeButton("算了", null)
                .show();
    }

    private void pickMotion() {
        final String[] labels = Motion.labels();
        final int checked = Motion.indexOf(Prefs.motion(this));
        new AlertDialog.Builder(this)
                .setTitle("遮罩动画")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    Prefs.setMotion(this, Motion.values()[which]);
                    d.dismiss();
                    build();
                })
                .setNegativeButton("算了", null)
                .show();
    }

    private void pickSpeed() {
        final String[] labels = MotionSpeed.labels();
        final int checked = MotionSpeed.indexOf(Prefs.motionSpeed(this));
        new AlertDialog.Builder(this)
                .setTitle("动画速度")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    Prefs.setMotionSpeed(this, MotionSpeed.values()[which]);
                    d.dismiss();
                    build();
                })
                .setNegativeButton("算了", null)
                .show();
    }

    /** 让用户自己敲一个整数；不合法就停在框里改，不存 */
    private interface NumOk {
        void run(int v);
    }

    private void askNumber(String title, String sub, int cur, final int min, final int max,
                           final NumOk ok) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(cur));
        et.setSelectAllOnFocus(true);

        FrameLayout wrap = new FrameLayout(this);
        int p = Ui.dp(this, 20);
        wrap.setPadding(p, Ui.dp(this, 6), p, 0);
        wrap.addView(et);

        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(sub + "（" + min + "–" + max + "）")
                .setView(wrap)
                .setPositiveButton("好", null)
                .setNegativeButton("算了", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int val = Limits.parse(et.getText().toString(), min, max);
            if (val < 0) {
                et.setError(Limits.hint(min, max));
                return;
            }
            ok.run(val);
            d.dismiss();
        }));
        d.show();
    }

    private void toggle() {
        if (Prefs.enabled(this)) {
            requirePin("输密码才能关", pin -> {
                if (!Gate.disable(this, pin)) {
                    return false;
                }
                stopService(new Intent(this, GuardService.class));
                ReviveJob.cancel(this);
                GuardState.get().countedMs = 0;
                build();
                toast("关掉了");
                return true;
            });
            return;
        }
        Gate.enable(this);
        ReviveJob.startGuard(this);
        ReviveJob.schedule(this);
        askNotifications();
        build();
        if (!Env.a11y(this)) {
            toast("还差无障碍，现在拦不住");
        } else if (!Env.overlay(this)) {
            toast("还差悬浮窗，到点了盖不住");
        }
    }

    private void testLock() {
        if (!Prefs.enabled(this)) {
            toast("先开起来");
            return;
        }
        if (!Env.overlay(this)) {
            toast("先去开悬浮窗");
            return;
        }
        Intent i = new Intent(this, GuardService.class);
        i.setAction(GuardService.ACTION_TEST);
        try {
            startService(i);
        } catch (Throwable t) {
            toast("没起来，稍后再试");
        }
    }

    /** 输密码的框；回调返回 true 表示这次输入被接受，框才会关 */
    private interface PinCheck {
        boolean run(String pin);
    }

    private void requirePin(String prompt, final PinCheck ok) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setHint("6 位数字");
        FrameLayout wrap = new FrameLayout(this);
        int p = Ui.dp(this, 20);
        wrap.setPadding(p, Ui.dp(this, 6), p, 0);
        wrap.addView(et);

        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(prompt)
                .setView(wrap)
                .setPositiveButton("好", null)
                .setNegativeButton("算了", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (ok.run(et.getText().toString().trim())) {
                d.dismiss();
            } else {
                et.setError("不对");
            }
        }));
        d.show();
    }

    private void wipeConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("全清掉？")
                .setMessage("密码、今天的数据、所有设置都会没了。防护也会关掉。")
                .setPositiveButton("清掉", (d, w) -> {
                    Prefs.wipe(this);
                    stopService(new Intent(this, GuardService.class));
                    ReviveJob.cancel(this);
                    GuardState.get().countedMs = 0;
                    GuardState.get().fgPackage = "";
                    startActivity(new Intent(this, SetupActivity.class));
                })
                .setNegativeButton("算了", null)
                .show();
    }

    private void askBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable t2) {
                toast("打不开，手动去系统设置里找");
            }
        }
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT < 33 || askedNoti) {
            return;
        }
        askedNoti = true;
        if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 10);
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
