# darkVault Security Audit Report
Generated: 2026-06-22
Auditor: Security Audit Agent

---

## Executive Summary

darkVault implements a solid envelope-encryption architecture (DEK/KEK split, AES-256-GCM, PBKDF2WithHmacSHA256 at 100k iterations, constant-time comparison, FLAG_SECURE, hardware-backed biometric key). No critical authentication-bypass or remote-code-execution vectors were found. The most impactful findings cluster around two themes: (1) key-material zeroing gaps where a KEK or DEK can linger in heap memory longer than necessary under exceptional code paths, and (2) a lack of defenses for the file-download surface — path traversal using a crafted `originalName` from Drive appProperties can write decrypted files outside the intended `darkVault-loc/` directory on Android 8–9, and the Switch Account password dialog has no brute-force lockout. The `data_extraction_rules.xml` file is left as the Android Studio boilerplate template (all-TODO, no exclusions), potentially exposing DataStore (password hash + salt + biometric blob) via Android 12+ Device-to-Device transfer. Release builds ship with `isMinifyEnabled = false`, leaking internal class/method names to anyone who decompiles the APK.

---

## Findings

### [HIGH-001] KEK not zeroed in outer exception catch — `tryUnlockWithVaultKey`

**File**: `app/src/main/java/com/darkvault/app/viewmodel/AuthViewModel.kt` lines 297–326

**Description**: `tryUnlockWithVaultKey()` derives the KEK at line 297 (`val kek = CryptoManager.deriveKey(password, bundle.kekSalt).encoded`), then enters an inner `try` block that only catches `AEADBadTagException`. The KEK is zeroed inside the happy-path try (line 300) and inside the `AEADBadTagException` catch (line 316). However, `VaultKeyManager.unwrapDek()` can also throw `IllegalBlockSizeException` (truncated/malformed ciphertext — not a subclass of `AEADBadTagException` on Android). In that case, the inner catch is skipped, the outer generic `catch (e: Exception)` fires at line 324, and the `kek` ByteArray is never zeroed. The KEK remains in heap memory until the next GC cycle.

```kotlin
// OUTER catch — kek is NOT zeroed here
} catch (e: Exception) {
    Log.w(TAG, "Drive-backed auth unavailable, falling back to local hash", e)
    UnlockAttemptResult.NETWORK_FALLBACK
}
```

**Impact**: A heap-dump of the process (possible on rooted or debug-build devices) may reveal the KEK, which is a per-password derivative of the master password. The attacker already needs local device access to obtain the dump, so this is a defence-in-depth gap rather than a standalone exploit chain.

**CVSS estimate**: 4.4 (AV:L/AC:L/PR:H/UI:N/S:U/C:H/I:N/A:N)

**Recommendation**: Wrap the `kek` zeroing in a `finally` block at the same scope where `kek` is declared, replacing the two scattered `Arrays.fill(kek, 0)` calls:

```kotlin
val kek = CryptoManager.deriveKey(password, bundle.kekSalt).encoded
try {
    try {
        val dek = VaultKeyManager.unwrapDek(bundle.dekWrappedByKek, kek)
        // ...
    } catch (e: javax.crypto.AEADBadTagException) {
        // distinguish wrong password vs changed-on-other-device
    }
} finally {
    Arrays.fill(kek, 0)
}
```

The same pattern applies to `loadOrCreateDek()` (line 463-465 does not zero the `kek` at all in any exception path).

---

### [HIGH-002] Path traversal via `originalName` on Android 8–9 (pre-Q)

**File**: `app/src/main/java/com/darkvault/app/viewmodel/HomeViewModel.kt` lines 391–395

**Description**: `saveToDownloads()` on Android 8–9 constructs the output path as:

```kotlin
val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "darkVault-loc")
dir.mkdirs()
File(dir, fileName).also { it.writeBytes(data) }
```

