package de.szalkowski.activitylauncher.core.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * PackageManager returns exported activities even when the caller does not hold the permission
 * required by those activities. Filter those handlers out and collapse aliases that target the same
 * activity so a plugin exposing one alias per permission family is not offered twice.
 */
internal fun List<ResolveInfo>.filterByHeldPermissions(context: Context): List<ResolveInfo> =
    filter { info ->
        val permission = info.activityInfo?.permission
        permission == null || context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }.distinctBy { info ->
        info.activityInfo?.let { activity ->
            "${activity.packageName}/${activity.targetActivity ?: activity.name}"
        }
    }
