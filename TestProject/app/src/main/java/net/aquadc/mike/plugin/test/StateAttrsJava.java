package net.aquadc.mike.plugin.test;

import android.animation.StateListAnimator;
import android.content.res.ColorStateList;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;

@SuppressWarnings("unused")
class StateAttrsJava {

    static {
        new ColorStateList(
                new int[][]{ { android.R.attr.checkable, android.R.attr.checked, android.R.attr.enabled, 0 } },
                null
        );
        new StateListDrawable().addState(
                new int[] { android.R.attr.checkable, android.R.attr.checked, android.R.attr.enabled, 1 },
                null
        );
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP)
            new StateListAnimator().addState(
                    new int[] { android.R.attr.checkable, android.R.attr.checked, android.R.attr.enabled, 2 },
                    null
            );
    }
}
