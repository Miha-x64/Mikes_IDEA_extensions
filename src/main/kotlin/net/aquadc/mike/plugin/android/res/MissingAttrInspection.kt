package net.aquadc.mike.plugin.android.res

import com.android.resources.ResourceFolderType
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlToken
import com.intellij.xml.util.XmlTagUtil
import net.aquadc.mike.plugin.NamedLocalQuickFix
import net.aquadc.mike.plugin.SortedArray
import net.aquadc.mike.plugin.android.resTypeOf

/**
 * @author Mike Gorünóv
 */
class MissingAttrInspection : LocalInspectionTool() {

    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): PsiElementVisitor = object : XmlElementVisitor() {
        override fun visitXmlTag(tag: XmlTag) {

            if (tag.containingFile.androidFacet?.resTypeOf(tag.containingFile) == ResourceFolderType.LAYOUT &&
                tag.isScrollable && tag.getAttribute("id", ANDROID_NS) == null) {
                val start: PsiElement = XmlTagUtil.getStartTagNameElement(tag) as PsiElement // ???
                holder.registerProblem(
                    start,
                    "Scrollable view should have an ID to save its scroll position",
                    object : NamedLocalQuickFix("Add ID attribute") {
                        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                            (descriptor.psiElement as? XmlToken)
                                ?.parentOfType<XmlTag>()
                                ?.setAttribute("id", ANDROID_NS, "@+id/")
                            // TODO IDK how to set caret from this context
                        }
                    }
                )
            }

            super.visitXmlTag(tag)
        }

        private val XmlTag.isScrollable
            get() = name in scrollableViews ||
                    (name in scrollableTextViews && getAttributeValue("scrollHorizontally", ANDROID_NS) == "true")
    }

    private companion object {
        private val scrollableViews = SortedArray.of(
            "android.widget.ScrollView", "ScrollView",
            "android.widget.HorizontalScrollView", "HorizontalScrollView",
            "androidx.core.widget.NestedScrollView",
            "android.widget.ListView", "ListView",
            "android.widget.GridView", "GridView",
            "androidx.recyclerview.widget.RecyclerView",
            "androidx.viewpager.widget.ViewPager",
            "androidx.viewpager2.widget.ViewPager2",
            "android.webkit.WebView",
        )
        private val scrollableTextViews = SortedArray.of(
            "android.widget.TextView", "TextView",
            "android.widget.CheckBox", "CheckBox",
            "android.widget.CheckedTextView", "CheckedTextView",
            "android.widget.RadioButton", "RadioButton",
            "android.widget.Switch", "Switch"
        )
    }
}
