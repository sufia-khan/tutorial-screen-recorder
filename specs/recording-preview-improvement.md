# Recording Preview Popup Improvement

Status: ready-for-agent

## Problem Statement

After a recording stops, a preview popup appears showing the saved video. Currently it reads
as "just an image in a box" — the thumbnail is small (42% of the screen width), the phone
frame around it is barely visible, and the Share/Close buttons look identical, giving the
user no visual clue about what to do. The popup should look like a polished product moment:
a clearly phone-shaped mockup, a larger thumbnail, and an obvious primary action.

## Solution

Improve the post-recording preview popup so the saved recording is shown inside a realistic
phone mockup with a bigger thumbnail, a success heading, and a clear primary Share button.
The popup remains a full-screen dimmed overlay with the phone mockup centered, the
duration/size badge inside the phone screen, and Share/Close buttons below.

Locked design:

- **Thumbnail size:** phone mockup width grows from 42% to ~50% of the screen width.
- **Phone mockup:** thicker, more realistic bezel around the screen, and the screen area
  (thumbnail) gets rounded corners matching the phone body. No notch, no side buttons,
  no status bar strip.
- **Duration/size badge:** stays inside the phone, as a dark rounded strip at the bottom
  of the screen ("00:42 • 5.2 MB").
- **Buttons:** Share becomes a red (#E53935) filled pill — the app's existing red accent;
  Close stays a neutral dark pill. Same side-by-side layout.
- **Heading:** "Recording Saved ✓" shown above the phone mockup.
- **Kept as-is:** full-screen dimmed background, white flash on open, spring-in animation,
  15-second auto-dismiss, tap-thumbnail-to-open-video, and the share/close actions.

## User Stories

1. As a user, I want the preview thumbnail to be noticeably bigger, so that I can actually
   see the video I just recorded.
2. As a user, I want the saved recording to appear inside a phone-shaped mockup, so that
   the preview feels like a real product moment instead of a flat image.
3. As a user, I want a thicker bezel and rounded screen corners on the mockup, so that it
   clearly looks like a phone.
4. As a user, I want the mockup to stay clean — no notch, no side buttons, no status bar —
   so that it doesn't look busy.
5. As a user, I want the duration and file size shown at the bottom of the phone screen,
   so that I can quickly check how long and how big the recording is.
6. As a user, I want a "Recording Saved ✓" heading above the mockup, so that I know the
   recording finished successfully before I look at the preview.
7. As a user, I want the Share button to stand out in red, so that the main action is
   obvious at a glance.
8. As a user, I want Close to stay a neutral dark button, so that the secondary action
   doesn't compete with Share.
9. As a user, I want to tap the thumbnail to open the video in my player, so that I can
   review the full recording.
10. As a user, I want the popup to still auto-dismiss after 15 seconds, so that it never
    blocks my screen forever.
11. As a user, I want the same white flash and spring-in animation on open, so that the
    popup still feels polished.
12. As a user, I want sharing and closing to work exactly as before, so that the visual
    change doesn't break anything.

## Implementation Decisions

- **Module changed:** only the recording preview service (the popup shown after a
  recording stops). No other module, preference, or behavior changes.
- **Thumbnail scale:** the mockup width constant increases from 42% to ~50% of the screen
  width. All other dimensions (bezel, corners, badge, buttons) derive from the mockup
  width as they do today, so everything scales together.
- **Phone mockup look:** increase the bezel thickness around the screen; give the screen
  content rounded corners that match the phone body's corner radius (the thumbnail is
  clipped to the rounded screen). No notch, no side buttons, no status bar strip are
  added.
- **Badge:** unchanged in position and style — dark rounded strip pinned to the bottom of
  the phone screen.
- **Buttons:** Share pill fill changes to the app's existing red accent (#E53935); Close
  keeps the neutral dark pill. Layout and sizes unchanged.
- **Heading:** a "Recording Saved ✓" text label is added above the phone mockup inside
  the centered card.
- **Interactions and animation:** open-video on thumbnail tap, share, close, flash,
  spring-in, fade-out, and 15-second auto-dismiss are all preserved unchanged.

## Testing Decisions

- This change is pure visual styling in a raw View service; it contains no testable logic.
  Per the confirmed decision, no new unit tests are added.
- **What makes a good test here (for the future):** any extracted logic (e.g. mockup
  dimension math) would follow the repo's existing pattern of plain JUnit tests over small
  pure functions.
- **Verification:** the existing unit test suite must stay green
  (`gradlew.bat testDebugUnitTest`), plus a manual device check: record a short video,
  confirm the popup shows a phone mockup with a ~50%-width thumbnail, rounded screen,
  badge at the bottom, "Recording Saved ✓" heading, red Share and neutral Close buttons,
  and that tap-to-open, share, close, and auto-dismiss all still work.

## Out of Scope

- Any change to recording, sharing, or storage behavior.
- The floating overlay or any other screen.
- New animations or interaction patterns beyond the visual restyle.
- Any other preview surfaces (none exist today).

## Further Notes

- The popup service is a legacy View-based service (not Compose); the styling is done with
  drawables, layout params, and view properties. Compose theming does not apply here —
  colors are hardcoded constants, and the red accent matches the overlay's stop-icon red
  for consistency.
- The mockup is centered on screen and sized by screen width, so on very small screens the
  ~50% width keeps everything inside the screen with room for the heading and buttons.
