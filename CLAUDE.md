# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

darkVault is an Android app (Kotlin + Jetpack Compose) that provides zero-knowledge encrypted file storage backed by Google Drive. Files are encrypted on-device with AES-256-GCM before upload; Google never sees plaintext.

## Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Unit tests (JVM only, no device needed)
./gradlew test

# Single test class
./gradlew test --tests "com.darkvault.app.KeyZeroingTest"

# Lint
./gradlew lint

# Instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

## Architecture

Single-activity app. `MainActivity` hosts `DarkVaultNavGraph`, which is driven entirely by `AuthState` from `AuthViewModel`. Navigation is reactive: when `authState` changes, the nav graph pops to the matching route.

**Auth state machine** (`AuthViewModel`):
```
Init → SignIn → CheckingVault → Setup (new user) → Home
                              → Unlock (returning user) → Home
                AppLocked (biometric gate; DEK still in memory) → Home
                NeedsConsent (Drive scope not granted) → ...
```

**In-memory session** (`VaultSession` singleton):
- Holds the DEK (`dek: ByteArray?`), master password, and Google account after unlock
- Zeroed on lock/sign-out via `clearDek()` — keys are never persisted to disk
- `UploadForegroundService` reads credentials from here (no disk storage)

**Encryption layers** (read before touching crypto code):

1. **DEK** (Data Encryption Key) — a random 256-bit AES key, generated once at setup
2. **KEK** (Key Encryption Key) — derived from the master password via PBKDF2-SHA256 (100k iterations), used only to wrap/unwrap the DEK
3. **vault.key** on Drive — JSON file (`VaultKeyBundle`) holding the DEK wrapped twice: once with the KEK (password path) and once with the recovery key. See `VaultKeyManager` for the 60-byte wrapped blob format.
4. **Vault files** — each file is gzip-compressed then AES-256-GCM encrypted using the DEK (`VERSION_DEK_GCM = 0x03`). A version byte is written as the first byte; `CryptoManager.decrypt()` dispatches on it and handles two legacy formats.

**File format versions** in `CryptoManager`:
- `0x03` (current): DEK-based, GZIP + AES-GCM — use `encryptWithDek()` for all new uploads
- `0x02` (legacy): per-file key derivation from password, GZIP + AES-GCM
- `0x00`-`0x01` (legacy): no version byte, salt is the raw first bytes

**Key packages:**
- `crypto/` — `CryptoManager` (file encrypt/decrypt), `VaultKeyManager` (DEK wrap/unwrap), `BiometricKeyManager` (Android Keystore key for biometric), `BiometricHelper`
- `drive/` — `DriveApiClient`: raw OkHttp calls against Drive REST API v3 with exponential-backoff retry. `getStorageQuotaOnly(knownVaultBytes)` avoids a redundant `listItems` call on the home screen.
- `data/` — `PreferencesManager`: DataStore-backed persistence for auth state, biometric IV/ciphertext, vault folder ID, settings, cache cap, and the offline vault key cache (`cached_kek_salt` / `cached_wrapped_dek`). See `cacheVaultKeyLocally()` / `getCachedVaultKey()`.
- `cache/` — three-tier cache layer:
  - `EncryptedFileCache` — session-scoped in-memory LRU (64 MB) of encrypted `ByteArray` values, keyed by `(fileId, modifiedTime)`. Cleared when `VaultSession.clearDek()` is called.
  - `LocalVaultCache` — persistent encrypted-file disk cache (`filesDir/vault_cache/`). Index is a plain JSON file (no filenames — only opaque IDs, sizes, timestamps). Per-file encrypted metadata sidecar. LRU eviction by `lastAccessedMs`, respecting `isPinned`. `evict(fileId)` removes a single entry (called on permanent delete). Upload-staged entries initially have `modifiedTime=""` and adopt the real Drive timestamp on first access rather than being evicted.
  - `FolderMetadataStore` — encrypted folder listing cache (`filesDir/folder_meta/`). Enables stale-while-revalidate on cold start: decrypted listing emitted instantly before Drive refresh. `allCachedFiles(dek)` aggregates every cached folder listing — used to build the offline file index.
- `service/` — `UploadForegroundService` + `UploadState` + `ReadyToUploadJob`: two-stage parallel pipeline. **N=3 encryption workers** (`Dispatchers.Default`) write to `cacheDir/encrypt_staging/<jobId>.vault`; **M=2 upload workers** (`Dispatchers.IO`) consume `ReadyToUploadJob` from a `Channel`. Thumbnail companions (scaled JPEG, re-encrypted with DEK) are generated and uploaded first so their Drive ID can be embedded in the main file's `appProperties.thumbnailId`.
- `viewmodel/` — `AuthViewModel` (auth state machine + lock/session timers + `isOffline` StateFlow), `HomeViewModel` (file listing, download, delete, trash, `clearLocalCache()`, `setOfflinePinned()`). `HomeViewModel` exposes `isOffline` and `offlineFiles` StateFlows; `refreshOfflineFiles()` rebuilds `offlineFiles` from `LocalVaultCache.pinnedFileIds()` + `FolderMetadataStore.allCachedFiles()`. `resetInMemoryState()` clears in-memory state only (called on `CheckingVault`); `clearDriveState()` additionally wipes disk caches (called only on sign-out / account switch).
- `model/` — `VaultKeyBundle`, `VaultFile` (now has `thumbnailFileId: String?`), `SortOrder`

