@file:Suppress("ObjectPropertyName", "unused", "SpellCheckingInspection")
package net.aquadc.mike.plugin.test

private val _xxx = ArrayList<String>()
val xxx: List<String> get() = _xxx

class Backing {
    private var _yyy = ArrayList<String>()
    val yyy: List<String> get() = _yyy

    private var privZzz = ArrayList<String>()
        get() = ArrayList(field)
        set(al) { field = al.filterTo(ArrayList(), String::isNotBlank) }
    var zzz: List<String>
        get() = privZzz
        internal set(v) { privZzz = ArrayList(v) }
}