`fileName` is set to `file.originalName` at every call site (lines 427, 470, 495). `originalName` comes directly from the Drive `appProperties.originalName` field (set at upload time, but readable/modifiable by anyone with access to the Drive folder). Java's `File(parent, child)` constructor does not canonicalize; a value like `../../somefile.apk` resolves outside `darkVault-loc`. On the app-private external path this means escape is limited to the app's own `files/` subtree, but it still allows writing arbitrary content to `files/<arbitrary-path>`, which could corrupt application state (e.g., overwrite DataStore files or shared preferences).

On Android 10+ (Q), `MediaStore.Downloads.insert()` uses `DISPLAY_NAME`, which is handled safely by MediaStore — only the pre-Q branch is vulnerable.

**Impact**: An attacker who can manipulate the Drive appProperties of a vault file (requires Drive access for the linked account) can cause the app to write arbitrary decrypted file content to an attacker-chosen path within the app's external file area. Severity is limited because external storage is app-private on Android 8–9; the app's internal DataStore lives in internal storage and is not reachable.

**CVSS estimate**: 5.0 (AV:N/AC:H/PR:H/UI:R/S:U/C:N/I:H/A:L)

**Recommendation**: Sanitize `fileName` before constructing the `File` path. At minimum, strip path components:

```kotlin
val safeName = File(fileName).name  // strips any directory component
File(dir, safeName).also { it.writeBytes(data) }
```

---

### [HIGH-003] Switch Account password dialog has no brute-force lockout

**File**: `app/src/main/java/com/darkvault/app/ui/screens/SettingsScreen.kt` lines 540–549

**Description**: The "Switch Account" dialog calls `CryptoManager.verifyPassword()` directly, with no rate-limiting, lockout, or attempt counter. An attacker with physical access to an unlocked device (i.e., the vault is already in `Home` state) can rapidly try passwords in this dialog without triggering the brute-force protection that guards `AuthViewModel.unlock()`.

```kotlin
val valid = stored != null && withContext(Dispatchers.Default) {
    CryptoManager.verifyPassword(switchAccountPassword, stored.first, stored.second)
}
```

Note: the primary unlock screen is correctly protected by the `lockoutUntilMs` check. The settings-screen path bypasses this entirely. The same gap exists to a lesser extent for the Rotate Recovery Key dialog (lines 598–619), though a successful rotation requires both a valid password AND Drive access.

**Impact**: If an attacker has access to a device where the vault is already unlocked (e.g., found/stolen while open), they can brute-force the master password via the Switch Account dialog without any delay. PBKDF2 at 100k iterations provides ~1–2 guesses/second on modern hardware, so a weak 8-character password could be cracked in hours during a single access window.

**CVSS estimate**: 5.5 (AV:P/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N)

**Recommendation**: Route the password check in both dialogs through `AuthViewModel` so the same lockout logic applies. Alternatively, add a per-dialog attempt counter with a short delay (e.g., `delay(1000L * attempt)`) and a cap after N failures.

---

### [HIGH-004] `data_extraction_rules.xml` is unconfigured — DataStore may be transferred via D2D

**File**: `app/src/main/res/xml/data_extraction_rules.xml`

**Description**: On Android 12+, `data_extraction_rules.xml` (referenced by `android:dataExtractionRules` in the manifest) governs Device-to-Device (cable) transfers independently of `android:allowBackup`. The file was left as the Android Studio boilerplate template with all content commented out and a TODO. The `<cloud-backup>` element with no child rules defaults to **including everything**, meaning the DataStore (`vault_prefs.preferences_pb`) — which holds the PBKDF2-derived password hash, salt, biometric IV, and biometric ciphertext — may be transferred to a new device via D2D restore (e.g., Pixel-to-Pixel migration).

If transferred, the new device has the biometric ciphertext but not the Keystore key (hardware-bound to the source device). However, it also has the password hash + salt, allowing offline PBKDF2 brute-force without touching the original device's brute-force counter in DataStore.

