# Security Policy

## Overview

darkVault is a locally-encrypted file backup client for Android. Files are encrypted on-device with AES-256-GCM before being uploaded to Google Drive. The master password never leaves the device. Google cannot read stored files.

This document describes the security model, supported versions, how to report vulnerabilities, and the disclosure process.

---

## Encryption Architecture

| Component | Implementation |
|-----------|---------------|
| File encryption | AES-256-GCM |
| Key derivation | PBKDF2WithHmacSHA256, 100,000 iterations, 256-bit output |
| Key model | Envelope encryption — per-vault DEK wrapped by password-derived KEK |
| Salt | 16 bytes, cryptographically random (SecureRandom), unique per derivation |
| GCM IV | 12 bytes, cryptographically random, unique per encryption operation |
| Auth tag | 128-bit GCM tag, verified before any plaintext is produced |
| Key storage | DEK never stored. KEK never stored. Only PBKDF2-derived hash+salt in DataStore for password verification. |
| Drive key bundle | `vault.key` on Drive: DEK wrapped by KEK + DEK wrapped by recovery key (AES-256-GCM) |
| Recovery key | 256-bit random, formatted as 8-group hex, shown once at setup |

### What is protected

- All `.vault` files on Google Drive are ciphertext. Google cannot read them.
- Master password never stored, transmitted, or logged.
- DEK bytes zeroed in memory after use and on app lock.
- `FLAG_SECURE` prevents screenshots and recents-screen leakage on all app screens.
- `android:allowBackup="false"` prevents system backup of DataStore (password hash + salt).
- Brute-force protection: exponential backoff lockout after 5 consecutive wrong attempts.

### What is not protected (known limitations)

- **File metadata**: original filename and MIME type stored in Google Drive `appProperties` in plaintext. An attacker with Drive access sees filenames but not file contents.
- **Existence**: presence of `darkVault/` folder and number of `.vault` files is visible to Google and anyone with Drive access.
- **KDF strength**: PBKDF2 at 100k iterations is within current norms but weaker than Argon2id. Argon2id planned for a future version.
- **Single-shot GCM**: large files loaded entirely into RAM. OOM risk on files >~200MB. Chunked AEAD streaming planned.
- **Temp files**: video, audio, and PDF previews write a decrypted temp file to app-private `cacheDir` for preview duration. Deleted immediately on close. Not accessible to other apps on non-rooted devices.

---

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest release (main branch) | ✅ |
| Older releases | ❌ — update to latest |

darkVault targets a "ship complete, use privately" model. All security fixes applied to main branch. No backport policy.

---

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Use GitHub private vulnerability reporting:

1. Go to `https://github.com/scap3sh4rk/darkVault/security/advisories`
2. Click **"Report a vulnerability"**
3. Fill in the advisory form (template below)
4. Submit — private, only visible to repo admins

Alternatively: `scap3sh4rk+darkvault@gmail.com` — subject line `[darkVault Security]`

### Response timeline

| Stage | Timeframe |
|-------|-----------|
| Acknowledgement | 72 hours |
| Initial assessment | 7 days |
| Fix timeline communicated | 14 days |
| Patch released (Critical/High) | 30 days |
| CVE published | After patch release |

---

## Vulnerability Report Template

```
Component affected: (e.g. CryptoManager, AuthViewModel, DriveRepository)
Vulnerability type: (e.g. CWE-326, CWE-321, CWE-330)
Android version tested:
darkVault version/commit:
Description:
Steps to reproduce:
Impact: What can an attacker achieve?
Suggested fix (optional):
CVSS v3.1 estimate (optional):
```

---

## Disclosure Policy

Coordinated disclosure:

1. Reporter submits via private advisory or email.
2. Maintainer acknowledges within 72 hours.
3. Fix developed in GitHub temporary private fork.
4. Patch released. Advisory published simultaneously.
5. CVE ID requested from GitHub CNA — issued within 72 hours of advisory publication.
6. Reporter credited in advisory (with consent).

---

## Scope

**In scope** (valid for CVE + advisory):

- Cryptographic flaws: key reuse, IV reuse, weak KDF, broken GCM auth tag handling
- Authentication bypass: brute-force protection bypass, session fixation
- Plaintext exposure: temp files not deleted, key material in logs, DataStore backup not excluded
- Drive API token leakage
- Path traversal via `originalName` from Drive `appProperties`
- Privilege escalation: accessing DEK without password
- Intent-based attacks: exported components accepting sensitive data

**Out of scope**:

- Attacks requiring physical access with USB debugging on rooted device
- Attacks requiring attacker to already have user's Google credentials
- DoS via Drive quota exhaustion
- Vulnerabilities in Google Drive infrastructure
- Social engineering

---

## Security Design Decisions

**Why Google Drive, not a custom server?**
Zero-server design eliminates server-side attack surface. No account registration, no server to breach, no central key store. Trade-off: file metadata (names) visible to Google.

**Why not encrypt filenames?**
Drive `appProperties` API requires plaintext to locate files. Encrypting filenames requires a local index, defeating the no-local-database design goal. Accepted trade-off. Users can rename files before upload if filename privacy is critical.

**Why PBKDF2 not Argon2id?**
Argon2id requires external native lib (not in `javax.crypto`). Design avoids external crypto dependencies to reduce supply chain risk. PBKDF2WithHmacSHA256 at 100k iterations is within NIST SP 800-132 guidance. Argon2id tracked as future enhancement.

**Why is recovery key shown only once?**
Recovery key is the only vault escape if master password is forgotten. Repeated display encourages insecure storage patterns. If lost, vault contents are permanently inaccessible — by design.

---

## Known Security Properties (for auditors)

- Constant-time hash comparison: `MessageDigest.isEqual()`
- Key zeroing: `Arrays.fill(keyBytes, 0)` in `finally` blocks after every crypto op
- No key material in logs: verified by automated grep in CI
- Drive scope: `drive.file` only (narrowest scope)
- Network security config: `cleartextTrafficPermitted="false"`
- DataStore excluded from backup: `android:allowBackup="false"` + `backup_rules.xml`
- Biometric: Android Keystore-backed key + `BiometricPrompt.CryptoObject` — DEK never exposed to biometric subsystem

---

## Past Advisories

| ID | Title | Severity | Fixed in |
|----|-------|----------|---------|
| — | No public advisories yet | — | — |

---

## Contact

visit the site and click contact - https://parthivkumarnikku.github.io/portfolio/ 

Security: `scap3sh4rk+darkvault@gmail.com`
Advisory portal: `https://github.com/scap3sh4rk/darkVault/security/advisories`
