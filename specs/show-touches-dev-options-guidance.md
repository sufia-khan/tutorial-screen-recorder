# Show Touches — Developer Options Guidance

## Problem Statement

The Show Touches toggle currently opens a dialog that sends the user straight to Developer Options with no guidance. If Developer Options is not yet enabled on the device, the user lands on a blank or confusing page and has no idea what to do next. Users need clear, step-by-step instructions delivered inside the app before they ever leave for Settings, so they know exactly what to do when they get there.

## Solution

Replace the current confirmation dialog (which has "Open Developer Options" and "Cancel") with a taller scrollable guidance dialog that walks the user through every step, from enabling Developer Options to turning on Show Taps. The dialog includes a button to jump directly to the "About Phone" Settings page where the Build Number lives, so users can start the process immediately. The toggle remains OFF until the user actually enables Show Taps in system settings and returns to the app, at which point `onResume` syncs the real state.

## User Stories

1. As a user who turns ON the Show Touches toggle for the first time, I want to see a full step-by-step guide inside the app, so that I know exactly what to do without guessing.

2. As a user who reads the guide, I want to tap "Open About Phone" to jump directly to the Build Number location, so that I can start enabling Developer Options immediately.

3. As a user who already has Developer Options enabled, I want the guide to still show the full steps, so that I can skip past the Build Number part and go straight to Show Taps.

4. As a user who dismisses the guide by tapping "Got It", I want the toggle to stay OFF, so that the app only shows the feature as enabled when it's actually working.

5. As a user who follows the steps and enables Show Taps in system settings, I want the toggle to show ON when I return to the app, so that I can confirm the feature is active.

6. As a user who returns to the app without having enabled Show Taps, I want the toggle to show OFF, so that I am not misled about the feature state.

7. As a user who already has Show Taps enabled from a previous session, I want the toggle to show ON immediately when I open Settings, so that I don't have to redo anything.

8. As a user who disables Show Taps later from Developer Options, I want the toggle to show OFF the next time I open Settings, so that the app always reflects the real system state.

9. As a user recording a tutorial video, I want to understand how to enable the native touch indicators through clear in-app guidance, so that my recordings show exactly where I tap.

## Implementation Decisions

### ShowTouchesSetting dialog — replaced

The existing dialog inside the `ShowTouchesSetting()` composable is replaced with a taller scrollable `AlertDialog`. This dialog contains:

- **Title**: "Enable Show Touches"
- **Body**: Full step-by-step guidance covering both Developer Options enablement and Show Taps toggling. The guidance text reads as follows:

  *"Show Touches is a built-in Android feature that displays a small circle wherever you tap. To enable it, follow these steps:*

  *1. Open the Settings app on your phone*
  *2. Scroll to About Phone → find Build Number → tap it 7 times → you'll see 'You are now a developer!'*
  *3. Go back — Developer Options will now appear in Settings*
  *4. Open Developer Options → scroll down to the Input section*
  *5. Tap Show taps to turn it ON*
  *6. Return to this app — the toggle will update automatically"*

- **Buttons**:
  - **"Open About Phone"** (primary button) — opens `Settings.ACTION_DEVICE_INFO_SETTINGS` via an `Intent`. This takes the user to the About Phone page where Build Number lives. The user can then follow the remaining steps in the guide on their own.
  - **"Got It"** (text button) — dismisses the dialog only. No intents fired. The toggle remains OFF.

### Toggle behavior — no change to state machine

- Toggle ON when system is OFF → show guidance dialog → user reads and dismisses → toggle stays OFF → `onResume` syncs only when system state actually changes.
- Toggle ON when system is already ON → no-op (system already active).
- Toggle OFF → no-op (user must disable via Developer Options).
- The `navigatedToSettings` flag is no longer needed since no intent is fired from the dialog that expects a return. However, the `DisposableEffect` + `LifecycleEventObserver` pattern stays for `onResume` sync in case the user navigates to Settings on their own (or via the "Open About Phone" button). The `navigatedToSettings` flag and its guard condition are removed; `onResume` always re-checks the system state.

### No change to RecordingPreferences

No new keys, getters, or setters are needed. The existing `KEY_SHOW_TOUCHES` and its accessors remain untouched.

### No change to tests

The existing 4 unit tests for `isShowTouchesEnabled(Int)` continue to pass unchanged. No new test seams are introduced.

### No change to preserved files

All files preserved in the MVP spec (`TouchDetectionService.kt`, `TouchIndicatorView.kt`, `IndicatorConfigProvider.kt`, `TapEventDedupStore.kt`, etc.) remain on disk and untouched.

## Testing Decisions

- **What makes a good test**: Only external behavior, not UI rendering or lifecycle hooks. The existing test seam (the pure function `isShowTouchesEnabled(Int)`) covers the boolean transformation. Compose dialog rendering and `onResume` sync behavior are covered by manual QA.
- **No new test seams needed**: The existing seam in `RecordingPreferences` already tests the system-state detection function. The dialog content is declarative Compose — testing it would require instrumentation tests with no prior art in this codebase.
- **Prior art**: `RecordingPreferencesTest.kt` in `app/src/test/java/com/screenrecorder/manager/`.

## Out of Scope

- Any changes to the dialog title, icon, or visual design beyond making it scrollable and adding the guidance text.
- Color/shape/size customization for touch indicators (still preserved but not exposed).
- Any overlay-based touch drawing during recording.
- Compose UI tests for the toggle or dialog (no prior art, high setup cost).
- Accessibility service detection or management.
- Any changes to the recording pipeline, overlays, permissions, or other Settings sections.
- Adding a "nudge" dialog if user returns without enabling Show Taps (no re-dialog).
- Multi-step wizards or image-based tutorials.

## Further Notes

- The grilling session that produced this spec is documented in the conversation history (session date: 2026-07-31).
- This spec is an evolution of `specs/show-touches-mvp.md`. The MVP spec covers the initial toggle infrastructure (prefs, test seam, manifest/service cleanup). This spec covers only the improved guidance dialog UX.
- No GitHub issue tracker is configured for this repo; this spec is stored locally at `specs/show-touches-dev-options-guidance.md`.
