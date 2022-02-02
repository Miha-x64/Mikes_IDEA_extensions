package net.aquadc.mike.plugin.android

import com.android.utils.SparseArray
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl.parseStringCharacters
import com.intellij.psi.util.PsiLiteralUtil.*
import com.intellij.psi.util.parentOfType
import com.intellij.util.SmartList
import it.unimi.dsi.fastutil.bytes.ByteArrays
import net.aquadc.mike.plugin.FunctionCallVisitor
import net.aquadc.mike.plugin.NamedReplacementFix
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.resolvedClassFqn
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.structuralsearch.visitor.KotlinRecursiveElementWalkingVisitor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getAsJavaPsiElement
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.geom.RoundRectangle2D
import java.util.*
import javax.swing.Icon
import org.jetbrains.kotlin.KtNodeTypes.INTEGER_CONSTANT as KT_INTEGER_CONSTANT

/**
 * @author Mike Gorünóv
 */
class ConstantParseColor : UastInspection(), CleanupLocalInspectionTool {
    override fun uVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): AbstractUastNonRecursiveVisitor = object : FunctionCallVisitor() {
        override fun visitCallExpr(node: UCallExpression): Boolean =
            if (node.methodName == "parseColor" && node.resolvedClassFqn == "android.graphics.Color") {
                node.sourcePsi?.let { src ->
                    node.valueArguments.firstOrNull()?.sourcePsi?.let { argSrc ->
                        report(holder, src.parent as? KtDotQualifiedExpression ?: src, argSrc)
                    }
                }
                true
            } else false
        private fun report(holder: ProblemsHolder, call: PsiElement, argSrc: PsiElement) {
            val colorInt = argSrc.toColorInt()
            val literal = argSrc.text
            if (colorInt == 1) {
                holder.registerProblem(argSrc, "$literal is not a valid color", ProblemHighlightType.GENERIC_ERROR)
            } else if (colorInt != 2) { // 2 == non-constant expression, give up
                val hex = colorInt.toPaddedUpperHex(8, HEX_LITERAL_PREFIX, ByteArrays.EMPTY_ARRAY)
                val const = colorConstantValues.indexOf(colorInt).let { if (it < 0) null else colorConstantNames[it] }
                val replacement = if (const == null) "$hex literal" else "Color.$const constant"
                holder.registerProblem(
                    argSrc,
                    "parseColor($literal) should be replaced with $replacement",
                    const?.let {
                        NamedReplacementFix("Replace with Color constant", "android.graphics.Color.$it", psi = call)
                    },
                    NamedReplacementFix("Replace with int literal", hex, "$hex.toInt()", call),
                )
            }
        }
    }

    private companion object {
        private val colorConstantNames = arrayOf(
            "BLACK", "DKGRAY", "GRAY", "LTGRAY",
            "WHITE", "RED", "GREEN", "BLUE",
            "YELLOW", "CYAN", "MAGENTA", "TRANSPARENT",
        )
        private val colorConstantValues = intArrayOf(
            0xFF000000.toInt(), 0xFF444444.toInt(), 0xFF888888.toInt(), 0xFFCCCCCC.toInt(),
            0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
            0xFFFFFF00.toInt(), 0xFF00FFFF.toInt(), 0xFFFF00FF.toInt(), 0x00000000,
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
    private val awtColors = arrayOf(
        Color.WHITE, Color.LIGHT_GRAY, Color.GRAY, Color.DARK_GRAY, Color.BLACK, Color.RED, Color.PINK, Color.ORANGE,
        Color.YELLOW, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.BLUE,
    ).also { it.sortBy(Color::getRGB) }

    private fun Int.toAwtColor(): Color =
        (awtColors as Array<out Any>).binarySearch(this, { c, ci -> (c as Color).rgb.compareTo(ci as Int) })
            .let { if (it >= 0) awtColors[it] else Color(this, true) }

    // synchronized access only
    private val iconCache = SparseArray<Icon>()

    // assume single-thread rendering
    private val sharedClip = RoundRectangle2D.Float()

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        var colorInt = element.toColorInt() // TODO preview Color.(a)rgb()
        if (colorInt == 2) colorInt = element.asReferenceToColorInt()
        if (colorInt == 1 || colorInt == 2 || !colorInt.worthPreviewing) return null // skip almost invisible colors
        val icon = synchronized(iconCache) {
            iconCache[colorInt] ?: ColorIcon(sharedClip, colorInt.toAwtColor()).also { iconCache.put(colorInt, it) }
        }
        return LineMarkerInfo(
            element.firstLeaf, element.textRange,
            icon, null, null, GutterIconRenderer.Alignment.LEFT
        ) { colorInt.toPaddedUpperHex(colorInt.opaque6translucent8, COLOR_PREFIX_HASH, COLOR_ACCESSIBILITY_POSTFIX) }
    }

    private val PsiElement.firstLeaf get(): PsiElement = firstChild?.firstLeaf ?: this
    private inline val Int.worthPreviewing get() = (this ushr 24) > 0x27 || this == 0

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

/**
 * @author Mike Gorünóv
 */
class ColorIntLiteralFolding : FoldingBuilderEx() {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        if (quick || (root !is PsiJavaFile && root !is KtFile)) return FoldingDescriptor.EMPTY
        val regions = SmartList<FoldingDescriptor>()
        when (root) {
            is PsiJavaFile -> root.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitLiteralExpression(expression: PsiLiteralExpression) {
                    if (expression.type == PsiType.INT) expression.tryFoldTo(regions)
                }
            })
            is KtFile -> root.accept(object : KotlinRecursiveElementWalkingVisitor() {
                override fun visitConstantExpression(expression: KtConstantExpression) {
                    if (expression.node.elementType == KT_INTEGER_CONSTANT) expression.tryFoldTo(regions)
                }
            })
        }
        return if (regions.isEmpty()) FoldingDescriptor.EMPTY else regions.toTypedArray()
    }
    private fun PsiElement.tryFoldTo(regions: SmartList<FoldingDescriptor>) {
        val colorInt = text.toHexIntLiteralValue("0x", 8, '_')
        if (colorInt != 1) {
            val node = (parentOfType<KtDotQualifiedExpression>()?.takeIf {
                (it.selectorExpression as? KtCallExpression)?.calleeExpression?.textMatches("toInt") == true
            } ?: this).node
            regions.add(
                FoldingDescriptor(
                    node, node.textRange, null,
                    colorInt.toPaddedUpperHex(colorInt.opaque6translucent8, COLOR_PREFIX_HASH, ByteArrays.EMPTY_ARRAY)
                )
            )
        }
    }

    override fun getPlaceholderText(node: ASTNode): String? = null

    override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}

