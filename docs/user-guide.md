---
layout: default
title: User Guide
---

# User Guide

> **No technical knowledge required.** This page explains how darkVault protects your files using everyday language.

---

## What happens when you back up a file?

Think of it like putting a document in a lockbox before handing it to a courier.

1. You pick a file on your phone.
2. darkVault scrambles it using your master password — this happens entirely on your device.
3. The scrambled file is uploaded to your Google Drive.
4. Google receives only the scrambled version. They have no way to read it.

When you want to view the file again, darkVault downloads it and unscrambles it in memory — your original file is displayed without ever writing the unscrambled version back to your phone's storage.

---

## Your master password

Your master password is the key to everything. Think of it like the combination to a safe.

- **darkVault never stores your password anywhere** — not on your phone, not on Google Drive.
- Every time you unlock the app, your password is used to unlock the encryption key, then discarded from memory when you lock.
- If someone has your phone but not your password, your files are safe.
- **Choose a strong password.** A short or common password makes your files vulnerable if someone gets your `vault.key` file (see below).

A good master password is at least 12 characters long and uses a mix of words, numbers, and symbols — or a passphrase of 4+ random words (e.g. `violet-truck-lamp-ocean`).

---

## The vault.key file

Inside your darkVault folder on Google Drive, there is a file called `vault.key`. This file is the lockbox that holds your encryption key — but in a locked form only your password can open.

**What's inside it:**
- Your encryption key, scrambled by your master password
- Your encryption key, scrambled by your Recovery Key (as a backup)
- A random value called a "salt" used when your password is processed

**What's NOT inside it:**
- Your master password
- Your files or their contents
- Anything that lets someone decrypt your files without a key

If someone gets `vault.key`, they still cannot read your files without your master password or Recovery Key. However, they could try to guess your password offline — which is why a strong password matters.

check [FAQ →](./faq) for more about `vault.key`
[https://github.com/scap3sh4rk/darkVault/blob/main/docs/faq.md#1-if-my-google-drive-account-gets-hacked-and-the-attacker-gets-vaultkey--can-they-crack-my-password](1)
[https://github.com/scap3sh4rk/darkVault/blob/main/docs/faq.md#1-if-my-google-drive-account-gets-hacked-and-the-attacker-gets-vaultkey--can-they-crack-my-password](2)
[https://github.com/scap3sh4rk/darkVault/blob/main/docs/faq.md#1-if-my-google-drive-account-gets-hacked-and-the-attacker-gets-vaultkey--can-they-crack-my-password](11)

---

## The Recovery Key

When you first set up darkVault, the app shows you a **Recovery Key** — a long string of letters and numbers that looks like this:

```
A1B2C3D4-E5F6A7B8-C9D0E1F2-A3B4C5D6-E7F8A9B0-C1D2E3F4-A5B6C7D8-E9F0A1B2
```

**This key is shown exactly once and never again.** It is your emergency backup.

### What the Recovery Key does
If you forget your master password, you can use the Recovery Key to regain access and set a new password. Without it, forgotten password = permanent data loss.

### Where to store it
- Write it on paper and keep it somewhere safe (not in your Google Drive)
- Save it in a separate password manager
- Store it in a different, secure location from your phone

### What the Recovery Key does NOT do
- It does not let anyone access your files without also having your `vault.key` file on Google Drive
- It does not replace your master password for day-to-day use

---

## What the biometric unlock does

If you enable fingerprint unlock in Settings, darkVault stores your master password in a secure area of your phone's chip (the Android Keystore) that only unlocks with your fingerprint.

- Your fingerprint never leaves your phone
- Biometric unlock is just a convenience shortcut — it uses your master password behind the scenes
- If you add a new fingerprint to your phone, the biometric unlock is automatically invalidated for security

---

## Auto-lock

darkVault automatically locks when you leave the app. You can configure how long it waits before locking in Settings (1, 5, 15, or 30 minutes, or immediately).

When locked:
- If biometric is enabled: the app shows a fingerprint prompt. Your encryption key stays safely in memory, guarded by your fingerprint.
- If biometric is disabled: your encryption key is wiped from memory. You must enter your master password again.

---

## What happens in different scenarios

| Scenario | Are your files safe? | Can you recover? |
|----------|---------------------|-----------------|
| Someone steals your phone (no password) | ✅ Yes | — |
| Someone hacks your Google Drive account | ✅ Yes — files are scrambled | ✅ Yes — change your Drive password |
| You forget your master password | ✅ Files safe | ✅ Only if you have your Recovery Key |
| vault.key is deleted from Drive | ✅ Files safe | ❌ No — encryption key is lost forever |
| You lose your phone | ✅ Yes | ✅ Sign in on a new device |
| You lose your Recovery Key | ✅ Files safe | ⚠️ Only if you remember your master password |
| Both master password AND Recovery Key are lost | ✅ Files safe | ❌ Permanent data loss |

---

## Tips for staying safe

1. **Back up your Recovery Key** the moment the app shows it. You will not see it again.
2. **Use a strong master password.** A passphrase (4+ random words) is both strong and memorable.
3. **Do not delete vault.key from Google Drive.** It is not a file you downloaded — it is the locked container holding your encryption key.
4. **Do not share your Google Drive vault folder.** Sharing gives others access to your `vault.key` and encrypted files — a determined attacker with a weak password can then try to crack it.
5. **Enable biometric unlock** if you unlock the app frequently — it is more convenient and no less secure.

---

[← Back to Home](./)  |  [FAQ →](./faq)
