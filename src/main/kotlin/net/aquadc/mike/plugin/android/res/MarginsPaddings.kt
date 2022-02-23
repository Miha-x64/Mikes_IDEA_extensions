package net.aquadc.mike.plugin.android.res

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemHighlightType.LIKE_UNUSED_SYMBOL
import com.intellij.codeInspection.ProblemHighlightType.WEAK_WARNING
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import net.aquadc.mike.plugin.component6
import net.aquadc.mike.plugin.component7
import net.aquadc.mike.plugin.component8
import net.aquadc.mike.plugin.component9
import net.aquadc.mike.plugin.miserlyMap as map

internal object MarginsPaddings {
    private const val LEFT = 0
    private const val TOP = 1
    private const val RIGHT = 2
    private const val BOTTOM = 3
    private const val START = 4
    private const val END = 5
    private const val HORIZONTAL = 6
    private const val VERTICAL = 7
    private const val ALL = 8

    private val PADDINGS: Array<String>
    private val MARGINS: Array<String>

    init {
        val directions = arrayOf("Left", "Top", "Right", "Bottom", "Start", "End", "Horizontal", "Vertical", "")
        PADDINGS = directions.map { "padding$it" }
        MARGINS = directions.map { "layout_margin$it" }
    }

    fun checkLayoutTag(holder: ProblemsHolder, isOnTheFly: Boolean, tag: XmlTag, tmp: Array<XmlAttribute?> = arrayOfNulls(PADDINGS.size)) {
        PADDINGS.forEachIndexed { i, padding -> tmp[i] = tag.getAttribute(padding, ANDROID_NS) }
        val (_, _, _, _, paSta, paEnd, _, _, paAll) = tmp
        if (paSta != null || paEnd != null) maybeReport(
            holder, isOnTheFly, tmp,
            if (paSta != null && paEnd != null) " is overridden by '${PADDINGS[START]}' and '${PADDINGS[END]}'"
            else " may be overridden by '${PADDINGS[if (paSta != null) START else END]}'",
            if (paSta != null && paEnd != null) LIKE_UNUSED_SYMBOL else WEAK_WARNING,
            PADDINGS, LEFT, RIGHT, HORIZONTAL,
        ) else if (paAll != null) maybeReport(
            holder, isOnTheFly, tmp, " is overridden by '${PADDINGS[ALL]}'", LIKE_UNUSED_SYMBOL,
            PADDINGS, LEFT, RIGHT, HORIZONTAL,
        ) else if (tmp[HORIZONTAL] != null) maybeReport(
            holder, isOnTheFly, tmp, " is overridden by '${PADDINGS[HORIZONTAL]}'", LIKE_UNUSED_SYMBOL,
            PADDINGS, LEFT, RIGHT,
        )

        if (paAll != null) maybeReport(
            holder, isOnTheFly, tmp, " is overridden by '${PADDINGS[ALL]}'", LIKE_UNUSED_SYMBOL,
            PADDINGS, TOP, BOTTOM, VERTICAL,
        ) else if (tmp[VERTICAL] != null) maybeReport(
            holder, isOnTheFly, tmp, " is overridden by '${PADDINGS[VERTICAL]}'", LIKE_UNUSED_SYMBOL,
            PADDINGS, TOP, BOTTOM,
        )


        MARGINS.forEachIndexed { i, margin -> tmp[i] = tag.getAttribute(margin, ANDROID_NS) }
        val (_, _, _, _, _, _, maHor, maVer, maAll) = tmp
        if (maAll != null) maybeReport(
            holder, isOnTheFly, tmp, " is overridden by '${MARGINS[ALL]}'", LIKE_UNUSED_SYMBOL,
            MARGINS, LEFT, TOP, RIGHT, BOTTOM, START, END, HORIZONTAL, VERTICAL,
        ) else {
            if (maHor != null) maybeReport(
                holder, isOnTheFly, tmp, " is overridden by '${MARGINS[HORIZONTAL]}'", LIKE_UNUSED_SYMBOL,
                MARGINS, LEFT, TOP, START, END,
            )
            if (maVer != null) maybeReport(
                holder, isOnTheFly, tmp, " is overridden by '${MARGINS[VERTICAL]}'", LIKE_UNUSED_SYMBOL,
                MARGINS, TOP, BOTTOM,
            )
        }

        tag.subTags.forEach {
            checkLayoutTag(holder, isOnTheFly, it, tmp)
        }

        /*
        android-31/data/res/values/attrs.xml:
        <!-- ... This value will take
             precedence over any of the edge-specific values (paddingLeft, paddingTop,
             paddingRight, paddingBottom, paddingHorizontal and paddingVertical), but will
             not override paddingStart or paddingEnd, if set. ... -->
        <attr name="padding" format="dimension" />
        <!-- Sets the padding, in pixels, of the left and right edges; see
             {@link android.R.attr#padding}. This value will take precedence over
             paddingLeft and paddingRight, but not paddingStart or paddingEnd (if set). -->
        <attr name="paddingHorizontal" format="dimension" />
        <!-- Sets the padding, in pixels, of the top and bottom edges; see
             {@link android.R.attr#padding}. This value will take precedence over
             paddingTop and paddingBottom, if set. -->
        <attr name="paddingVertical" format="dimension" />
        ...
        <!--  ...  If both layout_margin and any of layout_marginLeft,
              layout_marginRight, layout_marginStart, layout_marginEnd,
              layout_marginTop, and layout_marginBottom are
              also specified, the layout_margin value will take precedence over the
              edge-specific values. ... -->
        <attr name="layout_margin" format="dimension"  />
        ...
        <!--  ... Specifying layout_marginHorizontal is equivalent to specifying
              layout_marginLeft and layout_marginRight.
              If both layout_marginHorizontal and either/both of layout_marginLeft
              and layout_marginRight are also specified, the layout_marginHorizontal
              value will take precedence over the
              edge-specific values. Also, layout_margin will always take precedence over
              any of these values, including layout_marginHorizontal. ...-->
        <attr name="layout_marginHorizontal" format="dimension"  />
        <!--  ... Specifying layout_marginVertical is equivalent to specifying
              layout_marginTop and layout_marginBottom with that same value.
              If both layout_marginVertical and either/both of layout_marginTop and
              layout_marginBottom are also specified, the layout_marginVertical value
              will take precedence over the edge-specific values.
              Also, layout_margin will always take precedence over
              any of these values, including layout_marginVertical. ...-->
        <attr name="layout_marginVertical" format="dimension"  />
         */

    }

