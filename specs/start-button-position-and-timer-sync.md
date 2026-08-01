# Start Button Position & Timer Sync

## Problem Statement

Two user-facing annoyances:

1. **Start button position**: The "Start Recording" button (and the pause/stop recording controls) sits exactly at the vertical center of the screen. Visually, the content feels too high for comfortable thumb reach.

2. **Timer desync**: Three places display the recording duration — the Home screen timer, the floating overlay pill, and the notification. They all poll a shared counter value, but at different rates (200ms, 500ms, 1000ms). When the counter ticks from 7 to 8, each display picks up the change at a different moment, so the Home screen can show `00:08` while the overlay still shows `00:07` for up to half a second. To the user it looks like two clocks disagreeing.

## Solution

1. Move the start button and recording controls ~40dp below the vertical center by inserting a fixed spacer between the top elastic spacer and the content block.

2. Replace the shared counter with a **wall-clock anchor**: record when recording actually started, and how much time was spent paused. All three displays compute elapsed seconds from the same formula `(now - startedAt - pausedMs) / 1000` using a monotonic clock, so at any moment they all produce the same number. The counter's polling-rate skew disappears entirely — the displays can never disagree, because they no longer wait for someone to update a shared value; they each compute it from the same immutable anchors.

## User Stories

1. As a user, I want the Start Recording button to sit slightly below the middle of the screen, so that it feels more natural under my thumb.

2. As a user during recording, I want the Pause/Stop controls and timer to also sit slightly below center, so that the layout is consistent whether recording or idle.

3. As a user watching the timer on the Home screen, I want it to show the same seconds as the floating overlay at every moment, so that I never see two disagreeing clocks.

4. As a user watching the floating overlay pill, I want it to show the same seconds as the Home screen timer, so that I can trust either one.

5. As a user reading the notification, I want its time display to match the Home screen and overlay, so that all three places agree.

6. As a user who pauses the recording for a while, I want the timer to freeze while paused and continue from where it left off on resume, so that the total only counts actual recording time.

7. As a user who pauses, resumes, pauses, and resumes multiple times, I want the accumulated paused time to add up correctly, so that the final total is accurate.

8. As a user who pauses by locking the device, I want the paused time excluded exactly like a manual pause, so that lock-pauses do not inflate the recording duration.

9. As a user, I want the timer to show 00:00 at the moment recording starts (after the countdown), so that the countdown is not counted as recording time.

10. As a user, I want the timer to never show a negative value or go backwards, so that the UI always looks sane.

11. As a user, I want the timer to stay accurate even if the phone's clock is changed mid-recording, so that the elapsed time reflects actual recording, not wall-clock jumps.

12. As a developer, I want the elapsed-time computation to be a pure function taking raw values, so that I can unit test the pause math without Android.

## Implementation Decisions

### Button position

- The Home screen layout uses two equal elastic spacers (`weight(1f)`) above and below the content block, which vertically centers the content.
- Insert a fixed 40dp spacer between the top elastic spacer and the content block. This shifts the content ~40dp below center.
- The change applies to the whole content block — the "Start Recording" button when idle, and the timer + Pause/Stop controls when recording — so both states move identically.

### Timer — wall-clock anchor (model)

- The recording session model gains three fields:
  - `recordingStartedAtMs` — set when recording actually begins (after the countdown finishes), from `SystemClock.elapsedRealtime()` (monotonic — immune to user clock changes).
  - `pausedAccumulatedMs` — total time spent paused across all pause/resume cycles.
  - `pauseStartedAtMs` — when the current pause began; `0` when not paused.
- The mutable `elapsedSeconds` counter is replaced with a computed `elapsedSeconds()` that derives from the anchors.
- The session reset clears all anchor fields.

### Timer — elapsed computation

Pure function taking raw values (prototype from the grilling session):

```
computeElapsedSeconds(nowMs, startedAtMs, pausedAccumulatedMs, pauseStartedAtMs) =
    max(0, (nowMs - startedAtMs - pausedAccumulatedMs - currentPauseMs) / 1000)
    where currentPauseMs = if (pauseStartedAtMs > 0) nowMs - pauseStartedAtMs else 0
```

- Division is integer division (floor), so sub-second time rounds down.
- The result is clamped at 0 so it can never be negative.

### Timer — service lifecycle wiring

- On recording start (after countdown): set `recordingStartedAtMs`, reset both pause fields.
- On manual pause and lock-pause: record `pauseStartedAtMs = now`.
- On resume: add `now - pauseStartedAtMs` to `pausedAccumulatedMs`, then clear `pauseStartedAtMs`.
- On stop and cleanup: reset all anchor fields.
- The pause-by-lock path reuses the exact same pause/resume hooks as the manual path — no special handling.

### Timer — heartbeat and displays

- The periodic timer becomes a heartbeat only: it keeps firing every ~1 second so the notification can re-read the derived time, but it no longer owns the count. Its internal counter and pause flags become redundant and are removed; pause/resume no longer need to gate the heartbeat (the derived value already excludes paused time).
- Home screen, overlay, and notification all call the derived `elapsedSeconds()`:
  - Home screen keeps its 200ms polling cadence.
  - Overlay keeps its 500ms polling cadence.
  - Notification re-reads on each heartbeat tick.
- Because all three derive from the same immutable anchors, polling cadence differences no longer produce visible disagreement.

## Testing Decisions

- **What makes a good test**: Test the pure elapsed computation with raw inputs — external behavior (the seconds number), not how it reads the clock. No mocks, no instrumentation.
- **Single test seam**: The pure `computeElapsedSeconds(nowMs, startedAtMs, pausedAccumulatedMs, pauseStartedAtMs)` function. One test file, ~6 tests.
- **Prior art**: `PermissionManagerTest.kt` and `TapEventDedupStoreTest.kt` — pure JUnit 4, raw input values, no Android dependencies.
- **Tests to write**:
  1. No pause: `now - started = 5000ms` → `5`
  2. Accumulated pause: 5000ms recorded, 2000ms accumulated pause → `3`
  3. Currently paused: pause started 1000ms ago → that second excluded
  4. Boundary: exactly 1000ms → `1`
  5. Sub-second: 999ms → `0`
  6. Clock guard: `now < startedAt` → `0` (never negative)
- **Not testing**: Compose layout/positioning of the button (no Compose UI test infrastructure in the codebase — manual QA), service lifecycle wiring (integration), notification text rendering.

## Out of Scope

- Changing the polling cadence of any display (unnecessary once anchored).
- Any change to the countdown behavior or duration.
- Changing the position of any other UI element (settings gear, version text, mode hint).
- Introducing Compose UI testing infrastructure.
- Persisting the anchor across process death (the session model is in-memory; process death ends the session as today).

## Further Notes

- The grilling session that produced this spec is documented in the conversation history (session date: 2026-07-31).
- Decisions were made one at a time during the grilling, with the user picking from recommended options.
- `SystemClock.elapsedRealtime()` is used for the anchor so user clock changes mid-recording cannot skew the timer.
- No GitHub issue tracker is configured for this repo; this spec is stored locally at `specs/start-button-position-and-timer-sync.md`.
- "ready-for-agent" triage label is not applied (no issue tracker available).