**Impact**: Transfers the locally-protected password verifier to a new device, bypassing the lockout counter persisted in DataStore and enabling unrestricted offline brute-force.

**CVSS estimate**: 5.9 (AV:P/AC:H/PR:N/UI:N/S:U/C:H/I:N/A:N)

**Recommendation**: Add explicit exclusion rules to `data_extraction_rules.xml`:

```xml
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="datastore/"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="datastore/"/>
    </device-transfer>
</data-extraction-rules>
```

---

### [MEDIUM-001] `encBytes` (plaintext-equivalent compressed data) never zeroed in upload service

**File**: `app/src/main/java/com/darkvault/app/service/UploadForegroundService.kt` lines 118–128

**Description**: `processQueue()` encrypts the file into `encBytes` and then uploads it. `encBytes` is a `ByteArray` containing GZIP-compressed plaintext (for v0.03 DEK-encrypted files the compressed plaintext is what is encrypted). This byte array is held in memory throughout the chunked upload and is never zeroed on completion, failure, or cancellation. Similarly, `compressedBytes` inside `CryptoManager.encryptWithDek()` is zeroed at line 109 (inside the `finally` block), but `encBytes` itself (the ciphertext result, not the plaintext) remains until GC. The ciphertext alone does not expose plaintext but the compressed plaintext exists briefly in `CryptoManager.encryptWithDek` where `compressedBytes` is zeroed — this part is handled correctly.

The more concrete issue: if the upload is cancelled after `encBytes` is computed but before it is fully consumed by `uploadChunked`, `encBytes` is not zeroed. It remains in the service's memory until GC.

**Impact**: On a rooted device, a heap dump could recover the ciphertext (not the plaintext — `compressedBytes` is zeroed). The security value of zeroing ciphertext is low; this is primarily a defence-in-depth concern.

**CVSS estimate**: 3.5 (AV:L/AC:H/PR:H/UI:N/S:U/C:L/I:N/A:N)

**Recommendation**: Zero `encBytes` after upload completes or fails:

```kotlin
} finally {
    Arrays.fill(encBytes, 0)
}
```

---

### [MEDIUM-002] `loadOrCreateDek()` does not zero the KEK on any exception path

**File**: `app/src/main/java/com/darkvault/app/viewmodel/AuthViewModel.kt` lines 457–477

**Description**: `loadOrCreateDek()` calls `CryptoManager.deriveKey(password, bundle.kekSalt).encoded` at line 463 into local variable `kek`. If the subsequent `VaultKeyManager.unwrapDek()` throws any exception (line 464), the entire block is caught by the `catch (e: Exception)` at line 472, and `kek` is never zeroed.

```kotlin
val existingJson = client.downloadVaultKey(folderId)
if (existingJson != null) {
    val bundle = VaultKeyBundle.fromJson(existingJson)
    val kek = CryptoManager.deriveKey(password, bundle.kekSalt).encoded
    val dek = VaultKeyManager.unwrapDek(bundle.dekWrappedByKek, kek)
    java.util.Arrays.fill(kek, 0)
    VaultSession.dek = dek
    // ...
```

If `unwrapDek` throws, `Arrays.fill(kek, 0)` is skipped.

**Impact**: Same as HIGH-001 — heap dump on rooted/debug device may recover the KEK.

**CVSS estimate**: 4.4 (AV:L/AC:L/PR:H/UI:N/S:U/C:H/I:N/A:N)

**Recommendation**: Add `finally { Arrays.fill(kek, 0) }` around the unwrap call.

---

### [MEDIUM-003] `runBlocking` called on biometric callback dispatcher (potential ANR)

**File**: `app/src/main/java/com/darkvault/app/ui/screens/UnlockScreen.kt` line 121

**Description**: `launchBiometric()` is called from a Composable UI callback. Inside it, `launchBiometric()` calls `kotlinx.coroutines.runBlocking { prefs.getBiometricCredentials() }` to fetch credentials synchronously. `runBlocking` blocks the calling thread (the main thread when triggered from a UI event). A slow DataStore read (e.g., disk contention) could block the main thread long enough to trigger an ANR.

