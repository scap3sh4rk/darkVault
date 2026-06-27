package com.darkvault.app

import com.darkvault.app.cache.EncryptedFileCache
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

/**
 * In-memory session holder. Set on unlock, cleared on lock or process death.
 * Used by UploadForegroundService to access credentials without disk storage.
 *
 * DEK (Data Encryption Key) is loaded from vault.key on Drive after unlock and stored here.
 * It is zeroed out and nulled on lock via [clearDek].
 */
object VaultSession {
    @Volatile var masterPassword: String? = null
    @Volatile var signedInAccount: GoogleSignInAccount? = null

    /** The vault DEK (256-bit AES key). Non-null after successful unlock with Drive access. */
    @Volatile var dek: ByteArray? = null

    /** Zeros out the DEK and clears all session-scoped caches. Call on lock/sign-out. */
    fun clearDek() {
        dek?.let { java.util.Arrays.fill(it, 0) }
        dek = null
        EncryptedFileCache.clear()
    }
}
