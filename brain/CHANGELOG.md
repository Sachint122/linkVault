# Changelog

Format: date — change — impact. Newest first.

## 2026-11-17 (fast app-open: index.json read-back)

- **Problem:** app open took seconds — `IndexRepository` wrote `.index/index.json`
  but never read it, so every cold start did a full SAF scan of every record
  file *and* an index write before the list could render.
- **`IndexRepository.loadAll`:** cold start (no mutation this process) now
  reads persisted `index.json` (schema v2 = full LinkRecord/FileRecord JSON via
  codecs) — one file read instead of N. Legacy v1/absent/corrupt index falls
  back to full rescan and rewrites v2 (self-migrating).
- **Staleness guard:** `invalidate()` sets `dirtySinceScan` — any post-mutation
  load still does a full disk rescan. After the fast paint,
  `consumeColdVerify()` triggers one authoritative background rescan so a
  kill mid-mutation self-heals on next open (DiffUtil posts corrections with
  animation).
- **`HomeViewModel.refresh`:** paint first (log: `first paint`), then optional
  `verify rescan` — logs under tag `LV`.
- **Device-measured (`e893ddcf`):** first paint **342ms**, background verify
  rescan 1224ms (no longer blocks paint). List + schema v2 index verified on
  device. Build green, 58/58 tests pass.

## 2026-11-17 (fast list load + CRUD animations)

- **`LinksAdapter`:** replaced `notifyDataSetChanged()` with `DiffUtil` callback
  (id + title/subtitle/category/pin/tags/updatedAt/category-name compare) ->
  item insert/remove/change/move now animate via `DefaultItemAnimator`.
- **`HomeFragment`:** explicit animator durations (add 220ms, remove 200ms,
  change 180ms, move 220ms).
- **`HomeViewModel`:** removed `refresh()` from constructor — first load now
  happens only in `HomeFragment.onResume()`, eliminating a double disk scan on
  open (was constructor + onResume back-to-back).
- **Device-verified on `e893ddcf`:** open shows saved data; add via FAB ->
  Save -> new row appears immediately; row menu -> Delete -> row disappears.
  Build green, 58/58 unit tests pass.
- Compile fixes earlier same session: `androidx.appcompat.widget.PopupMenu`
  (material popupmenu package missing from cached AAR), `TextView` field types
  in SaveLink/Home fragments, `MainActivity.container()` accessor,
  `UrlPolicy.normalizeForCompare` strips leading `www.` (fixes
  `UrlPolicyTest.duplicates_sameResourceDifferentTracking_equal`).

## 2026-11-16 (Phase 2 verified complete on real device)

- **Share Intent & Review flow verified:** `ACTION_SEND` intent with URL received -> review/save screen displayed prefilled with title & URL -> saved to SAF storage as JSON file under `/sdcard/Link/user/categories/cat_ungrouped/`.
- **Home list display & Pin verified:** Saved link rendered in `RecyclerView` with title, URL, category, and overflow menu; Pinned via menu -> star pin icon appeared in UI -> `"pinned": true` verified in JSON on disk.
- **Category management & Link Move verified:** Created "Tech" category -> verified in `categories.json` on disk -> "Tech" chip rendered on Home; Moved link from "Uncategorized" to "Tech" -> physical JSON deleted from `cat_ungrouped` and created in `cat_15ef4f77-.../` -> UI updated to "Tech".
- **Delete with Undo verified:** Deleted link with confirmation dialog -> removed from disk -> snackbar with "Undo" displayed.
- **Critical fixes during device testing:**
  1. Fixed `ClassCastException` in `LinksAdapter$Holder` (`AppCompatImageButton` cast to `MaterialButton` -> changed to `View`).
  2. Fixed `fitsSystemWindows="true"` on root layout so toolbar insets below status bar.
  3. Fixed `setSupportActionBar` + `NavigationUI.setupActionBarWithNavController` in `MainActivity` so action bar, back navigation, and overflow menu work.
- **brain/STATUS.md: Phase 2 = COMPLETE.** Next: Phase 3 (Search, Rebuildable Index, File Import/Management).

## 2026-11-16 (Phase 1 verified complete)

- **All Phase 1 exit criteria device-verified** via adb + uiautomator-dump
  automation on Realme RMX3660 (`e893ddcf`): SAF pick/grant persisted;
  CRUD self-test **PASS** (fresh + post-recovery); force-stop restart keeps
  grant; folder deletion detected ("revoked/missing" UI) → reselect
  (picker → USE THIS FOLDER → ALLOW) → granted + PASS again; physical
  `/sdcard/Link/user/categories/categories.json` inspected (schema v1,
  uncategorized system category present).
