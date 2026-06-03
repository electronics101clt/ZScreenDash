package com.wifihotspot.selector

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.*
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import androidx.annotation.RequiresApi
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var statusText: TextView
    private lateinit var webView: WebView
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val TAG = "WifiSelector"
    private val ESP32_URL = "http://192.168.4.1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        statusText = findViewById(R.id.statusText)
        webView = findViewById(R.id.webView)

        setupWebView()
        checkPermissions()
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Hide status immediately when page starts loading
                statusText.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                statusText.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, request, error)
                updateStatus("Connection error")
            }
        }

        // Start hidden
        webView.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        forceWifiOn()
    }

    @Suppress("DEPRECATION")
    private fun forceWifiOn() {
        // Always try to force WiFi on first
        if (!wifiManager.isWifiEnabled) {
            Log.d(TAG, "WiFi is off, attempting to enable")

            // Try to kill SoftAP and Bluetooth first
            killSoftAp()
            killBluetooth()

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
                    // Auto-open panel silently
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

    @Suppress("DEPRECATION")
    private fun killBluetooth() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                val success = bluetoothAdapter.disable()
                Log.d(TAG, "killBluetooth: disable() returned $success")
                if (!success) {
                    Log.d(TAG, "killBluetooth: automatic disable failed")
                }
            } else {
                Log.d(TAG, "killBluetooth: Bluetooth already off or not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "killBluetooth failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Unregister network callback for API 29+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            networkCallback?.let {
                try {
                    connectivityManager.unregisterNetworkCallback(it)
                    Log.d(TAG, "Unregistered network callback")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unregister callback", e)
                }
            }
        }

        // Release binding for all versions
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
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                } else {
                    updateStatus("No WiFi connected\n\nTap to open Settings", true)
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
            // Try to kill Bluetooth to prevent CarPlay interference
            killBluetooth()

            // Check if accessibility service is enabled
            if (CarPlayKillerService.isServiceEnabled(this)) {
                // Auto-trigger WiFi enable silently
                CarPlayKillerService.triggerEnableWifi()
            } else {
                // Need accessibility - prompt once
                if (!accessibilityPrompted) {
                    accessibilityPrompted = true
                    updateStatus("Setup required\n\nEnable accessibility service", true)
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                } else {
                    updateStatus("Enable accessibility service\n\nTap to open settings", true)
                    statusText.setOnClickListener {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                    }
                }
            }
        } else {
            updateStatus("Cannot enable WiFi\n\nTap to retry", true)
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

    private fun bindToWifi(ssid: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bindToWifiApi29Plus(ssid)
        } else {
            bindToWifiLegacy(ssid)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun bindToWifiApi29Plus(ssid: String) {
        try {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connectivityManager.bindProcessToNetwork(network)
                    Log.d(TAG, "Bound to $ssid (API 29+)")
                    loadEsp32Interface()
                }

                override fun onUnavailable() {
                    updateStatus("WiFi not available", true)
                    Log.d(TAG, "WiFi connection unavailable")
                }

                override fun onLost(network: Network) {
                    updateStatus("Connection lost", true)
                    Log.d(TAG, "Lost WiFi connection")
                }
            }

            networkCallback = callback
            connectivityManager.requestNetwork(request, callback)
        } catch (e: Exception) {
            Log.e(TAG, "API 29+ bind failed", e)
            updateStatus("Error: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun bindToWifiLegacy(ssid: String) {
        try {
            for (network in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    connectivityManager.bindProcessToNetwork(network)
                    Log.d(TAG, "Bound to $ssid (legacy)")
                    loadEsp32Interface()
                    return
                }
            }
            updateStatus("WiFi not available", true)
        } catch (e: Exception) {
            Log.e(TAG, "Legacy bind failed", e)
            updateStatus("Error: ${e.message}", true)
        }
    }

    private fun loadEsp32Interface() {
        runOnUiThread {
            webView.visibility = View.VISIBLE
            statusText.visibility = View.GONE
            webView.loadUrl(ESP32_URL)
            Log.d(TAG, "Loading ESP32 interface: $ESP32_URL")
        }
    }

    private fun updateStatus(msg: String, show: Boolean = true) {
        Log.d(TAG, msg)
        runOnUiThread {
            statusText.text = msg
            statusText.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) tryBind()
    }
}
