package net.aquadc.mike.plugin.test;

import android.content.Context;
import android.view.View;
import android.widget.VideoView;

public class UnsupportedPropJ {
    @SuppressWarnings("unused")
    void test(Context context) {
        new View(context).setOnClickListener(null);
        new VideoView(context).setOnClickListener(null);
        new VideoView(context) {}.setOnClickListener(null);
        new SuperVideoView(context).setOnClickListener(null);
    }
    private static final class SuperVideoView extends VideoView {
        public SuperVideoView(Context context) {
            super(context);
        }
    }
}