```kotlin
val creds = runCatching {
    kotlinx.coroutines.runBlocking { prefs.getBiometricCredentials() }
}.getOrNull()
```

**Impact**: Denial of service / poor UX; not a security vulnerability per se. However, a force-close or ANR at this point leaves the user unable to unlock via biometric, with no plaintext data exposed.

**CVSS estimate**: 3.7 (AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:L)

**Recommendation**: Fetch biometric credentials with a coroutine before displaying the biometric prompt:

```kotlin
scope.launch {
    val creds = prefs.getBiometricCredentials() ?: run {
        biometricError = "Biometric credentials not set up"
        return@launch
    }
    val cipher = BiometricKeyManager.getCipherForDecryption(creds.first)
    // then authenticate
}
```

---

### [MEDIUM-004] Release build has `isMinifyEnabled = false` — no obfuscation

**File**: `app/build.gradle.kts` line 26

**Description**: `isMinifyEnabled = false` in the release build type disables R8/ProGuard code shrinking and obfuscation. Class names, method signatures, and string literals (including error messages that name internal architecture components such as "DEK", "KEK", "vault.key", "kekSalt") are fully visible to anyone who decompiles the release APK. This makes reverse engineering trivially easy and could assist in crafting targeted exploits against the cryptographic layer.

**Impact**: Reduces barrier to understanding the codebase for an attacker. Does not directly expose key material. Severity is limited because the cryptographic design does not rely on obscurity.

**CVSS estimate**: 3.1 (AV:N/AC:H/PR:N/UI:R/S:U/C:L/I:N/A:N)

