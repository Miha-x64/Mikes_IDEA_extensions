// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.introduceConstant

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.refactoring.RefactoringActionHandler
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.util.CodeInsightUtils
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceProperty.KotlinInplacePropertyIntroducer
import org.jetbrains.kotlin.idea.refactoring.introduce.selectElementsWithTargetSibling
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHint
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHintByKey
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.psi.patternMatching.toRange
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.plainContent
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.lang.Boolean.TYPE as boolean

object KtIntroduceConstantHandlerCompat : RefactoringActionHandler { // Mike-REMOVED helper parameter
    object InteractiveExtractionHelper : ExtractionEngineHelper(INTRODUCE_CONSTANT) {

        // Mike-REMOVED private fun getExtractionTarget(descriptor: ExtractableCodeDescriptor)

        override fun validate(descriptor: ExtractableCodeDescriptor) = // Mike-CHANGED: only property with initializer
            descriptor.validate(ExtractionTarget.PROPERTY_WITH_INITIALIZER)

        override fun configureAndRun(
            project: Project,
            editor: Editor,
            descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
            onFinish: (ExtractionResult) -> Unit
        ) {
            val descriptor = descriptorWithConflicts.descriptor // Mike-CHANGED target
            if (ExtractionTarget.PROPERTY_WITH_INITIALIZER.isAvailable(descriptor)) {
                // ExtractionGeneratorOptions(target = target, delayInitialOccurrenceReplacement = true, isConst = true)
                doRefactor(ExtractionGeneratorConfiguration(descriptor, IntroduceConstantExtractionOptions!!), onFinish) // Mike-CHANGED options
            } else {
                showErrorHint(
                    project,
                    editor,
                    KotlinBundle.message("error.text.can.t.introduce.constant.for.this.expression"),
                    INTRODUCE_CONSTANT
                )
            }
        }
    }

    fun doInvoke(project: Project, editor: Editor, file: KtFile, elements: List<PsiElement>, target: PsiElement) {
        val adjustedElements = (elements.singleOrNull() as? KtBlockExpression)?.statements ?: elements
        when {
            adjustedElements.isEmpty() -> {
                showErrorHintByKey(
                    project, editor, "cannot.refactor.no.expression",
                    INTRODUCE_CONSTANT
                )
            }
            else -> {
                val options = ExtractionOptions(extractAsProperty = true)
                val extractionData = ExtractionData(file, adjustedElements.toRange(), target, null, options)
                ExtractionEngine(InteractiveExtractionHelper).run(editor, extractionData) { // Mike-CHANGED helper
                    val property = it.declaration as KtProperty
                    val descriptor = it.config.descriptor

                    editor.caretModel.moveToOffset(property.textOffset)
                    editor.selectionModel.removeSelection()
                    if (editor.settings.isVariableInplaceRenameEnabled && !isUnitTestMode()) {
                        with(PsiDocumentManager.getInstance(project)) {
                            commitDocument(editor.document)
                            doPostponedOperationsAndUnblockDocument(editor.document)
                        }

                        val introducer = KotlinInplacePropertyIntroducer(
                            property = property,
                            editor = editor,
                            project = project,
                            title = INTRODUCE_CONSTANT,
                            doNotChangeVar = false,
                            exprType = descriptor.returnType,
                            extractionResult = it,
                            availableTargets = listOf(ExtractionTarget.PROPERTY_WITH_GETTER)
                        )
                        introducer.performInplaceRefactoring(LinkedHashSet(getNameSuggestions(property) + descriptor.suggestedNames))
                    } else {
                        processDuplicatesSilently(it.duplicateReplacers, project)
                    }
                }
            }
        }
    }

    private fun getNameSuggestions(property: KtProperty): List<String> {
        val initializerValue = property.initializer.safeAs<KtStringTemplateExpression>()?.plainContent
        val identifierValue = property.identifyingElement?.text

        return listOfNotNull(initializerValue, identifierValue).map { NameUtil.capitalizeAndUnderscore(it) }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is KtFile) return
        selectElements(editor, file) { elements, targets -> doInvoke(project, editor, file, elements, targets) }
    }

    fun selectElements(
        editor: Editor,
        file: KtFile,
        continuation: (elements: List<PsiElement>, targets: PsiElement) -> Unit
    ) {

        selectElementsWithTargetSibling(
            INTRODUCE_CONSTANT,
            editor,
            file,
            KotlinBundle.message("title.select.target.code.block"),
            listOf(CodeInsightUtils.ElementKind.EXPRESSION),
            ::validateExpressionElements,
            { _, sibling ->
                sibling.getExtractionContainers(strict = true, includeAll = true)
                    .filter { (it is KtFile && !it.isScript()) }
            },
            continuation
        )
    }

    // Mike-REMOVED private fun validateElements(elements: List<PsiElement>): String?
    // Mike-REMOVED private fun KtExpression.isNotConst(): Boolean

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        throw AssertionError("$INTRODUCE_CONSTANT can only be invoked from editor")
    }

    private fun validateExpressionElements(elements: List<PsiElement>): String? {
        if (elements.any { it is KtConstructor<*> || it is KtParameter || it is KtTypeAlias || it is KtPropertyAccessor }) {
            return KotlinBundle.message("text.refactoring.is.not.applicable.to.this.code.fragment")
        }
        return null
    }
}

val INTRODUCE_CONSTANT: String
    @Nls
    get() = KotlinBundle.message("introduce.constant")

// Mike-ADDED:
@Suppress("NAME_SHADOWING")
internal val IntroduceConstantExtractionOptions by lazy {
    try {
        ExtractionGeneratorOptions::class.java.getConstructor(
            boolean, ExtractionTarget::class.java, String::class.java, boolean, boolean, boolean
        ).newInstance(false, ExtractionTarget.PROPERTY_WITH_INITIALIZER, null, true, true, false)
    } catch (e: Exception) {
        null
    }
}