---
title: FAQ
---

# Frequently Asked Questions

---

## Security & Encryption

### 1. If my Google Drive account gets hacked and the attacker gets vault.key — can they crack my password?

**Yes, theoretically — but only if your password is weak.**

Here is exactly what happens. When the attacker gets `vault.key`, they have:
- Your DEK encrypted (wrapped) with a key derived from your password
- A random salt value (`kekSalt`) used in that derivation

To read your files, they need to reverse-engineer your master password. The only way to do that is to guess it — trying every possible password in a loop. Each guess requires running PBKDF2 with 100,000 rounds of SHA-256, which is deliberately slow.

```
Attacker's loop:
  for each candidate_password:
      kek = PBKDF2(candidate_password, kekSalt, 100,000 iterations)
      try: dek = AES_GCM_unwrap(dekWrappedByKek, kek)
      if success: password found — stop
```

How long does that take?

| Password type | Time to crack (modern GPU cluster) |
|--------------|-----------------------------------|
| 6-digit PIN | Under 1 second |
| 8-char common word | Hours to days |
| 10-char mixed random | Months to years |
| 4-word passphrase | Billions of years |
| Random 128-bit key | Computationally infeasible |

**The bottom line:** `vault.key` leaking is a real threat. PBKDF2 at 100,000 iterations is the speed bump. Your password is the wall. A strong passphrase (4+ random words) makes this attack practically impossible. A 6-digit PIN makes it trivial.

Also important: getting `vault.key` alone is not enough to read your files. The attacker also needs your encrypted `.vault` files (also on Drive) and must successfully crack your password. Two conditions must both be met.

---

### 2. If vault.key is lost from Google Drive, can I still recover my data?

**It depends on whether `vault.key` is actually gone or just inaccessible.**

First, understand what `vault.key` contains: your encrypted DEK — the actual key that encrypts your files — stored in a locked form that your password and Recovery Key can open.

```
 Your Recovery Key ──► can unlock dekWrappedByRecovery
                                   │
                                   └── BUT this blob lives INSIDE vault.key
                                       If vault.key is gone, so is the blob
```

**Scenario table:**

| Situation | Outcome |
|-----------|---------|
| Forgot password, `vault.key` still on Drive, have Recovery Key | ✅ Fully recoverable |
| `vault.key` deleted from Drive, have Recovery Key | ❌ Permanent loss — Recovery Key has nothing to decrypt |
| `vault.key` deleted, have a local backup of `vault.key` | ✅ Re-upload it to the vault folder, then use password or Recovery Key |
| `vault.key` deleted, no backup, no Recovery Key | ❌ Permanent loss |

**The Recovery Key is a key to a door. If the door (vault.key) is gone, the key is useless.**

**What to do right now:** Keep a local backup of `vault.key`. You can download it from your Google Drive darkVault folder and save it somewhere safe — an external drive, a different cloud service, or a USB stick. It cannot be used to read your files without your master password or Recovery Key.

---

### 3. Does Google have access to my files?

No. Files are encrypted on your device before upload. Google only ever receives scrambled bytes — they have no key and no way to unscramble your files. Even if Google's infrastructure was fully compromised, your files would be unreadable without your master password.

---

### 4. What encryption does darkVault use?

AES-256-GCM — the same algorithm used by the US government for Top Secret data, major cloud providers, and end-to-end encrypted messaging apps. The 256-bit key size means there are 2²⁵⁶ possible keys — more than the number of atoms in the observable universe. The GCM (Galois/Counter Mode) variant also detects any tampering with the ciphertext.

---

### 5. Is there any way to read my files without my password?

Only with the Recovery Key shown at setup. There is no backdoor, no master key, no "forgot password" email — darkVault intentionally has no mechanism to recover your files without one of these two secrets. This is by design: a backdoor that works for you also works for an attacker.

---

### 6. What happens to my encryption key when I lock the app?

When you lock the app manually or the auto-lock timer fires without biometric enabled, the DEK (Data Encryption Key) is **zeroed** from RAM — every byte of the 32-byte key array is overwritten with zeros using `Arrays.fill(dek, 0)`, then the reference is set to null. The key is not recoverable from memory after this point.

