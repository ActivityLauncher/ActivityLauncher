package de.szalkowski.activitylauncher.data.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import de.szalkowski.activitylauncher.core.util.filterByHeldPermissions
import de.szalkowski.activitylauncher.core.util.getLauncherLargeIconSize
import de.szalkowski.activitylauncher.domain.launcher.ActivityLauncherProxy
import de.szalkowski.activitylauncher.domain.launcher.ShortcutCreator
import de.szalkowski.activitylauncher.domain.model.ActivityIcon
import de.szalkowski.activitylauncher.domain.model.LaunchRequest
import de.szalkowski.activitylauncher.domain.model.PluginInfo
import javax.inject.Inject

class ActivityLauncherProxyImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActivityLauncherProxy {
    private val pm: PackageManager = context.packageManager

    override fun launchActivity(request: LaunchRequest, context: Context?) {
        val launchContext = context ?: this.context
        val intent = Intent(ActivityLauncherProxy.INTENT_LAUNCH_ACTIVITY)
        if (request.launcherPlugin != null) {
            intent.component = request.launcherPlugin
        }

        intent.putExtra(ShortcutCreator.INTENT_EXTRA_INTENT, request.intent.toUri(Intent.URI_INTENT_SCHEME))
        if (launchContext !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        launchContext.startActivity(intent)
    }

    override fun hasMultipleHandlers(): Boolean {
        val intent = Intent(ActivityLauncherProxy.INTENT_LAUNCH_ACTIVITY)
        val handlers = pm.queryIntentActivities(intent, 0).filterByHeldPermissions(context)
        return handlers.size > 1
    }

    override fun getPlugins(): List<PluginInfo> {
        val intent = Intent(ActivityLauncherProxy.INTENT_LAUNCH_ACTIVITY)
        val handlers = pm.queryIntentActivities(intent, 0).filterByHeldPermissions(context)
        return handlers.map { handler ->
            val name = handler.loadLabel(pm).toString()
            val componentName = ComponentName(handler.activityInfo.packageName, handler.activityInfo.name)
            val icon = ActivityIcon.from(handler.loadIcon(pm), context.getLauncherLargeIconSize())
            PluginInfo(name, componentName, icon)
        }
    }
}
