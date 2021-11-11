@file:Suppress("DEPRECATION", "RedundantOverride", "unused")
package net.aquadc.mike.plugin.test

import android.app.DialogFragment
import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import android.view.View

open class DialogFragKt : DialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle): View? {
        return inflater.inflate(android.R.layout.list_content, container, false)
    }

    class Another : DialogFragKt() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle): View? {
            return super.onCreateView(inflater, container, savedInstanceState)
        }
    }

    class AndMore : DialogFragKt() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle): View? =
            super.onCreateView(inflater, container, savedInstanceState)
    }
}
