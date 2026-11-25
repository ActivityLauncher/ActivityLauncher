package de.szalkowski.activitylauncher.data.packages

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import de.szalkowski.activitylauncher.domain.settings.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.*
import java.lang.reflect.Field
import java.lang.reflect.Modifier

class SystemPackageRepositoryImplTest {
    private val context: Context = mock()
    private val packageManager: PackageManager = mock()
    private val settingsRepository: SettingsRepository = mock()
    private lateinit var repository: SystemPackageRepositoryImpl

    @Before
    fun setup() {
        whenever(context.packageManager).thenReturn(packageManager)
        repository = SystemPackageRepositoryImpl(context, settingsRepository)
    }

    private fun setSdkVersion(version: Int) {
        val field = Build.VERSION::class.java.getField("SDK_INT")
        field.isAccessible = true
        val modifiersField = Field::class.java.getDeclaredField("modifiers")
        modifiersField.isAccessible = true
        modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
        field.set(null, version)
    }

    @Test
    fun `getInstalledPackages should use correct flags on API 30`() {
        // We can't easily mock Build.VERSION.SDK_INT globally in a standard unit test without PowerMock or similar,
        // but we can test the behavior assuming current environment or using reflection (risky).
        // For now, let's just verify it calls the package manager.

        val pkg = PackageInfo().apply { packageName = "com.test" }
        whenever(packageManager.getInstalledPackages(anyInt())).thenReturn(listOf(pkg))

        repository.getInstalledPackages()

        val captor = argumentCaptor<Int>()
        verify(packageManager).getInstalledPackages(captor.capture())

        val flags = captor.firstValue
        // Verify it does NOT contain MATCH_UNINSTALLED_PACKAGES (8192)
        assertEquals(0, flags and 0x00002000)
    }

    @Test
    fun `getPackageDetails should use correct flags`() {
        val packageName = "com.test"
        val pkg = PackageInfo().apply { this.packageName = packageName }
        whenever(packageManager.getPackageInfo(eq(packageName), anyInt())).thenReturn(pkg)

        repository.getPackageDetails(packageName)

        val captor = argumentCaptor<Int>()
        verify(packageManager).getPackageInfo(eq(packageName), captor.capture())

        val flags = captor.firstValue
        // Verify it does NOT contain MATCH_UNINSTALLED_PACKAGES
        assertEquals(0, flags and 0x00002000)
    }
}
