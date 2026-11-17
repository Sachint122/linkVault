# LinkVault — Testing Strategy

Principle: **no claim of done without code + tests + a manual checklist
run** (spec §5 Phase exit criteria). Data-safety paths get the deepest
coverage.

## 1. Test layers

| Layer | Tooling | What it covers |
|---|---|---|
| Unit (pure Java) | JUnit4 | URL normalize/extract, duplicate policy, PathSanitizer, schema validation, migrations, conflict policies, manifest parse, ZIP entry path validation |
| Repository/integration | JUnit4 + fake `DocumentTreeClient` (in-memory tree with configurable provider quirks: no-rename, slow, failing writes) | SafeWriteEngine fallbacks, CRUD round-trips, index rebuild, malformed-record reporting, temp sweep |
| Robolectric (optional) | Robolectric | Intent parsing, SharedPreferences wiring, ContentResolver edge cases |
| Instrumented (emulator/device) | androidx.test + Espresso, UiAutomator | Real SAF flows: folder pick, persistable grants, restart survival, revoke + recovery, share-sheet launch, file import copy |
| End-to-end / chaos | instrumented + scripted kills | Process death mid-copy/backup/restore, provider failure injection, low-storage simulation |
| Performance | benchmark-style instrumentation + synthetic data | 5k-record index build, list/search latency, 100 MB import memory |
| Manual QA | checklist per phase (below) | UX states, accessibility, real apps sharing (Instagram/YouTube/browsers) |

No mocking frameworks beyond hand-written fakes unless a real need appears
(keep dependency surface minimal).

## 2. Critical test matrix (must pass before release)

### Storage & SAF
- Pick folder → write record → kill app → restart → record readable.
- Revoke permission (adb or delete folder) → app detects, shows recovery,
  never crashes, never touches other data.
- Provider without rename support → SafeWriteEngine fallback path verified.
- Torn write (inject failure mid-write) → target intact or tmp-only;
  startup sweep cleans; Issues report accurate.
- Malformed JSON present → reported in Issues, app fully functional,
  file not deleted.
- Path traversal attempts in record fields (`../../evil`) → sanitized/
  rejected; containment check enforced on every relative path.

### Share intent
- `ACTION_SEND text/plain` with bare URL, with title, without title,
  with multiple URLs, with zero URLs, with malformed URL, with
  `javascript:` scheme → each lands in correct state; arbitrary text never
  auto-saved.
- Rapid double-share of same URL → one review instance / duplicate warning,
  not two silent records.
- Share from ≥3 real apps (browser, YouTube, messaging) on device.

### Links & categories
- CRUD round-trip preserves schema fields and timestamps (updatedAt bumps).
- Category rename touches only `categories.json`; folder name unchanged.
- Non-empty category delete → move-to-uncategorized vs cancel both verified.
- Delete/move/open/copy URL (incl. invalid URL → non-crash).

### Files
- Import → checksum of copy equals checksum of original; original mtime/
  content untouched.
- Collision: two imports of same display name → distinct physical files.
- Cancel mid-copy / kill mid-copy → no record without bytes; orphan sweep.
- Over-cap file → clear rejection before any write.
- Insufficient-space injection → clean error, no partial record.

### Backup / restore
- Round trip: N links + categories + files → backup → clean install →
  restore → counts and checksums identical.
- Restore with existing conflicts under each policy (skip/keep-both/
  replace-confirm) → exactly the chosen semantics; default never overwrites.
- Malicious fixtures: entry `../evil.json`, absolute path entry, manifest
  count mismatch, unsupported `formatVersion`, oversized total size → all
  rejected pre-write, collection untouched.
- Kill during staging and during commit → collection is either old or new
  state, never mixed; staging cleaned.

### Offline / privacy
- Full airplane-mode pass of every core flow.
- Assert no network permissions/hosts in release manifest (test + manifest
  review); no URL/note strings in logcat during flows.

### Scale
- 5,000 links: cold index build < 5 s on mid-range device, list scroll
  smooth, search < 500 ms after index warm; memory stable (no full-collection
  bitmap/JSON loads).

## 3. Per-phase verification gates

- **P1:** unit tests for codecs/sanitizer/safe-write + instrumented
  folder-pick & restart test. Exit-criteria checklist run.
- **P2:** URL/duplicate unit tests + Espresso save-flow test + tags/pin
  unit tests + real-app manual share test.
- **P3:** search/index unit tests + import instrumented tests + perf
  spot-check with synthetic 5k data.
- **P4:** full backup/restore round-trip automated + malicious-ZIP suite.
- **P5:** adversarial matrix (§2) fully executed; acceptance criteria in
  `REQUIREMENTS.md` §6 checked one by one with evidence (test name or
  manual note).

## 4. Manual QA checklist (release candidate)

- [ ] First-run onboarding with zero knowledge of storage concepts
- [ ] Share → save ≤ 2 taps after share sheet
- [ ] All empty/loading/error states reviewed
- [ ] Settings shows folder + access status; recovery understandable
- [ ] 200% font scale + TalkBack pass on primary flows
- [ ] Backup → uninstall → reinstall → restore happy path on real device
- [ ] Airplane-mode full pass
- [ ] Low-storage warning path

## 5. Evidence rule

Phase sign-off requires: test results (names/counts), checklist results,
and updated `brain/STATUS.md`. "It works" without evidence is not a pass.
