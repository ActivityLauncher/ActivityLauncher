package de.szalkowski.activitylauncher.entrypoint

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import de.szalkowski.activitylauncher.app.di.CoreServicesModule
import de.szalkowski.activitylauncher.domain.launcher.ActivityLauncher
import de.szalkowski.activitylauncher.domain.launcher.ActivityLauncherProxy
import de.szalkowski.activitylauncher.domain.launcher.IntentSigner
import de.szalkowski.activitylauncher.domain.launcher.ShortcutCreator
import de.szalkowski.activitylauncher.domain.launcher.ShortcutCreatorProxy
import de.szalkowski.activitylauncher.domain.launcher.ViewIntentParser
import de.szalkowski.activitylauncher.domain.model.*
import de.szalkowski.activitylauncher.domain.settings.BackupRepository
import de.szalkowski.activitylauncher.domain.settings.SettingsRepository
import de.szalkowski.activitylauncher.domain.shortcuts.ShortcutsRepository
import de.szalkowski.activitylauncher.domain.usecase.launcher.ResolveShortcutSourceUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@HiltAndroidTest
@UninstallModules(CoreServicesModule::class)
@RunWith(AndroidJUnit4::class)
class ShortcutFlowTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @BindValue
    val activityLauncher: ActivityLauncher = mock()

    @BindValue
    val intentSigner: IntentSigner = mock()

    @BindValue
    val getActivityIconUseCase: de.szalkowski.activitylauncher.domain.usecase.launcher.GetActivityIconUseCase = mock()

    @BindValue
    val packageRepository: de.szalkowski.activitylauncher.domain.packages.PackageRepository = mock()

    @BindValue
    val shortcutCreator: ShortcutCreator = mock()

    @BindValue
    val activityLauncherProxy: ActivityLauncherProxy = mock()

    @BindValue
    val shortcutCreatorProxy: ShortcutCreatorProxy = mock()

    @BindValue
    val iconLoader: de.szalkowski.activitylauncher.domain.launcher.IconLoader = mock()

    @BindValue
    val activitySharer: de.szalkowski.activitylauncher.domain.external.ActivitySharer = mock()

    @BindValue
    val viewIntentParser: ViewIntentParser = mock()

    @BindValue
    val resolveShortcutSourceUseCase: ResolveShortcutSourceUseCase = mock()

    @BindValue
    val settingsRepository: SettingsRepository = mock()

    @BindValue
    val backupRepository: BackupRepository = mock()

    @BindValue
    val favoritesRepository: de.szalkowski.activitylauncher.domain.favorites.FavoritesRepository = mock()

    @BindValue
    val recentsRepository: de.szalkowski.activitylauncher.domain.recents.RecentsRepository = mock()

    @BindValue
    val shortcutsRepository: ShortcutsRepository = mock()

    @Before
    fun init() {
        hiltRule.inject()

        val icon = ActivityIcon.Resource(ApplicationProvider.getApplicationContext<android.content.Context>().packageName, android.R.drawable.sym_def_app_icon)
        whenever(getActivityIconUseCase.invoke(anyOrNull(), any())).thenReturn(icon)

        whenever(settingsRepository.disclaimerAccepted).thenReturn(true)
        whenever(favoritesRepository.getFavorites()).thenReturn(emptySet())
        whenever(recentsRepository.getRecentActivities()).thenReturn(emptyList())
        whenever(favoritesRepository.getFavoritesFlow()).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(emptyList()))
        whenever(recentsRepository.getRecentsFlow()).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(emptyList()))
        whenever(packageRepository.packagesFlow).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(emptyList()))
        whenever(shortcutsRepository.getShortcutsFlow()).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(emptyList()))
        runBlocking { whenever(shortcutsRepository.recordShortcut(any(), anyOrNull())).thenReturn("uuid-1") }
        whenever(packageRepository.isSyncing).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(value = false))
        whenever(packageRepository.isLoaded).thenReturn(true)
        whenever(activityLauncherProxy.hasMultipleHandlers()).thenReturn(true)

        whenever(packageRepository.getActivity(any())).thenAnswer { invocation ->
            val componentName = invocation.getArgument<ComponentName>(0)
            MyActivityInfo(
                componentName,
                "Test Activity",
                null,
                false,
            )
        }

        whenever(packageRepository.getActivities(any())).thenAnswer { invocation ->
            val packageName = invocation.getArgument<String>(0)
            de.szalkowski.activitylauncher.domain.model.PackageActivities(
                packageName,
                "Test App",
                null,
                emptyList(),
            )
        }

        // Stub viewIntentParser to handle the URI parsing in ShortcutActivity
        whenever(viewIntentParser.parseShortcutRequest(any())).thenAnswer { invocation: org.mockito.invocation.InvocationOnMock ->
            val intent = invocation.getArgument<Intent>(0)
            val launchIntentUri = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_INTENT) ?: return@thenAnswer null
            val appName = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_NAME) ?: ""
            val icon = ActivityIcon.Resource(ApplicationProvider.getApplicationContext<android.content.Context>().packageName, android.R.drawable.sym_def_app_icon)

            val launchIntent = try {
                Intent.parseUri(launchIntentUri, Intent.URI_INTENT_SCHEME)
            } catch (_: Exception) {
                null
            } ?: return@thenAnswer null

            ShortcutProxyRequest(
                name = appName,
                icon = icon,
                intent = launchIntent,
                source = LaunchSource.SHORTCUT,
            )
        }

        whenever(viewIntentParser.parseLaunchRequest(any(), anyOrNull())).thenAnswer { invocation: org.mockito.invocation.InvocationOnMock ->
            val intent = invocation.getArgument<Intent>(0)
            val launchIntentStr = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_INTENT) ?: return@thenAnswer null
            val launchIntent = try {
                Intent.parseUri(launchIntentStr, Intent.URI_INTENT_SCHEME)
            } catch (_: Exception) {
                null
            } ?: return@thenAnswer null

            val launchPluginStr = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_LAUNCH_PLUGIN)
            val launchPlugin = launchPluginStr?.let { ComponentName.unflattenFromString(it) }

            LaunchRequest(
                intent = launchIntent,
                launcherPlugin = launchPlugin,
                source = LaunchSource.PROXY,
            )
        }

        whenever(viewIntentParser.parseLaunchSource(any())).thenAnswer { invocation: org.mockito.invocation.InvocationOnMock ->
            val intent = invocation.getArgument<Intent>(0)
            val shortcutId = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_SHORTCUT_ID)

            val launchIntentStr = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_INTENT)
            val launchIntent = launchIntentStr?.let {
                try {
                    Intent.parseUri(it, Intent.URI_INTENT_SCHEME)
                } catch (_: Exception) {
                    null
                }
            }

            val signature = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_SIGNATURE)
            val launchPluginStr = intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_LAUNCH_PLUGIN)
            val launchPlugin = launchPluginStr?.let { ComponentName.unflattenFromString(it) }

            if ((shortcutId == null) && (launchIntent == null) && (signature == null)) {
                return@thenAnswer null
            }

            ShortcutSource(
                id = shortcutId,
                intent = launchIntent,
                signature = signature,
                launcherPlugin = launchPlugin,
            )
        }

        runBlocking {
            whenever(resolveShortcutSourceUseCase.invoke(any())).thenAnswer { invocation ->
                val source = invocation.getArgument<ShortcutSource>(0)
                if (source.id == "uuid-1") {
                    return@thenAnswer ShortcutResolutionResult.Success(
                        LaunchRequest(
                            Intent().setComponent(ComponentName("com.test", "com.test.Activity")),
                            source = LaunchSource.SAVED,
                        ),
                    )
                }

                if (source.intent != null && source.signature != null) {
                    if (source.signature == "valid_signature") {
                        return@thenAnswer ShortcutResolutionResult.Success(
                            LaunchRequest(
                                source.intent,
                                launcherPlugin = source.launcherPlugin,
                                source = LaunchSource.SHORTCUT,
                            ),
                        )
                    } else {
                        return@thenAnswer ShortcutResolutionResult.InvalidSignature
                    }
                }

                ShortcutResolutionResult.NotFound
            }
        }
    }

    @Test
    fun testStage3_LaunchShortcutFlow() {
        // Stage 3: Receive LAUNCH intent and start activity
        val componentName = ComponentName("com.test", "com.test.Activity")
        val extras = android.os.Bundle().apply { putString("key", "value") }
        val launchIntent = Intent().apply {
            component = componentName
            putExtras(extras)
        }
        val signature = "valid_signature"

        val intent = Intent(ShortcutCreator.INTENT_LAUNCH_SHORTCUT).apply {
            putExtra(ShortcutCreator.INTENT_EXTRA_INTENT, launchIntent.toUri(Intent.URI_INTENT_SCHEME))
            putExtra(ShortcutCreator.INTENT_EXTRA_SIGNATURE, signature)
            setClassName(ApplicationProvider.getApplicationContext(), ShortcutActivity::class.java.name)
        }

        ActivityScenario.launch<ShortcutActivity>(intent).use {
            val captor = argumentCaptor<LaunchRequest>()
            verify(activityLauncher).launchActivity(captor.capture(), anyOrNull())
            assertEquals("com.test", captor.firstValue.intent.component?.packageName)
            assertEquals("com.test.Activity", captor.firstValue.intent.component?.className)
            assertEquals("value", captor.firstValue.intent.extras?.getString("key"))
        }
    }

    @Test
    fun testStage3_LaunchRootShortcutFlow() {
        // Stage 3: Receive LAUNCH_ROOT intent and start activity (handled as normal)
        val componentName = ComponentName("com.test", "com.test.Activity")
        val launchIntent = Intent().apply { component = componentName }
        val signature = "valid_signature"

        whenever(intentSigner.validateRequestSignature(any(), eq(signature))).thenReturn(true)

        val intent = Intent(ShortcutCreator.INTENT_LAUNCH_ROOT_SHORTCUT).apply {
            putExtra(ShortcutCreator.INTENT_EXTRA_INTENT, launchIntent.toUri(Intent.URI_INTENT_SCHEME))
            putExtra(ShortcutCreator.INTENT_EXTRA_SIGNATURE, signature)
            setClassName(ApplicationProvider.getApplicationContext(), ShortcutActivity::class.java.name)
        }

        ActivityScenario.launch<ShortcutActivity>(intent).use {
            val captor = argumentCaptor<LaunchRequest>()
            verify(activityLauncher).launchActivity(captor.capture())
            assertEquals(componentName, captor.firstValue.intent.component)
        }
    }

    @Test
    fun testStage3_LaunchShortcutFlowInvalidSignatureRedirect() {
        // Stage 3: Redirect to MainActivity if signature mismatch
        val componentName = ComponentName("com.test", "com.test.Activity")
        val launchIntent = Intent().apply { component = componentName }
        val signature = "invalid_signature"

        val intent = Intent(ShortcutCreator.INTENT_LAUNCH_SHORTCUT).apply {
            putExtra(ShortcutCreator.INTENT_EXTRA_INTENT, launchIntent.toUri(Intent.URI_INTENT_SCHEME))
            putExtra(ShortcutCreator.INTENT_EXTRA_SIGNATURE, signature)
            setClassName(ApplicationProvider.getApplicationContext(), ShortcutActivity::class.java.name)
        }

        ActivityScenario.launch<ShortcutActivity>(intent).use {
            verify(activityLauncher, never()).launchActivity(any(), anyOrNull())
        }
    }

    @Test
    fun testStage2_CreateShortcutFlow() {
        // Stage 2: Receive CREATE intent
        val componentName = ComponentName("com.test", "com.test.Activity")
        val launchIntent = Intent().apply {
            component = componentName
            putExtra(ShortcutCreator.INTENT_EXTRA_SHORTCUT_ID, "uuid-1")
        }
        val icon = ActivityIcon.Resource(ApplicationProvider.getApplicationContext<android.content.Context>().packageName, android.R.drawable.sym_def_app_icon)

        val intent = Intent(ShortcutCreatorProxy.INTENT_CREATE_SHORTCUT).apply {
            putExtra(ShortcutCreator.INTENT_EXTRA_NAME, "Test App")
            putExtra(ShortcutCreator.INTENT_EXTRA_INTENT, launchIntent.toUri(Intent.URI_INTENT_SCHEME))
            putExtra(ShortcutCreator.INTENT_EXTRA_ICON, icon.toIconCompat(ApplicationProvider.getApplicationContext<Context>()).toBundle())
            setClassName(ApplicationProvider.getApplicationContext(), ShortcutActivity::class.java.name)
        }

        ActivityScenario.launch<ShortcutActivity>(intent).use { scenario ->
            // On API 26+, immediately after launch, the activity should NOT be finished
            // because we need it in the foreground for requestPinShortcut.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                scenario.onActivity { activity ->
                    assertFalse("Activity should not be finishing immediately on API 26+", activity.isFinishing)
                }
            }

            // On API 26+, there is a delay before finishing.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Thread.sleep(2000)
            }

            // Success if it reaches destroyed state (finishes)
            assert(scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED)

            val captor = argumentCaptor<ShortcutProxyRequest>()
            runBlocking { verify(shortcutCreator).createLauncherIcon(captor.capture(), eq("uuid-1"), anyOrNull()) }
            assertEquals("Test App", captor.firstValue.name)
            assertEquals(launchIntent.toUri(Intent.URI_INTENT_SCHEME), captor.firstValue.intent.toUri(Intent.URI_INTENT_SCHEME))
        }
    }

    @Test
    fun testLaunchActivityFlow() {
        val componentName = ComponentName("com.test", "com.test.Activity")
        val launchIntent = Intent().apply { component = componentName }

        val intent = Intent(ActivityLauncherProxy.INTENT_LAUNCH_ACTIVITY).apply {
            putExtra(ShortcutCreator.INTENT_EXTRA_INTENT, launchIntent.toUri(Intent.URI_INTENT_SCHEME))
            setClassName(ApplicationProvider.getApplicationContext(), ShortcutActivity::class.java.name)
        }

        ActivityScenario.launch<ShortcutActivity>(intent).use {
            val captor = argumentCaptor<LaunchRequest>()
            verify(activityLauncher).launchActivity(captor.capture(), anyOrNull())
            assertEquals(componentName, captor.firstValue.intent.component)
        }
    }

    @Test
    fun testLaunchShortcutDelegation() {
        val componentName = ComponentName("com.test", "com.test.Activity")
        val launchIntent = Intent().apply { component = componentName }
        val signature = "valid_signature"
        val launchPlugin = "com.plugin/.LaunchActivity"

        val intent = Intent(ShortcutCreator.INTENT_LAUNCH_SHORTCUT).apply {
            putExtra(ShortcutCreator.INTENT_EXTRA_INTENT, launchIntent.toUri(Intent.URI_INTENT_SCHEME))
            putExtra(ShortcutCreator.INTENT_EXTRA_SIGNATURE, signature)
            putExtra(ShortcutCreator.INTENT_EXTRA_LAUNCH_PLUGIN, launchPlugin)
            setClassName(ApplicationProvider.getApplicationContext(), ShortcutActivity::class.java.name)
        }

        ActivityScenario.launch<ShortcutActivity>(intent).use {
            verify(activityLauncher, never()).launchActivity(any(), anyOrNull())
            val captor = argumentCaptor<LaunchRequest>()
            verify(activityLauncherProxy).launchActivity(captor.capture(), anyOrNull())
            assertEquals(ComponentName.unflattenFromString(launchPlugin), captor.firstValue.launcherPlugin)
        }
    }

    @Test
    fun testStage3_LaunchSavedShortcutFlow() {
        val intent = Intent(ShortcutCreator.INTENT_LAUNCH_SHORTCUT).apply {
            putExtra(ShortcutCreator.INTENT_EXTRA_SHORTCUT_ID, "uuid-1")
            setClassName(ApplicationProvider.getApplicationContext(), ShortcutActivity::class.java.name)
        }

        ActivityScenario.launch<ShortcutActivity>(intent).use {
            val captor = argumentCaptor<LaunchRequest>()
            verify(activityLauncher).launchActivity(captor.capture(), anyOrNull())
            assertEquals("com.test", captor.firstValue.intent.component?.packageName)
            assertEquals("com.test.Activity", captor.firstValue.intent.component?.className)
        }
    }

    @Test
    fun testStage3_LaunchSavedShortcutFlowNotFound() {
        val intent = Intent(ShortcutCreator.INTENT_LAUNCH_SHORTCUT).apply {
            putExtra(ShortcutCreator.INTENT_EXTRA_SHORTCUT_ID, "uuid-not-found")
            setClassName(ApplicationProvider.getApplicationContext(), ShortcutActivity::class.java.name)
        }

        ActivityScenario.launch<ShortcutActivity>(intent).use {
            verify(activityLauncher, never()).launchActivity(any(), anyOrNull())
        }
    }
}
