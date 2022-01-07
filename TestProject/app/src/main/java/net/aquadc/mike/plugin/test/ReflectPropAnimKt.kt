package net.aquadc.mike.plugin.test

import android.animation.ObjectAnimator
import android.annotation.TargetApi
import android.util.Property
import android.view.View

@Suppress("androidTargetApiShouldBeRequires")
@TargetApi(21)
fun anims() {
    ObjectAnimator().setPropertyName("alpha")
    ObjectAnimator().propertyName = "alpha"
    ObjectAnimator().run {
        setPropertyName("alpha")
        propertyName = "alpha"
    }
    ObjectAnimator().setProperty(View.ALPHA)
    ObjectAnimator().propertyName
    ObjectAnimator.ofFloat(View(null), "translationW")
    ObjectAnimator.ofFloat(View(null), "translationX")
    ObjectAnimator.ofFloat(View(null), "translationX", "translationY", null)
    ObjectAnimator.ofInt(null, null as Property<Nothing?, Int>?)
    ObjectAnimator.ofInt(null, null, null as Property<Nothing?, Int>?, null)

    val view = View(null)

    ObjectAnimator
        .ofFloat(view, "alpha", 0f, 1f)

    Property.of(View::class.java, Float::class.java, "translationX")
}
