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

//TODO cybersecurity
// ============================
//TODO: Implement cutting of audio (Double check hash tree, check edit history if matching current index order)
//TODO: Implement removing of audio (Encrypt with Password, append hash of unencrypted block, use hash of block in root hash checking if deleted)
//TODO: Implement digital signature stuff
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
