# LinkVault

Offline-first Android app for capturing links via the native Share Sheet,
organizing them into categories, importing small files (copy-only), and
keeping everything in user-owned JSON files inside a user-selected SAF
folder. No backend, no accounts, no cloud, no database.

**Status:** Phase 0 complete (analysis + architecture + docs). No code yet.
See `brain/STATUS.md` for live status.

**Source of truth:** [`LINKVAULT_MASTER_GOAL.md`](LINKVAULT_MASTER_GOAL.md)

## Documentation index

| Doc | Contents |
|---|---|
| `REQUIREMENTS.md` | Functional requirements FR-01…FR-08, constraints, acceptance criteria |
| `ARCHITECTURE.md` | Tech stack, layers, SAF strategy, Share Intent, backup/restore, security, performance |
| `DATA_SCHEMA.md` | JSON schemas (v1), storage layout, safe-write protocol, migration rules |
| `IMPLEMENTATION_PLAN.md` | Phases 0–5, tasks, exit criteria, risk register |
| `TESTING_STRATEGY.md` | Test layers, critical test matrix, tools, phase gates |
| `AI_CONTEXT.md` | Working rules for AI sessions (pointer to `brain/`) |
| `brain/` | **Read this first in every session:** INDEX, STATUS, DECISIONS, CHANGELOG |

## Hard constraints (non-negotiable)

- No backend, login, cloud sync, or any database (no SQLite/Room/Realm/MongoDB).
- JSON files in a user-selected folder (Storage Access Framework) are the
  only source of truth.
- Imports are **copy-only** — original user files are never moved or deleted.
- All core features work fully offline.
- ZIP backup/restore only, with validation and no silent overwrites.

## Approved stack (D-01/D-02 — 2026-11-16)

**Java** · XML layouts + Material Components · Fragments + Navigation ·
MVVM (ViewModel + LiveData) · manual DI · `org.json` · WorkManager ·
SharedPreferences · SAF-only storage · minSdk 26 / targetSdk 35.
Tags + pinned/favourite are **in MVP** (D-09); file imports capped at
100 MB (D-10); no network metadata fetch (D-08).

## Getting started (once Phase 1 lands)

```bash
# Project initialization command(s) will be documented here in Phase 1.
```
