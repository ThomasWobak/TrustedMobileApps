package com.example.mobileappstrusted.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.mobileappstrusted.cryptography.DigitalSignatureUtils.isPrivateKeyStored
import com.example.mobileappstrusted.cryptography.DigitalSignatureUtils.storePrivateKey
import com.example.mobileappstrusted.navigation.NavScreen

@Composable
fun HomeScreen(navController: NavHostController) {
    val context = LocalContext.current

    // 🔐 Check if a private key is already stored
    val keyExists by remember {
        mutableStateOf(isPrivateKeyStored(context))
    }

    // 📂 File picker launcher
    val keyFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = getFileNameFromUri(context, it)
            if (fileName.endsWith(".pem", ignoreCase = true) || fileName.endsWith(".key", ignoreCase = true)) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val pemBytes = inputStream.readBytes()
                        storePrivateKey(context, pemBytes)
                        Toast.makeText(context, "Private key stored securely.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load key: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "Please select a .pem or .key file", Toast.LENGTH_SHORT).show()
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = { navController.navigate(NavScreen.Record.route) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Go to Record Audio", style = MaterialTheme.typography.bodyLarge)
        }

        Button(
            onClick = { navController.navigate(NavScreen.Edit.route) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Go to Edit Audio", style = MaterialTheme.typography.bodyLarge)
        }

        Button(
            onClick = { keyFilePickerLauncher.launch("*/*") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = if (keyExists) "Update Private Key" else "Set Private Key",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

// 📄 Get file name from URI
fun getFileNameFromUri(context: android.content.Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    val nameIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME) ?: -1
    return if (cursor != null && cursor.moveToFirst() && nameIndex != -1) {
        val name = cursor.getString(nameIndex)
        cursor.close()
        name
    } else {
        cursor?.close()
        uri.lastPathSegment ?: "unknown"
    }
}