**Offline unlock path:** After every successful online unlock, `AuthViewModel.tryUnlockWithVaultKey()` calls `prefs.cacheVaultKeyLocally(kekSalt, dekWrappedByKek)` — two DataStore keys (`cached_kek_salt`, `cached_wrapped_dek`) store only the *wrapped* DEK (ciphertext). On offline unlock the local password hash passes first (`CryptoManager.verifyPassword`), then `getCachedVaultKey()` retrieves the bundle, re-derives the KEK from the password, and calls `VaultKeyManager.unwrapDek()` to restore the DEK into `VaultSession` without any Drive call. The cached blob is pure ciphertext — the master password is still required to unlock it.

**Biometric unlock path:** `BiometricKeyManager` stores an AES key in Android Keystore gated by biometric auth. On enrollment, the master password is AES-GCM encrypted with that key and the ciphertext + IV stored in DataStore. On unlock, the biometric cipher decrypts the stored ciphertext to recover the password in-memory — the Keystore key is invalidated if new biometrics are enrolled.

**NFC unlock path:** `NfcTagManager` reads a tag payload and passes it as the password to the normal unlock flow. Enabled/disabled via `PreferencesManager.nfcEnabled`. NFC counts as a quick-unlock method alongside biometric for the background lock decision.

**Lock modes:**
- *App-locked* (`AuthState.AppLocked`): background lock when biometric or NFC is enrolled — DEK stays in memory, only the UI is gated. Biometric re-auth is instant (no Drive call).
- *Vault-locked* (`AuthState.Unlock`): full lock — DEK is zeroed, password required, Drive call (or offline cached bundle) needed to unwrap DEK again.

**Lock trigger flow** (`MainActivity` → `AuthViewModel`):
- `onStop()` → `onAppBackground()`: if `_sessionPasswordEntered && (biometricEnabled || nfcEnabled)` → immediate `AppLocked`. Otherwise starts `autoLockJob` timer for a full vault lock after `autoLockMinutes`.
- `onStart()` → `onAppForeground()`: cancels `autoLockJob`; if session has already expired while frozen, calls `lockSessionExpired()` immediately.
- `_suppressNextLock` (private boolean): set before launching system file/folder pickers so `onStop` doesn't lock mid-picker. Cleared unconditionally on the next `onAppBackground()` call.
- `biometricAutoLaunch` (StateFlow): set `true` when transitioning to `AppLocked` if biometric is available — `UnlockScreen` observes this to auto-launch the biometric prompt without a button tap.

## Local storage layout

```
filesDir/
  vault_cache/          # LocalVaultCache — encrypted file bytes + meta
    <driveFileId>.vault
    <driveFileId>.meta  # AES-GCM encrypted sidecar: {fileId, modifiedTime, size}
    .index.json         # Unencrypted LRU index (no filenames, only opaque IDs)
  folder_meta/          # FolderMetadataStore — encrypted folder listings
    <folderId>.vault    # AES-GCM encrypted List<VaultFile> JSON
cacheDir/
  encrypt_staging/      # UploadForegroundService staging (deleted after upload)
    <jobId>.vault       # Encrypted file pending upload
    <jobId>_thumb.vault # Encrypted thumbnail pending upload
```

`vault_cache/` and `folder_meta/` are excluded from Android Auto Backup and device-transfer in `backup_rules.xml` and `data_extraction_rules.xml`.

## Key security invariants

- Every `ByteArray` holding a key or password must be zeroed with `Arrays.fill(array, 0)` in a `finally` block after use. Existing code uses this pattern; maintain it.
- `MessageDigest.isEqual` (constant-time) is used for password hash comparison — do not replace with `==` or `contentEquals`.
- `CryptoManager.verifyPassword` is the correct place for local-hash fallback checks; `unwrapDek` + GCM tag failure is the primary wrong-password signal.
- `FLAG_SECURE` is always on in production (screenshot disabled). The DataStore key `KEY_SCREENSHOT_ENABLED` is only checked in DEBUG builds.

## Debug builds

`app/src/debug/` contains `DeveloperOptionsManager` and `DebugPanelScreen`, compiled only in debug. All references to them in main sources are guarded with `BuildConfig.DEBUG`. A `debug_panel` nav route is registered only in debug builds (`NavGraph.kt`).

## Docs site

`docs/` is a MkDocs site (GitHub Pages). Config is `mkdocs.yml`; Python dependencies are in `requirements.txt`. Not part of the Android build.
