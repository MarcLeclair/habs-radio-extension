# Android TV Quality Checklist

Current implementation status for Google TV / Android TV review.

## Implemented

- Declares `android.software.leanback` as required.
- Uses `LEANBACK_LAUNCHER` for the TV launch activity.
- Provides app icon metadata.
- Provides TV banner metadata on the application and activity.
- Does not require touchscreen hardware.
- Uses a D-pad focusable main screen with visible focused button state.
- Requests initial focus on the primary Play action.
- Supports media remote keys for play, pause, and stop.
- Builds as an Android App Bundle through the Android TV module's Gradle release task.

## Manual Review Before Store Submission

- Test navigation with a physical Android TV / Google TV remote.
- Verify the banner appears correctly in the TV launcher.
- Verify the app starts playback from a fresh install.
- Verify background playback and notification behavior on Android TV.
- Verify all text is readable at TV distance.
- Verify the local-network phone remote pairing flow on the same Wi-Fi.
