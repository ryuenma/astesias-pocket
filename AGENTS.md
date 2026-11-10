# Astesia's Pocket — agent entry file

Android personal finance app (Kotlin/Compose). Offline-first, multi-device sync.

- **Repo:** `C:/Users/ryuen/Documents/Projects/AstesiaPocket-app`
- **Notes/research:** `C:/Users/ryuen/Documents/Projects/AstesiaPocket`
- **Queue:** `AstesiaPocket/WORKLIST.md` (no kanban board exists for this project — don't look for one)
- **Stack:** Kotlin, Jetpack Compose, Room, Hilt, WorkManager
- **Min SDK:** 28 (Android 9)

## Standing rules

1. **Idempotency needs two halves** — app check + DB unique constraint (PocketBase lesson)
2. **Tombstone terminal states** (DECLINED/CANCELLED) — never delete
3. **OnConflictStrategy.IGNORE** + guarded UPDATE, not REPLACE
4. **One fix/commit** — never bundle
5. **Live device QC** required for UI changes

## Relevant skills
- `astesia-pocket-gotchas` — repo-specific gotchas
- `compose-*` (6) — Compose UI patterns
- `kotlin-*` (5) — Kotlin patterns
- `idempotency-needs-two-halves` — sync/import replay protection
- `offline-first-sync-architecture` — multi-device sync
- `compose-layout-correctness` — layout crash prevention
- `android-device-ops` — device testing

## Gotchas
- Compose reports [0,0][0,0] bounds on POCO F1 (edge-to-edge, bottom 219px short) — verify by tapping, not dumps
- `windowSoftInputMode=adjustResize` — window resizes, primary action moves up above keyboard
- Paparazzi produces NO images for EditTransactionScreen — device walk only
- vision_analyze returns intermittent Gemini HTTP 503 — tolerate failed frames