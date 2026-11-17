# STATUS

**Last updated:** 2026-11-17
**Updated by:** AI session — fast load + CRUD list animations device-verified

## Current phase

**Phase 2 — Link capture & organization: ✅ COMPLETE**
**List UX (fast load + live CRUD updates + item animations): ✅ COMPLETE & device-verified**
Next: Phase 3 — Search & file support.

## Repository state

- Phase 0 docs: complete (`brain/INDEX.md` file map).
- Phase 1 & 2 code: **implemented & verified** — Gradle project (AGP 8.7.3, Gradle
  wrapper 8.11.1, JDK 17), `app` module, Java 17, minSdk 26/targetSdk 35.
- Build: `assembleDebug` OK; unit tests **pass**.
- Real device `e893ddcf` (Realme RMX3660) fully tested via ADB.
- Bug fixes during device testing:
  1. Fixed `ClassCastException` in `LinksAdapter$Holder` (`AppCompatImageButton` cast to `MaterialButton` -> changed to `View`).
  2. Fixed `fitsSystemWindows="true"` on root layout so toolbar insets below status bar.
  3. Fixed `setSupportActionBar` + `NavigationUI.setupActionBarWithNavController` in `MainActivity` so action bar, back navigation, and overflow menu work.

## Phase 2 exit-criteria evidence (2026-11-16, device `e893ddcf`, Realme RMX3660)

| Criterion | Evidence |
|---|---|
| Share Intent capture | `ACTION_SEND` intent with URL received -> review/save screen displayed prefilled with title & URL. |
| Review screen saving | Saved to SAF storage as JSON file: `/sdcard/Link/user/categories/cat_ungrouped/link_284e5180-...json`. Schema v1 verified. |
| Home list rendering | Link displayed in RecyclerView with title, URL, category, and overflow menu without crash. |
| Pin / unpin action | Pinned via menu -> star pin icon appeared in UI -> `"pinned": true` verified in JSON on disk. |
| Category management | Created "Tech" category -> verified in `categories.json` on disk -> "Tech" chip rendered on Home. |
| Move link | Moved link from "Uncategorized" to "Tech" -> physical JSON deleted from `cat_ungrouped` and created in `cat_15ef4f77-.../` -> UI updated to "Tech". |
| Delete link with undo | Deleted link with dialog confirmation -> removed from disk (`total 0`) -> snackbar with "Undo" displayed. |

## List UX evidence (2026-11-17, device `e893ddcf`)

| Criterion | Evidence |
|---|---|
| Fast app open | `IndexRepository` reads persisted `index.json` v2 on cold start — **first paint 342ms** (device logcat tag `LV`); background verify rescan (1224ms) no longer blocks paint. |
| Data shows on app open | Cold start -> Home RecyclerView rendered saved links (GitHub record) without manual refresh. |
| Create updates list live | FAB -> Add Link -> typed `https://example.com/fast-test` -> Save -> back on Home, new `example.com` row visible immediately (DiffUtil insert). |
| Delete updates list live | Row menu -> Delete -> confirm -> `example.com` row gone, remaining item intact (DiffUtil remove). |
| Item animations | `LinksAdapter.submit()` now uses `DiffUtil` (was `notifyDataSetChanged`); `DefaultItemAnimator` durations set (add 220ms / remove 200ms / change 180ms / move 220ms). |
| Fast open load | Removed duplicate first load (constructor + onResume both called `refresh()`); single load in `onResume` only. |

## Done

- [x] Phase 0: analysis, docs, brain folder, approved decisions.
- [x] Phase 1: foundation & local JSON storage, SAF engine, codecs, repo.
- [x] Phase 2: Share Intent, save/review screen, link CRUD UI, category screens, tags + pinned UI, overflow menu, on-device ADB verification.

## Next action

Phase 3: Search & sorting, rebuildable index (`IndexRepository`), file import (`ACTION_OPEN_DOCUMENT` + `FileCopyWorker`), file metadata JSON, file actions.

## Blockers

- None. Ready for Phase 3.
