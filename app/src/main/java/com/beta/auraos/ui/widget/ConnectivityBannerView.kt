package com.beta.auraos.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.beta.auraos.R

class ConnectivityBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val bannerContent: LinearLayout
    private val bannerText: TextView
    private var isOffline = false
    private var heightAnimator: ValueAnimator? = null

    private val BANNER_HEIGHT_PX: Int
    private val offlineColor: Int
    private val onlineColor: Int

    init {
        inflate(context, R.layout.view_connectivity_banner, this)
        bannerContent = findViewById(R.id.banner_content)
        bannerText = findViewById(R.id.banner_text)

        BANNER_HEIGHT_PX = resources.getDimensionPixelSize(R.dimen.connectivity_banner_height)

        offlineColor = resolveThemeColor(R.attr.connectivityOfflineCustom, android.graphics.Color.parseColor("#272727"))
        onlineColor = resolveThemeColor(R.attr.connectivityOnlineCustom, android.graphics.Color.parseColor("#2E7D32"))

        ViewCompat.setOnApplyWindowInsetsListener(bannerContent) { v, insets ->
            val bottomInset = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bottomInset)
            insets
        }

        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0
        )
    }

    fun showOffline() {
        removeCallbacks(hideRunnable)
        heightAnimator?.cancel()

        visibility = VISIBLE
        bannerContent.setBackgroundColor(offlineColor)
        bannerText.text = context.getString(R.string.connectivity_offline_text)

        if (layoutParams.height < BANNER_HEIGHT_PX) {
            animateHeight(layoutParams.height, BANNER_HEIGHT_PX)
        }
        isOffline = true
    }

    fun showOnline() {
        if (!isOffline) return

        removeCallbacks(hideRunnable)
        heightAnimator?.cancel()

        visibility = VISIBLE
        bannerContent.setBackgroundColor(onlineColor)
        bannerText.text = context.getString(R.string.connectivity_online_text)

        isOffline = false

        if (layoutParams.height < BANNER_HEIGHT_PX) {
            animateHeight(layoutParams.height, BANNER_HEIGHT_PX)
            postDelayed(hideRunnable, 2900)
        } else {
            postDelayed(hideRunnable, 2500)
        }
    }

    fun cleanup() {
        removeCallbacks(hideRunnable)
        heightAnimator?.cancel()
        visibility = GONE
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0
        )
    }

    private fun animateHeight(from: Int, to: Int) {
        heightAnimator?.cancel()
        heightAnimator = ValueAnimator.ofInt(from, to).apply {
            addUpdateListener { anim ->
                val lp = layoutParams
                lp.height = anim.animatedValue as Int
                layoutParams = lp
            }
            duration = 400
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private val hideRunnable = Runnable {
        animateHeight(layoutParams.height, 0)
    }

    private fun resolveThemeColor(attr: Int, default: Int): Int {
        val ta = context.obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, default)
        ta.recycle()
        return color
    }
}
