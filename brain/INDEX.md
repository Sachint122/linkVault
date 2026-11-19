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
   one relevant doc from the map below.
3. If the task needs code detail, read only the specific source files
   involved. Never glob-read the whole codebase.
4. After any meaningful change, update `brain/STATUS.md` and
   `brain/CHANGELOG.md`, and add/amend an entry in `brain/DECISIONS.md` if a
   decision was made or changed.

## Documentation Map

| File | Purpose | Read when |
|---|---|---|
| `brain/INDEX.md` | This protocol + doc map | Every session, first |
| `brain/STATUS.md` | Current phase, done/next/blockers | Every session, second |
| `brain/DECISIONS.md` | Decision log (D-01 to D-20) | Every session, third |
| `brain/CHANGELOG.md` | Chronological version & change history | Every session, fourth |
| `README.md` | Public repository overview & quickstart | Onboarding / release summary |
| `brain/DATA_SCHEMA.md` | JSON schemas (v1), storage layout, safe-write protocol | Storage, codecs, or record work |
| `brain/ARCHITECTURE.md` | Stack, layers, SAF, backup, and component architecture | Architecture, service, or DI work |
| `brain/REQUIREMENTS.md` | Functional requirements FR-01…FR-08 | Requirement or scope questions |
| `brain/AI_SIDEBAR.md` | AI Writing Assistant Sidebar architecture & specs | Floating overlay, gesture, or LLM work |
| `brain/TEST_GUIDE.md` | Manual test checklist & ADB command guide | Device testing & verification passes |

## Hard rules (from product spec, never violate)

- JSON files in a user-selected SAF folder are the only source of truth.
- No backend, no accounts, no cloud, no SQLite/Realm/MongoDB/database.
- Never move or delete the user's original files; imports are copy-only.
- Never claim a feature is implemented unless code + tests support it.
- Zero regression: All 122 unit tests must pass before declaring work complete.
