package de.szalkowski.activitylauncher.domain.usecase.launcher

import android.content.ComponentName
import androidx.core.graphics.drawable.IconCompat
import de.szalkowski.activitylauncher.domain.launcher.IconLoader
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GetActivityIconUseCaseTest {
    private val iconLoader: IconLoader = mock()
    private lateinit var useCase: GetActivityIconUseCase
    private val loadedIcon: IconCompat = mock()
    private val fallbackIcon: IconCompat = mock()
    private val componentName = mock<ComponentName> {
        on { packageName } doReturn "com.test"
        on { className } doReturn "Activity"
    }

    @Before
    fun setup() {
        useCase = GetActivityIconUseCase(iconLoader)
    }

    @Test
    fun `should return the icon loaded from the resource name`() {
        whenever(iconLoader.tryGetIcon("package/icon")).thenReturn(Result.success(loadedIcon))

        val result = useCase.invoke("package/icon", componentName)

        assertEquals(loadedIcon, result)
        verify(iconLoader, never()).getIcon(any())
    }

    @Test
    fun `should fall back to the activity icon when the resource lookup fails`() {
        whenever(iconLoader.tryGetIcon("package/missing"))
            .thenReturn(Result.failure(IconLoader.NullResourceException()))
        whenever(iconLoader.getIcon(componentName)).thenReturn(fallbackIcon)

        val result = useCase.invoke("package/missing", componentName)

        assertEquals(fallbackIcon, result)
        verify(iconLoader).getIcon(componentName)
    }

    @Test
    fun `should skip the lookup entirely when no resource name is given`() {
        whenever(iconLoader.getIcon(componentName)).thenReturn(fallbackIcon)

        val result = useCase.invoke(null, componentName)

        assertEquals(fallbackIcon, result)
        verify(iconLoader, never()).tryGetIcon(any())
    }
}
