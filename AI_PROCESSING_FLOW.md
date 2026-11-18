# AI_PROCESSING_FLOW — how a link gets analyzed

## 1. Happy path (sequence)

```
user saves link
  └─ SaveLinkViewModel.save()
       ├─ repo.create(rec)                    (blocking-ish IO on its own executor)
       ├─ aiQueueManager.enqueue(rec.getId()) ← non-blocking, deduped
       ├─ index invalidate + widget + saved event   (save flow DONE here — AI never delays it)
       └─ ...
AIQueueManager.enqueue
  ├─ ai_queue.json: append id (skip if already queued)   ← dedupe #1
  └─ kick: enqueueUniqueWork("ai_queue_worker", KEEP, battery-not-low)

AiWorker.doWork()  (background, one instance ever)
  loop (8-min budget, then Result.retry()):
    beginItem()  → peek id + markAttempt + persist (or null → success/exits)
    processOne(id):
      1. findById → NOT_FOUND → drop job (success)
      2. aiStatus == "done" → drop (dedupe #2)
      3. set aiStatus="pending" marker (best-effort, no updatedAt bump)
      4. RuleBasedAnalyzer.analyze(url, title)          ← always runs
      5. if model present: LocalAiManager.generate(prompt)
           → AiResultValidator.parse(raw) → AiResult.merge(rules, llm)
           (unusable reply → rules stand, source=rule)
      6. protection: summary fill-if-null; category only if cat_ungrouped
         (name-match → else create → else stay ungrouped); tags additive ≤20
      7. records.saveAi(applied)        ← writes JSON, moves folder if needed
      8. indexRepository.replaceItem + AiUpdateNotifier.fire
    finishItem(id, ok, reason)
      ok        → complete (removed from queue)
      !ok, attempts < 3  → rotate to tail (retried next run)
      !ok, attempts ≥ 3  → failed list (bounded 50), job dropped
```

`HomeViewModel` hears the notifier (500 ms throttle) → `refresh()` → new
summary/tags/category appear live. No dialog, no spinner, no save delay.

## 2. Queue persistence — `filesDir/ai_queue.json`

```json
{
  "pending": ["<recordId>", "..."],
  "attempts": { "<recordId>": 1 },
  "failed": [ { "id": "...", "at": "ISO", "reason": "..." } ]
}
```

- Written on **every** mutation (tiny file, atomic tmp+rename).
- Corrupt file → renamed `.bad`, queue starts clean (queue ≠ user data).
- Survives process death; WorkManager persists the *job* separately.

## 3. Job guarantees

| Property | Mechanism |
|---|---|
| Sequential (one at a time) | single unique work name + `ExistingWorkPolicy.KEEP`; a mid-run `enqueue` is picked up by the running loop (`workerActive` flag, set/cleared under the same lock as the queue peek — no stuck-item race) |
| Dedupe | queue-level (id already pending) + record-level (`aiStatus=done`) |
| Restart recovery | `App.onCreate → recover()` re-kicks if items pending; WorkManager itself reschedules across reboot (JobScheduler persistence) |
| Failure control | 3 attempts per item (rotate between runs), then `failed` list; infrastructure errors → `Result.retry()` with 30 s linear backoff; malformed LLM reply is **not** retried |
| Long-run safety | 8-min budget → `Result.retry()` continues later; single generation hard-capped at 90 s (thread abandoned on timeout) |
| Battery | `setRequiresBatteryNotLow(true)`; CPU inference only |
| Offline | zero network APIs in the pipeline |

## 4. Interactions

- **AutoCat rules:** applied at save-time; AI only touches an ungrouped
  record → rules always win.
- **User edits:** edit screen builds a 13-arg record → `update()` keeps
  stored AI fields; if the user later sets a category, AI never moves it.
- **Restore:** backup AI fields carried (`withAiFrom`); records without
  `done` are queued after restore.
- **Backup:** raw JSON zips → AI fields included automatically.
- **Dead-link checks / widget / export:** unaffected (`updatedAt` untouched
  by AI writes, so “recently changed” semantics stay user-driven).

## 5. States of `aiStatus`

```
(absent) → pending → done
                  ↘ (3 failures) → failed   [also recorded in ai_queue.json]
```

`aiSource` explains how `done` was reached: `rule` (no model / LLM skipped or
unusable) vs `local_ai` (validated LLM output merged in).
