package de.szalkowski.activitylauncher.data.favorites

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import de.szalkowski.activitylauncher.app.di.IoDispatcher
import de.szalkowski.activitylauncher.data.database.FavoriteDao
import de.szalkowski.activitylauncher.data.database.FavoriteEntity
import de.szalkowski.activitylauncher.data.database.SerializationUtils
import de.szalkowski.activitylauncher.domain.favorites.FavoritesRepository
import de.szalkowski.activitylauncher.domain.model.LaunchSource
import de.szalkowski.activitylauncher.domain.model.ShortcutRequest
import de.szalkowski.activitylauncher.domain.packages.PackageRepository
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
class FavoritesRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val favoriteDao: FavoriteDao,
    private val packageRepository: PackageRepository,
    private val getActivityIconUseCase: GetActivityIconUseCase,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
) : FavoritesRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("al_favorites", Context.MODE_PRIVATE)
    private val favoritesKey = "favorites"
    private val migrationKey = "room_migration_done"
    private val repositoryScope = CoroutineScope(dispatcher)

    init {
        migrateIfNeeded()
    }

    private fun migrateIfNeeded() {
        if (!prefs.getBoolean(migrationKey, false)) {
            val legacyFavorites = getLegacyFavorites()
            repositoryScope.launch {
                if (legacyFavorites.isNotEmpty()) {
                    legacyFavorites.forEach { componentName ->
                        try {
                            val activityInfo = packageRepository.getActivity(componentName)
                            val icon = getActivityIconUseCase(activityInfo.iconResourceName, componentName)
                            val intent = Intent().setComponent(componentName)
                            val entity = FavoriteEntity(
                                packageName = componentName.packageName,
                                className = componentName.className,
                                name = activityInfo.name,
                                intentUri = SerializationUtils.intentToUri(intent),
                                iconBundle = SerializationUtils.iconToByteArray(icon),
                                launcherPlugin = null,
                                timestamp = System.currentTimeMillis(),
                            )
                            favoriteDao.insert(entity)
                        } catch (_: Exception) {
                            // Skip if activity not found or other error
                        }
                    }
                }
                prefs.edit(commit = true) { putBoolean(migrationKey, true) }
            }
        }
    }

    private fun getLegacyFavorites(): Set<ComponentName> {
        val strings = prefs.getStringSet(favoritesKey, emptySet()) ?: emptySet()
        return strings.asSequence().mapNotNull { ComponentName.unflattenFromString(it) }.toSet()
    }

    override fun getFavorites(): Set<ComponentName> {
        // For backward compatibility, return components from current favorites
        return getLegacyFavorites()
    }

    override fun getFavoritesFlow(): Flow<List<ShortcutRequest>> {
        return favoriteDao.getAllFlow().map { entities ->
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

    override fun addFavorite(componentName: ComponentName) {
        repositoryScope.launch {
            try {
                val activityInfo = packageRepository.getActivity(componentName)
                val icon = getActivityIconUseCase(activityInfo.iconResourceName, componentName)
                val intent = Intent().setComponent(componentName)
                addFavorite(ShortcutRequest(activityInfo.name, intent, icon, source = LaunchSource.SAVED))
            } catch (_: Exception) {}
        }
    }

    override fun addFavorite(request: ShortcutRequest) {
        repositoryScope.launch {
            val component = request.intent.component ?: return@launch
            val entity = FavoriteEntity(
                packageName = component.packageName,
                className = component.className,
                name = request.name,
                intentUri = SerializationUtils.intentToUri(request.intent),
                iconBundle = SerializationUtils.iconToByteArray(request.icon),
                launcherPlugin = request.launcherPlugin?.flattenToString(),
                timestamp = System.currentTimeMillis(),
            )
            favoriteDao.insert(entity)

            // Also update legacy set for backward compatibility if needed
            val legacy = getLegacyFavorites().toMutableSet()
            legacy.add(component)
            prefs.edit { putStringSet(favoritesKey, legacy.map { it.flattenToString() }.toSet()) }
        }
    }

    override fun removeFavorite(componentName: ComponentName) {
        repositoryScope.launch {
            favoriteDao.deleteByComponent(componentName.packageName, componentName.className)

            val legacy = getLegacyFavorites().toMutableSet()
            legacy.remove(componentName)
            prefs.edit { putStringSet(favoritesKey, legacy.map { it.flattenToString() }.toSet()) }
        }
    }

    override fun removeFavorite(request: ShortcutRequest) {
        repositoryScope.launch {
            val component = request.intent.component ?: return@launch
            favoriteDao.deleteByComponent(component.packageName, component.className)

            request.intent.component?.let { component ->
                val legacy = getLegacyFavorites().toMutableSet()
                legacy.remove(component)
                prefs.edit { putStringSet(favoritesKey, legacy.map { it.flattenToString() }.toSet()) }
            }
        }
    }

    override fun isFavorite(componentName: ComponentName): Boolean {
        return runBlocking(Dispatchers.IO) {
            favoriteDao.isFavorite(componentName.packageName, componentName.className)
        }
    }
}
