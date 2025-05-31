// BottomBar.kt
package com.example.mobileappstrusted.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.mobileappstrusted.navigation.NavScreen

@Composable
fun BottomBar(navController: NavHostController) {
    val items = listOf(
        NavScreen.Home,
        NavScreen.Record,
        NavScreen.Edit
    )

    // Observe the current back stack so we can highlight the selected item
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry
        ?.destination
        ?.route
        // If the route is "edit/XYZ", substringBefore("/") gives "edit"
        ?.substringBefore("/")

    NavigationBar {
        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                selected = (currentRoute == screen.route),
                onClick = {
                    // If we’re already on this destination, do nothing.
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            // Pop up to the start destination to avoid building a large backstack
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
