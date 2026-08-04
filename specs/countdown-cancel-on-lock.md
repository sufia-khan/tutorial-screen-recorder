# Countdown Cancel on Lock

## Problem Statement

When the user taps "Start Recording" and the 3-2-1 countdown is running, locking the device does nothing to stop the countdown. The countdown keeps running in the background, recording starts anyway while the screen is locked, and the resulting video begins with the lock screen. A recording of a locked screen is useless.

Additionally, the lock-detection receiver (`ScreenLockReceiver`) is registered only inside `ScreenRecorderService`, which does not exist yet during the countdown — so the app has no way to react to the lock during this phase.

## Solution

Cancel the countdown the moment the device locks. No recording starts. When the user unlocks and returns to the app, the app is in the normal idle state with the Start button ready.

**New flow:**
```
Tap Start Recording (existing permission flow)
  ↓
Countdown: 3 → 2 → 1
  ↓
[if device locks at any point during countdown]
  ↓
Countdown cancels immediately → state returns to IDLE
  ↓
No recording starts. User unlocks, returns to app → idle screen, Start button ready
```

## User Stories

1. As a user, I want the countdown to cancel if I lock the device, so that no recording ever starts on a locked screen.

2. As a user, I want the cancellation to happen immediately at the moment the screen goes off, so that there is no window in which recording can sneak in.

3. As a user, I want the app to be in the normal idle state when I return after unlocking, so that I can just tap Start again.

4. As a user, I do not want a toast or message about the cancellation, so that the app stays quiet about it.

5. As a user who presses Home or switches to another app during the countdown, I want the countdown to keep running and recording to start, so that I can use the countdown to position myself in another app (e.g. a game) before recording begins.

6. As a user who locks the device at the same instant the countdown finishes, I want the existing lock-pause behavior to take over, so that the recording pauses and does not keep capturing a locked screen.

## Implementation Decisions

### Detecting the lock during countdown

- Register a runtime `BroadcastReceiver` for `Intent.ACTION_SCREEN_OFF` in `MainActivity`, active **only** while `recordingState == COUNTDOWN`.
- On receive: cancel the countdown immediately.
- Unregister the receiver when the countdown ends (state leaves `COUNTDOWN`) and as a safety net in the activity's `onStop`/`onDestroy`.

### Cancelling the countdown

- The cancel is simply a state change: `viewModel.onCountdownCancelled()` sets both the ViewModel state and `RecordingSession.state` to `RecordingState.IDLE`.
- Because `MainActivity` renders `CountdownScreen` only while the state is `COUNTDOWN` (MainActivity.kt:152-157), switching to `IDLE` disposes the countdown composable. Its `LaunchedEffect` coroutine is cancelled automatically, `onCountdownFinished()` is never called, and the recording service is never started.
- No changes to `CountdownScreen.kt` itself are needed.

### Grant data cleanup

- The pending MediaProjection grant (`pendingResultCode` / `pendingData` in the `ScreenRecorderService` companion) is cleared when the countdown is cancelled, so no stale grant can ever be consumed by a later `ACTION_START`.
- On the next Start tap, a fresh permission dialog overwrites the grant anyway; clearing is defensive cleanup.

### `deviceLocked` flag

- The cancelled-countdown path must **not** set `RecordingSession.deviceLocked = true`. `handleStart()` in `ScreenRecorderService` only shows the floating overlay when `!RecordingSession.deviceLocked`; a stale `true` would hide the overlay for the next recording.
- As a safety net, reset `deviceLocked = false` in `onMediaProjectionGranted()` so a new countdown always starts from a clean slate.

### Race: lock at the same instant the countdown ends

- If the screen-off event lands in the same moment `onCountdownFinished()` fires, recording may start; the existing `handlePauseByLock()` path (ScreenRecorderService.kt:234) pauses it. No special handling is added for this millisecond window.

## Testing Decisions

- **What makes a good test:** This change is Android lifecycle wiring (receiver registration, state transition) — there is no pure function to unit test. Per repo prior art (pure JUnit 4 tests only), no new unit tests are written.
- **Manual QA checklist:**
  1. Start countdown, press the lock button mid-countdown → countdown cancels, no recording, app returns to idle on unlock.
  2. Start countdown, let the screen auto-off (timeout) mid-countdown → same as above.
  3. Start countdown, press Home during countdown → countdown finishes, recording starts.
  4. Start countdown, switch to another app during countdown → countdown finishes, recording starts, captures the other app.
  5. After a lock-cancel, start a new recording → floating overlay appears normally (no stale `deviceLocked` bug).
  6. Lock at the very last moment of the countdown → recording starts, then pauses by lock, unlock notification appears (existing behavior).

## Out of Scope

- Any change to the recording service, MediaRecorder, VirtualDisplay, overlay, or touch indicator logic.
- Auto-backgrounding after recording starts (removed in `recording-start-flow-improvements.md`).
- Cancelling on Home button or app switch (intentionally NOT cancelled).
- A toast or notification about the cancellation.
- Any change to the countdown duration or visuals.
- Compose UI tests (no infrastructure in the codebase).

## Further Notes

- Decisions were made one at a time during a grilling session (session date: 2026-08-01).
- The user's chosen behavior: cancel only on a real device lock (screen off), never on app backgrounding, with no message on return.
- The screen-off receiver approach (Activity-runtime receiver active only during countdown) was chosen over a lifecycle-visibility check for precision.
- No GitHub issue tracker is configured; this spec is stored locally at `specs/countdown-cancel-on-lock.md`.
