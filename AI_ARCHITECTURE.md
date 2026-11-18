# AI_ARCHITECTURE — On-device offline AI pipeline

**Status:** implemented, build green (`assembleDebug testDebugUnitTest` EXIT=0,
75/75 unit tests). **Not device-tested** (owner directive: code + docs only).

## 1. Goals (from the AI spec)

- Fully **offline**: no cloud, no network calls, no API keys.
- Background only: **never blocks save**, never shows dialogs/spinners.
- **Rule-based first**, LLM only to enrich (deeper classification + summary).
- Structured JSON contract out of the LLM; validated before any write.
- Single sequential job: deduped, persisted, recovers across restart/reboot.
- **Never overwrite user-edited metadata** (fill-if-null / additive-only).
- Java + XML stack kept (D-01); JSON-only storage kept (D-03); no DB (D-05).

## 2. Phase 1 audit — integration points (as built)

| Concern | Where |
|---|---|
| Enqueue after save | `SaveLinkViewModel.save()` → `aiQueueManager().enqueue(rec.getId())` after `repo.create()` (new links only; edit path untouched) |
| Auto-categorize rules win | AutoCat assigns at save-time; AI only fills category while record is still `cat_ungrouped` |
| Schema extension | `LinkRecord` +6 fields, `LinkCodec` additive read/write (same pattern as `checkedAt`) |
| Persist without content-edit semantics | `RecordRepository.saveAi()` — no `updatedAt` bump; handles category-folder move (write new + delete old) |
| Edit-path AI carry | `RecordRepository.update()` takes incoming AI fields only when actually set (13/15-arg builders → stored analysis preserved) |
| Backup travel | `BackupEngine` zips `linkCodec.toJson()` output → AI fields travel automatically |
| Restore | KEEP_BOTH copies AI fields via `withAiFrom`; new/updated records enqueued only when not already `done` |
| Live UI refresh | `AiUpdateNotifier` → `HomeViewModel` (500 ms throttle) → `refresh()`; `IndexRepository.replaceItem()` for the cached row |
| Memory | `App.onTrimMemory(≥ BACKGROUND)` → `LocalAiManager.release()` (no-op while generating) |
| Process/reboot recovery | `App.onCreate` → `aiQueueManager().recover()`; WorkManager persists the job itself |

## 3. Runtime decision: LiteRT-LM (not llama.cpp JNI)

| | LiteRT-LM **0.19.1** (chosen) | llama.cpp JNI |
|---|---|---|
| Integration | Official Maven artifact, Kotlin API — **one** interop file (`LocalAiEngine.kt`), rest of app stays Java | Full NDK + CMake build of llama.cpp, JNI wrapper to write/maintain |
| Toolchain already present | ✅ (Kotlin plugin added) | ✅ NDK 29 + CMake 3.22 installed, but native build = fragile + slow |
| Model format | **`.litertlm` only — GGUF does NOT run** (officially documented) | GGUF direct |
| Recommended model | `litert-community/Qwen3-0.6B` `.litertlm` (0.6 GB, in 0.5–1.5 B spec band) | Qwen2.5-1.5B-Instruct Q4 GGUF |
| Backend | CPU (spec: CPU first), GPU/NPU possible later | CPU/GPU |
| Maturity | v0.19.1, powers Google AI Edge Gallery / Chrome on-device | community-maintained |

**Why LiteRT-LM won:** spec §8 explicitly names it the preferred runtime and
allows Kotlin interop for it; zero native build maintenance; official model
release in the required size band; CPU path needs no manifest native-library
entries. Cost: Kotlin 2.4.21 plugin (metadata 2.4.0 requirement discovered at
build time) and a model-format conversion story (user downloads `.litertlm`,
never GGUF — see `AI_MODEL_SETUP.md`).

## 4. Module map (`app/src/main/java/com/linkvault/app/ai/`)

| File | Role |
|---|---|
| `AiContract.java` | Constants: statuses, sources, caps (5 tags / 200 chars / 24-char category), timeouts, budget |
| `AiResult.java` | Immutable result {category, tags, summary, confidence} + `merge(rules, llm)` |
| `RuleBasedAnalyzer.java` | Phase 1: deterministic platform table (YouTube/GitHub/… 19 rules) → category/tags/summary/confidence; generic fallback (conf 0.3) |
| `AiResultValidator.java` | Parses raw LLM reply (strips fences/chatter), sanitizes every field, returns null when unusable |
| `AiPrompt.java` | Strict JSON-out system prompt + per-record user prompt |
| `LocalAiEngine.kt` | **Only Kotlin file.** LiteRT-LM wrapper: `EngineConfig(CPU)` → `initialize()` → `sendMessage()`; thinking disabled (Qwen3); text extracted from `Message.contents` |
| `LocalAiManager.java` | Facade: lazy init on worker thread, 90 s per-call timeout (hung native call abandoned on a daemon thread), `release()` under memory pressure |
| `AiQueueStore.java` | Persistent FIFO `filesDir/ai_queue.json` (pending/attempts/failed); corrupt file quarantined, never crashes |
| `AIQueueManager.java` | Single unique WorkManager job (`ai_queue_worker`, KEEP), battery-not-low, kick/recover, lock-safe begin/finish (no enqueue/exit race) |
| `AiWorker.java` | The sequential loop: claim → rules → optional LLM → validate → merge → protection → `saveAi` → notifier |
| `AiUpdateNotifier.java` | Static listener list for live UI refresh |

## 5. Data model (see `DATA_SCHEMA.md` §3 for the field table)

`summary, aiStatus (pending/done/failed), aiConfidence, aiSource
(rule/local_ai), aiAnalyzedAt, aiVersion` — additive, omitted when unset,
schemaVersion stays 1 (old readers ignore unknown keys; our reader is lenient).

**Protection rules enforced in `AiWorker`:**
1. `summary` — write only if currently null (never overwrite).
2. `categoryId` — AI assigns only while record is `cat_ungrouped` (user folder
   and AutoCat result always win); matched by name case-insensitively, created
   only when no match (failure → stay ungrouped, job still succeeds).
3. `tags` — additive union, case-insensitive dedupe, capped at 20 record tags
   (AI contributes ≤ 5); user tags never removed.
4. AI writes never bump `updatedAt`.
5. Malformed LLM reply → rule result stands, status `done`, `aiSource=rule`
   (no retry loop — same prompt is deterministic).

## 6. Toolchain changes

- Root `build.gradle`: `org.jetbrains.kotlin.android` **2.4.21** (litertlm
  ships Kotlin metadata 2.4.0 — 2.0.x cannot read it).
- `app/build.gradle`: Kotlin plugin, `kotlin { compilerOptions { jvmTarget 17 } }`,
  `com.google.ai.edge.litertlm:litertlm-android:0.19.1`.
- No new permissions; `INTERNET` already present for dead-link checks only —
  the AI path performs **no** network I/O.