**Recommendation**:

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
}
```

---

### [MEDIUM-005] Recovery key copied to clipboard without automatic expiry

**File**: `app/src/main/java/com/darkvault/app/ui/screens/HomeScreen.kt` line 224  
**File**: `app/src/main/java/com/darkvault/app/ui/screens/SettingsScreen.kt` line 665

**Description**: The "Copy" button in both the initial recovery key dialog and the rotated key dialog copies the 64-hex-character recovery key to the system clipboard using `clipboardManager.setText(AnnotatedString(key))`. There is no follow-up timer to clear the clipboard after a few seconds. On Android 13+ (API 33), other apps can still read the clipboard if the user taps into them, and Android's clipboard history feature retains entries. The recovery key in the clipboard could be accessed by any foreground app or read from clipboard history.

**Impact**: Recovery key exposure via clipboard to other apps or clipboard history viewers. The recovery key allows full vault access without the master password.

**CVSS estimate**: 4.3 (AV:L/AC:L/PR:N/UI:R/S:U/C:H/I:N/A:N)

**Recommendation**: Schedule clipboard clearing after 60 seconds:

```kotlin
clipboardManager.setText(AnnotatedString(key))
scope.launch {
    delay(60_000L)
    if (clipboardManager.getText()?.text == key) {
        clipboardManager.setText(AnnotatedString(""))
    }
}
```

On Android 13+ (API 33), use `ClipboardManager.clearPrimaryClip()` instead of setting an empty string.

---

### [LOW-001] `SecureRandom().generateSeed()` used instead of `nextBytes()` throughout

**Files**: 
- `app/src/main/java/com/darkvault/app/crypto/VaultKeyManager.kt` lines 29, 32, 57, 85
- `app/src/main/java/com/darkvault/app/crypto/CryptoManager.kt` lines 44, 64, 65
- `app/src/main/java/com/darkvault/app/viewmodel/AuthViewModel.kt` lines 429, 633, 690

**Description**: `SecureRandom().generateSeed(n)` is intended for seeding other random number generators, not for generating cryptographic material directly. The JCA documentation states: "The seed bytes shall not be generated by a SecureRandom that has not been seeded with a proper entropy source." On Android's default `SHA1PRNG` provider, `generateSeed()` pulls from `/dev/urandom` and is in practice secure, but on non-Android JVM implementations (relevant for the planned Linux client), `generateSeed()` behaviour is provider-dependent and may not produce cryptographically safe output without explicit seeding. The idiomatic approach is `SecureRandom().nextBytes(buf)`.

**Impact**: Negligible on Android as shipped. Could become a real issue if this code is reused on the planned Linux client.

**CVSS estimate**: 0.0 (no current exploit on Android)

**Recommendation**: Replace all `SecureRandom().generateSeed(n)` calls with the standard pattern:

```kotlin
fun secureBytes(n: Int): ByteArray {
    val buf = ByteArray(n)
    SecureRandom().nextBytes(buf)
    return buf
}
```

---

### [LOW-002] Brute-force lockout counter is not persisted in an integrity-protected store

**File**: `app/src/main/java/com/darkvault/app/data/PreferencesManager.kt` lines 113–130

**Description**: Failed-attempt count and lockout-until timestamp are stored in DataStore Preferences — plaintext on-disk (not encrypted). An attacker with physical device access who can access the app's data directory (e.g., via ADB on a debug build, or on a rooted device) can delete or overwrite the DataStore file to reset the lockout counter, then proceed to brute-force the master password. This is noted in CLAUDE.md as known debt ("brute-force lockout: is lockout state integrity-protected in DataStore"). The vault itself remains protected by the KEK on Drive, but the DataStore also stores the password hash + salt which enables offline brute-force.

**Impact**: A motivated attacker with root/ADB access can reset the lockout counter and brute-force the local password hash, bypassing the exponential backoff. Real-world impact is low because root access is required.

**CVSS estimate**: 3.5 (AV:L/AC:H/PR:H/UI:N/S:U/C:L/I:N/A:N)

**Recommendation**: Consider using Android's `EncryptedSharedPreferences` or storing a hardware-backed HMAC of the lockout state. This is an acknowledged architectural limitation; documenting it as accepted risk is reasonable.

---

### [LOW-003] Offline unlock allows Home state without DEK loaded

**File**: `app/src/main/java/com/darkvault/app/viewmodel/AuthViewModel.kt` lines 372–389  
**File**: `app/src/main/java/com/darkvault/app/ui/screens/HomeScreen.kt` lines 170–177

**Description**: When Drive is unavailable, `tryUnlockWithVaultKey()` returns `NETWORK_FALLBACK`. The `unlock()` function then validates against the local hash and, on success, sets `authState = Home` without setting `VaultSession.dek`. The HomeScreen handles this via `LaunchedEffect` at line 170, which calls `loadOrCreateDek()` when `dek == null`. However, between `authState = Home` and the successful DEK load, if the user triggers an upload, `UploadForegroundService` uses the legacy per-file PBKDF2 path (`CryptoManager.encrypt()`) instead of the DEK path. This is intentional fallback behaviour but results in v0.02 encrypted files instead of v0.03, inconsistently mixing encryption versions across the vault.

**Impact**: Functional inconsistency — no data exposure — but version-mixed vaults are harder to audit and may complicate future migrations.

**CVSS estimate**: 0.0 (no security impact)

**Recommendation**: Disable the upload FAB / upload action while `VaultSession.dek == null && !dekLoadAttempted`, or surface a "Reconnecting to Drive…" state that prevents uploads until the DEK is confirmed available.

---

### [LOW-004] VaultKeyBundle uses a fragile manual JSON parser

**File**: `app/src/main/java/com/darkvault/app/model/VaultKeyBundle.kt` lines 33–44

**Description**: `VaultKeyBundle.fromJson()` is a hand-rolled parser that uses string indexOf/substring operations. The `extract(key)` function finds the first `"` after the key's marker and uses it as the value end. If a Base64 value somehow contains a `"` character (which standard Base64 cannot, but `Base64.NO_WRAP` on Android won't either), or if the JSON is structurally malformed (e.g., a truncated Drive response), the function will silently produce wrong indices, potentially returning garbage Base64 that causes `ArrayIndexOutOfBoundsException` or similar during key operations. Additionally, `version` parsing via `substringAfter/substringBefore` is fragile if the JSON ordering varies.

**Impact**: A malformed or truncated `vault.key` response could cause an uncaught exception during unlock rather than a clean failure message, potentially crashing the app repeatedly (denial of service) or silently falling through to the offline path without user awareness.

**CVSS estimate**: 3.3 (AV:N/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:L)

**Recommendation**: Use Gson (already a dependency) to parse VaultKeyBundle, or add robust bounds-checking and failure modes to the manual parser.

---

### [LOW-005] `Log.d` and `Log.w` calls present in release builds (no ProGuard)

**File**: `app/src/main/java/com/darkvault/app/viewmodel/AuthViewModel.kt` lines 90, 112, 142, 265, 306, 325, 467, 470, 473, 649, 652, 712, 767, 772

**Description**: Because `isMinifyEnabled = false` (see MEDIUM-004), all `Log.d` and `Log.w` calls in `AuthViewModel` are present in the release build and will emit to logcat. On Android 4.1+ these require `READ_LOGS` permission for third-party apps to read, but system apps and ADB can read them. None of the log messages contain key bytes, passwords, or secrets, but they do confirm internal architecture details (e.g., "DEK loaded from existing vault.key", "Local hash updated: password was changed on another device") to anyone monitoring logcat via ADB.

**Impact**: Information disclosure of internal state to ADB-connected attackers. No key material exposed.

**CVSS estimate**: 2.0 (AV:L/AC:H/PR:H/UI:N/S:U/C:L/I:N/A:N)

**Recommendation**: Enable ProGuard/R8 (MEDIUM-004 fix covers this — R8 strips Log.d/Log.v by default in release builds). Alternatively, wrap debug-only logs in `if (BuildConfig.DEBUG)` guards.

---

### [LOW-006] Missing `android:networkSecurityConfig` — relies on platform defaults

**File**: `app/src/main/AndroidManifest.xml`

**Description**: No `android:networkSecurityConfig` attribute is set. The app relies on the Android platform default, which on API 28+ blocks cleartext HTTP. All actual network calls go to `https://www.googleapis.com/` so there is no present cleartext risk. However, without an explicit Network Security Config, the app inherits any changes in platform default policy and is also susceptible to user-added CA certificates in the system trust store (which could enable MITM if the user installs a rogue CA).