When biometric or NFC unlock is enabled and the app locks automatically (e.g., you switch to another app), the DEK remains in RAM but the app enters `AppLocked` state — a fingerprint or NFC tag is required to resume. The DEK is still cleared when you lock manually.

---

### 7. Can someone brute-force my password directly in the app?

No. The app enforces **exponential backoff** on failed unlock attempts:

- After 5 failed attempts: 30-second lockout
- Each additional failure doubles the lockout (30s → 60s → 2 min → 4 min → …)
- Maximum lockout: 30 minutes

This makes in-app brute-force attacks impractical. Offline attacks against `vault.key` (see the first FAQ) are not rate-limited, which is why password strength matters more than app-level rate limiting.

---

## Recovery Key

### 8. Where should I store my Recovery Key?

Somewhere separate from your Google Drive and your phone. Good options:

- Written on paper, stored in a safe or secure location
- In a different password manager (not the one on your phone)
- On an encrypted USB drive in a physically secure location

**Do not store it in your Google Drive** — if your Drive is compromised (the main attack scenario), the attacker would have both `vault.key` and your Recovery Key, giving them direct access to your DEK without even needing your password.

---

### 9. I didn't save my Recovery Key. What can I do?

If you still know your master password, you can rotate the Recovery Key from Settings → Security → Rotate Recovery Key. This generates a new random Recovery Key, re-wraps the DEK with it, and uploads the updated `vault.key` to Drive. You will be shown the new key once.

If you have both forgotten your password AND lost your Recovery Key, there is no recovery path. Your files are permanently inaccessible.

---

### 10. Does the Recovery Key change if I change my password?

No. The Recovery Key wraps the DEK independently of the password. When you change your password, only `dekWrappedByKek` is updated in `vault.key`. The `dekWrappedByRecovery` blob (and thus the Recovery Key) stays the same. Your old Recovery Key remains valid after a password change.

---

## vault.key

### 11. What is vault.key and should I be worried about it?

`vault.key` is a small JSON file (~200 bytes) stored in your darkVault folder on Google Drive. It contains your encryption key (the DEK) in a locked form — locked with your master password and your Recovery Key.

You should not delete it, and you should be aware it exists. It is not a file you downloaded — it is the lockbox that holds the key to all your other files. Without it, your encrypted files are permanently unreadable.

If someone gets `vault.key`, they cannot immediately read your files. They would need to crack your master password first (see the first FAQ). A strong password makes this attack computationally infeasible.

---

### 12. Can I back up vault.key?

Yes, and you should. Download it from your darkVault folder on Google Drive and keep it somewhere safe. By itself, `vault.key` is useless without your master password or Recovery Key. Keeping a copy protects you against accidental deletion.

---

### 13. What if vault.key gets corrupted?

If `vault.key` becomes unreadable (corrupted JSON or truncated file), the app will be unable to unwrap the DEK and you will be locked out of your files. The same recovery path applies as deletion: if you have a backup copy, re-upload it; otherwise, there is no recovery.

---

## Biometric Unlock

### 14. Is biometric unlock less secure than password unlock?

No — biometric unlock uses your master password behind the scenes. When you enroll your fingerprint, darkVault encrypts your master password with a key stored in the Android Keystore (a hardware-backed secure element on supported devices). Your fingerprint unlocks the Keystore key, which decrypts the master password, which then works exactly as normal.

The session timeout clock does NOT reset on biometric unlocks, so you cannot bypass the maximum session limit by repeatedly using your fingerprint.

---

### 15. What happens if I add a new fingerprint to my phone?

The Android Keystore automatically **invalidates** the darkVault biometric key when new biometrics are enrolled. The next time you try biometric unlock, it will fail and you will be asked to re-enroll using your master password. This is intentional — a new fingerprint could belong to someone else who added it without your knowledge.

---

### 16. Can I use darkVault without biometric unlock?

Yes. Biometric is entirely optional and can be enabled or disabled in Settings at any time. Without it, you enter your master password every time you unlock.

---

## App Behaviour

### 17. What happens if I change my Google account?

Switching Google accounts clears all local state: the stored PBKDF2 hash, salt, biometric credentials, cached vault folder ID, and the in-memory DEK. The app restarts from the sign-in screen. Your files on the old account's Drive are unaffected and accessible if you sign back in.

