# Recordings Library on Home Screen (Version 1)

Status: ready-for-agent

## Problem Statement

Today a user who records screen videos has no way to see their recordings
inside the app. After recording, the video silently lands in the device's
Movies/ScreenRecorder folder, and the only way to find it is to open a
file manager app. The home screen has just one Start Recording button in
the middle of an empty screen.

The long-term vision is "record → edit → preview → export → share"
without opening another app. The first step of that vision is a place
inside the app where the user can see all their recordings: a recordings
library on the home screen.

## Solution

Every finished recording is kept in the public Movies/ScreenRecorder
folder (as today) AND copied into the app's own private folder
(`getExternalFilesDir`), so the app owns a copy that the user can't
accidentally delete. The home screen shows all recordings from the
private folder as a scrollable list at the top, with the Start Recording
button pinned below the list. Tapping a recording opens the existing
playback screen. Edit comes later, from the Smart Zoom Editor work.

This version is intentionally narrow: no delete, no share, no rename, no
edit entry point — just a tap-to-play library.

## User Stories

1. As a user, I want to open the app and see all my recordings listed on
   the home screen, so that I can find any video I recorded.
2. As a user, I want the newest recording at the top of the list, so
   that the video I most recently recorded is easiest to find.
3. As a user, I want each recording to show a thumbnail, a friendly
   name, and its duration, so that I can recognize a recording at a
   glance.
4. As a user, I want to tap a recording to play it, so that I can watch
   any of my recordings right away.
5. As a user, I want the Start Recording button always visible below the
   list, so that I can start a new recording even with many recordings.
6. As a user, I want to scroll through the list without the Start button
   moving, so that the button is always reachable.
7. As a user, I want the list to stay on screen while I record, with the
   timer and Pause/Stop controls in the button's place, so that I can
   still see my recordings mid-recording.
8. As a user, when I come back to the home screen, I want new recordings
   to appear automatically, so that I never have to search for them.
9. As a user with no recordings yet, I want to see a friendly "No
   recordings yet" message, so that the empty screen doesn't look
   broken.
10. As a user, I want each recording to also exist in the device's
    Movies/ScreenRecorder folder, so that I can still share it from the
    Files app.

## Implementation Decisions

- **Storage:** recordings keep being saved to
  `Movies/ScreenRecorder/ScreenRecord_<timestamp>.mp4` exactly as today
  (`RecordingManager.kt` is untouched). When a recording stops
  (`ScreenRecorderService`), a background copy is made into
  `context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)/ScreenRecorder/`
  with the same file name. A few seconds of copying for a long video is
  acceptable; the copy runs in a background coroutine so it never blocks
  the UI.
- **List source:** the home list reads the private folder only. If a
  private copy is missing but the public file exists (copy failed or was
  interrupted), the row still shows and playback falls back to the
  public path.
- **Copy lifecycle:** when a recording stops, if a copy with the same
  name already exists, it is overwritten. Failed copies are logged and
  never crash the app — the public file still exists either way.
- **Recordings model:** a small pure helper lists the private folder,
  filters to `.mp4` files, and returns them newest-first with a friendly
  name parsed from the timestamp in the file name (e.g.
  `ScreenRecord_20260801_123456.mp4` → "Recording • Aug 1, 12:34 PM").
- **Home screen layout (`HomeScreen.kt`):** settings gear stays top
  right; below it a scrollable recordings list fills the space; the
  Start Recording button is pinned directly under the list (its current
  size and style stay the same); hint text and version text stay at the
  bottom. While recording or paused, the list stays visible and the
  timer + Pause/Stop controls appear in the Start button's spot.
- **Row contents:** thumbnail (a frame pulled with Android's built-in
  `MediaMetadataRetriever`, loaded lazily as rows scroll into view and
  cached in memory so scrolling stays smooth), friendly name, and
  duration (also from `MediaMetadataRetriever`).
- **Tap action:** a row tap opens `PlaybackActivity.start(context,
  filePath)` with the private copy's path (falling back to the public
  path if needed). Nothing else — no delete, share, or edit yet.
- **Refresh:** the list re-reads the private folder every time the home
  screen appears (a refresh on the screen's show / resume event, plus
  when the recording session transitions back to idle). No live folder
  watching.
- **Empty state:** when the folder has no recordings, show "No
  recordings yet" centered in the list area.

## Testing Decisions

- **Recordings listing helper (primary seam):** pure unit tests (no
  Android needed) covering: only `.mp4` files are listed; files are
  sorted newest first; the friendly name is parsed correctly from the
  timestamp (including midnight, 12-hour AM/PM edge cases); the fallback
  to the public path works when the private copy is missing.
- **Prior art:** the project already runs JUnit unit tests via
  `gradlew.bat testDebugUnitTest`; this seam follows the same pattern.
  UI, copying, and thumbnail loading are verified manually: record → the
  recording appears on home → tap plays it → the file also exists in
  Movies/ScreenRecorder.

## Out of Scope

- Delete, share, rename, and search actions on recordings.
- An Edit entry point on home rows (the Smart Zoom Editor is a separate
  workstream; its Edit button lives on the preview pill for now).
- Live folder watching / instant list updates in the background.
- Generated thumbnail files on disk (thumbnails are decoded on demand
  and cached in memory only).
- Storing recordings in internal storage (`getFilesDir`) — the copy
  uses external app-private storage, which has more space and needs no
  extra permissions.

## Further Notes

- This work has no file overlap with the Smart Zoom Editor's tickets
  (video player, zoom domain, sidecar JSON, editor shell, timeline,
  focus marker, preview, export) — the two agents can work in parallel
  without merge conflicts. The editor agent touches
  `RecordingPreviewService.kt`; this work touches `HomeScreen.kt` and
  new files only.
- The private copy is the app's own reliable copy of every recording.
  Future features (editor entries on home rows, delete, search) build on
  this model without changing the recording pipeline.
- The current in-progress recording never appears in the list: the
  private copy is only created when the recording stops, so the list
  only ever shows finished recordings.
