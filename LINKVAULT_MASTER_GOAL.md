# LinkVault --- Master Goal & Product Specification

**Document status:** Production Ready - All Phases Completed & Live Device Verified\
**Version:** 1.0 (Cyberpunk Edition)\
**Date:** 17 November 2026\
**Product type:** Android-first, offline-first personal link and
small-file organizer (Zero-Cloud, Pure SAF JSON Storage)

------------------------------------------------------------------------

## 1. Product vision

Build **LinkVault**, a simple, reliable Android application that lets a
person capture links from almost any Android app, organize them into
folders/categories, save small files, and access everything locally
without needing an account, backend, database server, or continuous
internet connection.

LinkVault should behave like a personal, portable knowledge collection.
The user owns the data and chooses where it is stored.

The product's core value is:

> Save anything useful in a few taps, organize it immediately or later,
> and keep it accessible on the device.

## 2. Primary goals

1.  Receive shared URLs through Android's native Share Sheet from other
    applications.
2.  Let the user review and edit the link before saving it.
3.  Organize links into user-created categories/folders.
4.  Store records as JSON files in a user-selected local folder.
5.  Save selected small files by copying them into LinkVault storage
    while preserving originals.
6.  Search and browse saved links and files.
7.  Open, copy, edit, move, and delete saved records.
8.  Export a complete ZIP backup and restore from a ZIP safely.
9.  Work offline for all core operations.
10. Recover gracefully if the user-selected folder becomes unavailable
    or permission is revoked.

## 3. Non-goals and constraints

Do **not** introduce these unless the product owner explicitly changes
the requirements:

-   No login, signup, user account, or authentication.
-   No cloud sync or remote database.
-   No backend API or server dependency.
-   No MongoDB, SQLite, Realm, or other database as the primary record
    store.
-   No mandatory internet connection.
-   No Chrome extension; this brief is for the Android application.
-   No automatic upload of user content.
-   No moving or deleting the user's original files when importing them.

The user-selected local folder and JSON files are the source of truth.
Lightweight in-memory indexing or a rebuildable local cache may be
considered for performance, but it must never become a second source of
truth or require a database.

## 4. Target user journey

### 4.1 Save a link from another app

1.  The user finds a useful URL in an app such as Instagram, YouTube, a
    browser, or a messaging app.
2.  The user taps Share and selects LinkVault.
3.  Android launches LinkVault's save flow with the shared URL and any
    available title/text.
4.  LinkVault validates and previews the URL.
5.  The user can edit the title, add a note/description, choose a
    category, and review the source.
6.  The user taps Save.
7.  LinkVault writes the record to the selected storage folder and
    confirms success.

If an app shares plain text containing a URL rather than a URL MIME
type, LinkVault should attempt safe URL extraction and allow the user to
review the extracted result. Do not silently save arbitrary text as a
URL.

### 4.2 Save a link inside LinkVault

The user can also paste or type a URL from the app's own interface and
save it using the same validation and metadata flow.

### 4.3 Browse and manage

The user can open categories, view saved records, search by
title/URL/description/category, and open, copy, edit, move, or delete an
item.

### 4.4 Save a file

The user selects a file using Android's system file picker. LinkVault
copies it into its own selected storage folder, records its metadata,
and leaves the original untouched. The app must handle unsupported file
types, large files, duplicate names, insufficient space, and interrupted
copies.

## 5. Functional requirements

### FR-01: Android Share Intent

-   Register the appropriate Android intent filters to receive shared
    web URLs.
-   Support `ACTION_SEND` for URL and plain-text shares where
    appropriate.
-   Handle `text/plain` and URL-compatible shared content.
-   Handle missing title, missing description, malformed URL, and
    multiple URLs.
-   Do not assume every source app provides the same metadata.
-   Show a review screen before persisting data.
-   Avoid duplicate saves caused by repeated intent delivery; define a
    safe duplicate-detection policy.

### FR-02: Link records

Each link record should support:

