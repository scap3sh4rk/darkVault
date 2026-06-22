# Performance + Fixes Audit

---

## Issue 5 Root Cause (CRITICAL) — Password Change Data Ordering

### Exact Bug Description

**File:** `AuthViewModel.kt`, function `changePassword()`, lines 610–673.

The password change function has a **correct ordering** for the Drive path (re-wrap DEK first, then write hash), but there is a subtle issue in both the Drive and offline-only paths:

**Step-by-step ordering as found in code (Drive path):**
1. (Line 618) Verify current password against DataStore hash.
2. (Line 627) Download `vault.key` from Drive.
3. (Lines 630–650) Derive `currentKek`, unwrap DEK, derive `newKek`, build `newBundle`, zero out keys.
4. (Line 653) Call `updateVaultKeyInPlace()` — re-upload `vault.key` with new KEK wrapping.
5. (Line 655) Set `VaultSession.dek = dek` — update in-memory DEK.
6. (Line 669) `prefs.savePasswordHash(newHash, newSalt)` — write new hash to DataStore.
7. (Line 671) `prefs.clearFailedAttempts()`.
8. (Line 672) `setActiveSession(newPassword)` — updates `_masterPassword` and `VaultSession.masterPassword`.

**Step-by-step ordering (offline path, no account/folderId):**
1. (Line 618) Verify current password.
2. Skips the Drive block entirely (lines 622–666).
3. (Line 669) Writes new password hash.
4. (Line 672) `setActiveSession(newPassword)`.

**Identified issues:**

### Issue 5A — Offline path does NOT re-wrap vault.key (MEDIUM severity)

When `changePassword()` is called with `account == null` or `folderId == null` (line 622 guard), the function skips the Drive re-key step entirely but still updates the local hash (line 669) and session password (line 672). This creates a state split:
- Local DataStore stores hash of `newPassword`.
- `vault.key` on Drive still has DEK wrapped by `currentPassword`-derived KEK.
- Next fresh unlock will derive KEK from `newPassword`, fail to unwrap DEK, get `AEADBadTagException`, and hit the `CHANGED_ON_OTHER_DEVICE` path (line 366–369 of `unlock()`), showing: `"Password was changed from another device. Enter the new password, or use your recovery key."`.

This is the exact error message described in Issue 5. **The offline password change path is the root cause.** In practice the SettingsScreen always passes `acc` and `folderId` (lines 726–728 of `SettingsScreen.kt`), but if either is `null` (offline, folder not yet discovered), the split occurs silently.

### Issue 5B — Biometric credentials NOT re-encrypted after password change

After `changePassword()` succeeds, the biometric-encrypted password blob (in DataStore: `KEY_BIOMETRIC_IV` + `KEY_BIOMETRIC_CT`) still holds the old password encrypted with the Android Keystore key. This means biometric unlock after a password change will decrypt the OLD password string, and `unlockWithBiometricCipher()` (AuthViewModel.kt line 411) will call `setActiveSession(oldPassword)`. The vault will appear unlocked (DEK retained) but `_masterPassword` now holds the stale password. Operations that re-derive the KEK (e.g. recovery key rotation) will silently use the wrong password.

**File:line:** `AuthViewModel.kt:672` — `setActiveSession(newPassword)` does NOT call `prefs.clearBiometricCredentials()` or re-enroll biometric.

### Issue 5C — `_sessionPasswordEntered` stays true through change

`setActiveSession()` (line 800) does not set `_sessionPasswordEntered = true` — that flag is set only at the call site in `unlock()` and `setup()`. However `changePassword()` calls `setActiveSession(newPassword)` (line 672) which does NOT set `_sessionPasswordEntered`. This is benign since the user is already in the session, but it means if they somehow reach `changePassword` without having set the flag (theoretically impossible currently), biometric would not be offered.

### Issue 5D — `dek` in `loadOrCreateDek()` may be uninitialized on exception

`AuthViewModel.kt` lines 470–474: the `finally` block zeroes `kek` correctly, but the `val dek = try { ... } finally { ... }` pattern means if `unwrapDek` throws, `dek` is never assigned, and `VaultSession.dek = dek` on line 475 is never reached. This is correct defensive behaviour. No bug here — just noting it was audited.

### Summary of Issue 5

