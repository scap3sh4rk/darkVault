# darkVault — Execution Plan

## Agent 1: DevOps Agent
- [x] DeveloperOptionsManager singleton created
- [x] Debug-only DebugPanelScreen added to NavGraph (hidden in release)
- [x] Crypto diagnostics: DEK status, KEK salt, vault.key presence, last wrap/unwrap timestamp
- [x] Network diagnostics: Drive API latency, last error code, retry queue depth
- [x] Session diagnostics: AuthState, sessionPasswordEntered, biometric status, auto-lock timer countdown
- [x] Upload pipeline diagnostics: active jobs, paused jobs, failed jobs, clientId list
- [x] Drive integrity checker: lists all .vault files + vault.key, shows appProperties, checks for orphans
- [x] Simulated fault injection: simulate wrong password, simulate Drive 429, simulate DEK null
- [x] Log viewer: in-app scrollable logcat filtered to "darkVault" tag, copy-to-clipboard
- [x] All developer options gated behind BuildConfig.DEBUG — zero impact on release build

## Agent 2: Security Audit Agent
- [ ] Audit report written to `audit/SECURITY_AUDIT.md`
- [ ] Workflow bugs identified and listed with file+line references
- [ ] Security bugs identified and listed with CVSS score estimate
- [ ] DEK/KEK flow traced end-to-end for logic errors
- [ ] Brute-force lockout logic verified
- [ ] vault.key optimistic concurrency race condition checked
- [ ] FLAG_SECURE verified on all Activities
- [ ] Clipboard hygiene checked (recovery key copy)
- [ ] Intent/export path traversal checked
- [ ] Drive token refresh flow checked for race conditions
- [ ] Upload cancel/pause state machine audited
- [ ] Trash/restore idempotency checked
- [ ] No plaintext written to disk at any point verified
- [ ] All findings written to `audit/SECURITY_AUDIT.md` with priority: CRITICAL / HIGH / MEDIUM / LOW

## Agent 3: Patch Agent
- [ ] Every CRITICAL finding patched
- [ ] Every HIGH finding patched
- [ ] Every MEDIUM finding patched
- [ ] LOW findings patched or documented as accepted risk
- [ ] Patch notes written to `audit/PATCH_NOTES.md` with finding ID cross-reference
- [ ] No new TODOs introduced
- [ ] Unit tests added for every patched crypto/auth path (JUnit4 + MockK)

## Agent 4: Review Agent
- [ ] All patches re-verified against original audit findings
- [ ] No regressions in existing implemented features (per CLAUDE.md v4/v5)
- [ ] Build compiles clean: `./gradlew assembleDebug` exits 0
- [ ] All unit tests pass: `./gradlew testDebugUnitTest` exits 0
- [ ] Review report written to `audit/REVIEW_REPORT.md`
- [ ] CLAUDE.md updated with resolved architectural debt items
- [ ] plan.md fully checked off

## UI/UX Tasks (Post-Backend — DO NOT touch until Review Agent completes)
- [ ] UnlockScreen: show signed-in email above password field
- [ ] Duplicate file conflict dialog: Rename / Replace / Skip options
- [ ] Batch download: multi-select across folders, select-all, download button
- [ ] Image/video thumbnails in file list (in-memory, no plaintext to disk)
- [ ] Upload cancel + pause working (button + notification actions)
- [ ] Multi-file upload notification: "Uploading 2/5 files" live counter
- [ ] Folder navigation breadcrumb back-button fix
- [ ] Multiple view layouts: List / Grid / Large Grid (user-toggleable, persisted in DataStore)
- [ ] Light/Dark mode toggle (persisted in DataStore, respects system default)
- [ ] Settings screen: consolidate ALL app features into Settings (incl. Export Local Backup)
