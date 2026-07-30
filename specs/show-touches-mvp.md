# Show Touches — MVP

## Problem Statement

The current touch highlight feature draws a custom circle overlay at the center of each tapped Accessibility node. The circle position is often wrong (it reflects the view center, not the user's actual finger position), and Android's accessibility APIs do not expose exact touch coordinates without enabling Touch Exploration Mode (which breaks single-tap UX). The feature needs exact coordinates to be useful, and custom indicators reliably cannot provide this.

Instead of fighting platform limitations, the app should leverage Android's built-in "Show Touches" developer option — a system-level touch indicator that draws at the exact finger position, with zero latency, and no custom code.

## Solution

Replace the custom touch indicator overlay (accessibility service + overlay window + circle drawing) with a Settings toggle that guides users to enable Android's built-in "Show Touches" in Developer Options. The app does not draw circles itself; it only records what Android already shows on screen.

The Settings toggle reflects the real system state of `show_touches`. When the user turns it ON, a dialog explains the feature and opens Developer Options. When they return, the toggle syncs to whether the system setting is actually enabled.

All custom indicator source code stays on disk but is no longer called, referenced, or exposed to users.

## User Stories

1. As a user, I want to see a "Show Touches" toggle in Settings, so that I can control whether touch indicators appear in recordings.

2. As a user who taps the toggle ON for the first time, I want to see a clear dialog explaining what "Show Touches" is and where to find it, so that I understand why I am being sent to system settings.

3. As a user who reads the dialog, I want to tap "Continue" and be taken directly to Android's Developer Options screen, so that I can enable Show Touches.

4. As a user who dismisses the dialog, I want the toggle to remain OFF, so that I am not forced to leave the app.

5. As a user who went to Developer Options and enabled Show Touches, I want the toggle to show ON when I return to the app, so that I can confirm the feature is active.

6. As a user who went to Developer Options but did NOT enable Show Touches, I want the toggle to show OFF when I return, so that I am not misled about the feature state.

7. As a user who already has Show Touches enabled in Developer Options, I want the toggle to show ON the next time I open Settings, so that it reflects the real system state without any extra steps.

8. As a user who disables Show Touches later from Developer Options, I want the toggle to show OFF the next time I open Settings, so that the app always reflects the real state.

9. As a user recording a tutorial video, I want Android's native Show Touches indicator to appear in the recording, so that viewers can see exactly where I tap.

10. As a developer, I want the system-state detection to be a testable pure function, so that I can write unit tests for it.

11. As a developer returning to this feature later, I want the old custom indicator code preserved on disk (not deleted), so that I can revive it if a technically robust solution becomes available.

## Implementation Decisions

### Settings UI — ShowTouchesSetting composable

- A single toggle switch labeled "Show Touches" under the "Tutorial Features" section, placed where the old `TouchHighlightSetting()` was.
- No color picker, shape selector, or size slider — just the toggle.
- The toggle reads the real system state via `Settings.System.getInt(contentResolver, "show_touches", 0) == 1` on:
  - Initial composition (app launch / Settings screen open)
  - Every `onResume` event (returning from Developer Options)
- Toggle behavior:
  - **Toggle ON when system is OFF**: Show confirmation dialog → "Continue" opens `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` → on return, re-check system state → toggle syncs to actual state.
  - **Toggle ON when system is already ON**: No-op (already enabled).
  - **Toggle OFF**: No-op (cannot disable from our app; user must turn it off in Developer Options).
- Dialog text: *"Show Touches is a built-in Android feature that displays a small circle wherever you tap. To enable it: Settings → Developer Options → Input → Show taps. Open Developer Options now?"*
- Buttons: "Open Developer Options" (primary) / "Cancel"

### Manifest — Remove TouchDetectionService

- Remove the `<service android:name=".service.TouchDetectionService">` block from `AndroidManifest.xml`.
- The `.kt` source file (`TouchDetectionService.kt`) stays on disk, unused.

### ScreenRecorderService — Remove overlay wiring

- Remove `if (RecordingPreferences.isTouchHighlightEnabled(this)) { showTouchIndicatorOverlay() }` from `handleStart()`.
- Remove `hideTouchIndicatorOverlay()` calls from `handleStop()` and `cleanup()`.
- Keep `showTouchIndicatorOverlay()`, `hideTouchIndicatorOverlay()`, and the `touchIndicatorView` field in the source file — they are no longer called.

### RecordingPreferences — New key

- Add: `private const val KEY_SHOW_TOUCHES = "show_touches"`
- Add getter/setter: `getShowTouchesEnabled(context)` / `setShowTouchesEnabled(context, enabled)` with default `false`.
- Keep all old keys (`KEY_TOUCH_HIGHLIGHT`, `KEY_TAP_COLOR`, `KEY_TAP_SHAPE`, `KEY_TAP_SIZE`) — unused but harmless.

### Files preserved on disk (not deleted)

- `TouchDetectionService.kt`
- `TouchIndicatorView.kt`
- `IndicatorConfigProvider.kt`
- `TapEventDedupStore.kt`
- `accessibility_service_config.xml`
- Old Settings composables (`TapColorPicker`, `TapShapeSelector`, `TapSizeSlider`, `TapIndicatorPreview`)
- All old prefs keys and methods in `RecordingPreferences`
- All old overlay methods in `ScreenRecorderService`
- `showTouchIndicatorOverlay()`, `hideTouchIndicatorOverlay()` methods
- `TouchIndicators` object
- `IndicatorConfig` data class

### System state detection

```kotlin
internal fun isShowTouchesEnabled(rawValue: Int): Boolean = rawValue == 1
```

This reads `Settings.System.getInt(contentResolver, "show_touches", 0)` and pipes the result through this function. The function itself is pure Kotlin — no Android dependency.

## Testing Decisions

- **What makes a good test**: Test the boolean transformation of the raw system value. No Android APIs, no mocks, no instrumentation.
- **Single test seam**: The internal `isShowTouchesEnabled(Int): Boolean` function. One test file, ~4 tests.
- **Prior art**: `PermissionManagerTest.kt` in `app/src/test/java/com/screenrecorder/manager/` — tests an internal function with raw input values, pure JUnit 4.
- **Test file location**: `app/src/test/java/com/screenrecorder/manager/` (same package as `RecordingPreferences`, where the getter lives).
- **Not testing**: Compose UI rendering, lifecycle observer behavior, system Settings API calls (covered by manual QA).
- **Tests to write**:
  1. `rawValue = 0` → returns `false`
  2. `rawValue = 1` → returns `true`
  3. `rawValue = -1` → returns `false` (any unexpected int)
  4. `rawValue = 999` → returns `false` (any unexpected int)

## Out of Scope

- Color/shape/size customization for touch indicators (preserved in old code but not exposed).
- Any overlay-based touch drawing during recording.
- Accessibility service detection or management (TouchDetectionService removed from manifest).
- Compose UI tests for the toggle or dialog (no prior art in codebase, high setup cost).
- Developer Options enablement guidance (the dialog mentions it; no deep-link to Build Number tapping flow exists).
- Any changes to the recording pipeline, overlays, permissions, or other Settings sections.

## Further Notes

- The grilling session that produced this spec is documented in the conversation history (session date: 2026-07-31).
- Decisions were made one at a time during the grilling, with the user picking from recommended options.
- No GitHub issue tracker is configured for this repo; this spec is stored locally at `specs/show-touches-mvp.md`.
- "ready-for-agent" triage label is not applied (no issue tracker available).
