// NavScreens.kt
package com.example.mobileappstrusted.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home

import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavScreen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home : NavScreen(
        route = "home",
        label = "Home",
        icon = Icons.Default.Home
    )

    object Record : NavScreen(
        route = "record",
        label = "Record",
        icon = Icons.Default.Call
    )

    object Edit : NavScreen(
        route = "edit",
        label = "Edit",
        icon = Icons.Default.Edit
    ) {
        const val routeWithArgs = "edit/{filePath}"

        /**
         * Build the actual navigation string by URL‐encoding the filePath.
         * e.g. "edit/%2Fstorage%2Femulated%2F0%2FDownload%2Fmyclip.wav"
         */
        fun createRoute(filePath: String): String {
            return "edit/${Uri.encode(filePath)}"
        }
    }
    object Debug : NavScreen(
        route = "debug",
        label = "Debug",
        icon = Icons.Default.Edit // or choose a different icon if you prefer
    ) {
        const val routeWithArgs = "debug/{filePath}"

        fun createRoute(filePath: String): String {
            return "debug/${Uri.encode(filePath)}"
        }
    }
}
