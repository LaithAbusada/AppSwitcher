package com.example.appswitcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SystemAlertWindowPermissionDialog(
    isPermanentlyDeclined: Boolean,
    onDismiss: () -> Unit,
    onGoToAppSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)) // Semi-transparent dark background
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp), // Add horizontal padding for responsiveness
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)) // Dark card background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp), // Increase padding for better spacing
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "Permission Required",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Message
                Text(
                    text = if (isPermanentlyDeclined) {
                        "To use this feature, please enable 'Display over other apps' permission from settings."
                    } else {
                        "This app requires 'Display over other apps' permission to function properly."
                    },
                    fontSize = 14.sp,
                    color = Color(0xFFD1D1D6), // Subtle gray for text
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp) // Add spacing between buttons
                ) {
                    // Dismiss Button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .width(130.dp) // Explicit width for the button
                            .height(50.dp), // Ensure enough height for text
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4E4E50), // Neutral gray
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Dismiss",
                            fontSize = 14.sp, // Text size that fits
                            maxLines = 1 // Ensure single-line text
                        )
                    }

                    // Go to Settings Button
                    Button(
                        onClick = onGoToAppSettingsClick,
                        modifier = Modifier
                            .width(160.dp) // Slightly wider button to fit "Go to Settings"
                            .height(50.dp), // Ensure enough height for text
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD700), // Gold
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = "Settings",
                            fontSize = 14.sp, // Text size that fits
                            maxLines = 1 // Ensure single-line text
                        )
                    }
                }
            }
        }
    }
}
/**
 * Check if the SYSTEM_ALERT_WINDOW permission is granted.
 */
fun hasSystemAlertWindowPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

/**
 * Navigate to the settings page for SYSTEM_ALERT_WINDOW permission.
 */
fun navigateToSystemAlertWindowSettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    context.startActivity(intent)
}
