package net.aquadc.mike.plugin.memory

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.codeStyle.CodeStyleManager
import net.aquadc.mike.plugin.FunctionCallVisitor
import net.aquadc.mike.plugin.NamedLocalQuickFix
import net.aquadc.mike.plugin.NamedReplacementFix
import net.aquadc.mike.plugin.PsiType_INT
import net.aquadc.mike.plugin.SortedArray
import net.aquadc.mike.plugin.UastInspection
import net.aquadc.mike.plugin.argument
import net.aquadc.mike.plugin.decapitalize
import net.aquadc.mike.plugin.fixes
import net.aquadc.mike.plugin.replaceAndShorten
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import net.aquadc.mike.plugin.miserlyMap as map

/**
 * @author Mike Gorünóv
 */
class BoxCollectionInspection : UastInspection() {
    override fun uVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): AbstractUastNonRecursiveVisitor =
        BoxCollectionVisitor(holder)
}

class BoxCollectionVisitor(
    private val holder: ProblemsHolder,
) : FunctionCallVisitor(constructor = true, arrayInitializer = true) {
    override fun visitCallExpr(
        node: UExpression,
        src: PsiElement,
        kind: UastCallKind,
        operator: String?,
        declaringClassFqn: String?,
        receiver: UExpression?,
        methodName: String,
        valueArguments: List<UExpression>,
    ): Boolean {
        node as UCallExpression
        val unboxed = unboxed[node.componentType()] ?: return true
        when (kind) {
            UastCallKind.METHOD_CALL -> {
                visitMethodCall(declaringClassFqn, methodName, src, unboxed, valueArguments)
            }
            UastCallKind.CONSTRUCTOR_CALL -> {
                var size: PsiElement? = null
                var factory: PsiElement? = null
                if (methodName == "Array" &&
                    node.argument(0, "size")?.sourcePsi.also { size = it } != null &&
                    node.argument(1, "init")?.sourcePsi.also { factory = it } != null) {
                    visitWithFactory(src, unboxed, size!!, factory!!)
                } else {
                    visitMethodCall(declaringClassFqn, methodName, src, unboxed, valueArguments)
                }
            }
            UastCallKind.NEW_ARRAY_WITH_DIMENSIONS -> {
                visitWithCapacity(src, unboxed, valueArguments.singleOrNull()?.sourcePsi, myFactories = arrayFactories)
            }
            UastCallKind.NEW_ARRAY_WITH_INITIALIZER, UastCallKind.NESTED_ARRAY_INITIALIZER ->
                visitOfElements(src, unboxed, arrayFactories)
        }
        return true
    }

    private fun visitMethodCall(
        declaringClassFqn: String?, methodName: String,
        src: PsiElement, unboxed: String, valueArguments: List<UExpression>,
    ) {
        lateinit var myFactory: FactoryDef
        val myFactories = factories.firstOrNull { facs ->
            facs.firstOrNull {
                it.declaringClassFqn == declaringClassFqn && it.funNames.contains(methodName)
            }?.also { myFactory = it } != null
        } ?: return

        when (myFactory) {
            is FactoryDef.OfElements ->
                visitOfElements(src, unboxed, myFactories)
            is FactoryDef.WithCapacity ->
                visitWithCapacity(src, unboxed, valueArguments.singleOrNull()?.takeIfInt()?.sourcePsi, myFactories)
            is FactoryDef.Empty ->
                visitWithCapacity(src, unboxed, null, myFactories)
        }
    }

    private fun UExpression.takeIfInt(): UExpression? =
        takeIf { it.getExpressionType() == PsiType_INT }

    private fun visitWithCapacity(src: PsiElement, unboxed: String, dimensions: PsiElement?, myFactories: Array<out FactoryDef>) {
        val libraryFactories = findReplacements<FactoryImpl.WithCapacity>(unboxed, myFactories)
        val unboxedLS = if (src.language == JavaLanguage.INSTANCE) unboxed.decapitalize() else unboxed
        holder.registerProblem(
            src,
            "${factoryNames[myFactories]!!} of boxed ${unboxedLS}s",
            *fixes(dimensions?.let { NamedReplacementFix(  // array is a language feature, thus 'Replace' goes before 'Use'
                name = "Replace with $unboxedLS array",
                expression = "new ${unboxedLS.lowercase()}[${dimensions.text}]",
                kotlinExpression = "${unboxedLS}Array(${dimensions.text})",
            ) }),
            *libraryFactories.map { (lib, fac) ->
                ReplaceWithCapacity(fac, lib, unboxed)
            }
        )
    }
    private class ReplaceWithCapacity(
        private val fac: FactoryImpl.WithCapacity,
        lib: Library,
        private val unboxed: String,
    ) : NamedLocalQuickFix("Use ${fac.name} from ${lib.name}") { // libraries are not language features, thus 'Use' go after 'Replace'
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val cap =
                descriptor.psiElement
                    ?.toUElementOfType<UCallExpression>()
                    ?.valueArguments?.singleOrNull()
                    ?.sourcePsi?.text
            val newKwSp =
                "new ".takeIf { descriptor.psiElement.language === JavaLanguage.INSTANCE }.orEmpty()
            replaceAndShorten(project, descriptor.psiElement, fac.expression(newKwSp, unboxed, cap))
        }
    }

    private fun visitOfElements(src: PsiElement, unboxed: String, myFactories: Array<out FactoryDef>) {
        val libraryFactories = findReplacements<FactoryImpl.OfElements>(unboxed, myFactories)
        val unboxedLS = if (src.language == JavaLanguage.INSTANCE) unboxed.decapitalize() else unboxed
        holder.registerProblem(
            src,
            "${factoryNames[myFactories]!!} of boxed ${unboxedLS}s",
            ReplaceOfElementsWithArray(unboxedLS, myFactories),
            *libraryFactories.map { (lib, fac) ->
                ReplaceOfElementsWithLibrary(fac, lib, myFactories, unboxed)
            }
        )
    }
    private class ReplaceOfElementsWithArray(
        private val unboxedLS: String,
        private val myFactories: Array<out FactoryDef>,
    ) : NamedLocalQuickFix("Replace with $unboxedLS array") { // array is a language feature, thus 'Replace' goes before 'Use'
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val psi = descriptor.psiElement ?: return
            val (success, sArgs) = psi.argsSortedIfOrdered(myFactories) ?: return
            replaceAndShorten(
                project, psi,
                when (psi.language) {
                    JavaLanguage.INSTANCE ->
                        "new ${unboxedLS.decapitalize()}[] {${sArgs.joinToString(", ")}}"
                    KotlinLanguage.INSTANCE ->
                        // UInt -> uint[ArrayOf()]
                        "${unboxedLS.lowercase()}ArrayOf(${sArgs.joinToString(", ")})".let {
                            if (success) it else "$it.also { it.sort() }"
                        }
                    else -> return
                }
            )
        }
    }
    private class ReplaceOfElementsWithLibrary(
        private val fac: FactoryImpl.OfElements,
        lib: Library,
        private val myFactories: Array<out FactoryDef>,
        private val unboxed: String,
    ) : NamedLocalQuickFix("Use ${fac.name} from ${lib.name}") { // libraries are not language features, thus 'Use' go after 'Replace'
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val psi = descriptor.psiElement ?: return
            val (success, sArgs) = psi.argsSortedIfOrdered(myFactories) ?: return
            val postfix = if (success) "" else fac.sorted // e.g. expr.sorted()
            replaceAndShorten(project, descriptor.psiElement, fac.expression(unboxed, sArgs) + postfix)
        }
    }

    private fun visitWithFactory(src: PsiElement, unboxed: String, size: PsiElement, factory: PsiElement) {
        val libraryFactories = findReplacements<FactoryImpl.WithFactory>(unboxed, arrayFactories)
        val size = size.text
        val trailingFac = factory is KtLambdaExpression
        val factory = factory.text
        holder.registerProblem(
            src,
            "Array of boxed ${unboxed}s",
            ReplaceWithFactoryWithArray(unboxed, trailingFac, size, factory),
            *libraryFactories.map { (lib, fac) ->
                ReplaceWithFactoryWithLibrary(fac, lib, unboxed)
            }
        )
    }
    private class ReplaceWithFactoryWithArray(
        private val unboxed: String,
        private val trailingFac: Boolean,
        private val size: String?,
        private val factory: String?,
    ) : NamedLocalQuickFix("Replace with $unboxed array") { // array is a language feature, thus 'Replace' goes before 'Use'
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val psi = descriptor.psiElement
            CodeStyleManager.getInstance(psi.project).reformat(
                psi.replace(
                    KtPsiFactory(psi.project).createExpression(
                        if (trailingFac) "${unboxed}Array($size) $factory"
                        else "${unboxed}Array($size, $factory)"
                    )
                )
            )
        }
    }
    private class ReplaceWithFactoryWithLibrary(
        private val fac: FactoryImpl.WithFactory,
        lib: Library,
        private val unboxed: String,
    ) : NamedLocalQuickFix("Use ${fac.name} from ${lib.name}") { // libraries are not language features, thus 'Use' go after 'Replace'
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val node = descriptor.psiElement?.toUElementOfType<UCallExpression>() ?: return
            val size = node.argument(0, "size")?.sourcePsi ?: return
            val init = node.argument(1, "init")?.sourcePsi ?: return
            replaceAndShorten(project, descriptor.psiElement, fac.expression(unboxed, size.text, init.text, init is KtLambdaExpression || init is KtLambdaArgument))
        }
    }

    private fun UCallExpression.componentType(): String? {
        val t = getExpressionType() ?: return null
        return when (t) {
            is PsiArrayType -> {
                val ct = t.componentType
                /*when (ct.nullability.nullability()) {
                    Nullability.NOT_NULL ->
                        {}
                    Nullability.NULLABLE ->
                        return null
                    Nullability.UNKNOWN ->
                        if (valueArguments.any { // deeper check for Java
                            it.getExpressionType()?.nullability?.nullability() == Nullability.NULLABLE
                        })
                            return null
                }*/
                ct.canonicalText
            }
            is PsiClassType -> {
                val qn = t.resolve()?.qualifiedName ?: return null
                if (qn !in containers) return null
                val ct = t.parameters.singleOrNull()
//                        if (ct?.nullability?.nullability() == Nullability.NULLABLE) return null
                when (ct) {
                    is PsiClassType -> ct.canonicalText
                    is PsiWildcardType if ct.isExtends -> ct.bound?.canonicalText
                    else -> {
                        Logger.getInstance(BoxCollectionInspection::class.java).error("Unsupported type ${ct?.javaClass}: $ct")
                        ct?.canonicalText
                    }
                }
            }
            else -> {
//                        "!! ${t.javaClass}: $t"
                null
            }
        }
    }
    private companion object {
        private fun PsiElement.argsSortedIfOrdered(myFactories: Array<out FactoryDef>): Pair<Boolean, Array<String>>? {
            val args = toUElementOfType<UCallExpression>()?.valueArguments ?: return null
            val distinctArgs =
                if (myFactories in setFactories) args.asInSet(sorted = myFactories === sortedSetFactories)?.asList()
                else args
            val sortedArgText = (distinctArgs ?: args).map { it.sourcePsi?.text ?: return null }
            return Pair(distinctArgs != null, sortedArgText)
        }
        private fun List<UExpression>.asInSet(sorted: Boolean): Array<UExpression>? =
            map { it to (it.evaluate() ?: return null) }
                .takeIf { !sorted || it.safeSortBy(Pair<*, *>::second) }
                ?.distinctBy(Pair<*, *>::second)
                ?.map(Pair<UExpression, *>::first)
        private fun <T, R> Array<T>.safeSortBy(selector: (T) -> R): Boolean =
            try {
                @Suppress("UNCHECKED_CAST")
                sortBy { selector(it) as Comparable<Any> }
                true
            } catch (_: ClassCastException) {
                false
            }

        private val arrayFactories = arrayOf(
            FactoryDef.OfElements(null, "arrayOf"),
            FactoryDef.WithCapacity(null, "arrayOfNulls"),
        )
        private val listFactories = arrayOf(
            FactoryDef.OfElements("java.util.List", "of"),
            FactoryDef.OfElements("java.util.Arrays", "asList"),
            FactoryDef.OfElements("kotlin.collections.CollectionsKt", "listOf", "mutableListOf", "arrayListOf"),
            FactoryDef.WithCapacity("java.util.ArrayList", "<init>"),
            FactoryDef.Empty("java.util.ArrayList", "<init>"),
            FactoryDef.WithCapacity("java.util.LinkedList", "<init>"),
            FactoryDef.Empty("java.util.LinkedList", "<init>"),
            FactoryDef.WithCapacity("java.util.ArrayDeque", "<init>"),
            FactoryDef.Empty("java.util.ArrayDeque", "<init>"),
            FactoryDef.Empty("java.util.Stack", "<init>"),
            FactoryDef.WithCapacity("java.util.Vector", "<init>"),
            FactoryDef.Empty("java.util.Vector", "<init>"),
        )
        private val unorderedSetFactories = arrayOf(
            FactoryDef.OfElements("kotlin.collections.SetsKt", "setOf", "mutableSetOf", "hashSetOf",),
            FactoryDef.OfElements("java.util.Set", "of"),
            FactoryDef.WithCapacity("java.util.HashSet", "<init>"),
            FactoryDef.Empty("java.util.HashSet", "<init>"),
        )
        private val sortedSetFactories = arrayOf(
            FactoryDef.OfElements("kotlin.collections.SetsKt", "sortedSetOf"),
            FactoryDef.Empty("java.util.TreeSet", "<init>"),
        )
        private val linkedSetFactories = arrayOf(
            FactoryDef.OfElements("kotlin.collections.SetsKt", "linkedSetOf"),
            FactoryDef.WithCapacity("java.util.LinkedHashSet", "<init>"),
            FactoryDef.Empty("java.util.LinkedHashSet", "<init>"),
        )

        private val linearFactories = arrayOf(arrayFactories, listFactories)
        private val setFactories = arrayOf(unorderedSetFactories, sortedSetFactories, linkedSetFactories)
        private val factories = arrayOf(arrayFactories, listFactories) + setFactories
        private val factoryNames = buildMap(5) {
            put(arrayFactories, "Array")
            put(listFactories, "List")
            put(unorderedSetFactories, "Set")
            put(sortedSetFactories, "SortedSet")
            put(linkedSetFactories, "LinkedSet")
        }

        @FileModifier.SafeTypeForPreview
        private sealed class FactoryDef(val declaringClassFqn: String?, vararg val funNames: String) {
            class OfElements(declaringClassFqn: String?, vararg funNames: String) : FactoryDef(declaringClassFqn, *funNames)
            class WithCapacity(declaringClassFqn: String?, vararg funNames: String) : FactoryDef(declaringClassFqn, *funNames)
            class Empty(declaringClassFqn: String?, vararg funNames: String) : FactoryDef(declaringClassFqn, *funNames)
//          class WithFactory(declaringClassFqn: String) : FactoryDef(declaringClassFqn) the only example is Array(size, init), resolved in code
        }

        private val containers = SortedArray.of(
            "java.util.ArrayList",
            "java.util.HashSet",
            "java.util.LinkedHashSet",
            "java.util.List",
            "java.util.Set",
            "java.util.TreeSet",
        )

        private val unboxed = buildMap(12) {
            put("java.lang.Boolean", "Boolean")
            put("java.lang.Byte", "Byte")
            put("java.lang.Character", "Char")
            put("java.lang.Double", "Double")
            put("java.lang.Float", "Float")
            put("java.lang.Integer", "Int")
            put("java.lang.Long", "Long")
            put("java.lang.Short", "Short")
            put("kotlin.UByte", "UByte")
            put("kotlin.UShort", "UShort")
            put("kotlin.UInt", "UInt")
            put("kotlin.ULong", "ULong")
        }

        private val libraries = arrayOf(
            Library(
                "JDK",
                null,
                arrayOf(
                    factories to arrayOf(
                        FactoryImpl.OfElements(
                            name = "Streams", sorted = ".sorted()",
                            "Int", "Long", "Double",
                        ) { type, els ->
                            "java.util.stream.${type}Stream.of(${els.joinToString(", ")})"
                        },
                        FactoryImpl.WithFactory(
                            name = "Streams",
                            "Int",
                        ) { type, size, factory, isLambda ->
                            "java.util.stream.${type}Stream.range(0, $size).map${factory.singleFunArg(isLambda)}"
                        },
                        FactoryImpl.WithFactory(
                            name = "Streams",
                            "Long", "Double",
                        ) { type, size, factory, isLambda ->
                            "java.util.stream.${type}Stream.range(0, $size).mapTo${type}${factory.singleFunArg(isLambda)}"
                        },
                    ),
                    linearFactories to arrayOf(
                        FactoryImpl.WithCapacity("BitSet", "Boolean") { newKwSp, _, cap -> "${newKwSp}java.util.BitSet${cap.orEmpty().paren()}" },
                    ),
                ),
            ),

//            Library("HPPC", "com.carrotsearch:hppc"),
            // Source: https://github.com/carrotsearch/hppc ⭐️ 1.1k
            // Artifact: https://mvnrepository.com/artifact/com.carrotsearch/hppc Used by (324)
            // Docs: https://carrotsearch.github.io/hppc/releases/0.12.0/api/
            // API:
            // com.carrotsearch.hppc.BitSet(long numBits)
            // com.carrotsearch.hppc.{Byte, Char, Double, Float, Int, Long, Short}ArrayList::{<init>(int cap), from(...)}
            // com.carrotsearch.hppc.{Char, Int, Long, Short}HashSet::{<init>(int cap), from(...)}

//            Library("fastutil", "it.unimi.dsi:fastutil-core"),
            // Source: https://github.com/vigna/fastutil/ ⭐️ 2.3k
            // Artifact: https://central.sonatype.com/artifact/it.unimi.dsi/fastutil-core
            // Docs: https://repo1.maven.org/maven2/it/unimi/dsi/fastutil/8.5.19/fastutil-8.5.19-javadoc.jar
            // API:
            // it.unimi.dsi.fastutil.{
            //     doubles.DoubleArrayList,
            //     ints.IntArrayList,
            //     longs.LongArrayList,
            // }::{<init>([]), <init>(int cap), of(...), wrap([])}

            // it.unimi.dsi.fastutil.{
            //     doubles.{DoubleOpenHashSet, DoubleLinkedOpenHashSet},
            //     ints.{IntOpenHashSet, IntLinkedOpenHashSet},
            //     longs.{LongOpenHashSet, LongLinkedOpenHashSet},
            // }::{<init>([]), <init>(int cap), of(...)}

            // it.unimi.dsi.fastutil.{
            //     doubles.{DoubleAVLTreeSet, DoubleRBTreeSet},
            //     ints.{IntAVLTreeSet, IntRBTreeSet},
            //     longs.{LongAVLTreeSet, LongRBTreeSet},
            // }::{<init>(), <init>([])}

//            Library("fastutil", "it.unimi.dsi:fastutil"),
            // Source: https://github.com/vigna/fastutil/ ⭐️ 2.3k
            // Artifact: https://central.sonatype.com/artifact/it.unimi.dsi/fastutil 8.5.18 Used in: 971 components
            // Docs: https://repo1.maven.org/maven2/it/unimi/dsi/fastutil/8.5.19/fastutil-8.5.19-javadoc.jar

            // API:
            // it.unimi.dsi.fastutil.{
            //     booleans.BooleanArrayList,
            //     bytes.ByteArrayList,
            //     chars.CharArrayList,
            //     floats.FloatArrayList,
            //     shorts.ShortArrayList
            // }::{<init>([]), <init>(int cap), of(...), wrap([])}

            // it.unimi.dsi.fastutil.{
            //     booleans.BooleanOpenHashSet,
            //     bytes.{ByteOpenHashSet, ByteLinkedOpenHashSet},
            //     chars.{CharOpenHashSet, CharLinkedOpenHashSet},
            //     floats.{FloatOpenHashSet, FloatLinkedOpenHashSet},
            //     shorts.{ShortOpenHashSet, ShortLinkedOpenHashSet},
            // }::{<init>([]), <init>(int cap), of(...)}

            // it.unimi.dsi.fastutil.{
            //     bytes.{ByteAVLTreeSet, ByteRBTreeSet},
            //     chars.{CharRBTreeSet, CharAVLTreeSet},
            //     floats.{FloatAVLTreeSet, FloatRBTreeSet},
            //     shorts.{ShortAVLTreeSet, ShortRBTreeSet},
            // }::{<init>(), <init>([])}

//            Library("Agrona", "org.agrona:agrona"),
            // Source: https://github.com/aeron-io/agrona ⭐️ 3.3k
            // Artifact: https://central.sonatype.com/artifact/org.agrona/agrona 2.6.1 Used in: 641 components
            // Docs: https://repo1.maven.org/maven2/org/agrona/agrona/2.7.0/agrona-2.7.0-javadoc.jar
            // API:
            // org.agrona.collections.{Int, Long}ArrayList::{<init>(), <init>(int cap, T nullValue), <init>(T[] initialElements, int initialSize, T nullValue)}
            // org.agrona.collections.{Int, Long}HashSet::{<init>(), <init>(cap)}

//            Library("Eclipse", "org.eclipse.collections:eclipse-collections-api"),
            // Source: https://github.com/eclipse-collections/eclipse-collections ⭐️ 2.7k
            // Artifact: https://central.sonatype.com/artifact/org.eclipse.collections/eclipse-collections 13.0.0 Used in: 1316 components
            // API:
            // org.eclipse.collections.api.factory.list.primitive.Mutable{Boolean, Byte, Short, Char, Float}ListFactory::{of(...), withInitialCapacity(int cap)}
            // org.eclipse.collections.api.factory.list.primitive.Mutable{Int, Long, Double}ListFactory::{of(...), ofAll(Stream), withInitialCapacity(int cap)}
            // org.eclipse.collections.api.factory.list.primitive.Mutable{Boolean, Byte}SetFactory::of(...)
            // org.eclipse.collections.api.factory.list.primitive.Mutable{Char}SetFactory::{of(...), withInitialCapacity(int cap)}
            // org.eclipse.collections.api.factory.list.primitive.Mutable{Char, Float, Short}SetFactory::{of(...), withInitialCapacity(int cap)}
            // org.eclipse.collections.api.factory.list.primitive.Mutable{Double, Int, Long}SetFactory::{of(...), withInitialCapacity(int cap), withAll(Stream)}

//            Library("Trove", "net.sf.trove4j:core"),
            // Source: https://bitbucket.org/trove4j/trove/src/master/
            // Artifact: https://mvnrepository.com/artifact/trove/trove Used by (136)
            // Artifact: https://mvnrepository.com/artifact/net.sf.trove4j/trove4j Used by (893)
            // Artifact: https://mvnrepository.com/artifact/net.sf.trove4j/core Used by (127)
            // API:
            // gnu.trove.list.array.T{Byte, Char, Double, Float, Int, Long, Short}ArrayList::{<init>([]), <init>(int cap)}
            // gnu.trove.set.hash.T{Byte, Char, Double, Float, Int, Long, Short}HashSet::{<init>([]), <init>(int cap)}

//            Library("AndroidX", "androidx.collection:collection"),
            // Artifact: https://developer.android.com/jetpack/androidx/releases/collection
            // Docs: https://developer.android.com/reference/kotlin/androidx/collection/package-summary
            // API:
            // androidx.collection.Mutable{Float, Int, Long}{List, Set}::<init>(int cap)
            // androidx.collection.MutableDoubleList::<init>(int cap)
            // androidx.collection::mutable{Long, Double, Float, Int}ListOf(...)
            // androidx.collection::mutable{Float, Int, Long}SetOf(...)

            // see also:
            // https://github.com/leventov/Koloboke ⭐️ 1k, Last commit >10 years ago:
            // https://mvnrepository.com/artifact/org.neo4j/neo4j-primitive-collections Used by (11), last version from 2020
        )
        private class Library(
            val name: String,
            val artifact: String?, // TODO add libraries
            factories: Array<Pair<Array<*>, Array<FactoryImpl>>>,
        ) {
            val factories: Map<Any?, Array<FactoryImpl>> =
                buildMap {
                    factories.forEach { (factoryDefs, factoryImpls) ->
                        factoryDefs.forEach { def ->
                            compute(def) { _, impls -> if (impls == null) factoryImpls else impls + factoryImpls }
                        }
                    }
                }
        }

        private inline fun <reified T : FactoryImpl> findReplacements(
            unboxed: String, forFactories: Array<out FactoryDef>,
        ): List<Pair<Library, T>> = libraries.mapNotNull { lib ->
            lib.factories[forFactories]?.firstOrNull {
                (it as? T)?.types?.contains(unboxed) == true
            }?.let { lib to it as T }
        }

        @FileModifier.SafeTypeForPreview
        private sealed class FactoryImpl(val name: String, vararg val types: String) {

            @FileModifier.SafeTypeForPreview
            class OfElements(
                name: String,
                val sorted: String = "",
                vararg types: String,
                val expression: (type: String, els: Array<String>) -> String,
            ) : FactoryImpl(name, types = types) {
                init {
                    require(types.isNotEmpty())
                }
            }

            @FileModifier.SafeTypeForPreview
            class WithCapacity(
                name: String,
                vararg types: String,
                val expression: (newKwSp: String, type: String, cap: String?) -> String,
            ) : FactoryImpl(name, types = types)

            @FileModifier.SafeTypeForPreview
            class WithFactory(
                name: String,
                vararg types: String,
                val expression: (type: String, size: String, factory: String, isLambda: Boolean) -> String,
            ) : FactoryImpl(name, types = types)
        }

        private fun String.paren() = if (isEmpty()) "()" else "($this)"
        private fun String.singleFunArg(isLambda: Boolean) =
            if (isLambda) " $this" else "($this)"
    }

}
