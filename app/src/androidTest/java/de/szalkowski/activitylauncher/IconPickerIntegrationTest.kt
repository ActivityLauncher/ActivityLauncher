package de.szalkowski.activitylauncher

import android.content.ComponentName
import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import de.szalkowski.activitylauncher.app.di.CoreServicesModule
import de.szalkowski.activitylauncher.domain.external.ActivitySharer
import de.szalkowski.activitylauncher.domain.favorites.FavoritesRepository
import de.szalkowski.activitylauncher.domain.launcher.*
import de.szalkowski.activitylauncher.domain.model.*
import de.szalkowski.activitylauncher.domain.packages.PackageRepository
import de.szalkowski.activitylauncher.domain.recents.RecentsRepository
import de.szalkowski.activitylauncher.domain.settings.BackupRepository
import de.szalkowski.activitylauncher.domain.settings.SettingsRepository
import de.szalkowski.activitylauncher.domain.shortcuts.ShortcutsRepository
import de.szalkowski.activitylauncher.domain.usecase.launcher.GetActivityIconUseCase
import de.szalkowski.activitylauncher.domain.usecase.packages.GetPackageIconUseCase
import de.szalkowski.activitylauncher.entrypoint.MainActivity
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Matchers
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import javax.inject.Inject

@HiltAndroidTest
@UninstallModules(CoreServicesModule::class)
@RunWith(AndroidJUnit4::class)
class IconPickerIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val screenshotTestRule = ScreenshotTestRule()

    @get:Rule
    val disableAnimationsRule = DisableAnimationsRule()

    @BindValue
    val activityLauncher: ActivityLauncher = mock()

    @BindValue
    val activityLauncherProxy: ActivityLauncherProxy = mock()

    @BindValue
    val shortcutCreator: ShortcutCreator = mock()

    @BindValue
    val shortcutCreatorProxy: ShortcutCreatorProxy = mock()

    @BindValue
    val packageRepository: PackageRepository = mock()

    @BindValue
    val iconLoader: IconLoader = mock()

    @BindValue
    val activitySharer: ActivitySharer = mock()

    @BindValue
    val intentSigner: IntentSigner = mock()

    @BindValue
    val viewIntentParser: ViewIntentParser = mock()

    @BindValue
    val settingsRepository: SettingsRepository = mock()

    @BindValue
    val backupRepository: BackupRepository = mock()

    @BindValue
    val favoritesRepository: FavoritesRepository = mock()

    @BindValue
    val recentsRepository: RecentsRepository = mock()

    @BindValue
    val shortcutsRepository: ShortcutsRepository = mock()

    @BindValue
    val getActivityIconUseCase: GetActivityIconUseCase = mock()

    @BindValue
    val getPackageIconUseCase: GetPackageIconUseCase = mock()

    @Inject
    lateinit var systemRepository: FakeSystemPackageRepository

    private val testIcons = listOf(
        IconInfo("com.example:drawable/icon_apple"),
        IconInfo("com.example:drawable/icon_banana"),
        IconInfo("com.example:drawable/icon_cherry"),
    )

    @Before
    fun setup() {
        hiltRule.inject()
        whenever(settingsRepository.disclaimerAccepted).thenReturn(true)
        whenever(favoritesRepository.getFavorites()).thenReturn(emptySet())
        whenever(favoritesRepository.getFavoritesFlow()).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(emptyList()))
        whenever(recentsRepository.getRecentActivities()).thenReturn(emptyList())
        whenever(recentsRepository.getRecentsFlow()).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(emptyList()))

        val icon = ActivityIcon.Resource(ApplicationProvider.getApplicationContext<android.content.Context>().packageName, android.R.drawable.sym_def_app_icon)
        whenever(getPackageIconUseCase(anyOrNull(), any())).thenReturn(icon)
        whenever(getActivityIconUseCase(anyOrNull(), any())).thenReturn(icon)
        whenever(iconLoader.getIcon(any<String>())).thenReturn(icon)
        whenever(iconLoader.loadIcons(anyOrNull())).thenReturn(testIcons)

        systemRepository.clear()

        val componentName = ComponentName("de.szalkowski.activitylauncher", "de.szalkowski.activitylauncher.entrypoint.SettingsActivity")
        val pkg = SystemPackage("de.szalkowski.activitylauncher", "Android Test App", "1.0 (1)", null)
        val activities = listOf(
            MyActivityInfo(componentName, "Settings Activity", null, isPrivate = false, isDefault = true),
        )
        systemRepository.addPackage(pkg, activities)

        val activityNames = activities.map {
            de.szalkowski.activitylauncher.domain.model.ActivityName(
                name = it.name,
                shortCls = it.componentName.className.substringAfterLast('.'),
                fullCls = it.componentName.className,
                isPrivate = it.isPrivate,
                iconResourceName = it.iconResourceName,
            )
        }

        val myPackageInfo = de.szalkowski.activitylauncher.domain.model.MyPackageInfo(
            id = 1L,
            packageName = pkg.packageName,
            name = pkg.name,
            version = pkg.version,
            defaultActivityName = activityNames[0],
            activityNames = activityNames,
            iconResourceName = pkg.iconResourceName,
        )
        whenever(packageRepository.packagesFlow).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(listOf(myPackageInfo)))
        whenever(packageRepository.isSyncing).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(false))
        whenever(packageRepository.isLoaded).thenReturn(true)

        whenever(packageRepository.getActivity(eq(componentName))).thenReturn(activities[0])
        whenever(packageRepository.getActivities(eq(pkg.packageName))).thenReturn(de.szalkowski.activitylauncher.domain.model.PackageActivities(pkg.packageName, pkg.name, activities[0], activities))
    }

    @Test
    fun testIconFiltering() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use {
            TestUtils.dismissSystemDialogs()
            TestUtils.waitForWindowFocus()

            // Navigate to ActivityDetails
            Thread.sleep(5000)
            onView(withId(R.id.PackageListFragment)).perform(click())
            Thread.sleep(2000)
            onView(withId(R.id.rvPackages)).check(matches(isDisplayed()))
            onView(withId(R.id.rvPackages))
                .perform(RecyclerViewActions.actionOnItemAtPosition<androidx.recyclerview.widget.RecyclerView.ViewHolder>(0, click()))
            Thread.sleep(2000)
            onView(withId(R.id.rvActivities))
                .perform(RecyclerViewActions.actionOnItemAtPosition<androidx.recyclerview.widget.RecyclerView.ViewHolder>(0, click()))
            Thread.sleep(2000)

            // Open Icon Picker
            onView(withId(R.id.ibIconPicker)).perform(scrollTo(), click())
            Thread.sleep(1000)
            onView(withText(R.string.action_pick_icon_library)).perform(click())
            Thread.sleep(2000)

            // Verify search view is displayed
            onView(Matchers.allOf<View>(withId(R.id.tiSearch), isDisplayed())).check(matches(isDisplayed()))

            // Verify initial count
            onView(withId(R.id.rvIcons)).check(matches(hasMinimumChildCount(3)))

            // Filter for "apple"
            onView(Matchers.allOf<View>(withId(R.id.tiSearch), isDisplayed()))
                .perform(click()) // Ensure focus
            onView(Matchers.allOf<View>(withId(R.id.tiSearch), isDisplayed()))
                .perform(typeText("apple"))

            Thread.sleep(2000)
            onView(withId(R.id.rvIcons)).check(matches(hasMinimumChildCount(1)))

            // Filter for "a" (apple and banana should match)
            onView(Matchers.allOf<View>(withId(R.id.tiSearch), isDisplayed()))
                .perform(replaceText("a"))

            Thread.sleep(2000)
            onView(withId(R.id.rvIcons)).check(matches(hasMinimumChildCount(2)))

            // Clear filter
            onView(org.hamcrest.Matchers.allOf<View>(withId(R.id.tiSearch), isDisplayed()))
                .perform(replaceText(""))

            Thread.sleep(2000)
            onView(withId(R.id.rvIcons)).check(matches(hasMinimumChildCount(3)))
        }
    }
}
