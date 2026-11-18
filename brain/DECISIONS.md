# Decision log (ADR style)

Status values: **PROPOSED** (recommended, awaiting approval) · **APPROVED**
(locked) · **REJECTED** · **SUPERSEDED**.
Promote PROPOSED → APPROVED only on explicit product-owner confirmation.

## D-01 — Language and UI framework

- **Status:** **APPROVED** (2026-11-16, product owner)
- **Decision:** **Java + XML layouts** (Material Components theme),
  Fragments + Navigation component, MVVM (ViewModel + LiveData).
- **Why:** Owner knows Java, not Kotlin — choosing the familiar stack beats
  theoretical ergonomics. All required functionality is fully supported in
  Java. Rejected: Kotlin + Jetpack Compose (owner's explicit choice
  against), Kotlin + XML.
- **Communication preference (owner):** explain Android-specific concepts
  in simple Hinglish when discussing them.

## D-02 — Minimum / target SDK

- **Status:** **APPROVED** (2026-11-16, product owner)
- **Decision:** `minSdk 26` (Android 8.0), `targetSdk 35`.
- **Why:** API 26 gives `java.time` built in (no desugaring), notification
  channels; SAF `ACTION_OPEN_DOCUMENT_TREE` +
  `takePersistableUriPermission` work on all supported versions; API 26+
  covers virtually all active devices. targetSdk 35 = current Play
  requirement baseline.

## D-03 — Storage model

- **Status:** APPROVED (mandated by spec §3, not a free choice)
- **Decision:** JSON files in a user-selected SAF document tree; no
  database of any kind; rebuildable in-memory/index cache allowed but never
  a second source of truth.

## D-04 — Dependency injection

- **Status:** PROPOSED (2026-11-16)
- **Decision:** Manual DI via an `AppContainer` created in the `Application`
  class. No Hilt/Dagger.
- **Why:** App has a handful of services; Hilt adds KSP/compiler complexity
  for little benefit. "Avoid unnecessary dependencies."

## D-05 — JSON serialization (superseded by D-01 Java choice)

- **Status:** PROPOSED (2026-11-16, revised after D-01)
- **Decision:** `org.json` (bundled in Android, **zero dependencies**) with
  hand-written parse/serialize per schema in `data/json`. Unknown keys are
  ignored on read (forward compat); missing optional keys default safely.
- **Why:** Works natively with Java, no extra library. Rejected:
  kotlinx.serialization (Kotlin-only), Gson/Moshi (possible later if
  org.json boilerplate becomes painful — add only with approval).

## D-06 — Category identity vs folder naming (spec open decision 8)

- **Status:** PROPOSED (2026-11-16)
- **Decision:** Stable category ID (UUID) is identity; filesystem folder is
  a **slug of the ID** (e.g. `cat_<id>`), NOT the display name. Display name
  lives only in `categories.json`. Renaming a category touches exactly one
  file.
- **Why:** Display names can contain characters illegal/unsafe on some
  providers, collide, and cause path churn; ID-based folders make rename
  cheap and path-traversal impossible.

## D-07 — Duplicate link detection (spec open decision 4)

- **Status:** PROPOSED (2026-11-16)
- **Decision:** Detect duplicates using a **normalized URL** (lowercase
  scheme/host, drop `utm_*`/`fbclid`/`gclid`/`ref` params, strip trailing
  slash, keep query order otherwise). On match: warn in the review screen
  with "already saved" + link to existing record; never block saving. Stored
  URL is the original, unmodified.
- **Why:** Exact-match misses the same resource shared twice by different
  apps; asking on every save is friction. Warn-but-allow respects user
  judgment.

## D-08 — Remote page title/metadata fetch (spec open decision 5)

- **Status:** **APPROVED** (2026-11-16, product owner chose "tags only")
- **Decision:** **Not in MVP.** Title comes from share intent extras or user
  input; never fetched from network. Revisit post-MVP as an explicit,
  optional user action that never blocks offline saving.

## D-09 — Tags / favourites / pinned (spec open decision 3)

