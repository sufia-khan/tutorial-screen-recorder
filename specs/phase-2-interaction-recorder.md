# Phase 2 — Interaction Recorder (Final Plan)

## 1. Goal

While the screen is being recorded, capture every user interaction (tap, long press, scroll, screen change) as a structured `InteractionEvent` and save it to `interactions.json` inside the current `RecordingSession`.

Nothing visual happens. No rendering, no zoom, no highlights, no UI changes. This phase only records data that future phases (Smart Zoom, Touch Highlights, Ripple Effects, Tutorial Generation, AI Editing) will consume.

## 2. What already exists (Phase 1 facts)

- `RecordingSession` model with an `interactionsPath` file.
- `RecordingSessionManager` creates sessions and eagerly writes an empty `interactions.json` (`[]`).
- `SessionJsonCodec` with `encodeInteractions(List<InteractionEvent>)` / `decodeInteractions(...)` — JSON is only ever touched by this codec.
- Empty placeholder models ready to be filled in: `InteractionEvent`, `TimelineInfo`, `TouchInfo`, `ElementInfo`, `ScreenInfo` (in `session/model/`).
- `UuidSerializer` for serializing UUIDs.
- Timeline sync: `RecorderRuntime` holds wall-clock anchors (`recordingStartedAtMs`, `pausedAccumulatedMs`, `pauseStartedAtMs`, all in `SystemClock.elapsedRealtime()`) and a pure `computeElapsedSeconds()` function that excludes paused time.
- `TouchDetectionService` (an `AccessibilityService`) exists but is **not registered in the manifest**. It currently shows visual indicators and logs debug info (both to be removed).
- `accessibility_service_config.xml` currently listens only for click / long-click / context-click events.
- `TapEventDedupStore` (bounds-keyed, 200ms dedup, pure Kotlin, unit-tested) exists but is unused.
- `ScreenRecorderService` creates the session on start and finalizes it on stop (writes metadata + thumbnail; never writes interactions yet).

## 3. Decisions (grilled and agreed)

| # | Decision | Choice |
|---|----------|--------|
| 1 | Accessibility service | Repurpose `TouchDetectionService`; register it in the manifest; remove visual-indicator call and debug logging |
| 2 | Event types | Click (Tap), Long-click (Long Press), Scrolled (Scroll), Window-state-changed (Screen Change). Context-click is dropped. |
| 3 | Model shape | Nested groups, filling the Phase 1 placeholders |
| 4 | Event id | UUID (`UuidSerializer`) |
| 5 | While paused | Drop events — nothing recorded while `RecorderRuntime.state == PAUSED` |
| 6 | Duplicates | Ignore duplicates; throttle scrolls; documented below |
| 7 | Save timing | Write once on stop (in-memory buffer during recording) |
| 8 | File writing | New `RecordingSessionManager.saveInteractions(session, events)` calling `SessionJsonCodec.encodeInteractions` |
| 9 | Components | 3 classes in a new `com.screenrecorder.interaction` package; no repository/serializer classes |
| 10 | Wiring | `InteractionRecorder` is a singleton object; `ScreenRecorderService` calls `begin()`/`end()`; `TouchDetectionService` calls `onAccessibilityEvent(event)` |
| 11 | Failure paths | Save interactions on every path where the session exists (success, invalid video, cleanup) |
| 12 | Save caller | The service calls `sessionManager.saveInteractions(...)` with the list returned by `recorder.end()` |
| 13 | Touch coords | Touch x/y = element bounds center for taps/long-presses; `null` for scrolls |
| 14 | Extra element field | Add `viewIdResourceName` (requires `flagReportViewIds` in the config) |
| 15 | Testing | Pure-JVM unit tests + manual device checklist; no new test dependencies |
| 16 | Enablement | Manual enablement via system settings (no UI changes this phase) |
| 17 | Rapid same-element taps | Accept the 200ms window as-is; a genuine double-tap on the same element merges into one event (documented trade-off) |

## 4. The InteractionEvent model (fill the placeholders)

All new fields are `@Serializable`; anything Android may not provide is `null` — never invented.