/**
 * @author Mike Gorünóv
 */
class CopyPasteColor : CopyPastePreProcessor {

    override fun preprocessOnCopy(
        file: PsiFile?, startOffsets: IntArray?, endOffsets: IntArray?, text: String?
    ): String? =
        null

    override fun preprocessOnPaste(
        project: Project?, file: PsiFile, editor: Editor?, text: String, rawText: RawText?): String {
        if (file.language.let { it != JavaLanguage.INSTANCE && it != KotlinLanguage.INSTANCE }) return text
        if (text.length !in 7..50) return text // " background:  rgba( 111 , 111 , 255 , 0.12345 ) ; "

        val data = StringBuilder(text)
        // trim
        while (data.isNotEmpty() && data.first().let { it.isWhitespace() || it == ':' }) data.deleteCharAt(0)
        while (data.isNotEmpty() && data.last().let { it.isWhitespace() || it == ';' }) data.deleteCharAt(data.lastIndex)
        val separatorIndex = data.indexOf(':')
        val identifier = if (separatorIndex < 0) null else
            StringBuilder().append(data, 0, separatorIndex).also { sb ->
                var iof: Int
                while (sb.indexOf('-').also { iof = it } >= 0)
                    sb.deleteCharAt(iof).setCharAt(iof, sb[iof].toUpperCase())

                data.delete(0, separatorIndex + 1)
                while (data.isNotEmpty() && data.first().isWhitespace()) data.deleteCharAt(0)
            }

        // reference: https://www.w3schools.com/cssref/css_colors_legal.asp
        val color = when {
            data.isEmpty() -> 1
            data[0] == '#' && data.length == 7 -> data.parseColorString()
            data[0] == '#' && data.length == 9 -> {
                // #RRGGBBAA to 0xAARRGGBB
                val aHi = Character.digit(data[7], 16)
                val aLo = Character.digit(data[8], 16)
                data.delete(7, 9)
                val col = data.parseColorString()
                if (col == 1 || aHi < 0 || aLo < 0) 1 else (col and 0xFFFFFF) or (aHi shl 28) or (aLo shl 24)

            }
            data.endsWith(')') -> {
                data.deleteCharAt(data.lastIndex)
                if (data.startsWith("rgb(")) data.delete(0, 4).parseChannels()
                else if (data.startsWith("rgba(")) data.delete(0, 5).parseChannels()
                else 1
            }
            // don't mind HSL(A) at this point
            else -> 1
        }
        return if (color == 1) text else {
            val hex = color.toPaddedUpperHex(8, HEX_LITERAL_PREFIX, ByteArrays.EMPTY_ARRAY)
            val kotlin = file.language == KotlinLanguage.INSTANCE
            identifier?.insert(0, if (kotlin) "private const val " else "private static final int ")
                ?.append(" = ")?.append(hex)?.append(if (kotlin) ".toInt()" else ";")?.toString()
                ?: if (kotlin) data.clear().append(hex).append(".toInt()").toString() else hex
        }
    }

