# darkVault — Project Context

## What This Is
darkVault is a personal encrypted backup client for Google Drive. Files are encrypted locally with AES-256-GCM before upload; Google never sees plaintext. Future phase: built-in encrypted password manager synced via the same Drive folder.

## Core Requirements
- **Encryption first**: AES-256-GCM + PBKDF2WithHmacSHA256 key derivation, password never leaves device
- **Google Drive as storage backend**: files stored in a `darkVault/` folder, encrypted as `.vault` files with original name/type in Drive `appProperties`
- **No cloud dependency besides Drive**: zero external servers, no account registration
- **Privacy**: even Google cannot read stored files
- **Cross-platform future**: Linux desktop client planned (design crypto layer to be portable)
- **Long-term stable**: full feature set delivered at once, no continuous update cycle — ship it complete, use it privately

## Authors & Git
- Primary author: `Parthiv Kumar Nikku <develop@parthiv.sec>`
- Secondary/GitHub: `scap3sh4rk@gmail.com`
- Remote: `https://github.com/parthivkumarnikku/darkVault.git`
- **Never** include Claude/Anthropic in commit messages or commit any `.claude/`, `MEMORY.md`, or AI-tool config files

## Tech Stack
| Layer | Choice | Reason |
|-------|--------|--------|
| UI | Jetpack Compose + Material Design 3 | Material You, forward-compatible |
| Theme | Dark M3 with Electric Cyan (`#00D4FF`) seed | Futuristic + Material |
| Encryption | `javax.crypto` AES-256-GCM, PBKDF2WithHmacSHA256 | Built-in, no external dep |
| Google Auth | `play-services-auth` GoogleSignIn | Drive scope at sign-in |
| Drive API | OkHttp REST calls + Gson | Lightweight, no Apache HTTP conflicts |
| Navigation | `navigation-compose` | Standard Compose nav |
| State | DataStore Preferences | password hash+salt, first-run flag |
| ViewModels | `lifecycle-viewmodel-compose` | MVVM |
| Coroutines | `kotlinx.coroutines` | All async ops |

## Application ID & Package
- `applicationId = "com.darkvault.app"`
- `namespace = "com.darkvault.app"`
- Package root: `com.darkvault.app`

## File Encryption Format
```
[16 bytes salt][12 bytes GCM IV][AES-256-GCM ciphertext + 16-byte auth tag]
```
- Drive file name: `<original_name>.vault` (e.g. `photo.jpg.vault`)
- Drive `appProperties`: `{ "originalName": "photo.jpg", "originalMimeType": "image/jpeg" }`
- Key derivation: PBKDF2WithHmacSHA256, 100 000 iterations, 256-bit key

## Screens & Navigation
```
App Start
  └─ AuthViewModel.initializeAuth()
       No Google account ──► SignInScreen (Google OAuth)
       Account found ──► CheckingVault (spinner) ──► SetupScreen or UnlockScreen

  UnlockScreen (enter master password; biometric offered only after password entered this session)
    └─► HomeScreen
          ├─ Files list (vault files, folders, recents, search/filter/sort)
          ├─ More (⋮) menu → Export backup | View Trash | Settings
          └─ FAB → Upload files / Upload folder

  SettingsScreen ← from HomeScreen More menu
  TrashScreen    ← from HomeScreen More menu ("View trash")
```

## Password Strategy
- Master password **never stored**; only PBKDF2-derived hash+salt (Base64 in DataStore) for verification
- Password kept in `AuthViewModel` (Activity-scoped, cleared on process death → forces re-unlock)
- `_sessionPasswordEntered` (in-memory flag): must type password at least once per process lifetime before biometric is offered

## Settings Screen Sections
| Section | Contents |
|---------|----------|
| Security | Biometric toggle, Auto-lock timer, Lock Now |
| Password | Change password, Rotate recovery key |
| Account | Signed-in email (read-only), Switch account (requires password confirm) |
| Previews | Image preview toggle, Video preview toggle |

## Google Drive Setup (user must do manually)
1. Google Cloud Console → create project
2. Enable Google Drive API
3. OAuth consent screen → app name "darkVault", scope `drive.file`
4. OAuth client: Android, package `com.darkvault.app`, SHA-1 of signing key
5. No `google-services.json` required for this flow

