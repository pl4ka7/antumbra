package app.antumbra.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Switches between the branded launcher entry and a neutral "Browser" entry. */
object LauncherIcon {

    fun apply(context: Context, discreet: Boolean) {
        val pm = context.packageManager
        toggle(pm, ComponentName(context, "app.antumbra.LauncherDefault"), !discreet)
        toggle(pm, ComponentName(context, "app.antumbra.LauncherDiscreet"), discreet)
    }

    private fun toggle(pm: PackageManager, component: ComponentName, enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}
