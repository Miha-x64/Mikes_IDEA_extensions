package net.aquadc.mike.plugin

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.*
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.uast.UastVisitorAdapter
import com.siyeh.ig.PsiReplacementUtil
import com.siyeh.ig.callMatcher.CallMatcher
import it.unimi.dsi.fastutil.ints.IntArrayList
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import kotlin.math.min


abstract class UastInspection : LocalInspectionTool() {

    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        UastVisitorAdapter(uVisitor(holder, isOnTheFly), true)

    abstract fun uVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): AbstractUastNonRecursiveVisitor

}

abstract class FunctionCallVisitor(
    private val assignment: Boolean = false,
    private val comparison: Boolean = false,
) : AbstractUastNonRecursiveVisitor() {
    final override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
        if (node.sourcePsi?.language === KotlinLanguage.INSTANCE) return true
        node.selector.accept(this)
        return true
    }
    final override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) =
        true
    final override fun visitCallExpression(node: UCallExpression): Boolean {
        node.sourcePsi?.let { src ->
            if (src is PsiExpressionStatement) // some wisdom from IDEA sources, not sure whether it is useful
                return true

            val method = if (node.kind == UastCallKind.CONSTRUCTOR_CALL) "<init>" else node.methodName
            val cls = node.resolvedClassFqn
            val args = if (node.valueArgumentCount == 0) emptyList() else object : AbstractList<UExpression>() {
                override val size: Int get() = node.valueArgumentCount
                override fun get(index: Int): UExpression = node.valueArguments[index]
            }
            if (method != null && cls != null) {
                return visitCallExpr(node, src, node.kind, null, cls, node.receiver, method, args)
            }
        }
        return true
    }

    final override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
        val src = node.sourcePsi ?: return true
        val op = node.operatorIdentifier?.name
        if (assignment && op == "=" && false) {
            //             DISABLED ^^^^^^^^ seems to be already handled by UAST!
            (node.leftOperand as? UReferenceExpression)?.let { leftRef ->
                val name = leftRef.resolvedName
                val cls = leftRef.resolvedClassFqn
                if (name != null && cls != null) {
                    val receiver = when (leftRef) {
                        is UCallExpression -> leftRef.receiver
                        is UQualifiedReferenceExpression -> leftRef.receiver
                        else -> null
                    }
                    return visitCallExpr(
                        node, src, UastCallKind.METHOD_CALL, op,
                        cls, receiver, name, listOf(node.rightOperand),
                    )
                }
            }
        } else if (comparison && (op == "<" || op == "<=" || op == ">" || op == ">=")) {
            (node.leftOperand as? UReferenceExpression)?.let { leftRef ->
                val cls = leftRef.resolvedClassFqn
                if (cls != null) {
                    return visitCallExpr(
                        node, src, UastCallKind.METHOD_CALL, op,
                        cls, node.leftOperand, "compareTo", listOf(node.rightOperand),
                    )
                }
            }
        }
        return true
    }
    abstract fun visitCallExpr(
        node: UExpression, src: PsiElement, kind: UastCallKind, operator: String?,
        declaringClassFqn: String, receiver: UExpression?, methodName: String, valueArguments: List<UExpression>,
    ): Boolean
}

val UResolvable.resolvedClassFqn: String?
    get() = (resolve() as? PsiMember)?.containingClass?.qualifiedName

fun CallMatcher.test(expr: PsiElement): Boolean = when (expr) {
    is PsiMethodCallExpression -> test(expr)
    is KtExpression -> test(expr)
    else -> false
}

fun CallMatcher.test(expr: KtExpression): Boolean {
    val (refExpr, args) = if (expr is KtCallExpression) {
        val refExpr = expr.referenceExpression() ?: return false

        val name = refExpr.referencedName ?: return false
        if (names().noneMatch { it == name }) return false

        refExpr to expr.valueArguments.size
    } else if (expr is KtBinaryExpression) {
        val op = expr.operationToken
        if (op == KtTokens.EQ) {
            val refExpr = expr.leftRef ?: return false
            refExpr to 1
        } else {
            return false
        }
    } else {
        return false
    }

    /* impossible to check this from outside class, skip it:
    if (myParameters != null && myParameters!!.size > 0) {
        if (args.size < myParameters!!.size) return false
    }*/
    val method = (refExpr as? KtNameReferenceExpression)?.references
        ?.firstNotNullOfOrNull(PsiReference::resolve) as? PsiMethod ?: return false

    return method.isCorrectArgCount(args) && methodMatches(method)
}

val KtBinaryExpression.leftRef: KtReferenceExpression?
    get() = (left as? KtDotQualifiedExpression)?.selectorExpression?.referenceExpression()
        ?: left as? KtReferenceExpression

val KtReferenceExpression.referencedName: String?
    get() = (this as? KtNameReferenceExpression)?.getReferencedName()

