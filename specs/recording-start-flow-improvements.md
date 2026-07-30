# Recording Start Flow Improvements

## Problem Statement

When the user taps "Start Recording", the countdown, recording start, timer, and app backgrounding all fire at once in an uncontrolled sequence. This causes three issues: (1) the countdown may appear in the recorded video, (2) the countdown timing is inconsistent (each number has a different duration), and (3) there is no confirmation that recording has actually begun. The app also automatically backgrounds itself, which is not the desired behavior for the MVP — the user should manually switch to another app.

## Solution

Simplify the recording start flow: countdown completes with precise timing → recording starts immediately → timer starts → confirmation toast appears. The app stays on screen; the user manually switches apps. No auto-background.

**New flow:**
```
Tap Start Recording (existing permission flow)
  ↓
Countdown: 3 → 2 → 1 (equal duration via target-time)
  ↓
Recording starts (Service)
  ↓
Timer starts at 00:00
  ↓
"Recording started" toast
  ↓
App stays on screen — user switches manually
```

## User Stories

1. As a user, I want the countdown numbers to each appear for exactly the same duration, so that the countdown feels consistent and professional.

2. As a user, I want recording to begin as soon as the countdown finishes, so that I don't wait longer than necessary.

3. As a user, I want to see a "Recording started" confirmation, so that I know the recording has actually begun successfully.

4. As a user, I want the app to stay on screen after recording starts, so that I can decide when to switch to another app myself.

5. As a user, I want the recorded video to never contain any part of the countdown, so that the recording starts cleanly.

6. As a user, I want the timer to start only when recording actually begins, so that the elapsed time in the recording is accurate.

7. As a developer, I want the countdown step-duration logic to be a testable pure function, so that I can verify its correctness with unit tests.

## Implementation Decisions

- **No auto-background:** Remove `moveTaskToBack(true)` from `onCountdownFinished()`. The app stays on screen. The user switches to another app manually. This is the explicit design choice for the MVP.

- **Countdown timing:** Replace the existing `delay(1000)` × 3 loop with a target-time approach. Record `System.nanoTime()` at the start of the countdown. Each tick evaluates `startTime + stepIndex × stepDurationNs` and delays until that absolute time. This self-corrects scheduler jitter so every number displays for the same duration.

- **Confirmation toast:** The Compose content in `MainActivity` observes `recordingState` via `collectAsStateWithLifecycle()`. A `LaunchedEffect` keyed on `recordingState` shows a `Toast` ("Recording started") when the state transitions from `COUNTDOWN` to `RECORDING`.

- **Timer remains unchanged:** The timer already starts in `ScreenRecorderService.handleStart()` after `recordingManager.setupAndStart()` returns. No change needed — it is already properly sequenced after recording begins.

- **Service launch unchanged:** `onCountdownFinished()` still calls `ScreenRecorderService.startRecording(this)` and `viewModel.onRecordingStarted()` in sequence. Only `moveTaskToBack(true)` is removed.

## Testing Decisions

- **What makes a good test:** Test external behavior — given a desired countdown duration and step index, the function returns the correct delay in milliseconds. Pure function, no mocks, no Android dependencies.

- **Single test seam:** Extract the countdown step-duration calculation into a pure function (e.g. `fun nextCountdownDelayMs(totalDurationMs: Long, stepIndex: Int, elapsedMs: Long): Long`). Test that:
  - Each step returns the expected delay for nominal timing
  - Steps self-correct when previous delay ran over (returns a shorter delay)
  - Steps never return a negative delay

- **Prior art:** `TapEventDedupStoreTest.kt` and `PermissionManagerTest.kt` — pure Kotlin unit tests with JUnit 4, no instrumentation, no mocking framework.

- **Not tested:** The Compose countdown composable itself (no prior art for Compose UI tests). The toast on state change (trivial, depends on Android Context).

## Out of Scope

- Auto-background after recording start (intentionally removed).
- Detecting when the user has manually switched to another app.
- Any changes to the screen recording service, MediaRecorder, VirtualDisplay, overlay, or touch indicator logic.
- Compose UI tests for the countdown composable.
- Changes to the timer display in HomeScreen (HomeScreen is not visible during recording).

## Further Notes

- Decisions were made one at a time during a grilling session (session date: 2026-07-30).
- The app stays on screen after recording starts, matching the xrecorder pattern for the MVP.
- No GitHub issue tracker is configured; this spec is stored locally.