## Drive Operations
All via Drive REST API v3 with OkHttp + Bearer token from `GoogleAuthUtil.getToken()`:
- `ensureVaultFolder()` — find or create `darkVault/` folder
- `listItems(folderId)` — list `.vault` files + sub-folders (trashed=false)
- `listTrashedVaultFiles(folderId)` — list trashed items in vault folder (trashed=true)
- `uploadEncryptedFile(name, bytes, folderId)` — multipart upload
- `downloadFile(fileId)` — download encrypted bytes
- `trashFile(fileId)` — soft delete (PATCH trashed:true)
- `restoreFile(fileId)` — restore from trash (PATCH trashed:false)
- `deleteFile(fileId)` — permanent delete

## Phase 2: Password Manager (planned, not yet implemented)
- Separate encrypted JSON blob: `passwords.vault` in the same Drive folder
- Structure: `{ "entries": [{ "id", "title", "username", "password", "url", "notes", "updatedAt" }] }`
- UI: bottom nav tab, CRUD for entries, copy-to-clipboard, auto-fill intent

## Linux Client (planned)
- Pure Kotlin (kotlinx) or Rust CLI/GUI
- Same encryption format (AES-256-GCM, same header structure) → files decryptable on Linux
- Same Drive API via REST, OAuth Device Flow for auth

## Device Compatibility
- `minSdk = 26` (Android 8.0 Oreo) — covers 98%+ of active devices
- `targetSdk = 36` — latest
- Use Material You dynamic color with static fallback for pre-Android 12 devices
- Edge-to-edge UI (`enableEdgeToEdge()`)

## UI Principles (Material Design 3 + Futuristic Dark)
- Base: M3 dark scheme with custom seed color `#00D4FF` (Electric Cyan)
- Typography: M3 type scale with increased letter spacing for headings
- No emoji in UI unless explicitly requested
- Animations: subtle scale + fade (M3 motion tokens)
- All screens: full-bleed dark background, floating cards
- File type icons: material symbols by extension
- Empty state: illustrated with vector drawable

## Implemented Features (v2)
- **Folder navigation**: Browse sub-folders inside `darkVault/` with breadcrumb trail
- **Background uploads**: `UploadForegroundService` with progress notifications; cancel support
- **Duplicate detection**: Checks `appProperties.originalName` before uploading
- **Resumable uploads**: Drive resumable upload protocol (chunked, survives interruption)
- **Storage quota**: Shows vault usage + Drive free space via Drive `about` API
- **Image preview**: In-memory decrypt + `BitmapFactory` decode, never writes plaintext to disk
- **Search / filter / sort**: Real-time search, `FilterType` chips, `SortOrder` dropdown
- **Batch operations**: Multi-select mode for bulk delete
- **Biometric unlock**: Android Keystore key + `BiometricPrompt.CryptoObject` pattern; persists across process restarts
- **Settings screen**: Biometric toggle (enroll/revoke), auto-lock timer, image/video preview toggles
- **Auto-lock timer**: Locks vault after N minutes in background (via `onPause`/`onResume`)
- **appProperties truncation**: All values capped at 100 chars to stay within Drive's 124-byte limit

## Implemented Features (v3)

### Security
- **FLAG_SECURE**: Activity window flag prevents screenshots/recents-screen leakage
- **Constant-time password verify**: `MessageDigest.isEqual()` prevents timing side-channels
- **Key zeroing**: All key bytes, IVs, and salts zeroed via `Arrays.fill()` after use; PBKDF2 spec cleared with `clearPassword()`
- **Brute-force protection**: Exponential backoff on failed unlock attempts (30s, 60s, 120s… up to 30 min cap); persisted in DataStore across restarts

### Envelope Encryption (DEK/KEK)
- **VaultKeyManager**: Generates/wraps/unwraps 256-bit DEK using AES-256-GCM; formats/parses recovery key as dash-separated hex
- **VaultKeyBundle**: JSON-serializable bundle with `kekSalt`, `dekWrappedByKek`, `dekWrappedByRecovery`; stored as `vault.key` on Drive
- **vault.key on Drive**: First unlock generates DEK + recovery key, wraps both, uploads to Drive; subsequent unlocks download and unwrap
- **Recovery key**: Shown once at setup via `AuthViewModel.recoveryKey` StateFlow; formatted as 8-group hex for offline storage; copy button on display dialog
- **Re-keying ready**: Password change = re-derive KEK, re-wrap DEK, re-upload vault.key — no file re-encryption needed
- **Multi-version decrypt**: Handles v0x00 (legacy), v0x02 (per-file PBKDF2), v0x03 (DEK-based) transparently
- **VaultSession.clearDek()**: Zeros and nulls DEK bytes on lock
- **DEK-aware encrypt/decrypt**: UploadForegroundService uses `encryptWithDek()` when DEK is available; all download/decrypt paths pass DEK to `CryptoManager.decrypt()`

