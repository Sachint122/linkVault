# AI_TESTING — manual verification guide

> **Status: NOT executed.** Owner directive for this task was
> “test mat karna — bus code and update app”. Unit tests
> (`AiResultValidatorTest`, `RuleBasedAnalyzerTest`) ran in the build —
> 75/75 green. Everything below is a guide for a later device pass.

## A. Unit tests (already in the build)

```
gradlew testDebugUnitTest
```
Covers: JSON-reply parsing (valid / fenced / garbage), field sanitization
(category, tags ≤5 lowercase dedupe, summary ≤200), confidence clamping,
rule table (YouTube/GitHub/Wikipedia/unknown host/summary cap/malformed URL).

## B. Rule-only path (no model installed) — ~2 min

1. Fresh install, **no model pushed**.
2. Share a YouTube URL into the app → save.
3. Wait ~5–10 s on Home → open the record JSON (or check list meta):
   - `aiStatus: "done"`, `aiSource: "rule"`, `summary` present,
     category `Videos` (record moved out of Uncategorized).
   - `updatedAt` unchanged from save time.
4. Save `https://github.com/...` → category `Development`, tags include
   `development`.
5. Save an unknown host → stays ungrouped (or gets weak summary only),
   `aiConfidence` ≈ 0.3.

## C. Model path (~10 min, model push first)

Follow `AI_MODEL_SETUP.md` §2, then:

1. Save 2–3 new links (YouTube + article + unknown site).
2. First analysis takes longer (model load ≈ 10 s, one-time).
3. Record JSON now shows `aiSource: "local_ai"` for enriched ones,
   `confidence` from the LLM, summary ≤ 200 chars, ≤ 5 AI tags merged with
   user tags (user tags never removed).
4. Category: if a matching category exists (case-insensitive) it is reused —
   no duplicate category; else one is created with the AI name.
5. Live UI: Home list refreshes without manual pull (notifier path).

## D. Protection rules (must-pass)

| # | Action | Expected |
|---|---|---|
| D1 | Set a category manually, then let AI run | category **unchanged** |
| D2 | Edit title/description/tags after analysis | AI fields **persisted** (update() carry) |
| D3 | Manually set a description; AI produces summary | summary fills only if it was null; description untouched |
| D4 | User tags vs AI tags | union only; user tags never lost; ≤ 20 total |
| D5 | Save flow timing | save UI responds instantly; analysis never blocks it |

## E. Queue / recovery

1. **Dedupe:** kill the app right after saving (queue has the id) → reopen →
   job runs once, `ai_queue.json` drains to `{"pending":[]}`.
2. **Restart recovery:** save 5 links quickly, force-stop mid-run → reopen →
   remaining items processed (single worker, no duplicates).
3. **Battery low:** emulator/device at ≤15% → job waits (constraint), runs
   after charging/level up — no crash, no hot loop.
4. **Failure cap:** (optional) push a corrupt `ai_model.litertlm` (>1 MB
   garbage) → init fails once (latched), rule path still completes every
   record, `done` + `rule` — no crash, no infinite retry.
5. **Failed list:** `adb shell run-as com.linkvault.app cat files/ai_queue.json`
   shows `attempts`/`failed` when something went wrong 3×.

## F. Interop / data safety

1. Backup with analyzed links → restore (SKIP) → records keep AI fields.
2. Restore KEEP_BOTH → duplicate carries the backup's AI fields; without
   them it gets queued.
3. Old backups (no AI keys) restore cleanly — codec defaults apply.
4. Dead-link check after analysis → `checkedAt/httpStatus` set, AI fields
   untouched, `updatedAt` still not bumped by either.

## G. Performance / battery sanity

- `adb shell dumpsys jobscheduler | findstr /i linkvault` → one AI job,
  not a queue of parallel workers.
- During inference: CPU busy on one thread only; UI stays scrollable.
- `onTrimMemory` (background the app) → model freed; next analysis re-inits.

## Report format (bas itna bhejo)

```
AI test: pass/fail per section B–F
record JSON snippets (aiStatus/aiSource/summary) for 2 links
ai_queue.json snapshot
anything unexpected
```