-   Stable unique ID.
-   URL.
-   User-editable title.
-   Optional description/note.
-   Category ID or category reference.
-   Source application/package label when available.
-   Optional image/thumbnail URL.
-   Created timestamp and updated timestamp.
-   Record type/version for future migrations.
-   Optional tags or pinned/favourite state if included in the agreed
    MVP scope.

Do not download remote thumbnails by default. Store an image URL only
when available and useful. The UI must still work if the image URL is
absent, invalid, or unreachable.

### FR-03: Categories and folders

-   Create, rename, and delete categories.
-   Move links between categories.
-   Display category contents.
-   Define what happens when a non-empty category is deleted: prompt the
    user to move its items to an uncategorized/default category or
    cancel.
-   Prevent path traversal and unsafe folder names.
-   Keep category metadata and filesystem folder names consistently
    mapped.
-   Support an uncategorized/default location.

### FR-04: Search and browsing

-   Search title, URL, description, and category name.
-   Sort by newest, oldest, and title.
-   Provide empty, loading, and error states.
-   Search must remain usable with a large collection.
-   Use a rebuildable index/cache if necessary; JSON files remain
    authoritative.

### FR-05: Link actions

-   Open URL using an Android-supported handler.
-   Copy URL to clipboard.
-   Edit metadata.
-   Move to another category.
-   Delete with confirmation or undo where practical.
-   Handle invalid or unsupported URLs without crashing.

### FR-06: File management

-   Select files through Android's system picker.
-   Copy files into LinkVault; never move or delete the original.
-   Save metadata in JSON, including ID, display name, relative storage
    path, MIME type, size, and timestamps.
-   Prevent filename collisions safely.
-   Validate that resolved paths remain inside the LinkVault storage
    root.
-   Show copy progress for larger files.
-   Handle cancellation, insufficient storage, inaccessible source URIs,
    and interrupted copies.
-   Do not claim a file was saved until the copy and metadata write have
    both succeeded or a recoverable state has been recorded.

### FR-07: User-selected storage

-   Let the user select the LinkVault root folder through Android's
    Storage Access Framework (SAF), using the appropriate document-tree
    picker.
-   Persist URI permissions where Android allows it.
-   Store the selected tree URI and permission state in app preferences,
    not as a hard-coded filesystem path.
-   On startup and before storage operations, check that the folder
    remains accessible.
-   If permission is revoked or the folder is missing, show a clear
    recovery flow to reselect the folder.
-   Explain any Android-version-specific limitations honestly.
-   Do not request broad storage permissions when SAF can satisfy the
    requirement.

### FR-08: JSON storage

Use a documented, versioned JSON schema. A suggested logical structure
is:

``` json
{
  "schemaVersion": 1,
  "id": "stable-unique-id",
  "type": "link",
  "title": "Useful resource",
  "url": "https://example.com/resource",
  "description": "Optional note",
  "categoryId": "development",
  "source": {
    "appName": "Instagram",
    "packageName": null
  },
  "imageUrl": null,
  "createdAt": "2026-11-16T10:00:00Z",
  "updatedAt": "2026-11-16T10:00:00Z"
}
```

This is a starting point, not permission to ignore schema design.
Finalize schemas for links, categories, and files before implementation.

Storage requirements:

-   Each saved link should have its own JSON record file, consistent
    with the original product decision.
-   Category metadata should also be stored in a documented JSON form.
-   File metadata should be stored as JSON; actual file bytes remain
    separate.
-   Use safe write patterns: write to a temporary file, validate, then
    replace/rename where supported.
-   Detect and report malformed JSON rather than silently discarding it.
-   Include schema versioning and migration strategy.
-   Use stable IDs, not titles or filenames, as record identity.
-   Sanitize filenames and never trust user-provided title text as a
    path.

### Important Android SAF engineering constraint

SAF document providers do not guarantee ordinary filesystem semantics,
direct path access, atomic rename, or identical performance. Design
storage operations around `ContentResolver` and document URIs where
required. Do not pretend a document-tree URI is a normal filesystem
path. Verify provider capabilities and define safe fallbacks for create,
read, replace, rename, and delete operations.

## 6. Suggested storage layout

