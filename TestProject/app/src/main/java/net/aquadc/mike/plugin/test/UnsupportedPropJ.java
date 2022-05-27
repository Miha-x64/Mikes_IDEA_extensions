package net.aquadc.mike.plugin.test;

import android.content.Context;
import android.view.View;
import android.widget.VideoView;

public class UnsupportedPropJ {
    @SuppressWarnings("unused")
    static void test(Context context) {
        new View(context).setOnClickListener(null);
        new VideoView(context).setOnClickListener(null);
        new VideoView(context) {}.setOnClickListener(null);
        new SuperVideoView(context).setOnClickListener(null);

        context.obtainStyledAttributes(new int[]{android.R.attr.content}).recycle();
        int[] var1 = {android.R.attr.content};
        context.obtainStyledAttributes(var1).recycle();
        int[] var2 = new int[] {android.R.attr.content};
        context.obtainStyledAttributes(var2).recycle();
        context.obtainStyledAttributes(ATTRS).recycle();
        context.obtainStyledAttributes(UnsupportedPropKKt.getATTRS()).recycle();
        context.obtainStyledAttributes(UnsupportedPropKKt.ATTRS2).recycle();
    }
    static final int[] ATTRS = {android.R.attr.content, 0};
    private static final class SuperVideoView extends VideoView {
        public SuperVideoView(Context context) {
            super(context);
        }
    }
}