### File Management
- **Duplicate rename**: Files with conflicting names upload as `file (2).ext`, `file (3).ext` etc. instead of being skipped
- **Idempotent uploads**: Each upload job has a `clientId` UUID stored in Drive `appProperties`; retries skip completed jobs
- **Soft delete / trash**: Delete moves files to Drive trash (`PATCH {"trashed":true}`); permanent delete available separately
- **Rate limiting**: Drive API calls retry up to 4× with exponential backoff (1s→2s→4s→…→30s cap) on 429/500/503 errors
- **Recents section**: Top 8 most-recently-modified files shown as horizontal scroll row at vault root
- **Batch download**: Multi-select + download button decrypts and saves all selected files to `Downloads/darkVault-loc/`
- **Export/backup**: Downloads and decrypts all vault files to `Downloads/darkVault-loc/`
- **Last synced indicator**: Shows "just now / N min ago / Nh ago" below filter chips after each refresh

## Implemented Features (v4)

### UI / UX
- **Signed-in account in title**: Google account email shown as subtitle under "darkVault" at vault root (replaced by breadcrumb when in sub-folder)
- **Toolbar trimmed**: HomeScreen top bar shows Search, Sort, Refresh + More (⋮) overflow. More contains: Export backup, View trash, Settings. Account button removed.
- **Image tap-to-preview**: Tapping an image file card opens the in-memory preview dialog immediately; explicit Preview icon button also retained
- **NavGraph-level HomeViewModel**: `HomeViewModel` hoisted to `DarkVaultNavGraph` so state is shared between `HomeScreen` and `TrashScreen`

### Security & Account
- **Two-tier lock model** (v5):
  - **Vault lock** (master password): unlocks DEK from Drive on first use; DEK held in `VaultSession.dek` for the process lifetime; required on fresh launch / after reboot / after manual "Lock Now"
  - **App lock** (biometric): when biometric is enabled and `_sessionPasswordEntered=true`, going to background → `AuthState.AppLocked`; DEK is **retained** in memory; biometric re-gates the UI only — no Drive call needed; sessions persist until process death (reboot)
  - Manual "Lock Now" always clears DEK and `_sessionPasswordEntered` → full vault lock
  - On process restart: `_sessionPasswordEntered=false` → only master password is offered (no biometric until password typed)
  - `suppressNextLock()` suppresses the next `onAppBackground()` lock — called before file/folder picker launches to prevent spurious locks
- **Session timeout**: configurable timer (Never / 30 min / 1 hr / 2 hr / 4 hr / 8 hr / 24 hr / Custom) that forces master password re-entry after the set duration, regardless of biometric; timer starts on password entry, is not reset by biometric unlocks, fires even in foreground; belt-and-suspenders `onAppForeground()` check catches expiry if process was frozen while backgrounded
- **Switch account security check**: Settings → Account → Switch requires master password re-entry (verified against stored hash) before signing out; prevents accidental account switches
- **Recovery key copy**: Copy-to-clipboard button on initial recovery key display dialog (HomeScreen) and on rotated key display (SettingsScreen)
- **Recovery key rotation**: Settings → Password → Rotate: verifies current password, generates new recovery key, re-wraps DEK with new key, re-uploads vault.key with optimistic-concurrency retry; invalidates old recovery key immediately

### File Management
- **View Trash screen** (`TrashScreen.kt`): Dedicated screen listing all trashed vault files. Per-item actions: Restore (untrash) or Delete Permanently. Refreshes list after each action. Reached via HomeScreen More (⋮) → View trash.
- **Downloads directory**: All single-file downloads, batch downloads, and export backup now save decrypted files to `Downloads/darkVault-loc/` using `MediaStore.Downloads` on Android 10+ (no permission needed); falls back to app-private downloads folder on Android 8–9.

## Known Architectural Debt (updated)

The following items are now resolved:
- ~~Envelope encryption / re-keying~~ — Implemented via DEK/KEK split with VaultKeyBundle
- ~~Recovery key~~ — Implemented: shown at setup, copyable, rotatable from Settings
- ~~Rate limiting~~ — Implemented with exponential backoff (1s→30s cap) on 429/500/503
- ~~Auto-lock biometric~~ — Replaced by session-gated two-tier biometric (v4)

