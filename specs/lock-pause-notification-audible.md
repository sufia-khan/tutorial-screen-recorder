# Lock-Pause Notification Should Be Audible — Fix Plan

## Problem Statement

The app shows two types of notifications:

1. **Active recording notification** — the foreground-service notification shown while recording (timer text, Pause/Stop actions). It is silent by design and must stay silent.
2. **"Recording Paused" notification** — shown when the device is locked during a recording ("Recording was paused because your device was locked." with Resume/Stop actions). It should make a sound, like a normal notification, but it arrives silently.

**Root cause (verified on device):** The "Recording Paused" notification posts to the `lock_event_channel` ("Lock Events") channel. On the user's device this channel's importance is stuck at **NONE (0) = silent**, even though the app requested **HIGH (4)** (`mOriginalImp=4`). Android does not allow an app to raise an existing channel's importance back up after it has been muted, so no amount of normal updating fixes it. The device is not in Do Not Disturb; the channel itself is muted.

## Solution

Do **not** attempt to repair the stuck old channel (Android forbids changing a channel's importance after creation). Instead:

- Create a **brand-new notification channel** with **IMPORTANCE_DEFAULT** (plays the user's normal notification sound).
- Point **only** the "Recording Paused" notification at the new channel.
- Leave the active recording notification, its channel, and all other logic untouched.

## Implementation Decisions

- **New channel:** Replace the old lock-channel constant (`lock_event_channel`) with a new id (e.g. `lock_alert_channel`), display name stays "Lock Events". Because this id has never been created, its importance is fully settable on first creation.
- **Importance:** `IMPORTANCE_DEFAULT` when notifications are enabled; `IMPORTANCE_NONE` when disabled — same mute pattern as the existing channels (the mute → unmute cycle back to the original importance is the one change Android permits, and will be verified on device).
- **Recording channel untouched:** `screen_recorder_channel` stays IMPORTANCE_LOW (silent), no code changes for the active recording notification.
- **No channel deletion anywhere:** The old orphaned `lock_event_channel` stays on devices (invisible to the user); nothing references it after this change.
- **Trigger and content unchanged:** The "Recording Paused" notification still appears exactly when it does today (device locked during recording), with the same text and the same Resume/Stop actions. Only its channel changes.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/screenrecorder/manager/NotificationChannels.kt` | New lock channel id + `IMPORTANCE_DEFAULT` in channel creation |
| `app/src/main/java/com/screenrecorder/service/ScreenRecorderService.kt` | "Recording Paused" notification builder uses the new channel id |

## Edge Cases

- **User toggles notifications off/on:** New channel mutes (NONE) and unmutes (back to DEFAULT) — the permitted restore pattern; verified on device.
- **User mutes the new channel manually in system settings:** their choice wins; the app makes no further change (same as today).
- **Old stuck channel:** left in the system untouched; no user-visible impact.

## Verification

1. Start a recording, lock the device → "Recording Paused" notification appears **with the normal notification sound**.
2. The active recording notification remains silent.
3. Toggle notifications off then on in the app → lock the device again → sound still plays (mute/unmute cycle works).
4. Resume/Stop actions on the "Recording Paused" notification still work.
