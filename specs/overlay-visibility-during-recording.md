# Overlay Visibility During Recording

Status: ready-for-agent

## Problem Statement

The app has a "Show Floating Controls" toggle in Settings → Floating Controls that already
controls whether the floating overlay appears during recording (ON = overlay visible in
overlay mode; OFF = "Clean Mode", no overlay at all during recording — the notification is
the only control). But the toggle's name and OFF-state description do not say that it
controls visibility **during recording**, so users do not understand it. Users who want
recordings without the overlay do not realize the existing toggle does exactly that.

## Solution

Rename the existing toggle to **"Show Floating Controls During Recording"** and update its
descriptions so the behavior is obvious:

- Title: "Show Floating Controls During Recording"
- ON description: "Visible on screen and in recordings" (unchanged)
- OFF description: "Hidden during recording — notification controls only"

Also update the Home screen hint text for Clean Mode ("No overlay in recording" →
"No overlay during recording") to match. No behavior or storage changes are made — the
toggle still switches between overlay mode and Clean Mode exactly as today.

In addition, remove the half-built "Hide from Recording" code that was added earlier
(a `hide_overlay_in_recording` preference, an `overlayFlags` window-flags function, a
settings switch, and its unit tests). That work belongs to a future feature — hiding the
overlay from the video only (FLAG_SECURE), while keeping it visible on screen — which is
explicitly deferred.

## User Stories

1. As a user, I want the overlay toggle to be called "Show Floating Controls During
   Recording", so that its name tells me it controls the overlay while recording.
2. As a user, I want the OFF description to say "Hidden during recording — notification
   controls only", so that I understand Clean Mode means no overlay during recording.
3. As a user, I want the ON description to stay "Visible on screen and in recordings", so
   that I understand the overlay appears both live and in the video.
4. As a user, I want the toggle to keep switching between overlay mode and Clean Mode
   exactly as before, so that nothing about recording behavior changes.
5. As a user, I want the Home screen hint to say "No overlay during recording" for Clean
   Mode, so that the wording matches the Settings toggle.
6. As a user who wants clean recordings, I want to find this behavior under the existing
   toggle, so that I don't have to search for a separate hidden-overlay setting.
7. As a developer, I want the half-built "Hide from Recording" code (preference, flags
   function, settings switch, tests) removed from the codebase, so that no dead code or
   unused settings remain.
8. As a developer, I want no new preferences or behavior introduced by this change, so
   that the change is purely user-facing wording.
9. As a future developer, I want the "hide from recording only" feature (overlay visible
   on screen but excluded from the video, via FLAG_SECURE) recorded as future work, so
   that its design decisions are not lost.

## Implementation Decisions

- **Settings UI:** change the title of the existing "Show Floating Controls" switch to
  "Show Floating Controls During Recording", keep its ON description, and change its OFF
  description to "Hidden during recording — notification controls only". No other changes
  to the switch or its logic (it still sets overlay vs. Clean mode).
- **Home screen:** change the Clean Mode hint text from "No overlay in recording" to
  "No overlay during recording" for consistency with the new toggle wording.
- **Revert prior half-built work:** remove the `hide_overlay_in_recording` preference
  (key, default, getter, setter) from the preferences module; remove the `overlayFlags`
  function from the overlay service module and restore the plain window flags in its
  overlay window parameters; remove the "Hide from Recording" switch and its state from
  the settings screen; delete the dedicated unit-test file for the flags function.
- **No storage or behavior changes:** the recording mode preference and all recording
  logic remain untouched. This is a text-only change plus a code cleanup.
- **Future work (recorded, not implemented):** a separate setting that keeps the overlay
  visible on screen during recording but excludes it from the video, using Android's
  `FLAG_SECURE` on the overlay window. Known trade-off: while enabled, the overlay also
  disappears from screenshots and other capture tools. This future setting is disabled
  while recording (no live changes), defaults OFF, and applies only to the floating
  overlay — touch indicators are out of scope for it.

## Testing Decisions

- This change introduces no new logic, so there are no new unit tests. A good test for
  the future FLAG_SECURE feature is: the preference has the right default and round-trips
  through set/get, and the window-flags decision is a pure function returning the base
  flags when off and adding `FLAG_SECURE` when on.
- **Verification:** the existing unit test suite must pass unchanged
  (`gradlew.bat testDebugUnitTest`). Manual check: in Settings, the renamed toggle shows
  the new title/descriptions; turning it OFF and recording shows no overlay during
  recording (screen or video); turning it ON shows the overlay as today; the Home screen
  hint reads "Clean Mode • No overlay during recording".

## Out of Scope

- The "hide from recording only" feature (overlay on screen but excluded from the video,
  FLAG_SECURE) — recorded as future work above.
- Live toggling of overlay visibility while a recording is in progress.
- Any change to recording behavior, storage schema, or the touch indicator overlays.

## Further Notes

- The floating overlay only exists during a recording session (shown at start/resume,
  hidden at stop and lock-pause), so "hidden during recording" and "hidden from the
  video" are the same thing today. The future FLAG_SECURE feature is what separates the
  two.
- Implementation must land on a compiling working tree; at the time of writing, the
  overlay service file was mid-edit in an editor session and did not compile.
