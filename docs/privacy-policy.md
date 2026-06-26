---
title: "Privacy Policy"
---

# Privacy Policy

**Effective Date:** June 26, 2025
**Last Updated:** June 26, 2025



# 1. Overview

darkVault is a zero-knowledge encrypted file backup application for Android.

Files are encrypted locally on your device using **AES-256-GCM** before being uploaded to your personal Google Drive account. The developer cannot access, read, or decrypt your files under any circumstances.

---

# 2. Information We Collect

darkVault is designed with a strict **no-collection policy**. The application does **not** collect, transmit, or share any personal data with the developer or any third party.

## 2.1 Information Stored Locally on Your Device

The following information is stored exclusively on your device in Android's secure application storage and is never transmitted to the developer:

* A PBKDF2-derived cryptographic hash of your Master Password (the password itself is never stored)
* A randomly generated cryptographic salt used for key derivation
* A vault setup completion flag
* Encrypted file metadata required for application functionality

## 2.2 Information Stored in Your Google Drive

Encrypted `.vault` files are uploaded to a dedicated **darkVault** folder within your own Google Drive account.

These files are encrypted on your device before upload. The developer has **no access** to your Google Drive account and no ability to read these files.

## 2.3 Information We Do Not Collect

darkVault does **not** collect:

* Analytics or usage data
* Crash reports or diagnostic information
* Device identifiers
* Location data
* Contact lists or personal information
* Advertising identifiers
* Financial or payment information

---

# 3. Zero-Knowledge Architecture

darkVault is built around a **zero-knowledge** architecture.

Your Master Password never leaves your device. All encryption and decryption occur entirely on your device. The developer has no technical capability to access your files or recover your password. This is an intentional architectural design rather than a limitation.

### Cryptographic Properties

| Component            | Implementation                                                                               |
| -------------------- | -------------------------------------------------------------------------------------------- |
| Encryption Algorithm | AES-256-GCM                                                                                  |
| Key Derivation       | PBKDF2WithHmacSHA256 using a unique random salt per user                                     |
| Master Password      | Never stored in any form; only a one-way cryptographic verification hash is retained locally |
| Encryption Keys      | Derived locally and held in memory only during an active session                             |

---

# 4. Google Drive Integration

darkVault uses Google Drive as its storage backend through the official Google Drive API.

The application requests only the **`drive.file`** permission scope, limiting access exclusively to files created by darkVault. The application cannot access any other files stored in your Google Drive account.

Your use of Google Drive remains subject to Google's own Privacy Policy and Terms of Service.

Google account authentication is handled entirely by Android and Google's authentication libraries. darkVault never receives or stores your Google account password.

---

# 5. Data Sharing and Disclosure

darkVault does **not** share user data with third parties.

There are:

* No advertising partners
* No analytics providers
* No data brokers
* No third-party tracking services
* No external SDKs used for data collection

The developer may be required to respond to lawful requests under applicable Indian law, including the Information Technology Act, 2000 and the Digital Personal Data Protection Act, 2023.

However, because the developer has no access to your encrypted files or your Master Password, there is no meaningful user data available to disclose.

---

# 6. Data Security

Security is the primary design objective of darkVault.

The application implements the following protections:

* AES-256-GCM encryption with a unique initialization vector (IV) for every encrypted file
* Master Password never stored in plaintext
* Password verification performed using a one-way cryptographic hash
* Encryption keys exist only in memory during an active unlocked session
* Automatic vault lock when the application is backgrounded or terminated
* `FLAG_SECURE` enabled on sensitive screens to prevent screenshots and recent-app previews
* HTTPS-only communication with Google Drive API endpoints

> **Important Notice**
>
> Because darkVault follows a zero-knowledge architecture, there is **no password recovery mechanism**.
>
> If you forget your Master Password, your encrypted files cannot be decrypted.
>
> You are solely responsible for securely storing your Master Password and Recovery Key.

---

# 7. Children's Privacy

darkVault is not directed toward children under 13 years of age.

The application does not knowingly collect personal information from children.

If you believe a child has used the application, please contact the developer using the contact information provided below.

---

# 8. Compliance with the Digital Personal Data Protection Act, 2023 (India)

darkVault is designed to comply with the principles of the Digital Personal Data Protection Act, 2023 (DPDP Act).

### Data Minimisation

The application collects no personal data beyond what is strictly necessary for local cryptographic verification.

### Purpose Limitation

Any locally stored information is used exclusively for vault authentication and encrypted file management.

### Data Principal Rights

Because the developer stores no personal user data, requests relating to access, correction, or erasure should instead be directed toward your own device storage and Google Drive account, both of which remain under your control.

### Storage Limitation

No personal data is stored on developer-controlled servers.

### Grievance Redressal

Privacy-related grievances may be submitted using the contact information provided in Section 11.

---

# 9. Compliance with the Information Technology Act, 2000 (India)

darkVault complies with the Information Technology Act, 2000 and the Information Technology (Reasonable Security Practices and Procedures and Sensitive Personal Data or Information) Rules, 2011.

The application implements reasonable security practices appropriate to the sensitivity of protected data, including industry-standard encryption mechanisms.

---

# 10. Changes to This Privacy Policy

The developer reserves the right to update this Privacy Policy at any time.

Any revisions will be published through:

* GitHub
* Google Play Store
* Samsung Galaxy Store

The **Effective Date** at the top of this document will always reflect the latest version.

Continued use of darkVault after changes are published constitutes acceptance of the revised Privacy Policy. If you disagree with any changes, you should discontinue use of the application.

---

# 11. Contact and Grievance Redressal

For privacy-related questions, concerns, or grievances:

**Developer**
Parthiv Kumar Nikku

**Email**
[parthivkumarnikku@gmail.com](mailto:parthivkumarnikku@gmail.com)

**GitHub**
https://github.com/scap3sh4rk/darkVault

Grievances submitted under the Digital Personal Data Protection Act, 2023 or the Information Technology Act, 2000 will be acknowledged within **72 hours**, and reasonable efforts will be made to resolve them within **30 days**.

---

# 12. Governing Law

This Privacy Policy is governed by and construed in accordance with the laws of India.

Any disputes arising in connection with this Privacy Policy shall be subject to the jurisdiction of the competent courts in India.
