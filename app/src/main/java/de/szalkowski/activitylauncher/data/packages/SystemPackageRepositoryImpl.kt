package de.szalkowski.activitylauncher.data.packages

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.szalkowski.activitylauncher.core.util.isPrivate
import de.szalkowski.activitylauncher.domain.model.MyActivityInfo
import de.szalkowski.activitylauncher.domain.model.SystemPackage
import de.szalkowski.activitylauncher.domain.packages.SystemPackageRepository
import de.szalkowski.activitylauncher.domain.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemPackageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : SystemPackageRepository {

    private val packageManager: PackageManager = context.packageManager

    override fun getInstalledPackages(): List<SystemPackage> {
        var flags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            flags = flags or PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            flags = flags or PackageManager.MATCH_APEX
        }

        return packageManager.getInstalledPackages(flags).map { it.toSystemPackage() }
    }

    override fun getPackageDetails(packageName: String): SystemPackage? {
        return try {
            var flags = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                flags = flags or PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
            }
            val info = packageManager.getPackageInfo(packageName, flags)
            info.toSystemPackage()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    override fun getActivities(packageName: String): List<MyActivityInfo> {
        return try {
            var flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                flags = flags or PackageManager.MATCH_DISABLED_COMPONENTS or
                    PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS or
                    PackageManager.MATCH_DIRECT_BOOT_AWARE or
                    PackageManager.MATCH_DIRECT_BOOT_UNAWARE
            }

            val info = packageManager.getPackageInfo(packageName, flags)
            val activities = info.activities ?: return emptyList()
            val appRes = getLocalizedResources(packageName)

            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }
            val launcherActivities = packageManager.queryIntentActivities(launcherIntent, 0)
                .map { it.activityInfo.name }
                .toSet()

            activities.map { activity ->
                val name = if (appRes != null && activity.labelRes != 0) {
                    runCatching { appRes.getString(activity.labelRes) }.getOrElse { activity.loadLabel(packageManager).toString() }
                } else {
                    activity.loadLabel(packageManager).toString()
                }

                MyActivityInfo(
                    componentName = ComponentName(packageName, activity.name),
                    name = name,
                    isPrivate = activity.isPrivate(packageManager),
                    iconResourceName = runCatching { appRes?.getResourceName(activity.iconResource) }.getOrNull(),
                    isDefault = launcherActivities.contains(activity.name),
                )
            }
        } catch (e: PackageManager.NameNotFoundException) {
            emptyList()
        }
    }

    private fun PackageInfo.toSystemPackage(): SystemPackage {
        val app = applicationInfo
        val name = app?.loadLabel(packageManager)?.toString() ?: packageName
        val appRes = getLocalizedResources(packageName)
        return SystemPackage(
            packageName = packageName,
            name = name,
            version = "$versionName (${PackageInfoCompat.getLongVersionCode(this)})",
            iconResourceName = runCatching { appRes?.getResourceName(app?.icon ?: 0) }.getOrNull(),
        )
    }

    private fun getLocalizedResources(packageName: String): Resources? {
        return runCatching {
            val appRes = packageManager.getResourcesForApplication(packageName)
            val config = settingsRepository.getLocaleConfiguration()
            @Suppress("DEPRECATION")
            appRes.updateConfiguration(config, appRes.displayMetrics)
            appRes
        }.getOrNull()
    }
}
