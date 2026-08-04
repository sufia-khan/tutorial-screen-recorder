# Countdown Leaks Into Recorded Video — Fix Plan

## Problem Statement

Before a recording starts, the app shows a 3 → 2 → 1 countdown. The recording should begin only after the countdown has completely disappeared. In practice, the recorded video briefly shows the last countdown digit ("1") at the very start.

**Root cause (confirmed in code):**

1. The countdown digits are drawn in the app's own window, and `MediaProjection` with `AUTO_MIRROR` captures the whole display including the app window.
2. When the count reaches 0, `onCountdownFinished()` (MainActivity.kt:364) immediately calls `ScreenRecorderService.startRecording()`. The service runs `handleStart()` → `RecordingManager.setupAndStart()` → `MediaRecorder.start()` synchronously on the main thread — possibly before Compose has even recomposed the screen without the countdown.
3. The `AnimatedVisibility` exit animation (`exit = scaleOut() + fadeOut()`, CountdownScreen.kt:58) keeps the "1" visibly shrinking/fading for ~200–500ms after the countdown ends, guaranteeing overlap with the recording start window.

Result: the first encoded frames of the video show the "1".

## Solution

**Remove-then-start:** make the countdown disappear instantly when it ends, wait for the screen to physically redraw one frame without it, and only then start the recording service. No arbitrary delays — the only wait is ~2 screen frames (~33ms), which is a render synchronization, not a guess.

**New flow:**
```
Tap Start Recording (existing permission flow)
  ↓
Countdown: 3 → 2 → 1 (unchanged timing, digit vanishes instantly at the end)
  ↓
Wait 2 screen frames (~33ms) — the digit-free frame is now on the display
  ↓
State → STARTING (normal home view, no timer, no stop button)
  ↓
Start recording service (unchanged service code)
  ↓
State → RECORDING (timer, stop button, toast — all unchanged)
```

## Implementation Decisions

- **Remove the exit animation:** Change `exit = scaleOut() + fadeOut()` to no exit animation in CountdownScreen.kt:58. The digit disappears the instant its second ends. The enter animation stays. This makes "countdown done" and "countdown off-screen" the same moment.

- **Two-frame wait:** Inside the countdown's `LaunchedEffect`, after the loop ends, wait for two frames with `withFrameNanos` before calling the finish callback. Compose's frame callback fires before the next frame is drawn, so two waits guarantee the digit-free frame has been drawn and presented to the display before anything else happens. `onCountdownFinished` becomes a `suspend` callback.

- **New `STARTING` state:** Add `STARTING` to `RecordingState`. During it, the app shows the normal home view — no recording timer, no stop button, so the user cannot tap Stop before the service exists. `shouldShowOverlay` (RecorderRuntime.kt:79) already falls into `else -> false` for it; the Home screen and Settings screen treat anything that is not RECORDING/PAUSED as normal. No ripple fixes needed there.

- **Handoff ordering in `onCountdownFinished` (now suspend):**
  1. Keep the existing guard: if state is no longer `COUNTDOWN`, ignore (protects the cancel race during the 2-frame wait; the screen-off receiver is still registered during those 33ms).
  2. `viewModel.onCountdownPreparing()` → state + `RecorderRuntime.state` = `STARTING`.
  3. `try { ScreenRecorderService.startRecording(this) } catch { reset to IDLE + log }` — safety net. Also fixes the pre-existing edge case where starting a foreground service from the background throws and crashes the app.
  4. `viewModel.onRecordingStarted()` → `RECORDING`.

- **Service unchanged:** `ScreenRecorderService`, `RecordingManager`, `MediaRecorder`, timer, toast, and overlay logic are untouched. The service still does its full setup before `MediaRecorder.start()`; by then the countdown-free frame has long been on the display, so the first captured frames are clean.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/screenrecorder/ui/CountdownScreen.kt` | Remove exit animation; add 2-frame wait; `onCountdownFinished` becomes suspend |
| `app/src/main/java/com/screenrecorder/MainActivity.kt` | `onCountdownFinished` suspend; new handoff order (STARTING → start service → RECORDING); `STARTING` branch in the `when(recordingState)`; try/catch safety net |
| `app/src/main/java/com/screenrecorder/model/RecordingState.kt` | Add `STARTING` |
| `app/src/main/java/com/screenrecorder/MainViewModel.kt` | Add `onCountdownPreparing()` (sets state + `RecorderRuntime.state` to `STARTING`) |
| `app/src/test/java/com/screenrecorder/MainViewModelTest.kt` | Tests for `STARTING` transitions: countdown → starting → recording; cancel from countdown → idle; service failure → idle |

## Edge Cases

- **Cancel during the 2-frame wait:** Cancel button / screen-off receiver is still active during the wait. If triggered, state → `IDLE`, the countdown composable leaves the tree and its coroutine is cancelled, so the finish callback never fires. The existing guard in `onCountdownFinished` is a second safety layer.
- **Service fails to start** (e.g., app backgrounded during countdown): caught → reset to `IDLE` → user can simply try again. No crash, no stuck state.
- **Process death during `STARTING`:** `MainViewModel.init` only restores RECORDING/PAUSED, so a fresh process starts `IDLE`. Consistent with how `COUNTDOWN` behaves today.

## Verification

1. **Unit tests:** `MainViewModelTest` covers the new `STARTING` transitions (listed above). `CountdownScreenTest` (pure delay-math) remains unchanged and passing.
2. **Manual, multiple sessions:** Record at least 5 sessions on a device. For each saved video, open it and pause at the very first frame. The video must begin with the actual screen content — never a countdown digit.
3. **Regression:** Start, pause, resume, stop, and cancel flows still behave as before; "Recording started" toast still appears; timer still starts at 00:00.
