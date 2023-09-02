package net.aquadc.mike.plugin.android.res

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceFolderType.DRAWABLE
import com.android.resources.ResourceFolderType.LAYOUT
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.*
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import net.aquadc.mike.plugin.SortedArray
import net.aquadc.mike.plugin.android.androidMinSdk
import net.aquadc.mike.plugin.android.resTypeOf
import java.text.MessageFormat
import java.util.*
import java.lang.Character.MIN_VALUE as nullChar

/**
 * @author Mike Gorünóv
 */
class UnsupportedAttrInspection : LocalInspectionTool() {

    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitFile(file: PsiFile) {
            if (file !is XmlFile) return
            val af = file.androidFacet ?: return
            val minSdk = af.androidMinSdk()?.apiLevel ?: return
            val resType = af.resTypeOf(file)?.takeIf(Crap.resTypes::contains) ?: return
            file.accept(object : XmlRecursiveElementVisitor() {
                override fun visitXmlAttribute(attribute: XmlAttribute) {
                    val parentTag = attribute.parent.parentTag?.name
                    val tag = attribute.parent.name
                    val attr = attribute.localName
                    attribute.parent.parentTag
                    Crap.shitBunch.firstOrNull { it.test(minSdk, resType, parentTag, tag, attr, attribute) }?.let {
                        val cries = "support requires API level ${it.requiredSdk} (current min is $minSdk)"
                        holder.registerProblem(attribute.originalElement, MessageFormat.format(it.message, attr, cries))
                    }
                }

            })
        }

    }
}

private class Crap(
    val resType: ResourceFolderType,
    private val parent: SortedArray<String>? = null,
    private val tag: String,
    private val attr: String,
    val message: String,
    val requiredSdk: Int,
    private val prohibitedFirstChar: Char = nullChar,
) {
    fun test(minSdk: Int, resType: ResourceFolderType, parentTag: String?, tag: String, attr: String, xml: XmlAttribute): Boolean {
        if (this.requiredSdk <= minSdk || this.resType != resType ||
            (this.parent != null && (parentTag == null || parentTag !in this.parent)) ||
            this.tag != tag || this.attr != attr)
            return false

        if (prohibitedFirstChar == nullChar)
            return true // bad attribute, don't even check value

        val value = xml.value?.takeIf(String::isNotEmpty) ?: return false
        return value[0] == prohibitedFirstChar // bad prefix
    }

    companion object {
        val knownSdks = intArrayOf(21, 23)
        val shitBunch = arrayOf(
            Crap(
                DRAWABLE, tag = "bitmap", attr = "tint", requiredSdk = 21,
                message = "<bitmap android:{0}> {1}",
            ),
            Crap(
                DRAWABLE,
                parent = SortedArray.of("layer-list", "ripple", "transition"),
                tag = "item", attr = "gravity", requiredSdk = 23,
                message = "<layer-list><item android:gravity> {1}",
            ),
            Crap(
                LAYOUT, tag = "include", attr = "layout", requiredSdk = 23, prohibitedFirstChar = '?',
                message = "<include layout=\"?themeAttribute\"> {1}",
            ),
        )
        val resTypes = shitBunch.mapTo(EnumSet.noneOf(ResourceFolderType::class.java), Crap::resType)
    }

}
