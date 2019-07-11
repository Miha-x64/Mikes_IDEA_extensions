package net.aquadc.mike.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.uast.UastVisitorAdapter
import org.jetbrains.kotlin.j2k.getContainingClass
import org.jetbrains.uast.UClassInitializer
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor


class EnumValuesUncachedInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        UastVisitorAdapter(object : AbstractUastNonRecursiveVisitor() {

            override fun visitElement(node: UElement): Boolean {
                val body = when (node) {
                    is UClassInitializer -> node.uastBody
                    is UMethod -> node.uastBody
                    else -> null
                }
                if (body != null) {
                    // this ignores Kotlin init-blocks, fuck them
                    val srcPsi = body.sourcePsi
                    val javaPsi = body.javaPsi
                    if (srcPsi != null && javaPsi != null) {
                        javaPsi.accept(object : JavaRecursiveElementVisitor() {
                            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                                super.visitMethodCallExpression(expression)

                                val meth = expression.methodExpression
                                if (meth.referenceName == "values" && meth.resolve()?.getContainingClass()?.isEnum == true) {
                                    holder.registerProblem(meth, "Calling Enum values() without caching")
                                }
                            }
                        })
                    }
                }

                return super.visitElement(node)
            }

        }, true);

}
