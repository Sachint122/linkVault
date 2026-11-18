# Changelog

Format: date — change — impact. Newest first.

## 2026-11-18 (v1.1.0 release — public APK + README)

- **Version bump:** `versionCode 2` / `versionName 1.1.0`.
- **Release APK** built with the existing release keystore
  (`linkvault-release.jks`, gitignored; verified via apksigner —
  CN=LinkVault) → committed as `releases/LinkVault-v1.1.0.apk`
  (54.4 MB; replaces `releases/linkVault-debug.apk`). Raw download link
  lives in README; `lintVitalRelease` skipped after the Gradle daemon
  OOM-crashed on it (minify is off, non-blocking).
- **README rewritten** end-user-first: download + install steps (unknown
  sources / Play Protect note), feature tour (direct save, on-device AI,
  auto-clean, organize, backup), privacy, build-from-source, docs index.

## 2026-11-18 (auto-clean: AI maintains the vault — D-19)

Owner: "AI khud app clean kare — unused tags/folder hatao, **link delete
na kare**." Build green (**87/87**, 12 new `AutoCleanPlannerTest`), installed,
launch clean; both daily WorkManager jobs verified scheduled on device.

- **`AutoCleanWorker`** (daily `auto_clean_periodic`, scheduled in
  `App.onCreate` like auto-backup):  1. **Folders** — deletes non-system categories with 0 links + 0 files, no
     children, no Auto-Cat rule, untouched ≥30 days (`AutoCleanPlanner`,
     grace on createdAt/updatedAt); also sweeps empty orphan
     `user/categories/*` dirs (non-empty always kept).
  2. **Tags** — prunes redundant tags on link records (tag == folder name,
     singular/plural pair, blanks) via new `RecordRepository.saveTags()`
     (no `updatedAt` bump → browse list doesn't re-sort).
  3. **Never deletes/rewrites link or file records** — planner invariant +
     pre-delete `countIn()` recheck vs. concurrent saves.
  On change: index invalidate + `AiUpdateNotifier.fire(null)`; stats →
  `lv_prefs` (`auto_clean_last_run/_last_stats`).
- **Settings AI card:** new "Auto-clean" line (last run time + "X folder(s),
  Y tag(s) cleaned — links kabhi delete nahi hote"; "abhi tak nahi chala"
  before first run). Card hint updated to mention daily self-cleaning.

## 2026-11-18 (zero-touch save + AI status UI — D-18)

Owner: "user ko kuch karna na pade." Three changes, build green (75/75),
installed, launch + share smoke verified on device (record created with
`aiSource: local_ai` summary in 31 s; smoke record cleaned up).

- **Direct share save (D-18):** ACTION_SEND URL shares no longer open the
  review screen — `MainActivity.directSaveUrl()` persists on a background
  thread (AutoCat → ungrouped), enqueues AI, updates widget/index, toasts.
  File shares / manual add / edit / clipboard offer unchanged.
- **Summary in list:** `item_link.xml` gains a 2-line `itemSummary` row under
  every link; `LinksAdapter` binds `LinkRecord.summary` (hidden when null)
  and DiffUtil now compares summary (AI fills don't bump `updatedAt`, so
  contents-diff must catch it).
- **Settings "On-Device AI" card:** status (worker active / model ready /
  rules-only), model size, queue pending·failed — via new `AiQueueStore`
  counts + `AIQueueManager.stats()`/`isWorkerActive()` and
  `SettingsViewModel.AiStatus` (refreshed on open/resume).
- **Prompt:** "tech" added to category examples (AiPrompt).

## 2026-11-18 (on-device offline AI pipeline — LiteRT-LM)

Full offline AI integration (owner directive: code + docs only, no device
tests): background analysis that fills summary/tags/category for saved links.

- **Runtime:** LiteRT-LM `0.19.1` (official Maven, CPU) — Kotlin interop
  isolated to `ai/LocalAiEngine.kt`; Kotlin plugin **2.4.21** added (the
  AAR ships metadata 2.4.0, unreadable by 2.0.x — discovered at build).
  GGUF unsupported by design; model = user-pushed `.litertlm`
  (`AI_MODEL_SETUP.md`), rule-only fallback when absent.
- **Pipeline:** `RuleBasedAnalyzer` (19 platform rules) always runs; LLM
  enriches when a model is present; `AiResultValidator` enforces the strict
  JSON contract (fences/chatter stripped, ≤5 tags, ≤200-char summary,
  clamped confidence); malformed reply → rules stand (no retry loop).
- **Queue:** single sequential WorkManager job (`ai_queue_worker`, KEEP,
  battery-not-low) + persistent `filesDir/ai_queue.json` (dedupe, 3-attempt
  cap → failed list, 8-min budget with retry, process-death/reboot recovery
  via `App.onCreate` + WorkManager persistence). Enqueue hooks:
  `SaveLinkViewModel` (new links) and `RestoreEngine`.
- **Schema:** 6 additive link fields (`summary, aiStatus, aiConfidence,
  aiSource, aiAnalyzedAt, aiVersion`) in `LinkRecord`/`LinkCodec`; new
  `RecordRepository.saveAi()` (no `updatedAt` bump, folder move) and
  AI-carry in `update()`. Protection: summary fill-if-null, category only
  while `cat_ungrouped` (AutoCat/user always win), tags additive ≤20.
- **Live UI:** `AiUpdateNotifier` → `HomeViewModel.refresh()` (500 ms
  throttle) + `IndexRepository.replaceItem`; model freed on
  `onTrimMemory(≥BACKGROUND)`, re-init latched after real failures.
- **Docs:** `AI_ARCHITECTURE.md`, `AI_MODEL_SETUP.md`,
  `AI_PROCESSING_FLOW.md`, `AI_TESTING.md` (guide only, not executed),
  `TASK.md`, `DATA_SCHEMA.md` §3 updated (AI + dead-link fields),
  decision `D-17`.
- Build green: `assembleDebug testDebugUnitTest` EXIT=0, **75/75** unit
  tests (58 old + 17 new validator/rules tests).

## 2026-11-18 (app-lock removed, optimistic delete, UI polish)

- **App lock (B6) fully removed** per owner decision — deleted
  `LockFragment`, `fragment_lock.xml`, `AppLockManager`,
  `MainActivity.onStart/onStop` gate, Settings PIN card + set-PIN dialog,
  `appLock()` from AppContainer, `androidx.biometric` dependency, nav
  destination. Lock screen UI served as the design reference for the
  polish pass below.
- **Optimistic delete + animation:** `delete`/`deleteAll`/`restore` update
  the in-memory index + list instantly (DiffUtil + DefaultItemAnimator
  remove/add animation), then persist in background; rollback + toast on
  failure. New `IndexRepository.removeItem/addItem/persistCache` (single
  file write; index marked dirty if the write fails).
- **Home refresh bug fixed:** after save, a new item could land above the
  viewport (RecyclerView kept pixel offset on position-0 insert) — the
  list observer now snaps to top when the user was at top
  (`scrollToPosition(0)`).
- **meta_dead badge fix:** `LinksAdapter` called `getString(meta_dead)`
  without the status arg (rendered literal `%1$d`); now formats the HTTP
  status (e.g. `404`).
- **UI polish (lock-screen reference):** subtle `bg_cyber_vault` wallpaper
  on Settings/Save/Onboarding/Categories; cyan section titles; cyber cards
  (surface_card + border + 16dp radius) on Settings/Onboarding and
  category rows; onboarding logo; Categories FAB in cyan.
- Debug `LV` logs stripped; `releases/linkVault-debug.apk` refreshed;
  build green (`assembleDebug testDebugUnitTest` EXIT=0, 58/58).

## 2026-11-17 (feature push: batches 1–10, compile-verified)

All batches green (`assembleDebug testDebugUnitTest` EXIT=0, 58/58 tests).
Owner will device-test each feature before GitHub push (per owner directive:
agent writes code + test guide only).

- **B1 Clipboard detect:** `maybeOfferClipboardSave()` on Home resume —
  offers save when clipboard holds a URL; `last_copied_url` written to
  `lv_prefs`; share intent detected via `isShareAction()`. INTERNET permission
  added (needed by dead-link check).
- **B1 Release signing:** `linkvault-release.jks` + `app/keystore.properties`
  (gitignored), `signingConfigs.release` wired; minifyEnabled stays false.
- **B2 Dead-link check:** `domain/DeadLinkChecker` (HEAD/GET, 5s timeout,
  status capture), `LinkRecord` extended (15-arg ctor, `withCheck`,
  `checkedAt`/`httpStatus`), `RecordRepository.update()/markChecked`,
  `IndexRepository.replaceItem`, HomeViewModel `checkLink`, row-menu item +
  adapter status badges, Settings **Check all links** button with progress.
- **B3 Search extras:** tag filter chips (`currentTagFilter`, `allTags`,
  `setTagFilter`), recent-searches dropdown (AutoCompleteTextView, JSON list
  capped at 10 in `lv_prefs`), saved on `onPause`.
- **B4 Auto-categorize rules:** `AutoRule` + `AutoCatCodec` +
  `AutoCatRepository` (lazy `user/autorules.json`, max 200, domain→category,
  `matchFor` applies on create when folder = Uncategorized); Settings UI
  (rule list, add-rule 2-step dialog, delete-rule dialog).
- **B5 Export CSV/text:** `exportCsv`/`exportText` (proper CSV escaping),
  `CreateDocument` launchers, Settings Export card. Multi-select: long-press
  enters selection mode, `selectionBar` (share/delete/cancel), FAB hides,
  `HomeViewModel.deleteAll` single-pass delete.
- **B6 App lock:** `AppLockManager` (salted SHA-256 PIN in `lv_prefs`,
  in-memory session unlock), `fragment_lock` + `LockFragment` (PIN + biometric
  via `androidx.biometric:1.1.0`, back blocked), gate in `MainActivity.onStart`
  (covers return-from-share), relock in `onStop`, Settings lock card +
  set-PIN dialog (4–12 digits).
- **B7 Auto backup:** `AutoBackupWorker` (WorkManager, daily, self-gates on
  `auto_backup_enabled` pref, writes `backups/linkvault_auto_<ts>.zip`,
  prunes newest 7, retry on failure), scheduled in `App.onCreate()` (unique
  KEEP), Settings card (toggle + run-now + status).
- **B8 Pagination:** HomeViewModel windowed rendering (initial 50, step 100,
  `loadMore()`), RecyclerView scroll listener triggers at 5-from-end.
- **B9 Nested subcategories:** `Category.parentId` (codec round-trip, schema
  stays 1), `CategoryRepository.create(name, parentId)` depth ≤3 + delete
  re-parents children, `IndexRepository.query` category filter → expanded id
  set (self + descendants via `parentByChild`), DFS-ordered indented rows in
  Categories screen, indented Home chips (`└ `), **Add subcategory** menu item.
- **B10 Home widget:** `widget/LinkVaultWidgetProvider` — static 5-row layout
  (`widget_linkvault.xml` + `@xml/widget_linkvault_info`), header opens app,
  rows open URL in browser; `updateAll()` called from `HomeViewModel.refresh`
  and `SaveLinkViewModel` save; manifest receiver registered.

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
