# Habs Radio Sync

A small project I create to help line up the French radio commentary (98.5) with Habs game streams. Currently, the project supports google tv (with android remote) and web extension.

- `apps/browser-extension` - Chrome/Firefox extension to play on certain sites (sportsnet, rds, etc...)
- `apps/android-tv` - Android TV playback that runs the radio stream and exposes local-network controls.
- `apps/android-remote` - Android phone remote for controlling the Android TV app.
- `shared` - Kotlin Multiplatform module for stations, playback state, and the remote protocol.

## Supported Station

- 98,5 FM - Cogeco, Canadiens en francais

## App Docs

- [Browser extension](apps/browser-extension/README.md)
- [Android TV app](apps/android-tv/README.md)
- [Android phone remote](apps/android-remote/README.md)

## Maintainer Docs

- [Play Console drafts](docs/play-console/README.md)

## Build

See the app READMEs for build and packaging instructions:

- [Browser extension](apps/browser-extension/README.md#build)
- [Android TV app](apps/android-tv/README.md#build)
- [Android phone remote](apps/android-remote/README.md#build)

## Privacy

Habs Radio Sync does not use accounts, analytics, ads, or tracking. Privacy policies live with each app:

- [Browser extension](apps/browser-extension/PRIVACY.md)
- [Android TV app](apps/android-tv/PRIVACY.md)
- [Android phone remote](apps/android-remote/PRIVACY.md)
