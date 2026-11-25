package de.szalkowski.activitylauncher.data.launcher

import android.content.ComponentName
import android.content.Intent
import de.szalkowski.activitylauncher.domain.launcher.ShortcutCreator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.*

class ViewIntentParserImplTest {
    private lateinit var parser: ViewIntentParserImpl

    @Before
    fun setup() {
        parser = ViewIntentParserImpl()
    }

    private fun withMockedIntent(block: () -> Unit) {
        mockStatic(Intent::class.java).use { mockedIntent ->
            mockedIntent.`when`<Intent> {
                Intent.parseUri(anyString(), anyInt())
            }.thenAnswer { invocation ->
                val uri = invocation.arguments[0] as String
                val intent = mock<Intent>()
                if (uri.contains("component=com.test/.Activity")) {
                    val componentName = mock<ComponentName>()
                    whenever(componentName.packageName).thenReturn("com.test")
                    whenever(componentName.className).thenReturn(".Activity")
                    whenever(intent.component).thenReturn(componentName)
                }
                intent
            }
            block()
        }
    }

    @Test
    fun testParseLaunchRequest_RootShortcut() = withMockedIntent {
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn(ShortcutCreator.INTENT_LAUNCH_ROOT_SHORTCUT)
        whenever(intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_INTENT))
            .thenReturn("intent:#Intent;component=com.test/.Activity;end")

        val request = parser.parseLaunchRequest(intent)

        assertNotNull(request)
        assertEquals("com.test", request?.intent?.component?.packageName)
        assertEquals(".Activity", request?.intent?.component?.className)
    }

    @Test
    fun testComponentNameFromIntent_RootShortcut() = withMockedIntent {
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn(ShortcutCreator.INTENT_LAUNCH_ROOT_SHORTCUT)
        whenever(intent.getStringExtra(ShortcutCreator.INTENT_EXTRA_INTENT))
            .thenReturn("intent:#Intent;component=com.test/.Activity;end")

        val componentName = parser.componentNameFromIntent(intent)

        assertNotNull(componentName)
        assertEquals("com.test", componentName?.packageName)
        assertEquals(".Activity", componentName?.className)
    }
}
