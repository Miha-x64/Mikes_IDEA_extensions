package net.aquadc.mike.plugin.android

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import net.aquadc.mike.plugin.FunctionCallVisitor
import net.aquadc.mike.plugin.NamedReplacementFix
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.resolvedClassFqn
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import java.util.Locale

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
}

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
