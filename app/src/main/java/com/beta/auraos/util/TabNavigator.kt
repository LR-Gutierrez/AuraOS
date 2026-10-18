package com.beta.auraos.util

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.core.view.doOnLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.beta.auraos.AnalyticsActivity
import com.beta.auraos.DashboardActivity
import com.beta.auraos.HomeActivity
import com.beta.auraos.ProfileActivity
import com.beta.auraos.R
import com.beta.auraos.databinding.ViewPcTabBarBinding
import com.beta.auraos.ui.audit.AuditActivity

object TabNavigator {

    const val EXTRA_TAB_ID = "tab_id"

    private val TAB_ORDER = listOf(
        R.id.nav_dashboard,
        R.id.nav_register,
        R.id.nav_cash,
        R.id.nav_analytics,
        R.id.nav_profile,
    )

    private val TAB_CLASS = mapOf(
        R.id.nav_dashboard to DashboardActivity::class.java,
        R.id.nav_register to HomeActivity::class.java,
        R.id.nav_cash to AuditActivity::class.java,
        R.id.nav_analytics to AnalyticsActivity::class.java,
        R.id.nav_profile to ProfileActivity::class.java,
    )

    private fun positionOf(itemId: Int): Int = TAB_ORDER.indexOf(itemId)

    /**
     * Wires the shared tab bar: marks [currentItemId] as selected, shows the amber indicator
     * under it, pads the bar above the system navigation bar and routes the other tabs.
     */
    fun setup(activity: Activity, tabBar: ViewPcTabBarBinding, currentItemId: Int) {
        val bottomNav = tabBar.bottomNavigation
        bottomNav.selectedItemId = currentItemId
        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId != currentItemId) navigate(activity, item.itemId, currentItemId)
            true
        }
        bottomNav.setOnItemReselectedListener { }
        ViewCompat.setOnApplyWindowInsetsListener(tabBar.root) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBar.bottom)
            insets
        }
        tabBar.root.doOnLayout { placeIndicator(tabBar, currentItemId) }
    }

    /** 14x3dp bar 5dp under the active icon, fading and widening in like BCV Tracker's NavTab. */
    private fun placeIndicator(tabBar: ViewPcTabBarBinding, itemId: Int) {
        val item = tabBar.bottomNavigation.findViewById<View>(itemId) ?: return
        val icon = item.findViewById<View>(com.google.android.material.R.id.navigation_bar_item_icon_view) ?: item
        val indicator = tabBar.tabIndicator
        val root = IntArray(2).also { tabBar.root.getLocationInWindow(it) }
        val at = IntArray(2).also { icon.getLocationInWindow(it) }
        val gap = 5 * tabBar.root.resources.displayMetrics.density
        indicator.translationX = at[0] - root[0] + (icon.width - indicator.width) / 2f
        indicator.translationY = at[1] - root[1] + icon.height + gap
        indicator.alpha = 0f
        indicator.scaleX = 0f
        indicator.visibility = View.VISIBLE
        indicator.animate().alpha(1f).scaleX(1f).setDuration(200).start()
    }

    fun navigate(activity: Activity, targetItemId: Int, currentItemId: Int? = null) {
        val targetClass = TAB_CLASS[targetItemId] ?: return
        open(activity, Intent(activity, targetClass), targetItemId, currentItemId)
    }

    /**
     * Replaces the current tab screen with [intent] (e.g. Registro in its Salida half),
     * sliding in the direction of [targetItemId].
     */
    fun open(activity: Activity, intent: Intent, targetItemId: Int, currentItemId: Int? = null) {
        val currentPos = positionOf(
            currentItemId ?: activity.intent.getIntExtra(EXTRA_TAB_ID, R.id.nav_dashboard)
        )
        val targetPos = positionOf(targetItemId)

        intent.putExtra(EXTRA_TAB_ID, targetItemId)
        activity.startActivity(intent)
        activity.finish()

        if (targetPos == currentPos) {
            activity.overridePendingTransition(0, 0)
        } else if (targetPos > currentPos) {
            activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } else {
            activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }
}
