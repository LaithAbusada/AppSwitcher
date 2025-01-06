package com.innovo.appswitcher

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.View.OVER_SCROLL_NEVER
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.innovo.appswitcher.R
import kotlin.math.absoluteValue

class OverlayService : Service() {

    private lateinit var overlayButton: ImageButton
    private lateinit var containerView: FrameLayout
    private lateinit var backgroundView: View
    private lateinit var windowManager: WindowManager
    private var iconsVisible = false
    private var selectedApps: List<SelectedAppInfo>? = null

    private val NOTIFICATION_CHANNEL_ID = "OverlayServiceChannel"
    private val NOTIFICATION_ID = 1
    private var clickCounter = 0
    private var lastClickTime = 0L
    private val CLICK_THRESHOLD = 6000L // 6 seconds in milliseconds
    private val REQUIRED_CLICKS = 9
    private var sequenceStartTime = 0L
    private var isTimerRunning = false



    override fun onBind(intent: Intent?): IBinder? {
        return null // This is a Service, not bound to an Activity
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Overlay Active")
            .setContentText("The overlay is running.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        selectedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getSerializableExtra("selectedApps", ArrayList::class.java) as? ArrayList<SelectedAppInfo>
        } else {
            @Suppress("DEPRECATION")
            intent?.getSerializableExtra("selectedApps") as? ArrayList<SelectedAppInfo>
        }

        val isDiscreteMode = intent?.getBooleanExtra("isDiscreteMode", false) ?: false

        if (selectedApps.isNullOrEmpty()) {
            Log.d("OverlayService", "No selected apps received")
            stopSelf()
            return START_NOT_STICKY
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission is required.", Toast.LENGTH_SHORT).show()
            stopSelf()
        } else {
            setupOverlay(selectedApps!!, isDiscreteMode)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Notification channel for overlay service"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun setupOverlay(selectedApps: List<SelectedAppInfo>, isDiscreteMode : Boolean) {
        clearExistingOverlays()

        overlayButton = ImageButton(this).apply {
            setBackgroundResource(if (isDiscreteMode) R.drawable.half_circle_discrete else R.drawable.half_circle)
            setOnClickListener {
                toggleIconsVisibility()
            }
            layoutParams = ViewGroup.LayoutParams(25, 50) // Match the drawable dimensions
            alpha = if (isDiscreteMode) 0.10f else 0.6f // 15% opacity for discrete mode, 60% otherwise

        }

        val overlayButtonParams = createOverlayParams(Gravity.TOP or Gravity.START).apply {
            val displayMetrics = resources.displayMetrics

            // Calculate position for bottom-right corner
            x = displayMetrics.widthPixels - width // Align to the right edge
            y = displayMetrics.heightPixels - height // Align to the bottom edge
        }

        overlayButton.setOnTouchListener(@SuppressLint("ClickableViewAccessibility")
        object : View.OnTouchListener {
            private var initialY = 0
            private var initialTouchY = 0f
            private var initialTouchX = 0f
            private var isMoving = false

            override fun onTouch(view: View?, motionEvent: android.view.MotionEvent): Boolean {
                when (motionEvent.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialY = overlayButtonParams.y
                        initialTouchY = motionEvent.rawY
                        initialTouchX = motionEvent.rawX
                        isMoving = false
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val deltaY = motionEvent.rawY - initialTouchY
                        val deltaX = motionEvent.rawX - initialTouchX
                        if (deltaY.absoluteValue > 10 || deltaX.absoluteValue > 10) {
                            isMoving = true
                        }

                        // Update only the y-coordinate
                        overlayButtonParams.y = initialY + deltaY.toInt()

                        // Keep the button within the screen bounds vertically
                        val displayMetrics = view?.context?.resources?.displayMetrics
                        val screenHeight = displayMetrics?.heightPixels ?: 0
                        overlayButtonParams.y = overlayButtonParams.y.coerceIn(0, screenHeight - overlayButton.height)

                        // Apply the updated layout during dragging
                        windowManager.updateViewLayout(overlayButton, overlayButtonParams)
                        return true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (!isMoving) {
                            view?.performClick()
                            return true
                        }

                        // Determine whether to snap to the left or right edge
                        val displayMetrics = view?.context?.resources?.displayMetrics
                        val screenWidth = displayMetrics?.widthPixels ?: 0
                        val snapToLeft = motionEvent.rawX < screenWidth / 2

                        // Update background based on edge
                        overlayButton.setBackgroundResource(
                            if (snapToLeft) {
                                if (isDiscreteMode) R.drawable.half_circle_left_discrete else R.drawable.half_circle_left
                            } else {
                                if (isDiscreteMode) R.drawable.half_circle_discrete else R.drawable.half_circle
                            }                        )

                        // Snap the button to the nearest edge (left or right)
                        overlayButtonParams.x = if (snapToLeft) {
                            0 // Snap to the left edge
                        } else {
                            screenWidth - overlayButton.width // Snap to the right edge
                        }

                        // Apply the updated layout after release
                        windowManager.updateViewLayout(overlayButton, overlayButtonParams)
                        return true
                    }
                }
                return false
            }
        })
        overlayButton.setOnClickListener {
            toggleIconsVisibility()
        }

        windowManager.addView(overlayButton, overlayButtonParams)

        backgroundView = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
            setOnClickListener {
                toggleIconsVisibility()
            }
        }

