package com.beta.auraos.util

import android.app.Activity
import android.content.Intent
import com.beta.auraos.AnalyticsActivity
import com.beta.auraos.DashboardActivity
import com.beta.auraos.HomeActivity
import com.beta.auraos.MapActivity
import com.beta.auraos.ProfileActivity
import com.beta.auraos.R

object TabNavigator {

    const val EXTRA_TAB_ID = "tab_id"

    private val TAB_ORDER = listOf(
        R.id.nav_dashboard,
        R.id.nav_register,
        R.id.nav_map,
        R.id.nav_analytics,
        R.id.nav_profile,
    )

    private val TAB_CLASS = mapOf(
        R.id.nav_dashboard to DashboardActivity::class.java,
        R.id.nav_register to HomeActivity::class.java,
        R.id.nav_map to MapActivity::class.java,
        R.id.nav_analytics to AnalyticsActivity::class.java,
        R.id.nav_profile to ProfileActivity::class.java,
    )

    private fun positionOf(itemId: Int): Int = TAB_ORDER.indexOf(itemId)

    fun navigate(activity: Activity, targetItemId: Int) {
        val targetClass = TAB_CLASS[targetItemId] ?: return
        val currentPos = positionOf(activity.intent.getIntExtra(EXTRA_TAB_ID, R.id.nav_dashboard))
        val targetPos = positionOf(targetItemId)

        val intent = Intent(activity, targetClass).putExtra(EXTRA_TAB_ID, targetItemId)

        activity.startActivity(intent)
        activity.finish()

        if (targetPos > currentPos) {
            activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } else {
            activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }
}
