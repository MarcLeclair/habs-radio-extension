# Puck Radio Sync TV

Android TV playback host for the radio stream. The app starts a foreground playback service, plays 98,5 FM, and exposes a small HTTP API on the local network so the phone remote can control playback, delay, volume, and station selection.

## Build

```bash
./gradlew :apps:android-tv:assembleDebug
```

## Local Control API

The TV app listens on port `8787` while its playback service is running.

```bash
curl "http://TV_IP:8787/state"
curl "http://TV_IP:8787/play"
```

All successful endpoints return the current playback state as JSON. Unknown paths return `404` with `{"error":"not found"}`.

### Endpoints

| Endpoint | Description |
| --- | --- |
| `GET /state` | Return current playback state. |
| `GET /play` | Start or resume playback. |
| `GET /pause` | Pause playback. |
| `GET /stop` | Stop playback and clear delay buffering. |
| `GET /reload` | Reload the current stream. |
| `GET /delay?seconds=20` | Set sync delay in seconds. Values are clamped to `0..60`. |
| `GET /nudge?seconds=1` | Add or subtract sync delay seconds. Example: `/nudge?seconds=-1`. |
| `GET /volume?percent=100` | Set volume percent. Values are clamped to `0..300`. |
| `GET /volumeNudge?percent=10` | Add or subtract volume percent. Example: `/volumeNudge?percent=-10`. |
| `GET /station?id=chmp` | Select a station by id. Currently `chmp` is the only station. |

### State Response

```json
{
  "stationId": "chmp",
  "playing": true,
  "delaySeconds": 20.0,
  "delayBufferedSeconds": 18.4,
  "delayAvailableSeconds": 18.4,
  "delayBuffering": true,
  "audioBytesWritten": 123456,
  "audioBytesPerSecond": 16000,
  "volumePercent": 100,
  "status": "Playing",
  "error": null
}
```

`status` is one of `Idle`, `Buffering`, `Playing`, `Paused`, or `Error`.

## Notes

- The phone remote uses the same API through the shared `RemoteProtocol` code.
- The HTTP server sends `Access-Control-Allow-Origin: *` for local development and browser-based controls.
- The radio feed is usually ahead of video streams, so practical delay values are often around `15..25` seconds.
