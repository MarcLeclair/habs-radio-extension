# Puck Radio Remote

Android phone remote for controlling the Puck Radio Sync TV app on the same local network. It uses the TV app's local HTTP control API for playback, delay, volume, and station changes.

## Build

```bash
./gradlew :apps:android-remote:assembleDebug
```

Release builds require these environment variables:

- `HRS_UPLOAD_STORE_FILE`
- `HRS_UPLOAD_STORE_PASSWORD`
- `HRS_UPLOAD_KEY_ALIAS`
- `HRS_UPLOAD_KEY_PASSWORD`

## Usage

1. Start Puck Radio Sync TV on the Android TV device.
2. Connect the phone to the same Wi-Fi network.
3. Open Puck Radio Remote and connect to the TV.
4. Use playback, delay, and volume controls to match the radio call to the video stream.

The TV app owns the API contract. See [Android TV API docs](../android-tv/README.md#local-control-api).

## Sideload Install

Download the latest phone remote APK from the [latest GitHub release](https://github.com/MarcLeclair/habs-radio-extension/releases/latest).

### Browser or file manager

1. Download the phone remote APK on the Android phone.
2. Open the APK from the browser downloads list or a file manager.
3. If Android blocks the install, allow installs from that app when prompted.
4. Return to the APK and install it.

### ADB

Enable developer options and USB debugging on the phone. Android's official developer-options documentation is here:
[Configure on-device developer options](https://developer.android.com/studio/debug/dev-options).

Then run:

```bash
adb install -r puck-radio-remote.apk
```
