package com.example.appswitcher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.Settings
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainPageActivity : ComponentActivity() {
    val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPreferences = getSharedPreferences("AppSwitcherPrefs", Context.MODE_PRIVATE)

        // ViewModel instance

        setContent {
            val rememberedPin = sharedPreferences.getString("PIN", null)
            val isRememberMeChecked = sharedPreferences.getBoolean("RememberMe", false)

            // Collect the StateFlow in a composable-friendly way
            val isDialogVisible = viewModel.visiblePermissionDialogQueue.collectAsState().value
            var hasOpenedSettings by remember { mutableStateOf(false) }

            // Check for SYSTEM_ALERT_WINDOW permission
            LaunchedEffect(Unit) {
                if (!Settings.canDrawOverlays(this@MainPageActivity)) {
                    viewModel.enqueuePermissionDialog()
                }
            }

            MainPageScreen(
                onSettingsPageClick = { pinEntered ->


                    // Check if the SYSTEM_ALERT_WINDOW permission is granted
                    if (!Settings.canDrawOverlays(this)) {
                        // Show the dialog to request permission
                        viewModel.enqueuePermissionDialog()
                        Toast.makeText(
                            this,
                            "Permission required: Please enable 'Display over other apps' to proceed.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@MainPageScreen
                    }



                    val storedPin = sharedPreferences.getString("PIN", "1234") ?: "1234"
                    if (pinEntered == storedPin) {

                        val rememberMeChecked = sharedPreferences.getBoolean("RememberMe", false)
                        if (rememberMeChecked) {
                            sharedPreferences.edit().putString("RememberedPIN", pinEntered).apply()
                        }

                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                    }
                },
                onRebootClick = {
                    try {
                        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                        powerManager.reboot(null)
                    } catch (e: SecurityException) {
                        e.printStackTrace()
                        Toast.makeText(this, "Reboot failed. Ensure the app has system-level permissions.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, "Reboot failed due to an unknown error.", Toast.LENGTH_SHORT).show()
                    }
                },
                onPinChange = { newPin ->
                    sharedPreferences.edit().putString("PIN", newPin).apply()
                },
                onRememberMeToggle = { isChecked, pinToRemember ->
                    sharedPreferences.edit()
                        .putBoolean("RememberMe", isChecked)
                        .putString("RememberedPIN", if (isChecked) pinToRemember else null)
                        .apply()
                },
                onExitAppClick = {
                    Toast.makeText(this, "Exiting app...", Toast.LENGTH_SHORT).show()
                    val stopOverlayServiceIntent = Intent(this, OverlayService::class.java)
                    stopService(stopOverlayServiceIntent)
                    finishAffinity()
                },
                rememberedPin = rememberedPin ?: "",
                isRememberMeChecked = isRememberMeChecked,
                sharedPreferences = sharedPreferences
            )

            // Show dialog if SYSTEM_ALERT_WINDOW permission is not granted
            if (isDialogVisible) {
                SystemAlertWindowPermissionDialog(
                    isPermanentlyDeclined = hasOpenedSettings,
                    onDismiss = {
                        viewModel.dismissPermissionDialog()
                    },
                    onGoToAppSettingsClick = {
                        hasOpenedSettings = true
                        navigateToSystemAlertWindowSettings(this@MainPageActivity)
                    }
                )
            }
        }
    }


    override fun onResume() {
        super.onResume()
        // Recheck the SYSTEM_ALERT_WINDOW permission when the activity resumes
        if (Settings.canDrawOverlays(this)) {
            // Permission is granted, dismiss the dialog
            viewModel.dismissPermissionDialog()
        } else {
            // Permission is not granted, show the dialog
            viewModel.enqueuePermissionDialog()
        }
    }
}

/**
 * ViewModel to manage the permission dialog state.
 */
class MainViewModel : ViewModel() {
    private val _visiblePermissionDialogQueue = MutableStateFlow(false)
    val visiblePermissionDialogQueue = _visiblePermissionDialogQueue.asStateFlow()

    fun enqueuePermissionDialog() {
        _visiblePermissionDialogQueue.value = true
    }

    fun dismissPermissionDialog() {
        _visiblePermissionDialogQueue.value = false
    }
}
@Composable
fun MainPageScreen(
    onSettingsPageClick: (String) -> Unit,
    onRebootClick: () -> Unit,
    onPinChange: (String) -> Unit,
    onRememberMeToggle: (Boolean, String) -> Unit,
    onExitAppClick: () -> Unit,
    rememberedPin: String,
    isRememberMeChecked: Boolean,
    sharedPreferences: SharedPreferences
) {
    // Initialize the PIN code state based on the "Remember Me" checkbox
    var pinCode by remember { mutableStateOf(if (isRememberMeChecked) rememberedPin else "") }
    var rememberMeChecked by remember { mutableStateOf(isRememberMeChecked) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Scroll state for the column
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF232230))
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Logo
        Image(
            painter = painterResource(id = R.mipmap.ic_innovo_big),
            contentDescription = "Innovo Logo",
            modifier = Modifier
                .width(200.dp)
                .height(120.dp)
        )

        // Title
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Welcome to Innovo",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "App Switcher",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // PIN Entry Section
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.9f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CustomTextField(
                    value = pinCode,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            pinCode = if (newValue.length <= 6) newValue else newValue.take(6)
                        }
                    },
                    leadingIcon = painterResource(id = R.mipmap.ic_lock)
                )
            }
        }

        // Remember Me and Change PIN Section
        Row(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(vertical = 0.dp)
                .height(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rememberMeChecked,
                    onCheckedChange = { isChecked ->
                        rememberMeChecked = isChecked
                        if (!isChecked) {
                            // Clear remembered PIN if unchecked
                            sharedPreferences.edit().remove("RememberedPIN").apply()
                        }
                        onRememberMeToggle(isChecked, pinCode)
                    }
                )
                Text("Remember Me", fontSize = 14.sp, color = Color.White)
            }

            Text(
                text = "Change PIN Code",
                fontSize = 14.sp,
                color = Color.Yellow,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { showChangePinDialog = true }
            )
        }

        // Access Settings Button
        Button(
            onClick = { onSettingsPageClick(pinCode) },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(45.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
        ) {
            Text("Access Settings", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        // Show Change PIN Dialog
        if (showChangePinDialog) {
            ChangePinDialog(
                currentPinStored = sharedPreferences.getString("PIN", "1234") ?: "1234",
                onDismissRequest = { showChangePinDialog = false },
                onPinChangeSuccess = { newPin ->
                    sharedPreferences.edit().putString("PIN", newPin).apply()
                    Toast.makeText(context, "PIN successfully changed!", Toast.LENGTH_SHORT).show()
                    showChangePinDialog = false
                    pinCode = "" // Clear PIN field after PIN change
                }
            )
        }

        // Additional Content: OR Divider
        Text(
            text = "OR",
            fontSize = 14.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        // Reboot and Exit App Buttons
        RebootButton(onClick = onRebootClick)
        ExitAppSwitcherButton(onClick = onExitAppClick)

        // Footer Links
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Visit Innovo for all your automation needs: ",
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "innovo.net",
                fontSize = 12.sp,
                color = Color.Yellow,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://innovo.net"))
                    context.startActivity(intent)
                }
            )
        }
    }
}
// Reusable Reboot Button
@Composable
fun RebootButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(45.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
    ) {
        Text("Reboot Device", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// Reusable Exit App Switcher Button
@Composable
fun ExitAppSwitcherButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(45.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF8E2DE2), Color(0xFFFC466B))
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Exit App Switcher",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ChangePinDialog(
    currentPinStored: String, // Add stored PIN as a parameter
    onDismissRequest: () -> Unit,
    onPinChangeSuccess: (String) -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var secondPin by remember { mutableStateOf("") }
    val pinMatch = firstPin == secondPin && firstPin.isNotEmpty()
    val currentPinMatch = currentPin == currentPinStored // Check if the current PIN matches the stored PIN
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Change PIN Code",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column {
                CustomTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = currentPin,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            currentPin = if (newValue.length <= 6) newValue else newValue.take(6)
                        }
                    },                    leadingIcon = painterResource(id = R.mipmap.ic_lock),
                    placeholder = { Text(text = "Current PIN") }
                )
                if (!currentPinMatch && currentPin.isNotEmpty()) {
                    Text(
                        text = "Current PIN is incorrect",
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                CustomTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = firstPin,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            firstPin = newValue.take(6)
                        }
                    },                    leadingIcon = painterResource(id = R.mipmap.ic_lock),
                    placeholder = { Text(text = "New PIN") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                CustomTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = secondPin,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            secondPin = newValue.take(6)
                        }
                    },                    leadingIcon = painterResource(id = R.mipmap.ic_lock),
                    placeholder = { Text(text = "Confirm New PIN") }
                )
                if (firstPin.length !in 4..6&& firstPin.isNotEmpty()) {
                    Text(
                        text = "PIN must be at between 4-6 characters",
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                }
                if (!pinMatch && secondPin.isNotEmpty()) {
                    Text(
                        text = "PINs do not match",
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinMatch && currentPinMatch && firstPin.length in 4..6) {
                        onPinChangeSuccess(firstPin)
                    }
                },
                enabled = pinMatch && currentPinMatch, // Button is conditionally actionable
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700), // Always yellow/gold
                    contentColor = if (pinMatch && currentPinMatch) Color.Black else Color.Gray, // Gray text when disabled
                    disabledContainerColor = Color(0xFFFFD700), // Force yellow even when disabled
                    disabledContentColor = Color.Gray // Gray text when disabled
                )
            ) {
                Text("Confirm", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E1E1E), // Dark background for cancel button
                    contentColor = Color.White
                )
            ) {
                Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF232230) // Dialog background color
    )
}


@Composable
fun CustomTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: Painter? = null,
    onTrailingIconClick: () -> Unit = {},
    trailingIcon: Painter? = null,
    placeholder: @Composable (() -> Unit)? = null // Add placeholder parameter
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.Yellow,
            focusedLabelColor = Color.Yellow,
            unfocusedLabelColor = Color.Gray,
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.Gray,
            focusedLeadingIconColor = Color.White,
            unfocusedLeadingIconColor = Color.White,
            focusedTrailingIconColor = Color.Yellow,
            unfocusedTrailingIconColor = Color.Gray
        ),
        visualTransformation = PasswordVisualTransformation(),
        leadingIcon = {
            if (leadingIcon != null)
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = leadingIcon,
                    contentDescription = null
                )
        },
        trailingIcon = {
            if (trailingIcon != null)
                IconButton(onClick = onTrailingIconClick) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = trailingIcon,
                        contentDescription = null
                    )
                }
        },
        placeholder = placeholder, // Pass the placeholder to OutlinedTextField

        singleLine = true,
        textStyle = TextStyle(fontSize = 24.sp, color = Color.White)
    )
}
