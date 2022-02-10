package net.aquadc.mike.plugin.android.res

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import java.util.*
import kotlin.collections.HashMap


private val resTypes = HashMap<String, ResourceType>().apply {
    ResourceType.values().forEach {
        if (it.hasInnerClass) {
            put(it.name.toLowerCase(Locale.ROOT), it)
        }
    }
}
fun ResourceResolver?.resolve(tag: XmlTag, name: String, namespace: String): String? =
    resolve(tag.getAttributeValue(name, namespace))

fun ResourceResolver?.resolve(attr: XmlAttribute): String? =
    resolve(attr.value)

fun ResourceResolver?.resolve(attr: XmlAttributeValue): String? =
    resolve(attr.value)

fun ResourceResolver?.resolve(raw: String?): String? {
    if (raw.isNullOrBlank()) return raw

    // @[<package_name>:]<resource_type>/<resource_name>
    // ?[<package_name>:][<resource_type>/]<resource_name>
    val refType = raw[0]
    if (refType != '@' && refType != '?') return raw

    if (this == null) return null // can't resolve

    val colon = raw.indexOf(':')
    val resNs =
        if (colon > 0 && raw.regionMatches(1, "android:", 0, "android:".length)) ResourceNamespace.ANDROID
        else ResourceNamespace.RES_AUTO

    val slash = raw.indexOf('/', colon)
    val resType = when {
        refType == '?' -> ResourceType.ATTR
        slash < 0 -> return null // invalid
        else -> resTypes[raw.substring(1, slash)] ?: return null // invalid
    }

    val resName = raw.substring(if (slash < 0) 1 else (slash + 1))
    val resourceReference = ResourceReference(resNs, resType, resName)
    return getResolvedResource(resourceReference)?.value
}


