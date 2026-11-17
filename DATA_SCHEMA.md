# LinkVault — Data Schema (v1)

Status: approved for implementation (stack: Java, `org.json` — D-01/D-05).
Every record file carries `schemaVersion`; readers **ignore unknown keys**
(forward compat) and reject records with a *newer* schema only after a
migration check.

## 1. Principles

- **One JSON file per record.** Links and files each get their own file.
- **Stable IDs are identity** (UUIDv4 strings). Titles/filenames never are.
- **JSON files in the SAF tree are the only source of truth.** The index
  cache is disposable.
- **Timestamps:** ISO-8601 UTC strings (`2026-11-16T10:00:00Z`).
- **Malformed files are reported, never silently deleted** (quarantine
  listing in Settings → Issues).

## 2. Storage layout (inside the user-selected tree)

```text
LinkVault/
├── user/
│   ├── categories/
│   │   ├── categories.json            # all category metadata (single file)
│   │   ├── cat_<uuid>/                # folder name IS the stable ID (D-06)
│   │   │   ├── link_<id>.json
│   │   │   └── file_<id>.json
│   │   └── cat_ungrouped/             # built-in "Uncategorized" (fixed ID)
│   ├── files/
│   │   └── <safe-generated-name>      # imported file bytes (copy-only)
│   └── backup/                        # optional local backup copies
├── .index/
│   └── index.json                     # rebuildable cache (NOT source of truth)
├── .staging/                          # restore/import staging (transient)
└── .tmp/                              # safe-write temp files (transient)
```

Notes: display category names appear **only** in `categories.json` — folders
always use the ID slug (renames touch one file; no path traversal; no
collisions). Records whose `categoryId` has no folder fall back to
`cat_ungrouped` on sync. Storage is rooted at the tree URI; all paths above
are *relative* and validated for containment on every operation.

## 3. Link record — `link_<id>.json`

```json
{
  "schemaVersion": 1,
  "id": "b7c1e0a2-6f3d-4c8e-9a10-2d5f8b3c7e41",
  "type": "link",
  "title": "Useful resource",
  "url": "https://example.com/resource",
  "description": "Optional note",
  "categoryId": "cat_ungrouped",
  "source": { "appName": "Instagram", "packageName": "com.instagram.android" },
  "imageUrl": null,
  "tags": ["android", "reference"],
  "pinned": false,
  "createdAt": "2026-11-16T10:00:00Z",
  "updatedAt": "2026-11-16T10:00:00Z"
}
```

| Field | Type | Rules |
|---|---|---|
| `schemaVersion` | int | required, = 1 |
| `id` | string | required, UUIDv4, immutable, unique across all records |
| `type` | string | `"link"` |
| `title` | string | required (fallback = domain of URL), 1–300 chars |
| `url` | string | required, must parse, scheme http/https (validated) |
| `description` | string? | optional, ≤ 10,000 chars |
| `categoryId` | string | must exist in `categories.json` (else resolves to uncategorized) |
| `source` | object? | `appName`/`packageName` best-effort, both nullable |
| `imageUrl` | string? | stored only, never auto-fetched (D-08) |
| `tags` | string[] | MVP (D-09): 0–20 tags, each 1–30 chars, trimmed, deduped case-insensitive |
| `pinned` | boolean | MVP (D-09): default false; pinned items sort first |
| `createdAt`/`updatedAt` | string | ISO-8601 UTC |

Filename: `link_<id>.json` (UUID chars are already path-safe).

## 4. File record — `file_<id>.json`

```json
{
  "schemaVersion": 1,
  "id": "5a9d2f44-1c67-4f0a-8f2e-9c3b6d1e7a55",
  "type": "file",
  "displayName": "report.pdf",
  "relativePath": "user/files/report_5a9d2f44.pdf",
  "mimeType": "application/pdf",
  "sizeBytes": 204800,
  "categoryId": "cat_ungrouped",
  "tags": [],
  "createdAt": "2026-11-16T10:05:00Z",
  "updatedAt": "2026-11-16T10:05:00Z"
}
```

