package net.aquadc.mike.plugin.android

import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceFolderType
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.model.AndroidModel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers


fun AndroidFacet.resTypeOf(file: PsiFile): ResourceFolderType? =
    ModuleResourceManagers.getInstance(this).localResourceManager.getFileResourceFolderType(file)

val AndroidFacet.androidMinSdk: AndroidVersion?
    get() = AndroidModel.get(this)?.minSdkVersion

private var resolveResolver: (ConfigurationManager, VirtualFile) -> ResourceResolver? = { man, vf ->
    try {
        man.getConfiguration(vf).resourceResolver.also { _ ->
            resolveResolver = { man, vf -> man.getConfiguration(vf).resourceResolver }
        }
    } catch (e: LinkageError) {
        val getConfiguration = ConfigurationManager::class.java.getMethod("getConfiguration", VirtualFile::class.java)
        val TConfiguration = Class.forName("com.android.tools.configurations.Configuration")
        //                              was com.android.tools.idea.configurations.Configuration
        val getResourceResolver = TConfiguration.getMethod("getResourceResolver")
        (getResourceResolver(getConfiguration(man, vf)) as ResourceResolver).also { _ ->
            resolveResolver = { man, vf -> getResourceResolver(getConfiguration(man, vf)) as ResourceResolver }
        }
    }
}
fun ConfigurationManager.resourceResolver(file: VirtualFile): ResourceResolver? =
    resolveResolver(this, file)
