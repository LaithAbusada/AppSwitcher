package com.innovo.appswitcher

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.innovo.appswitcher.R

class OverlayActivity : ComponentActivity() {

    private lateinit var overlayButton: ImageButton
    private lateinit var containerView: FrameLayout
    private lateinit var windowManager: WindowManager
    private var iconsVisible = false

    companion object {
        var instance: OverlayActivity? = null
    }

    private var selectedApps: List<SelectedAppInfo>? = null // Store the selected apps

    private val requestOverlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            if (Settings.canDrawOverlays(this)) {
                if (selectedApps != null) {
                    setupOverlay(selectedApps!!)
                } else {
                    Toast.makeText(this, "No apps to display.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Overlay permission is required.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this // Assign the instance

        // Receive the selected apps from the Intent
        selectedApps = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("selectedApps", ArrayList::class.java) as? ArrayList<SelectedAppInfo>
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("selectedApps") as? ArrayList<SelectedAppInfo>
        }

        if (selectedApps.isNullOrEmpty()) {
            Toast.makeText(this, "No apps received to display", Toast.LENGTH_SHORT).show()
            Log.d("OverlayActivity", "No selected apps received")
            return
        }

        // Log the received apps
        selectedApps?.forEach { app ->
            Log.d("OverlayActivity", "App Name: ${app.name}, Package: ${app.packageName}")
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            requestOverlayPermissionLauncher.launch(intent)
        } else {
            setupOverlay(selectedApps!!)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        instance = null // Clear the instance to avoid memory leaks
        removeOverlay()
    }


    fun removeOverlay() {
        try {
            if (::overlayButton.isInitialized) {
                windowManager.removeView(overlayButton)
            }
            if (::containerView.isInitialized) {
                windowManager.removeView(containerView)
            }
        } catch (e: Exception) {
            Log.e("OverlayActivity", "Error removing overlay", e)
        }
    }






    private fun setupOverlay(selectedApps: List<SelectedAppInfo>) {
        // Main overlay button
        overlayButton = ImageButton(this).apply {
            setImageResource(R.mipmap.ic_launcher);
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { toggleIconsVisibility() }
        }

        val overlayButtonParams = createOverlayParams(Gravity.CENTER_VERTICAL or Gravity.END).apply {
            width = 80 // Set custom width
            height = 80 // Set custom height
        };
        windowManager.addView(overlayButton, overlayButtonParams)



        // Container for icons with background
        containerView = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CC000000")) // Semi-transparent black
            visibility = View.GONE // Initially hidden
        }

        val containerParams = createOverlayParams(Gravity.CENTER)
        windowManager.addView(containerView, containerParams)

        setupMenuIcons(selectedApps)
    }
    private fun setupMenuIcons(selectedApps: List<SelectedAppInfo>) {
        // Remove all previous views from the container
        containerView.removeAllViews()

        // Icon size and spacing
        val iconSize = 150 // Icon size
        val spacing = 20   // Spacing between items
        val titleHeight = 50 // Space for the title

        // Create a horizontal layout to contain all icons
        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = spacing // Add some margin from the top if needed
            }
        }

        selectedApps.forEach { app ->
            val iconDrawable = getAppIcon(app.packageName)

            // Create an icon button for each app
            val iconButton = ImageButton(this).apply {
                setImageDrawable(iconDrawable ?: getDrawable(R.mipmap.ic_launcher)) // Default icon if not found
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    openApp(app.packageName) // Open the app when clicked
                }
            }

            // Create a text view for the app title
            val title = TextView(this).apply {
                text = app.name
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
            }

            // Create a vertical layout for each item (icon + title)
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    iconSize + spacing,
                    iconSize + titleHeight
                )
                addView(iconButton, LinearLayout.LayoutParams(iconSize, iconSize))
                addView(title, LinearLayout.LayoutParams(iconSize, titleHeight))
            }

            // Add each item layout to the row layout
            rowLayout.addView(itemLayout)
        }

        // Add the row layout to the container view
        containerView.addView(rowLayout)
    }

    private fun openApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Unable to open app.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("OverlayActivity", "Error opening app $packageName", e)
            Toast.makeText(this, "Error opening app.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleIconsVisibility() {
        iconsVisible = !iconsVisible
        containerView.visibility = if (iconsVisible) View.VISIBLE else View.GONE
    }

    private fun createOverlayParams(gravity: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = android.graphics.PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            this.gravity = gravity
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }
    }

    private fun getAppIcon(packageName: String): Drawable? {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            Log.e("OverlayActivity", "Error fetching icon for $packageName", e)
            null
        }
    }
}
