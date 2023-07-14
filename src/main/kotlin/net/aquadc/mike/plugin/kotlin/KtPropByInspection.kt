package net.aquadc.mike.plugin.kotlin

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptorWithAccessors
import org.jetbrains.kotlin.descriptors.accessors
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * @author Mike Gorünóv
 */
class KtPropByInspection : LocalInspectionTool() {

    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitProperty(property: KtProperty) {
            property.delegate?.let { delegate ->
                if (delegate.getTargetDescriptors(property.analyze())
                    .any { (it as? FunctionDescriptor)?.isInline == false }) {
                    holder.registerProblem(delegate, TextRange.from(0, 2), "Heavyweight property delegation")
                }
            }
        }
        // copy-paste from hidden org.jetbrains.kotlin.idea.references.KtPropertyDelegationMethodsReferenceDescriptorsImpl.getTargetDescriptors
        fun KtPropertyDelegate.getTargetDescriptors(context: BindingContext): Collection<DeclarationDescriptor> {
            val property = expression?.getStrictParentOfType<KtProperty>() ?: return emptyList()
            val descriptor = context[BindingContext.DECLARATION_TO_DESCRIPTOR, property] as? VariableDescriptorWithAccessors
                ?: return emptyList()
            return descriptor.accessors.mapNotNull { accessor ->
                context.get(BindingContext.DELEGATED_PROPERTY_RESOLVED_CALL, accessor)?.candidateDescriptor
            } + listOfNotNull(context.get(BindingContext.PROVIDE_DELEGATE_RESOLVED_CALL, descriptor)?.candidateDescriptor)
        }
        override fun visitNamedFunction(function: KtNamedFunction) {
            if (function.isDelegationFunction && !function.hasModifier(KtTokens.INLINE_KEYWORD)) {
                holder.registerProblem(
                    function.nameIdentifier ?: function,
                    "Delegation functions should be inline",
                    AddModifierFix(function, KtTokens.INLINE_KEYWORD)
                )
            }
        }

        private val KtNamedFunction.isDelegationFunction: Boolean
            get() = isPotentiallyOperator && name?.let {
                it == OperatorNameConventions.PROVIDE_DELEGATE.asString() ||
                        it == OperatorNameConventions.GET_VALUE.asString() ||
                        it == OperatorNameConventions.SET_VALUE.asString()
            } ?: false

        // org.jetbrains.kotlin.idea.search.isPotentiallyOperator
        val PsiElement?.isPotentiallyOperator: Boolean
            get() {
                val namedFunction = safeAs<KtNamedFunction>() ?: return false
                if (namedFunction.hasModifier(KtTokens.OPERATOR_KEYWORD)) return true
                // operator modifier could be omitted for overriding function
                if (!namedFunction.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return false
                val name = namedFunction.name ?: return false
                if (!OperatorConventions.isConventionName(Name.identifier(name))) return false
                return true
            }
    }

}