Rules: `relativePath` must resolve inside the storage root (containment
check on read/open); `displayName` is the original name (UI only, ≤ 255
chars, sanitized for display); physical filename =
`<sanitized-base>_<shortId>.<ext>` — collision-proof by construction; record
is written **only after** the byte copy is flushed and size-verified;
`tags` follows the same rules as link tags (D-09); files have no `pinned`.

## 5. Category metadata — `categories.json`

```json
{
  "schemaVersion": 1,
  "updatedAt": "2026-11-16T10:00:00Z",
  "categories": [
    { "id": "cat_ungrouped", "name": "Uncategorized", "createdAt": "2026-11-16T09:00:00Z", "updatedAt": "2026-11-16T09:00:00Z", "sortOrder": 0, "system": true },
    { "id": "cat_3f8b…", "name": "Development", "createdAt": "…", "updatedAt": "…", "sortOrder": 1, "system": false }
  ]
}
```

Rules: `cat_ungrouped` always exists and cannot be deleted or renamed out of
existence; name 1–60 chars, trimmed, non-empty, uniqueness by
case-insensitive trim; `system` flags built-ins; delete of non-empty
category requires the move-to-uncategorized-or-cancel prompt (FR-03).

## 6. Index cache — `.index/index.json`

```json
{
  "schemaVersion": 1,
  "builtAt": "2026-11-16T10:00:00Z",
  "records": [
    { "id": "…", "type": "link", "title": "…", "url": "…", "descriptionExcerpt": "…", "categoryId": "…", "tags": ["…"], "pinned": false, "sizeBytes": null, "createdAt": "…", "updatedAt": "…" }
  ]
}
```

Disposable: deleted/corrupt cache triggers a full rescan; every mutation
updates the index in the same logical step as the record write (index write
failure never fails the record write; it flags "rebuild needed").

## 7. Backup manifest — `manifest.json` (first ZIP entry)

```json
{
  "format": "linkvault-backup",
  "formatVersion": 1,
  "appVersion": "1.0.0",
  "createdAt": "2026-11-16T12:00:00Z",
  "counts": { "categories": 5, "links": 120, "files": 12, "fileBytes": 34012671 },
  "omissions": []
}
```

ZIP entry paths mirror the relative layout of §2 (minus `.index/`,
`.staging/`, `.tmp/`).

## 8. ID and filename rules

- IDs: UUIDv4 (`java.util.UUID.randomUUID()`), generated once at creation.
- Duplicate IDs on restore: per conflict policy (skip / re-ID keep-both /
  replace-after-confirm) — never auto-merged.
- Filename sanitizer: strip/reject anything outside `[a-zA-Z0-9._-]`,
  reject `.`/`..`, cap length (60 chars for physical names), always prefix
  type (`link_`, `file_`, `cat_`).

## 9. Safe-write protocol (all JSON + file metadata)

1. Serialize → write to `.tmp/<name>.<rand>.tmp` in the tree.
2. Re-read + re-parse the temp file; verify schema-valid & byte length.
3. Replace target: `renameDocument` if supported, else copy-bytes onto
   target then delete temp (target's previous content is preserved until
   step 2 passed; for overwrite cases keep `.bak` of the old target).
4. Verify target parses; only then report success.
5. Startup sweep removes stale app-owned `.tmp` files (age > 1 h).

## 10. Schema versioning & migration

- `schemaVersion` per file; central `Migrations` registry
  (vN → vN+1 pure functions) applied on read; migrated records rewritten
  lazily on next edit (read-path migration, write-path upgrade optional).
- Records with **newer** `schemaVersion` than the app understands → shown
  as read-only "newer version" placeholder, never overwritten.
- Backup `formatVersion` gates restore; unsupported → refuse with message.
