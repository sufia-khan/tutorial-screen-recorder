# Settings Lock & Overlay Collapse — Final Plan

## The two bugs

1. **Settings unlock during pause:** The Floating Controls section in Settings is locked only while a recording is *actively running*. When a recording is *paused*, the section wrongly unlocks and the user can change overlay settings mid-session. The locked notice is also too light to notice.
2. **Overlay pill starts expanded and stays open:** When a recording starts, the pill appears with its controls already open and stays that way until manually closed. It should start closed, and any in-app screen change should close the open controls.

## Decisions (agreed)

| # | Decision | Choice |
|---|----------|--------|
| 1 | Lock the section while paused too | **Yes** — locked while a recording is in progress (running OR paused); unlocks only when recording fully stops |
| 2 | Locked message text | **"Overlay controls are disabled while a recording is in progress"** |
| 3 | Locked message style | **Bold + warning color** (same size) |
| 4 | Pill start state when recording begins | **Collapsed** (small bubble, tap to open controls) |
| 5 | When open controls close by themselves | **On any in-app screen change** (Settings ↔ Home, either direction) |
| 6 | Expanding while on the Settings screen | **Allowed** — it just closes when you leave |
| 7 | Phone Home button / app background | **Does nothing** — only in-app screen changes close the controls |

## Implementation Steps

### Step 1 — Lock the section for running AND paused (SettingsScreen.kt)
- Line 74: change `isRecording = recordingState == RecordingState.RECORDING` to also match `PAUSED`:
  ```kotlin
  val isRecording = recordingState == RecordingState.RECORDING ||
      recordingState == RecordingState.PAUSED
  ```
- Line 388: change the notice text to **"Overlay controls are disabled while a recording is in progress"**.
- Style the notice: bold + `MaterialTheme.colorScheme.error` (warning color), keep the current font size (remove the light `alpha(0.6f)` gray look).

### Step 2 — Start the pill collapsed (FloatingOverlayService.kt)
- In `showOverlay()`, remove the `expand(animate = false)` call at line 259. The pill now appears as the small collapsed bubble. Tapping it expands (existing logic in `OverlayTouchListener` already toggles).

### Step 3 — New "collapse" command on the overlay service (FloatingOverlayService.kt)
- Add `ACTION_COLLAPSE = "com.screenrecorder.overlay.COLLAPSE"` to the companion object.
- Add a static helper `collapse(context)` that starts the service with `ACTION_COLLAPSE` (same pattern as `show()`/`hide()`).
- In `onStartCommand`, handle `ACTION_COLLAPSE` → call the existing `collapse()` (it already no-ops when not expanded or when the overlay isn't shown).

### Step 4 — Collapse on any screen change (MainActivity.kt)
- Observe `viewModel.screen` with `LaunchedEffect(currentScreen)`.
- On every screen change (including the first composition — safe no-op), call `FloatingOverlayService.collapse(this)`.
- This covers Settings → Home and Home → Settings in both directions. The pill's own Settings button already collapses before opening Settings (existing behavior), so no double work.

### Step 5 — Keep existing auto-collapse setting as-is
- The "Auto Collapse" setting in Settings continues to work independently (closes controls after inactivity while expanded). No changes to it.

## Device Test Checklist (after implementation)
1. Start recording → pill appears **collapsed** (small bubble, no buttons).
2. Tap pill → controls open. Tap pill again → close.
3. Start recording → expand controls → open Settings → go back Home → controls are **closed**.
4. Open Settings from the pill's gear button → controls already close before Settings opens → back Home → still closed.
5. Start recording → **pause** from the notification → open Settings → section is **locked** with the highlighted message.
6. Pause → stop recording → open Settings → section is **unlocked** again.
7. Press the phone Home button while expanded → controls **stay open** (no collapse).
8. Clean Mode → no pill at all, section still locked only while a recording is in progress.

## Out of Scope
- Any change to the overlay permission flow (already solved separately).
- No GitHub commit — this stays a local plan.

## File Summary
| File | Change |
|------|--------|
| `ui/SettingsScreen.kt` | `isRecording` includes `PAUSED`; new notice text + bold/warning style |
| `service/FloatingOverlayService.kt` | Start collapsed (remove `expand()` on show); `ACTION_COLLAPSE` + `collapse(context)` + handler |
| `MainActivity.kt` | `LaunchedEffect(currentScreen)` → collapse on any screen change |