The logical layout may look like this:

``` text
LinkVault/
├── user/
│   ├── categories/
│   │   ├── categories.json
│   │   ├── development/
│   │   │   ├── link_<id>.json
│   │   │   └── file_<id>.json
│   │   ├── instagram/
│   │   │   └── link_<id>.json
│   │   └── uncategorized/
│   │       └── link_<id>.json
│   ├── files/
│   │   └── <safe-generated-filename>
│   └── backup/
└── ...
```

This is a logical organization proposal. The implementation must account
for SAF provider behavior and should not assume that every provider
exposes a normal directory tree with conventional filesystem operations.

## 7. Backup and restore

### Backup

-   Export all category metadata, link JSON records, file metadata, and
    copied file contents.
-   Generate a ZIP with a manifest containing backup format version,
    creation time, and included item counts.
-   Verify that the archive is readable before reporting success.
-   Do not include temporary files, caches, or inaccessible content
    without clearly reporting omissions.
-   Handle low storage and interrupted export.
-   Allow the user to choose the destination through the system picker.

### Restore

-   Let the user select a LinkVault ZIP backup.
-   Validate archive structure, manifest, schema versions, file sizes,
    and paths before writing.
-   Reject path traversal entries such as `../`.
-   Preview restore counts and conflicts.
-   Never silently overwrite existing user data.
-   Provide a clear conflict policy: skip, keep both with new IDs/names,
    or replace only after explicit confirmation.
-   Restore through a staging area where feasible, then commit safely.
-   If restore fails, preserve the existing collection and report what
    happened.
-   Support future backup format migrations.

## 8. Reliability, privacy, and security

-   Core operations must work offline.
-   Do not transmit saved URLs, notes, file names, or file contents to a
    server.
-   Do not add analytics or crash-reporting SDKs that transmit personal
    data without explicit product approval and disclosure.
-   Validate all external URLs before opening them.
-   Treat imported JSON and ZIP archives as untrusted input.
-   Protect against path traversal, malicious archive entries, oversized
    files, malformed JSON, and duplicate IDs.
-   Avoid logging private URLs or user notes.
-   Use clear confirmation for destructive actions.
-   Recover from process death during save, copy, backup, and restore.
-   Never report success before data is actually persisted.

## 9. Performance and scale

The app should remain responsive with a large number of records.

-   Do not load every JSON record and thumbnail into memory at once.
-   Use pagination or incremental loading for lists.
-   Build a rebuildable index/cache if scanning every file becomes slow.
-   Keep JSON records as the source of truth.
-   Cache only what is needed and provide a way to rebuild it from JSON.
-   Avoid downloading thumbnails automatically.
-   Measure performance with realistic test collections, including
    thousands of link records.

## 10. UX requirements

-   Keep saving a shared link to a few simple steps.
-   Make category selection easy.
-   Provide clear success and failure feedback.
-   Include useful empty states and first-run guidance.
-   Show the selected storage folder and its access status in Settings.
-   Make permission recovery understandable to a non-technical user.
-   Use accessible text sizes, touch targets, and contrast.
-   Do not add visual complexity that slows down the primary save flow.

## 11. Suggested implementation phases

### Phase 0 --- Requirements and technical validation [COMPLETED & VERIFIED]

-   [x] Confirm Android language/framework and minimum supported Android
    version.
-   [x] Validate SAF document-tree behavior on target Android
    versions/providers.
-   [x] Finalize link, category, and file JSON schemas.
-   [x] Decide category deletion and duplicate-link policies.
-   [x] Define backup manifest and restore conflict policy.
-   [x] Produce architecture and implementation notes before feature coding.

**Exit criteria:** Decisions documented; no major unresolved storage or
permission assumption.

### Phase 1 --- App foundation and storage [COMPLETED & VERIFIED]

-   [x] Create the Android project and app structure.
-   [x] Implement first-run storage-folder selection.
-   [x] Persist and revalidate SAF permissions.
-   [x] Implement JSON read/write utilities and schema validation.
-   [x] Implement category metadata and safe file naming.
-   [x] Add error handling and recovery states.