```
InteractionType (enum): TAP, LONG_PRESS, SCROLL, SCREEN_CHANGE

InteractionEvent
├── id: UUID                  (UuidSerializer)
├── type: InteractionType
├── timeline: TimelineInfo
│   ├── monotonicMs: Long     (SystemClock.elapsedRealtime() at event time)
│   └── videoMs: Long         (normalized: pauses excluded — see section 7)
├── touch: TouchInfo?         (null for SCROLL and SCREEN_CHANGE)
│   ├── x: Int
│   └── y: Int
├── element: ElementInfo?     (null when the event has no source node)
│   ├── className: String?
│   ├── packageName: String?
│   ├── text: String?
│   ├── contentDescription: String?
│   ├── viewIdResourceName: String?
│   ├── clickable: Boolean?
│   ├── enabled: Boolean?
│   ├── focused: Boolean?
│   ├── selected: Boolean?
│   ├── checkable: Boolean?
│   ├── checked: Boolean?
│   ├── left: Int?, top: Int?, right: Int?, bottom: Int?     (bounds)
│   ├── centerX: Int?, centerY: Int?                          (calculated)
│   └── scroll fields (only for SCROLL): scrollX, scrollY, maxScrollX, maxScrollY, deltaX, deltaY (all Int?)
└── screen: ScreenInfo?
    ├── width: Int
    ├── height: Int
    └── rotationDegrees: Int   (0/90/180/270)
```

Naming note: bounds and center stay flat inside `ElementInfo` (no extra `Bounds` class — Phase 1 has no placeholder for one).

## 5. Event filtering strategy (documented)

All logic lives in a pure, testable `InteractionDedupStore` (builds on the existing `TapEventDedupStore` pattern).

- **TAP / LONG_PRESS**: duplicate if the same (type, bounds) was recorded within `tapWindowMs` (200ms, configurable).
- **TAP after LONG_PRESS**: a click on the same bounds within `longPressSuppressMs` (500ms) of a recorded long press is dropped — it's the release of the same long press.
- **SCROLL**: same-bounds scrolls are throttled to one per `scrollThrottleMs` (50ms); scroll events with `deltaX == 0 && deltaY == 0` are skipped.
- **SCREEN_CHANGE**: no dedup (each is a distinct screen transition).
- Stale entries are cleared continuously (same approach as `TapEventDedupStore.clearStale()`).
- Documented trade-off: two real taps on the *same* element within 200ms are treated as one event. The window is a constructor parameter.

## 6. Timeline sync (reuses Phase 1 architecture)

- `monotonicMs` = `SystemClock.elapsedRealtime()` captured in the recorder — the same clock `RecorderRuntime` anchors use, so nothing drifts.
- `videoMs` = normalized video time = `computeElapsedMs(nowMs, recordingStartedAtMs, pausedAccumulatedMs, pauseStartedAtMs)` reading the anchors from `RecorderRuntime`.
- Add a pure `computeElapsedMs(...)` function (millisecond precision version of `computeElapsedSeconds`) with unit tests mirroring `ComputeElapsedSecondsTest`. The renderer will later consume only `videoMs`.

## 7. Components (3 new classes)

All in `com.screenrecorder.interaction`:

1. **`InteractionRecorder`** (object, singleton — same pattern as `RecorderRuntime`)
   - `begin()` — clears the buffer and dedup store; marks the session active.
   - `onAccessibilityEvent(event)` — gate 1: only when active; gate 2: only when `RecorderRuntime.state == RECORDING` (drops paused events); then map → dedup → append to buffer. Never throws.
   - `end()` — idempotent; returns the buffered `List<InteractionEvent>` snapshot and clears.
   - Owns no session reference, no file I/O, no knowledge of rendering.
2. **`AccessibilityEventMapper`** — maps `AccessibilityEvent` → `InteractionEvent`.
   - Split in two halves so it is testable without Android: `extract(event)` (thin Android layer, reads node fields/bounds/event data) and `build(extracted)` (pure Kotlin, assembles the model). Only the pure half is unit-tested.
   - The whole mapping for one event is wrapped in try/catch — a missing source node or a weird event never crashes the recorder.
   - Does not call `recycle()` (deprecated no-op since API 33).
3. **`InteractionDedupStore`** (pure Kotlin, no Android imports)
   - Constructor: `(now, tapWindowMs = 200, longPressSuppressMs = 500, scrollThrottleMs = 50)`.
   - `shouldRecord(type, bounds, time)` per section 5.

## 8. Serialization strategy

- Models are `@Serializable`; `SessionJsonCodec.encodeInteractions` serializes the list (a plain JSON array) — **no manual JSON strings anywhere**.
- Add `RecordingSessionManager.saveInteractions(session, events: List<InteractionEvent>)` which encodes via the codec and writes to `session.interactionsPath`. The codec remains the only JSON touchpoint.

## 9. Manifest and config changes

`AndroidManifest.xml` — register the accessibility service:

