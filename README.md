# LinkVault

**100% offline link vault for Android** — share se seedha save, on-device AI khud folder/summary/tags banata hai, floating AI Writing Assistant sidebar har app ke upar kaam karta hai, aur roz khud app clean karta hai. No accounts, no cloud, no ads. Sab kuch **aapke apne folder** me plain JSON files ke roop me.

| | |
|---|---|
| **Version** | **1.2.0** (versionCode 3) |
| **Requires** | Android 8.0+ (API 26) |
| **APK size** | ~56 MB (model bundled **nahi** hai) |
| **Internet** | Optional — sirf dead-link check ke liye; baaki sab fully offline |

---

## ⬇️ Download & Install

**1. Latest APK download karein:**
**[LinkVault-v1.2.0.apk](https://github.com/Sachint122/linkVault/raw/refs/heads/main/releases/LinkVault-v1.2.0.apk)**

**2. Install karein:**
- APK par tap karein → Android poochega *"Install unknown apps"* → apne browser/file manager ke liye **Allow** karein
- **Install** → app kholein
- Pehli baar ek **storage folder select** karna hoga (onboarding) — bas ek baar; uske baad sab automatic

**3. Future Updates:** naya version ka APK isi par tap karke install karein (data safe rehta hai — same signature, uninstall ki zarurat nahi).

> **Note:** phone "Play Protect" block dikhaaye to *"Install anyway"* / *"More details → Install anyway"* chunein — ye ek self-signed personal app hai, Play Store par nahi hai.

---

## ✨ Key Features

### 🚀 Share → Direct Save (Zero Taps)
Kisi bhi app se link **Share** karein — LinkVault **bina kisi dialog ke seedha save** kar leta hai aur background me AI baaki sab kar deta hai.

### 🤖 Floating AI Writing Assistant Sidebar (System-Wide)
- **Har App Ke Upar Kaam Karta Hai:** System-wide edge pill handle — kisi bhi app me inward slide karein aur writing assistant panel expand ho jata hai.
- **Dynamic Highlight Pill & Glowing Dot:** Background me result complete hote hi pill handle luminous gradient highlight me badal jata hai aur vibrant amber glowing dot appear hota hai.
- **10 Writing Tones:** Professional, Formal, Friendly, Casual, Polite, Confident, Concise, Persuasive, Empathetic, Natural English (with compact `More ∨` toggle).
- **5 Writing Actions:** Rewrite, Improve, Shorten, Expand, Translate.
- **Custom Instructions:** Apne mutabik extra prompt instructions add karein.
- **100% Offline Inference:** Qwen3-0.6B local model ke saath CPU par chalta hai — zero data leaves your device.
- **Android Back-Gesture Safe:** Android 10+ edge exclusion rects use karta hai taaki system back gesture trigger na ho.

### 🧠 On-Device AI Auto-Analysis
- Har link ke niche **2-line auto summary**.
- **Auto-Folders:** AI domain aur context ke hisaab se automatic folder banata hai (e.g. `Tech`, `Movies`).
- **Auto-Tags:** Relevant search tags automatically attach hote hain.
- Rules-first (19 platform rules) + optional local LLM (**Qwen3-0.6B**, `.litertlm`). Bina model ke bhi sab chalta hai (rules-only).

### 🧹 Daily AI Auto-Clean
- **30+ din se khaali unused folders** delete + khaali orphan folders disk se saaf.
- **Redundant tags** hataata hai (folder-name duplicates, singular/plural duplicates).
- **Links/files KABHI delete nahi hote** — hard zero-loss guarantee.

### 🎨 Unified Modern Cyber UI/UX
- **Palette:** Dark Navy & Charcoal (`#000B10`, `#091720`) with vibrant Cyan & Teal accents (`#00F0FF`, `#00E5FF`). Strictly zero purple/violet.
- **Header & Search:** Cyber capsule search bar with instant filter sliders.
- **Instant Type Filters:** `✓ All`, `🔗 Links`, `📁 Files` chips for real-time item type switching.
- **Smart Cards:** GitHub cards par official Octocat logo, circular chevron expander, cyan URLs, badges, aur 3-dots action menu.
- **Navigation:** Floating cyan squircle FAB (`+`) aur glowing bottom navigation bar (`Home`, `Folders`, `Tags`, `Settings`).

### 📦 Files, Safety & Local Backup
- File import (**copy-only**, ≤ 100 MB) — original file kabhi move ya delete nahi hoti.
- **Dead-link checker** (single / check-all).
- **ZIP backup & restore** (full vault export/import, conflict choice ke saath).
- **Auto backup** daily (newest 7 backups secure rakhta hai).
- **Home-screen widget** (5 recent links).

---

## ⚙️ Optional Local AI Model Setup

App bina model ke bhi 100% kaam karti hai (rule-based auto-categorize & tagging). Agar aapko deep summarization aur writing assistant ke liye local LLM chahiye:

1. **Model Format:** LiteRT-LM compatible **`Qwen3-0.6B.litertlm`** (~613 MB). *(GGUF format supported nahi hai)*.
2. **Push to device storage via ADB:**
   ```bash
   adb push Qwen3-0.6B.litertlm /data/local/tmp/ai_model.litertlm
   adb shell run-as com.linkvault.app cp /data/local/tmp/ai_model.litertlm files/ai_model.litertlm
   ```
3. App restart karein — Settings me **On-Device AI** status `Ready (model active)` dikhayega.

---

## 🔒 Privacy & Data Guarantee

- **No account, no server, no telemetry, no ads.**
- App INTERNET permission sirf *dead-link check* ke liye use karta hai — core features **fully offline** kaam karte hain.
- Data sirf **aapke chosen SAF folder** me rehta hai — plain JSON files, koi proprietary database nahi.

---

## 🛠 Build from Source

Prerequisites: **JDK 17**, **Android SDK (compileSdk 35)**

```bash
git clone https://github.com/Sachint122/linkVault.git
cd linkVault

# Run 122 unit tests & build debug APK
gradlew assembleDebug testDebugUnitTest

# Assemble Release APK (requires keystore.properties)
gradlew assembleRelease
```

---

## 📚 Project Documentation

Detailed project architecture and specifications are documented inside [`brain/`](brain/):

| Document | Purpose |
|---|---|
| [`brain/INDEX.md`](brain/INDEX.md) | Documentation index & working protocol |
| [`brain/STATUS.md`](brain/STATUS.md) | Real-time project status & completed phases |
| [`brain/DECISIONS.md`](brain/DECISIONS.md) | Technical decisions log (D-01 to D-20) |
| [`brain/CHANGELOG.md`](brain/CHANGELOG.md) | Complete version history & release notes |
| [`brain/AI_SIDEBAR.md`](brain/AI_SIDEBAR.md) | AI Writing Assistant Sidebar specifications |
| [`brain/DATA_SCHEMA.md`](brain/DATA_SCHEMA.md) | JSON storage schemas & safe-write protocol |
| [`brain/ARCHITECTURE.md`](brain/ARCHITECTURE.md) | Layered architecture, SAF engine & DI design |
| [`brain/REQUIREMENTS.md`](brain/REQUIREMENTS.md) | Functional requirements & acceptance criteria |
| [`brain/TEST_GUIDE.md`](brain/TEST_GUIDE.md) | Device verification & test automation guide |

---

## 🧱 Technology Stack

**Java 17** · XML + Material 3 · Fragments + Navigation Component · MVVM (ViewModel + LiveData) · Manual DI (`AppContainer`) · `org.json` · WorkManager · SAF-only JSON storage · minSdk 26 / targetSdk 35 · LiteRT-LM 0.19.1 (Kotlin interop).
