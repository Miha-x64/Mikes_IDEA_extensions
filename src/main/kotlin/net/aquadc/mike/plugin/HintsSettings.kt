package net.aquadc.mike.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.BaseConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.panels.VerticalBox
import com.intellij.util.xmlb.XmlSerializerUtil
import javax.swing.JCheckBox
import javax.swing.JComponent


class HintsSettingsUi() : BaseConfigurable(), Configurable.NoScroll {
    private var component: JComponent? = null
    private var upcast: JCheckBox? = null
    private var overr: JCheckBox? = null

    override fun getDisplayName(): String = "Upcast and override hints"

    override fun createComponent(): JComponent = component ?: VerticalBox().apply {
        add(JBCheckBox("Show upcast hints in Java").also { upcast = it })
        add(JBCheckBox("Show override hints in Java and Kotlin").also { overr = it })
        component = this
    }

    override fun isModified(): Boolean  = HintsSettingsState.instance.let { state ->
        upcast!!.isSelected != state.upcast || overr!!.isSelected != state.overr
    }

    override fun apply() {
        HintsSettingsState.instance.let { state ->
            state.upcast = upcast!!.isSelected
            state.overr = overr!!.isSelected
        }
    }

    override fun reset() {
        HintsSettingsState.instance.let { state ->
            upcast!!.isSelected = state.upcast
            overr!!.isSelected = state.overr
        }
    }

    override fun disposeUIResources() {
        component = null
        upcast = null
        overr = null
    }
}

@Service
@State(name = "net.aquadc.mike.plugin.HintsSettings", storages = [
    Storage(value = "net.aquadc.mike.plugin.HintsSettings.xml", roamingType = RoamingType.DISABLED)
])
class HintsSettingsState : PersistentStateComponent<HintsSettingsState> {
    var upcast = true
    var overr = true
    override fun getState(): HintsSettingsState = this
    override fun loadState(state: HintsSettingsState): Unit = XmlSerializerUtil.copyBean(state, this)
    companion object {
        val instance get() = ApplicationManager.getApplication().getService(HintsSettingsState::class.java)
    }
}