**Primary bug:** If `changePassword()` is invoked with `account=null` or `folderId=null`, local hash is updated but `vault.key` is NOT re-keyed. On next full unlock, `tryUnlockWithVaultKey()` attempts to unwrap DEK with KEK derived from `newPassword`, gets `AEADBadTagException`, verifies `newPassword` against the local hash (succeeds), and returns `CHANGED_ON_OTHER_DEVICE`. The error message "Password was changed from another device" is emitted (AuthViewModel.kt line 366–368). The fix is to block the offline-only code path (show error "Cannot change password while offline") or queue a pending re-key on next connection.

**Secondary bug:** Biometric credentials must be cleared (or re-enrolled) after every successful password change.

---

## Issue 1 — Slowness Root Causes

### Drive Token: per-call fetch (no caching)

`DriveApiClient.kt` line 33: `private suspend fun token(): String` calls `GoogleAuthUtil.getToken(...)` on every invocation. This is a blocking OAuth round-trip. **Every Drive API function calls `token()` independently.** Google's `GoogleAuthUtil.getToken` does maintain a token cache internally and returns a cached token if still valid, but each call goes through the GMS OAuth stack. For burst operations (list → storage info → vault.key check), this adds latency on every call even if the token is cached internally.

- `ensureVaultFolder()` calls `token()` once → 1 call per startup.
- `listItems()` calls `token()` inside `withRetry` → 1 call per list.
- `downloadVaultKeyFull()` calls `token()` twice (search + download) → 2 calls on unlock.
- `getStorageInfo()` calls `token()` + calls `listItems()` which calls `token()` again → 2 calls.

No explicit token caching in `DriveApiClient` — relies entirely on GMS internal cache.

### Vault Folder ID: CACHED correctly in HomeViewModel

`HomeViewModel.kt` line 671–675: `ensureFolder()` checks `cachedFolderId` first. However, this cache is instance-scoped and reset on `clearDriveState()`. The ViewModel is hoisted at NavGraph level, so it persists across navigation. Root cause of folder ID fetching is isolated to cold start and `clearDriveState()`.

### `runBlocking` occurrences

Only ONE historical occurrence found; it was already fixed:
- `UnlockScreen.kt` line 129: comment says `// Fix: MEDIUM-003 — replace runBlocking ... with a coroutine`. The fix is already applied; no live `runBlocking` remains in main source.

**No active `runBlocking` calls found in `app/src/main/`.**

### LazyColumn key stability

`HomeScreen.kt` line 658: `items(displayItems, key = { it.id })` — keys ARE set correctly using Drive file ID.
`HomeScreen.kt` line 639: `items(recentItems, key = { "recent_${it.id}" })` — also keyed.
`LazyVerticalGrid` line 698: `items(displayItems, key = { it.id })` — keyed.

No missing keys in the lists.

### Derived computation memoization

`HomeViewModel.kt` lines 116–151: `displayItems` is a `combine(...)...stateIn(...)` — this is efficient; computed only when inputs change. `recentItems` (line 154) is also a `map { }.stateIn()`. Both are correct.

`HomeScreen.kt` line 577–589: `lastSynced` label computation is done inline on every recompose without `remember` or `derivedStateOf`. This is a minor allocation issue but negligible in practice.

### Cold start blocking init

`MainActivity.kt` onCreate (line 32–52): `FLAG_SECURE` is set synchronously, then `setContent {}`. No blocking I/O in `onCreate`. `PreferencesManager` DataStore reads happen asynchronously via `collectAsState`. No blocking cold-start issue found.

### File list caching between navigations

`HomeViewModel.kt` line 293–306: `loadItemsForCurrentFolder()` always re-fetches from Drive on folder open/back. There is NO in-memory cache of the file list per folder — navigating back to a folder always triggers a fresh Drive API call. This is the largest single performance gap.

### OkHttpClient creation

`DriveApiClient.kt` line 24: `private val http = OkHttpClient.Builder()...build()` — a NEW `OkHttpClient` is created for every `DriveApiClient` instantiation. `DriveApiClient` is instantiated in multiple places in HomeViewModel and AuthViewModel (not reused). Each instance creates its own thread pool and connection pool. This wastes resources and prevents connection reuse between calls.

