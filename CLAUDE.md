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
  └─ DataStore: isSetupDone?
       No ──► SetupScreen (set master password → Google sign-in optional)
       Yes ──► UnlockScreen (enter master password)
                └─► HomeScreen
                      ├─ Files tab (list Drive vault files, upload FAB)
                      └─ Vault Info / Settings
```

## Password Strategy
- Master password **never stored**; only PBKDF2-derived hash+salt (Base64 in DataStore) for verification
- Password kept in `AuthViewModel` (Activity-scoped, cleared on process death → forces re-unlock)
- ViewModel cleared → user redirected to UnlockScreen

## Google Drive Setup (user must do manually)
1. Google Cloud Console → create project
2. Enable Google Drive API
3. OAuth consent screen → app name "darkVault", scope `drive.file`
4. OAuth client: Android, package `com.darkvault.app`, SHA-1 of signing key
5. No `google-services.json` required for this flow

## Drive Operations
All via Drive REST API v3 with OkHttp + Bearer token from `GoogleAuthUtil.getToken()`:
- `ensureVaultFolder()` — find or create `darkVault/` folder
- `listFiles(folderId)` — list `.vault` files with appProperties
- `uploadEncryptedFile(name, bytes, folderId)` — multipart upload
- `downloadFile(fileId)` — download encrypted bytes
- `deleteFile(fileId)` — delete

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
- **Biometric unlock (persistent)**: Android Keystore key + `BiometricPrompt.CryptoObject` pattern; persists across process restarts
- **Settings screen**: Biometric toggle (enroll/revoke), auto-lock timer, image/video preview toggles
- **Auto-lock timer**: Locks vault after N minutes in background (via `onPause`/`onResume`)
- **appProperties truncation**: All values capped at 100 chars to stay within Drive's 124-byte limit

## Known Architectural Debt (plan before adding user data)
- **Envelope encryption / re-keying**: Current format derives the data key directly from password+salt per file (no DEK/KEK split). Password change = re-encrypt all files. Fix: wrap a random per-vault DEK with the PBKDF2 KEK. This is a breaking format change.
- **Recovery key**: Forgetting master password = permanent data loss. Fix requires envelope encryption first, then offer a recovery key (high-entropy, shown once at setup) that can also decrypt the DEK.
- **Chunked streaming AEAD**: Single-shot AES-GCM loads whole file into RAM; will OOM on video files. Fix: stream in fixed-size chunks, each with own nonce. Breaking format change.
- **Integrity manifest**: No signed index of vault contents; tampering/corruption on Drive goes undetected.
- **Conflict resolution**: No `updatedAt` version field; last-write-wins is undefined when two devices write concurrently.
- **Rate limiting**: No backoff/retry on Drive API 403/429 responses.

## Ideas & Future Notes
- Offline mode: track upload queue in Room, retry on next launch
- File versioning: keep previous `.vault` file as `<name>.vault.bak` on Drive
- Video thumbnail preview: write to temp file, use `MediaMetadataRetriever`, delete immediately
- Shared/widget: quick upload from share sheet intent
- Wear OS companion: unlock via watch
- Export / local backup: pull an unencrypted or re-encrypted local archive without going through Drive

---
*Update this file whenever a major decision is made or a new feature is scoped.*
