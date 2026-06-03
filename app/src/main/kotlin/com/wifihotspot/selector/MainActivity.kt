package com.wifihotspot.selector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.*
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var statusText: TextView

    private val TAG = "WifiSelector"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        statusText = findViewById(R.id.statusText)

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        forceWifiOn()
    }

    @Suppress("DEPRECATION")
    private fun forceWifiOn() {
        // Always try to force WiFi on first
        if (!wifiManager.isWifiEnabled) {
            updateStatus("Forcing WiFi ON...")
            Log.d(TAG, "WiFi is off, attempting to enable")

            // Try to kill SoftAP first
            killSoftAp()

            // Try setWifiEnabled (works on Android 9 and below)
            val success = try {
                wifiManager.setWifiEnabled(true)
            } catch (e: Exception) {
                Log.e(TAG, "setWifiEnabled exception: ${e.message}")
                false
            }

            if (success) {
                Log.d(TAG, "setWifiEnabled returned true, waiting...")
                statusText.postDelayed({ tryBind() }, 1500)
            } else {
                Log.d(TAG, "setWifiEnabled failed")
                // On Android 10+, use Settings panel
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    updateStatus("Tap to enable WiFi")
                    statusText.setOnClickListener {
                        startActivity(Intent(Settings.Panel.ACTION_WIFI))
                    }
                    // Auto-open panel
                    startActivity(Intent(Settings.Panel.ACTION_WIFI))
                } else {
                    tryBind()
                }
            }
        } else {
            tryBind()
        }
    }

    @Suppress("DEPRECATION")
    private fun killSoftAp() {
        try {
            // Try multiple reflection methods to disable SoftAP
            val setWifiApEnabled = wifiManager.javaClass.getMethod(
                "setWifiApEnabled",
                android.net.wifi.WifiConfiguration::class.java,
                Boolean::class.javaPrimitiveType
            )
            setWifiApEnabled.invoke(wifiManager, null, false)
            Log.d(TAG, "killSoftAp: setWifiApEnabled(null, false) called")
        } catch (e: Exception) {
            Log.d(TAG, "killSoftAp failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release the hotspot binding
        try {
            connectivityManager.bindProcessToNetwork(null)
            Log.d(TAG, "Released binding")
        } catch (e: Exception) {}
    }

    private fun checkPermissions() {
        val needed = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        }

        // WRITE_SETTINGS requires special handling
        if (!Settings.System.canWrite(this)) {
            Log.d(TAG, "Requesting WRITE_SETTINGS permission")
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private var settingsOpened = false

    private fun tryBind() {
        val ssid = getCurrentSsid()

        if (ssid != null) {
            settingsOpened = false
            bindToWifi(ssid)
        } else {
            if (!wifiManager.isWifiEnabled) {
                // WiFi still off after forceWifiOn attempt
                showBlockedByHotspot()
            } else {
                // WiFi enabled but not connected
                if (!settingsOpened) {
                    settingsOpened = true
                    updateStatus("Opening WiFi Settings...")
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                } else {
                    updateStatus("No WiFi connected\n\nTap to open Settings")
                    statusText.setOnClickListener {
                        settingsOpened = true
                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    }
                }
            }
        }
    }

    private var accessibilityPrompted = false

    private fun showBlockedByHotspot() {
        val apState = getWifiApState()
        if (apState == 13 || apState == 12) { // AP_STATE_ENABLED or AP_STATE_ENABLING
            // Check if accessibility service is enabled
            if (CarPlayKillerService.isServiceEnabled(this)) {
                // Auto-trigger WiFi enable
                updateStatus("Enabling WiFi...")
                CarPlayKillerService.triggerEnableWifi()
            } else {
                // Need accessibility - prompt once
                if (!accessibilityPrompted) {
                    accessibilityPrompted = true
                    updateStatus("Setup required\n\nEnable accessibility service")
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                } else {
                    updateStatus("Enable accessibility service\n\nTap to open settings")
                    statusText.setOnClickListener {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                    }
                }
            }
        } else {
            updateStatus("Cannot enable WiFi\n\nTap to retry")
            statusText.setOnClickListener {
                forceWifiOn()
            }
        }
    }

    private fun getWifiApState(): Int {
        return try {
            val method = wifiManager.javaClass.getMethod("getWifiApState")
            method.invoke(wifiManager) as Int
        } catch (e: Exception) {
            Log.d(TAG, "getWifiApState failed: ${e.message}")
            0
        }
    }

    @Suppress("DEPRECATION")
    private fun getCurrentSsid(): String? {
        val info = wifiManager.connectionInfo ?: return null
        val ssid = info.ssid ?: return null
        if (ssid == "<unknown ssid>") return null
        return ssid.trim('"')
    }

    @Suppress("DEPRECATION")
    private fun bindToWifi(ssid: String) {
        try {
            for (network in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    connectivityManager.bindProcessToNetwork(network)
                    updateStatus("OWNED: $ssid")
                    Log.d(TAG, "Bound to $ssid: $network")
                    return
                }
            }
            updateStatus("WiFi not available")
        } catch (e: Exception) {
            Log.e(TAG, "Bind failed", e)
            updateStatus("Error: ${e.message}")
        }
    }

    private fun updateStatus(msg: String) {
        Log.d(TAG, msg)
        statusText.text = msg
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) tryBind()
    }
}