**Evidence:** `HomeViewModel.kt` lines 237, 264, 298, 363, 383, 459, 513, 537, 559 — all call `DriveApiClient(getApplication(), account)` in separate coroutine scopes.

---

## Issue 2 — Preview/Thumbnail Gaps

### What exists

- **In-memory image preview dialog** (`HomeScreen.kt` line 1102–1160): downloads, decrypts, and shows a static `BitmapFactory.decodeByteArray` result in a 300dp `AlertDialog`. No pinch-to-zoom.
- **Coil thumbnail pipeline** (`VaultThumbnailFetcher.kt`): downloads + decrypts full file in memory, validates bitmap bounds, feeds to Coil. Capped at 2 MB encrypted size. Disk cache explicitly DISABLED (`VaultImageLoader.kt` line 17–18; per-request at `VaultComponents.kt` line 627). Memory cache: 10% of heap.
- **Shimmer placeholder** while thumbnail loads.
- **Fallback icon** on error or when DEK is null.

### Missing features

- **No pinch-to-zoom** in `ImagePreviewDialog` — the image fills a 300dp fixed box with `ContentScale.Fit`. No `TransformableState` or `rememberTransformableState` used.
- **No thumbnail downsampling** — `VaultThumbnailFetcher` decrypts the full file and hands raw bytes to Coil. For large images (e.g. 1.9 MB encrypted JPEG), Coil's own `BitmapFactory` subsample occurs at decode time, but the full encrypted download + full decrypted bytes must be in RAM simultaneously.
- **No text/audio/PDF preview** — these MIME types fall through to a generic icon. `coil-video:2.6.0` is in dependencies (build.gradle.kts line 70) but no video thumbnail composable exists — only the `isVideoMime` check that disables the thumbnail path (`VaultThumbnailImage`, line 604).
- **Video preview toggle** exists in Settings but clicking a video file triggers `downloadAndDecrypt` to Downloads folder — no in-app playback.
- **No waterfall / staggered grid** for thumbnails — grid layout is fixed `GridCells.Fixed`.
- **`imagePreviewEnabled` and `thumbnailsEnabled` are AND-gated** (`HomeScreen.kt` line 174): both must be true. User may be confused if "Image previews" is off but "Show thumbnails" is on in settings — thumbnails won't show. No visual feedback about this.

### MIME types handled for preview

Images (`image/*`): full preview dialog + thumbnails.
Videos (`video/*`): download to disk only (no preview).
Audio (`audio/*`): download to disk only.
Documents (PDF, etc.): download to disk only.

---

## Issue 3 — Conflict Dialog Gaps

### What exists

`HomeScreen.kt` lines 813–870: `pendingConflict` is consumed from `homeViewModel.pendingConflict`. Dialog shows:
- Title: `"<name>" already exists`.
- "File X of Y" counter when `totalConflicts > 1`.
- Three `OutlinedButton` actions: Rename to `suggestedName`, Replace existing, Skip this file.

`UploadForegroundService.kt` lines 147–190: conflict detection calls `findExistingOriginalName()` and suspends on `UploadState.conflictChannel.receive()`.

### Missing features

- **No existing file metadata shown** (size, modified date of the existing Drive file). The conflict event (`UploadEvent.ConflictDetected`) carries only `originalName`, `suggestedName`, `conflictIndex`, `totalConflicts` — no size/date of the incumbent file.
- **No editable rename field** — the rename option shows `"Rename to "<suggestedName>""` as a button, not a text field. The user cannot choose a custom name.
- **Replace has no confirmation step** — clicking "Replace existing file" immediately sends `ConflictResolution.Replace` to the channel. There is no "Are you sure?" prompt before trashing the existing file.
- **`conflictIndex` is always hardcoded to 1** and `totalConflicts` is always 1 (`UploadForegroundService.kt` line 159–162). The `conflictsAhead` count is computed (line 152–155) but not used in the emitted event. Multi-conflict sequencing (correct "File 2 of 5" display) is not wired up.
- **"Skip all" / "Replace all" / "Rename all" batch actions** do not exist.

---

## Issue 4 — Brute-force Threshold Gaps

### Current threshold

