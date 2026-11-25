package de.szalkowski.activitylauncher.data.launcher

import android.content.ComponentName
import android.content.Intent
import androidx.core.graphics.drawable.IconCompat
import de.szalkowski.activitylauncher.domain.launcher.ActivityLauncherProxy
import de.szalkowski.activitylauncher.domain.launcher.ShortcutCreator
import de.szalkowski.activitylauncher.domain.launcher.ViewIntentParser
import de.szalkowski.activitylauncher.domain.model.ActivityIcon
import de.szalkowski.activitylauncher.domain.model.LaunchRequest
import de.szalkowski.activitylauncher.domain.model.LaunchSource
import de.szalkowski.activitylauncher.domain.model.ShortcutProxyRequest
import de.szalkowski.activitylauncher.domain.model.ShortcutSource
import javax.inject.Inject

class ViewIntentParserImpl @Inject constructor() : ViewIntentParser {
    override fun parseLaunchRequest(intent: Intent, source: LaunchSource?): LaunchRequest? {
        val launchIntentStr = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_INTENT)
            ?: intent.getStringExtra(ShortcutCreator.LEGACY_INTENT_EXTRA_INTENT)
        val launchIntent = launchIntentStr?.let { parseShortcutIntent(it) }

        val launcherPlugin = if (intent.action != ActivityLauncherProxy.INTENT_LAUNCH_ACTIVITY) {
            val launcherPluginStr = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_LAUNCH_PLUGIN)
            launcherPluginStr?.let { ComponentName.unflattenFromString(it) }
        } else {
            null
        }

        val name = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_NAME)
        val iconBundle = intent.getBundleExtra(ShortcutCreator.INTENT_EXTRA_ICON)
        val icon = iconBundle?.let { IconCompat.createFromBundle(it) }?.let { ActivityIcon.from(it) }

        val resolvedSource = source ?: when (intent.action) {
            ActivityLauncherProxy.INTENT_LAUNCH_ACTIVITY -> LaunchSource.PROXY
            Intent.ACTION_VIEW -> LaunchSource.DEEPLINK
            else -> LaunchSource.PRIMARY
        }

        if (launchIntent != null) {
            return LaunchRequest(
                launchIntent,
                name = name,
                icon = icon,
                launcherPlugin = launcherPlugin,
                source = resolvedSource,
            )
        }

        val component = componentNameFromIntent(intent) ?: return null
        val newLaunchIntent = Intent().apply {
            this.component = component
        }

        return LaunchRequest(newLaunchIntent, launcherPlugin = launcherPlugin, source = resolvedSource)
    }

    override fun parseShortcutRequest(intent: Intent): ShortcutProxyRequest? {
        val name = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_NAME) ?: ""
        val launchIntentStr = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_INTENT)
            ?: intent.getStringExtra(ShortcutCreator.LEGACY_INTENT_EXTRA_INTENT)
            ?: return null
        val launchIntent = parseShortcutIntent(launchIntentStr) ?: return null

        val iconBundle = intent.getBundleExtra(ShortcutCreator.INTENT_EXTRA_ICON)
        val icon = iconBundle?.let { IconCompat.createFromBundle(it) }?.let { ActivityIcon.from(it) } ?: return null

        val source = when (intent.action) {
            ShortcutCreator.INTENT_LAUNCH_SHORTCUT -> LaunchSource.SHORTCUT
            ActivityLauncherProxy.INTENT_LAUNCH_ACTIVITY -> LaunchSource.PROXY
            Intent.ACTION_VIEW -> LaunchSource.DEEPLINK
            else -> LaunchSource.PRIMARY
        }

        return ShortcutProxyRequest(name, icon, launchIntent, source = source)
    }

    override fun parseLaunchSource(intent: Intent): ShortcutSource? {
        val launchIntentStr = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_INTENT)
            ?: intent.getStringExtra(ShortcutCreator.LEGACY_INTENT_EXTRA_INTENT)
        val launchIntent = launchIntentStr?.let { parseShortcutIntent(it) }
        val signature = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_SIGNATURE)
        val launcherPluginStr = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_LAUNCH_PLUGIN)
        val launcherPlugin = launcherPluginStr?.let { ComponentName.unflattenFromString(it) }
        val shortcutId = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_SHORTCUT_ID)

        if ((shortcutId == null) && (launchIntent == null) && (signature == null)) {
            return null
        }

        return ShortcutSource(
            id = shortcutId,
            intent = launchIntent,
            signature = signature,
            launcherPlugin = launcherPlugin,
        )
    }

    override fun componentNameFromIntent(intent: Intent): ComponentName? {
        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                runCatching {
                    val url = intent.dataString.orEmpty()
                    val rawComponent = url.removePrefix("https://activitylauncher.net/activity/")
                    ComponentName.unflattenFromString(rawComponent)
                        ?: throw Exception("Invalid component name")
                }.getOrNull()
            }

            ShortcutCreator.INTENT_LAUNCH_SHORTCUT,
            ShortcutCreator.INTENT_LAUNCH_ROOT_SHORTCUT,
            -> {
                val launchIntentStr = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_INTENT)
                    ?: intent.getStringExtra(ShortcutCreator.LEGACY_INTENT_EXTRA_INTENT)
                    ?: return null
                parseShortcutIntent(launchIntentStr)?.component
            }

            else -> null
        }
    }

    override fun parseShortcutIntent(uri: String): Intent? {
        val intent = try {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
        } catch (_: Exception) {
            null
        }

        if (intent != null && intent.component != null) {
            return intent
        }

        return try {
            Intent.parseUri(uri, 0)
        } catch (_: Exception) {
            null
        }
    }
}
