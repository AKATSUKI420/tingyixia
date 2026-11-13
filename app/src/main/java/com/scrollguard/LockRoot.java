package com.scrollguard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.FrameLayout;

/**
 * 锁屏的根容器。把返回键吞掉，免得一按就退回上一个界面。
 */
public class LockRoot extends FrameLayout {

    public LockRoot(Context c) {
        super(c);
    }

    public LockRoot(Context c, AttributeSet a) {
        super(c, a);
    }

    public LockRoot(Context c, AttributeSet a, int defStyleAttr) {
        super(c, a, defStyleAttr);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
}
