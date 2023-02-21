@file:Suppress("UnstableApiUsage")
package net.aquadc.mike.plugin.interfaces

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.java.JavaBundle
import com.intellij.lang.jvm.types.JvmReferenceType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope.allScope
import com.intellij.ui.dsl.builder.panel
import net.aquadc.mike.plugin.maxByIf
import net.aquadc.mike.plugin.referencedName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.j2k.getContainingClass
import org.jetbrains.kotlin.j2k.getContainingMethod
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.toUElementOfType
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

class InterfaceHintsJava : InterfaceHintsP(settingsKey) {
    private companion object {
        private val settingsKey = SettingsKey<HintsSettings>("net.aquadc.mike.plugin.interfaces.hints.java")
    }
}
class InterfaceHintsKotlin : InterfaceHintsP(settingsKey) {
    private companion object {
        private val settingsKey = SettingsKey<HintsSettings>("net.aquadc.mike.plugin.interfaces.hints.kotlin")
    }
}

/**
 * @author Mike Gorünóv
 */
abstract class InterfaceHintsP(
    override val key: SettingsKey<HintsSettings>,
) : InlayHintsProvider<HintsSettings> {

    override fun getCollectorFor(
        file: PsiFile, editor: Editor, settings: HintsSettings, sink: InlayHintsSink
    ): InlayHintsCollector =
        InterfaceHintsCollector(editor, file, settings)

    override fun createSettings(): HintsSettings = HintsSettings()

    override val name: String get() = "Interface hints"
    override val group: InlayGroup
        get() = InlayGroup.TYPES_GROUP
    override val description: String?
        get() = "Hints related to interfaces"
    override val previewText: String?
        get() = null
    /*override*/ fun getCaseDescription(case: ImmediateConfigurable.Case): String? =
        when (case.id) {
            "upcast" -> "When expression is passed to function as a parameter, shows which interface it was upcast to."
            "overr" -> "For overridden methods, shows which interface it came from."
            else -> null
        }?.let {
            "<p>$it</p><br/>" +
                    "<p><small>The feature is provided by " +
                    "<a href=\"https://github.com/Miha-x64/Mikes_IDEA_extensions\">Mike's IDEA Extensions</a>." +
                    "</small></p>"
        }

    override fun createConfigurable(settings: HintsSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener): JComponent = panel {}
        override val mainCheckboxText: String
            get() = JavaBundle.message("settings.inlay.java.show.hints.for") // “Show hints for:”
        override val cases: List<ImmediateConfigurable.Case> get() = listOf(
            Case("Upcast to interface", settings::upcast),
            Case("Interface method override", settings::overr),
        )
        private fun Case(name: String, property: KMutableProperty0<Boolean>) =
            ImmediateConfigurable.Case(name, property.name, property)
    }
}

