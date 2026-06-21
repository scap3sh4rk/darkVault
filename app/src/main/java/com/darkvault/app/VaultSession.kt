package com.darkvault.app

import com.google.android.gms.auth.api.signin.GoogleSignInAccount

/**
 * In-memory session holder. Set on unlock, cleared on lock or process death.
 * Used by UploadForegroundService to access credentials without disk storage.
 */
object VaultSession {
    @Volatile var masterPassword: String? = null
    @Volatile var signedInAccount: GoogleSignInAccount? = null
}
