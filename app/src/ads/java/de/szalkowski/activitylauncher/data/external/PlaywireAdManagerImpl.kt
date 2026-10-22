package de.szalkowski.activitylauncher.data.external

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.net.toUri
import com.intergi.playwiresdk.PlaywireSDK
import com.intergi.playwiresdk.ads.PWAdError
import com.intergi.playwiresdk.ads.view.PWViewAd
import com.intergi.playwiresdk.ads.view.banner.PWBannerView
import com.intergi.playwiresdk.logger.LogLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.szalkowski.activitylauncher.BuildConfig
import de.szalkowski.activitylauncher.R
import de.szalkowski.activitylauncher.domain.external.AdManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaywireAdManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AdManager {
    private var isInitialized = false

    companion object {
        private const val FALLBACK_BANNER_TAG = "fallback_banner"
    }

    override fun loadBanner(activity: Activity, container: ViewGroup) {
        // Show fallback immediately
        showFallbackBanner(activity, container)

        if (isInitialized) {
            setupBanner(activity, container)
            return
        }

        val publisherId = context.getString(R.string.publisher_id)
        val appId = context.getString(R.string.app_id)

        if (publisherId.isEmpty() || appId.isEmpty()) {
            return
        }

        if (BuildConfig.DEBUG) {
            PlaywireSDK.setLogLevel(LogLevel.INFO)
        }

        PlaywireSDK.start(publisherId, appId, activity) { success, _ ->
            if (success) {
                isInitialized = true
                setupBanner(activity, container)
            }
        }
    }

    override fun removeBanner(container: ViewGroup) {
        container.visibility = View.GONE

        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is PWBannerView) {
                child.destroy()
            }
        }
        container.removeAllViews()
    }

    private fun setupBanner(context: Context, container: ViewGroup) {
        var banner: PWBannerView? = null
        val listener = object : PWViewAd.Listener {
            override fun onViewAdLoaded(ad: PWViewAd) {
                // Remove fallback and show ad
                container.findViewWithTag<View>(FALLBACK_BANNER_TAG)?.let {
                    container.removeView(it)
                }
                banner?.visibility = View.VISIBLE
            }

            override fun onViewAdFailedToLoad(ad: PWViewAd, error: PWAdError) {
                // Remove the failed ad view, fallback stays
                banner?.let {
                    container.removeView(it)
                    it.destroy()
                }
            }

            override fun onViewAdOpened(ad: PWViewAd) {}
            override fun onViewAdClosed(ad: PWViewAd) {}
            override fun onViewAdClicked(ad: PWViewAd) {}
            override fun onViewAdImpression(ad: PWViewAd) {}
        }

        val layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        )

        banner = PWBannerView(context, "banner-320x50", listener)
        banner.visibility = View.GONE
        container.addView(banner, layoutParams)
        banner.load()
    }

    private fun showFallbackBanner(activity: Activity, container: ViewGroup) {
        val existing = container.findViewWithTag<View>(FALLBACK_BANNER_TAG)
        if (existing != null) {
            existing.setOnClickListener {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, context.getString(R.string.url_pro).toUri())
                    activity.startActivity(intent)
                }
            }
            return
        }

        val banner = ImageView(activity).apply {
            tag = FALLBACK_BANNER_TAG
            setImageResource(R.drawable.getpro_banner)
            setOnClickListener {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, context.getString(R.string.url_pro).toUri())
                    activity.startActivity(intent)
                }
            }
        }

        val layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        )

        container.addView(banner, layoutParams)
        container.visibility = View.VISIBLE
    }
}
