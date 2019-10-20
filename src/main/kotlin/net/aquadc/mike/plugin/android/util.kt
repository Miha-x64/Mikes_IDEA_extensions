package net.aquadc.mike.plugin.android

import com.android.resources.ResourceFolderType
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers


inline val PsiFile.androidFacet: AndroidFacet?
    get() = AndroidFacet.getInstance(this)

inline val AndroidFacet.moduleResManagers: ModuleResourceManagers
    get() = ModuleResourceManagers.getInstance(this)

fun AndroidFacet.isLowerThan(expected: Int): Boolean? =
    AndroidModuleModel.get(this)?.minSdkVersion?.apiLevel?.let { it < expected }

val PsiFile.isLayoutXml: Boolean
    get() = this is XmlFile && androidFacet?.moduleResManagers?.localResourceManager?.getFileResourceFolderType(this) == ResourceFolderType.LAYOUT

val PsiFile.isDrawableXml: Boolean
    get() = this is XmlFile && androidFacet?.moduleResManagers?.localResourceManager?.getFileResourceFolderType(this) == ResourceFolderType.DRAWABLE

