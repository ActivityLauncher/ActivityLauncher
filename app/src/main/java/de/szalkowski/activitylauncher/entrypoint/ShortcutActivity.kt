package de.szalkowski.activitylauncher.entrypoint

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.szalkowski.activitylauncher.R
import de.szalkowski.activitylauncher.domain.launcher.ActivityLauncher
import de.szalkowski.activitylauncher.domain.launcher.ActivityLauncherProxy
import de.szalkowski.activitylauncher.domain.launcher.ShortcutCreator
import de.szalkowski.activitylauncher.domain.launcher.ShortcutCreatorProxy
import de.szalkowski.activitylauncher.domain.launcher.ViewIntentParser
import de.szalkowski.activitylauncher.domain.model.LaunchRequest
import de.szalkowski.activitylauncher.domain.model.LaunchSource
import de.szalkowski.activitylauncher.domain.model.ShortcutResolutionResult
import de.szalkowski.activitylauncher.domain.usecase.launcher.CreateShortcutUseCase
import de.szalkowski.activitylauncher.domain.usecase.launcher.LaunchActivityUseCase
import de.szalkowski.activitylauncher.domain.usecase.launcher.ResolveShortcutSourceUseCase
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ShortcutActivity : AppCompatActivity() {
    @Inject
    internal lateinit var viewIntentParser: ViewIntentParser

    @Inject
    internal lateinit var launchActivityUseCase: LaunchActivityUseCase

    @Inject
    internal lateinit var activityLauncher: ActivityLauncher

    @Inject
    internal lateinit var shortCutCreator: ShortcutCreator

    @Inject
    internal lateinit var createShortcutUseCase: CreateShortcutUseCase

    @Inject
    internal lateinit var resolveShortcutSourceUseCase: ResolveShortcutSourceUseCase

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            when (intent.action) {
                ShortcutCreator.INTENT_LAUNCH_SHORTCUT,
                ShortcutCreator.INTENT_LAUNCH_ROOT_SHORTCUT,
                -> {
                    lifecycleScope.launch {
                        handleLaunchShortcut()
                        finish()
                    }
                }

                ShortcutCreatorProxy.INTENT_CREATE_SHORTCUT -> {
                    handleCreateShortcut()
                    // On API 26+, requestPinShortcut requires the activity to be in the foreground.
                    // If we finish() immediately, the request might fail.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        finish()
                    }
                }

                ActivityLauncherProxy.INTENT_LAUNCH_ACTIVITY -> {
                    handleLaunchActivity()
                    finish()
                }

                else -> finish()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if ((intent.action == ShortcutCreatorProxy.INTENT_CREATE_SHORTCUT) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
            // Give the system a moment to process the pinning request before finishing
            window.decorView.postDelayed(
                {
                    if (!isFinishing) finish()
                },
                1000,
            )
        }
    }

    private fun redirectToMain(launchRequest: LaunchRequest) {
        val mainIntent = Intent(this, MainActivity::class.java)
        mainIntent.putExtra(MainActivity.EXTRA_SHORTCUT_LAUNCH_REDIRECT, launchRequest)
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(mainIntent)
    }

    private suspend fun handleLaunchShortcut() {
        val source = viewIntentParser.parseLaunchSource(intent)
        if (source == null) {
            Toast.makeText(this, R.string.error_invalid_activity_link, Toast.LENGTH_SHORT).show()
            return
        }

        when (val result = resolveShortcutSourceUseCase(source)) {
            is ShortcutResolutionResult.Success -> {
                launchActivityUseCase.invoke(result.request, this)
            }

            ShortcutResolutionResult.InvalidSignature -> {
                Toast.makeText(this, R.string.error_invalid_signature, Toast.LENGTH_SHORT).show()
                source.intent?.let {
                    val launchRequest = LaunchRequest(
                        intent = it,
                        launcherPlugin = source.launcherPlugin,
                        source = LaunchSource.SHORTCUT,
                    )
                    redirectToMain(launchRequest)
                }
            }

            ShortcutResolutionResult.NotFound -> {
                Toast.makeText(this, R.string.error_shortcut_not_found, Toast.LENGTH_SHORT).show()
                source.intent?.let {
                    val launchRequest = LaunchRequest(
                        intent = it,
                        launcherPlugin = source.launcherPlugin,
                        source = LaunchSource.SHORTCUT,
                    )
                    redirectToMain(launchRequest)
                }
            }
        }
    }

    private fun handleLaunchActivity() {
        val request = viewIntentParser.parseLaunchRequest(intent)
        if (request == null) {
            Toast.makeText(this, R.string.error_invalid_activity_link, Toast.LENGTH_SHORT).show()
            return
        }
        launchActivityUseCase.invoke(request, this)
    }

    private fun handleCreateShortcut() {
        val shortcutProxyRequest = viewIntentParser.parseShortcutRequest(intent)
        if (shortcutProxyRequest == null) {
            Toast.makeText(this, R.string.error_invalid_activity_link, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val shortcutId = shortcutProxyRequest.intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_SHORTCUT_ID)

            if (shortcutId != null) {
                shortCutCreator.createLauncherIcon(shortcutProxyRequest, shortcutId)
            } else {
                // Fallback for intents that don't have the full AL metadata (unlikely with new protocol)
                // but we don't have ShortcutRequest here anymore in ShortcutActivity if it comes from a plugin.
                // However, plugins SHOULD send the full intent they received.
                Toast.makeText(this@ShortcutActivity, R.string.error_invalid_activity_link, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
