# Changelog

All notable changes to ZScreen Dash will be documented in this file.

## [Unreleased]

### Added
- Auto-dismiss WiFi settings dialog when successfully connected to ESP32
- Connection lost alert dialog when WiFi disconnects while app is running
- Automatic app exit after user dismisses connection lost dialog
- Improved network callback handling for Android 10+ (API 29+)

### Changed
- Enhanced WiFi connection flow with automatic settings dismissal
- Updated network request flags to bring app to foreground after connection

### Fixed
- WiFi settings dialog remaining open after successful connection
- Missing user notification when WiFi connection is lost

## [1.0.0] - Initial Release

### Features
- **Automatic WiFi Connection**: Forces WiFi on and connects to ESP32 access points
- **Hotspot Override**: Kills device hotspot to free WiFi hardware
- **CarPlay Bypass**: Automatically disables Bluetooth to prevent CarPlay interference
- **State Restoration**: Restores Bluetooth and hotspot when app closes
- **WebView Interface**: Displays ESP32 web interface directly in app
- **Multi-Android Support**: Works on Android 8.0, 9.0, and 10.0+

### Android 10+ (API 29+)
- Uses WiFi Network Request API with `removeCapability(NET_CAPABILITY_INTERNET)`
- Suppresses "no internet" warnings
- Maintains stable connection to local ESP32 networks

### Android 8-9 (API 26-28)
- Uses legacy `bindProcessToNetwork()` method
- System notifications may appear but connection remains stable

### Permissions Required
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

### Optional
- Accessibility Service for devices where hotspot blocks WiFi forcing

### ESP32 Configuration
- **SSID**: ESP32-Control (configurable)
- **Password**: 12345678 (configurable)
- **IP**: 192.168.4.1
- **Protocol**: HTTP (cleartext)

### Technical Details
- **Package**: com.wifihotspot.selector
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Language**: Kotlin
- **Build System**: Gradle

### Known Issues
- Bluetooth disable may not work on Android 13+ (requires system app privileges)
- Some devices may require manual accessibility service enablement

### Compatibility
- ✅ Android 8.0 (API 26) - Minimum supported version
- ✅ Android 9.0 (API 28) - Full support, may show "no internet" notification
- ✅ Android 10.0+ (API 29+) - Full support, no warnings
- ✅ Android 12 (API 31) - Bluetooth disable works
- ⚠️ Android 13+ (API 33+) - Bluetooth disable restricted for non-system apps

### Use Cases
- Car head units with CarPlay that block WiFi
- IoT device configuration and control
- ESP32-based projects requiring persistent WiFi connection
- Local network access on devices that prefer cellular data

### Related Projects
- [ESP32 Car Dashboard](https://github.com/electronics101clt/esp32-car-dashboard) - Comprehensive car dashboard with 8 gauges
- [ZScreen ESP Client](https://github.com/electronics101clt/ZScreenESPClient) - Compatible ESP32 web server

---

## Release Notes Format

### [Version] - YYYY-MM-DD

#### Added
- New features

#### Changed
- Changes to existing functionality

#### Deprecated
- Soon-to-be removed features

#### Removed
- Removed features

#### Fixed
- Bug fixes

#### Security
- Security improvements
