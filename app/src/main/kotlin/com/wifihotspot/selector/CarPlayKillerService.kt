package com.wifihotspot.selector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CarPlayKillerService : AccessibilityService() {

    companion object {
        private const val TAG = "WifiEnabler"

        var isRunning = false
        var shouldEnableWifi = false
        private var instance: CarPlayKillerService? = null

        fun triggerEnableWifi() {
            shouldEnableWifi = true
            instance?.openWifiSettings()
        }

        fun isServiceEnabled(context: android.content.Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(context.packageName)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var clickAttempts = 0
    private val maxAttempts = 15
    private var wifiEnabled = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        instance = this
        Log.d(TAG, "Accessibility service connected")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!shouldEnableWifi) return
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowChange()
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    private fun openWifiSettings() {
        clickAttempts = 0
        wifiEnabled = false
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Log.d(TAG, "Opened WiFi settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open WiFi settings: ${e.message}")
            shouldEnableWifi = false
        }
    }

    private fun handleWindowChange() {
        if (clickAttempts >= maxAttempts) {
            Log.d(TAG, "Max attempts reached, returning to app")
            shouldEnableWifi = false
            returnToApp()
            return
        }

        handler.postDelayed({
            val rootNode = rootInActiveWindow ?: return@postDelayed

            // Look for WiFi toggle/switch and enable it
            if (!wifiEnabled && findAndEnableWifiToggle(rootNode)) {
                Log.d(TAG, "Enabled WiFi toggle")
                wifiEnabled = true
                // Wait for WiFi to come up then return
                handler.postDelayed({ returnToApp() }, 2000)
                shouldEnableWifi = false
                return@postDelayed
            }

            clickAttempts++
        }, 300)
    }

    private fun findAndEnableWifiToggle(root: AccessibilityNodeInfo): Boolean {
        // Try common WiFi toggle IDs
        val toggleIds = listOf(
            "com.android.settings:id/switch_widget",
            "com.android.settings:id/switchWidget",
            "com.android.settings:id/switch_bar",
            "android:id/switch_widget",
            "android:id/checkbox",
            "android:id/toggle"
        )

        for (id in toggleIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (node.isCheckable && !node.isChecked && node.isEnabled) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Clicked toggle: $id")
                    return true
                }
            }
        }

        // Try finding by text "Wi-Fi" or "WiFi" and clicking nearby switch
        val wifiLabels = listOf("Wi-Fi", "WiFi", "WLAN")
        for (label in wifiLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                // Try to find a sibling or parent switch
                val switch = findNearbySwitch(node)
                if (switch != null && !switch.isChecked) {
                    switch.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Clicked switch near: $label")
                    return true
                }
            }
        }

        // Try finding any unchecked switch/toggle
        if (findAndClickFirstSwitch(root)) {
            return true
        }

        return false
    }

    private fun findNearbySwitch(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Check parent for switch
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i) ?: continue
                if (child.isCheckable) {
                    return child
                }
                // Check grandchildren
                for (j in 0 until child.childCount) {
                    val grandchild = child.getChild(j) ?: continue
                    if (grandchild.isCheckable) {
                        return grandchild
                    }
                }
            }
            parent = parent.parent
            depth++
        }
        return null
    }

    private fun findAndClickFirstSwitch(node: AccessibilityNodeInfo): Boolean {
        if (node.isCheckable && !node.isChecked && node.isEnabled) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Clicked first available switch")
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickFirstSwitch(child)) {
                return true
            }
        }
        return false
    }

    private fun returnToApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (intent != null) {
                startActivity(intent)
                Log.d(TAG, "Returned to app")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to return to app: ${e.message}")
        }
    }
}