`AuthViewModel.kt` line 396–400 (`recordFailedAttemptAndError()`):
- The backoff is: `backoffMs = min(30_000 * (1L shl (attempts - 1)), 30 * 60 * 1000)`.
  - Attempt 1: 30 000 ms (30s)
  - Attempt 2: 60 000 ms (1 min)
  - Attempt 3: 120 000 ms (2 min)
  - Attempt 4: 240 000 ms (4 min)
  - Attempt 5: 480 000 ms (8 min)
  - Attempt 6: 960 000 ms (16 min)
  - Attempt 7+: 1 800 000 ms (30 min, capped)
- There is **no hard lockout** (account never permanently locked). The lockout just gets longer.
- There is **no threshold** (e.g. "after 10 attempts, lock until recovery key") — the counter just accumulates.

### Timer display

`UnlockScreen.kt` line 341: displays `"Too many failed attempts. Try again in ${seconds}s."` — this is a **static string** computed at the time of the unlock attempt. There is **no live countdown ticker**. The countdown is NOT re-emitted on a 1s interval; it only recalculates on the next unlock attempt.

`SettingsScreen.kt` line 481–497: the lockout status row shows `"Locked until HH:mm"` — a formatted time, not a countdown. No live tick.

### What's missing

- **Live countdown**: needs a `LaunchedEffect` with a 1s `delay` loop that re-reads `lockoutUntilMs` and recalculates remaining seconds.
- **Maximum attempt threshold**: no "nuclear lockout" after N attempts.
- **Attempt counter reset**: correctly clears on successful unlock (`prefs.clearFailedAttempts()` at lines 353 and 381).
- **The attempts counter is persisted in DataStore** (readable by root, as noted in CLAUDE.md LOW-002 known risk).

---

## Issue 6 — Screenshot Toggle Gaps

### FLAG_SECURE location

`MainActivity.kt` line 34: `window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)` — set **unconditionally** in `onCreate`. This is the **only** place it is set. There is a single Activity (single-window app), so all screens are protected.

### DebugPanelScreen exists

`app/src/debug/java/com/darkvault/app/debug/DebugPanelScreen.kt` — fully implemented (see read above). Contains 6 sections: Crypto state, Auth state, Drive/Network, Upload pipeline, Drive integrity check, In-app logcat viewer.

### What's missing for Issue 6

- **No screenshot toggle** in `DebugPanelScreen` or anywhere in the UI. The `FLAG_SECURE` flag is hardcoded always-on. There is no `SharedPreferences` or DataStore key for `screenshotEnabled`.
- To implement: add `KEY_SCREENSHOT_ENABLED = booleanPreferencesKey("screenshot_enabled")` to `PreferencesManager`, read it in `MainActivity.onResume()`, and conditionally `clearFlags(FLAG_SECURE)` or `addFlags(FLAG_SECURE)`. Add a toggle to `DebugPanelScreen` (debug builds only) and optionally to `SettingsScreen`.

---

## Bonus Features — Implementation Hook Points

### Offline upload queue (Room)
- **Where uploads are initiated:** `HomeViewModel.kt` lines 311–344 (`uploadFiles()`) — add files to `UploadState.queue` then start service.
- **Hook:** Persist `UploadJob` to a Room database before adding to the in-memory queue. On app launch, read pending Room jobs and re-enqueue. Room dependency is not yet present in `build.gradle.kts`.

### File versioning (keep previous vault file as .bak)
- **Where Replace happens:** `UploadForegroundService.kt` lines 179–186 — on `ConflictResolution.Replace`, calls `client.trashFile(existingFileId)` before uploading. Instead, call `client.copyFileWithNewName(existingFileId, "${name}.vault.bak")` (new DriveApiClient method) and skip the trash.

### Additional sort options
- **SortOrder enum location:** `HomeViewModel.kt` line 98 — `val sortOrder = MutableStateFlow(SortOrder.NAME_ASC)`. The `SortOrder` enum is in the `model/` package (not read but referenced). The Drive query in `listItems()` (`DriveApiClient.kt` line 184) uses `orderBy=name` server-side; all other sorts are client-side in `displayItems` combine.
- **Hook:** Add `SortOrder.MODIFIED_DESC` / `CREATED_DESC` variants; server-side `orderBy=modifiedTime desc` could be passed in `listItems()`.

### Pull-to-refresh
- **Where file list refresh is called:** `HomeScreen.kt` line 378: `IconButton` Refresh button calls `homeViewModel.loadFiles(it)`. Wrap the `LazyColumn` in a `SwipeRefresh` (accompanist or M3 `PullRefreshIndicator`), calling the same `homeViewModel.loadFiles(currentAccount)`.

