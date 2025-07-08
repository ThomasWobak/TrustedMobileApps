
//TODO cybersecurity
// ============================
//TODO: Implement cutting of audio (Double check hash tree, check edit history if matching current index order)
//Comment: Need edit history for this
//TODO: Implement removing of audio (Encrypt with Password, append hash of unencrypted block, use hash of block in root hash checking if deleted)
//Comment: Need export functionality for this
//TODO: Implement digital signature stuff
//Comment: Need export functionality for this
//TODO: Implement validate Recording (Almost done --> need removing and cutting to validate properly)
//TODO: Implement validate imported Audio
// Lukas

//TODO: Implement editing script with metadata and changes (user information) DONE
//TODO: Implement general metadata on recording (user information) DONE
// Thomas

//TODO usability
// ============================
//TODO: Import audio from filesystem DONE
//TODO: Be able to read .wav files DONE
//TODO: Implement looking at audio DONE
//TODO: Implement playback of audio DONE
//TODO: Implement exporting of edited file DONE
//TODO: Improve Record Audio Screen (Showing audio being recorded, resuming recording,...) DONE
//TODO: Implement reversing operation using temporary in memory editing script DONE

//TODO: Improve cutting of audio (ease of use) GOOD ENOUGH?
//TODO: Improve removing of audio (granularity?, ease of use) GOOD ENOUGH?
//TODO: Implement menu in editing screen NOT NEEDED?
// Thomas


//TODO Low priority
// ============================
//TODO: verify if added block headers are noticeable
//TODO: audio watermark with metadata information


package com.example.mobileappstrusted

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mobileappstrusted.components.BottomBar
import com.example.mobileappstrusted.navigation.NavScreen
import com.example.mobileappstrusted.screens.DebugAudioScreen
import com.example.mobileappstrusted.screens.EditAudioScreen
import com.example.mobileappstrusted.screens.HomeScreen
import com.example.mobileappstrusted.screens.RecordAudioScreen
import com.example.mobileappstrusted.ui.theme.MobileAppsTrustedTheme
import java.net.URLDecoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobileAppsTrustedTheme {
                val navController = rememberNavController()

                Scaffold(
                    bottomBar = { BottomBar(navController = navController) }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = NavScreen.Home.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        //Home
                        composable(NavScreen.Home.route) {
                            HomeScreen(navController)
                        }

                        //Record
                        composable(NavScreen.Record.route) {
                            RecordAudioScreen { targetPath ->
                                Handler(Looper.getMainLooper()).post {
                                    when {
                                        targetPath.startsWith("debug:") -> {
                                            val actualPath = targetPath.removePrefix("debug:")
                                            navController.navigate(NavScreen.Debug.createRoute(actualPath)) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    inclusive = false
                                                }
                                                launchSingleTop = true
                                                restoreState = false
                                            }
                                        }
                                        else -> {
                                            navController.navigate(NavScreen.Edit.createRoute(targetPath)) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    inclusive = false
                                                }
                                                launchSingleTop = true
                                                restoreState = false
                                            }
                                        }
                                    }
                                }
                            }


                        }

                        // Edit with no args → placeholder
                        composable(NavScreen.Edit.route) {
                            EditAudioScreen(filePath = "")
                        }

                        //Edit with a real path: "edit/{filePath}"
                        composable(
                            route = NavScreen.Edit.routeWithArgs,
                            arguments = listOf(navArgument("filePath") {
                                type = NavType.StringType
                            })
                        ) { backStackEntry ->
                            // Decode the URL‐encoded filePath
                            val encoded = backStackEntry.arguments?.getString("filePath") ?: ""
                            val filePath = URLDecoder.decode(encoded, "UTF-8")
                            EditAudioScreen(filePath = filePath)
                        }

                        // Debug with no args → placeholder
                        composable(NavScreen.Debug.route) {
                            DebugAudioScreen(filePath = "")
                        }

                        // Debug with a real path: "debug/{filePath}"
                        composable(
                            route = NavScreen.Debug.routeWithArgs,
                            arguments = listOf(navArgument("filePath") {
                                type = NavType.StringType
                            })
                        ) { backStackEntry ->
                            val encoded = backStackEntry.arguments?.getString("filePath") ?: ""
                            val filePath = URLDecoder.decode(encoded, "UTF-8")
                            DebugAudioScreen(filePath = filePath)
                        }
                    }
                }
            }
        }
    }
}
