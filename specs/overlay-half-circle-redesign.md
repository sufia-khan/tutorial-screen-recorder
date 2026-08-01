# Overlay Redesign — Half Circle with Arc Action Buttons

## Goal

Replace the pill-shaped floating overlay with a **half-circle** overlay:
- The overlay is **always a half circle** (both collapsed and expanded) — never a full circle.
- The **flat edge sits flush against the screen edge** (no gap), the curved edge points inward toward the screen content.
- The **timer is always dead center** of the half circle, in both states.
- When expanded, **5 action buttons fly out along the curved edge** in an arc.
- More internal padding than the current pill (12dp).

## Decided Design

### Shape & Orientation
- Shape: half circle (semicircle), drawn as a rounded-corner rect (top-left + bottom-left corners rounded at radius R, flat right edge) when docked on the **right**; mirrored when docked on the **left**.
- Size: radius R = **40dp default** (sticks out 40dp from the edge, height 80dp).
- User preference: radius **30–60dp**, applied via a new slider in Settings.
- Edge margins: flat edge **flush** against the screen edge (0dp); **8dp** top/bottom margin from screen edges.

### Collapsed state
- Just the half circle with the timer, **dead center**.
- Timer: white, bold, **14sp**.
- Tap on the half circle → expand.

### Expanded state
- The half circle **stays the same size and shape**; the window grows to fit the buttons.
- 5 buttons appear **along the curved edge in an arc**, order top → bottom:
  1. **Home** — opens the app's main screen (MainActivity)
  2. **Pause/Play** — toggles pause/resume (icon switches to play when paused)
  3. **Stop** — stops recording
  4. **Settings** — opens the app's Settings screen
  5. **Collapse** — collapses back to timer-only
- Buttons: **no background circle**, bare icons floating over the screen.
- Icon colors: **black** for all except **Stop = red**.
- Collapse icon: chevron pointing **toward the screen edge** (direction flips with dock side).
- Home icon: house; Settings icon: gear. Both canvas-drawn like existing icons.
- Visible icon ≈ **14dp**; touch area ≈ **32dp** (bigger tap target, transparent padding).
- Gap between curved edge and button centers: **4dp**.
- Appearance animation: buttons **slide/fly out from behind the half circle** along their arc path (~220ms, staggered).

### Behavior
- **Start expanded**: when recording starts, buttons are visible immediately; then auto-collapse after the delay.
- **Auto-collapse**: same as now — after inactivity (pref, default ~2.5–3s, configurable 1–10s) collapse back to timer-only.
- **Collapse after action**: after tapping **any** action button (Pause, Stop, Home, Settings) → collapse back.
- **Tap outside** (while expanded): collapses back (via `FLAG_WATCH_OUTSIDE_TOUCH` → `ACTION_OUTSIDE`).
- **Drag**: same as before — free drag anywhere, snap to nearest edge (left/right). Curve flips to point inward depending on docked side.
- **Background**: current dark color `0xDD1C1C1C`, and the existing **overlay opacity setting (30–100%) is now actually applied** to the half circle background alpha.
- **Pause button**: toggles to play icon while paused (as today).
- Recording mode setting still applies: CLEAN mode = no overlay at all.

## Files to Change

| File | Change |
|------|--------|
| `app/src/main/java/com/screenrecorder/service/FloatingOverlayService.kt` | Main rework: half-circle drawable, arc button layout, fly-out animation, flush-to-edge + 8dp top/bottom margins, opacity applied, size from prefs, mirroring by dock side, start-expanded, tap-outside collapse, collapse-after-action, new icons (house, gear, edge-facing chevron, red stop) |
| `app/src/main/java/com/screenrecorder/manager/RecordingPreferences.kt` | Add `overlay_size` pref (Int dp, default 40, clamp 30–60) with getter/setter |
| `app/src/main/java/com/screenrecorder/ui/SettingsScreen.kt` | Add "Overlay Size" slider (30–60dp) next to existing overlay settings |
| `app/src/main/java/com/screenrecorder/MainActivity.kt` + `MainViewModel.kt` | Read an intent extra (e.g. `open_screen=settings`) so the overlay's Settings button lands on the Settings screen; Home button just opens MainActivity normally |
| `app/src/main/AndroidManifest.xml` | No change expected (MainActivity already exported; proxy already exists) |

## Implementation Notes

- **Half circle drawable**: `GradientDrawable.cornerRadii` with two corners at R and two at 0, on a rect of width R × height 2R. Mirrored via `setCornerRadii` order when docked left.
- **Arc positions**: circle center at flat-edge midpoint. Button centers at angles 0° (top) → 180° (bottom) along the curved edge, offset `R + 4dp + buttonRadius`. The 5 buttons sit at 0°, 45°, 90°, 135°, 180°.
- **Expanded window size**: grows to fit all buttons (wider + taller than the collapsed R×2R window); animate width/height like today's `animateOverlay`, keeping the flat edge pinned to the screen edge.
- **Fly-out animation**: each button animates translation from the flat-edge side + alpha 0→1 with a small stagger; reuse `ValueAnimator` + `AccelerateDecelerateInterpolator`.
- **Tap outside**: add `FLAG_WATCH_OUTSIDE_TOUCH` to window params while expanded; on `ACTION_OUTSIDE` collapse. Remove flag when collapsed.
- **Touch area**: buttons are ImageViews with transparent padding — visual icon ~14dp, total touchable ~32dp.
- **Opening screens**: use existing `IntentProxyActivity.launch(context, Intent(MainActivity, ...))`; Settings intent carries an extra that MainActivity/ViewModel reads on start (and in `onNewIntent` if resumed).
- **Opacity**: `finalAlpha = round(255 * opacity / 100)` applied to the dark background color of the half circle.

## Out of Scope
- `RecordingPreviewService`, `TouchIndicatorView`, `ScreenRecorderService` core recording — untouched.
- Notification-based (CLEAN mode) flow — untouched.
