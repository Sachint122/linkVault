# LinkVault — Architecture

Status: **Stack approved (D-01: Java + XML, D-02: 26/35); architecture
documented; implementation not started.** Decision log: `brain/DECISIONS.md`.

## 1. Recommended technology stack

| Concern | Choice | Justification |
|---|---|---|
| Language | **Java** (Java 8+ level, lambdas) | Owner-approved (D-01); fully capable for all requirements |
| UI | **XML layouts** + Material Components (Material 3 theme), Fragments, `androidx.navigation` | Owner-approved (D-01); one Activity hosts the NavHost fragment |
| Architecture | MVVM: Fragment (view) → ViewModel (Java) → repository layer; clean separation ui / domain / data | Testable, lifecycle-aware, standard Android pattern |
| Async | `java.util.concurrent.ExecutorService` for background work + `LiveData`/`Handler(main)` for UI updates | Java-native structured async; WorkManager for surviving jobs |
| JSON | `org.json` (platform-bundled, zero deps) with hand-written per-schema codecs (D-05) | Java-native, unknown keys ignored on read |
| Storage | SAF document tree + JSON files only | Mandated (D-03) |
| Background jobs | `androidx.work:work-runtime` (WorkManager, Java API) | Survives process death for copy/backup (D-12) |
| Prefs | `SharedPreferences` (URI string + grant state), written off-main-thread | Simple, Java-native, sufficient for small state |
| DI | Manual `AppContainer` in `Application` (D-04) | No Hilt/KSP machinery |
| Min/target SDK | minSdk 26 / targetSdk 35 (D-02) | Built-in `java.time`, modern SAF behavior |
| Testing | JUnit4, androidx.test + Espresso, Robolectric (optional), emulator SAF tests | See `TESTING_STRATEGY.md` |

Deliberately excluded: Kotlin/Compose (owner choice), Hilt, Room/DB,
Retrofit/OkHttp (no network in MVP), thumbnail loaders (spec forbids
auto-download), Paging3 (MVP uses windowed loading), Gson/Moshi unless
org.json pain appears (needs approval), analytics/crash SDKs.

## 2. Module and package structure

Single Gradle module (`app`, Groovy DSL or Kotlin DSL — Kotlin DSL works
fine with Java source) with strict package layering (multi-module split is
premature):

```text
app/src/main/java/<pkg>/
├── App.java / di/AppContainer.java     Application + manual DI
├── ui/                                 Fragments, ViewModels, adapters
│   ├── home/  save/  categories/  search/  files/  settings/  onboarding/
├── domain/                             Pure Java: models, policies, use cases
│   ├── model/  policy/ (duplicate, conflict, path sanitize, url normalize)
├── data/
│   ├── saf/             DocumentTreeClient, SafeWriteEngine, PermissionManager
│   ├── json/            Codecs (org.json), schema validation, migrations
│   ├── repo/            RecordRepository, CategoryRepository, FileRepository, IndexRepository
│   ├── share/           ShareIntentParser, dedupe guard
│   └── backup/          BackupEngine, RestoreEngine, manifest
└── work/               FileCopyWorker, BackupWorker, startup recovery
```

Layer rules: `ui` (Fragments/ViewModels) talks to repositories through
interfaces; `domain` has zero Android imports; `data` is the only layer
touching `ContentResolver`/`DocumentsContract`; nothing outside `data/json`
parses JSON files directly.

## 3. Component map

| Component | Responsibility |
|---|---|
| `DocumentTreeClient` | Wraps tree URI: find/create children, read/write/delete streams, capability flags (`canWrite`, `canDelete`, rename support), document-ID cache |
| `SafeWriteEngine` | temp → validate → replace/verify write protocol with provider fallbacks (§5) |
| `RecordRepository` | CRUD for link/file records as individual JSON files |
| `CategoryRepository` | `categories.json` CRUD, uncategorized invariant, non-empty delete policy |
| `IndexRepository` | Rebuildable `index.json` cache; incremental sync after mutations; full rebuild |
| `ShareIntentParser` | Extract/validate/normalize URLs from `ACTION_SEND` payloads |
| `FileImportService` | Picker → size/space checks → streamed copy to `files/` → metadata commit (WorkManager) |
| `BackupEngine` / `RestoreEngine` | ZIP export with manifest; validate → preview → stage → commit |
| `StoragePermissionManager` | Persist/revalidate/release tree URI permission; recovery state machine |
| `PathSanitizer` | Filename whitelist, ID-based names, containment checks (no `..`, no absolute) |

## 4. Data flow (share → save)

```text
Source app → ACTION_SEND → host Activity (`singleTop`) → SaveLink fragment
  → ShareIntentParser.extract()  (URL candidates + title/source label)
  → Review screen (user edits title/note/category; duplicate warning via D-07)
  → RecordRepository.create()  [SafeWriteEngine: tmp → parse-validate → replace → verify]
  → IndexRepository.onRecordCreated()
  → success toast / error with retry (never success before verified write)
```

## 5. SAF strategy (critical constraint)

Facts driving the design: a document-tree URI is **not** a filesystem path;
providers may not support rename, atomic replace, or fast child queries.

- **Selection:** `Intent.ACTION_OPEN_DOCUMENT_TREE` (persistable read+write
  flags) → `takePersistableUriPermission` → store `treeUri` string +
  grant timestamps in SharedPreferences (written off-main-thread). Never
  store a raw path.