- Helper script: `%TEMP%\opencode\lv_ui.ps1` (dump/find/tap helpers) —
  reusable for later Phase 2+ device checks.
- **brain/STATUS.md: Phase 1 = COMPLETE.** Next: Phase 2 (Share Intent,
  save/review screen, categories UI, tags + pinned).

## 2026-11-16 (Phase 1 implementation)

- **Android project initialized:** Gradle 8.11.1 wrapper, AGP 8.7.3,
  single `app` module, Java 17, minSdk 26/targetSdk 35, Groovy DSL,
  Material 3 theme, Navigation component, `git` not yet initialized.
- **Phase 1 code written** (packages ui/domain/data/work-ready): App +
  AppContainer (manual DI), MainActivity + NavHost (onboarding → home),
  OnboardingFragment (ACTION_OPEN_DOCUMENT_TREE + persistable grant),
  StoragePermissionManager (SharedPreferences tree URI, startup
  revalidation, release-old-grant), DocumentTreeClient (DocumentsContract
  only, path→docId cache, rename-support probe), SafeWriteEngine
  (tmp → validate → rename-swap / copy-replace → verify + crash
  recovery), org.json LinkCodec/CategoryCodec with schema validation
  (NEWER_SCHEMA never overwritten), CategoryRepository (uncategorized
  invariant, ID = folder name), RecordRepository (CRUD, move, issue scan),
  HomeFragment/HomeViewModel with on-device **CRUD self-test** + issues
  scan.
- **Build green:** `assembleDebug` OK; **34/34 unit tests pass**
  (PathSanitizerTest, LinkCodecTest, CategoryCodecTest,
  SafeWritePlannerTest); 2 test-expectation bugs found and fixed by tests.
- **Debug APK installed + launched on real device** `e893ddcf`.
- Compile fixes applied: `renameDocument` returns Uri; caught
  `FileNotFoundException`; `UriPermission` import.
- **Pending:** owner's 4-step device verification (pick folder → self-test
  → restart survival → revoke/recovery) before Phase 2 starts.

## 2026-11-16 (decision round)

- **D-01 APPROVED: Java + XML layouts** (owner knows Java, not Kotlin) —
  stack revised across all docs: Fragments + Navigation, ViewModel +
  LiveData, Executor-based async, `org.json` for JSON (D-05 revised),
  Material Components. Rejected Kotlin/Compose for this project.
- **D-02 APPROVED:** minSdk 26 / targetSdk 35.
- **D-08 APPROVED:** no network metadata fetch in MVP.
- **D-09 APPROVED: tags + pinned/favourite IN MVP** (schema fields added to
  link records; tags also on file records) — scope grows slightly in
  Phase 2.
- **D-10 APPROVED:** 100 MB per-file import cap, any MIME type.
- **Communication preference recorded:** explain Android concepts in simple
  Hinglish (see `brain/STATUS.md`, D-01).
- Remaining ADRs (D-04, D-05, D-06, D-07, D-11, D-12, D-13) stay PROPOSED
  as non-blocking defaults; spec §13 coverage table added to
  `brain/DECISIONS.md`.

## 2026-11-16

- **Initialized `brain/` context hub** (INDEX, STATUS, DECISIONS,
  CHANGELOG) so future sessions read this folder instead of the whole
  project. No impact on app code (none exists yet).
- **Analyzed project directory:** empty apart from
  `LINKVAULT_MASTER_GOAL.md` — project must be initialized from scratch.
- **Created full documentation set:** `README.md`, `REQUIREMENTS.md`,
  `ARCHITECTURE.md`, `DATA_SCHEMA.md`, `IMPLEMENTATION_PLAN.md`,
  `AI_CONTEXT.md`, `TESTING_STRATEGY.md`.
- **Proposed technology stack:** Kotlin + Jetpack Compose (M3), single
  Activity, manual DI, kotlinx.serialization, coroutines/Flow, WorkManager,
  SAF-only storage. Status: PROPOSED (D-01, D-04, D-05, D-12).
- **Proposed data model:** v1 schemas for link/file/category records,
  `categories.json`, rebuildable `index.json`, backup `manifest.json` —
  see `DATA_SCHEMA.md`.
- **Phase 0 substantially complete**; waiting on approval before Phase 1
  (project initialization).
