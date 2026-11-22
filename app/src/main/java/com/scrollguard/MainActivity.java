package com.scrollguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Set;

/**
 * 主界面。「今天」和「规则」两页，底部一条 tabbar。
 * 全部用代码拼，XML 只留容器。
 */
public class MainActivity extends Activity {

    private static final int TAB_TODAY = 0;
    private static final int TAB_RULES = 1;

    private LinearLayout root;
    private LinearLayout tabbar;
    private ScrollView scroll;
    private Handler handler;
    private int tab = TAB_TODAY;
    private boolean askedNoti;
    /** 从本页主动 launch 出去的（设置页、系统设置），离开时不算「用户走了」。 */
    private boolean internalNav;

    private TextView statusLine;
    private View statusDot;
    private View liveDot;
    private TextView liveText;
    private TextView metricTime;
    private TextView metricTag;
    private Ui.Bar bar;
    private TextView footLeft;
    private TextView footRight;
    private TextView limitValue;
    private boolean checkingUpdate;

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
        tabbar = findViewById(R.id.tabbar);
        scroll = findViewById(R.id.main_scroll);
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();
        internalNav = false;
        if (!Prefs.hasPin(this)) {
            launch(new Intent(this, SetupActivity.class));
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

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (internalNav || isFinishing() || isChangingConfigurations()) {
            return;
        }
        if (Prefs.hideRecents(this)) {
            finishAndRemoveTask();
        }
    }

    private void launch(Intent intent) {
        internalNav = true;
        startActivity(intent);
    }

    // ------------------------------------------------------------------ 外框

    private void build() {
        tabbar.removeAllViews();
        addTab(0, R.drawable.ic_bolt, "今天");
        addTab(1, R.drawable.ic_sliders, "规则");
        root.removeAllViews();
        statusLine = null;
        liveDot = null;
        liveText = null;
        metricTime = null;
        bar = null;
        if (tab == TAB_RULES) {
            buildRules();
        } else {
            buildToday();
        }
    }

