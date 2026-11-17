# LinkVault — Requirements

Derived from `LINKVAULT_MASTER_GOAL.md` v1.0 (2026-11-16). That document is
authoritative; this file is the working, ID-tagged version. Update both when
scope changes.

## 1. Product summary

Personal, offline-first Android organizer for shared URLs and small files:
capture via Share Sheet → review/edit → categorize → persist as JSON files
in a user-selected SAF folder → search/manage → ZIP backup/restore.

## 2. Constraints and non-goals

- No login/signup/auth of any kind.
- No backend API, server, cloud sync, or automatic upload.
- No MongoDB, SQLite, Room, Realm, or any DB as primary record store.
- No mandatory internet for any core operation.
- No Chrome extension (Android app only).
- Never move/delete/modify the user's original files on import.
- No broad storage permissions (`READ/WRITE_EXTERNAL_STORAGE`) when SAF
  satisfies the need.
- No analytics/crash SDKs transmitting personal data without approval.
- A rebuildable index/cache is allowed; it must never become a second
  source of truth.

## 3. Functional requirements

### FR-01 — Android Share Intent
Intent filters for `ACTION_SEND` (`text/plain`, URL-compatible MIME) to
receive shared URLs; safe URL extraction from plain text with user review
before persisting; handle missing/malformed title/description and multiple
URLs; never silently save arbitrary text as a URL; guard against duplicate
saves from repeated intent delivery (policy: D-07 + review screen).

### FR-02 — Link records
Stable unique ID, URL, editable title, optional description, category
reference, source app label/package, optional image URL (never auto-
downloaded), createdAt/updatedAt, `schemaVersion`/`type`; **tags +
pinned/favourite are IN MVP** (D-09: chip editor, pin toggle, pinned-first
ordering); UI must render fine with a missing/invalid image URL.

### FR-03 — Categories and folders
Create/rename/delete categories; move links; show category contents;
non-empty delete prompts (move items to uncategorized vs cancel); path-
traversal-proof names; consistent category-metadata ↔ folder mapping
(ID-based, D-06); always-present uncategorized/default location.

### FR-04 — Search and browsing
Search title/URL/description/category name; sort newest/oldest/title; empty/
loading/error states; usable with large collections via rebuildable index
(D-13); JSON stays authoritative.

### FR-05 — Link actions
Open with a validated handler, copy to clipboard, edit metadata, move
between categories, delete with confirmation/undo, non-crashing handling
of invalid/unsupported URLs.

### FR-06 — File management
System picker selection; copy-only import (original untouched); JSON
metadata (id, display name, relative path, MIME, size, timestamps);
collision-safe filenames; resolved-path containment inside storage root;
progress for large copies; handle cancellation, insufficient space,
inaccessible URIs, interrupted copies; success reported only after copy +
metadata both commit.

### FR-07 — User-selected storage
`ACTION_OPEN_DOCUMENT_TREE` selection; persisted URI permissions; tree URI
+ state in preferences (never a hard-coded path); accessibility check at
startup and before storage ops; clear reselect/recovery flow on revocation;
document Android-version limitations honestly.

### FR-08 — JSON storage
Versioned schema for links, categories, files (see `DATA_SCHEMA.md`); one
JSON file per record; safe write (temp → validate → replace); malformed
JSON is reported, never silently discarded; schema versioning + migration;
stable IDs as identity; sanitized filenames, titles never used as paths.

## 4. Backup & restore requirements (spec §7)

- ZIP export includes categories, link records, file metadata, file bytes;
  manifest with format version, creation time, item counts; archive verified
  readable before success; omissions reported; destination chosen via system
  picker.
- Restore validates structure/manifest/schema/sizes/paths first; rejects
  `../` traversal entries; previews counts + conflicts; never silently
  overwrites (skip / keep both / replace-after-confirm, D-11); stages before
  commit; failure preserves the existing collection; future format migration
  supported.

## 5. Non-functional requirements

- **Offline:** every core operation works with no network.
- **Privacy:** no transmission of URLs/notes/filenames/contents; no
  private-URL logging.
- **Robustness:** treat imported JSON/ZIP as untrusted input; resist path
  traversal, malicious entries, oversized files, malformed JSON, duplicate
  IDs; recover from process death in save/copy/backup/restore; never report
  success before persistence is verified.
- **Performance:** responsive with thousands of records; no loading all
  records/thumbnails at once; pagination/incremental loading; no automatic
  thumbnail downloads.
- **UX:** few-tap save flow, easy category selection, clear success/failure
  feedback, empty states, first-run guidance, storage status in Settings,
  understandable permission recovery, accessible sizes/contrast.

## 6. Acceptance criteria checklist (spec §12)

- [ ] SAF folder selection works
- [ ] Folder access survives restart while permission granted
- [ ] Revoked/missing folder detected + recovery offered
- [ ] Share a URL from another app into LinkVault
- [ ] Review/edit title, note, category before saving
- [ ] Each link persisted as a versioned JSON record
- [ ] Categories: create, rename, safe delete
- [ ] Links: search, open, copy, edit, move, delete
- [ ] File import by copy; original unchanged
- [ ] File metadata persisted as JSON
- [ ] Core features work without internet
- [ ] ZIP backup includes records, metadata, copied files
- [ ] Restore validates archive; no silent overwrite
- [ ] Interrupted operations do not corrupt the collection
- [ ] Malformed data/storage failures handled without crashing
- [ ] Large collections stay usable and responsive
- [ ] No backend/account/cloud/database added without approval

Status of all items: **not started** (tracked in `brain/STATUS.md`).

## 7. Open decisions

Spec §13 decisions are tracked as ADRs in `brain/DECISIONS.md`:
**approved** — D-01 (Java + XML), D-02 (26/35), D-08 (no metadata fetch),
D-09 (tags/pinned in MVP), D-10 (100 MB cap); **proposed defaults pending
no objection** — D-06 (ID-slug folders), D-07 (normalized-URL duplicate
warning), D-11 (skip/keep-both/replace restore conflicts).
