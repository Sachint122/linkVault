# AI_MODEL_SETUP — installing the on-device model

The app works **without any model** (rule-based classification only). The
model is optional enrichment, pushed manually into app-internal storage.

## 1. Format — read this first

- Runtime: **LiteRT-LM 0.19.1** → model file must be **`.litertlm`**.
- **A GGUF file will NOT run.** LiteRT-LM officially does not accept GGUF
  (no conversion tool is bundled with this app). Do not download GGUF
  quantizations for LinkVault.
- Recommended: **`litert-community/Qwen3-0.6B`** on Hugging Face
  (`Qwen3-0.6B.litertlm`, ~0.6 GB — inside the 0.5–1.5 B spec band).
- Alternative: `litert-community/Gemma3-1B-IT` (`.litertlm`).
- Unknown/other sizes: any `.litertlm` ≤ ~1.5 B params should work; bigger
  models will be slow on mid-range CPUs.

## 2. Install (debug build, ADB)

```bat
:: 1) Download Qwen3-0.6B.litertlm on the PC, then:
adb push Qwen3-0.6B.litertlm C:\Users\sachi\AppData\Local\Temp\

:: 2) Copy into the app's internal files dir (debug APKs allow run-as):
adb shell run-as com.linkvault.app sh -c "cat /data/local/tmp/Qwen3-0.6B.litertlm > files/ai_model.litertlm"

:: 3) (optional) restart the app
adb shell am force-stop com.linkvault.app
```

If `run-as` fails, the APK is not debuggable — install the debug build first.

## 3. Expected path / size check

- Path: `filesDir/ai_model.litertlm` →
  `/data/data/com.linkvault.app/files/ai_model.litertlm`
- The pipeline only accepts files **≥ 1 MB** (`LocalAiManager.isModelReady`).
- Verify: `adb shell run-as com.linkvault.app ls -l files/ai_model.litertlm`

## 4. Behavior with vs without the model

| | No model | Model present |
|---|---|---|
| Pipeline | rule-based only, `aiSource=rule` | rules first, LLM enriches, `aiSource=local_ai` |
| First analysis after install | instant | first job loads the model (~10 s, one-time, on the worker thread) |
| Failure to init | silent fallback to rules | latched until memory release/restart; rules still run |
| UI/save flow | unaffected | unaffected (all background) |

## 5. Removing / replacing the model

```bat
adb shell run-as com.linkvault.app rm files/ai_model.litertlm
```

Restart the app (or wait for `onTrimMemory` release) → back to rule-only.
To replace: delete the old file first, then push the new one.

## 6. Storage & battery notes

- Model lives in **internal storage**, never in the SAF vault folder — it is
  not backed up, not exported, not part of the JSON tree.
- Worker constraint: **battery not low**; 8-minute budget per run with
  retry/backoff; generations capped at 90 s each.
- Removing the file while a generation is in flight is safe (the running
  call finishes, the next `ensureReady()` sees no model).
