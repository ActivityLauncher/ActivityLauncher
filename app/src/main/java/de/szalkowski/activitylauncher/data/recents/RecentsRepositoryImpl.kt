package de.szalkowski.activitylauncher.data.recents

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import de.szalkowski.activitylauncher.app.di.IoDispatcher
import de.szalkowski.activitylauncher.data.database.RecentDao
import de.szalkowski.activitylauncher.data.database.RecentEntity
import de.szalkowski.activitylauncher.data.database.SerializationUtils
import de.szalkowski.activitylauncher.domain.model.LaunchSource
import de.szalkowski.activitylauncher.domain.model.ShortcutRequest
import de.szalkowski.activitylauncher.domain.packages.PackageRepository
import de.szalkowski.activitylauncher.domain.recents.RecentsRepository
import de.szalkowski.activitylauncher.domain.usecase.launcher.GetActivityIconUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val recentDao: RecentDao,
    private val packageRepository: PackageRepository,
    private val getActivityIconUseCase: GetActivityIconUseCase,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
) : RecentsRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("al_recent_activities", Context.MODE_PRIVATE)
    private val recentsKey = "recents"
    private val migrationKey = "room_migration_done"
    private val maxRecents = 20
    private val repositoryScope = CoroutineScope(dispatcher)

    init {
        migrateIfNeeded()
    }

    private fun migrateIfNeeded() {
        if (!prefs.getBoolean(migrationKey, false)) {
            val legacyRecents = getLegacyRecents()
            repositoryScope.launch {
                if (legacyRecents.isNotEmpty()) {
                    legacyRecents.sortedBy { it.timestamp }.forEach { recent ->
                        try {
                            val activityInfo = packageRepository.getActivity(recent.componentName)
                            val icon = getActivityIconUseCase(activityInfo.iconResourceName, recent.componentName)
                            val intent = Intent().setComponent(recent.componentName)
                            val entity = RecentEntity(
                                packageName = recent.componentName.packageName,
                                className = recent.componentName.className,
                                name = activityInfo.name,
                                intentUri = SerializationUtils.intentToUri(intent),
                                iconBundle = SerializationUtils.iconToByteArray(icon),
                                launcherPlugin = null,
                                timestamp = recent.timestamp,
                            )
                            recentDao.insert(entity)
                        } catch (_: Exception) {}
                    }
                }
                prefs.edit(commit = true) { putBoolean(migrationKey, true) }
            }
        }
    }

    private fun getLegacyRecents(): List<RecentsRepository.RecentActivity> {
        val strings = prefs.getStringSet(recentsKey, emptySet()) ?: emptySet()
        return strings.mapNotNull { fromString(it) }
    }

    override fun getRecentActivities(): List<RecentsRepository.RecentActivity> {
        return runBlocking(Dispatchers.IO) {
            getLegacyRecents().sortedByDescending { it.timestamp }
        }
    }

    override fun getRecentsFlow(): Flow<List<ShortcutRequest>> {
        return recentDao.getAllFlow().map { entities ->
            entities.map { entity ->
                ShortcutRequest(
                    name = entity.name,
                    intent = SerializationUtils.uriToIntent(entity.intentUri),
                    icon = SerializationUtils.byteArrayToIcon(entity.iconBundle)!!,
                    launcherPlugin = entity.launcherPlugin?.let { ComponentName.unflattenFromString(it) },
                    source = LaunchSource.SAVED,
                )
            }
        }
    }

    override fun addActivity(componentName: ComponentName) {
        repositoryScope.launch {
            try {
                val activityInfo = packageRepository.getActivity(componentName)
                val icon = getActivityIconUseCase(activityInfo.iconResourceName, componentName)
                val intent = Intent().setComponent(componentName)
                addActivity(ShortcutRequest(activityInfo.name, intent, icon, source = LaunchSource.SAVED), updateMetadata = false)
            } catch (_: Exception) {}
        }
    }

    override fun addActivity(request: ShortcutRequest, updateMetadata: Boolean) {
        addActivityInternal(request, System.currentTimeMillis(), updateMetadata)
    }

    private fun addActivityInternal(request: ShortcutRequest, timestamp: Long, updateMetadata: Boolean) {
        repositoryScope.launch {
            val component = request.intent.component ?: return@launch
            val intentUri = SerializationUtils.intentToUri(request.intent)
            val launcherPluginStr = request.launcherPlugin?.flattenToString()

            if (!updateMetadata) {
                val updated = recentDao.updateUsage(
                    packageName = component.packageName,
                    className = component.className,
                    timestamp = timestamp,
                    launcherPlugin = launcherPluginStr,
                    intentUri = intentUri,
                )
                if (updated > 0) {
                    syncLegacy(component, timestamp)
                    return@launch
                }
            }

            val entity = RecentEntity(
                packageName = component.packageName,
                className = component.className,
                name = request.name,
                intentUri = intentUri,
                iconBundle = SerializationUtils.iconToByteArray(request.icon),
                launcherPlugin = launcherPluginStr,
                timestamp = timestamp,
            )
            recentDao.insert(entity)
            recentDao.trim(maxRecents)

            syncLegacy(component, timestamp)
        }
    }

    private fun syncLegacy(component: ComponentName, timestamp: Long) {
        // Sync legacy
        val legacy = getLegacyRecents().toMutableList()
        legacy.removeAll { it.componentName == component }
        legacy.add(0, RecentsRepository.RecentActivity(component, timestamp))
        val newStringSet = legacy.asSequence().take(maxRecents).map { toString(it) }.toSet()
        prefs.edit { putStringSet(recentsKey, newStringSet) }
    }

    override fun removeActivity(componentName: ComponentName) {
        repositoryScope.launch {
            recentDao.deleteByComponent(componentName.packageName, componentName.className)

            val legacy = getLegacyRecents().toMutableList()
            legacy.removeAll { it.componentName == componentName }
            prefs.edit { putStringSet(recentsKey, legacy.map { toString(it) }.toSet()) }
        }
    }

    override fun removeActivity(request: ShortcutRequest) {
        repositoryScope.launch {
            val component = request.intent.component ?: return@launch
            recentDao.deleteByComponent(component.packageName, component.className)

            request.intent.component?.let { component ->
                val legacy = getLegacyRecents().toMutableList()
                legacy.removeAll { it.componentName == component }
                prefs.edit { putStringSet(recentsKey, legacy.map { toString(it) }.toSet()) }
            }
        }
    }

    private fun toString(activity: RecentsRepository.RecentActivity): String {
        return "${activity.componentName.flattenToString()};${activity.timestamp}"
    }

    private fun fromString(string: String): RecentsRepository.RecentActivity? {
        return try {
            val parts = string.split(';')
            val componentName = ComponentName.unflattenFromString(parts[0])!!
            val timestamp = if (parts.size > 2) parts[2].toLong() else parts[1].toLong()
            RecentsRepository.RecentActivity(componentName, timestamp)
        } catch (e: Exception) {
            null
        }
    }
}