Still outstanding:
- **Chunked streaming AEAD**: Single-shot AES-GCM still loads whole file into RAM (OOM on large videos)
- **Integrity manifest**: No signed index of vault contents
- **Conflict resolution**: No `updatedAt` version field per file
- **appProperties size limits**: Values capped at 100 chars but no enforcement on very long paths
- **KDF upgrade**: PBKDF2 at 100k iterations; Argon2id would be stronger but requires external dep
- **AAD on GCM**: File-level additional authenticated data not yet bound to metadata
- **WorkManager background sync**: Upload retry on next launch uses foreground service only
- **Clipboard hygiene**: Needed when Phase 2 password manager is implemented (recovery key copy already uses `ClipboardManager` correctly)
- **Schema versioning for passwords.vault**: Needed for Phase 2
- ~~**DEK reload on biometric re-auth**~~ — Fixed in v5: biometric app lock retains DEK; no Drive call on resume
- **Upload FAB while DEK null** (LOW-003): Brief window after offline unlock where upload uses v0.02 PBKDF2 instead of DEK. Deferred UI fix.
- **VaultKeyBundle manual JSON parser** (LOW-004): Fragile string parser; accepted risk; Gson migration is future hardening.
- **Brute-force lockout counter integrity** (LOW-002): DataStore is plaintext on disk; root access can reset counter. Accepted risk.

### Resolved (Agent 3 patches — 2026-06-22)
- ~~**KEK heap lingering in exception paths**~~ (HIGH-001, MEDIUM-002) — KEK now zeroed in `finally` blocks in `tryUnlockWithVaultKey` and `loadOrCreateDek`; covers all exception paths including `IllegalBlockSizeException`
- ~~**Path traversal via originalName on pre-Q**~~ (HIGH-002) — `saveToDownloads()` now sanitizes filename (strips separators, illegal chars, control chars; truncates to 200 chars; blank fallback)
- ~~**Switch Account dialog has no brute-force lockout**~~ (HIGH-003) — UI-level 5-attempt counter added; button disabled after limit; resets on dialog dismiss
- ~~**data_extraction_rules.xml unconfigured**~~ (HIGH-004) — DataStore excluded from both cloud-backup and device-transfer on Android 12+
- ~~**encBytes not zeroed after upload**~~ (MEDIUM-001) — `finally { Arrays.fill(encBytes, 0) }` wraps the resumable-upload session in UploadForegroundService
- ~~**runBlocking on main thread in biometric launch**~~ (MEDIUM-003) — Replaced with `scope.launch { prefs.getBiometricCredentials() }` in UnlockScreen
- ~~**isMinifyEnabled = false in release**~~ (MEDIUM-004) — R8/ProGuard enabled; `proguard-rules.pro` added; release stubs for debug-only types; Log.d/v stripped in release (LOW-005)
- ~~**Recovery key clipboard never cleared**~~ (MEDIUM-005) — 60-second delayed clear scheduled in both HomeScreen and SettingsScreen copy handlers
- ~~**SecureRandom.generateSeed() misuse**~~ (LOW-001) — All calls replaced with `ByteArray(n).also { SecureRandom().nextBytes(it) }` across VaultKeyManager, CryptoManager, AuthViewModel
- ~~**No explicit network_security_config**~~ (LOW-006) — Created with `cleartextTrafficPermitted="false"` and system CA trust; referenced in AndroidManifest

## Implemented Features (v6)

### Performance (Issue 1)
- **OkHttpClient connection pool**: `DriveApiClient` uses a `companion object { val sharedHttpClient by lazy { ... } }` — single instance shared across all `DriveApiClient` instantiations; prevents per-call thread pool creation and enables HTTP/2 connection reuse
- **Stale-while-revalidate file list**: `HomeViewModel.folderCache` (Map<folderId→List<VaultFile>>) emits cached data immediately on folder navigation; Drive fetch updates in background; no blank screen on re-entry
- **Shimmer skeleton loading**: `ShimmerFileCard()` composable shows 8 animated shimmer placeholders while `uiState == HomeUiState.Loading`; replaces blank/spinner state
- **Pull-to-refresh**: M3 1.4.0 `PullToRefreshBox` wraps file list; swipe-down triggers `homeViewModel.loadFiles(currentAccount)`; indicator visible while refreshing

### File Previews (Issue 2)
- **Image pinch-to-zoom**: `ImagePreviewDialog` uses `rememberTransformableState { zoomChange, panChange, _ -> ... }` + `transformable()` modifier; `detectTapGestures(onDoubleTap)` resets scale to 1× and offset to Zero
- **Text preview**: `TextPreviewDialog` — decrypts file in memory, decodes UTF-8, renders in `SelectionContainer { Text(..., fontFamily = FontFamily.Monospace, fontSize = 12.sp) }` inside scrollable `LazyColumn`; max 512 KB (larger files show "File too large for preview"); triggered by tapping `text/*`, `application/json`, `application/xml`, `application/javascript` files

