# Play Data Safety Draft

This draft matches the current Android TV and Android phone remote behavior.

## Data Collection

Answer: The app does not collect user data.

Rationale:

- No accounts.
- No analytics SDK.
- No ads SDK.
- No telemetry endpoint.
- No personal data is sent to the developer.
- The phone remote stores the selected TV IP address and port locally on the device.

## Data Sharing

Answer: The app does not share user data.

Rationale:

- No user data is transmitted to the developer.
- No user data is sold or shared with third parties.
- The TV app connects to radio stream providers to load audio streams; this is core app functionality and not developer-side user data collection.

## Security Practices

Suggested answers:

- Data encrypted in transit: No, because the remote control protocol currently uses cleartext HTTP on the user's local network.
- Users can request data deletion: Not applicable if declaring no data collection. Local app data can be cleared by uninstalling or clearing app storage.

## Permissions / Access Explanation

Android phone remote:

- `INTERNET`: Sends local control requests to the TV app.
- `CHANGE_WIFI_MULTICAST_STATE`: Supports local network discovery.
- `NEARBY_WIFI_DEVICES`: Finds the TV app on the local Wi-Fi network.
- `POST_NOTIFICATIONS`: Shows quick remote controls in the notification shade.

Android TV app:

- `INTERNET`: Loads radio streams and serves local control responses.
- `FOREGROUND_SERVICE`: Keeps playback running.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: Declares media playback foreground service use.
- `POST_NOTIFICATIONS`: Shows playback status notification.
- `CHANGE_WIFI_MULTICAST_STATE`: Supports local network service advertisement/discovery.

## App Privacy Summary

Suggested Play listing text:

Puck Radio Sync does not use accounts, analytics, ads, or tracking. The apps use your local network to find and control your TV, use notifications for playback controls, and connect to radio stream URLs to play audio.
