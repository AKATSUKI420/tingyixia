package com.scrollguard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.FrameLayout;

/**
 * 锁屏的根容器。把返回键吞掉，免得一按就退回上一个界面。
 */
public class LockRoot extends FrameLayout {

    private boolean hadWindowFocus;
    private Runnable onFocusLost;

    public LockRoot(Context c) {
        super(c);
    }

    public LockRoot(Context c, AttributeSet a) {
        super(c, a);
    }

    public LockRoot(Context c, AttributeSet a, int defStyleAttr) {
        super(c, a, defStyleAttr);
    }

    /** 只在真正失去焦点时通知；窗口刚挂上时那次 false 不算。 */
    public void setOnFocusLost(Runnable r) {
        onFocusLost = r;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) {
            hadWindowFocus = true;
        } else if (hadWindowFocus) {
            hadWindowFocus = false;
            if (onFocusLost != null) {
                onFocusLost.run();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        hadWindowFocus = false;
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
}
