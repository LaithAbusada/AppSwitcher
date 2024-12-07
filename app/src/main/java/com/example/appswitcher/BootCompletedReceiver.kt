package com.example.appswitcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import org.json.JSONArray

class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "onReceive called")

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

        // Add predefined apps if they don't already exist
        val predefinedApps = listOf(
            SelectedAppInfo("Sonos", "com.sonos.arc2"),
            SelectedAppInfo("Spotify", "com.spotify.music"),
            SelectedAppInfo("Elan", "com.homelogic"),
            SelectedAppInfo("Control4", "com.control4.phoenix") ,
            SelectedAppInfo("REBOOT", "REBOOT")
        )

        predefinedApps.forEach { predefinedApp ->
            if (savedApps.none { it.packageName == predefinedApp.packageName }) {
                savedApps.add(predefinedApp)
            }
        }

        // Start the OverlayService
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
    }
}