// this is more correct than original CallMatcher check
private fun PsiMethod.isCorrectArgCount(args: Int): Boolean {
    val params = parameterList.parametersCount
    return if (isVarArgs) args >= (params-1) else args == params
}

abstract class NamedLocalQuickFix(
    name: String
) : LocalQuickFix {
    private val _name = name
    final override fun getFamilyName(): String = _name
}

fun ProblemsHolder.register(srcPsi: PsiElement, text: String, fix: LocalQuickFix? = null): Unit =
    if (fix == null) registerProblem(srcPsi, text) else registerProblem(srcPsi, text, fix)

class NamedReplacementFix(
    name: String,
    private val expression: String,
    private val kotlinExpression: String = expression,
    psi: PsiElement? = null,
    private val shorten: Boolean = true,
) : NamedLocalQuickFix(name) {
    private val psiRef = psi?.let {
        val file = it.containingFile
        SmartPointerManager.getInstance(file?.project ?: it.project).createSmartPsiElementPointer<PsiElement>(psi, file)
    }
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val psi = psiRef?.let { it.element ?: return } ?: descriptor.psiElement
        if (FileModificationService.getInstance().preparePsiElementForWrite(psi)) {
            if (shorten) replaceAndShorten(project, psi) else replace(project, psi)
        }
    }

    private fun replaceAndShorten(project: Project, psi: PsiElement) {
        if (psi.language === JavaLanguage.INSTANCE) when (psi) {
            is PsiExpression -> PsiReplacementUtil.replaceExpressionAndShorten(psi, expression)
            is PsiStatement -> PsiReplacementUtil.replaceStatementAndShortenClassNames(psi, expression)
            is PsiAnnotation -> CodeStyleManager.getInstance(project).reformat(
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(
                    replaceJAnnotation(project, psi)
                )
            )
        } else if (psi.language === KotlinLanguage.INSTANCE)
            CodeStyleManager.getInstance(psi.project).reformat(ShortenReferences.DEFAULT.process(replaceKt(psi)))
    }

    private fun replace(project: Project, psi: PsiElement) {
        if (psi.language === JavaLanguage.INSTANCE) when (psi) {
            is PsiExpression -> PsiReplacementUtil.replaceExpression(psi, expression)
            is PsiStatement -> PsiReplacementUtil.replaceStatement(psi, expression)
            is PsiAnnotation -> CodeStyleManager.getInstance(project).reformat(replaceJAnnotation(project, psi))
        } else if (psi.language === KotlinLanguage.INSTANCE)
            CodeStyleManager.getInstance(psi.project).reformat(replaceKt(psi))
    }

    private fun replaceJAnnotation(project: Project, psi: PsiAnnotation) =
        psi.replace(JavaPsiFacade.getElementFactory(project).createAnnotationFromText(expression, psi.parent))

    private fun replaceKt(psi: PsiElement) = psi.replace(
        if (psi is KtAnnotationEntry) KtPsiFactory(psi).createAnnotationEntry(kotlinExpression)
        else KtPsiFactory(psi).createExpression(kotlinExpression)
    ) as KtElement
}

abstract class KtFunctionObjectVisitor : KtVisitorVoid() {

    final override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (function.isLocal) visitFunctionObject(function) // btw, this can be named, too
    }
    final override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
        super.visitCallableReferenceExpression(expression)
        visitFunctionObject(expression)
    }
    final override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
        super.visitLambdaExpression(lambdaExpression)
        visitFunctionObject(lambdaExpression)
    }

    protected abstract fun visitFunctionObject(expression: KtExpression)

}

val KtFunction.isInline: Boolean
    get() = modifierList?.hasModifier(KtTokens.INLINE_KEYWORD) == true

val KtParameter.isNoinline: Boolean
    get() = modifierList?.hasModifier(KtTokens.NOINLINE_KEYWORD) == true

val KtExpression.containingFunction: KtFunction?
    get() {
        var context = context
        while (context != null) {
            if (context is KtFunction) {
                return context
            }
            context = context.context
        }

        return null
    }

