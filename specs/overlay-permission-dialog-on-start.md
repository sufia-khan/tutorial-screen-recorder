# Overlay Permission Dialog on Start — Final Plan

## The problem

Tapping **Start Recording** while the overlay permission is missing currently jumps **straight** to the device settings page (MainActivity.kt:270-274) with a toast. No choice is given, and Clean Mode users are sent there too even though they don't use the overlay.

## Decisions (agreed)

| # | Decision | Choice |
|---|----------|--------|
| 1 | Dialog replaces the auto-jump | **Yes** — a friendly dialog with two options |
| 2 | Button 1: "Go to Settings" | Opens the device overlay settings page to grant permission |
| 3 | Button 2: "Control from Notification Only" | Recording starts anyway, no overlay, controls from the notification |
| 4 | After "Notification Only" | Recording flow continues normally (screen-capture dialog appears) |
| 5 | Remember the denial | **Yes** — once chosen at Start, never asked again at Start (Settings toggle still re-prompts when turned ON while permission is missing) |
| 6 | Back from Settings WITHOUT granting | Proceed without overlay — no loops, no blocking |
| 7 | Back from Settings WITH granting | Continue automatically — screen-capture dialog appears immediately |
| 8 | Clean Mode | Never asked — starts directly (fixes existing bug) |
| 9 | Buttons | Exactly two — no Cancel button |

Draft message text (adjustable): *"Floating controls need the 'Display over other apps' permission. Allow it to see controls on screen, or continue and control recording from the notification."*

## Implementation Steps

### Step 1 — Only ask in Floating Controls mode (MainActivity.kt)
- In `onStartRecording()`, check `RecordingPreferences.getRecordingMode(this) == RecordingMode.OVERLAY` **before** the permission check.
- Clean Mode → skip the whole permission block, go straight to the screen-capture intent (fixes the existing bug).

### Step 2 — Show the dialog instead of jumping (MainActivity.kt)
- Replace the current `openOverlaySettings(this)` + toast block with a state that shows an `AlertDialog` (Compose, same style as the Settings toggle dialog).
- State: `var showOverlayStartDialog by remember { mutableStateOf(false) }` in the composable; set `true` when Start is tapped without permission (Floating Controls mode).
- Dialog buttons:
  - **"Go to Settings"** → `PermissionManager.openOverlaySettings(this)`; set a `pendingOverlayStartAfterGrant = true` flag.
  - **"Control from Notification Only"** → remember denial (`RecordingPreferences.setOverlayStartDenied(context, true)`), then proceed with the recording start.

### Step 3 — Remember the denial (RecordingPreferences.kt)
- Add `KEY_OVERLAY_START_DENIED` boolean preference + `isOverlayStartDenied(context)` / `setOverlayStartDenied(context, value)`.
- In `onStartRecording()`: if `isOverlayStartDenied` → skip the dialog entirely, start recording directly (Floating Controls mode only; Clean Mode never asks anyway).

### Step 4 — Handle the return from device settings (MainActivity.kt)
- `onResume()`: if `pendingOverlayStartAfterGrant`:
  - `hasOverlayPermission()` == true → clear flag, launch the screen-capture intent (continue automatically).
  - still false → clear flag, proceed without overlay (start recording anyway, no loop).
- `pendingOverlayStartAfterGrant` resets on a fresh `onCreate` (plain field, not persisted).

### Step 5 — Keep the Settings toggle prompt as-is
- The existing "Allow Overlay" dialog in Settings (when turning ON "Show Floating Controls" without permission) stays unchanged — it already handles the grant-return flow.

## Device Test Checklist (after implementation)
1. Fresh install → tap Start (Floating Controls mode, no permission) → **dialog appears** (not the settings page).
2. Tap "Control from Notification Only" → screen-capture dialog appears → record → no pill shown, notification has Pause/Resume/Stop.
3. Stop → tap Start again → **no dialog** (remembered) → recording starts directly.
4. Fresh deny → Settings → turn ON "Show Floating Controls" (permission still missing) → existing "Allow Overlay" dialog appears (unchanged).
5. Tap Start → dialog → "Go to Settings" → grant → return → screen-capture dialog appears automatically.
6. Tap Start → dialog → "Go to Settings" → return WITHOUT granting → recording starts anyway (no overlay).
7. Clean Mode + no permission → tap Start → **no dialog**, screen-capture dialog appears directly.

## Out of Scope
- Changing the existing Settings-toggle permission dialog.
- Any change to the notification controls.
- No GitHub commit — this stays a local plan.

## File Summary
| File | Change |
|------|--------|
| `MainActivity.kt` | Mode check before permission; dialog state + buttons; `pendingOverlayStartAfterGrant` flag + `onResume` continuation |
| `manager/RecordingPreferences.kt` | New `KEY_OVERLAY_START_DENIED` preference + getter/setter |
