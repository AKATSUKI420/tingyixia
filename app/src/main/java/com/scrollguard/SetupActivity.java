package com.scrollguard;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * 设密码 / 换密码。两步：输一次，再输一次确认。
 */
public class SetupActivity extends Activity {

    public static final String EXTRA_CHANGE = "change";

    private int step;
    private String first;
    private PinPadView pad;
    private TextView title;
    private TextView sub;
    private boolean changing;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_setup);
        title = findViewById(R.id.setup_title);
        sub = findViewById(R.id.setup_sub);
        pad = findViewById(R.id.setup_pad);
        changing = getIntent() != null && getIntent().getBooleanExtra(EXTRA_CHANGE, false);
        pad.setListener(this::onPin);
        showStep(0);
    }

    private void showStep(int s) {
        step = s;
        if (s == 0) {
            title.setText(changing ? "设一个新密码" : "先设个密码");
            sub.setText("6 位数字，忘了只能清数据重来");
        } else {
            title.setText("再输一遍");
            sub.setText("确认一下刚才那 6 位");
        }
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
    public void onBackPressed() {
        // 没设完不能溜
    }
}
