# Trash & Edit Actions + Navigation Bar (Version 1)

Status: ready-for-agent

## Problem Statement

A user who records screen videos can see and play their recordings in the
app, but cannot delete or edit them from the home screen. Editing is only
reachable from the small preview pill after a recording stops. And the
app has no navigation structure — the settings gear floats at the top of
the home screen, and there is no way to browse "everything I can do".

The long-term vision is "record → edit → preview → export → share"
without leaving the app. The next step of that vision: every recording
gets Edit and Delete actions right on the home screen, a Trash so
deletion is safe and reversible, and a bottom navigation bar that makes
Home, Edit, Settings, and Trash one tap away.

## Solution

Add a bottom navigation bar with four tabs: **Home**, **Edit**,
**Settings**, and **Trash**.

- **Home** keeps the recordings library; each row gains Edit and Delete
  buttons next to the thumbnail and name. Tapping the row still plays.
  The settings gear icon is removed (Settings is now a tab).
- **Edit** shows the same recordings list; tapping a row opens the Smart
  Zoom Editor for that video.
- **Settings** is the existing settings screen, now reachable from the
  bar instead of the gear.
- **Trash** shows recordings the user has deleted, with the time left
  before they are permanently removed from the app ("23 hrs left") and a
  Restore button on each item.

**Delete is safe:** tapping Delete shows a friendly confirmation dialog
("Move to Trash?"). Confirming moves the recording to the Trash: it
disappears from Home and Edit, and its app-private copy is hidden. The
public video file in Movies/ScreenRecorder is NEVER deleted by the app.
After 24 hours, the app permanently removes its private copy and the
private zoom sidecar; the device file stays. A recording can be restored
from Trash at any time before the 24 hours are up.

## User Stories

1. As a user, I want to delete a recording from the home screen, so that
   I can clean up my list.
2. As a user, I want a clear confirmation before anything is deleted, so
   that I never lose a recording by accident.
3. As a user, I want deleted recordings to go to a Trash instead of
   vanishing forever, so that I can undo a mistake.
4. As a user, I want to see how much time is left before a trashed
   recording is removed from the app, so that I know when to restore it.
5. As a user, I want to restore a trashed recording, so that it comes
   back to my home list exactly as it was.
6. As a user, I want the app to clean up trashed recordings after 24
   hours on its own, so that I never have to think about it.
7. As a user, I want my video files to stay on my device even after
   deletion from the app, so that I never lose the raw recording.
8. As a user, I want to open the Smart Zoom Editor from the home screen,
   so that I can edit any recording without hunting for buttons.
9. As a user, I want an Edit tab that shows my recordings and opens the
   editor, so that editing is one tap away.
10. As a user, I want a bottom navigation bar with Home, Edit, Settings,
    and Trash, so that I always know where I am and how to get anywhere.

## Implementation Decisions

- **Trash store:** a small persistent store in the app's private
  storage (a JSON file) holding one entry per trashed recording: the
  file name and the timestamp when it was trashed. The store supports
  add, list, restore (remove entry), and is the single source of truth
  for what the Trash screen shows.
- **24-hour expiry:** the purge runs every time the app opens or the
  home/trash screen appears: any entry older than 24 hours is dropped
  from the store and the app's private copy of that video (plus its
  private zoom sidecar, `*.zoom.json` next to the video) is deleted.
  The public file in Movies/ScreenRecorder is never touched.
- **Delete flow:** the Delete button on a home row opens a Material 3
  confirmation dialog: title "Move to Trash?", message "The video stays
  on your device. It will be removed from the app after 24 hours.",
  buttons Cancel / Move to Trash. Confirming adds the entry to the
  trash store and the recording disappears from Home and Edit lists
  immediately (lists filter out trashed file names).
- **Trash screen:** lists trashed entries with the friendly recording
  name and the time left ("23 hrs left" when an hour or more remains,
  "59 min left" below that, "Less than a minute" in the final minute).
  Each row has a Restore button. Restoring removes the entry from the
  store and the recording reappears in Home/Edit immediately. An empty
  store shows "Trash is empty".
- **Navigation bar:** a Material 3 bottom bar with four tabs (Home,
  Edit, Settings, Trash) replaces the single-screen navigation in the
  main activity. Home no longer shows the settings gear icon. The
  existing screens (Home library, Settings) keep their content. The
  Edit tab reuses the same recordings list component as Home, but a row
  tap opens the Smart Zoom Editor instead of playback. Playback and the
  editor remain separate activities launched on top.
- **Restore of a public-only recording:** a recording whose private copy
  was already missing still restores (it simply re-lists from the
  public file via the existing fallback).

## Testing Decisions

- **Trash store (primary seam):** pure unit tests (no Android needed)
  covering: add → list round-trip; restore removes the entry; entries
  older than 24 hours are reported as expired; entries under 24 hours
  are not; the remaining-time label ("23 hrs left" / "59 min left" /
  "Less than a minute"); corrupted or missing store files load as
  empty without crashing.
- **Prior art:** the project runs JUnit unit tests via
  `gradlew.bat testDebugUnitTest`; this seam follows the same pattern.
  UI flows (dialog, tab switching, delete → trash → restore) are
  verified manually on device.

## Out of Scope

- Permanently deleting the public video file from the device (the app
  never deletes device files; users can delete them from the Files app
  themselves).
- Trash expiry shorter/longer than 24 hours or per-item custom
  durations.
- A trash-emptying "Empty Trash" button.
- Editing actions beyond opening the Smart Zoom Editor (no zoom
  features added here).
- Rename, share, or search actions on recordings.

## Further Notes

- The store keeps only tiny JSON entries, so trashing 100 recordings
  uses a few kilobytes — the hidden private video copies are the real
  storage cost, and they are freed 24 hours after deletion.
- Both the Home and Edit lists must filter out trashed file names; the
  single trash store powers Home, Edit, and Trash screens.
- This work touches the navigation shell of the app for the first time;
  the editor and playback activities stay untouched.