    private fun CharSequence.parseChannels(): Int {
        // sorry, now I will allocate
        val channels = split(',')
        var color = when (channels.size) {
            3 -> 0xFF
            4 -> channels[3].toFloatOrNull()?.let { (it * 255).toInt() } ?: return 1
            else -> return 1
        }
        for (i in 0..2) {
            val trimmed = channels[i].trim()
            color = (color shl 8) or
                    if (trimmed.endsWith('%'))
                        trimmed.substring(0, trimmed.lastIndex).toIntOrNull()?.let { it * 255 / 100 } ?: return 1
                    else
                        trimmed.toIntOrNull() ?: return 1
        }
        return color
    }

    override fun requiresAllDocumentsToBeCommitted(editor: Editor, project: Project): Boolean =
        false
}

// shared

private val Int.opaque6translucent8 get() = if (isOpaque) 6 else 8
private val Int.isOpaque get() = (this ushr 24) == 0xFF

private fun PsiElement.toColorInt(): Int = when {
    textLength !in 3..17 -> 2 // red..0xF_F_F_F_F_F_F_F
    this is PsiLiteralExpression -> when (type) {
        PsiType.INT -> text.toHexIntLiteralValue("0x", 8, '_')
        !is PsiPrimitiveType -> stringValue().parseColorString()
        else -> 2
    }
    this is KtLiteralStringTemplateEntry -> // UAST.sourcePsi peeks into string template, if I understood correctly
        text.parseColorString()
    this is KtConstantExpression && node.elementType == KT_INTEGER_CONSTANT ->
        text.toHexIntLiteralValue("0x", 8, '_')
    else -> 2
}
private fun PsiElement.asReferenceToColorInt(): Int = when (this) {
    is PsiReferenceExpression -> resolve()
    is KtReferenceExpression -> toUElementOfType<UReferenceExpression>()?.resolve()
    else -> null
}?.toUElementOfType<UField>()?.getAsJavaPsiElement(PsiField::class.java)
    ?.takeIf { AnnotationUtil.findAnnotation(it, "androidx.annotation.ColorInt") != null }
    ?.computeConstantValue() as? Int ?: 2