**Exit criteria:** The app can select a folder,
create/read/update/delete test JSON records, survive restart, and
recover from revoked permission.

### Phase 2 --- Link capture and organization [COMPLETED & VERIFIED]

-   [x] Implement Android Share Intent receiving.
-   [x] Build the save/review screen.
-   [x] Implement link create/edit/delete/move.
-   [x] Implement category create/rename/delete.
-   [x] Add uncategorized handling.
-   [x] Add open and copy actions.

**Exit criteria:** A URL can be shared from another app, reviewed, saved
as JSON, reopened after app restart, edited, moved, and deleted.

### Phase 3 --- Search and file support [COMPLETED & VERIFIED]

-   [x] Implement search and sorting.
-   [x] Add incremental loading or a rebuildable index if needed.
-   [x] Implement file picker and copy-only import.
-   [x] Save file metadata JSON.
-   [x] Handle duplicate names, interrupted copies, and insufficient space.

**Exit criteria:** Links and imported files can be found and managed
reliably with a realistic collection.

### Phase 4 --- Backup and restore [COMPLETED & VERIFIED]

-   [x] Implement ZIP export and manifest.
-   [x] Implement archive validation.
-   [x] Implement restore preview and conflict handling.
-   [x] Add safe staging and rollback/recovery behavior.

**Exit criteria:** A collection can be backed up, restored to a clean
installation, and restored without silently overwriting existing data.

### Phase 5 --- Hardening and release [COMPLETED & VERIFIED]

-   [x] Test malformed URLs, malformed JSON, revoked permissions, provider
    failures, low storage, process death, duplicate records, and
    malicious ZIP entries.
-   [x] Test offline behavior.
-   [x] Test large collections and large file imports.
-   [x] Add accessibility and UX polish.
-   [x] Prepare release build, privacy statement, and user documentation.

**Exit criteria:** Critical flows pass tests; known limitations are
documented; no data-loss issue remains open.

### Phase 6 --- Cyberpunk High-Tech UI & Filter Tabs Polish [COMPLETED & VERIFIED]

