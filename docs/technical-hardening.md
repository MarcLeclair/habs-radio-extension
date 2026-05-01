# Technical Hardening Notes

## Implemented

- Phone remote keeps a saved/manual TV address and no longer overwrites it automatically when discovery finds another address.
- Phone remote discovery retries after failure and keeps the saved/manual address usable as fallback.
- Phone remote stops NSD discovery when backgrounded and restarts it on resume.
- Notification actions use `BroadcastReceiver.goAsync()` before doing network work.
- Notification actions now send the command, request `/state`, and refresh notification state afterward.
- Notification actions include Play, Pause, Stop, Reload, -5s, and +5s.
- Android 13+ notification permission is checked before posting remote notifications.
- Release build config and R8 minify are configured for both Android modules.

## Manual Device Matrix

Before store submission, test:

- Android phone, normal portrait screen.
- Pixel Fold or narrow cover display.
- Android tablet or unfolded foldable layout.
- Android TV hardware with D-pad remote.
- Google TV hardware with D-pad remote.

## Manual Scenarios

- Fresh install TV app first, then phone app discovery.
- Fresh install phone app first, then open TV app.
- Discovery failure, then manual IP entry.
- Saved TV address after app restart.
- TV unreachable while using notification actions.
- Notification Play, Pause, Stop, Reload, -5s, and +5s.
- TV background playback while another TV app is foreground.
- Debug diagnostics hidden by default.
- Debug diagnostics enabled by five title taps on phone.
- Debug diagnostics enabled on TV with title taps or D-pad sequence: Up, Up, Down, Down, Left, Right, Left, Right.
