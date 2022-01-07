package net.aquadc.mike.plugin.test;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.util.Property;
import android.view.View;
import androidx.annotation.RequiresApi;

@SuppressLint("ObjectAnimatorBinding")
@SuppressWarnings({"unused", "RedundantArrayCreation"})
@RequiresApi(21)
public class ReflectPropAnimJava {
    static {
        new ObjectAnimator().setPropertyName("alpha");
        new ObjectAnimator().setProperty(View.ALPHA);
        new ObjectAnimator().getPropertyName();
        ObjectAnimator.ofFloat(new View(null), "translationW");
        ObjectAnimator.ofFloat(new View(null), "translationX", new float[]{});
        ObjectAnimator.ofFloat(new View(null), "translationX", "translationY", null);
        ObjectAnimator.ofFloat(new Object(), "translationX", "translationY", null); // unfixable because arg[0] is Object
        ObjectAnimator.ofInt(null, (Property<?, Integer>) null);
        ObjectAnimator.ofInt(null, null, (Property<?, Integer>) null, null);
        Property.of(View.class, Float.class, "translationX");
        Property.of(Object.class, Float.class, "translationX"); // unfixable
    }
}
