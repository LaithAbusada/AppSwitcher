package com.innovo.appswitcher

import android.app.Activity
    import android.app.ActivityManager
    import android.content.Context
    import android.content.Intent
    import android.content.pm.ApplicationInfo
    import android.content.pm.PackageManager
    import android.graphics.Bitmap
    import android.graphics.drawable.BitmapDrawable
    import android.graphics.drawable.Drawable
    import android.os.Bundle
    import android.os.PowerManager
    import android.provider.Settings
    import android.widget.Toast
    import androidx.activity.ComponentActivity
    import androidx.activity.compose.setContent
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.material3.Button
    import androidx.compose.material3.Text
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.compose.foundation.Image
    import androidx.compose.foundation.background
    import androidx.compose.foundation.border
    import androidx.compose.foundation.clickable
    import androidx.compose.ui.graphics.Color
    import coil.compose.rememberAsyncImagePainter
import com.example.appswitcher.ui.theme.AppSwitcherTheme
import com.innovo.appswitcher.R
    import org.json.JSONArray
    import org.json.JSONException
    import org.json.JSONObject
    import java.io.ByteArrayOutputStream
    import java.io.Serializable
    import kotlin.collections.forEach


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
                    var selectedTab by remember { mutableStateOf("chooseApps") } // Track the active tab
    
                    ChooseAppsScreen(
                        selectedTab = selectedTab,
                        packageManager = packageManager,
                        apps = installedApps,
                        onConfirmSelection = { selectedApps, isDiscreteMode ->
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
    
                            // Pass the updated selected apps and isDiscreteMode flag to the OverlayService
                            val overlayServiceIntent = Intent(this@ChooseAppsActivity, OverlayService::class.java).apply {
                                putExtra("selectedApps", ArrayList(updatedSelectedApps.map { app ->
                                    SelectedAppInfo(app.name, app.packageName)
                                }))
                                putExtra("isDiscreteMode", isDiscreteMode)
                            }
                            startService(overlayServiceIntent)
    
                            // Pass the updated selected apps to the result intent
                            val resultIntent = Intent().apply {
                                putExtra("chosenApps", ArrayList(updatedSelectedApps.map { app ->
                                    SelectedAppInfo(app.name, app.packageName)
                                }))
                            }
                            setResult(Activity.RESULT_OK, resultIntent)
                            moveTaskToBack(true) // Sends the app to the background
    
                            finish()
                        },
                        onHomeClick = {
                            val intent = Intent(this@ChooseAppsActivity, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        },
                        onLaunchAppClick = {
                            val packageName1 = "com.portworld.bootstartapp"
                            val packageName2 = "com.adw.bootapp"

                            val predefinedApps = listOf(
                                SelectedAppInfo("Elan", "com.homelogic"),
                                SelectedAppInfo("REBOOT", "REBOOT")
                            )
    
                            val packageManager = this.packageManager
    
    
                            // Function to check if a package exists
                            fun isAppInstalled(packageName: String): Boolean {
                                return try {
                                    packageManager.getPackageInfo(packageName, 0)
                                    true
                                } catch (e: PackageManager.NameNotFoundException) {
                                    false
                                }
                            }
    
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
                                val intent = packageManager.getLaunchIntentForPackage(packageName2)
                                if (intent != null) {
                                    startActivity(intent)
                                } else {
                                    Toast.makeText(this, "Unable to launch $packageName2", Toast.LENGTH_SHORT).show()
                                }
                            }
                            // Show a message if neither app is installed
                            else {
                                Toast.makeText(this, "Unable to find the specified apps.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onExitClick = {
    
                            val predefinedApps = listOf(
                                SelectedAppInfo("Elan", "com.homelogic"),
                                SelectedAppInfo("REBOOT", "REBOOT")
                            )
    
                            val packageManager = this.packageManager
    
    
                            // Function to check if a package exists
                            fun isAppInstalled(packageName: String): Boolean {
                                return try {
                                    packageManager.getPackageInfo(packageName, 0)
                                    true
                                } catch (e: PackageManager.NameNotFoundException) {
                                    false
                                }
                            }
    
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
    
        fun isAppInstalled(packageName: String): Boolean {
            return try {
                packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    
    
    
        private fun getInstalledApps(): List<AppInfo> {
            val packageManager = packageManager
            val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            // Specify the allowed system app package names
            val allowedSystemApps =  mutableSetOf(
                "com.homelogic",
                "com.lutron.mmw",
                "com.control4.phoenix",
                "com.rakocontrols.android",
                "com.sonos.acr2",
                "com.spotify.music",
                "com.craigd.lmsmaterial.app"
            )


            // Include any app whose package name contains "uk.org.ngo.squeezer"
            apps.forEach { app ->
                if (app.packageName.contains("uk.org.ngo.squeezer")) {
                    allowedSystemApps.add(app.packageName)
                }
            }
    
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
        onConfirmSelection: (List<AppInfo>, Boolean) -> Unit,
        onHomeClick: () -> Unit,
        onLaunchAppClick : () -> Unit,
        onExitClick: () -> Unit,
        foundApps : Boolean
    ) {
        var selectedApps by remember { mutableStateOf(setOf<AppInfo>()) }
        var selectedCategory by remember { mutableStateOf("All Apps") } // Default category

        val allowedSystemApps =  mutableSetOf(
            "com.homelogic",
            "com.lutron.mmw",
            "com.control4.phoenix",
            "com.rakocontrols.android",
            "com.sonos.acr2",
            "com.spotify.music",
            "com.craigd.lmsmaterial.app"
        )


        // Include any app whose package name contains "uk.org.ngo.squeezer"
        apps.forEach { app ->
            if (app.packageName.contains("uk.org.ngo.squeezer")) {
                allowedSystemApps.add(app.packageName)
            }
        }


    
        val filteredApps = when (selectedCategory) {
            "All Apps" ->   apps.filter { it.packageName in allowedSystemApps }
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
                        onClick = { onConfirmSelection(selectedApps.toList(), false) },
                        enabled = selectedApps.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFAEB00), // Button background color
                            contentColor = Color.Black         // Button text color
                        )
                    ) {
                        Text("Launch in Default Mode")
                    }
    
                    Button(
                        onClick = { onConfirmSelection(selectedApps.toList(), true) },
                        enabled = selectedApps.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFAEB00), // Button background color
                            contentColor = Color.Black         // Button text color
                        )
                    ) {
                        Text("Launch in Discrete Mode")
                    }
                }
    
                // Bottom Navigation Bar
                BottomNavBar(
                    selectedTab = selectedTab,
                    onHomeClick = onHomeClick,
                    onChooseAppsClick = {
                        // No-op since we're already on the Choose Apps screen
                    },
                onLaunchAppClick = onLaunchAppClick,
                    onExitClick = onExitClick,
                    foundApps = foundApps
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
