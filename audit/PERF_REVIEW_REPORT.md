# Phase 3 Performance + Fixes Review
Date: 2026-06-22
Verdict: PASS

---

## Critical: Issue 5 Password Change Auth Flow
Status: FIXED (pre-existing — verified correct)

- **Offline guard**: YES — `AuthViewModel.changePassword()` line 639–643: returns `PasswordChangeResult.Error("Cannot change password while offline")` when `account == null || folderId == null`.
- **Drive-before-hash ordering**: YES — Drive `updateVaultKeyInPlace()` called at line 675, `prefs.savePasswordHash()` called at line 691 (after Drive success).
- **Biometric cleared**: YES — `prefs.clearBiometricCredentials()` called at line 694 after Drive success and before `setActiveSession()`.
- **Navigate-to-unlock on change**: YES — `SettingsScreen.kt` lines 733–736: on success, calls `authViewModel.lockVault()` then shows snackbar; NavGraph then routes to UnlockScreen on vault lock state.

---

## Batch Download (User Request)
Status: WIRED

Multi-select mode shows a top-bar action row with:
- `SelectAll` icon button
- `Download` icon button with a `BadgedBox` showing count of downloadable (non-folder) selected files — badge is cyan, disabled when 0 files selected
- `DeleteSweep` icon (bulk trash)
- `Close` icon (deselect all)

The Download button calls `homeViewModel.downloadSelected(pwd, acc)` which decrypts and saves all selected non-folder files to `Downloads/darkVault-loc/` and shows a Done snackbar with count.

---

## Issue Verification

| Issue | Status | Notes |
|-------|--------|-------|
| Issue 1 — App Speed | VERIFIED | OkHttpClient singleton in `companion object { ... by lazy }` (DriveApiClient.kt:27); folderCache stale-while-revalidate in loadItemsForCurrentFolder (HomeViewModel.kt:298–317); shimmer skeleton (HomeScreen.kt ShimmerFileCard); LazyColumn keyed by file.id; displayItems is combine+stateIn |
| Issue 2 — Previews | VERIFIED + EXTENDED | Image preview: rememberTransformableState+double-tap reset (HomeScreen.kt:1349–1372); Text preview: new TextPreviewDialog 512 KB limit, monospace, SelectionContainer; Video/Audio/PDF: deferred (no ExoPlayer dep) |
| Issue 3 — Conflict Dialog | VERIFIED | OutlinedTextField for rename (HomeScreen.kt:917–927); Replace triggers showReplaceConfirmDialog (line 941); RenameAs handled in UploadForegroundService.kt:179; Skip wired to ConflictResolution.Skip |
| Issue 4 — Brute-force | VERIFIED | Attempts 1–4: "N / 5 attempts" shown (AuthViewModel.kt:400), no lockout (computeBackoffMs returns 0 for attempts<5 at line 408); Attempt 5+: exponential lockout fires; Live countdown: LaunchedEffect(lockoutUntilMs) with delay(1000) loop (UnlockScreen.kt:174–181); Field disabled while timeLeftMs > 0 (line 351) |
| Issue 5 — Auth Fix | VERIFIED FIXED | See Critical section above |
| Issue 6 — Screenshot Toggle | VERIFIED | DebugPanelScreen.kt:347–381 has toggle with password confirm dialog; PreferencesManager has KEY_SCREENSHOT_ENABLED; MainActivity.onResume() reads screenshotEnabled and conditionally clears/adds FLAG_SECURE (MainActivity.kt:90–97) |

---

## Build Results
- `assembleDebug`: PASS (clean build 17s, 39 tasks)
- `testDebugUnitTest`: PASS (26 tasks)

---

## Fixes Applied by Review Agent

1. **Pull-to-refresh** (`HomeScreen.kt`): Wrapped the `when (uiState)` block in `PullToRefreshBox` (M3 1.4.0 `PullToRefreshBox` + `rememberPullToRefreshState`). Added imports for `PullToRefreshBox` and `rememberPullToRefreshState`. Swipe-down calls `homeViewModel.loadFiles(currentAccount)`. Spinning indicator shown while `isRefreshing = uiState is HomeUiState.Loading`.

2. **Text preview** (`HomeScreen.kt`): Added `TextPreviewDialog` composable — decrypts file in memory via `homeViewModel.decryptToMemory()`, shows UTF-8 text in `SelectionContainer { Text(..., fontFamily = FontFamily.Monospace) }` inside a `LazyColumn`. Max 512 KB enforced; larger files show "File too large for preview". Added `isTextMime()` helper covering `text/*`, `application/json`, `application/xml`, `application/xhtml+xml`, `application/javascript`. Tapping a text-MIME file in list view now opens TextPreviewDialog instead of downloading.

3. **State variable** (`HomeScreen.kt`): Added `var textPreviewFile by remember { mutableStateOf<VaultFile?>(null) }` state variable and updated VaultFileCard onClick lambda to branch on `canTextPreview`.

---

## Remaining Items (not implemented, with reason)

| Item | Reason |
|------|--------|
| Video preview (ExoPlayer) | Requires `media3-exoplayer` dependency and temp-file lifecycle; risky to add without integration test |
| Audio preview (MediaPlayer) | Same temp-file lifecycle concern; MediaPlayer is stateful and prone to resource leaks without careful testing |
| PDF preview (PdfRenderer) | Requires writing decrypted bytes to a temp file; same cleanup concern |
| Offline upload queue (Room) | Requires Room dependency, schema migration design, Worker class — multi-file change; deferred |
| File versioning (.vault.bak) | Minor DriveApiClient change but needs conflict with existing .bak handling; deferred |
| Drive token caching | `GoogleAuthUtil.getToken()` already uses GMS internal cache (~60 min); additional in-process caching is low-value risk |
| Cold-start deferral | No blocking I/O found in `onCreate`; no actionable deferral needed |
| In-memory old password zeroing | The `currentPassword` string is on the JVM heap; Java Strings are immutable and cannot be zeroed reliably. `setActiveSession()` replaces the reference. Accepted risk documented. |
| Unit test for password change | Not added (scope: review+fix only, not new test authoring) |
