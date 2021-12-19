@file:Suppress("UnstableApiUsage")
package net.aquadc.mike.plugin.interfaces

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsProviderFactory
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.ProviderInfo
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.java.JavaBundle
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.jvm.types.JvmReferenceType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope.allScope
import com.intellij.ui.layout.panel
import net.aquadc.mike.plugin.maxByIf
import net.aquadc.mike.plugin.referencedName
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.j2k.getContainingClass
import org.jetbrains.kotlin.j2k.getContainingMethod
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

/**
 * @author Mike Gorünóv
 */
class InterfaceHintsPF : InlayHintsProviderFactory {

    override fun getProvidersInfo(project: Project): List<ProviderInfo<out Any>> = listOf(
        ProviderInfo(JavaLanguage.INSTANCE, InterfaceHintsP(jSettingsKey)),
        ProviderInfo(KotlinLanguage.INSTANCE, InterfaceHintsP(kSettingsKey)),
    )

    private companion object {
        private val jSettingsKey = SettingsKey<HintsSettings>("net.aquadc.mike.plugin.interfaces.hints.java")
        private val kSettingsKey = SettingsKey<HintsSettings>("net.aquadc.mike.plugin.interfaces.hints.kotlin")
    }

}

private class InterfaceHintsP(
    override val key: SettingsKey<HintsSettings>,
) : InlayHintsProvider<HintsSettings> {

    override fun getCollectorFor(
        file: PsiFile, editor: Editor, settings: HintsSettings, sink: InlayHintsSink
    ): InlayHintsCollector =
        InterfaceHintsCollector(editor, file, settings)

    override fun createSettings(): HintsSettings = HintsSettings()

    override val name: String get() = "Interface hints"
    override val previewText: String? = null

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
            element.valueArguments.takeIf(List<*>::isNotEmpty)?.let { args ->
                element.calleeExpression?.mainReference?.resolve()?.functionParams?.let { params ->
                    args.forEachIndexed { idx, arg: KtValueArgument ->
                        (if (arg.name != null) params.firstOrNull { it.first == arg.name } else params.getOrNull(idx))
                            ?.second?.let { pType ->
                                arg.getArgumentExpression()?.toUElementOfType<UExpression>()
                                    ?.getExpressionType()?.let { aType ->
                                        element.referenceExpression()?.referencedName?.let { methodName ->
                                            visitParameter(methodName, pType, aType, arg.textRange.endOffset, sink)
                                        }
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
            ((this as? PsiMethod) ?: toUElementOfType<UMethod>()?.javaPsi)?.let {
                it.parameters.map {
                    null to (it.type as? JvmReferenceType)?.resolve() as? PsiClass
                }
            }

    private fun visitParameter(
        methodName: String, parameterType: PsiElement, argumentType: PsiType, offset: Int, sink: InlayHintsSink
    ) {
        if (parameterType is PsiClass) {
            if (!parameterType.isInterface) return
            val argClass = when (argumentType) {
                PsiType.NULL -> null
                is PsiClassType -> argumentType
                is PsiPrimitiveType -> argumentType.getBoxedType(parameterType.manager, allScope(parameterType.getProject()))
                else -> null
            }?.resolve() ?: return
            argClass.mainInterface?.let { main ->
                if (main == parameterType || main.isInheritor(parameterType, true)) return
            }
            parameterType.typeName?.takeIf { !methodName.contains(it, ignoreCase = true) }?.let { typeName ->
                sink.addInlineElement(offset, false, hint("as $typeName"), false)
            }
        }
    }

    private fun hint(text: String): InlayPresentation =
        factory.roundWithBackgroundAndSmallInset(factory.smallText(text))

    private val PsiClass.mainInterface: PsiClass?
        get() =
            mainInterfaceByName ?: mainInterfaceByImplCount ?: interfaces.takeIf { superClass == null }?.singleOrNull()

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
        get() = if (qualifiedName?.startsWith("kotlin.jvm.functions.Function") == true) "(…) -> …" else name
}

data class HintsSettings(var upcast: Boolean = true, var overr: Boolean = true)
