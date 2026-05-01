# Habs Radio Sync Browser Extension

Chrome/Firefox extension that plays the 98,5 FM stream alongside supported Habs game pages. It adds a floating sync panel and can mute the page video while delayed radio audio plays through the extension.

## Supported Sites

- `sportsnet.ca`
- `rds.ca`
- `tvasports.ca`

## Build

Prepare the browser-specific manifest:

```bash
apps/browser-extension/build.sh chrome
apps/browser-extension/build.sh firefox
```

## Chrome

1. Open `chrome://extensions`.
2. Toggle **Developer mode** on.
3. Run:

   ```bash
   apps/browser-extension/build.sh chrome
   ```

4. Click **Load unpacked** and pick `apps/browser-extension`.

## Firefox

1. Open `about:debugging#/runtime/this-firefox`.
2. Run:

   ```bash
   apps/browser-extension/build.sh firefox
   ```

3. Click **Load Temporary Add-on**.
4. Pick `apps/browser-extension/manifest.json`.

## Usage

1. Open a Habs game on one of the supported sites.
2. Press **Play**. The page video is muted automatically.
3. Drag the sync delay slider until the radio call matches the video.
4. Use the nudge buttons for smaller live adjustments.

The delay range is `0..60` seconds. The radio feed is usually ahead of video streams, so practical values are often around `15..25` seconds.

## Packaging

Create browser-specific zips:

```bash
apps/browser-extension/build.sh chrome zip
apps/browser-extension/build.sh firefox zip
```
