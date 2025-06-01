
//TODO: Import audio from filesystem DONE
//TODO: Be able to read .wav files DONE
//TODO: Implement looking at audio DONE
//TODO: Implement playback of audio DONE
//TODO: Implement cutting of audio
//TODO: Implement removing of audio
//TODO: Implement menu in editing screen
//TODO: Implement exporting of project
//TODO: Implement adding hash value when cutting/removing
//TODO: Implement hash tree to editing steps
//TODO: Implement validate Recording
//TODO: Implement going back using strg+z using hash tree
//TODO: improve audio recording

package com.example.mobileappstrusted

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.mobileappstrusted.navigation.NavScreen
import com.example.mobileappstrusted.screens.EditAudioScreen
import com.example.mobileappstrusted.screens.HomeScreen
import com.example.mobileappstrusted.components.BottomBar
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
                            RecordAudioScreen { recordedFilePath ->
                                // Always navigate on the main thread to avoid IllegalStateException
                                Handler(Looper.getMainLooper()).post {
                                    navController.navigate(NavScreen.Edit.createRoute(recordedFilePath))
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
                    }
                }
            }
        }
    }
}
