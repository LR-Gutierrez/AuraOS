package com.beta.auraos.util

import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView

object BottomNavIndicator {

    private val TAB_ORDER = listOf(
        com.beta.auraos.R.id.nav_dashboard,
        com.beta.auraos.R.id.nav_register,
        com.beta.auraos.R.id.nav_analytics,
        com.beta.auraos.R.id.nav_profile,
    )

    fun attach(
        bottomNav: BottomNavigationView,
        indicator: View,
        initialItemId: Int,
    ) {
        bottomNav.post {
            val itemCount = bottomNav.menu.size()
            val itemWidth = bottomNav.width / itemCount
            val indicatorWidth = indicator.width
            val index = TAB_ORDER.indexOf(initialItemId).coerceAtLeast(0)
            indicator.translationX = index * itemWidth + (itemWidth - indicatorWidth) / 2f
            indicator.visibility = View.VISIBLE
        }
    }

    fun animateTo(
        bottomNav: BottomNavigationView,
        indicator: View,
        itemId: Int,
    ) {
        val itemCount = bottomNav.menu.size()
        val itemWidth = bottomNav.width / itemCount
        val indicatorWidth = indicator.width
        val index = TAB_ORDER.indexOf(itemId).coerceAtLeast(0)
        val targetX = index * itemWidth + (itemWidth - indicatorWidth) / 2f

        indicator.animate()
            .translationX(targetX)
            .setDuration(250)
            .start()
    }
}
