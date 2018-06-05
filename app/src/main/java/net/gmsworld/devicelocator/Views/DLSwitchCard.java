package net.gmsworld.devicelocator.Views;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.CardView;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;

import net.gmsworld.devicelocator.R;

public class DLSwitchCard extends CardView {
    private TypedArray attributes = null;
    private LinearLayout linearLayout = null;
    private Switch title = null;

    /**
     * Instantiates a new Ld card.
     *
     * @param context the context
     * @param attrs   the attrs
     */
    public DLSwitchCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        attributes = getContext().obtainStyledAttributes(attrs, R.styleable.LDCard);
    }

    @Override
    protected void onFinishInflate() {

        View[] children = this.detachChildren();
        inflate(getContext(), R.layout.dlswitchcard, this);

        linearLayout = this.findViewById(R.id.linear_layout);
        title = this.findViewById(R.id.dlSwitchName);
        title.setText(attributes.getString(R.styleable.LDCard_dlSwitchName));
        if (title.getText().length() == 0) {
            linearLayout.removeView(title);
        }


        for (int i = 0; i < children.length; i++) {
            this.addView(children[i]);
        }

        attributes.recycle();
        super.onFinishInflate();
    }

    private View[] detachChildren() {
        int count = this.getChildCount();
        View[] views = new View[count];
        for (int i = 0; i < count; i++) {
            views[i] = this.getChildAt(i);
        }

        this.detachAllViewsFromParent();

        return views;
    }

    @Override
    public void addView(View view) {
        if (linearLayout != null) {
            linearLayout.addView(view);
        }
    }
}