private class InterfaceHintsCollector(
    editor: Editor,
    private val file: PsiFile,
    private val settings: HintsSettings
) : FactoryInlayHintsCollector(editor) {
    override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean =
        if (file.project.let { DumbService.isDumb(it) || it.isDefault }) false
        else true.also { doCollect(element, sink) }

    private fun doCollect(element: PsiElement, sink: InlayHintsSink): Unit = when {
        settings.upcast && element is PsiMethodCallExpression -> {
            element.argumentList.expressions.takeIf(Array<*>::isNotEmpty)?.let { args ->
                element.methodExpression.reference?.resolve()?.functionParams?.let { params ->
                    args.forEachIndexed { idx, arg: PsiExpression ->
                        params.getOrNull(idx)?.second?.let { parameterType ->
                            element.methodExpression.referenceName?.let { methodName ->
                                arg.type?.let { argType ->
                                    visitParameter(methodName, parameterType, argType, arg.textRange.endOffset, sink)
                                }
                            }
                        }
                    }
                }
            }
        }
        settings.upcast && element is KtCallExpression -> {
            element.calleeExpression?.mainReference?.resolve()?.functionParams?.takeIf { it.isNotEmpty() }?.let { params ->
                element.referenceExpression()?.referencedName?.let { methodName ->
                    element.valueArgumentList?.arguments?.forEachIndexed { idx, arg ->
                        val argName = arg.getArgumentName()?.asName?.asString()
                        arg.getArgumentExpression()?.toUElementOfType<UExpression>()?.getExpressionType()?.let { argType ->
                            if (argName == null) {
                                params.getOrNull(idx)?.second?.let { pType ->
                                    visitParameter(methodName, pType, argType, arg.textRange.endOffset, sink)
                                }
                            } else {
                                params.firstOrNull { (name, _) -> name == argName }?.second?.let { pType ->
                                    visitParameter(methodName, pType, argType, arg.textRange.endOffset, sink)
                                }
                            }
                        }
                    }
                    element.lambdaArguments.singleOrNull()?.let { arg ->
                        arg.getArgumentExpression()?.toUElementOfType<UExpression>()?.getExpressionType()?.let { argType ->
                            params.last().second?.let { pType ->
                                visitParameter(methodName, pType, argType, arg.textRange.startOffset, sink, prefix = "")
                            }
                        }
                    }
                }
            }
        }
        settings.overr && element is PsiAnnotation -> {
            element.takeIf { it.nameReferenceElement?.qualifiedName == "java.lang.Override" }?.let { el ->
                el.getContainingMethod()?.findSuperMethods()?.firstOrNull()?.containingClass?.let { declaringT ->
                    visitOverride(el, declaringT, sink, element.textRange.endOffset, true)
                }
            }
        }
        settings.overr && element is KtNamedFunction -> {
            element.takeIf {
                it.receiverTypeReference == null && // ignore extensions
                        it.modifierList?.getModifier(KtTokens.OVERRIDE_KEYWORD) != null
            }?.nameIdentifier?.let { name ->
                val asJavaMethod = element.toUElementOfType<UMethod>()?.javaPsi
                (asJavaMethod?.findSuperMethods()?.firstOrNull()?.containingClass)?.let { declaringT ->
                    visitOverride(asJavaMethod, declaringT, sink, name.textRange.startOffset, false)
                }
            }
        }
        else -> null
    } ?: Unit

    private fun visitOverride(
        javaElement: PsiElement, declaringType: PsiClass, sink: InlayHintsSink, offset: Int, java: Boolean
    ) {
        if (!declaringType.isInterface) return // such a hint is useless in single class inheritance model
        val containingClass = javaElement.getContainingClass() ?: return
        containingClass.mainInterface?.let { main ->
            if (main == declaringType || main.isInheritor(declaringType, true)) return
        }
        val className = declaringType.typeName ?: return
        sink.addInlineElement(offset, false, hint(if (java) "from $className" else "$className."), false)
    }

    private val PsiElement.functionParams
        get() =
            (this as? PsiMethod)?.parameters?.map { null to (it.type as? JvmReferenceType)?.resolve() as? PsiClass }
                ?: (this as? KtCallableDeclaration)?.valueParameters?.map { param ->
                    param.name to
                        (param.toUElementOfType<UParameter>()?.type as? JvmReferenceType)?.resolve() as? PsiClass
                }

    private fun visitParameter(
        methodName: String, parameterType: PsiElement, argumentType: PsiType, offset: Int, sink: InlayHintsSink,
        prefix: String = "as ",
    ) {
        if (parameterType is PsiClass) {
            if (!parameterType.isInterface) return
            val argClass = when (argumentType) {
                UastErrorType, is PsiCapturedWildcardType -> null
                is PsiClassType -> argumentType
                is PsiPrimitiveType -> argumentType.getBoxedType(parameterType.manager, allScope(parameterType.getProject()))
                is PsiMethodReferenceType, is PsiLambdaExpressionType -> null
                else -> Logger.getInstance(InterfaceHintsCollector::class.java)
                    .error("arg type is ${argumentType::class.java} : $argumentType")
                    .let { null }
            }?.resolve() ?: return
            if (argClass.qualifiedName == "java.lang.Void") return // null as Something
            argClass.mainInterface?.let { main ->
                if (main == parameterType || main.isInheritor(parameterType, true)) return
            }
            parameterType.typeName?.takeIf { !methodName.contains(it, ignoreCase = true) }?.let { typeName ->
                sink.addInlineElement(offset, false, hint(prefix + typeName), false)
            }
        }
    }

    private fun hint(text: String): InlayPresentation =
        factory.roundWithBackgroundAndSmallInset(factory.smallText(text))

    private val PsiClass.mainInterface: PsiClass?
        get() =
            interfaces.takeIf { this is PsiAnonymousClass }?.singleOrNull()
                ?: mainInterfaceByName
                ?: mainInterfaceByImplCount
                ?: interfaces.takeIf { superClass == null }?.singleOrNull { it.methods.isNotEmpty() }

    private val PsiClass.mainInterfaceByName: PsiClass?
        get() {
            if (isInterface) return this
            val name = name ?: return null
            return interfaces.firstOrNull {
                val iName = it.name
                // i.e. ArrayList's main interface is List, CoolHandler's main is IdleHandler
                val cLastCap = name.indexOfLast(Character::isUpperCase)
                val iLastCap = iName?.indexOfLast(Character::isUpperCase)
                iName != null && cLastCap > -1 && iLastCap != null && iLastCap > -1 &&
                        name.regionMatches(cLastCap, iName, iLastCap, iName.length - iLastCap)
            } ?: superClass?.mainInterfaceByName
        }

    // LOL, very simple, ignores how many of them were actually implemented
    private val PsiClass.mainInterfaceByImplCount: PsiClass?
        get() = interfaces // ignore single- or two-method interfaces
            .maxByIf({ it.methods.count { it.modifierList.hasModifierProperty(PsiModifier.ABSTRACT) } }, { it > 2 })

    private val PsiClass.typeName: String?
        get() {
            val qn = qualifiedName
            return if (qn?.startsWith("kotlin.jvm.functions.Function") == true)
                buildString {
                    append('(')
                    qn.substring("kotlin.jvm.functions.Function".length).toIntOrNull()?.let {
                        repeat(it) { append('.') }
                    } ?: Logger.getInstance(InterfaceHintsCollector::class.java).error("unparsable arity: $qn")
                    append(") -> …")
                }
            else name
        }
}

data class HintsSettings(var upcast: Boolean = true, var overr: Boolean = true)
