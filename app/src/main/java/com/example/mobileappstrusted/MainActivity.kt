// MainActivity.kt
package com.example.mobileappstrusted
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mobileappstrusted.ui.theme.MobileAppsTrustedTheme

//TODO: Import audio from filesystem
//TODO: Be able to read .wav files
//TODO: improve audio recording
//TODO: Implement looking at audio
//TODO: Implement cutting of audio
//TODO: Implement removing of audio
//TODO: Implement menu in editing screen
//TODO: Implement exporting of project
//TODO: Implement adding hash value when cutting/removing
//TODO: Implement hash tree to editing steps
//TODO: Implement validate Recording
//TODO: Implement going back using strg+z using hash tree
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobileAppsTrustedTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                startActivity(Intent(this@MainActivity, RecordAudioActivity::class.java))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text("Go to Record Audio")
                        }

                        Button(
                            onClick = {
                                startActivity(Intent(this@MainActivity, EditAudioActivity::class.java))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text("Go to Edit Audio")
                        }
                    }
                }
            }
        }
    }
}
