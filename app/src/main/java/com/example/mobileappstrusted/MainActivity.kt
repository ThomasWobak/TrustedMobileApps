package com.example.mobileappstrusted

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.mobileappstrusted.ui.theme.MobileAppsTrustedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobileAppsTrustedTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("record_audio") { RecordAudioScreen() }
        composable("edit_audio") { EditAudioScreen() }
    }
}

@Composable
fun HomeScreen(navController: NavHostController) {
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
                onClick = { navController.navigate("record_audio") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Go to Record Audio")
            }
            Button(
                onClick = { navController.navigate("edit_audio") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Go to Edit Audio")
            }
        }
    }
}

@Composable
fun RecordAudioScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Record Audio Screen",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun EditAudioScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Edit Audio Screen",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MobileAppsTrustedTheme {
        HomeScreen(rememberNavController())
    }
}
