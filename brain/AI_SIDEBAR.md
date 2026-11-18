# AI Sidebar — system-wide writing assistant (D-20)

**Status:** code complete, build + **122/122** unit tests green; device
verification pending (owner connecting device via adb).

**Owner directive (2026-11-18):** system-wide floating AI writing assistant
that reuses the **existing Qwen3-0.6B / LiteRT-LM engine** — pill + expandable
panel, 10 tones, 5 actions, shared inference queue with link-AI, background
processing, 10-entry local history, 6 settings, offline only.

## Hard rules (from spec)

- **No second model, no cloud, no Accessibility service.**
- Java + XML only; no unrequested features (no Explain Changes, no favourite
  tones, no boot receiver).
- Clipboard is read only on expand or explicit Paste — never monitored.

## Architecture

```
Settings toggle / MainActivity.onCreate (foreground only)
        │
        ▼
SidebarService (FOREGROUND_SERVICE_SPECIAL_USE, notif id 4701,
                channel "sidebar_ai", icon ic_stat_sidebar)
        │  owns exactly one window (AiContract.SIDEBAR_WINDOW_TAG)
        ▼
SidebarOverlay ─── pill (WRAP_CONTENT, NOT_FOCUSABLE, NOT_TOUCH_MODAL,
        │            drag + snap edge + yFraction persist, tap → expand)
        ├── panel (MATCH_FOCUS, dim + 87% width, top-anchored, IME-aware)
        ├── panel History view (10 entries, load/delete/clear)
        └── panel Settings view (6 settings rows)
        │
        ▼ submit(system, prompt, MAX_WRITE_TOKENS=640, cb)
AppContainer.inferenceQueue()  ← single daemon FIFO shared with link-AI
        │  exactly-once; cancel = pending pre-run discard / in-flight discard
        ▼
AiWorker.generateSync → LiteRT-LM (ai_model.litertlm, already installed)
```

- **One window, two states** (collapsed pill ↔ expanded panel) — never two
  overlays, so state (history, settings, result) survives collapse/expand.
- Start only from Settings toggle or `MainActivity.onCreate` — background FGS
  start is an Android 12+ restriction; **no boot receiver** (spec).
- `START_STICKY`; on redelivery re-checks enable toggle + overlay permission.
- Service `onDestroy → overlay.destroy()`; destroy() sets `destroyed` first so
  no late callback touches a dead window.

## Staleness guards (SidebarOverlay)

| Guard | Purpose |
|---|---|
| `runSeq` | every run bumps it; cancel/new run invalidates old callbacks |
| `syncingChips` | programmatic chip checks don't trigger regenerate |
| `renderingSettings` | switch `setChecked` doesn't fire listeners |
| `displayedIsHistoryEntry` | history-loaded result survives collapse/expand |
| `destroyed` | no touch of window/views after destroy |

## Permissions & Android restrictions handled