### Brute-force Protection (Issue 4)
- **5-attempt threshold before lockout**: Attempts 1–4 show "N / 5 incorrect attempts" counter below password field; no lockout until attempt 5
- **Exponential lockout**: Attempt 5 → 30s; attempt 6 → 60s; attempt 7 → 2m; … cap 30 min
- **Live countdown**: `LaunchedEffect(lockoutUntilMs)` with `delay(1000L)` loop updates `timeLeftMs` every second; field auto-re-enables when reaches 0; format "M:SS"
- **Persisted counter**: `failedAttempts` and `lockoutUntilMs` stored in DataStore; survives process kill

### Password Change Auth Flow (Issue 5)
- **Offline guard**: `changePassword()` returns error immediately if `account == null || folderId == null` — no state split
- **Drive-before-hash**: Drive `updateVaultKeyInPlace()` called before `prefs.savePasswordHash()` — no window where hash and vault.key are inconsistent
- **Biometric cleared**: `prefs.clearBiometricCredentials()` called on successful Drive update — stale encrypted password blob invalidated
- **UI flow**: Success → Snackbar + `authViewModel.lockVault()` → NavGraph routes to UnlockScreen

### Conflict Dialog (Issue 3)
- **Editable rename**: `OutlinedTextField` pre-filled with `suggestedName`; user can type any name before confirming; sanitized before send
- **Replace confirmation**: Clicking "Replace existing file" shows a secondary confirmation dialog before sending `ConflictResolution.Replace`
- **RenameAs variant**: `ConflictResolution.RenameAs(newName)` handled in `UploadForegroundService` — user-chosen name respected

### Screenshot Toggle (Issue 6 — Debug Only)
- `DebugPanelScreen` has a toggle with master-password confirmation dialog before enabling
- `PreferencesManager.screenshotEnabled` DataStore key (defaults false)
- `MainActivity.onResume()` reads `screenshotEnabled` and calls `window.clearFlags(FLAG_SECURE)` or `window.addFlags(FLAG_SECURE)` accordingly

### File Management
- **Batch download UI**: Multi-select mode shows Download icon with count badge in TopAppBar; calls `homeViewModel.downloadSelected()` for all non-folder selected items; Snackbar with count on completion
- **Empty state differentiation**: Search/filter active → `EmptySearchState`; vault truly empty → `EmptyVaultState` (distinct illustrated states)
- **Long-press quick actions**: `combinedClickable(onLongClick)` on `VaultFileCard`; bottom sheet shows Download, Info, Rename, Move to Trash
- **File info sheet**: Name, type, size, modified, uploaded, Drive ID shown in `AlertDialog`
- **Rename file**: Long-press → Rename → `OutlinedTextField` dialog → `homeViewModel.renameFile()` → Drive PATCH
- **New Folder via FAB**: FAB dropdown includes "New folder" option; `AlertDialog` with name field → `homeViewModel.createFolder()`
- **Sort by Size**: `SortOrder.SIZE_ASC` / `SIZE_DESC` in enum; handled in `displayItems` combine

### UX
- **Haptic feedback on unlock**: `LaunchedEffect(authState)` fires `view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)` on `AuthState.Home` (API 30+); falls back to `VIRTUAL_KEY`
- **Signed-in email in UnlockScreen**: Email chip shown above password field in vault-lock mode
- **Breadcrumb navigation**: LazyRow breadcrumb below TopAppBar when in sub-folder; supports collapse ellipsis for deep paths

## Ideas & Future Notes
- Offline mode: track upload queue in Room, retry on next launch
- File versioning: keep previous `.vault` file as `<name>.vault.bak` on Drive
- Video preview: decrypt → write to EncryptedFile in app cache → ExoPlayer (delete in finally block)
- Audio preview: same pattern, MediaPlayer
- PDF preview: PdfRenderer with temp file, delete in finally block
- Video thumbnail preview: write to temp file, use `MediaMetadataRetriever`, delete immediately
- Shared/widget: quick upload from share sheet intent
- Wear OS companion: unlock via watch
- Export / local backup: pull an unencrypted or re-encrypted local archive without going through Drive

---
*Update this file whenever a major decision is made or a new feature is scoped.*
