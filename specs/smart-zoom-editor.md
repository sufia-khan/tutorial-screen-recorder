# Manual Smart Zoom Editor (Version 1)

Status: ready-for-agent

## Problem Statement

A user records a tutorial of their phone screen, but the raw recording is
hard to follow: the important details (a tapped button, a small setting, a
list item) are tiny on screen, and nothing draws the viewer's eye to them.
Today the app can only record and play back — there is no way to turn a
recording into a proper tutorial video. Users must open a separate video
editor on a computer, which is exactly what the app's long-term vision
("record → edit → preview → export → share, without opening another editing
app") is meant to eliminate.

The first step of that vision is a Manual Smart Zoom Editor: the user
watches the recorded video, places zoom segments on the areas they care
about, and exports a zoomed tutorial video.

## Solution

Add a Smart Zoom Editor screen, opened by tapping **Edit** (the Edit button
currently shows an "Editing coming soon" toast). The editor shows the
recorded video with play/pause and seeking, a zoom timeline, and tools to
add, edit, and delete zoom segments. A zoom segment is a time range (start
and end) with a zoom scale (1x–3x) and a focus point on the screen; during
that range the exported video smoothly zooms into the focus point, holds,
then zooms back out. The user previews the animation live in the editor —
what they see is exactly what the exported video will look like — then
exports a new zoomed video file next to the original and shares it.

The editor is intentionally narrow: no trimming, cropping, filters, text,
stickers, music, speed, transitions, AI, or automatic zoom generation.
Future versions (custom touch visuals, auto-zoom, tutorial generator) plug
in without rewriting the editor.

## User Stories

1. As a user, I want to tap Edit on a finished recording, so that the
   Smart Zoom Editor opens with my video ready to edit.
2. As a user, I want to play, pause, and seek through the video in the
   editor, so that I can find the exact moments I want to zoom.
3. As a user, I want to see the video timeline, so that I know where in
   the video I am and where my zooms sit.
4. As a user, I want to add a zoom segment at the current playhead, so
   that I can mark the moment where the viewer should look closely.
5. As a user, I want a new zoom segment to default to a sensible length
   (3 seconds) and strength (2x, mid-screen), so that I rarely need to
   tweak it for a basic zoom.
6. As a user, I want to choose where the zoom focuses by tapping the
   video picture (while paused) and dragging a marker to fine-tune, so
   that the zoom lands exactly on the button or area I tapped during the
   recording.
7. As a user, I want to set the zoom strength between 1x and 3x, so that
   I can choose how much the viewer sees the detail.
8. As a user, I want each zoom to animate smoothly — zoom in at the
   start of the segment, hold, zoom back out at the end — so that the
   exported video feels polished, not jumpy.
9. As a user, I want to see every zoom segment as a colored block on a
   timeline bar, so that I can see at a glance when each zoom happens.
10. As a user, I want to select a segment by tapping it, so that I can
    edit or delete it.
11. As a user, I want to drag a segment's edges on the timeline to change
    its start and end time, so that I can adjust timing visually.
12. As a user, I want to edit a selected segment's scale and exact times
    with sliders and fields in a bottom panel, so that I can fine-tune it
    precisely.
13. As a user, I want to delete a segment I no longer want, so that my
    timeline only contains real zooms.
14. As a user, I want to see a list of all segments with their time
    ranges, so that I can find and select any segment even when blocks on
    the timeline are small.
15. As a user, I want to preview the full zoom animation while playing,
    so that I can check the result without exporting.
16. As a user, I want the live preview to show exactly what the exported
    video will show, so that there are no surprises in the final file.
17. As a user, I want my zoom segments saved automatically as I work, so
    that I never lose my edits if I leave the editor.
18. As a user, I want to reopen a recording and see my previous zoom
    segments still there, so that I can continue editing across sessions.
19. As a user, I want to export the zoomed video, so that I get a real
    video file of my tutorial.
20. As a user, I want to see export progress with a Cancel button, so
    that I can stop a long export if I change my mind.
21. As a user, I want the exported file to keep the same size and quality
    as the original, so that the zoomed details stay sharp.
22. As a user, I want the exported file saved next to the original with a
    clear name (e.g. "…_zoom.mp4"), so that I can tell the versions
    apart.
23. As a user, I want a Share button after a successful export, so that I
    can send the tutorial video anywhere immediately.
24. As a user, I want to place zoom segments anywhere on the timeline,
    including overlapping ones, so that I am never blocked from building
    the video I want.
25. As a user, when two segments overlap, I want the one that starts
    later to win during the overlap, so that the exported video is always
    deterministic and matches the preview.
26. As a user, I want the zoom focus point clamped so it never leaves the
    video picture at high zoom, so that the viewer never sees empty
    border areas.
27. As a developer, I want the zoom math to live in one pure function
    used by both preview and export, so that the preview always matches
    the export and the math is unit-testable.
28. As a developer, I want the editor, timeline, zoom model, and export
    engine in separate packages, so that future versions (touch visuals,
    auto-zoom, tutorial generator) plug in without refactoring the
    editor.

## Implementation Decisions

- **Architecture:** keep the single app module; create dedicated packages
  for the editor screen, the timeline UI, the zoom domain (model +
  interpolation math), and the export engine. The recording, playback,
  and overlay code stays untouched.
- **Entry point:** the Edit button (currently a toast) opens the editor
  with the recording's file path.
- **Zoom segment model** (agreed during design; inline because it encodes
  the domain precisely):

  ```kotlin
  data class ZoomSegment(
      val id: String,
      val startMs: Long,   // when the zoom-in begins
      val endMs: Long,     // when the zoom-out finishes
      val scale: Float,    // 1.0f..3.0f
      val centerX: Float,  // focus point, normalized 0f..1f (0 = left edge)
      val centerY: Float   // focus point, normalized 0f..1f (0 = top edge)
  )
  ```

- **Zoom engine:** one pure function takes the segment list and a
  timestamp and returns the active transform (scale + center). Behavior:
  smooth ease-in at segment start, hold, smooth ease-out at segment end;
  between segments the video is at 1x. When segments overlap, the one
  starting later wins for the overlapping frames. The focus point is
  clamped so the zoomed view always stays inside the video picture.
  Both the live preview and the exporter consume this same function.
- **Adding a segment:** a toolbar button places a new 3-second segment at
  the current playhead with default scale and screen-center focus, which
  the user can then adjust.
- **Focus placement:** while paused, tapping the video picture places the
  segment's focus marker; dragging the marker fine-tunes it. The focus is
  stored normalized so it works for any video size and rotation.
- **Timeline UI:** a horizontal timeline bar under the video shows each
  segment as a colored block spanning its duration; tap to select, drag
  edges to resize. A segment list below shows every segment with its time
  range.
- **Edit panel:** selecting a segment opens a bottom panel with a scale
  slider (1x–3x), exact start/end controls, and Delete/Done buttons.
- **Preview:** the video surface is scaled using the zoom engine's
  output, so the live preview is a faithful WYSIWYG of the export.
- **Export engine:** Media3 Transformer with a crop+scale effect driven
  by the zoom engine. Output is the same resolution as the source, saved
  in the same folder as "ScreenRecord_xxx_zoom.mp4". Export runs in the
  editor with a progress bar and Cancel; a Share button appears on
  success. If the source ever contains an audio track, the exporter
  preserves it.
- **Persistence:** zoom segments are auto-saved on every change to a
  sidecar file next to the video (e.g. "ScreenRecord_xxx.zoom.json") and
  loaded when the editor opens, so edits survive across sessions. A
  malformed sidecar file is treated as "no segments" rather than a crash.
- **Out-of-scope features are not half-built:** no placeholder UI for
  trimming, cropping, filters, text, stickers, music, speed, transitions,
  or automatic zoom generation.

## Testing Decisions

- A good test verifies external behavior, not implementation details: the
  zoom engine tests assert what the video SHOWS at a given time, and the
  repository tests assert that saved segments come back intact.
- **Zoom engine (primary seam):** pure unit tests (no Android needed)
  covering: 1x outside segments; ease-in start, hold middle, ease-out end
  of a segment; the exact start/end boundaries; overlap resolution
  (later-start wins); scale and focus clamping at edges/corners with
  maximum zoom; and stability of the interpolation curve (monotonic,
  returns to 1x after the segment ends).
- **Zoom segment repository (secondary seam):** round-trip tests
  (write → read → equal), defaults for missing fields, and graceful
  handling of malformed files.
- **Prior art:** the project already runs JUnit unit tests via
  `gradlew.bat testDebugUnitTest`; these seams follow the same pattern.
  UI and Transformer wiring are verified manually: record → edit →
  preview matches exported file, export produces a playable file next to
  the original, re-opening the editor restores segments.

## Out of Scope

- Trimming, cropping, filters, text, stickers, music, speed controls,
  transitions, AI, and automatic zoom generation.
- Microphone/app audio recording (recordings remain silent in V1).
- Custom touch visualization (Version 2 — replaces Android's "Show
  Taps"; the export effect pipeline is designed so it can be added as an
  extra effect without rewriting the exporter).
- Automatic zoom from touch events (Version 3) and the one-click
  Tutorial Generator (Version 4).
- Undo/redo for segment edits.
- Background export with notifications (export runs in the editor with
  cancel support only).

## Further Notes

- The zoom engine being a single pure function is the keystone of this
  spec: it makes the preview trustworthy, the export deterministic, and
  Version 3's auto-zoom trivial (it would only generate `ZoomSegment`
  objects). Version 2's touch visuals and Version 4's ripples/trails
  attach as additional effects in the same export pipeline.
- The focus point is normalized (0–1) on purpose, so segments survive
  video rotation or future resolution changes without re-editing.
- Recordings today are video-only (no audio), so V1 exports are silent;
  the exporter's audio passthrough is a safety net for future versions.
