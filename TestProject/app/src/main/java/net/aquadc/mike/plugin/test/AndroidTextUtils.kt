@file:Suppress("unused")

package net.aquadc.mike.plugin.test

import android.text.TextUtils
import java.util.regex.Pattern

fun androidTextUtils() {
    TextUtils.join(", ", arrayOf(1, 2, 3))
    TextUtils.join(", ", listOf(1, 2, 3))
    TextUtils.split("a, b, c", ", ")
    TextUtils.split("a, b, c", Pattern.compile(", "))
    TextUtils.isEmpty("")
}