        val backgroundParams = createOverlayParams(Gravity.TOP or Gravity.START).apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }

        windowManager.addView(backgroundView, backgroundParams)

        containerView = FrameLayout(this).apply {
            // Create a dynamic rounded background
            background = GradientDrawable().apply {
                setColor(Color.BLACK) // Dark Navy Color

                // Set corner radii for each corner (top-left, top-right, bottom-right, bottom-left)
                cornerRadii = floatArrayOf(
                    100f, 100f, // Top-left radius
                    0f, 0f,     // Top-right radius
                    0f, 0f,     // Bottom-right radius
                    100f, 100f  // Bottom-left radius
                )
            }
            setPadding(35, 0, 35, 0) // Left, top, right, bottom padding

            visibility = View.GONE
        }

        val containerParams = createOverlayParams(Gravity.CENTER)
        windowManager.addView(containerView, containerParams)

        setupMenuIcons(selectedApps)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun setupMenuIcons(selectedApps: List<SelectedAppInfo>) {
        containerView.removeAllViews()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Calculate dynamic icon size (e.g., 10% of screen width)
        val iconSize = (screenWidth * 0.2).toInt() // 10% of screen width
        val spacing = dpToPx(10) // Space between icons
        val padding = dpToPx(100) // Padding for the rowLayout

        // Create a HorizontalScrollView to enable horizontal scrolling
        val horizontalScrollView = HorizontalScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false // Disables the horizontal scrollbar
            clipToPadding = true
            overScrollMode = OVER_SCROLL_NEVER // Disables the stretch/overscroll effect

        }



        // Create a horizontal LinearLayout for the app icons
        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )

        }


        selectedApps.forEach { app ->
            val originalDrawable = getAppIcon(app.packageName) ?: getDrawable(R.mipmap.ic_launcher)

            // Convert the drawable to a resized BitmapDrawable
            val iconDrawable = originalDrawable?.let {
                val resizedBitmap = drawableToBitmap(it, iconSize - (iconSize * 0.4).toInt()) // Resize to 90% of iconSize
                BitmapDrawable(resources, resizedBitmap)
            }


            // Create a circular ImageButton with a yellow border
            val iconButton = ImageButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    setMargins(spacing, spacing, spacing, spacing) // Add consistent spacing between icons
                }
                setImageDrawable(iconDrawable ?: getDrawable(R.mipmap.ic_launcher)) // Set app icon or default

                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL // Circular shape
                    setColor(Color.TRANSPARENT) // Transparent background inside the button
                    setStroke(4, Color.parseColor("#FFFAEB00")) // Yellow border
                }
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_INSIDE// Ensure the image fits within the button bounds
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8)) // Add padding inside the button


                if (app.packageName == "REBOOT") {
                    // Handle long press for REBOOT
                    setOnTouchListener(object : View.OnTouchListener {
                        private var isLongPress = false
                        private val longPressHandler = android.os.Handler()
                        private val longPressRunnable = Runnable {
                            isLongPress = true
                            rebootDevice() // Trigger reboot on long press
                        }

                        override fun onTouch(v: View?, event: android.view.MotionEvent): Boolean {
                            when (event.action) {
                                android.view.MotionEvent.ACTION_DOWN -> {
                                    isLongPress = false
                                    longPressHandler.postDelayed(longPressRunnable, 5000) // 5 seconds delay
                                }
                                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                    longPressHandler.removeCallbacks(longPressRunnable) // Cancel long press detection
                                    if (!isLongPress) {
                                        // Handle normal click
                                        if (!isTimerRunning) {
                                            // Start the 7-second timer on the first click
                                            isTimerRunning = true
                                            sequenceStartTime = System.currentTimeMillis()
                                            clickCounter = 1 // Count the first click

                                            // Start a handler to evaluate clicks after 7 seconds
                                            Handler(Looper.getMainLooper()).postDelayed({
                                                validateClicks {
                                                    openApp() // Show your app after 9 clicks
                                                }
                                                isTimerRunning = false // Reset the timer flag
                                            }, CLICK_THRESHOLD)
                                        } else {
                                            // Increment the click counter for subsequent clicks
                                            clickCounter++
                                        }

                                    }
                                }
                            }
                            return true
                        }
                    })
                } else {
                    // Handle normal app icon clicks
                    setOnClickListener {
                        openApp(app.packageName)
                        toggleIconsVisibility()  // Hide the drawer
                    }
                }
            }

            // Wrap the iconButton in a vertical LinearLayout
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    iconSize + spacing,
                    iconSize + spacing // Adjusted for proper spacing
                )
                addView(iconButton, LinearLayout.LayoutParams(iconSize, iconSize))
            }

            // Add the itemLayout to the rowLayout
            rowLayout.addView(itemLayout)
        }

        // Add the rowLayout to the HorizontalScrollView
        horizontalScrollView.addView(rowLayout)

        // Add the HorizontalScrollView to the containerView
        containerView.addView(horizontalScrollView)
    }
    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        return if (drawable is BitmapDrawable) {
            // Resize the existing bitmap if it doesn't match the required size
            val originalBitmap = drawable.bitmap
            if (originalBitmap.width != size || originalBitmap.height != size) {
                Bitmap.createScaledBitmap(originalBitmap, size, size, true)
            } else {
                originalBitmap
            }
        } else if (drawable is AdaptiveIconDrawable) {
            // Handle AdaptiveIconDrawable
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap
        } else {
            // Fallback for other drawable types
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap
        }
    }

    private fun validateClicks(action: () -> Unit) {
        if (clickCounter == REQUIRED_CLICKS) {
            action() // Perform the action (open the app)
        }
        // Reset the state regardless of success or failure
        sequenceStartTime = 0L
        clickCounter = 0
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
            Log.e("OverlayService", "Error opening app $packageName", e)
            Toast.makeText(this, "Error opening app.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun
            toggleIconsVisibility() {
        iconsVisible = !iconsVisible

        // Get the location of the overlayButton on the screen
        val overlayButtonLocation = IntArray(2)
        overlayButton.getLocationOnScreen(overlayButtonLocation)
        val overlayButtonY = overlayButtonLocation[1] // Y-position of the overlayButton
        val overlayButtonX = overlayButtonLocation[0] // X-position of the overlayButton
        val overlayButtonHeight = overlayButton.height

        // Determine if the button is on the left or right edge
        val isOnLeftEdge = overlayButtonX == 0

        if (iconsVisible) {
            // Calculate containerView's position based on the overlayButton's position
            val containerParams = containerView.layoutParams as WindowManager.LayoutParams

            if (isOnLeftEdge) {
                // Align container to the left of the screen
                containerParams.x = 0
                containerParams.gravity = Gravity.TOP or Gravity.START
                containerView.translationX = -containerView.width.toFloat() // Start off-screen (left)
                // Update corner radii for left edge
                (containerView.background as GradientDrawable).cornerRadii = floatArrayOf(
                    0f, 0f,       // Top-left radius
                    100f, 100f,   // Top-right radius
                    100f, 100f,   // Bottom-right radius
                    0f, 0f        // Bottom-left radius
                )
            } else {
                // Align container to the right of the screen
                containerParams.x = 0
                containerParams.gravity = Gravity.TOP or Gravity.END
                containerView.translationX = containerView.width.toFloat()

                (containerView.background as GradientDrawable).cornerRadii = floatArrayOf(
                    100f, 100f,   // Top-left radius
                    0f, 0f,       // Top-right radius
                    0f, 0f,       // Bottom-right radius
                    100f, 100f    // Bottom-left radius
                )// Start off-screen (right)
            }

            // Align container vertically relative to the overlayButton
            containerParams.y = overlayButtonY - (overlayButtonHeight / 2)

            // Update the layout with the calculated parameters
            windowManager.updateViewLayout(containerView, containerParams)

            // Add sliding animation to show the drawer
            containerView.animate()
                .translationX(0f) // Fully visible
                .setDuration(300) // Duration in milliseconds
                .start()

            containerView.visibility = View.VISIBLE
            backgroundView.visibility = View.VISIBLE
        } else {
            // Add sliding animation to hide the drawer
            containerView.animate()
                .translationX(
                    if (isOnLeftEdge) -containerView.width.toFloat() // Slide out to left
                    else containerView.width.toFloat() // Slide out to right
                )
                .setDuration(300)
                .withEndAction {
                    containerView.visibility = View.GONE
                    backgroundView.visibility = View.GONE
                }
                .start()
        }
    }
    private fun createOverlayParams(gravity: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            this.gravity = gravity
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }
    }
    private fun clearExistingOverlays() {
        try {
            // Remove the overlayButton if it's initialized
            if (::overlayButton.isInitialized) {
                windowManager.removeView(overlayButton)
            }

            // Remove the containerView if it's initialized
            if (::containerView.isInitialized) {
                windowManager.removeView(containerView)
            }

            // Remove the backgroundView if it's initialized
            if (::backgroundView.isInitialized) {
                windowManager.removeView(backgroundView)
            }
        } catch (e: Exception) {
            Log.e("OverlayService", "Error clearing existing overlays", e)
        }
    }

    private fun getAppIcon(packageName: String): Drawable? {

       return if (packageName=="REBOOT") {
            getDrawable(R.drawable.reboot_xm)!!        }
        else
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            Log.e("OverlayService", "Error fetching icon for $packageName", e)
            null
        }
    }
    private fun rebootDevice() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                powerManager.reboot(null) // Reboots the device normally
            } else {
                throw UnsupportedOperationException("Reboot not supported on this version.")
            }
        } catch (e: Exception) {
            Log.e("OverlayService", "Reboot failed: ", e)
            Toast.makeText(this, "Reboot failed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppSwitcher() {
        val appSwitcherPackage = applicationContext.packageName

        // Check if "App Switcher" already exists
        val existingAppSwitcher = selectedApps?.find { it.packageName == appSwitcherPackage }

        if (existingAppSwitcher != null) {
            // Remove "App Switcher" if it already exists
            selectedApps = selectedApps?.filter { it.packageName != appSwitcherPackage }
            Toast.makeText(this, "App Switcher removed!", Toast.LENGTH_SHORT).show()
        } else {
            // Add "App Switcher" if it doesn't exist
            val appSwitcher = SelectedAppInfo(
                name = "App Switcher",
                packageName = appSwitcherPackage,
            )
            selectedApps = selectedApps.orEmpty() + appSwitcher
            Toast.makeText(this, "App Switcher added!", Toast.LENGTH_SHORT).show()
        }

        // Update the UI to reflect the change
        setupMenuIcons(selectedApps.orEmpty())
    }


    private fun handleNineClickSequence(action: () -> Unit) {
        Log.d("TestButton","in nine clicks");

        val currentTime = System.currentTimeMillis()

        // Start the sequence timer on the first click
        if (sequenceStartTime == 0L || currentTime - sequenceStartTime > CLICK_THRESHOLD) {
            sequenceStartTime = currentTime
            clickCounter = 0 // Reset counter if outside the threshold
        }

        clickCounter++

        if (clickCounter == REQUIRED_CLICKS && currentTime - sequenceStartTime <= CLICK_THRESHOLD) {
            clickCounter = 0 // Reset counter
            sequenceStartTime = 0L // Reset the sequence timer
openApp()
        } else if (currentTime - sequenceStartTime > CLICK_THRESHOLD) {
            // Reset if the time exceeds the threshold during the sequence
            clickCounter = 0
            sequenceStartTime = 0L
        }
    }


    private fun openApp() {
        try {
            val mainActivityIntent = Intent(this, MainPageActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(mainActivityIntent)
        } catch (e: Exception) {
            Log.e("OverlayService", "Error opening the app", e)
            Toast.makeText(this, "Error opening the app.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun removeOverlay() {
        try {
            if (::overlayButton.isInitialized) {
                windowManager.removeView(overlayButton)
            }
            if (::containerView.isInitialized) {
                windowManager.removeView(containerView)
            }
            if (::backgroundView.isInitialized) {
                windowManager.removeView(backgroundView)
            }
        } catch (e: Exception) {
            Log.e("OverlayService", "Error removing overlay", e)
        }
    }
}
