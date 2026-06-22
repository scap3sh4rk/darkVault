# darkVault Review Report
Reviewer: Review Agent
Date: 2026-06-22

## Verdict: PASS

All HIGH and MEDIUM findings from the security audit are correctly patched. LOW findings are either
patched (LOW-001, LOW-005, LOW-006) or documented as accepted risk (LOW-002, LOW-004) or mitigated
with a TODO (LOW-003). Both debug and release builds compile cleanly. All 28 unit tests pass.

---

## Findings Verification

| Finding ID  | Patched? | Verified? | Notes |
|-------------|----------|-----------|-------|
| HIGH-001    | Yes      | Yes       | `tryUnlockWithVaultKey`: KEK now zeroed in `finally` block. Covers success, `AEADBadTagException`, `IllegalBlockSizeException`, and any other exception path. Line 323–326 in AuthViewModel.kt. Correct. |
| HIGH-002    | Yes      | Yes       | `saveToDownloads()`: `safeFileName` sanitizes `/`, `\`, `:`, `*`, `?`, `"`, `<`, `>`, `|`, control chars; truncates to 200 chars; falls back to `"file"` if blank. Used in both pre-Q and Q+ paths. Line 383–386 in HomeViewModel.kt. Correct. |
| HIGH-003    | Yes      | Yes       | SettingsScreen Switch Account dialog: `switchAccountAttempts` counter + `switchAccountMaxAttempts = 5` constant. Button disabled after 5 failures. Counter resets on dismiss. Lines 117–126, 538–561 in SettingsScreen.kt. Correct. |
| HIGH-004    | Yes      | Yes       | `data_extraction_rules.xml` replaced with explicit `<exclude domain="file" path="datastore/"/>` rules for both `<cloud-backup>` and `<device-transfer>`. Manifest already referenced this file. Correct. |
| MEDIUM-001  | Yes      | Yes       | `encBytes` zeroed in `finally` block wrapping `startResumableSession` + `uploadChunked` calls. Lines 155–158 in UploadForegroundService.kt. Structurally correct — zeroing happens on upload success, exception, and coroutine cancellation. Code after the finally (lines 160–167) does not use `encBytes`. |
| MEDIUM-002  | Yes      | Yes       | `loadOrCreateDek()`: KEK now zeroed in `finally` block. Lines 470–474 in AuthViewModel.kt. Correct. |
| MEDIUM-003  | Yes      | Yes       | `launchBiometric()` in UnlockScreen: replaced `runBlocking { prefs.getBiometricCredentials() }` with `scope.launch { val creds = prefs.getBiometricCredentials() ?: ... }`. Line 118–123 in UnlockScreen.kt. Main thread no longer blocked. Correct. |
| MEDIUM-004  | Yes      | Yes       | `build.gradle.kts`: `isMinifyEnabled = true`, `isShrinkResources = true`, `proguardFiles(...)`. `proguard-rules.pro` created with keep rules for crypto, data, VaultKeyBundle/Gson, OkHttp, Coroutines, Biometric, DataStore. Release stubs in `src/release/` resolve debug-only types at compile time. Release build compiles and R8 runs successfully. |
| MEDIUM-005  | Yes      | Yes       | Both HomeScreen (initial recovery key dialog, line 231–240) and SettingsScreen (rotated key dialog, line 679–688) now schedule a `scope.launch { delay(60_000L); clipboardManager.setText(AnnotatedString("")) }` after copy. Correct. |
| LOW-001     | Yes      | Yes       | All `SecureRandom().generateSeed(n)` calls replaced with `ByteArray(n).also { SecureRandom().nextBytes(it) }` in VaultKeyManager.kt, CryptoManager.kt, and AuthViewModel.kt. Grepped for remaining `generateSeed` calls — zero hits in main source. |
| LOW-002     | N/A      | Yes       | Accepted risk, documented in PATCH_NOTES.md. Rationale is sound — root access required; deleting DataStore also destroys the hash (attacker loses the target); DEK is on Drive not on device. |
| LOW-003     | Partial  | Yes       | `TODO LOW-003` comment added in HomeScreen.kt LaunchedEffect. The DEK-null window before HomeScreen DEK load still exists. FAB not disabled while DEK is null. Accepted risk documented — UI/UX fix deferred to post-review phase. |
| LOW-004     | N/A      | Yes       | Accepted risk, documented in PATCH_NOTES.md. Manual parser is correct for well-formed Base64. Malformed input causes uncaught exception caught by outer `catch (Exception)` → falls back to local hash. No user-visible crash. |
| LOW-005     | Yes      | Yes       | Fixed via MEDIUM-004: `proguard-rules.pro` contains `-assumenosideeffects class android.util.Log { public static int d(...); public static int v(...); public static int i(...); }`. R8 strips these in release APK. |
| LOW-006     | Yes      | Yes       | `network_security_config.xml` created with `cleartextTrafficPermitted="false"` and system CA trust only. `android:networkSecurityConfig="@xml/network_security_config"` added to AndroidManifest.xml line 15. |

---

## Regression Check

