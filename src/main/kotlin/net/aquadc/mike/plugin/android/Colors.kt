package net.aquadc.mike.plugin.android

import com.android.utils.SparseArray
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiType
import com.intellij.psi.impl.ResolveScopeManager.getElementResolveScope
import net.aquadc.mike.plugin.FunctionCallVisitor
import net.aquadc.mike.plugin.NamedReplacementFix
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.resolvedClassFqn
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.parsing.parseNumericLiteral
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.geom.RoundRectangle2D
import java.util.*
import javax.swing.Icon

/**
 * @author Mike Gorünóv
 */
class ConstantParseColor : UastInspection() {
    override fun uVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): AbstractUastNonRecursiveVisitor = object : FunctionCallVisitor() {
        override fun visitCallExpr(node: UCallExpression): Boolean =
            if (node.methodName == "parseColor" && node.resolvedClassFqn == "android.graphics.Color") {
                node.sourcePsi?.let { src ->
                    val arg = node.valueArguments.firstOrNull()
                    arg?.sourcePsi?.let { argSrc ->
                        (arg.evaluate() as? String)?.let { col ->
                            report(holder, src.parent as? KtDotQualifiedExpression ?: src, argSrc, col)
                        }
                    }
                }
                true
            } else false
        private fun report(holder: ProblemsHolder, call: PsiElement, arg: PsiElement, colorStr: String) {
            val colorInt = android_graphics_Color_parseColor_orNull(colorStr)
            if (colorInt == null) {
                holder.registerProblem(arg, "$colorStr is not a valid color", ProblemHighlightType.GENERIC_ERROR)
            } else {
                val hex = "0x" + (colorInt.toLong() and 0xFFFFFFFFL).toString(16).toUpperCaseAsciiOnly()
                holder.registerProblem(
                    arg, "parseColor(<constant>) should be replaced with an int constant",
                    colorConstantNames.getOrNull(colorConstantValues.indexOf(colorInt))?.let {
                        NamedReplacementFix(
                            "android.graphics.Color.$it", name = "Replace with Color.$it constant", psi = call
                        )
                    },
                    NamedReplacementFix(hex, kotlinExpression = "$hex.toInt()", psi = call),
                )
            }
        }
    }
    companion object {
        private val colorConstantNames = arrayOf(
            "BLACK", "DKGRAY", "GRAY", "LTGRAY", "WHITE",
            "RED", "GREEN", "BLUE", "YELLOW", "CYAN",
            "MAGENTA", "TRANSPARENT",
        )
        private val colorConstantValues = intArrayOf(
            0xFF000000.toInt(), 0xFF444444.toInt(), 0xFF888888.toInt(), 0xFFCCCCCC.toInt(), 0xFFFFFFFF.toInt(),
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFF00.toInt(), 0xFF00FFFF.toInt(),
            0xFFFF00FF.toInt(), 0,
        )
    }
}

/**
 * @author Mike Gorünóv
 */
class GutterColorPreview : LineMarkerProviderDescriptor() {
    override fun getName(): String =
        "Color preview"

    // never modified so multithreaded access is safe
    private val colors = arrayOf(
        Color.WHITE, Color.LIGHT_GRAY, Color.GRAY, Color.DARK_GRAY, Color.BLACK, Color.RED, Color.PINK, Color.ORANGE,
        Color.YELLOW, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.BLUE,
    ).also { it.sortBy(Color::getRGB) }

    private fun Int.asColor(): Color =
        (colors as Array<out Any>).binarySearch(this, { c, ci -> (c as Color).rgb.compareTo(ci as Int) })
            .let { if (it >= 0) colors[it] else Color(this, true) }

    // synchronized access only
    private val iconCache = SparseArray<Icon>()

