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
