package de.szalkowski.activitylauncher.domain.usecase.packages

import androidx.core.graphics.drawable.IconCompat
import de.szalkowski.activitylauncher.domain.launcher.IconLoader
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GetPackageIconUseCaseTest {
    private val iconLoader: IconLoader = mock()
    private lateinit var useCase: GetPackageIconUseCase
    private val loadedIcon: IconCompat = mock()
    private val fallbackIcon: IconCompat = mock()

    @Before
    fun setup() {
        useCase = GetPackageIconUseCase(iconLoader)
    }

    @Test
    fun `should return the icon loaded from the resource name`() {
        whenever(iconLoader.tryGetIcon("package/icon")).thenReturn(Result.success(loadedIcon))

        val result = useCase.invoke("package/icon", "com.test")

        assertEquals(loadedIcon, result)
        verify(iconLoader, never()).getPackageIcon(any())
    }

    @Test
    fun `should fall back to the package icon when the resource lookup fails`() {
        whenever(iconLoader.tryGetIcon("package/missing"))
            .thenReturn(Result.failure(IconLoader.NullResourceException()))
        whenever(iconLoader.getPackageIcon("com.test")).thenReturn(fallbackIcon)

        val result = useCase.invoke("package/missing", "com.test")

        assertEquals(fallbackIcon, result)
        verify(iconLoader).getPackageIcon("com.test")
    }

    @Test
    fun `should skip the lookup entirely when no resource name is given`() {
        whenever(iconLoader.getPackageIcon("com.test")).thenReturn(fallbackIcon)

        val result = useCase.invoke(null, "com.test")

        assertEquals(fallbackIcon, result)
        verify(iconLoader, never()).tryGetIcon(any())
    }
}
