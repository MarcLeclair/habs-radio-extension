# Habs Radio Sync

Browser extension that overlays a (for right now) French radio commentary stream from 98.5 FM on top of your Sportsnet / RDS / TVA Sports game tab (by default, can add more website if need be), with an adjustable delay slider so the play-by-play matches the action on screen.

Works in Chrome and Firefox.

## Stations
- **98,5 FM** — Cogeco, Canadiens en français

## Install (locally)

### Chrome

1. Open `chrome://extensions`
2. Toggle **Developer mode** on
3. Click **Load unpacked** and pick this folder

### Firefox

1. Open `about:debugging#/runtime/this-firefox`
2. Click **Load Temporary Add-on**
3. Pick `manifest.json` in this folder

The extension activates on `sportsnet.ca`, `rds.ca`, and `tvasports.ca`. A floating panel appears in the bottom-right of the page.

## Usage

1. Open a Habs game on one of the supported sites.
2. Press **Play** — the page video auto-mutes.
3. Drag the **Sync delay** slider until the radio call matches what you see (typically 15–25s).
4. Use the ±1s,5s,10s buttons for live nudging.

## Notes on latency

- Slider range: 0–60s
- The radio should (mostly) be ahead of any stream from sportsnet / other stations. Otherwise, you can try to pause your video feed.

## Files

- `manifest.json` — MV3 manifest, cross-browser
- `src/stations.js` — preset stream URLs and per-host default delays
- `src/sync.js` — Web Audio graph: `<audio>` → `MediaElementSource` → `DelayNode` → output
- `src/content.js` — injects and wires up the floating panel
- `src/panel.css` — panel styling
