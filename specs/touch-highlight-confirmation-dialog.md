# Touch Highlight Confirmation Dialog

## Problem Statement

When a user taps the "Touch Highlight" toggle in Settings to turn it on, the app immediately navigates to the system Accessibility Settings screen without any explanation. This sudden navigation is confusing and jarring — the user does not understand why they left the app or what they are expected to do next.

## Solution

Before navigating to Accessibility Settings, show a confirmation dialog that explains why the navigation is needed and asks the user for permission. If the accessibility service is already enabled, skip the dialog and navigation entirely — just flip the toggle on silently.

## User Stories

1. As a user, I want to see a clear explanation before being sent to system settings, so that I understand why the navigation is happening.

2. As a user, I want to be able to cancel the action if I change my mind, so that I am not forced to leave the app unexpectedly.

3. As a user who already has the accessibility service enabled, I want toggling Touch Highlight to work immediately without any dialog or navigation, so that I don't see unnecessary interruptions.

4. As a user who cancelled the dialog, I want the toggle to remain off, so that my preference is respected and no partial state is left behind.

5. As a user who confirmed and navigated to Settings but did not enable the service, I want the toggle to revert to off when I return, so that I am not left in a broken state where the preference says on but the feature doesn't work.

6. As a user who navigated to Settings and enabled the service, I want the toggle to stay on when I return, so that the feature works without me having to toggle it again.

7. As a user who accidentally taps back or outside the dialog, I want it treated as a cancel, so that the standard Android dismiss gesture works as expected.

8. As a developer, I want the accessibility service check to be a testable utility function, so that I can write unit tests for it.

## Implementation Decisions

- **Dialog component**: Material3 `AlertDialog` (not a custom Dialog or ModalBottomSheet). It is the standard Android confirmation pattern and the codebase already uses Material3.

- **Dialog title**: "Enable Touch Highlight?"

- **Dialog message**: "To show tap indicators during recording, the app needs accessibility access. Open Settings to enable it?"

- **Buttons**: "Go to Settings" (primary) / "Cancel". Back gesture and outside tap also count as Cancel.

- **Toggle-showing order**: The toggle stays visually OFF until the user confirms. On confirm: toggle flips ON, preference is persisted, then navigation fires. This avoids a revert step.

- **Skip-if-already-enabled check**: Before showing the dialog, check if the accessibility service is already enabled via a new utility function. If yes, skip dialog + navigation entirely — just flip the toggle and persist.

- **Resume check**: When the app resumes after navigating to Settings, check if the service is now enabled. If not, revert the toggle to OFF, remove the preference, and show a Toast: "Accessibility service not enabled. Touch Highlight is off."

- **Rapid-toggle protection**: While the dialog is open, subsequent taps on the toggle are ignored (the dialog already captures focus). No debounce or queue needed.

## Testing Decisions

- **What makes a good test**: Test external behavior (the boolean result), not implementation details (how the check queries `Settings.Secure`). The function should be a pure transformation of a system state into a boolean.

- **Single test seam**: Extract the accessibility service check into a pure utility function (e.g. `fun isAccessibilityServiceEnabled(context: Context): Boolean`). Test with mocked/enabled scenarios.

- **Prior art**: `TapEventDedupStoreTest.kt` in `app/src/test/java/com/screenrecorder/service/` follows the same pattern — pure unit test, JUnit 4, no instrumentation.

- **Not testing**: Compose UI rendering of the dialog (no prior art for Compose UI tests in the codebase, high setup cost for a simple dialog).

## Out of Scope

- Applying the same confirmation pattern to other permission-gating toggles (e.g., overlay permission). This can be addressed in a follow-up if needed.
- Compose UI tests for the dialog component.
- Accessibility service detection via `AccessibilityService` lifecycle callbacks (rely on polling via `Settings.Secure` on resume).
- Any animation or transition effects for the dialog.

## Further Notes

- The grilling session that produced this spec is in the conversation history (session date: 2026-07-30).
- Decisions were made one at a time during the grilling, with the user picking from recommended options.
- No GitHub issue tracker is configured for this repo; this spec is stored locally at `specs/touch-highlight-confirmation-dialog.md`.
- "ready-for-agent" triage label is not applied (no issue tracker available).
