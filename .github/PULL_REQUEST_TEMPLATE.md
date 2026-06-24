## Summary

<!-- One sentence: what does this PR do? -->

## Type of Change

- [ ] Bug fix
- [ ] Feature / enhancement
- [ ] Performance improvement
- [ ] UI / UX change
- [ ] Security fix (if this — contact maintainer before merging)
- [ ] Documentation
- [ ] Refactor (no behavior change)

## Related Issue

Closes #<!-- issue number -->

## What Changed

<!-- List the key changes. Be specific about files modified. -->

## Security Checklist

<!-- Every PR touching auth, crypto, or Drive must answer these. -->

- [ ] No key material (DEK, KEK, password, salt, recovery key) added to any log statement
- [ ] No plaintext written to external storage or shared storage
- [ ] No new network endpoints added outside Drive REST API v3
- [ ] `AuthViewModel`, `CryptoManager`, `VaultKeyManager`, `VaultSession` behavior unchanged (or changes reviewed)
- [ ] `android:allowBackup="false"` still set in manifest
- [ ] `FLAG_SECURE` still applied to all Activity windows

## Testing

- [ ] `./gradlew assembleDebug` passes
- [ ] `./gradlew assembleRelease` passes
- [ ] `./gradlew testDebugUnitTest` passes (all 28+ tests green)
- [ ] Manually tested on device: <!-- device + Android version -->
- [ ] Tested vault unlock → upload → download → preview flow end-to-end

## Screenshots (if UI change)

<!-- Before / After -->

## Notes for Reviewer

<!-- Anything the reviewer should pay special attention to -->
