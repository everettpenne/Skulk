package com.everettpenne.skulk.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.everettpenne.skulk.R

/**
 * The launcher icons the person can pick from. Each is an activity-alias in the manifest; exactly one is enabled, and
 * switching enables the new one before disabling the old so the app is never briefly absent from the launcher.
 */
enum class AppIcon(val alias: String, val label: String, val mipmap: Int, val enabledInManifest: Boolean = false) {
    Sandworm("IconSandworm", "Original Sandworm", R.mipmap.ic_launcher_original, enabledInManifest = true),
    Skulk("IconSkulk", "Skulk", R.mipmap.ic_launcher),
    PeekOrange("IconPeekOrange", "Peek Orange", R.mipmap.ic_launcher_peek_orange),
    PeekPearl("IconPeekPearl", "Peek Pearl", R.mipmap.ic_launcher_peek_pearl),
    PeekBlue("IconPeekBlue", "Peek Blue", R.mipmap.ic_launcher_peek_blue),
}

object AppIcons {
    private fun component(context: Context, icon: AppIcon) = ComponentName(context.packageName, "${context.packageName}.${icon.alias}")

    private fun isEnabled(context: Context, icon: AppIcon): Boolean =
        when (context.packageManager.getComponentEnabledSetting(component(context, icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> icon.enabledInManifest
        }

    /** The icon showing now: the first enabled alias, or the original if none reads as enabled. */
    fun current(context: Context): AppIcon = AppIcon.entries.firstOrNull { isEnabled(context, it) } ?: AppIcon.Sandworm

    fun select(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(component(context, icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (other in AppIcon.entries) if (other != icon)
            pm.setComponentEnabledSetting(component(context, other), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        // The launcher shortcuts point at the enabled alias, so they move with the icon.
        com.everettpenne.skulk.SkulkShortcuts.publish(context)
    }
}
