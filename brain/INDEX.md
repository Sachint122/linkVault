# Brain — Project Context Hub

**Purpose:** Single entry point for AI working sessions. Never re-read the
whole project. Read this folder first, then open only the specific files the
task requires.

## Session protocol

1. Read, in order:
   - `brain/STATUS.md` — where the project is right now, what is next.
   - `brain/DECISIONS.md` — approved/proposed technical decisions (ADRs).
   - `brain/CHANGELOG.md` — what changed recently and why.
2. If the task needs requirements/schema/architecture detail, open only the
   one relevant root doc from the map below.
3. If the task needs code detail, read only the specific source files
   involved. Never glob-read the whole codebase.
4. After any meaningful change, update `brain/STATUS.md` and
   `brain/CHANGELOG.md`, and add/amend an entry in `brain/DECISIONS.md` if a
   decision was made or changed.

## File map

| File | Purpose | Read when |
|---|---|---|
| `brain/INDEX.md` | This protocol + doc map | Every session, first |
| `brain/STATUS.md` | Current phase, done/next/blockers | Every session, second |
| `brain/DECISIONS.md` | Decision log (proposed/approved/rejected) | Every session, third |
| `brain/CHANGELOG.md` | Dated change history | Every session, fourth |
| `README.md` | Project overview + doc index | Onboarding / summary needed |
| `REQUIREMENTS.md` | Functional requirements + acceptance criteria | Requirement or scope questions |
| `ARCHITECTURE.md` | Stack, layers, SAF/Share/backup strategies | Design or structural work |
| `DATA_SCHEMA.md` | JSON schemas, storage layout, safe-write rules | Any storage/record work |
| `IMPLEMENTATION_PLAN.md` | Phase plan, tasks, exit criteria, risks | Planning / status reporting |
| `TESTING_STRATEGY.md` | Test layers, critical cases, tools | Writing or running tests |
| `AI_CONTEXT.md` | AI working rules for this repo | Session start (thin pointer here) |
| `LINKVAULT_MASTER_GOAL.md` | Authoritative product spec (read-only source of truth) | Only when a requirement is ambiguous |

## Hard rules (from product spec, never violate)

- JSON files in a user-selected SAF folder are the only source of truth.
- No backend, no accounts, no cloud, no SQLite/Realm/MongoDB/database.
- Never move or delete the user's original files; imports are copy-only.
- Never claim a feature is implemented unless code + tests support it.
