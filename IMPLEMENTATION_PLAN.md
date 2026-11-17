# LinkVault — Implementation Plan

Phases follow spec §11. **Each phase must pass its exit criteria (verified
by the checks listed) before the next begins.** Status is mirrored in
`brain/STATUS.md`.

## Phase 0 — Requirements & technical validation ✅ (docs complete, approval pending)

Tasks:
- [x] Read spec; analyze project directory (empty → from-scratch init)
- [x] Propose stack + architecture (`ARCHITECTURE.md`)
- [x] Finalize v1 schemas (`DATA_SCHEMA.md`)
- [x] Propose duplicate/category/backup conflict policies (D-06, D-07, D-11)
- [x] Risk register (below)
- [ ] **Product-owner approval of PROPOSED decisions** (D-01, D-02 gate the rest)

**Exit criteria:** decisions documented; no unresolved storage/permission
assumption. → met except approval.

## Phase 1 — Foundation & local JSON storage

Tasks:
1. Init Android project: Gradle (Groovy DSL), single `app` module, Java
   source, XML layouts + Material Components (Material 3 theme), minSdk 26 /
   targetSdk 35, `git init` (recommended), baseline lint.
2. App skeleton: `App.java` + `AppContainer`, single Activity with
   NavigationHost fragment, theme, base layouts.
3. Onboarding: `ACTION_OPEN_DOCUMENT_TREE` picker → persist tree URI +
   `takePersistableUriPermission` (SharedPreferences, off-main-thread) →
   storage-status indicator.
4. `StoragePermissionManager`: startup revalidation, recovery screen,
   reselect flow, release old grant on folder change.
5. `DocumentTreeClient` + `SafeWriteEngine` (capability probe, tmp→validate
   →replace, startup temp sweep).
6. JSON codecs (org.json) + schema validation, error type for malformed
   records.
7. `CategoryRepository` (categories.json CRUD, uncategorized invariant,
   ID-slug folders, safe delete policy).
8. `RecordRepository` CRUD + `IssuesReport` for malformed files.
9. Unit tests for codecs, sanitizer, safe-write fallback logic.

**Exit criteria (verified):** select folder; create/read/update/delete test
JSON records; survive process restart; re-grant flow works after revoked
permission (emulator check).

## Phase 2 — Link capture & organization

1. Share entry: intent filters + thin activity (`singleTop`), payload parsing
   (`ShareIntentParser`), URL validation/extraction, source attribution.
2. Save/review screen: editable title, URL, note, category picker, duplicate
   warning (D-07), ≥2-tap save path.
3. Manual save (paste/type URL) reusing the same flow.
4. Link actions: open (validated handler), copy, edit, move, delete
   (confirm + snackbar undo where practical).
5. **Tags + pinned/favourite (D-09):** chip editor on link edit screen,
   pin toggle, pinned-first list ordering, tag filter entry point.
6. Category screens: list/create/rename/delete (+ non-empty prompt),
   uncategorized handling.
7. Home/browse list with categories.
8. Unit tests: URL extraction/normalization, duplicate policy; UI tests
   (Espresso) for the save flow.

**Exit criteria (verified):** share from a second app → review → save →
JSON in tree → survives restart → edit → move → delete.

## Phase 3 — Search & file support

1. Search (title/URL/description/category/tag) + sort (newest/oldest/
   title, pinned-first); empty/loading/error states.
2. `IndexRepository`: build/incremental update/full rebuild + Settings
   action; windowed list loading (D-13).
3. File import: `ACTION_OPEN_DOCUMENT` → size cap (D-10) + free-space check
   → `FileCopyWorker` (WorkManager) with progress/cancel → collision-safe
   name → size-verified → metadata record write → index update.
4. File actions: open/copy-name/move/delete (bytes + record together;
   orphan sweep on startup).
5. Perf pass with 5,000 synthetic records; measure search/list.

**Exit criteria (verified):** links + files findable/manageable with a
realistic collection; interrupted copy leaves no corrupt record; original
files untouched (verified by checksum in test).

## Phase 4 — Backup & restore

1. `BackupEngine`: ZIP streaming export + manifest + read-back verification
   + omission report; destination via `CreateDocument`.
2. `RestoreEngine` pass 1 validation (manifest/format/paths/`../`/size
   caps), pass 2 preview (counts, conflicts).
3. Conflict UI (D-11: skip / keep-both / replace-with-confirm) + staging
   commit + rollback on failure; index rebuild after restore.
4. Tests: round-trip backup→clean-restore, malicious ZIP fixtures
   (traversal, zip-bomb caps, bad manifest), mid-restore kill simulation.

**Exit criteria (verified):** backup → restore on clean install passes;
restore never silently overwrites; failure preserves existing data.

## Phase 5 — Hardening & release

1. Adversarial matrix: malformed URLs/JSON/ZIPs, revoked permissions, slow
   providers, low storage, process death in every job, duplicate records,
   airplane-mode full pass.
2. Scale tests (5k+ records, 100 MB imports), memory profiling.
3. Accessibility (text scaling ≥200%, 48dp targets, contrast, semantics),
   first-run guidance, Settings storage status polish.
4. Release build (R8, signing), privacy statement (no data leaves device),
   user documentation, README finalization.
5. Full acceptance-criteria walkthrough (`REQUIREMENTS.md` §6).

**Exit criteria (verified):** all acceptance criteria pass; no open
data-loss issue; known limitations documented.

## Risk register

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | SAF providers lack rename/atomic ops; behavior varies | Data corruption / failed writes | Capability probe + layered fallbacks in `SafeWriteEngine`; tmp→verify always; provider matrix tested (internal storage, SD, Drive) |
| R2 | Non-atomic replace on slow providers → partial record | Corrupt JSON | Write-verify-then-swap; malformed record surfaced in Issues, never deleted |
| R3 | Process death mid-copy/backup/restore | Orphan bytes, half state | WorkManager idempotence, staging-then-commit, startup temp/orphan sweep |
| R4 | Revoked/expired tree permission (folder deleted, OEM quirks) | Data appears "lost" | Startup revalidation, persistent grants, recovery UI; collection never touched |
| R5 | Duplicate intent delivery / double-tap | Duplicate records | singleTop launch, intent-window dedupe, D-07 normalize check, single-flight save |
| R6 | Malicious ZIP (traversal, zip bombs, huge counts) | Path escape / OOM | Pass-1 validation, path containment, size/count caps, staging sandbox |
| R7 | Index drifts from JSON (source of truth) | Wrong search results | Index is derived; rebuild command; mutation order = record first, index second |
| R8 | Large collections degrade UX | Unusable at scale | Windowed queries, index-only fields, perf gate at 5k records |
| R9 | Slow/OOM on large file imports | ANR/crash | Streaming copy, 100 MB cap, WorkManager + progress, free-space precheck |
| R10 | Provider kills read grants over time (Drive offline) | Backup failures | Pre-flight probe, clear error + retry, offline-provider messaging |
| R11 | Category rename/move mismatches metadata ↔ folders | Lost records | ID-slug folders (D-06), sync pass moves orphans to uncategorized |
| R12 | Scope creep (DB "just for search", backend "just for titles") | Constraint violation | `brain/DECISIONS.md` + AI rules: changes require explicit approval |

## Verification discipline

Every phase ends with: (a) automated tests for new logic, (b) a manual
checklist run against the exit criteria, (c) updated docs + `brain/`
files. No phase is "done" on claims alone.
