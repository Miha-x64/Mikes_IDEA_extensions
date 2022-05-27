package net.aquadc.mike.plugin.android

import com.android.resources.ResourceFolderType
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.intellij.psi.PsiFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import java.lang.reflect.Method


fun AndroidFacet.resTypeOf(file: PsiFile): ResourceFolderType? =
    ModuleResourceManagers.getInstance(this).localResourceManager.getFileResourceFolderType(file)

var androidMinSdk: AndroidFacet.() -> AndroidVersion? = {
    try {
        val ret = AndroidModuleModel.get(this)?.minSdkVersion
        androidMinSdk = { AndroidModuleModel.get(this)?.minSdkVersion }
        ret
    } catch (e: IncompatibleClassChangeError) { // Дебилы, блядь!
        val AndroidModuleModel_get = AndroidModuleModel::class.java.getMethod("get", AndroidFacet::class.java)
        val AndroidModuleModel_getMinSdkVersion = AndroidModuleModel::class.java.getMethod("getMinSdkVersion")
        val ret = getMinSdkVersion(AndroidModuleModel_get, AndroidModuleModel_getMinSdkVersion)
        androidMinSdk = { getMinSdkVersion(AndroidModuleModel_get, AndroidModuleModel_getMinSdkVersion) }
        ret
    }
}
    private set

private fun AndroidFacet.getMinSdkVersion(
    AndroidModuleModel_get: Method, AndroidModuleModel_getMinSdkVersion: Method
): AndroidVersion? =
    (AndroidModuleModel_get(null, this))?.let { AndroidModuleModel_getMinSdkVersion(it) as AndroidVersion? }
