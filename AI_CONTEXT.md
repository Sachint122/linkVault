# AI_CONTEXT — Working rules for AI sessions

**Session bootstrap: read `brain/` first.**
Order: `brain/INDEX.md` → `brain/STATUS.md` → `brain/DECISIONS.md` →
`brain/CHANGELOG.md`. Open other root docs only when the task needs them.
Never bulk-read the codebase; read specific files on demand.

## What this project is

LinkVault: offline-first Android app (**Java + XML layouts**, MVVM —
owner-approved D-01) that saves shared URLs and copies small files into a
user-selected SAF folder as versioned JSON files, with categories, tags/
pinned, search, and ZIP backup/restore. Authoritative spec:
`LINKVAULT_MASTER_GOAL.md`. Live state: `brain/STATUS.md`.

**Communication preference:** explain Android-specific concepts in simple
Hinglish when discussing them with the owner.

## Never do (hard constraints)

1. Do not add a database (SQLite/Room/Realm/MongoDB), backend, accounts,
   cloud sync, or any network transmission of user data — the JSON tree is
   the sole source of truth.
2. Do not replace JSON-per-record storage with bulk files or a cache as
   primary storage.
3. Do not move/delete/modify users' original files; imports are copy-only.
4. Do not store raw filesystem paths; only SAF tree URIs + persisted grants.
5. Do not silently overwrite user data (no implicit replace in restore,
   destructive ops need confirmation).
6. Do not claim a feature is implemented unless code exists and tests
   cover it; update `REQUIREMENTS.md` checklist honestly.
7. Do not log URLs, notes, or file names.
8. Do not change an APPROVED decision in `brain/DECISIONS.md` without
   explaining why and getting product-owner approval; record new decisions
   there.

## Always do

1. Follow the current phase's exit criteria in `IMPLEMENTATION_PLAN.md`
   before starting the next phase.
2. Match existing code style; reuse `DocumentTreeClient` /
   `SafeWriteEngine` / repositories instead of ad-hoc file I/O.
3. Run tests for touched code before declaring work complete.
4. Update `brain/STATUS.md` + `brain/CHANGELOG.md` (and `DECISIONS.md`
   when applicable) after every meaningful change; keep
   `REQUIREMENTS.md`/`ARCHITECTURE.md`/`DATA_SCHEMA.md` synchronized with
   implementation drift.
5. Surface spec conflicts or technically problematic requirements with a
   practical alternative — never silently re-scope.

## Architecture quick map (details: `ARCHITECTURE.md`)

`ui` (Fragments + ViewModels, XML layouts) → `domain` (pure Java policies/
use cases) → `data` (saf/json/repo/share/backup — only layer touching SAF)
+ `work` (WorkManager jobs). Manual DI via `di/AppContainer`. JSON via
`org.json`.

## Current state shortcut

Phase 0 docs done; stack **approved** (Java + XML, minSdk 26). **No code
exists yet.** Next: Phase 1 project initialization after explicit
go-ahead (see `brain/STATUS.md`).
