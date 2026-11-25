package de.szalkowski.activitylauncher.domain.launcher

import android.content.Context
import de.szalkowski.activitylauncher.domain.model.ShortcutProxyRequest

interface ShortcutCreator {
    suspend fun createLauncherIcon(request: ShortcutProxyRequest, shortcutId: String, context: Context? = null)

    companion object {
        const val INTENT_LAUNCH_SHORTCUT = "activitylauncher.intent.action.LAUNCH_SHORTCUT"
        const val INTENT_LAUNCH_ROOT_SHORTCUT = "activitylauncher.intent.action.LAUNCH_ROOT_SHORTCUT"
        const val INTENT_EXTRA_NAME = "name"
        const val INTENT_EXTRA_INTENT = "intent"
        const val LEGACY_INTENT_EXTRA_INTENT = "extra_intent"
        const val INTENT_EXTRA_ICON = "icon"
        const val INTENT_EXTRA_SIGNATURE = "sign"
        const val INTENT_EXTRA_LAUNCH_PLUGIN = "launch_plugin"
        const val INTENT_EXTRA_SHORTCUT_ACTIVITY = "shortcut_activity"
        const val INTENT_EXTRA_SHORTCUT_ID = "shortcut_id"
    }
}