private const val Char_UNASSIGNED: Char = 0x2FEF.toChar()
private fun CharSequence.toHexIntLiteralValue(prefix: String, targetLen: Int, skip: Char): Int {
    if (!startsWith(prefix)) return 1
    val payloadLen = this.length - prefix.length
    if (payloadLen < targetLen || skip != Char_UNASSIGNED && payloadLen > (2*targetLen-1))
        return 1 // !in FFFFFFFF..F_F_F_F_F_F_F_F, for example
    var out = 0
    var digits = 0
    for (i in prefix.length until this.length) { // skip "0x" or "#"
        val char = this[i]
        if (char == skip) continue
        if (digits++ == targetLen) return 1
        val digit = Character.digit(char, 16)
        if (digit < 0) return 1
        out = (out shl 4) or digit
    }
    return if (digits == targetLen) out else 1
}

private fun PsiLiteralExpression.stringValue(): String? =
    getStringLiteralContent(this)?.let { literal ->
        literal.takeIf { '\\' !in it }
        // com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl.internedParseStringCharacters clone:
            ?: StringBuilder(literal.length).takeIf { out -> parseStringCharacters(literal, out, null) }?.toString()
    }

private val uppercaseHex = "0123456789ABCDEF".toByteArray()
private val HEX_LITERAL_PREFIX = "0x".toByteArray()
private val COLOR_PREFIX_HASH = "#".toByteArray()
private val COLOR_ACCESSIBILITY_POSTFIX = " color".toByteArray()
private fun Int.toPaddedUpperHex(digits: Int, prefix: ByteArray, postfix: ByteArray): String {
    val preLen = prefix.size
    val out = ByteArray(preLen + digits + postfix.size)
    System.arraycopy(prefix, 0, out, 0, preLen)
    var ci = this
    for (i in (preLen + digits - 1) downTo preLen) {
        out[i] = uppercaseHex[ci and 0xF]
        ci = ci ushr 4
    }
    System.arraycopy(postfix, 0, out, out.size - postfix.size, postfix.size)
    return String(out)
}

private val colorNames = arrayOf(
    "black", "darkgray", "gray", "lightgray",
    "white", "red", "green", "blue",
    "yellow", "cyan", "magenta", "aqua",
    "fuchsia", "darkgrey", "grey", "lightgrey",
    "lime", "maroon", "navy", "olive",
    "purple", "silver", "teal",
)
private val colorValues = intArrayOf(
    0xFF000000.toInt(), 0xFF444444.toInt(), 0xFF888888.toInt(), 0xFFCCCCCC.toInt(),
    0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
    0xFFFFFF00.toInt(), 0xFFFFFF00.toInt(), 0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(),
    0xFFFF00FF.toInt(), 0xFF444444.toInt(), 0xFF888888.toInt(), 0xFFCCCCCC.toInt(),
    0xFF00FF00.toInt(), 0xFF800000.toInt(), 0xFF000080.toInt(), 0xFF808000.toInt(),
    0xFF800080.toInt(), 0xFFC0C0C0.toInt(), 0xFF008080.toInt(),
)
private fun CharSequence?.parseColorString(): Int = // android.graphics.Color#parseColor clone
    if (isNullOrBlank())
        1
    else if (this[0] == '#')
        when (length) {
            7 -> toHexIntLiteralValue("#", 6, Char_UNASSIGNED).let { if (it == 1) 1 else (it or 0xFF000000.toInt()) }
            9 -> toHexIntLiteralValue("#", 8, Char_UNASSIGNED)
            else -> 1
        }
    else
        colorNames.indexOf(toString().toLowerCase(Locale.ROOT))
            .let { index -> if (index < 0) 1 else colorValues[index] }