    private void addTab(final int index, int iconRes, String label) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setFocusable(true);
        boolean on = tab == index;

        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Ui.col(this, on ? R.color.accent : R.color.dim2));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                Ui.dp(this, 18), Ui.dp(this, 18));
        t.addView(iv, ip);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(11);
        tv.setTypeface(Ui.medium(this));
        tv.setTextColor(Ui.col(this, on ? R.color.accent : R.color.dim));
        tv.setIncludeFontPadding(false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = Ui.dp(this, 5);
        t.addView(tv, tp);

        t.setOnClickListener(v -> {
            if (tab != index) {
                tab = index;
                build();
                if (scroll != null) {
                    scroll.scrollTo(0, 0);
                }
            }
        });
        tabbar.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
    }

    // ------------------------------------------------------------------ 今天

    private void buildToday() {
        boolean on = Prefs.enabled(this);

        // 顶上那行：今天的状态灯
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout live = new LinearLayout(this);
        live.setOrientation(LinearLayout.HORIZONTAL);
        live.setGravity(Gravity.CENTER_VERTICAL);
        liveDot = new View(this);
        int ds = Ui.dp(this, 6);
        live.addView(liveDot, new LinearLayout.LayoutParams(ds, ds));
        liveText = new TextView(this);
        liveText.setTextSize(11);
        liveText.setTypeface(Ui.medium(this));
        liveText.setLetterSpacing(0.14f);
        liveText.setIncludeFontPadding(false);
        LinearLayout.LayoutParams ltp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ltp.leftMargin = Ui.dp(this, 6);
        live.addView(liveText, ltp);
        LinearLayout.LayoutParams liveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(live, liveLp);
        root.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 日期
        TextView eyebrow = new TextView(this);
        eyebrow.setText(dateLabel());
        eyebrow.setTextSize(11);
        eyebrow.setTypeface(Ui.medium(this));
        eyebrow.setLetterSpacing(0.04f);
        eyebrow.setTextColor(Ui.col(this, R.color.dim));
        eyebrow.setIncludeFontPadding(false);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ep.topMargin = Ui.dp(this, 18);
        root.addView(eyebrow, ep);

        // 大标题：停一下（「一下」蓝）
        TextView title = new TextView(this);
        title.setId(R.id.page_title);
        SpannableStringBuilder sb = new SpannableStringBuilder("停一下");
        sb.setSpan(new ForegroundColorSpan(Ui.col(this, R.color.accent)), 1, 3,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(sb);
        title.setTextSize(Ui.TITLE_SP);
        title.setTypeface(Ui.medium(this));
        title.setLetterSpacing(-0.02f);
        title.setTextColor(Ui.col(this, R.color.text));
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(this, 6);
        root.addView(title, tlp);

        // 一条蓝色短线
        View rule = new View(this);
        GradientDrawable line = new GradientDrawable();
        line.setColor(Ui.col(this, R.color.accent));
        line.setCornerRadius(Ui.dp(this, 1));
        rule.setBackground(line);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                Ui.dp(this, 28), Math.max(1, Ui.dp(this, 1)));
        rlp.topMargin = Ui.dp(this, 16);
        root.addView(rule, rlp);

        // 状态行
        LinearLayout state = new LinearLayout(this);
        state.setOrientation(LinearLayout.HORIZONTAL);
        state.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        state.addView(statusDot, new LinearLayout.LayoutParams(ds, ds));
        statusLine = new TextView(this);
        statusLine.setId(R.id.status_line);
        statusLine.setTextSize(13);
        statusLine.setTextColor(Ui.col(this, R.color.dim));
        statusLine.setIncludeFontPadding(false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = Ui.dp(this, 8);
        state.addView(statusLine, sp);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stp.topMargin = Ui.dp(this, 16);
        root.addView(state, stp);

        // 今天刷了多久
        LinearLayout panel = Ui.card(this);
        panel.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 13));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView headTitle = new TextView(this);
        headTitle.setText("今天刷了");
        headTitle.setTextSize(11);
        headTitle.setTypeface(Ui.medium(this));
        headTitle.setLetterSpacing(0.14f);
        headTitle.setTextColor(Ui.col(this, R.color.dim));
        headTitle.setIncludeFontPadding(false);
        head.addView(headTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        metricTag = Ui.pill(this, "被拦 " + Prefs.todayLocks(this) + " 次");
        head.addView(metricTag);
        panel.addView(head);

        LinearLayout big = new LinearLayout(this);
        big.setOrientation(LinearLayout.HORIZONTAL);
        big.setGravity(Gravity.BOTTOM);
        metricTime = new TextView(this);
        metricTime.setTextSize(44);
        metricTime.setTypeface(Ui.medium(this));
        metricTime.setLetterSpacing(-0.02f);
        metricTime.setTextColor(Ui.col(this, R.color.text));
        metricTime.setIncludeFontPadding(false);
        Ui.tabular(metricTime);
        big.addView(metricTime);
        TextView unit = new TextView(this);
        unit.setText("MIN");
        unit.setTextSize(11);
        unit.setTypeface(Ui.medium(this));
        unit.setLetterSpacing(0.14f);
        unit.setTextColor(Ui.col(this, R.color.dim));
        unit.setIncludeFontPadding(false);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ulp.leftMargin = Ui.dp(this, 8);
        ulp.bottomMargin = Ui.dp(this, 7);
        big.addView(unit, ulp);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 6);
        panel.addView(big, blp);

        bar = Ui.bar(this, 0f);
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 4));
        brp.topMargin = Ui.dp(this, 14);
        panel.addView(bar, brp);

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        footLeft = new TextView(this);
        footLeft.setTextSize(13);
        footLeft.setTextColor(Ui.col(this, R.color.dim));
        footLeft.setIncludeFontPadding(false);
        foot.addView(footLeft, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        footRight = new TextView(this);
        footRight.setTextSize(13);
        footRight.setTextColor(Ui.col(this, R.color.dim));
        footRight.setIncludeFontPadding(false);
        Ui.tabular(footRight);
        foot.addView(footRight);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Ui.dp(this, 12);
        panel.addView(foot, flp);
        root.addView(panel);

        // 拦截时间：一行设置项
        LinearLayout stat = Ui.card(this);
        limitValue = Ui.value(this, Text.limit(Prefs.limitMin(this)));
        limitValue.setTextColor(Ui.col(this, R.color.text));
        LinearLayout limitRow = Ui.row(this, 0, "拦截时间", null, limitValue);
        Ui.tap(limitRow, this::pickLimit);
        stat.addView(limitRow);
        root.addView(stat);

        // 开始 / 关闭
        TextView btn = Ui.button(this, on ? "关闭" : "开始", on ? Ui.DANGER : Ui.PRIMARY);
        btn.setId(R.id.primary_button);
        LinearLayout.LayoutParams cta = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cta.topMargin = Ui.dp(this, 26);
        root.addView(btn, cta);
        btn.setOnClickListener(v -> toggle());

        updateStatus();
    }

    // ------------------------------------------------------------------ 规则

    private void buildRules() {
        // 解锁
        Ui.header(root, "解锁");
        LinearLayout unlock = Ui.card(this);
        TextView autoVal = Ui.value(this, AutoUnlock.label(Prefs.autoUnlockMin(this)));
        autoVal.setTextColor(Ui.col(this, R.color.text));
        LinearLayout autoRow = Ui.row(this, R.drawable.ic_lock, "自动解锁", null, autoVal);
        Ui.tap(autoRow, this::pickAutoUnlock);
        unlock.addView(autoRow);
        root.addView(unlock);

        // 动画
        Ui.header(root, "动画");
        LinearLayout anim = Ui.card(this);
        TextView motionVal = Ui.valueAccent(this, Motion.label(Prefs.motion(this)));
        LinearLayout motionRow = Ui.row(this, R.drawable.ic_bolt, "遮罩动画", null, motionVal);
        Ui.tap(motionRow, this::pickMotion);
        anim.addView(motionRow);
        anim.addView(Ui.divider(this));
        TextView speedVal = Ui.value(this, MotionSpeed.label(Prefs.motionSpeed(this)));
        speedVal.setTextColor(Ui.col(this, R.color.text));
        LinearLayout speedRow = Ui.row(this, R.drawable.ic_play, "动画速度", null, speedVal);
        Ui.tap(speedRow, this::pickSpeed);
        anim.addView(speedRow);
        root.addView(anim);

        // 目标 App
        Ui.header(root, "目标 App");
        LinearLayout targets = Ui.card(this);
        targets.addView(Ui.row(this, R.drawable.ic_douyin, "抖音", null, switchTarget(Prefs.DOUYIN)));
        targets.addView(Ui.divider(this));
        targets.addView(Ui.row(this, R.drawable.ic_bilibili, "哔哩哔哩", null, switchTarget(Prefs.BILI)));
        targets.addView(Ui.divider(this));
        targets.addView(Ui.row(this, R.drawable.ic_target, "播放时才计时", null, strictSwitch()));
        root.addView(targets);

        // 后台
        Ui.header(root, "后台");
        LinearLayout background = Ui.card(this);
        background.addView(Ui.row(this, R.drawable.ic_eye_off, "后台隐藏",
                "从最近任务里看不到", hideSwitch()));
        root.addView(background);

        // 权限
        boolean a11y = Env.a11y(this);
        boolean over = Env.overlay(this);
        boolean bat = Env.battery(this);
        int ready = (a11y ? 1 : 0) + (over ? 1 : 0) + (bat ? 1 : 0);

        LinearLayout permHead = new LinearLayout(this);
        permHead.setOrientation(LinearLayout.HORIZONTAL);
        permHead.setGravity(Gravity.CENTER_VERTICAL);
        permHead.setPadding(0, Ui.dp(this, 22), 0, 0);
        TextView permTitle = new TextView(this);
        permTitle.setText("权限");
        permTitle.setTextSize(11);
        permTitle.setTypeface(Ui.medium(this));
        permTitle.setLetterSpacing(0.14f);
        permTitle.setTextColor(Ui.col(this, R.color.dim));
        permTitle.setIncludeFontPadding(false);
        LinearLayout.LayoutParams ptp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ptp.leftMargin = Ui.dp(this, 2);
        permHead.addView(permTitle, ptp);

        LinearLayout readyRow = new LinearLayout(this);
        readyRow.setOrientation(LinearLayout.HORIZONTAL);
        readyRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView check = new ImageView(this);
        check.setImageResource(R.drawable.ic_check);
        readyRow.addView(check, new LinearLayout.LayoutParams(Ui.dp(this, 12), Ui.dp(this, 12)));
        TextView readyText = new TextView(this);
        readyText.setText(ready + " / 3 已开");
        readyText.setTextSize(11);
        readyText.setTextColor(Ui.col(this, R.color.dim));
        readyText.setIncludeFontPadding(false);
        LinearLayout.LayoutParams rtp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rtp.leftMargin = Ui.dp(this, 5);
        readyRow.addView(readyText, rtp);
        permHead.addView(readyRow);
        root.addView(permHead);

        LinearLayout perms = Ui.card(this);
        perms.setPadding(0, Ui.dp(this, 0), 0, Ui.dp(this, 0));
        LinearLayout a11yRow = tapRow(Ui.row(this, R.drawable.ic_shield, "无障碍",
                "识别当前 App", Ui.state(this, a11y ? "已开" : "去开", a11y)), () -> {
            try {
                launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Throwable t) {
                toast("打不开设置，手动去系统设置里找");
            }
        });
        perms.addView(a11yRow);
        perms.addView(Ui.divider(this));
        LinearLayout overRow = tapRow(Ui.row(this, R.drawable.ic_phone, "悬浮窗",
                "显示遮罩", Ui.state(this, over ? "已开" : "去开", over)), () -> {
            try {
                launch(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable t) {
                toast("打不开设置，手动去系统设置里找");
            }
        });
        perms.addView(overRow);
        perms.addView(Ui.divider(this));
        perms.addView(tapRow(Ui.row(this, R.drawable.ic_bell, "电池优化",
                "后台常驻", Ui.state(this, bat ? "已设" : "去设", bat)), this::askBattery));
        root.addView(perms);

        // 关于
        Ui.header(root, "关于");
        LinearLayout about = Ui.card(this);
        LinearLayout update = Ui.row(this, R.drawable.ic_info, "检查更新",
                getString(R.string.update_current, versionName()));
        Ui.tap(update, this::checkForUpdates);
        about.addView(update);
        root.addView(about);

        // 三个按钮
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView test = Ui.button(this, "试一下拦截", Ui.GHOST);
        TextView change = Ui.button(this, "换密码", Ui.GHOST);
        TextView forget = Ui.button(this, "忘记密码", Ui.GHOST);
        for (TextView b : new TextView[]{test, change, forget}) {
            b.setTextSize(14);
            b.setSingleLine(true);
            b.setPadding(Ui.dp(this, 6), b.getPaddingTop(), Ui.dp(this, 6), b.getPaddingBottom());
        }
        test.setOnClickListener(v -> testLock());
        change.setOnClickListener(v -> requirePin("先输现在的密码", pin -> {
            if (!Gate.check(this, pin)) {
                return false;
            }
            Intent i = new Intent(this, SetupActivity.class);
            i.putExtra(SetupActivity.EXTRA_CHANGE, true);
            launch(i);
            return true;
        }));
        forget.setOnClickListener(v -> wipeConfirm());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0,
                Ui.dp(this, 52), 1f);
        ap.rightMargin = Ui.dp(this, 4);
        actions.addView(test, ap);
        LinearLayout.LayoutParams ap2 = new LinearLayout.LayoutParams(0,
                Ui.dp(this, 52), 1f);
        ap2.leftMargin = Ui.dp(this, 4);
        ap2.rightMargin = Ui.dp(this, 4);
        actions.addView(change, ap2);
        LinearLayout.LayoutParams ap3 = new LinearLayout.LayoutParams(0,
                Ui.dp(this, 52), 1f);
        ap3.leftMargin = Ui.dp(this, 4);
        actions.addView(forget, ap3);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.dp(this, 16);
        root.addView(actions, alp);

        TextView foot = new TextView(this);
        foot.setText("本地数据 · 仅更新时联网");
        foot.setTextSize(11);
        foot.setTextColor(Ui.col(this, R.color.dim2));
        foot.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fp.topMargin = Ui.dp(this, 20);
        root.addView(foot, fp);
    }

    private LinearLayout tapRow(LinearLayout row, Runnable action) {
        Ui.tap(row, action);
        return row;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "";
        }
    }

    private void checkForUpdates() {
        if (checkingUpdate) {
            return;
        }
        checkingUpdate = true;
        final ProgressDialog progress = ProgressDialog.show(this,
                getString(R.string.update_check),
                getString(R.string.update_checking), true, false);
        UpdateChecker.check(this, (info, error) -> {
            checkingUpdate = false;
            if (isFinishing() || isDestroyed()) {
                return;
            }
            progress.dismiss();
            if (error != null) {
                toast(getString(R.string.update_check_failed));
                return;
            }
            if (info == null) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.update_check)
                        .setMessage(R.string.update_latest)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            }
            showUpdate(info);
        });
    }

    private void showUpdate(final UpdateInfo info) {
        String notes = info.notes.isEmpty()
                ? getString(R.string.update_empty_notes) : info.notes;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.update_found, info.versionName))
                .setMessage(notes)
                .setPositiveButton(R.string.update_download, (d, w) -> downloadUpdate(info))
                .setNegativeButton(R.string.update_later, null)
                .show();
    }

    private void downloadUpdate(final UpdateInfo info) {
        if (Build.VERSION.SDK_INT >= 26
                && !getPackageManager().canRequestPackageInstalls()) {
            showUnknownSources();
            return;
        }
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setTitle(getString(R.string.update_found, info.versionName));
        progress.setMessage(getString(R.string.update_downloading));
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();

        UpdateDownloader.download(this, info, new UpdateDownloader.Listener() {
            @Override
            public void onProgress(int percent) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progress.setIndeterminate(false);
                progress.setProgress(percent);
            }

            @Override
            public void onDone(File apk) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progress.dismiss();
                installUpdate(apk);
            }

            @Override
            public void onError(int code) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progress.dismiss();
                int message = R.string.update_download_failed;
                if (code == UpdateDownloader.ERROR_HASH
                        || code == UpdateDownloader.ERROR_SIGNATURE) {
                    message = R.string.update_verify_failed;
                } else if (code == UpdateDownloader.ERROR_STORAGE) {
                    message = R.string.update_storage_failed;
                }
                toast(getString(message));
            }
        });
    }

    private void installUpdate(File apk) {
        try {
            UpdateInstaller.install(this, apk);
            toast(getString(R.string.update_install_request));
        } catch (SecurityException e) {
            showUnknownSources();
        } catch (Exception e) {
            toast(getString(R.string.update_install_failed));
        }
    }

    private void showUnknownSources() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.update_unknown_sources)
                .setMessage(R.string.update_unknown_sources_msg)
                .setPositiveButton(R.string.update_go_settings, (d, w) -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Throwable t) {
                        toast(getString(R.string.update_install_failed));
                    }
                })
                .setNegativeButton(R.string.update_later, null)
                .show();
    }

    private String dateLabel() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat f = new SimpleDateFormat("M 月 d 日 · EEEE", Locale.CHINA);
        return f.format(c.getTime());
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

    private IosSwitch hideSwitch() {
        IosSwitch sw = new IosSwitch(this);
        sw.setChecked(Prefs.hideRecents(this));
        sw.setOnCheckedChangeListener((v, checked) -> {
            Prefs.setHideRecents(this, checked);
            toast(checked ? "离开以后最近任务里看不到，回桌面再点图标进来"
                    : "最近任务里又能看到它了");
        });
        return sw;
    }

    // ------------------------------------------------------------------ 状态

    private void updateStatus() {
        boolean on = Prefs.enabled(this);
        boolean a11y = Env.a11y(this);
        boolean over = Env.overlay(this);
        GuardState st = GuardState.get();
        boolean running = on && a11y && over && st.serviceRunning;

        String text;
        int color;
        if (!on) {
            text = "关闭状态";
            color = Ui.col(this, R.color.dim2);
        } else if (!a11y) {
            text = "无障碍没开 · 拦不住";
            color = Ui.col(this, R.color.bad);
        } else if (!over) {
            text = "悬浮窗没开 · 盖不住";
            color = Ui.col(this, R.color.bad);
        } else if (!st.serviceRunning) {
            text = "计时服务不在 · 点开始重开";
            color = Ui.col(this, R.color.warn);
        } else if (st.countedMs >= 1000L) {
            text = "正在计时 " + Text.mmss(st.countedMs);
            color = Ui.col(this, R.color.accent);
        } else {
            text = "在看着 · 没在刷";
            color = Ui.col(this, R.color.accent);
        }

        if (statusLine != null) {
            statusLine.setText(text);
        }
        if (statusDot != null) {
            boolean hollow = !on || !running;
            statusDot.setBackground(hollow ? Ui.ring(this, color) : Ui.dot(this, color));
        }
        if (liveDot != null) {
            liveDot.setBackground(running
                    ? Ui.dot(this, Ui.col(this, R.color.accent))
                    : Ui.ring(this, Ui.col(this, R.color.dim2)));
        }
        if (liveText != null) {
            liveText.setText(running ? "ON" : "OFF");
            liveText.setTextColor(Ui.col(this, running ? R.color.accent : R.color.dim));
        }

        long today = Prefs.todayMs(this);
        long limitMs = Prefs.limitMin(this) * 60_000L;
        float fraction = limitMs <= 0 ? 0f : Math.min(1f, today / (float) limitMs);
        if (metricTime != null) {
            metricTime.setText(clockOf(today));
        }
        if (metricTag != null) {
            metricTag.setText("被拦 " + Prefs.todayLocks(this) + " 次");
        }
        if (bar != null) {
            bar.setFraction(fraction);
        }
        if (footLeft != null) {
            long left = Math.max(0L, limitMs - today);
            footLeft.setText("距离上限 " + clockOf(left));
        }
        if (footRight != null) {
            footRight.setText(clockOf(limitMs));
        }
        if (limitValue != null) {
            limitValue.setText(Text.limit(Prefs.limitMin(this)));
        }
    }

    /** 18:42 这种写法，给大数字用。 */
    private static String clockOf(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        long m = total / 60L;
        long s = total % 60L;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    // ------------------------------------------------------------------ 动作

    private void pickLimit() {
        pickMinutes("拦截时间", (int) Prefs.limitMin(this), Limits.MAX, false, mins -> {
            Prefs.setLimitMin(this, mins);
            if (Prefs.enabled(this)) {
                stopService(new Intent(this, GuardService.class));
                ReviveJob.startGuard(this);
            }
            build();
        });
    }

    private interface MinutesOk {
        void run(int minutes);
    }

    /**
     * 时 / 分两个滚轮：分钟精确到 1 分钟；allowOff 时 0:00 表示关掉。
     */
    private void pickMinutes(String title, int current, int maxMin, boolean allowOff,
                             final MinutesOk ok) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.dialog_bg);
        int p = Ui.dp(this, 18);
        box.setPadding(p, Ui.dp(this, 20), p, Ui.dp(this, 14));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTypeface(Ui.medium(this));
        t.setTextColor(Ui.col(this, R.color.text));
        t.setGravity(Gravity.CENTER);
        box.addView(t);

        int hourMax = Math.max(0, maxMin / 60);
        int[] hours = new int[hourMax + 1];
        for (int i = 0; i < hours.length; i++) {
            hours[i] = i;
        }
        int[] minutes = new int[60];
        for (int i = 0; i < minutes.length; i++) {
            minutes[i] = i;
        }
        int curHour = Math.max(0, Math.min(hourMax, current / 60));
        int curMinute = Math.max(0, Math.min(59, current % 60));

        LinearLayout wheels = new LinearLayout(this);
        wheels.setOrientation(LinearLayout.HORIZONTAL);
        wheels.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 250));
        wp.topMargin = Ui.dp(this, 8);

        final WheelView hour = new WheelView(this, hours, curHour);
        final WheelView minute = new WheelView(this, minutes, curMinute);
        wheels.addView(column(hour, "时"), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        wheels.addView(column(minute, "分"), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        box.addView(wheels, wp);

        if (allowOff) {
            TextView offHint = new TextView(this);
            offHint.setText("调到 00:00 就是不自动解锁");
            offHint.setTextSize(12);
            offHint.setTextColor(Ui.col(this, R.color.dim));
            offHint.setGravity(Gravity.CENTER);
            offHint.setIncludeFontPadding(false);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            hp.topMargin = Ui.dp(this, 8);
            box.addView(offHint, hp);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.button(this, "取消", Ui.GHOST);
        TextView okButton = Ui.button(this, "完成", Ui.PRIMARY);
        cancel.setOnClickListener(v -> d.dismiss());
        okButton.setOnClickListener(v -> {
            int mins = hour.index() * 60 + minute.index();
            if (!(allowOff && mins == 0)) {
                if (mins < Limits.MIN) {
                    mins = Limits.MIN;
                }
                if (mins > maxMin) {
                    mins = maxMin;
                }
            }
            d.dismiss();
            ok.run(mins);
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cp.rightMargin = Ui.dp(this, 5);
        actions.addView(cancel, cp);
        LinearLayout.LayoutParams okp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        okp.leftMargin = Ui.dp(this, 5);
        actions.addView(okButton, okp);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.dp(this, 14);
        box.addView(actions, alp);

        d.setContentView(box);
        d.show();
        android.view.Window win = d.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
            win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams attrs = win.getAttributes();
            attrs.dimAmount = 0.45f;
            win.setAttributes(attrs);
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
            win.setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private LinearLayout column(View w, String label) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(11);
        t.setTextColor(Ui.col(this, R.color.dim));
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        lp.bottomMargin = Ui.dp(this, 4);
        col.addView(t, lp);
        col.addView(w, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 220)));
        return col;
    }

    private void pickAutoUnlock() {
        pickMinutes("自动解锁", Prefs.autoUnlockMin(this), AutoUnlock.MAX, true, mins -> {
            Prefs.setAutoUnlockMin(this, mins);
            build();
        });
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
                .setNegativeButton("取消", null)
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
                .setNegativeButton("取消", null)
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
                .setNegativeButton("取消", null)
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
                .setNegativeButton("取消", null)
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
                .setTitle("清空数据后重设")
                .setMessage("密码、今天的数据、所有设置都会没了。防护也会关掉。")
                .setPositiveButton("清掉", (d, w) -> {
                    Prefs.wipe(this);
                    stopService(new Intent(this, GuardService.class));
                    ReviveJob.cancel(this);
                    GuardState.get().countedMs = 0;
                    GuardState.get().fgPackage = "";
                    launch(new Intent(this, SetupActivity.class));
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void askBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            launch(i);
        } catch (Throwable t) {
            try {
                launch(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
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