    // assume single-thread rendering
    private val sharedClip = RoundRectangle2D.Float()

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? =
        element.takeIf { it.textRange.length in 3..11 }?.constantValue()?.let { // 3: red 10: 0xFFFFFFFF
            when (it) {
                is String -> android_graphics_Color_parseColor_orNull(it)
                is Int -> it
                is Long -> it.toInt() // size check guards against huge longs
                else -> null
            }
        }?.let { colorInt ->
            val icon = synchronized(iconCache) {
                iconCache[colorInt] ?: ColorIcon(sharedClip, colorInt.asColor()).also { iconCache.put(colorInt, it) }
            }
            LineMarkerInfo(
                element.firstChild ?: element, element.textRange,
                icon, null, null, GutterIconRenderer.Alignment.LEFT) {
                colorInt.toLong().toString(16) + " color"
            }
        }

    private fun PsiElement.constantValue(): Any? = when (this) {
        is PsiLiteralExpression ->
            takeIf {
                textContains('x') && type == PsiType.INT ||
                        type == containingFile.run { PsiType.getJavaLangString(manager, getElementResolveScope(this)) }
            }?.value
        is KtStringTemplateExpression ->
            analyze().get(BindingContext.COMPILE_TIME_VALUE, this)?.getValue(TypeUtils.NO_EXPECTED_TYPE)
        is KtConstantExpression ->
            takeIf { node.elementType == KtNodeTypes.INTEGER_CONSTANT && textContains('x') }?.let {
                parseNumericLiteral(text, node.elementType)
            }
        else -> null
    }

    private class ColorIcon(
        private val sharedBounds: RoundRectangle2D.Float,
        private val color: Color,
    ) : Icon {
        override fun getIconWidth(): Int = 12
        override fun getIconHeight(): Int = 12
        override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
            val oldClip = g.clip
            val oldColor = g.color
            g.clip = sharedBounds.apply { setRoundRect(x.toFloat(), y.toFloat(), 12f, 12f, 3f, 3f) }
            if (color.alpha < 250) g.drawChecker(x, y)
            g.color = color
            g.fillRect(x, y, 12, 12)
            g.clip = oldClip
            g.color = oldColor
        }

        private fun Graphics.drawChecker(x: Int, y: Int) {
            color = Color.WHITE
            fillRect(x, y, 6, 6)
            fillRect(x + 6, y + 6, 6, 6)
            color = Color.BLACK
            fillRect(x + 6, y, 6, 6)
            fillRect(x, y + 6, 6, 6)
        }
    }
}

// shared between inspection and gutter

private val colorNames = arrayOf(
    "black", "darkgray", "gray", "lightgray", "white",
    "red", "green", "blue", "yellow", "cyan",
    "magenta", "aqua", "fuchsia", "darkgrey", "grey",
    "lightgrey", "lime", "maroon", "navy", "olive",
    "purple", "silver", "teal",
)
private val colorValues = intArrayOf(
    0xFF000000.toInt(), 0xFF444444.toInt(), 0xFF888888.toInt(), 0xFFCCCCCC.toInt(), 0xFFFFFFFF.toInt(),
    0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFF00.toInt(), 0xFFFFFF00.toInt(),
    0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(), 0xFFFF00FF.toInt(), 0xFF444444.toInt(), 0xFF888888.toInt(),
    0xFFCCCCCC.toInt(), 0xFF00FF00.toInt(), 0xFF800000.toInt(), 0xFF000080.toInt(), 0xFF808000.toInt(),
    0xFF800080.toInt(), 0xFFC0C0C0.toInt(), 0xFF008080.toInt(),
)
private fun android_graphics_Color_parseColor_orNull(colorString: String): Int? =
    if (colorString.isEmpty()) null
    else if (colorString[0] == '#') {
        // Use a long to avoid rollovers on #ffXXXXXX
        val hex = colorString.substring(1)
        when (colorString.length) {
            7 -> hex.toLongOrNull(16)?.let { (it or 0x00000000ff000000).toInt() }
            9 -> hex.toLongOrNull(16)?.toInt()
            else -> null
        }
    } else {
        colorValues.getOrNull(colorNames.indexOf(colorString.toLowerCase(Locale.ROOT)))
    }
