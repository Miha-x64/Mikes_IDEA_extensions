@file:Suppress("unused", "ktNoinlineFunc")

import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

val a by lazy { "whatever" }
val b by mapOf("y" to "lulz")
val c by Delegates.notNull<String>()

operator fun Byte.provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<Any?, Byte> {
    TODO()
}
operator fun Byte.getValue(thisRef: Any?, property: KProperty<*>): String {
    return "$thisRef, thank you for delegating '${property.name}' to me!"
}
operator fun Byte.setValue(thisRef: Any?, property: KProperty<*>, value: String) {
    println("$value has been assigned to '${property.name}' in $thisRef.")
}

inline operator fun Int.provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<Any?, Byte> {
    TODO()
}
inline operator fun Int.getValue(thisRef: Any?, property: KProperty<*>): String {
    return "$thisRef, thank you for delegating '${property.name}' to me!"
}
inline operator fun Int.setValue(thisRef: Any?, property: KProperty<*>, value: String) {
    println("$value has been assigned to '${property.name}' in $thisRef.")
}