- **Revalidation:** at app start and before every storage batch:
  confirm the grant is still in `ContentResolver.persistedUriPermissions`;
  probe with a lightweight document query. Missing/revoked → Settings
  recovery screen (re-pick or re-grant), collection untouched.
- **Operations:** all via `ContentResolver` streams +
  `DocumentsContract` (create/rename/delete child), with a per-provider
  capability probe cached in memory; unsupported operations fall back:
  - *replace*: write tmp → copy bytes onto target → delete tmp (if rename
    unsupported), or rename-swap where supported.
  - *delete*: `deleteDocument`; if unsupported → clear content + report.
- **Reads:** `openInputStream` per file; never list the whole tree on the
  main thread; document-ID cache invalidated on mutation.
- **No broad permissions:** SAF only; no `MANAGE_EXTERNAL_STORAGE`.
- **Known limitations to surface honestly in Settings/docs:** performance
  varies by provider (Drive/SD card/slow providers), renaming may be
  unsupported, deletion of the folder by the user auto-revokes access, and
  permission must be re-granted manually afterwards.

## 6. Share Intent strategy

- Dedicated thin activity (or the single host Activity with
  `singleTop`) with filters: `ACTION_SEND`, MIME `text/plain` (+ `text/url-list`,
  `application/url` where meaningful), categories `DEFAULT` + `BROWSABLE`;
  label "Save to LinkVault". `launchMode="singleTop"` to collapse
  re-deliveries.
- Payload handling: prefer `EXTRA_TEXT`/`EXTRA_TITLE`/`EXTRA_SUBJECT`;
  extract URL candidates with `Patterns.WEB_URL` + `URI` parse; if 0 valid
  URLs → friendly "no link found" state; if >1 → chooser to pick one (or
  save sequentially with review each); malformed → review screen with the
  raw text editable, never auto-persist.
- Source attribution via `EXTRA_REFERRER`/package label when available.
- Duplicate guard: (a) review screen is the primary human gate; (b) intent
  delivery dedupe — same normalized-URL intent within a short window maps
  to the existing review instance; (c) saved-record check per D-07 shows
  "already saved" warning.
- Launch UI: compact bottom-sheet-like review screen (title, URL, note,
  category picker, Save) reachable in ≤2 taps after the share sheet.

## 7. Backup & restore strategy

**Backup:** `CreateDocument("application/zip")` picker for destination →
stream `ZipOutputStream` directly from SAF (constant memory) → entries:
`manifest.json` first, then `user/categories/categories.json`, all
`link_*.json`/`file_*.json`, all copied file bytes; report omissions
(unreadable items) explicitly → re-open archive (`ZipFile`) and verify
entry count + manifest parse before "Backup complete". Temp file +
rename/copy on providers without atomicity; low-space check first.

**Restore:** `OpenDocument` picker → **pass 1 validate** (zip readable,
manifest present + `formatVersion` supported, entry count vs manifest,
reject absolute paths and `../`, total uncompressed size cap vs free
space) → **pass 2 preview** (counts of new/conflicting/invalid items) →
user picks conflict policy (D-11: skip/keep-both/replace-confirmed) →
**stage** into `LinkVault/.staging/<uuid>/`, parse-validate every record →
**commit** into the live tree (new IDs for keep-both), index rebuild →
cleanup staging. Any failure: live collection untouched, staging deleted,
human-readable error. Never restore onto itself without confirmation.

## 8. Security considerations

- URL validation before `ACTION_VIEW`: allow `http/https` only (default),
  resolve an activity first, no implicit-intent crashes; reject
  `javascript:`, `file:`, `intent:` schemes.
- Untrusted input: JSON size cap per record (~1 MB), ZIP entry count /
  per-entry / total size caps, path normalization + containment check for
  every archive entry, duplicate-ID policy (reject or re-ID per conflict
  setting).
- `PathSanitizer` for all generated filenames: `[a-zA-Z0-9._-]`, length
  cap, ID-based; titles/names never become paths.
- Safe write: tmp → validate → replace → verify; startup scan deletes only
  app-owned `*.tmp` leftovers (never user data).
- No logging of URLs, notes, or filenames (release builds strip; no
  third-party SDKs).
- No exported attack surface beyond the share activity (explicit filters,
  no content providers exposing data).

## 9. Performance considerations

- Never load the full collection: index-backed windowed queries
  (limit/offset), manual pagination (D-13).
- `index.json` rebuilt from JSON on demand; incremental update after each
  mutation; full rebuild surfaced in Settings as recovery tool.
- Streaming I/O for copies/ZIP; buffers ~64–128 KB; progress via
  WorkManager + notification.
- Per-record files mean ~N file reads to rebuild index — acceptable at
  thousands; if slow, index stores only search fields (id, title, url
  excerpt, category, timestamps), not full records.
- No thumbnails downloaded; optional local thumbnails out of MVP scope.
- Benchmark gate: 5,000-record collection search/list must stay
  interactive (see `TESTING_STRATEGY.md`).

## 10. Error handling & recovery model

- Every storage op returns `Result`/sealed success-failure; UI maps to
  states (loading/empty/error+retry).
- Corrupted record: file left in place, surfaced in an "Issues" report —
  never silently skipped or deleted (spec §5).
- Process death: WorkManager jobs are idempotent (tmp names + startup
  recovery sweep); restore commits only after staging validation so a kill
  leaves either old or new state, never half-mixed.
- Permission loss: non-blocking banner + Settings recovery flow; read-only
  degrade where possible.
