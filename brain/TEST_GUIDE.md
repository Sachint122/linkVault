# LinkVault — Feature Test Guide (Batches 1–10)

**APK:** `releases/linkVault-debug.apk` (install over existing app — data safe)
**Device:** your Realme RMX3660. Har feature ke baad "PASS ✅ / FAIL ❌ + kya hua"
bata do. FAIL pe code fix karke naya APK dunga.

> Install: `adb install -r releases\linkVault-debug.apk`
> ya phone par existing LinkVault ke upar direct install karo.

---

## Batch 1 — Clipboard offer + share detect

1. Chrome/WhatsApp me koi URL copy karo (e.g. `https://example.com`).
2. LinkVault kholo (Home screen).
3. **Expected:** "Clipboard me URL mili — save karein?" type offer dialog/dikhawata
   hai (ya notification-like prompt). Save pe Review screen khulta hai.
4. Share se test: kisi page ka **Share → LinkVault** — Review screen prefilled
   title+URL ke saath khulta hai.
5. **Release signing check:** `apksigner verify --print-certs releases\linkVault-debug.apk`
   (optional — debug APK self-signed hota hai, ye batch mainly release build
   ke liye tha).

## Batch 2 — Dead-link check

1. Home pe kisi link ke row menu (⋮) me **Check link** option tap karo.
2. **Expected:** thoda rukke status badge dikhta hai — 🟢 `200` ya 🔴 `404`
   etc. (network chahiye).
3. Galat URL save karo (e.g. `https://example.invalid/xyz`) → check → red badge.
4. Settings → **Check all links** button.
5. **Expected:** progress text chalta hai, end me "X checked" jaisa message.
   Har link pe status update.

## Batch 3 — Tag chips + recent searches

1. Kuch links ko alag-alag tags do (Save screen me `tag1, tag2` type karo).
2. Home pe Tags section me chips dikhte hain — chip tap → sirf wahi tag ke
   links filter. "All Tags" se clear.
3. Search box tap karo → **Expected:** pehle ki searches dropdown me dikhti
   hain. Naya search type karo → app band → dobara kholo → search ab bhi
   dropdown me (survives restart).

## Batch 4 — Auto-categorize rules

1. Settings → Auto-Categorize card → **Add rule**.
2. Step 1: domain daalo (e.g. `github.com`) → OK. Step 2: category chuno
   (e.g. "Tech") → OK.
3. Ab `https://github.com/sachint122` jaisa naya link **bina folder chune**
   save karo (folder = Uncategorized rehne do).
4. **Expected:** link apne aap "Tech" me save. Home pe Tech filter me dikhega.
5. Rule list me rule dikhta hai; **delete** se rule hat jaata hai.

## Batch 5 — Export CSV/text + multi-select

1. Settings → Export card → **Export CSV** → Save file location chuno.
   Excel/Notepad me kholo: header `title,url,description,category,tags,createdAt`
   + sahi rows. **Export Text** bhi aise hi.
2. Home pe kisi row ko **long-press** karo → top me selection bar aayi
   (X selected, Share, Delete, Cancel). FAB chhup gaya.
3. 2–3 rows select → **Share** → share sheet me titles+URLs text ke roop me.
4. **Delete** → confirm → sab delete, bar hat jaaye.
5. **Cancel** → selection khatam, kuch delete nahi hua.

## Batch 6 — App lock (PIN + biometric)

1. Settings → Lock card → **Set PIN** → 4–12 digit PIN daalo (do baar).
2. App band karo (recent se bhi hatao) → dobara kholo.
3. **Expected:** PIN screen. Galat PIN → error. Sahi PIN → app khula.
4. **Biometric button** dikh raha hai? (fingerprint set hone par) → fingerprint
   se unlock.
5. App open rehte Home se Share ke bahar jao aur wapas aao (ya app background
   se wapas) → **Expected:** phir se lock (onStop pe relock).
6. Back daba kar lock screen chhodne ki koshish → **na jaye**.
7. Settings → **Disable lock** → restart → no lock.

## Batch 7 — Auto backup

1. Settings → Auto Backup card → status text dikh raha hai ("off" initially).
2. **Toggle ON** → status ON. **Run backup now** tap karo.
3. File manager me `/sdcard/Link/backups/` → `linkvault_auto_*.zip` bana hai.
4. 7 se zyada backups hone pe purane delete (manual run 7 baar karke check —
   optional).
5. App band/restart → backup still ON (pref survive), status me "last run"
   dikhe.

## Batch 8 — Pagination (long list)

1. 60+ links save karo (script se bhi kar sakte ho — bata dena).
2. **Expected:** app khulte hi ~50 dikhte hain, scroll karte hi 10-10 aur
   judte hain (jump nahi — smooth load).
3. Search/filter pe window reset → filtered results theek.

## Batch 9 — Nested subcategories

1. Categories screen → koi category banao (e.g. "Work").
2. "Work" row ke menu (⋮) → **Add subcategory** → naam (e.g. "Office") → OK.
   (Menu me ye option sirf depth < 3 par hai.)
3. **Expected:** list me parent seedha upar, neeche `└ Office` indented.
4. Home chips me bhi child chip `└ Office` prefix ke saath.
5. "Office" me link save karo → ab **"Work" chip tap** → **Expected:** parent
   filter me child ke links bhi dikhte hain (descendant expansion).
6. "Work" delete karo → confirm. **Expected:** "Office" gayab nahi — uska
   parent ab root level (re-parented), uske links safe.
7. Depth check: Office ke andar bhi subcategory bana ke dekho (max 3 levels —
   4th pe error message).

## Batch 10 — Home-screen widget

1. Home screen par long-press → **Widgets** → LinkVault widget dhundho →
   add karo.
2. **Expected:** dark widget header "LinkVault" + recent links (title + URL,
   max 5).
3. Row tap → browser me URL khula. Header tap → LinkVault app khula.
4. Naya link save karo → widget **apne aap update** (ya app kholte hi).
5. Link delete karo → widget se bhi gayab.

---

## Report format (bas itna bhejo)

```
B1: PASS/FAIL — ...
B2: PASS/FAIL — ...
...
B10: PASS/FAIL — ...
```

Sab PASS → main sab commits **GitHub push** kar dunga.