fun noinlineMessage(expression: KtExpression): String? {
    return when (val parent = expression.context) {
        is KtProperty ->
            "This function cannot be inlined as it is assigned to variable ${parent.name}"
        is KtValueArgument -> {
            val argList = parent.parent as? KtValueArgumentList
            (argList?.parent as? KtCallExpression ?: parent.parent as? KtCallExpression)
                ?.let { (it.calleeExpression as? KtReferenceExpression) }
                ?.mainReference?.resolve()?.let { it as? KtNamedFunction }
                ?.let { callee ->
                    if (callee.isInline) {
                        val param = parent.getArgumentName()?.text
                            ?.let { parameterName -> callee.valueParameters.first { it.name == parameterName } }
                            ?: callee.valueParameters.getOrNull(
                                    argList?.arguments?.indexOf(parent)
                                        ?: callee.valueParameters.lastIndex // for `func(...) { }` we won't get KtValueArgumentList
                            )

                        if (param?.isNoinline == true) {
                            noinlineParamOfInline(callee, param)
                        } else null
                    } else {
                        paramOfNoinline(callee)
                    }
                }
        }
        is KtReturnExpression ->
            "This function cannot be inlined as it is returned from a function"
        is KtBlockExpression ->
            "This function cannot be inlined" // looks like a named function
        is KtBinaryExpression -> {
            // left expression is a receiver and cannot be inlined
            if (expression == parent.right) {
                parent.operationReference.mainReference.resolve()?.let { it as? KtNamedFunction }
                    ?.let { callee ->
                        if (callee.isInline) {
                            if (callee.valueParameters[0].isNoinline) {
                                noinlineParamOfInline(callee, callee.valueParameters[0])
                            } else null
                        } else {
                            paramOfNoinline(callee)
                        }
                    }
            } else null
        }
        else -> null
    }
}

private fun paramOfNoinline(callee: KtNamedFunction): String =
    "This function cannot be inlined as it is passed to a non-inline function ${callee.name}"
private fun noinlineParamOfInline(callee: KtNamedFunction, param: KtParameter): String =
    "This function cannot be inlined as it is passed to inline function ${callee.name} as a value for noinline parameter ${param.name}"

inline fun <T, R : Comparable<R>> Array<out T>.maxByIf(selector: (T) -> R, predicate: (R) -> Boolean): T? {
    var i = 0
    var hasFirst = false
    var maxValue: T? = null
    var maxSelected: R? = null
    while (i < size) {
        val value = this[i++]
        val selected = selector(value)
        if (predicate(selected) && (!hasFirst || selected > (maxSelected as R))) {
            hasFirst = true
            maxValue = value
            maxSelected = selected
        }
    }
    return maxValue
}

inline fun <T> Array<out T>.miserlyFilter(predicate: (T) -> Boolean): List<T> {
    val iof = indexOfFirst(predicate)
    if (iof < 0) return emptyList()
    var i = iof + 1
    while (i < size) {
        val el = this[i++]
        if (predicate(el)) {
            val out = alOf(iof, i-1)
            while (i < size) {
                val el = this[i++]
                if (predicate(el)) out.add(el)
            }
            return out
        }
    }
    return listOf(this[iof])
}
@PublishedApi internal fun <T> Array<out T>.alOf(first: Int, second: Int): ArrayList<T> =
    ArrayList<T>(min(1 + size - second /* found + left */, 10)).also {
        it.add(this[first])
        it.add(this[second])
    }
inline fun <T> List<T>.miserlyFilter(predicate: (T) -> Boolean): List<T> {
    val iof = indexOfFirst(predicate)
    if (iof < 0) return emptyList()
    var i = iof + 1
    while (i < size) {
        val el = this[i++]
        if (predicate(el)) {
            val out = alOf(iof, i-1)
            while (i < size) {
                val el = this[i++]
                if (predicate(el)) out.add(el)
            }
            return out
        }
    }
    return listOf(this[iof])
}
@PublishedApi internal fun <T> List<T>.alOf(first: Int, second: Int): ArrayList<T> =
    ArrayList<T>(min(1 + size - second /* found + left */, 10)).also {
        it.add(this[first])
        it.add(this[second])
    }
inline fun <T, reified R> Array<out T>.miserlyMap(transform: (T) -> R): Array<R> =
    Array(size) { transform(this[it]) }
inline fun <reified R : Any> IntArray.miserlyMapNullize(transform: (Int) -> R?): Array<R>? =
    if (isEmpty()) null else Array(size) { transform(this[it]) ?: return null }

operator fun <T> ((T) -> Boolean).not(): (T) -> Boolean = { !this(it) }

fun PsiElement.getParentUntil(c1: Class<out PsiElement>, c2: Class<out PsiElement>): PsiElement? {
    var element = this
    while (true) {
        element = (element.parent ?: return null).also {
            if (c1.isInstance(it) || c2.isInstance(it)) {
                return element
            }
        }
    }
}

inline fun IntArrayList.indexOfFirst(predicate: (Int) -> Boolean): Int {
    for (i in 0 until size)
        if (predicate(getInt(i)))
            return i
    return -1
}

@Suppress("NOTHING_TO_INLINE")
inline fun Boolean.toInt(): Int =
    if (this) 1 else 0

internal val PsiType_INT: PsiPrimitiveType by lazy {
    try {
        // still unavailable in 2022.1.4 (last AS release)
        Class.forName("com.intellij.psi.PsiTypes").getMethod("intType").invoke(null) as PsiPrimitiveType
    } catch (e: ReflectiveOperationException) {
        PsiType.INT // deprecated for removal since IU-231.6471.13
    }
}
