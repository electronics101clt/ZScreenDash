# ZScreen Dash

Android app that automatically connects to ESP32 WiFi networks and displays their web interface, bypassing hotspot mode and CarPlay interference.

## Features

- **Automatic WiFi Connection**: Forces WiFi on and connects to ESP32 access points
- **Hotspot Override**: Kills device hotspot to free WiFi hardware
- **CarPlay Bypass**: Automatically disables Bluetooth to prevent CarPlay interference
- **State Restoration**: Restores Bluetooth and hotspot when app closes
- **WebView Interface**: Displays ESP32 web interface directly in app
- **Multi-Android Support**: Works on Android 8.0, 9.0, and 10.0+

## How It Works

### Android 10+ (API 29+)
Uses WiFi Network Request API with `removeCapability(NET_CAPABILITY_INTERNET)` to suppress "no internet" warnings and maintain stable connection to local ESP32 networks.

### Android 8-9 (API 26-28)
Uses legacy `bindProcessToNetwork()` method to maintain WiFi connection. System notifications may appear but connection remains stable.

## App Behavior

### When App Opens:
1. Saves current Bluetooth and hotspot state
2. Disables Bluetooth (prevents CarPlay auto-connect)
3. Kills WiFi hotspot (frees WiFi hardware)
4. Forces WiFi client mode on
5. Connects to ESP32 WiFi network
6. Displays ESP32 web interface at 192.168.4.1

### When App Closes:
1. Releases WiFi binding
2. Restores Bluetooth to original state
3. Restores hotspot to original state
4. Returns control to Android system

**No persistent interference** - app only affects WiFi/Bluetooth while running.

## ESP32 Configuration

Compatible with ESP32 access points using:
- **SSID**: ESP32-Control (configurable)
- **Password**: 12345678 (configurable)
- **IP**: 192.168.4.1
- **Protocol**: HTTP (cleartext)

See companion project: [esp32_webserver](https://github.com/electronics101clt/esp32_webserver)

## Permissions Required

- `INTERNET` - WebView access to ESP32
- `BLUETOOTH` - Query Bluetooth state
- `BLUETOOTH_ADMIN` - Disable/enable Bluetooth
- `ACCESS_WIFI_STATE` - Check WiFi status
- `CHANGE_WIFI_STATE` - Force WiFi on/off
- `ACCESS_NETWORK_STATE` - Monitor network connection
- `CHANGE_NETWORK_STATE` - Bind to WiFi network
- `ACCESS_FINE_LOCATION` - Required for WiFi scanning (Android requirement)
- `ACCESS_COARSE_LOCATION` - Required for WiFi scanning
- `WRITE_SETTINGS` - Modify system settings

### Optional: Accessibility Service
For devices where hotspot blocks WiFi forcing, the app includes an accessibility service that can automatically tap the WiFi toggle in Settings. User must manually enable this in Android Settings → Accessibility.

## Installation

### From Source:
```bash
git clone https://github.com/electronics101clt/ZScreenDash.git
cd ZScreenDash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### From APK:
Download latest APK from [Releases](https://github.com/electronics101clt/ZScreenDash/releases) and install via ADB or file manager.

## Usage

1. Flash ESP32 with web server firmware
2. Install ZScreen Dash on Android device
3. Open ZScreen Dash app
4. App automatically connects to ESP32
5. Web interface appears
6. Close app when done (restores original state)

## Technical Details

### WiFi Network Request API (Android 10+)

```kotlin
val specifier = WifiNetworkSpecifier.Builder()
    .setSsid("ESP32-Control")
    .build()

val request = NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .setNetworkSpecifier(specifier)
    .build()

connectivityManager.requestNetwork(request, callback)
```

The key line `.removeCapability(NET_CAPABILITY_INTERNET)` tells Android this is an intentional local-only network, preventing "no internet" warnings and auto-disconnection.

### Bluetooth Disabling

```kotlin
val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
bluetoothAdapter.disable()
```

Prevents CarPlay from auto-connecting via Bluetooth while app is active. Restored on app close.

### Hotspot Killing

```kotlin
val setWifiApEnabled = wifiManager.javaClass.getMethod(
    "setWifiApEnabled",
    android.net.wifi.WifiConfiguration::class.java,
    Boolean::class.javaPrimitiveType
)
setWifiApEnabled.invoke(wifiManager, null, false)
```

Uses reflection to access hidden Android API for disabling WiFi hotspot.

## Compatibility

- **Android 8.0 (API 26)** - Minimum supported version
- **Android 9.0 (API 28)** - Full support, may show "no internet" notification
- **Android 10.0+ (API 29+)** - Full support, no warnings
- **Android 12 (API 31)** - Bluetooth disable works
- **Android 13+ (API 33+)** - Bluetooth disable restricted for non-system apps

## Use Cases

- Car head units with CarPlay that block WiFi
- IoT device configuration and control
- ESP32-based projects requiring persistent WiFi connection
- Local network access on devices that prefer cellular data

## Related Projects

- [esp32_webserver](https://github.com/electronics101clt/esp32_webserver) - Compatible ESP32 web server

## License

MIT License - See LICENSE file for details

## Credits

Developed for controlling ESP32 devices from Android head units with CarPlay interference.

Built with:
- Kotlin
- Android SDK
- WiFi Network Request API
- WebView
- Accessibility Services

---

**Note**: This app temporarily disables Bluetooth and hotspot while running. It restores them when closed. Not recommended for devices that require constant Bluetooth or hotspot operation.
