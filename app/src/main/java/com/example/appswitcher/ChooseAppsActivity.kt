package com.example.appswitcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appswitcher.ui.theme.AppSwitcherTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import coil.compose.rememberAsyncImagePainter
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Serializable

// Data class for app info
data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable
)

// Data class for the selected app (serializable)
data class SelectedAppInfo(
    val name: String,
    val packageName: String,
) : Serializable

class ChooseAppsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val installedApps = getInstalledApps()

        setContent {
            AppSwitcherTheme {
                var selectedTab by remember { mutableStateOf("chooseApps") } // Track the active tab

                ChooseAppsScreen(
                    selectedTab = selectedTab,
                    packageManager = packageManager,
                    apps = installedApps,
                    onConfirmSelection = { selectedApps ->
                        val sharedPreferences = getSharedPreferences("AppSwitcherPrefs", Context.MODE_PRIVATE)
                        val editor = sharedPreferences.edit()

                        // Convert the list of selected apps to a JSON string
                        val jsonArray = JSONArray()
                        for (app in selectedApps) {
                            val jsonObject = JSONObject().apply {
                                put("name", app.name)
                                put("packageName", app.packageName)
                            }
                            jsonArray.put(jsonObject)
                        }

                        // Add "App Switcher" directly to selectedApps
                        val appSwitcher = AppInfo(
                            name = "App Switcher",
                            packageName = applicationContext.packageName,
                            icon = applicationContext.packageManager.getApplicationIcon(applicationContext.packageName)
                        )

                        // Add "App Switcher" to the JSON array as well
                        val appSwitcherJson = JSONObject().apply {
                            put("name", appSwitcher.name)
                            put("packageName", appSwitcher.packageName)
                        }
                        jsonArray.put(appSwitcherJson)

                        val rebootApp = AppInfo(
                            name = "REBOOT",
                            packageName = "REBOOT",
                            icon = getDrawable(R.drawable.reboot_xm)!!
                        )

                        val updatedSelectedApps = selectedApps.toMutableSet().apply { add(rebootApp) }

                        // Add "Reboot" to the JSON array
                        val rebootAppJson = JSONObject().apply {
                            put("name", rebootApp.name)
                            put("packageName", rebootApp.packageName)
                        }
                        jsonArray.put(rebootAppJson)


                        // Save the updated JSON array to SharedPreferences
                        editor.putString("selectedApps", jsonArray.toString())
                        editor.apply()

                        // Pass the updated selected apps to the OverlayService
                        val overlayServiceIntent = Intent(this@ChooseAppsActivity, OverlayService::class.java).apply {
                            putExtra("selectedApps", ArrayList(updatedSelectedApps.map { app ->
                                SelectedAppInfo(app.name, app.packageName)
                            }))
                        }
                        startService(overlayServiceIntent)

                        // Pass the updated selected apps to the result intent
                        val resultIntent = Intent().apply {
                            putExtra("chosenApps", ArrayList(updatedSelectedApps.map { app ->
                                SelectedAppInfo(app.name, app.packageName)
                            }))
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                    onHomeClick = {
                        val intent = Intent(this@ChooseAppsActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
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
                    onExitClick = {
                        finishAffinity()
                    }
                )
            }
        }
    }

    private fun getInstalledApps(): List<AppInfo> {
        val packageManager = packageManager
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        // Specify the allowed system app package names
        val allowedSystemApps = setOf(
            "com.homelogic",
            "com.lutron.mmw",
            "com.control4.phoenix",
            "com.rakocontrols.android",
            "com.sonos.acr2",
            "com.spotify.music"
        )

        return apps.filter { app ->
            val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isAppSwitcher = app.packageName == applicationContext.packageName // Replace with the actual package name of your app if needed

            if (isAppSwitcher) {
                // Exclude the app switcher itself
               false
            } else if (isSystemApp) {
                // System app: Include only if it matches the allowed list
                app.packageName in allowedSystemApps
            } else {
                // Include non-system apps
                true
            }
        }.map {
            AppInfo(
                name = it.loadLabel(packageManager).toString(),
                packageName = it.packageName,
                icon = it.loadIcon(packageManager)
            )
        }
    }

}

private fun drawableToByteArray(drawable: Drawable): ByteArray {
    val bitmap = (drawable as BitmapDrawable).bitmap
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}
@Composable
fun ChooseAppsScreen(
    selectedTab: String,
    packageManager: PackageManager,
    apps: List<AppInfo>,
    onConfirmSelection: (List<AppInfo>) -> Unit,
    onHomeClick: () -> Unit,
    onRebootClick: () -> Unit,
    onExitClick: () -> Unit
) {
    var selectedApps by remember { mutableStateOf(setOf<AppInfo>()) }
    var selectedCategory by remember { mutableStateOf("All Apps") } // Default category

    // Dummy Innovo Apps list
    val innovoAppsPackageNames = setOf("com.example.innovoapp1", "com.example.innovoapp2")

    val filteredApps = when (selectedCategory) {
        "All Apps" -> apps
      "Innovo Apps" -> apps.filter { it.packageName in innovoAppsPackageNames }
        "Social Media Apps" -> apps.filter { isSocialMediaApp(packageManager, it.packageName) }
        else -> apps
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF232230))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Main Content Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Choose your Apps",
                    fontSize = 20.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Category Selection Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClickableCategoryText(
                        text = "Innovo Apps",
                        isSelected = selectedCategory == "Innovo Apps",
                        onClick = { selectedCategory = "Innovo Apps" }
                    )
                    ClickableCategoryText(
                        text = "Social Media",
                        isSelected = selectedCategory == "Social Media Apps",
                        onClick = { selectedCategory = "Social Media Apps" }
                    )
                    ClickableCategoryText(
                        text = "All Apps",
                        isSelected = selectedCategory == "All Apps",
                        onClick = { selectedCategory = "All Apps" }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredApps) { app ->
                        val isSelected = selectedApps.contains(app)
                        AppListItem(
                            app = app,
                            isSelected = isSelected,
                            onSelectChange = { selected ->
                                selectedApps = if (selected) {
                                    if (selectedApps.size < 6) selectedApps + app else selectedApps
                                } else {
                                    selectedApps - app
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    selectedApps.forEach { app ->
                        Image(
                            painter = rememberAsyncImagePainter(app.icon),
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .padding(4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onConfirmSelection(selectedApps.toList()) },
                    enabled = selectedApps.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFAEB00), // Button background color
                        contentColor = Color.Black         // Button text color
                    )
                ) {
                    Text("Start Launcher")
                }
            }

            // Bottom Navigation Bar
            BottomNavBar(
                selectedTab = selectedTab,
                onHomeClick = onHomeClick,
                onChooseAppsClick = {
                    // No-op since we're already on the Choose Apps screen
                },
            onRebootClick = onRebootClick,
                onExitClick = onExitClick
            )
        }
    }
}

@Composable
fun ClickableCategoryText(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = if (isSelected) Color(0xFFFF69B4) else Color.White,
        modifier = Modifier
            .padding(horizontal = 12.dp) // Add spacing between text items
            .clickable(onClick = onClick),
        fontSize = 14.sp // Make the font size smaller
    )
}
@Composable
fun AppListItem(app: AppInfo, isSelected: Boolean, onSelectChange: (Boolean) -> Unit) {
    val backgroundColor = if (isSelected) Color(0xFFFFF9C4) else Color.Black
    val textColor = if (isSelected) Color.Black else Color.White
    val borderColor = if (isSelected) Color(0xFFFFC107) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(color = backgroundColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .border(
                width = 2.dp,
                color = borderColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .clickable { onSelectChange(!isSelected) } // Toggle selection on click
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberAsyncImagePainter(app.icon),
            contentDescription = null,
            modifier = Modifier.size(45.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = app.name,
            modifier = Modifier.weight(1f),
            color = textColor
        )
    }
}

private fun isSocialMediaApp(packageManager: PackageManager, packageName: String): Boolean {
    val socialMediaPackages = setOf(
        "com.facebook.katana",   // Facebook
        "com.instagram.android", // Instagram
        "com.twitter.android",   // Twitter
        "com.snapchat.android",  // Snapchat
        "com.linkedin.android",  // LinkedIn
        "com.whatsapp",          // WhatsApp
        "com.tiktok.android",    // TikTok
        "com.pinterest"          // Pinterest
    )
    return socialMediaPackages.contains(packageName)
}
