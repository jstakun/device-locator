package net.gmsworld.devicelocator.Views;

import android.content.Context;
import android.support.v7.widget.AppCompatEditText;
import android.util.AttributeSet;
import android.view.KeyEvent;

/**
 * Created by jstakun on 4/21/18.
 */

public class KeyboardEditText extends AppCompatEditText {
    public KeyboardEditText(Context context) {
        super(context);
    }

    public KeyboardEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }
}