**Impact**: Low — no cleartext HTTP in use, and Drive's TLS is enforced server-side. Explicit configuration is a hardening best practice.

**CVSS estimate**: 3.1 (AV:N/AC:H/PR:N/UI:R/S:U/C:L/I:N/A:N)

**Recommendation**: Add `res/xml/network_security_config.xml` with `cleartextTrafficPermitted="false"` and certificate pinning for `googleapis.com` if a high-assurance deployment is required.

---

## Workflow Trace — No Issues Found

The following areas were audited and found clean:

- **FLAG_SECURE**: Set unconditionally at `MainActivity.onCreate()` line 30 (`window.addFlags(FLAG_SECURE)`). All UI screens run in this single Activity. Correct.
- **GCM IV reuse**: Every encrypt call generates a fresh IV via `SecureRandom`. No IV is ever stored and reused. Correct.
- **Constant-time comparison**: `CryptoManager.verifyPassword()` uses `MessageDigest.isEqual()` (line 53). Correct.
- **DEK null-check before encrypt**: `UploadForegroundService` checks `VaultSession.dek` at line 122 and falls back to per-file PBKDF2 if null. No NPE path.
- **Biometric key parameters**: `BiometricKeyManager` sets `setUserAuthenticationRequired(true)` and `setInvalidatedByBiometricEnrollment(true)`. On API 30+, uses `AUTH_BIOMETRIC_STRONG` with timeout=0 (per-use auth). Correct.
- **PBKDF2 salt uniqueness**: A fresh `SecureRandom().generateSeed(16)` KEK salt is generated in `createAndUploadDek()` (line 429), `changePassword()` (line 633), and `recoverWithRecoveryKey()` (line 690). Each vault.key write uses a new salt. Correct.
- **Drive scope**: `driveScope = "oauth2:https://www.googleapis.com/auth/drive.file"` in `DriveApiClient` (line 30) — only `drive.file` scope, not full `drive`. Correct.
- **vault.key GCM integrity**: `unwrapDek()` uses AES-256-GCM; a tampered vault.key will throw `AEADBadTagException`, which is caught and translated to a user-facing error. Correct.
- **Logcat key material**: All `Log.*` calls examined; none contain key bytes, password strings, DEK content, or Base64-encoded key material.
- **android:allowBackup="false"**: Set in AndroidManifest.xml line 12. Correct for API ≤ 31.
- **Implicit intent safety**: All service intents use explicit class references (`UploadForegroundService::class.java`). No implicit intents used for sensitive data.
- **Auth state machine**: All `AuthState` transitions are handled in `DarkVaultNavGraph`. `AppLocked → Home` requires biometric cipher success. `Init/CheckingVault/SignIn → signin`, `Setup → setup`, `Unlock/AppLocked → unlock`, `Home → home`. No reachable inconsistent state in the happy path.
- **Folder navigation back-stack**: `_folderStack` is a `StateFlow<List<FolderEntry>>`. `navigateUp()` drops the last element; `navigateTo()` slices to the target index; `openFolder()` appends. The stack is correctly maintained on back navigation.
- **Recovery key rotation**: After rotation, `dek` is zeroed at line 760 **after** `VaultKeyManager.wrapDek(dek, newRecoveryKeyBytes)` completes at line 758. Correct ordering.
- **Biometric ciphertext**: The biometric credential stores `AES/GCM`-encrypted master password, protected by a Keystore key. On biometric unlock, the Keystore key decrypts the password, which is then used normally. No DEK is stored in biometric credentials — just the master password. Correct architecture.
- **Token refresh**: `GoogleAuthUtil.getToken()` is called fresh at the start of each Drive API function; if the token has expired, the Google SDK refreshes it transparently. The retry logic (`withRetry`) covers 429/500/503 but not 401; a 401 would propagate as an exception, failing the operation but not silently using a stale token.
- **Trash/restore state**: After `trashFile()`, the local `_rawItems` list is updated immediately (line 530). If the Drive call succeeds but list refresh fails on the next load, the item is correctly absent because it was already removed from local state. No inconsistency.
- **Upload cancel mid-stream**: Cancel adds `job.id` to `cancelledIds` (set). The upload loop checks `cancelledIds` before starting encryption (line 112) and inside the chunk-progress callback (line 147). Encryption itself is not interrupted, but the partial session is abandoned and the Drive resumable session expires naturally. Cancel is best-effort, which is acceptable.
- **Duplicate detection race**: `UploadForegroundService.processQueue()` processes one job at a time in a single coroutine (sequential `while` loop). Two simultaneous uploads of the same file would only occur if two service instances ran concurrently, which `START_NOT_STICKY` and the single-scope design prevent.
- **No plaintext written to disk**: Image preview uses in-memory `ByteArrayOutputStream` decryption (confirmed in `HomeViewModel.decryptToMemory()` and `CryptoManager.decrypt()`). Exports use `MediaStore.Downloads` (Android 10+) or `app-private external storage` (Android 8–9) but write the **decrypted** file there intentionally as part of the export feature — this is expected behaviour, not a bug.
- **Password never stored in DataStore**: Confirmed — DataStore holds only PBKDF2-derived hash + salt. The master password lives only in `AuthViewModel._masterPassword` and `VaultSession.masterPassword` (both in-memory `@Volatile` fields), cleared on lock/sign-out.
