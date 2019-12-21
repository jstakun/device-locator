package net.gmsworld.devicelocator.utilities;

import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

public class LinkMovementMethodFixed extends LinkMovementMethod {
    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event ) {
        try {
            return super.onTouchEvent( widget, buffer, event ) ;
        } catch( SecurityException ex ) {
            Toast.makeText( widget.getContext(), "Could not load the link on this device due to Security permissions!", Toast.LENGTH_LONG ).show();
            return true;
        } catch( Exception ex ) {
            Toast.makeText( widget.getContext(), "Could not load the link!", Toast.LENGTH_LONG ).show();
            return true;
        }
    }
}
