package de.szalkowski.activitylauncher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionManifestTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager: PackageManager = context.packageManager

    @Test
    fun protectedAliasesUseTheDistributionPermissionNamespace() {
        val declaredNames =
            listOf(
                "de.szalkowski.activitylauncher",
                "de.szalkowski.activitylauncher.oss",
            ).map { "$it.permission.CREATE_SHORTCUT" }
                .mapNotNull { permissionName ->
                    try {
                        packageManager.getPermissionInfo(permissionName, 0)
                    } catch (_: PackageManager.NameNotFoundException) {
                        null
                    }
                }

        // Exactly one distribution declares permissions in a given APK.
        assertEquals(1, declaredNames.size)
        val namespace = declaredNames.single().name.substringBeforeLast(".permission.")

        listOf("CREATE_SHORTCUT" to "CreateShortcutActivity", "LAUNCH_ACTIVITY" to "LaunchActivity")
            .forEach { (capability, alias) ->
                val permissionName = "$namespace.permission.$capability"
                val info = packageManager.getPermissionInfo(permissionName, 0)

                assertEquals(
                    PermissionInfo.PROTECTION_SIGNATURE,
                    info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE,
                )
                assertEquals(
                    permissionName,
                    packageManager.getActivityInfo(
                        ComponentName(context.packageName, "${context.packageName}.$alias"),
                        0,
                    ).permission,
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // These members are @SystemApi and absent from the SDK stubs, so inspect them
                    // reflectively on devices where the platform defines them.
                    val knownSignerFlag =
                        PermissionInfo::class.java.getField("PROTECTION_FLAG_KNOWN_SIGNER").getInt(null)
                    assertTrue(info.protectionLevel and knownSignerFlag != 0)

                    @Suppress("UNCHECKED_CAST")
                    val knownCerts =
                        PermissionInfo::class.java.getField("knownCerts").get(info) as? Set<String>
                    assertNotNull(knownCerts)
                    assertTrue(
                        knownCerts.orEmpty().contains(
                            "76ADD486A275E14096D7C9AABE25A5266D19C761B9427B60BB596F79A03ED4F7",
                        ),
                    )
                }
            }
    }
}
