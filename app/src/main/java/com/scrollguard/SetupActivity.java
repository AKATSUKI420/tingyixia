package com.scrollguard;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * 设密码 / 换密码。两步：输一次，再输一次确认。
 * 极简白：导航 + 大字 + 两段进度 + 六个点 + 带框键盘。
 */
public class SetupActivity extends Activity {

    public static final String EXTRA_CHANGE = "change";

    private int step;
    private String first;
    private PinPadView pad;
    private TextView title;
    private TextView stepText;
    private View seg1;
    private View seg2;
    private boolean changing;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_setup);
        title = findViewById(R.id.setup_title);
        title.setTypeface(Ui.medium(this));
        stepText = findViewById(R.id.setup_step);
        pad = findViewById(R.id.setup_pad);
        changing = getIntent() != null && getIntent().getBooleanExtra(EXTRA_CHANGE, false);

        seg1 = ((android.view.ViewGroup) findViewById(R.id.setup_progress)).getChildAt(0);
        seg2 = ((android.view.ViewGroup) findViewById(R.id.setup_progress)).getChildAt(1);

        findViewById(R.id.setup_cancel).setOnClickListener(v -> leave());
        findViewById(R.id.setup_done).setOnClickListener(v -> leave());

        pad.setListener(this::onPin);
        showStep(0);
    }

    private void leave() {
        if (Prefs.hasPin(this)) {
            finish();
        }
    }

    private void showStep(int s) {
        step = s;
        if (s == 0) {
            title.setText(changing ? "设一个新密码" : "先设个密码");
        } else {
            title.setText("再输一遍");
        }
        stepText.setText(s == 0 ? "01 / 02" : "02 / 02");
        seg1.setBackgroundResource(R.drawable.progress_seg);
        seg2.setBackgroundResource(s == 0 ? R.drawable.progress_seg_off : R.drawable.progress_seg);
        pad.setMessage("");
        pad.clear();
    }

    private void onPin(String pin) {
        if (!Pin.wellFormed(pin)) {
            pad.error("要 6 位数字");
            return;
        }
        if (step == 0) {
            first = pin;
            showStep(1);
            return;
        }
        if (pin.equals(first)) {
            Pin.save(this, pin);
            finish();
        } else {
            first = null;
            showStep(0);
            pad.error("两次不一样，重来");
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Prefs.hideRecents(this)) {
            finishAndRemoveTask();
        }
    }

    @Override
    public void onBackPressed() {
        // 没设完不能溜
    }
}
