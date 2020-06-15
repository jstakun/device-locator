package net.gmsworld.devicelocator.utilities;

import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.view.MotionEvent;
import android.widget.TextView;

public class LinkMovementMethodFixed extends LinkMovementMethod {
    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event ) {
        try {
            return super.onTouchEvent( widget, buffer, event ) ;
        } catch( SecurityException ex ) {
            Toaster.showToast(widget.getContext(), "Could not load the link on this device due to Security permissions!");
            return true;
        } catch( Exception ex ) {
            Toaster.showToast(widget.getContext(), "Could not load the link!");
            return true;
        }
    }
}
