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
