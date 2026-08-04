# Share Sheet Cleanup — Stop Recents Duplicates

Status: ready-for-agent

## Problem Statement

After the "app jumping forward when sharing" fix, the share sheet now opens in its own
separate window. But that window looks exactly like the app (same icon, same name), so
Android treats it as a second copy of the app. Result:

- Every time the user taps Share, a new look-alike window is created, and the recents
  list fills up with duplicate "instances" of the app.
- When the user goes back to "their app" from recents, Android sometimes shows them the
  look-alike window instead: the share sheet floating over the home screen, with the real
  app nowhere behind it.

The share sheet works fine while you're using it. The problem is everything around it:
the recents list and what happens when you switch back to the app.

## Solution

Give the share sheet a **hidden slot** — its own private window that Android is told
never to show in recents and never to match against the app's window when you tap the
app icon or the app's recents card.

Concretely, two small changes:

1. **Manifest** (`AndroidManifest.xml`): declare the proxy activity with an empty task
   affinity (`android:taskAffinity=""`). This labels the share sheet's window as "not
   the app", so tapping the app icon or the app's recents card always opens the real
   app — never the share sheet window.
2. **Proxy launch** (`IntentProxyActivity.kt`, `launch()`): change the flags from
   `NEW_TASK or MULTIPLE_TASK` to `NEW_TASK or EXCLUDE_FROM_RECENTS` (and remove
   `MULTIPLE_TASK`). The share sheet's window never appears in the recents list, and
   sharing repeatedly doesn't stack up new windows.

Everything else stays exactly as it is today:

- Share sheet still opens over whatever the user is doing; the app does not pop up.
- Canceling the sheet still returns the user to where they were.
- The preview popup still closes immediately when Share is tapped.
- The floating overlay's Home/Settings buttons still use the same proxy and keep
  working the same (they open the app's real window, which is what those buttons are
  for).

## User Stories

1. As a user, I want the recents list to show only ONE entry for the recorder app —
   even after sharing several videos.
2. As a user, I want tapping my app in recents to open the real app — never the share
   sheet floating over the home screen.
3. As a user, I want the share sheet to still open over whatever I'm using, without the
   app popping up behind it.
4. As a user, I want canceling the share sheet to return me to where I was.
5. As a user, I want a leftover share sheet (opened but unused, then I switched away) to
   stay hidden behind my app — pressing Back once shows it, pressing Back again closes
   it.

## Implementation Decisions

- **Module changed:** only `IntentProxyActivity.kt` (the `launch()` flags) and
  `AndroidManifest.xml` (the proxy activity's `taskAffinity`). No other module,
  preference, or behavior changes.
- **Flags:** `Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS`,
  `FLAG_ACTIVITY_MULTIPLE_TASK` removed.
- **Manifest attribute:** `android:taskAffinity=""` added to the
  `.service.IntentProxyActivity` declaration. With an empty affinity, the share sheet's
  window can never be matched by the launcher icon or the app's recents card, and it
  can never join the app's real window.
- **Overlay buttons unaffected:** Home and Settings buttons in the floating overlay
  launch through the same proxy; with these changes they still open the app's real
  window and bring it forward — the desired behavior for those buttons.
- **Leftover sheet (confirmed):** if the user opens the sheet, doesn't use it, and
  switches away, the sheet stays open behind the app. Coming back to the app shows the
  app normally; pressing Back once brings the sheet out, Back again closes it. This is
  accepted as-is — the system share sheet cannot be force-closed from outside.

## Testing Decisions

- **Unit tests:** the existing suite must stay green (`gradlew.bat testDebugUnitTest`,
  60 tests). The change is one flag and one manifest attribute — no new testable logic,
  so no new unit tests.
- **Manual device check (the real verification):**
  1. Record a short video; when the popup appears, tap Share — the sheet opens, the app
     does NOT pop up.
  2. Cancel the sheet — you return to where you were.
  3. Share again twice more (cancel each time) — open recents: exactly ONE recorder app
     entry, no duplicates.
  4. With the app open, share, then press Home, open another app, then open recents and
     tap the recorder app — the real app shows (with any leftover sheet hidden behind
     it), never the sheet over the home screen.
  5. Overlay Home/Settings buttons still open the app.

## Out of Scope

- Any change to how sharing works (intent, chooser, file permissions).
- The custom share sheet idea (rejected — the system sheet keeps Nearby Share/Copy).
- The preview popup's look or behavior.
- The in-app media player.

## Further Notes

- This relies on Android's standard task rules: a task with empty affinity is created
  fresh on demand and never merges with the app's task, and `EXCLUDE_FROM_RECENTS`
  keeps it out of the recents list. Both are stable, supported behavior on all
  supported Android versions (the app targets API 29+).
- The transparent proxy theme stays unchanged; the sheet's window is fully covered by
  the opaque share sheet while open, so nothing looks different visually.
