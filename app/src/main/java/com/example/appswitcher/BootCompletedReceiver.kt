package com.innovo.appswitcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import org.json.JSONArray

class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {

        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "BOOT_COMPLETED action received")

            context?.let { ctx ->
                // Check if overlay permission is granted
                if (Settings.canDrawOverlays(ctx)) {
                    Log.d(TAG, "Overlay permission granted. Starting OverlayService.")
                    // Start the OverlayService with predefined apps
                    startOverlayServiceWithPredefinedApps(ctx)
                } else {
                    Log.w(TAG, "Overlay permission not granted.")
                    Toast.makeText(ctx, "Overlay permission is required to start the service.", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Log.w(TAG, "Received unexpected action: ${intent?.action}")
        }
    }

    fun getInstalledApps(context: Context): List<AppInfo> {
        val packageManager = context.packageManager
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        return apps.map { app ->
            AppInfo(
                name = app.loadLabel(packageManager).toString(), // Human-readable app name
                packageName = app.packageName, // Package name
                icon = app.loadIcon(packageManager) // App icon
            )
        }
    }

    private fun startOverlayServiceWithPredefinedApps(context: Context) {

        val sharedPreferences = context.getSharedPreferences("AppSwitcherPrefs", Context.MODE_PRIVATE)
        val appsJson = sharedPreferences.getString("selectedApps", null)

        val savedApps = mutableListOf<SelectedAppInfo>()
        if (appsJson != null) {
            try {
                val jsonArray = JSONArray(appsJson)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val name = jsonObject.getString("name")
                    val packageName = jsonObject.getString("packageName")
                    savedApps.add(SelectedAppInfo(name, packageName))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    Log.d(TAG,savedApps.size.toString());
        if (savedApps.isEmpty() || (savedApps.size == 1 && savedApps[0].name == "REBOOT")) {
            // Add predefined apps if they exist on the device
            val predefinedApps = mutableListOf(
                SelectedAppInfo("Elan", "com.homelogic"),
                SelectedAppInfo("REBOOT", "REBOOT")
            )

            val packageManager = context.packageManager









            predefinedApps.forEach { predefinedApp ->
                try {
                    packageManager.getPackageInfo(predefinedApp.packageName, 0) // Check if the app exists
                    if (savedApps.none { it.packageName == predefinedApp.packageName }) {
                        savedApps.add(predefinedApp)
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.d(TAG, "App not installed: ${predefinedApp.packageName}")
                }
            }
            // Ensure "REBOOT" is the last element
            val rebootApp = savedApps.find { it.packageName == "REBOOT" }
            if (rebootApp != null) {
                savedApps.remove(rebootApp) // Remove "REBOOT" if it exists
                savedApps.add(rebootApp)   // Add "REBOOT" back as the last element
            } else {
                // Add "REBOOT" if it doesn't exist
                savedApps.add(SelectedAppInfo("REBOOT", "REBOOT"))
            }
        }

        if (savedApps.isNotEmpty()) {

            // Start the OverlayService with the savedApps list
            val serviceIntent = Intent(context, OverlayService::class.java).apply {
                putExtra("selectedApps", ArrayList(savedApps))
            }

            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            Log.d(TAG, "Service not started. No apps available in 'savedApps'.")
        }
    }
}