---

### 18. What happens if I use darkVault on a new phone?

Sign in with your Google account. The app will find `vault.key` on your Drive, ask for your master password, unwrap the DEK, and you will have access to all your files. No data transfer between phones is required.

---

### 19. Can darkVault work offline?

Yes — for files you have marked as available offline (pinned files).

Here is exactly how it works:

**After every successful online unlock,** darkVault saves a small bundle to your device's private storage:
- `cached_kek_salt` — the 16-byte salt used in key derivation
- `cached_wrapped_dek` — your encryption key (DEK), locked with the same password you use to unlock

This bundle is **pure ciphertext** — it is useless without your master password. Nothing that could decrypt your files is stored in plain form.

**When you unlock offline,** darkVault:
1. Checks your password against the locally stored hash (no network needed)
2. Re-derives the KEK from your password + cached salt
3. Unwraps the DEK from the cached bundle
4. Loads the DEK into memory — exactly as if you had unlocked online

Once the DEK is in memory, any file stored in the local encrypted cache (i.e., pinned files) can be decrypted and viewed normally.

**What works offline:**
- Unlocking with your master password
- Viewing pinned (offline-available) files
- Browsing the cached folder listing

**What does NOT work offline:**
- Uploading new files (requires Drive)
- Viewing non-pinned files (not cached locally)
- Password change, recovery key rotation, or any Drive operation

### 20. How do I make files available offline?

Long-press any file in the home screen and tap **"Make available offline"** (or the pin icon). darkVault downloads and saves the encrypted file to your device's private storage. It will be accessible the next time you unlock offline, as long as you have unlocked online at least once since pinning it.

Pinned files are excluded from the automatic LRU cache eviction — they stay on your device until you explicitly unpin them or permanently delete them.

### 21. Does the offline cache store my files in plaintext?

No. The local cache stores files in exactly the same encrypted format as they exist on Google Drive — AES-256-GCM encrypted with your DEK. The only difference is that the files are stored in your device's app-private `filesDir` rather than on Drive. They require the DEK to decrypt, which requires your master password to unlock.

The local cache is also excluded from Android's automatic backup system — it is never uploaded to Google's backup servers.

### 22. Can I use NFC to unlock darkVault?

Yes, if your device supports NFC and you enable it in Settings. darkVault supports two types of NFC sources:

**Writable NFC tags (blank NDEF tags)**
The app writes a randomly generated 32-byte secret to the tag. When you tap it, the app reads that secret and uses it as your unlock credential. Treat this tag like a physical key — if someone gets hold of it, they can unlock your vault (but they also need your device, which has its own screen lock).

**Bank cards, transit cards, and other read-only cards**
You do not need a blank NFC tag. Any ISO-DEP card (bank card, transit card, official ID with NFC) can be enrolled. The app sends a standard EMV query to the card and computes a `SHA-256` hash of the card's hardware identifier and its response. This hash becomes your unlock credential.

**Nothing is ever written to your bank card.** The card is only read from, and it has no way of knowing it is being used by this app.

**What is stored on your device:**
A cryptographic hash derived from the card's hardware data is stored on your device, encrypted in the same way as your master password (AES-GCM, protected by the Android Keystore). The actual card data is not stored — only a one-way hash that cannot be reversed to recover the card's original values.

**Is this safe?**
Yes. The hash stored on your device is specific to your exact card. A different card of the same type will produce a different hash. The stored hash alone is not enough to unlock the vault — it is wrapped with a Keystore key that requires biometric confirmation or the physical card to use. Someone who extracts the hash from your device still cannot use it without also having the physical card present at tap time.

NFC unlock behaves identically to biometric unlock for auto-lock: when you leave the app with NFC enrolled and your session active, darkVault immediately enters the `AppLocked` state (NFC or biometric required to resume) rather than starting the full vault-lock timer.

---

### 20. Are file names private?

Currently, no. File names are stored in Google Drive's `appProperties` field as plaintext (truncated to 100 characters). Google can see your file names. Only the file contents are encrypted. This is a known limitation and is listed as planned improvement.

---

---

[← Threat Model](./threat-model.md)  |  [Back to Home](./index.md)