- **Status:** **APPROVED — IN MVP** (2026-11-16, product owner)
- **Decision:** MVP includes: `tags` (string array, editable chip input on
  the link detail/edit screen) and `pinned`/favourite toggle (pinned items
  sort first in lists). Both stored in the link record schema (see
  `DATA_SCHEMA.md` §3). Files get tags too (same field) — cheap and
  consistent; favourites UI is link-only.

## D-10 — File import limits (spec open decision 6)

- **Status:** **APPROVED** (2026-11-16, product owner)
- **Decision:** MVP imports any MIME type the system picker returns; hard
  cap **100 MB per file** (clear error above), soft warning above 20 MB.
  Size check before copy + free-space check on the target tree.
- **Why:** Bounded memory/duration for copies; avoids running the device
  out of space mid-import. Cap is a constant, easily changed later.

## D-11 — Restore conflict policy (spec open decision 7)

- **Status:** PROPOSED (2026-11-16)
- **Decision:** MVP restores the **whole collection only**. Conflict
  options shown in the preview: **Skip** (default) · **Keep both** (new IDs,
  collision-safe filenames) · **Replace** (per-item explicit confirmation).
  Never silent overwrite. Selective/category-only restore = post-MVP.

## D-12 — Background work (process-death survival)

- **Status:** PROPOSED (2026-11-16)
- **Decision:** `androidx.work:work-runtime` (WorkManager) for file-copy
  import and ZIP backup jobs; foreground progress for large jobs; startup
  cleanup/recovery scan for orphaned temp files.
- **Why:** Spec requires recovery from process death during save/copy/
  backup/restore. WorkManager is the only AndroidX-standard answer; one
  justified dependency.

## D-13 — List loading / search index

- **Status:** PROPOSED (2026-11-16)
- **Decision:** Manual incremental loading (limit/offset) over a rebuildable
  `index.json` cache built by scanning record files. No Paging3 library in
  MVP; JSON remains authoritative; full rebuild command in Settings.
- **Why:** Paging3 adds complexity; an index-backed windowed loader is
  enough for thousands of records and stays dependency-light.

## D-17 — On-device AI runtime (offline link analysis)

- **Status:** PROPOSED (2026-11-18)
- **Decision:** LiteRT-LM `0.19.1` (Maven, Kotlin API, CPU backend) as the
  optional LLM runtime; one Kotlin interop file (`LocalAiEngine.kt`), app
  core stays Java + XML (D-01 intact). Model = user-pushed **`.litertlm`**
  in app-internal `filesDir/ai_model.litertlm` (never GGUF — LiteRT-LM does
  not accept it; never in the SAF vault folder). Recommended model:
  `litert-community/Qwen3-0.6B`. Pipeline is rule-based first with graceful
  rule-only fallback when no model is present; single sequential WorkManager
  job, persistent queue, protection rules (fill-if-null summary, ungrouped-
  only category, additive tags, no `updatedAt` bump).
- **Why:** spec names LiteRT-LM as preferred runtime and permits Kotlin
  interop for it; official artifact avoids NDK/CMake native builds; official
  0.6 B model fits the 0.5–1.5 B band; CPU-only keeps battery/manifest simple.
  llama.cpp JNI was the alternative (GGUF direct) but needs a native toolchain
  build + JNI wrapper with no functional gain for this use case.

## D-18 — Share-to-save skips the review screen (owner directive)