- `SYSTEM_ALERT_WINDOW` — pill needs "Display over other apps"; requested via
  `ACTION_MANAGE_OVERLAY_PERMISSION` (Settings card button + panel toggle).
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` + property
  `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` — Android 14+ typed-FGS rule.
- **No `POST_NOTIFICATIONS`** — FGS notifications are exempt from the
  Android 13+ runtime notification permission.
- `VIBRATE` — haptic setting.
- Background start avoided (Android 12+ `ForegroundServiceStartNotAllowed`);
  start = user action in-app or app-open foreground.
- Overlay touch: expanded window is `MATCH_PARENT` focusable, so taps inside
  the panel never fall through to other apps; outside-tap = dim view click →
  collapse.

## UI map

**Pill:** edge-anchored dot, long-press-free drag (touchSlop separates tap
from drag), snaps to nearest edge, persists `pill_edge` + `pill_y_fraction`.
Dot hidden when `result_unviewed` is false (seen result → no badge).

**Panel header:** `← back / ✕ close`, History, Settings buttons.

**Panel main (`sidebar_main.xml`):**
- input field + Paste button (explicit clipboard read, try/catch SecurityException)
- 10 tone chips (single-selection ChipGroup) + "Custom instructions ▾" toggle
- 5 action buttons: Rewrite, Improve, Shorten, Expand, Translate
- progress row + Cancel (pending cancelled pre-run; in-flight output discarded)
- result card: tone tabs (tap = regenerate in that tone), text,
  Copy / Regenerate / Clear input

**History view:** 10 entries (tone, time, input, output), tap = load entry,
per-entry delete, Clear all.

**Panel Settings view (6):** Enable, Auto-collapse after copy, Clipboard
autofill on expand, Background processing, Indicator (fixed "Small dot" row),
Haptic feedback — all persist to `sidebar_prefs` via `SidebarSettings`.

**Main Settings card (`fragment_settings.xml`):** Enable switch + overlay
permission status/button only (other 5 live in the panel — spec's placement).

## Data (all local, `sidebar_prefs` / `writing_history.json`)

- `SidebarSettings`: enabled, auto_collapse_copy, clipboard_autofill,
  background_processing, haptic, indicator, pill_edge, pill_y_fraction,
  result_unviewed, pending_request, current_result.
- `current_result` JSON: `{input, output, tone, action, custom, ts}` — pill
  reappears with last result after app restart.
- `pending_request` written before submit, **cleared on overlay attach**
  (stale spinner must not return — spec §7).
- `WritingHistory`: 10 entries max (FIFO), `Entry(input, output, tone, ts)`.

## Generation flow

1. Action click → validator (`WritingValidator.sanitize` strips model
   preamble lines like "Sure, here's…") → history/pending persist → submit.
2. `InferenceQueue` FIFO on one daemon thread; link-AI and sidebar share it,
   so generations never run in parallel.
3. Callback marshalled to main thread; `runSeq` checked → stale results
   dropped, exactly-once.
4. Success → render, `AiUpdateNotifier` untouched (no vault change), copy
   → auto-collapse setting honored + unviewed dot cleared.
5. Failure/invalid output → `sidebarError` shown, retry via Regenerate.

## Settings placement (why split)

Main Settings card answers only "kya chalu hai + permission" (the two things
a user needs to find the feature); fine-grained controls live in the panel's
own Settings view next to where they matter (spec §6: panel settings screen).
The panel's Enable row and the Settings card switch are the same
`sidebar_prefs` value — both re-render on resume.

## File map

| File | Role |
|---|---|
| `ai/SidebarSettings.java` | prefs accessors (87 lines) |
| `ai/InferenceQueue.java` | shared FIFO + exactly-once + cancel |
| `ai/Writing{Action,Tone,Prompt,Validator,History}.java` | pure AI layer |
| `ai/AiContract.java` | `MAX_WRITE_TOKENS=640`, `SIDEBAR_WINDOW_TAG` |
| `sidebar/SidebarService.java` | specialUse FGS host, notif, ensureOverlay |
| `sidebar/SidebarOverlay.java` | full window/interaction logic |
| `res/layout/sidebar_*.xml` (5) | overlay, main, history, item, settings |
| `res/drawable/bg_sidebar_*.xml` (5) + `ic_stat_sidebar.xml` | styles/icons |
| `AndroidManifest.xml` | 4 permissions + service + FGS subtype property |
| `ui/settings/SettingsFragment.java` | AI Sidebar card wiring |
| `ui/MainActivity.java` | guarded start on app open |

## Tests

**Unit (green, 122/122):** `InferenceQueueTest` (11 — FIFO, exactly-once,
cancel pre/in-flight, multi-submit ordering), `WritingPromptTest` (8 — system
prompt per action+tone+custom, user framing), `WritingValidatorTest` (9 —
preamble strip, empty/garbage rejection, tone word guards),
`WritingHistoryTest` (7 — FIFO cap 10, ordering). Plus 87 pre-existing.

**Device checklist (pending):**

1. `adb install -r app-debug.apk`; `adb shell appops set com.linkvault.app
   SYSTEM_ALERT_WINDOW allow`.
2. Open app → pill visible on edge (enabled default ON).
3. Drag pill left/right → snaps + persists after app restart.
4. Tap pill → panel expands, dim behind; tap outside → collapse.
5. Expand without typing → clipboard autofill fills input (setting ON).
6. Run each of 5 actions once → spinner → result (model ~30-60 s cold).
7. Switch tones from result tone tabs → regeneration fires.
8. Cancel mid-run → no stale result lands.
9. Copy → auto-collapse (setting ON) + dot badge on new result.
10. History: entries accumulate to 10, load/delete/clear work.
11. Panel Settings: toggle each of 6 → persists across restart.
12. Kill app from recents, reopen → pill + last result restored, no spinner.
13. Link-AI (share-sheet summary) while sidebar idle → both still work
    (shared queue sanity).
14. Settings card: switch off → pill gone; switch on → pill back.

## Known limitations (documented, spec-sanctioned)

- Cancel cannot abort LiteRT-LM mid-inference (no API) — output discarded
  on arrival; queue freed only when the token finishes.
- Special-use FGS is a Google Play declaration concern; this build is a
  sideloaded debug/release APK, not Play-distributed.
- Indicator is fixed "Small dot" only (no edge colour options — not
  requested).
