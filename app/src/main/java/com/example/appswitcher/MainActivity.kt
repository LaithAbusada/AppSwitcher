package com.innovo.appswitcher

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import com.example.appswitcher.ui.theme.AppSwitcherTheme
import com.innovo.appswitcher.BuildConfig
import com.innovo.appswitcher.R
import org.json.JSONArray
import org.json.JSONException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            // Function to check if a package exists
            fun isAppInstalled(packageName: String): Boolean {
                return try {
                    packageManager.getPackageInfo(packageName, 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
            }

            // Pre-check and pass as parameters
            val packageName1 = "com.portworld.bootstartapp"
            val packageName2 = "com.adw.bootapp"

            val isPackagesFound = isAppInstalled(packageName2) || isAppInstalled(packageName1)

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

                        val predefinedApps = listOf(
                            SelectedAppInfo("Elan", "com.homelogic"),
                            SelectedAppInfo("REBOOT", "REBOOT")
                        )

                        val packageManager = this.packageManager




                        // Check and start overlay if not active
                        if (!isOverlayActive()) {
                            val appsFromPreferences = getAppsFromPreferences()
                            val rebootApp = SelectedAppInfo("REBOOT", "REBOOT")

                            if (appsFromPreferences.isNotEmpty()) {
                                startOverlayService(appsFromPreferences)
                            } else {
                                val existingPredefinedApps = predefinedApps.filter {
                                    it.packageName == "REBOOT" || isAppInstalled(it.packageName)
                                }

                                if (existingPredefinedApps.isNotEmpty()) {
                                    startOverlayService(existingPredefinedApps)
                                } else {
                                    Toast.makeText(this, "No apps found to start OverlayService.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                   finishAffinity()
                    },
                    onHomeClick = {
                    },
                    onLaunchAppClick = {
                        val packageName1 = "com.portworld.bootstartapp"
                        val packageName2 = "com.adw.bootapp"

                        val predefinedApps = listOf(
                            SelectedAppInfo("Elan", "com.homelogic"),
                            SelectedAppInfo("REBOOT", "REBOOT")
                        )

                        val packageManager = this.packageManager




                        // Check and start overlay if not active
                        if (!isOverlayActive()) {
                            val appsFromPreferences = getAppsFromPreferences()
                            val rebootApp = SelectedAppInfo("REBOOT", "REBOOT")

                            if (appsFromPreferences.isNotEmpty()) {
                                startOverlayService(appsFromPreferences)
                            } else {
                                val existingPredefinedApps = predefinedApps.filter {
                                    it.packageName == "REBOOT" || isAppInstalled(it.packageName)
                                }

                                if (existingPredefinedApps.isNotEmpty()) {
                                    startOverlayService(existingPredefinedApps)
                                } else {
                                    Toast.makeText(this, "No apps found to start OverlayService.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        // Check for the first app
                        if (isAppInstalled(packageName1)) {
                            val intent = packageManager.getLaunchIntentForPackage(packageName1)
                            if (intent != null) {
                                startActivity(intent)
                            } else {
                                Toast.makeText(this, "Unable to launch $packageName1", Toast.LENGTH_SHORT).show()
                            }
                        }
                        // Check for the second app if the first one doesn't exist
                        else if (isAppInstalled(packageName2)) {
                            try {
                                // Create an explicit intent to start the activity
                                val intent = Intent()
                                intent.component = ComponentName(packageName2, "$packageName2.MainActivity")
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK // Ensure it's launched as a new task
                                startActivity(intent)
                            } catch (e: Exception) {
                                // Handle exceptions, e.g., if the activity name is incorrect or app is not accessible
                                e.printStackTrace()
                                Toast.makeText(this, "Unable to launch $packageName2.MainActivity", Toast.LENGTH_SHORT).show()
                            }
                        }
                        // Show a message if neither app is installed
                        else {
                            Toast.makeText(this, "Unable to find the specified apps.", Toast.LENGTH_SHORT).show()
                        }
                    },
                foundApps = isPackagesFound
                )
            }
        }
    }



     fun isOverlayActive(): Boolean {
        // Check if overlay permission is granted
        if (!Settings.canDrawOverlays(this)) {
            return false
        }

        // Check if the overlay service is running
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return activityManager.getRunningServices(Int.MAX_VALUE).any { service ->
            service.service.className == OverlayService::class.java.name
        }
    }

    // Retrieve apps from SharedPreferences
    fun getAppsFromPreferences(): List<SelectedAppInfo> {
        val sharedPreferences = getSharedPreferences("AppSwitcherPrefs", Context.MODE_PRIVATE)
        val selectedAppsJson = sharedPreferences.getString("selectedApps", null)
        return if (!selectedAppsJson.isNullOrEmpty()) {
            try {
                val jsonArray = JSONArray(selectedAppsJson)
                (0 until jsonArray.length()).map { i ->
                    jsonArray.getJSONObject(i).let {
                        SelectedAppInfo(it.getString("name"), it.getString("packageName"))
                    }
                }
            } catch (e: JSONException) {
                emptyList() // Return an empty list if JSON parsing fails
            }
        } else {
            emptyList() // Return an empty list if no apps are found in SharedPreferences
        }
    }

    // Start the OverlayService with a list of apps
    fun startOverlayService(apps: List<SelectedAppInfo>) {
        val overlayServiceIntent = Intent(this, OverlayService::class.java).apply {
            putExtra("selectedApps", ArrayList(apps))
        }
        startService(overlayServiceIntent)
    }


}




@Composable
fun MainPageScreen(
    selectedTab: String,
    onChooseAppsClick: () -> Unit,
    onRebootClick: () -> Unit,
    onExitAppClick: () -> Unit,
    onHomeClick: () -> Unit,
    onLaunchAppClick : () -> Unit,
    foundApps : Boolean
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
                painter = painterResource(id = R.drawable.innovo_new),
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
            // Choose Apps Button





            Button(
                onClick = onLaunchAppClick,
                enabled = foundApps,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(45.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700),
                    disabledContainerColor = Color(0xFFB0B0B0), // Optional: Color for the disabled button
                    disabledContentColor = Color.White          // Optional: Text color for the disabled state
                )
            ) {
                Text("Launch Selector App", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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



            // Exit App Switcher Button
            ExitAppSwitcherButton(onClick = onExitAppClick)

            // Link Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "App Switcher (Version: ${BuildConfig.VERSION_NAME})",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium,

                    )
            }
        }

        // Bottom Navigation Bar
        BottomNavBar(
            selectedTab = selectedTab,
            onHomeClick = onHomeClick,
            onChooseAppsClick = onChooseAppsClick,
            onLaunchAppClick = onLaunchAppClick,
            onExitClick = onExitAppClick,
            foundApps = foundApps
        )
    }
}

@Composable
fun BottomNavBar(
    selectedTab: String,
    onHomeClick: () -> Unit,
    onChooseAppsClick: () -> Unit,
    onLaunchAppClick: () -> Unit,
    onExitClick: () -> Unit,
    foundApps: Boolean = true
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
            isSelected = selectedTab == "SelectorApp",
            iconRes = R.mipmap.ic_reboot, // Replace with your home icon
            label = "Selector App",
            onClick = onLaunchAppClick,
            enabled = foundApps
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
    onClick: () -> Unit,
    enabled: Boolean = true // Optional `enabled` parameter with a default value
) {
    val iconColor = if (isSelected) Color(0xFFFF69B4) else Color.White // Pink color for icon when selected
    val labelColor = if (isSelected) Color(0xFFFF69B4) else Color.White // Pink color for label when selected

    Column(
        modifier = Modifier
            .padding(8.dp)
            .let { if (enabled) it.clickable { onClick() } else it }, // Apply clickable only if enabled
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            modifier = Modifier.size(24.dp), // Icon size
            tint = if (enabled) iconColor else Color.Gray, // Set icon color dynamically
        )
        Spacer(modifier = Modifier.height(4.dp)) // Space between icon and text
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (enabled) labelColor else Color.Gray, // Gray color for label when disabled
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center // Ensure the label is centered
        )
    }
}