- **Status:** APPROVED (2026-11-18)
- **Decision:** ACTION_SEND **URL** shares persist immediately — no review
  dialog (supersedes FR-01's mandatory review for this entry point). Save
  path: AutoCat rule → `cat_ungrouped`, then enqueue the AI pipeline which
  fills folder (create-if-missing), 2-line summary, and tags automatically.
  Toast confirms ("Link saved — AI …"). File shares, manual in-app add,
  clipboard offer, and edit mode keep the review screen unchanged.
- **Why:** owner: "share karne per dialog open na ho, direct save ho jaye …
  AI khud folder/summary/tag kar de, user ko kuch karna na pade."
- **Also:** list rows show the AI summary under every link (max 2 lines);
  Settings gains an "On-Device AI" status card (model present, queue
  pending/failed, worker active).

## D-19 — Auto-clean pass (AI maintains the vault, never deletes links)

- **Status:** APPROVED (2026-11-18)
- **Decision:** a daily WorkManager job (`auto_clean_periodic`, App.onCreate
  schedule) runs `AutoCleanWorker` + pure `AutoCleanPlanner`:
  1. **Folders:** removes non-system categories that hold **zero links and
     zero files**, have no child category, are **not targeted by an
     Auto-Categorize rule**, and have been untouched for **≥30 days**
     (grace on `max(createdAt, updatedAt)`). Also sweeps **empty orphan
     `user/categories/*` directories** left by earlier manual deletes
     (non-empty dirs are never touched).
  2. **Tags:** prunes redundant tags *on link records* — tag equal to the
     record's folder name, singular/plural pairs on the same record
     ("video"/"videos"; conservative: only words >4 chars ending in "s"),
     blank entries. Written via `RecordRepository.saveTags()` (no
     `updatedAt` bump — housekeeping must not re-sort the browse list).
  3. **Hard invariant: link and file records are never deleted or
     rewritten** (the planner cannot even express record deletion).
  Side effects on change: index invalidate + `AiUpdateNotifier.fire(null)`.
  Stats land in `lv_prefs` (`auto_clean_last_run` / `_last_stats`) and
  render in the Settings AI card ("Auto-clean: X folder(s), Y tag(s)").
- **Why:** owner: "AI khud app clean kare — unused tags/folder agar bahut
  time se use nahi hua ho, **link delete na kare**." Deterministic rules
  (not LLM) because the decisions are structural, cheap, and must be
  provably safe; LLM stays in the enrichment path only (D-17).

## D-20 — AI Sidebar: system-wide writing assistant (spec A §14)

- **Status:** **APPROVED** (2026-11-18, product-owner spec directive)
- **Decision:** one system-wide overlay window (pill ⇄ panel) hosted by a
  `FOREGROUND_SERVICE_SPECIAL_USE` service, driven by the **existing**
  Qwen3-0.6B/LiteRT-LM engine through the **same** `InferenceQueue` as
  link-AI (single daemon FIFO, exactly-once, pending-cancel / in-flight
  discard — LiteRT-LM has no cancel API). 10 tones, 5 actions
  (Rewrite/Improve/Shorten/Expand/Translate), 10-entry local history,
  6 settings in `sidebar_prefs`, clipboard read only on expand/Paste.
  Start only from the Settings toggle or `MainActivity.onCreate`
  (foreground); no boot receiver, no Accessibility service, no second
  model, no cloud, no network.
- **Permissions:** `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_SPECIAL_USE` (+ `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`),
  `VIBRATE`; deliberately **no** `POST_NOTIFICATIONS` (FGS notif exempt).
- **Settings placement:** main Settings card = Enable + overlay-permission
  status only; the other 5 settings live in the panel's Settings view
  (spec §6).
- **Why:** owner spec handed the full feature (pill everywhere, reuse the
  local engine, background processing so long generations survive app
  switch). Shared queue keeps one-model/one-runtime rule and prevents two
  concurrent generations; single-window design keeps history/settings/result
  alive across collapse; special-use FGS + foreground-only start are the
  documented answer to Android 12/14 service restrictions (spec §7).
- **Full detail + device checklist:** `brain/AI_SIDEBAR.md`.

## Spec §13 open-decision coverage

| Spec decision | ADR | Status |
|---|---|---|
| 1. Framework/language | D-01 | APPROVED (Java + XML) |
| 2. Min Android API | D-02 | APPROVED (26/35) |
| 3. Tags/favourites in MVP | D-09 | APPROVED (in MVP) |
| 4. Duplicate detection | D-07 | PROPOSED (normalized-URL warn) |
| 5. Metadata fetch | D-08 | APPROVED (not in MVP) |
| 6. File size/type limits | D-10 | APPROVED (100 MB, any MIME) |
| 7. Restore conflicts | D-11 | PROPOSED (skip/keep-both/replace) |
| 8. Category folder naming | D-06 | PROPOSED (ID-slug folders) |

Remaining PROPOSED entries (D-04, D-05, D-06, D-07, D-11, D-12, D-13) are
non-blocking defaults; change any by replying — they are recorded here, not
guessed.