| Feature | Status | Notes |
|---------|--------|-------|
| DEK/KEK wrap/unwrap | PASS | `tryUnlockWithVaultKey` and `loadOrCreateDek` both load/unwrap DEK correctly. The `finally`-based zeroing does not interfere with the happy path — `VaultSession.dek = dek` runs before the finally. |
| Brute-force lockout (primary unlock) | PASS | `recordFailedAttemptAndError()` and `lockoutUntilMs` check in `unlock()` are untouched by any patch. |
| Biometric two-tier lock (v5) | PASS | `lockVault(auto=true)` path to `AppLocked` state, DEK retention in memory, `_sessionPasswordEntered` flag — all intact. `MEDIUM-003` change to `UnlockScreen.launchBiometric` does not break biometric re-auth flow: credentials are fetched inside `scope.launch`, cipher is created and `BiometricPrompt` is launched from the same coroutine. |
| vault.key upload/download | PASS | `createAndUploadDek`, `downloadVaultKeyFull`, `updateVaultKeyInPlace` — not changed by any patch. |
| Upload foreground service | PASS | `MEDIUM-001` adds a `try { } finally { Arrays.fill(encBytes, 0) }` around the chunked upload call. All post-upload state (setting `active = null`, removing from `activeJobIds`, emitting `Completed` event) runs correctly after the finally block in the success path. |
| Trash/restore | PASS | `trashFile`, `restoreFile`, `listTrashedVaultFiles` in DriveApiClient.kt — not changed by any patch. TrashScreen.kt — not changed. |
| Image preview (in-memory) | PASS | `decryptToMemory` and `CryptoManager.decrypt` — not changed. |
| Recovery key rotation | PASS | `rotateRecoveryKey` — not changed. Already has correct `finally { Arrays.fill(kek, 0) }` (lines 759–763). |
| Folder navigation | PASS | `HomeViewModel._folderStack`, `navigateUp`, `navigateTo`, `openFolder` — not changed. |
| Search/filter/sort | PASS | Not changed by any patch. |
| Auto-lock timer | PASS | `onAppBackground`, `autoLockJob` — not changed. |
| Session timeout | PASS | `startSessionTimer`, `lockSessionExpired` — not changed. |
| Duplicate rename | PASS | `findUniqueOriginalName` call in UploadForegroundService — not changed by MEDIUM-001 patch (only wrapping what follows it). |

---

## Security Spot Checks

| Check | Result |
|-------|--------|
| `Log.d`/`Log.v` printing key material | CLEAN — grepped all Log.d/v calls for key/dek/kek/password/secret/salt/hash patterns; none print key bytes |
| `android:allowBackup="false"` | PRESENT — AndroidManifest.xml line 12 |
| `network_security_config.xml` blocks cleartext | PRESENT — `cleartextTrafficPermitted="false"` in base-config |
| DEK/KEK zeroed in `finally` blocks | CONFIRMED — HIGH-001 and MEDIUM-002 fixes both use `finally` for zeroing |
| IV freshly generated per encrypt call | CONFIRMED — CryptoManager.kt lines 66–67 (encrypt) and encryptWithDek; each call generates new SecureRandom salt+IV |
| Path sanitization in `saveToDownloads` | PRESENT — regex strips traversal chars, truncates to 200 chars, blank fallback |
| Recovery key clipboard cleared after 60s | PRESENT — both HomeScreen.kt and SettingsScreen.kt have 60s delayed clear coroutine |

---

## Fixes Applied by Review Agent

None. All patches from the Patch Agent were verified to be correct and complete. No additional code changes were required.

---

## Unresolved Issues

The following items are known and documented but intentionally deferred:

1. **LOW-003 (partial)**: Upload FAB still active while `VaultSession.dek == null` between HomeScreen arrival and DEK load. Deferred to post-review UI/UX phase to avoid behavior changes during audit cycle. Risk is low (fallback to v0.02 per-file PBKDF2, not a security breach).
2. **LOW-002 (accepted risk)**: Brute-force lockout counter in DataStore is not integrity-protected. Root access required to exploit; documented as accepted risk.
3. **LOW-004 (accepted risk)**: VaultKeyBundle manual JSON parser. Correct for well-formed input; malformed input caught by outer exception handler. Migration to Gson noted as future hardening.
4. **Chunked streaming AEAD**: Single-shot AES-GCM loads whole file into RAM (OOM risk on large videos). Pre-existing architectural debt.
5. **AAD on GCM**: File metadata not bound as additional authenticated data. Pre-existing architectural debt.

---

## Build Status

| Target | Result | Notes |
|--------|--------|-------|
| assembleDebug | PASS | BUILD SUCCESSFUL in 54s. Only warnings: `GoogleSignIn` deprecation (pre-existing, not introduced by patches). |
| assembleRelease | PASS | BUILD SUCCESSFUL. R8/ProGuard ran successfully with `isMinifyEnabled = true`. No new errors. Strip warnings for `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so` are pre-existing native lib warnings. |
| testDebugUnitTest | PASS (28 tests) | 28 tests, 0 failures, 0 errors, 0 skipped. Breakdown: PathSanitizationTest (10), ConstantTimeComparisonTest (8), KeyZeroingTest (5), ClipboardHygieneTest (5). |