    private fun maybeReport(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        tmp: Array<XmlAttribute?>,
        msg: String, highlight: ProblemHighlightType,
        names: Array<String>,
        which1: Int, which2: Int, which3: Int = -1, which4: Int = -1,
        which5: Int = -1, which6: Int = -1, which7: Int = -1, which8: Int = -1,
    ) {
        val fix = removeAttrFix.takeIf { isOnTheFly || highlight == LIKE_UNUSED_SYMBOL }
        tmp[which1]?.let { holder.registerProblem(it, names[which1] + msg, highlight, fix) }
        tmp[which2]?.let { holder.registerProblem(it, names[which2] + msg, highlight, fix) }
        tmp.getOrNull(which3)?.let { holder.registerProblem(it, names[which3] + msg, highlight, fix) }
        tmp.getOrNull(which4)?.let { holder.registerProblem(it, names[which4] + msg, highlight, fix) }
        tmp.getOrNull(which5)?.let { holder.registerProblem(it, names[which5] + msg, highlight, fix) }
        tmp.getOrNull(which6)?.let { holder.registerProblem(it, names[which6] + msg, highlight, fix) }
        tmp.getOrNull(which7)?.let { holder.registerProblem(it, names[which7] + msg, highlight, fix) }
        tmp.getOrNull(which8)?.let { holder.registerProblem(it, names[which8] + msg, highlight, fix) }
    }

}
