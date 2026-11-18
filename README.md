# LinkVault

**100% offline link vault for Android** — share se seedha save, on-device AI
khud folder/summary/tags banata hai, aur roz khud app clean karta hai.
No accounts, no cloud, no ads. Sab kuch **aapke apne folder** me plain JSON
files ke roop me.

| | |
|---|---|
| **Version** | **1.1.0** (versionCode 2) |
| **Requires** | Android 8.0+ (API 26) |
| **APK size** | ~54 MB (model bundled **nahi** hai) |
| **Internet** | Optional — sirf dead-link check ke liye; baaki sab fully offline |

---

## ⬇️ Download & Install

**1. APK download karein:**
**[LinkVault-v1.1.0.apk](https://github.com/Sachint122/linkVault/raw/refs/heads/main/releases/LinkVault-v1.1.0.apk)**

**2. Install karein:**
- APK par tap karein → Android poochega *"Install unknown apps"* → apne
  browser/file manager ke liye **Allow** karein
- **Install** → app kholein
- Pehli baar ek **storage folder select** karna hoga (onboarding) — bas
  ek baar; uske baad sab automatic

**3. Aage update:** naya version ka APK isi par tap karke install karein
(data safe rehta hai — same signature, uninstall ki zarurat nahi).

> **Note:** phone "Play Protect" block dikhaaye to *"Install anyway"* /
> *"More details → Install anyway"* chunein — ye ek self-signed personal
> app hai, Play Store par nahi hai.

---

## ✨ Features

### Share → direct save (zero taps)
Kisi bhi app se link **Share** karein — LinkVault **bina kisi dialog ke
seedha save** kar leta hai aur background me AI baaki sab kar deta hai.

### On-device AI (fully offline)
- Har link ke niche **2-line summary**
- **AI khud folder banata hai** (e.g. `Tech`) — folder na ho to **khud
  create** kar leta hai
- **Auto tags**
- Rules-first (19 platform rules) + optional local LLM
  (**Qwen3-0.6B**, `.litertlm`) — model app ke saath nahi aata, chahiye to
  `AI_MODEL_SETUP.md` dekhein. Bina model ke bhi sab chalta hai (rules-only).

### AI auto-clean (roz, background me)
- **30+ din se khaali unused folders** delete + khaali orphan folders disk
  se saaf
- **Redundant tags** hataata hai (folder-name duplicates, singular/plural
- **Links/files KABHI delete nahi hote** — hard guarantee
- Status: *Settings → On-Device AI* card me

### Organize
- Categories with **nested subfolders** (3 levels tak) + **Auto-Categorize
  rules** (domain → folder)
- Tags + **pinned** favourites
- **Multi-select** share / delete
- Fast **search** (index-first, ~300 ms open) + folder/type/tag filter chips

### Files, safety, backup
- File import (**copy-only**, ≤ 100 MB) — original kabhi move/delete nahi
- **Dead-link checker** (single / check-all)
- **ZIP backup & restore** (full vault export/import, conflict choice ke
  saath)
- **Auto backup** daily (newest 7 rakhta hai)
- **Home-screen widget** (5 recent links)
- Clipboard URL detect on app open

---

## 🔒 Privacy

- **No account, no server, no analytics, no ads.**
- App INTERNET permission sirf *dead-link check* ke liye use karta hai —
  core features **fully offline** kaam karte hain.
- Data sirf **aapke chosen folder** (SAF) me rehta hai — plain JSON files,
  koi proprietary database nahi. Folder ka backup copy karke le ja sakte
  hain.

---

## 🛠 Build from source

Prerequisites: **JDK 17**, **Android SDK (compileSdk 35)**

```bash
git clone https://github.com/Sachint122/linkVault.git
cd linkVault

# Debug APK + unit tests (87/87)
gradlew assembleDebug testDebugUnitTest

# Release APK (signed — app/keystore.properties + linkvault-release.jks,
# both gitignored; release folder me daalne ke liye)
gradlew assembleRelease
```

Outputs: `app/build/outputs/apk/debug/app-debug.apk`,
`app/build/outputs/apk/release/app-release.apk`

---

## 📚 Documentation

| Doc | Contents |
|---|---|
| [`AI_ARCHITECTURE.md`](AI_ARCHITECTURE.md) | On-device AI pipeline design (LiteRT-LM, queue, protection rules) |
| [`AI_MODEL_SETUP.md`](AI_MODEL_SETUP.md) | Local LLM model kaise daalein (`.litertlm`) |
| [`AI_PROCESSING_FLOW.md`](AI_PROCESSING_FLOW.md) | Link save → analyze → display flow |
| [`AI_TESTING.md`](AI_TESTING.md) | AI test guide |
| [`DATA_SCHEMA.md`](DATA_SCHEMA.md) | JSON schemas (v1), storage layout, safe-write protocol |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Tech stack, layers, SAF strategy, backup/restore |
| [`REQUIREMENTS.md`](REQUIREMENTS.md) | Functional requirements FR-01…FR-08 |
| [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) | Phases 0–5, tasks, exit criteria |
| [`TESTING_STRATEGY.md`](TESTING_STRATEGY.md) | Test layers & critical matrix |
| [`brain/`](brain/) | **Session source of truth:** INDEX, STATUS, DECISIONS, CHANGELOG |

---

## 🧱 Stack

**Java 17** · XML + Material 3 · Fragments + Navigation · MVVM
(ViewModel + LiveData) · manual DI · `org.json` · WorkManager · SAF-only
storage · minSdk 26 / targetSdk 35 · optional **LiteRT-LM 0.19.1**
(one Kotlin interop file; app core stays Java).

**Hard rules:** no backend / login / cloud / database (SQLite/Room/etc.);
JSON files in a user-selected folder are the only source of truth; imports
are copy-only; all core features work offline; ZIP backup only, no silent
overwrites.

---

## 📜 Changelog

See [`brain/CHANGELOG.md`](brain/CHANGELOG.md) for the full history.

**v1.1.0 (2026-11-18)** — on-device offline AI (summary/folder/tags),
direct share save (no dialog), AI auto-clean (links never deleted),
2-line summary in list, Settings AI status card, plus all Phase 1–3 +
feature batches 1–10 (search, files, backup, widget, dead-link check, …).

**v1.0.0** — MVP: share capture, categories, search, file import,
backup/restore, fast index load.
