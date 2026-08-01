# Overlay Visibility Rules — Final Plan

## The Contract (what the overlay must do)

The floating overlay pill is visible **only when it is useful and safe to show**:

| # | Situation | Overlay |
|---|-----------|---------|
| 1 | Device is locked (screen off) | **Never visible** — even if the user manually paused, even if a manual pause was done from the lock-screen notification |
| 2 | Manual pause, device unlocked | **Visible** — the user needs it to tap Resume/Stop |
| 3 | Lock-pause, device unlocked | **Hidden** — until the user resumes (via notification or app) |
| 4 | Recording active (unlocked) | **Visible** — including the rare case where the screen went off during the countdown and recording started while locked |

Order of importance: **locked wins over everything**. Then: recording → show; manual pause → show; lock-pause → hide.

## Implementation Steps

### Step 1 — Track "device locked" as a state
- Add `@Volatile var deviceLocked: Boolean = false` to `RecordingSession` (next to the existing `pausedByLock` flag).
- `ScreenRecorderService` sets it: `true` when the `ACTION_PAUSE_BY_LOCK` broadcast arrives, `false` when `ACTION_UNLOCKED` arrives.
- Reset to `false` in `RecordingSession.reset()`.

### Step 2 — One pure decision function (testable)
In `RecordingSession.kt` (model package), add:

```kotlin
internal fun shouldShowOverlay(
    state: RecordingState,
    pausedByLock: Boolean,
    deviceLocked: Boolean
): Boolean {
    if (deviceLocked) return false
    return when (state) {
        RecordingState.RECORDING -> true
        RecordingState.PAUSED -> !pausedByLock
        else -> false
    }
}
```

This is the single source of truth for the contract above. Callers that care about Clean Mode still check `getRecordingMode() == OVERLAY` first — that check stays at the call site, not in this function.

### Step 3 — Rework the service handlers (ScreenRecorderService.kt)
- **`handlePauseByLock()`** (screen off): remove the early-return that skips manual-pause sessions.
  - Always: `deviceLocked = true`.
  - If `recordingStarted && state == RECORDING` → do the existing pause-by-lock (anchors, `pausedByLock = true`, "Paused - device locked" notification).
  - Always: `FloatingOverlayService.hide(this)` (Rule 1 — a manually paused session also hides its overlay).
- **`handleUnlocked()`** (screen unlocked): first `deviceLocked = false`, then:
  - `RECORDING` → show overlay (if overlay mode) — Rule 4 (countdown edge).
  - `PAUSED && !pausedByLock` → show overlay (if overlay mode) — Rule 3 (manual pause).
  - `PAUSED && pausedByLock` → keep hidden, show the "Lock Events" notification (existing behavior).
- **`handleResume()`**: gate the existing `FloatingOverlayService.show(this)` with `!deviceLocked` (Rule 2 — resume tapped from the lock screen must not paint an overlay on the keyguard).
- **`handleStart()`**: same gate on its `show()` call (Rule 2 — countdown edge).

### Step 4 — Safety net in the overlay itself (FloatingOverlayService.kt)
- In `updateUI()` (runs every 500ms), read `snapshot()`, plus `pausedByLock`/`deviceLocked` from `RecordingSession`, and evaluate `shouldShowOverlay(...)`.
- If it says *hide* but the overlay window exists → `removeOverlay()` (self-healing: even if a race shows it, it disappears within half a second).
- If it says *show* but no window → nothing (showing stays event-driven from the service).

### Step 5 — Unit tests (new file: `ShouldShowOverlayTest.kt`, model package)
One test per contract line, following the repo's plain-JUnit style:

1. locked + RECORDING → false (Rule 1/2)
2. locked + manual PAUSED → false (Rule 1/2)
3. locked + lock PAUSED → false (Rule 1/2)
4. unlocked + RECORDING → true (Rule 4)
5. unlocked + manual PAUSED → true (Rule 3)
6. unlocked + lock PAUSED → false (Rule 3)
7. unlocked + IDLE → false
8. unlocked + COUNTDOWN → false

### Step 6 — Keep DEBUG-lock logs for now
The `[DEBUG-lock]` instrumentation stays in until the device test passes, then gets removed (single grep + delete).

## Device Test Checklist (after implementation)
Run all of these on the Oppo, checking the overlay, the home screen button, and the notification all agree:

1. Record with app open → power off → unlock → overlay hidden, notification "Paused - device locked"; tap Resume → overlay returns.
2. Record → tap Pause on the overlay (manual pause, overlay visible) → power off → unlock → overlay visible again (manual pause).
3. Record → power off → on the lock screen tap "Pause" in the notification → unlock → overlay visible (manual pause done while locked).
4. Record → power off → tap "Resume Recording" from the lock notification → unlock → recording active, overlay visible.
5. Tap Start → screen off during the 3s countdown → unlock → recording active, overlay visible.
6. Clean Mode → no overlay in any of the above.

## Out of Scope
- Live toggling of the "Hide from Recording" setting mid-recording.
- Any change to the notification text flow beyond what is listed.
- No GitHub commit — this stays a local plan (per standing instruction).

## File Summary
| File | Change |
|------|--------|
| `model/RecordingSession.kt` | `deviceLocked` flag + `shouldShowOverlay()` pure function + reset() update |
| `service/ScreenRecorderService.kt` | Rework `handlePauseByLock` / `handleUnlocked`; gate `show()` in `handleResume`/`handleStart` |
| `service/FloatingOverlayService.kt` | Self-healing hide in `updateUI()` |
| `test/.../ShouldShowOverlayTest.kt` | 8 unit tests |
