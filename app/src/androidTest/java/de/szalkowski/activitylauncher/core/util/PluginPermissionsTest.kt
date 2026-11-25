package de.szalkowski.activitylauncher.core.util

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PluginPermissionsTest {
    @Test
    fun filtersHandlersWhosePermissionsAreNotHeld() {
        val context = mock<Context>()
        whenever(context.checkCallingOrSelfPermission(PERMISSION_HELD))
            .thenReturn(PackageManager.PERMISSION_GRANTED)
        whenever(context.checkCallingOrSelfPermission(PERMISSION_DENIED))
            .thenReturn(PackageManager.PERMISSION_DENIED)

        val held = resolveInfo(PERMISSION_HELD, name = ".Held")
        val denied = resolveInfo(PERMISSION_DENIED, name = ".Denied")
        val unprotected = resolveInfo(null, name = ".Unprotected")

        assertEquals(
            listOf(held, unprotected),
            listOf(held, denied, unprotected).filterByHeldPermissions(context),
        )
    }

    @Test
    fun collapsesAliasesThatTargetTheSameActivity() {
        val context = mock<Context>()
        whenever(context.checkCallingOrSelfPermission(PERMISSION_HELD))
            .thenReturn(PackageManager.PERMISSION_GRANTED)
        whenever(context.checkCallingOrSelfPermission(PERMISSION_ALSO_HELD))
            .thenReturn(PackageManager.PERMISSION_GRANTED)

        val firstAlias =
            resolveInfo(PERMISSION_HELD, name = ".FirstAlias", targetActivity = ".LauncherActivity")
        val secondAlias =
            resolveInfo(PERMISSION_ALSO_HELD, name = ".SecondAlias", targetActivity = ".LauncherActivity")
        val otherPlugin = resolveInfo(null, name = ".Other", packageName = "other.plugin")

        assertEquals(
            listOf(firstAlias, otherPlugin),
            listOf(firstAlias, secondAlias, otherPlugin).filterByHeldPermissions(context),
        )
    }

    private fun resolveInfo(
        permission: String?,
        name: String = ".Alias",
        packageName: String = "plugin",
        targetActivity: String? = null,
    ): ResolveInfo {
        val activityInfo =
            ActivityInfo().apply {
                this.packageName = packageName
                this.name = name
                this.permission = permission
                this.targetActivity = targetActivity
            }
        return ResolveInfo().apply { this.activityInfo = activityInfo }
    }

    private companion object {
        const val PERMISSION_HELD = "de.szalkowski.activitylauncher.permission.LAUNCH_ACTIVITY"
        const val PERMISSION_DENIED = "de.szalkowski.activitylauncher.oss.permission.LAUNCH_ACTIVITY"
        const val PERMISSION_ALSO_HELD =
            "de.szalkowski.activitylauncher.oss.permission.LAUNCH_ACTIVITY"
    }
}