### Long-press quick actions (context menu)
- **Where card tap handlers are:** `VaultFileCard` in `VaultComponents.kt` line 187. The `onClick` / `onToggleSelect` lambdas are the entry point. Add `combinedClickable` with `onLongClick = { showContextMenu = true }` and a `DropdownMenu` with quick actions (Download, Delete, Info).

### File info dialog
- **Where Drive file metadata is available:** `VaultFile` model carries `id`, `name`, `originalName`, `originalMimeType`, `size`, `createdTime`, `modifiedTime`. All available at `VaultFileCard` call site in `HomeScreen.kt` line 668. No additional Drive call needed for basic info.

### Empty state differentiation (no files vs. filtered-empty)
- **Current location:** `HomeScreen.kt` line 598–599: `EmptyVaultState()` is shown whenever `displayItems.isEmpty() && uiState is HomeUiState.Success`. No distinction between "vault is empty" and "search/filter has no results".
- **Hook:** Check `searchQuery.isBlank() && filterType == FilterType.ALL` — if false, show "No files match your filter" instead.

### Haptic feedback on lock/unlock
- **Lock success events:** `AuthViewModel.kt` line 544 (`lockVault()`). Unlock success: lines 356 and 382.
- **Hook:** Call `view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)` from the composable using `LocalView.current` in a `LaunchedEffect` on `authState` transitions.

### New Folder
- **Where FAB is defined:** `HomeScreen.kt` lines 494–524. Add a third `DropdownMenuItem("Create folder")` calling a new dialog that collects a name then calls `client.ensureSubFolder(name, currentFolderId)`.

### Rename file
- **Where file name mutations happen:** No rename path currently exists. The closest entry point is `DriveApiClient` — would need a new `renameFile(fileId, newName)` method (`PATCH {"name":"..."}`) and a UI dialog hooked into `VaultFileCard`'s long-press or action overflow.

---

## runBlocking Occurrences

**None found in `app/src/main/`.**

The only reference found is a comment at `UnlockScreen.kt` line 129 documenting that a previous `runBlocking` was replaced: `// Fix: MEDIUM-003 — replace runBlocking (blocks main thread, ANR risk) with a coroutine`.

---

## Drive Token Fetch Pattern

**Pattern: per-call, not explicitly cached.**

`DriveApiClient.kt` line 32–34:
```kotlin
private suspend fun token(): String = withContext(Dispatchers.IO) {
    GoogleAuthUtil.getToken(context, account.account!!, driveScope)
}
```

Called at the start of every public method. `GoogleAuthUtil.getToken` uses GMS internal cache (typically valid for ~60 min), so most calls hit the cache without a network round-trip. However the function incurs a JNI/IPC call to GMS on every Drive operation. The fix would be to cache the token in a `@Volatile private var cachedToken: String? = null` field with a `tokenExpiresAt: Long`, and only re-fetch when within 5 minutes of expiry.

---

## Vault Folder ID Cache

**Pattern: cached in HomeViewModel; NOT cached in DriveApiClient or AuthViewModel.**

`HomeViewModel.kt` lines 193–194:
```kotlin
private var cachedFolderId: String? = null
private var cachedAccount: GoogleSignInAccount? = null
```

`ensureFolder()` (line 671–675) checks `cachedFolderId` before calling `ensureVaultFolder()`. This cache is correct for HomeViewModel operations.

**Not cached in AuthViewModel:** `checkVaultOnDrive()` (line 123) calls `client.ensureVaultFolder(savedFolderId)` each time, which makes a Drive API call to verify the folder ID is still valid. The result is saved to DataStore (`prefs.saveVaultFolderId(folderId)`) and passed via `_pendingFolderId`. On next app launch, `prefs.vaultFolderId.first()` is used as `savedFolderId` input, which short-circuits `ensureVaultFolder()` if the folder is still valid (line 95–104 of `DriveApiClient`).

**OkHttpClient not shared:** Every `DriveApiClient` instantiation (there are ~10 per session) creates a new `OkHttpClient` with its own connection pool. This prevents HTTP/2 connection reuse across calls and wastes thread resources.
