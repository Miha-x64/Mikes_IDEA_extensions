package net.aquadc.mike.plugin.android.res

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag


private val resTypes = HashMap<String, ResourceType>().apply {
    ResourceType.values().forEach {
        if (it.hasInnerClass) {
            put(it.getName(), it)
        }
    }
}
private val nullToNull = null to null
fun ResourceResolver?.resolve(tag: XmlTag, name: String, namespace: String): String? =
    resolve(tag.getAttributeValue(name, namespace)).second

fun ResourceResolver?.resolve(attr: XmlAttribute): String? =
    resolve(attr.value).second

/**
 * Parse android resource reference.
 * @return Pair<canonical, resolvedValue>
 */
fun ResourceResolver?.resolve(raw: String?): Pair<String?, String?> {
    if (raw.isNullOrBlank()) return raw to raw

    var canonical = raw
    // @[<package_name>:]<resource_type>/<resource_name>
    // ?[<package_name>:][<resource_type>/]<resource_name>
    val refType = raw[0]
    if (this == null || (refType != '@' && refType != '?')) {
        if (refType == '#' && raw.length.let { it == 4 || it == 5 || it == 7 || it == 9 }) {
            canonical = when (raw.length) { // canonicalize color
                4 /*#RGB*/ -> "#FF${raw[1]}${raw[1]}${raw[2]}${raw[2]}${raw[3]}${raw[3]}".uppercase()
                5 /*#ARGB*/ -> "#${raw[1]}${raw[1]}${raw[2]}${raw[2]}${raw[3]}${raw[3]}${raw[4]}${raw[4]}".uppercase()
                7 /*#RRGGBB*/ -> "#FF${raw[1]}${raw[2]}${raw[3]}${raw[4]}${raw[5]}${raw[6]}".uppercase()
                9 /*#AARRGGBB*/ -> raw.uppercase()
                else -> throw AssertionError()
            }
        }
        return canonical to canonical
    }

    val colon = raw.indexOf(':')
    val resNs =
        if (colon > 0 && raw.regionMatches(1, "android:", 0, "android:".length)) ResourceNamespace.ANDROID
        else ResourceNamespace.RES_AUTO

    val slash = raw.indexOf('/', colon)
    val resType = when {
        refType == '?' -> ResourceType.ATTR
        slash < 0 -> return nullToNull // invalid
        else -> resTypes[raw.substring(1, slash)] ?: return nullToNull // invalid
    }

    val resName = raw.substring(if (slash < 0) 1 else (slash + 1))
    canonical = "@${if (resNs === ResourceNamespace.ANDROID) "android:" else ""}${resType.getName()}/$resName"
    val resourceReference = ResourceReference(resNs, resType, resName)
    return canonical to getResolvedResource(resourceReference)?.value
}
