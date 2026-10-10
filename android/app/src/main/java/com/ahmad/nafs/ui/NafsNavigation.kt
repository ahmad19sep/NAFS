package com.ahmad.nafs.ui

import androidx.navigation.NavHostController

internal fun NavHostController.navigateTopDestination(route: String) {
    navigate(route) {
        popUpTo("dashboard") { saveState = true }
        launchSingleTop = true
        // Restoring Home would also restore its last child screen, such as a report.
        restoreState = route != "dashboard"
    }
}
