# darkVault

**Your data, your drive, your rules.**

darkVault is a security-first Android application designed to turn your Google Drive into a high-security, encrypted vault. In an era where data breaches are common, darkVault ensures that your most sensitive files—photos, videos, and documents—remain completely private, even from the cloud provider itself.

---

## The Problem: Your Cloud isn't as Private as You Think
Most cloud storage services have the "keys" to your data. They can see your files, and if their servers are breached, so is your privacy. **darkVault solves this by implementing "Zero-Knowledge" security.** Your files are encrypted on your device using keys that *only you* possess before they ever touch the internet.

---

## Key Features

### Military-Grade Encryption
Every file you upload is protected using **AES-256-GCM encryption**. This is the same standard used by governments and financial institutions. Your data is compressed and encrypted locally, ensuring maximum security and efficient storage.

### Zero-Knowledge Architecture
*   **No Third-Party Servers:** Your data stays between your phone and your personal Google Drive.
*   **In-Memory Security:** Sensitive encryption keys are never saved to your phone's storage. They live only in protected memory while the app is open and are "zeroed out" the moment you lock the vault.
*   **Recovery Key:** Lost your password? At setup, darkVault generates a unique, high-entropy Recovery Key. Store it safely, and you'll never be locked out of your own data.

### Biometric Quick-Unlock
Security doesn't have to be a chore. Access your vault instantly using your **fingerprint or face recognition**, protected by Android's most secure biometric standards.

### Secure Media Previews
View your files without ever decrypting them to your phone's public gallery. darkVault includes built-in, secure viewers for:
*   **Images & Photos:** High-resolution viewing with pinch-to-zoom.
*   **Videos:** Direct streaming from your encrypted vault.
*   **Documents:** Full PDF support.
*   **Text & Code:** Built-in viewer for notes and scripts.

### Intelligent Organization
*   **Folder Support:** Organize your vault just like a desktop computer.
*   **Smart Search & Filters:** Quickly find what you need with categories for Images, Videos, Audio, and Documents.
*   **Recents:** Access your most frequently used files from the home screen.
*   **Grid & List Views:** Choose the layout that fits your style.

### Built for Reliability
*   **Resumable Uploads:** Uploading a large video? If your connection drops, darkVault remembers where it left off and resumes automatically.
*   **Conflict Resolution:** If you upload a file that already exists, the app intelligently asks if you'd like to rename, replace, or skip it.
*   **Soft Delete (Trash):** Accidentally deleted a file? Move it to the vault's trash and restore it later.

---

## How It Works (The Simple Version)
1.  **Unlock:** You enter your master password or use biometrics.
2.  **Encrypt:** When you select a file, darkVault scrambles it into "gibberish" on your phone.
3.  **Store:** The "gibberish" is sent to a hidden folder on your Google Drive.
4.  **Decrypt:** When you want to see the file, darkVault pulls the "gibberish" back and turns it into your file using your private key. 

**Result:** To anyone else (including Google), your files look like random noise. To you, it's your digital life, safe and sound.

---

## Visual Identity
darkVault features a sleek, **Cyberpunk-inspired UI** with high-contrast cyan accents and a dark "stealth" theme, designed to be easy on the eyes while feeling like a true digital fortress.

---

## 🚀 Getting Started
1.  Sign in with your Google Account.
2.  Set a strong Master Password.
3.  **Crucial:** Save your Recovery Key in a safe place!
4.  Start securing your digital life.

---

## Open Source & Privacy
darkVault is **100% free and open-source**. We believe that security tools should be transparent. You can audit the code, build it yourself, and verify that your data never leaves your control.

## Contributing
Contributions are welcome! If you have a feature request, bug report, or want to improve the code:
1.  **Fork** the repository.
2.  Create a new branch for your feature or fix.
3.  Submit a **Pull Request**.

Let's build a more private web together.
