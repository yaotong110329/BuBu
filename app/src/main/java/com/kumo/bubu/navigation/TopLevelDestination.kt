package com.kumo.bubu.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.kumo.bubu.R

enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    DASHBOARD("dashboard", R.string.dashboard_title, R.drawable.ic_dashboard),
    REPORTS("reports", R.string.reports_title, R.drawable.ic_reports),
    SETTINGS("settings", R.string.settings_title, R.drawable.ic_settings),
}
