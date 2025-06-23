
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

//TODO: Implement editing script with metadata and changes (user information)
//TODO: Implement general metadata on recording (user information)
// Thomas

//TODO usability
// ============================
//TODO: Import audio from filesystem DONE
//TODO: Be able to read .wav files DONE
//TODO: Implement looking at audio DONE
//TODO: Implement playback of audio DONE

//TODO: Improve cutting of audio (ease of use)
//TODO: Improve removing of audio (granularity?, ease of use)
//TODO: Implement menu in editing screen
//TODO: Implement exporting of edited file
//TODO: Implement going back using strg+z using temporary in memory editing script
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mobileappstrusted.components.BottomBar
import com.example.mobileappstrusted.navigation.NavScreen
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
