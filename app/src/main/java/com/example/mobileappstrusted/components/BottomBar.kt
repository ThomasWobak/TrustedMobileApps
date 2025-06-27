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

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore("/")

    NavigationBar {
        items.forEach { screen ->
            val selected = when (screen) {
                is NavScreen.Edit -> navBackStackEntry?.destination?.route?.startsWith("edit/") == true
                else -> currentRoute == screen.route
            }

            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                selected = selected,
                onClick = {
                    if (!selected) {
                        //Edit needs dynamic args (filepath), but others don't. So the back stack if cleared first for those
                        if (screen != NavScreen.Edit) {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = true
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        } else {
                            navController.navigate(screen.route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                }
            )
        }
    }
}