```xml
<service
    android:name=".service.TouchDetectionService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

`accessibility_service_config.xml` — new event types + report view ids (needed for `viewIdResourceName`):

```xml
android:accessibilityEventTypes="typeViewClicked|typeViewLongClicked|typeViewScrolled|typeWindowStateChanged"
android:accessibilityFlags="flagReportViewIds"
android:canRetrieveWindowContent="true"
android:notificationTimeout="0"
```

## 10. Wiring in ScreenRecorderService

- `handleStart()`: call `InteractionRecorder.begin()` right after `sessionManager.createSession()`.
- `handleStop()`: after `recordingManager.stopRecording()` and the validity check, call `val events = InteractionRecorder.end()` and then `sessionManager.saveInteractions(session, events)` — on **both** the success and the invalid-video path, inside try/catch (non-fatal), on a background thread (coroutine on `Dispatchers.IO` — coroutines are already used in the project).
- `cleanup()` (service destroyed mid-recording): same `end()` + save before `markSessionFailed()`.
- `TouchDetectionService`: `onAccessibilityEvent(event)` now only forwards to `InteractionRecorder.onAccessibilityEvent(event)`. Remove the `TouchIndicators.show(...)` call and the `logTapDebugInfo` debug block. `onInterrupt()` stays.

## 11. Performance

- Collection on the main thread is cheap: no tree walks, no recycling, a few field reads + one list append per event. No allocations per event beyond the model itself.
- The 50ms scroll throttle caps scroll-burst growth.
- File write happens once per recording, on a background thread.
- No disk writes during recording (decision #7).

## 12. Error handling

- Missing source node → `element = null`, event still recorded with timeline + screen.
- Any field Android doesn't provide → `null`.
- A single bad event never crashes the recorder — the per-event try/catch swallows it.
- Serialization/write failures are caught and logged; they never fail the session finalize.

## 13. Testing plan

**Unit tests (pure JVM, JUnit4, no new dependencies):**
- `InteractionDedupStoreTest` — duplicate click within 200ms dropped; click after long-press within 500ms dropped; scroll throttle; zero-delta scroll skipped; stale entries cleared; different bounds not deduped; window values configurable.
- `ComputeElapsedMsTest` — no pause, accumulated pause, currently paused, boundary, clock guard (mirrors `ComputeElapsedSecondsTest`).
- `InteractionEventSerializationTest` — full event round-trip (all fields), null-field round-trip, empty list → `[]`, corrupted input → empty list.
- `AccessibilityEventMapperTest` — pure `build()` half: correct model for tap, long press, scroll, screen-change extracted data.

**Manual device checklist (spec scenarios):**
1. Tapping buttons → TAP events with correct element/bounds/center.
2. Long pressing → LONG_PRESS event.
3. Scrolling lists → throttled SCROLL events with deltas.
4. Opening a new screen → SCREEN_CHANGE event.
5. Returning to a previous screen → another SCREEN_CHANGE event.
6. Rapid consecutive taps on different elements → all recorded.
7. Duplicate accessibility events → merged/ignored (verify no doubles in `interactions.json`).
8. Pause mid-recording, tap while paused, resume → no events during the paused period; `videoMs` continues correctly after resume.

Verify `interactions.json` inside the session folder (`sessions/<sessionId>/`) after each scenario.

## 14. Sample interactions.json (illustrative, from a short recording)

```json
[
  {
    "id": "f7c2a1b4-9d3e-4a5b-8c6d-2e1f0a9b8c7d",
    "type": "TAP",
    "timeline": { "monotonicMs": 48230123, "videoMs": 2450 },
    "touch": { "x": 540, "y": 1200 },
    "element": {
      "className": "android.widget.Button",
      "packageName": "com.example.notes",
      "text": "Add Note",
      "contentDescription": null,
      "viewIdResourceName": "com.example.notes:id/btn_add",
      "clickable": true,
      "enabled": true,
      "focused": false,
      "selected": false,
      "checkable": false,
      "checked": false,
      "left": 380, "top": 1150, "right": 700, "bottom": 1250,
      "centerX": 540, "centerY": 1200,
      "scrollX": null, "scrollY": null, "maxScrollX": null,
      "maxScrollY": null, "deltaX": null, "deltaY": null
    },
    "screen": { "width": 1080, "height": 2400, "rotationDegrees": 0 }
  },
  {
    "id": "9b1d2e3f-4a5c-6b7d-8e9f-0a1b2c3d4e5f",
    "type": "LONG_PRESS",
    "timeline": { "monotonicMs": 48230980, "videoMs": 3307 },
    "touch": { "x": 540, "y": 1750 },
    "element": {
      "className": "android.widget.TextView",
      "packageName": "com.example.notes",
      "text": "Grocery list",
      "contentDescription": null,
      "viewIdResourceName": "com.example.notes:id/list_item_title",
      "clickable": false,
      "enabled": true,
      "focused": false,
      "selected": false,
      "checkable": false,
      "checked": false,
      "left": 60, "top": 1700, "right": 1020, "bottom": 1800,
      "centerX": 540, "centerY": 1750,
      "scrollX": null, "scrollY": null, "maxScrollX": null,
      "maxScrollY": null, "deltaX": null, "deltaY": null
    },
    "screen": { "width": 1080, "height": 2400, "rotationDegrees": 0 }
  },
  {
    "id": "3e5f6a7b-8c9d-0e1f-2a3b-4c5d6e7f8a9b",
    "type": "SCROLL",
    "timeline": { "monotonicMs": 48232500, "videoMs": 4827 },
    "touch": null,
    "element": {
      "className": "androidx.recyclerview.widget.RecyclerView",
      "packageName": "com.example.notes",
      "text": null,
      "contentDescription": null,
      "viewIdResourceName": "com.example.notes:id/notes_list",
      "clickable": false,
      "enabled": true,
      "focused": false,
      "selected": false,
      "checkable": false,
      "checked": false,
      "left": 0, "top": 600, "right": 1080, "bottom": 2400,
      "centerX": 540, "centerY": 1500,
      "scrollX": 0, "scrollY": 480, "maxScrollX": 0, "maxScrollY": 3200,
      "deltaX": 0, "deltaY": 24
    },
    "screen": { "width": 1080, "height": 2400, "rotationDegrees": 0 }
  },
  {
    "id": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
    "type": "SCREEN_CHANGE",
    "timeline": { "monotonicMs": 48234012, "videoMs": 6339 },
    "touch": null,
    "element": null,
    "screen": { "width": 1080, "height": 2400, "rotationDegrees": 0 }
  }
]
```

## 15. Android limitations (public APIs only)

1. **No raw touch coordinates** — accessibility events never expose the actual finger position. Touch x/y is the element's bounds center; for large views this can differ noticeably from where the finger landed.
2. **No swipe paths** — finger movement is not available; scrolls are recorded as element scroll state + deltas, never as paths.
3. **Scroll delta getters deprecated on API 34+** — `getScrollDeltaX/Y`, `getScrollX/Y`, `getMaxScrollX/Y` still exist but official guidance says to query the source node instead; values may be unreliable on the newest Android versions. We still read them (nullable, try/catch), and note them as scroll position/delta rather than absolute truth.
4. **Service must be enabled manually** — no in-app UI this phase; user enables it in system settings. Without it, zero events arrive.
5. **Apps can block accessibility** — banking/password apps deliberately suppress events; those apps produce no interaction data (and some block screen capture entirely).
6. **Accessibility-data-sensitive apps (API 34+)** — apps can mark content sensitive so it's withheld from non-accessibility-tool services; events may not arrive.
7. **Text masking** — password fields and some apps report empty/null text; `contentDescription` is often missing on plain elements.
8. **Long-press not always announced** — some apps don't emit `TYPE_VIEW_LONG_CLICKED` even though the view supports it; then only a `TYPE_VIEW_CLICKED` (or nothing) arrives.
9. **`viewIdResourceName` is null for many third-party views** (WebView content, some frameworks) even with `flagReportViewIds`.
10. **Window-state-changed events may have no source node** — element info is null; only screen + timeline are recorded.
11. **Same-element rapid double-taps merge** — a documented trade-off of the 200ms dedup window.
12. **Timestamps use `elapsedRealtime`** — monotonic, unaffected by wall-clock changes; `videoMs` excludes paused time by construction.

## 16. Deliverables for Phase 2

1. New classes: `InteractionRecorder`, `AccessibilityEventMapper`, `InteractionDedupStore` (plus `computeElapsedMs` pure function and filled-in models).
2. Responsibility of each class (section 7).
3. `InteractionEvent` model (section 4).
4. Event filtering strategy (section 5).
5. Serialization strategy (section 8).
6. Sample `interactions.json` (section 14).
7. Android limitations (section 15).

Explicitly **not** in scope: Smart Zoom, camera movement, zoom rendering, touch highlights, ripple effects, swipe trails, video editing, Media3 changes, and any UI changes.
