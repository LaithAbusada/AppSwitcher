package com.example.appswitcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appswitcher.ui.theme.AppSwitcherTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppSwitcherTheme {
                var selectedTab by remember { mutableStateOf("home") } // Track the active tab

                MainPageScreen(
                    selectedTab = selectedTab,
                    onChooseAppsClick = {
                        val intent = Intent(this, ChooseAppsActivity::class.java)
                        startActivity(intent)
                    },
                    onRebootClick = {
                        try {
                            // Get the PowerManager service
                            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

                            // Reboot the device (requires root or system app permissions)
                            powerManager.reboot(null)
                        } catch (e: SecurityException) {
                            e.printStackTrace()
                            // Show a toast or log message if permissions are insufficient
                            Toast.makeText(this, "Reboot failed. Ensure the app has system-level permissions.", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // General error handling
                            Toast.makeText(this, "Reboot failed due to an unknown error.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onExitAppClick = {
                        val stopOverlayServiceIntent = Intent(this, OverlayService::class.java)
                        stopService(stopOverlayServiceIntent)
                   finishAffinity()
                    },
                    onHomeClick = {
                    }
                )
            }
        }
    }
}

@Composable
fun MainPageScreen(
    selectedTab: String,
    onChooseAppsClick: () -> Unit,
    onRebootClick: () -> Unit,
    onExitAppClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF232230))
    ) {
        // Main content area
        Column(
            modifier = Modifier
                .weight(1f) // Fills the available vertical space above the navbar
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Logo Section
            Image(
                painter = painterResource(id = R.mipmap.ic_innovo_big),
                contentDescription = "Innovo Logo",
                modifier = Modifier
                    .width(200.dp)
                    .height(120.dp)
            )

            // Welcome Text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Welcome to Innovo", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("App Switcher", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            // Choose Apps Button
            Button(
                onClick = onChooseAppsClick,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(45.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
            ) {
                Text("Choose Your Apps", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            // "OR" Separator
            Text(
                text = "OR",
                fontSize = 14.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // Reboot Button
            RebootButton(onClick = onRebootClick)

            // Exit App Switcher Button
            ExitAppSwitcherButton(onClick = onExitAppClick)

            // Link Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Learn about Innovo's great products at:  ",
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

        // Bottom Navigation Bar
        BottomNavBar(
            selectedTab = selectedTab,
            onHomeClick = onHomeClick,
            onChooseAppsClick = onChooseAppsClick,
            onRebootClick = onRebootClick,
            onExitClick = onExitAppClick
        )
    }
}

@Composable
fun BottomNavBar(
    selectedTab: String,
    onHomeClick: () -> Unit,
    onChooseAppsClick: () -> Unit,
    onRebootClick: () -> Unit,
    onExitClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp) // Increase height to ensure space for icons and labels
            .padding(bottom = 8.dp) // Slight margin at the bottom
            .background(Color(0xFF232230)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(
            isSelected = selectedTab == "home",
            iconRes = R.mipmap.ic_home_icon, // Replace with your home icon
            label = "Home",
            onClick = onHomeClick
        )
        BottomNavItem(
            isSelected = selectedTab == "chooseApps",
            iconRes = R.mipmap.ic_choose_apps, // Replace with your home icon
            label = "Choose Apps",
            onClick = onChooseAppsClick
        )
        BottomNavItem(
            isSelected = selectedTab == "Reboot",
            iconRes = R.mipmap.ic_reboot, // Replace with your home icon
            label = "Reboot",
            onClick = onRebootClick
        )
        BottomNavItem(
            isSelected = false, // Exit button doesn't have a selected state
            iconRes = R.mipmap.ic_exit, // Replace with your home icon
            label = "Exit",
            onClick = onExitClick
        )
    }
}

@Composable
fun BottomNavItem(
    isSelected: Boolean,
    iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    val iconColor = if (isSelected) Color(0xFFFF69B4) else Color.White // Pink color for icon when selected
    val labelColor = if (isSelected) Color(0xFFFF69B4) else Color.White // Pink color for label when selected

    Column(
        modifier = Modifier
            .padding(8.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            modifier = Modifier.size(24.dp), // Icon size
            tint = iconColor // Set icon color dynamically
        )
        Spacer(modifier = Modifier.height(4.dp)) // Space between icon and text
        Text(
            text = label,
            fontSize = 12.sp,
            color = labelColor, // Set label color dynamically
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center // Ensure the label is centered
        )
    }
}