-   [x] **High-Tech Obsidian Dark Cyberpunk Theme:** Designed immersive dark aesthetic (#090D16 base, #00F0FF neon cyan, #A855F7 neon purple, #23314D borders) with subtle circuit wallpaper (`bg_cyber_vault.jpg`).
-   [x] **App Logo & Branding:** Vector hexagonal cyber shield vault logo (`ic_vault_logo.xml`) and adaptive launcher icon (`ic_launcher_foreground.xml`).
-   [x] **Card Entry & Pulse Animations:** Custom layout and item entrance animations (`anim_card_entry.xml`, `layout_anim_cards.xml`, `anim_pulse.xml`).
-   [x] **Filter Tabs Architecture & Race Condition Fix:**
    - Separated top Type Filter bar (`All`, `🔗 Links`, `📁 Files`) from category folder chips (`📁 All Folders`, `Uncategorized`, `Tech`).
    - Fixed asynchronous filter race condition in `HomeViewModel.java` using synchronous volatile filter states (`currentTypeFilter`, `currentCategoryId`).
    - Implemented `ChipGroup.setOnCheckedStateChangeListener` for mutual exclusivity.
    - Verified live on connected physical device (`e893ddcf`) via ADB taps: `[All]`, `[🔗 Links]`, `[📁 Files]`, and category filters work with zero latency and clean state toggles.

### Phase 7 --- Share Intent & Back Navigation Loop Resolution [COMPLETED & VERIFIED]

-   [x] **Share Sheet Ingestion Multi-Source Extraction (`MainActivity.java`):**
    - Expanded share extraction to support `Intent.EXTRA_TEXT` (String/CharSequence), `Intent.getClipData()` (text and uri items), and `Intent.getData()`.
    - Auto-prefill Title from domain fallback when empty (`LinkRecord.fallbackTitle(url)`).
    - Clear Intent after handling so share intents never re-trigger on orientation, lifecycle changes, or back navigation.
-   [x] **Toolbar Tick (✓) Action Button (`SaveLinkFragment.java`, `menu_save.xml`):**
    - Added glowing cyan tick icon (`ic_check.xml`) in top Toolbar as an always-visible save action, accessible even when soft keyboard is open.
-   [x] **Outbound Link Sharing via Native System Chooser (`HomeFragment.java`):**
    - Added `Share link` option in link item popup menu (`action_share`).
    - Launches `Intent.ACTION_SEND` with `text/plain` and `Intent.createChooser`, listing all installed apps (WhatsApp, Telegram, Gmail, Messages, Twitter, etc.) for instant link sharing.
-   [x] **Caller Finish on Save & Back (`isFromShare`):**
    - When opened from Chrome or any external share, saving finishes the activity (`requireActivity().finish()`) with a Toast and returns user straight back to Chrome.
    - Pressing Back or `←` from a shared review screen cancels and finishes back to Chrome.
-   [x] **Infinite Back Navigation Loop Elimination:**
    - Set `app:startDestination="@id/homeFragment"` in `nav_graph.xml` when storage is granted.
    - Overrode `OnBackPressedCallback` on `HomeFragment` and `MainActivity` so pressing Back on the Main Dashboard immediately exits the app cleanly to launcher.
    - Tested live via ADB on device `e893ddcf`: zero loops, instant exit on back, clean 1-tap save.

## 12. Acceptance criteria

The product is not complete until all of the following are true:

-   [x] User can select a storage folder through SAF.
-   [x] Selected folder access survives app restarts when permission
    remains granted.
-   [x] App detects revoked or unavailable folder access and offers
    recovery.
-   [x] User can share a URL from another Android app into LinkVault.
-   [x] User can review/edit title, note, and category before saving.
-   [x] Each link is persisted as a versioned JSON record.
-   [x] Categories can be created, renamed, and safely deleted.
-   [x] Links can be searched, opened, copied, edited, moved, and
    deleted.
-   [x] User can import a file by copying it; original remains
    unchanged.
-   [x] File metadata is persisted in JSON.
-   [x] Core features work without internet.
-   [x] ZIP backup includes records, metadata, and copied files.
-   [x] Restore validates the archive and never silently overwrites
    existing data.
-   [x] Interrupted operations do not corrupt the existing collection.
-   [x] App handles malformed data and storage failures without
    crashing.
-   [x] Large collections remain usable and responsive.
-   [x] No backend, account, cloud sync, or database has been added
    without approval.

## 13. Open decisions resolved and locked

All technical decisions are confirmed, implemented, and verified:

1.  **Framework/Language:** Android Java + Jetpack Navigation + Material 3 Components with XML layouts (Cyberpunk High-Tech UI).
2.  **Android API Levels:** Min API 26 (Android 8.0 Oreo), Target API 35 (Android 15).
3.  **Tags & Pinning:** Implemented with tag chips, pinning switch, and priority sorting in `IndexRepository`.
4.  **Duplicate Detection:** Implemented via URL normalization in `UrlPolicy.normalizeForCompare` with non-blocking warning ("Save Anyway").
5.  **Page Titles/Metadata:** Offline-first approach with immediate domain fallback (`LinkRecord.fallbackTitle`) and system share title ingestion.
6.  **File Import Scope:** SAF streaming copy with original file preservation and MIME type autodetection.
7.  **Backup & Restore:** Full ZIP packaging with `manifest.json`, archive validation, and safe staging restoration.
8.  **Category Mapping:** Stable IDs (`cat_<uuid>`, `cat_ungrouped`) so folder renames never require disk-level directory movements.

## 14. Definition of done

LinkVault is ready for its first production release when the acceptance
criteria pass, data survives normal restarts and recoverable failures,
backup/restore has been tested, offline behavior is verified, and the
product has no hidden backend or cloud dependency.

The priority order is:

1.  Data safety and user ownership.
2.  Reliable Share Sheet capture.
3.  Simple folder/category organization.
4.  Search and everyday usability.
5.  Safe file import.
6.  Backup and restore.
7.  Visual polish and optional enhancements.
