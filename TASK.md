# TASK — current work tracker

## ✅ Done: on-device offline AI integration (2026-11-18)

Owner ask: *"Fully integrate an offline AI engine… test mat karna — bus code and update app."*

| Step | Status |
|---|---|
| Phase 1 audit (integration points, runtime research) | ✅ |
| Runtime decision: LiteRT-LM 0.19.1 vs llama.cpp JNI → LiteRT-LM (`AI_ARCHITECTURE.md` §3, `D-17`) | ✅ |
| Build config: Kotlin 2.4.21 + `litertlm-android:0.19.1` | ✅ |
| Schema: `LinkRecord` AI fields + `LinkCodec` + `saveAi()`/`update()` carry | ✅ |
| Rule-based analyzer + JSON validator + prompts | ✅ |
| `LocalAiEngine.kt` + `LocalAiManager` (timeout, memory release) | ✅ |
| Persistent queue + single sequential `AiWorker` (dedupe/attempts/budget/recovery) | ✅ |
| Hooks: save, restore, `App.onCreate`/`onTrimMemory`, live UI notifier | ✅ |
| Unit tests + build | ✅ **EXIT=0, 75/75** |
| Docs: `AI_ARCHITECTURE`, `AI_MODEL_SETUP`, `AI_PROCESSING_FLOW`, `AI_TESTING`, `DATA_SCHEMA` §3, `CHANGELOG`, `STATUS`, `D-17` | ✅ |
| Install updated APK | ✅ (see session report) |
| Device testing | ⬜ **skipped on purpose** (owner: “test mat karna”) — guide in `AI_TESTING.md` |
| Push to GitHub | ⬜ not requested yet |

## Open / next

1. **Optional model push** by owner: `.litertlm` → `filesDir/ai_model.litertlm`
   (`AI_MODEL_SETUP.md`). Without it the app runs rule-only — fully functional.
2. **Device pass** of `AI_TESTING.md` whenever owner wants.
3. Commit + push the AI work when asked.
4. Phase 4 planning per `IMPLEMENTATION_PLAN.md`.

## Hard reminders

- Never claim device verification that did not happen (AI_CONTEXT rule 6).
- AI = background only; no dialogs, no save blocking, no network (rule 1).
- GGUF does **not** run in LiteRT-LM — `.litertlm` only.
