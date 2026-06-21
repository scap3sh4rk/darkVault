package com.darkvault.app.model

import android.util.Base64

/**
 * Represents the serialized vault.key file stored on Drive.
 * Contains the DEK wrapped twice: once with the password KEK, once with the recovery key.
 * Also contains the KEK salt needed to re-derive the KEK from the master password.
 *
 * JSON format stored in vault.key:
 * {
 *   "version": 1,
 *   "kekSalt": "<base64-16-bytes>",
 *   "dekWrappedByKek": "<base64-60-bytes>",
 *   "dekWrappedByRecovery": "<base64-60-bytes>"
 * }
 */
data class VaultKeyBundle(
    val version: Int = 1,
    val kekSalt: ByteArray,
    val dekWrappedByKek: ByteArray,
    val dekWrappedByRecovery: ByteArray
) {
    fun toJson(): String {
        val saltB64 = Base64.encodeToString(kekSalt, Base64.NO_WRAP)
        val kekB64 = Base64.encodeToString(dekWrappedByKek, Base64.NO_WRAP)
        val recB64 = Base64.encodeToString(dekWrappedByRecovery, Base64.NO_WRAP)
        return """{"version":$version,"kekSalt":"$saltB64","dekWrappedByKek":"$kekB64","dekWrappedByRecovery":"$recB64"}"""
    }

    companion object {
        fun fromJson(json: String): VaultKeyBundle {
            // Simple manual parse to avoid Gson dependency in the model layer
            fun extract(key: String): String {
                val marker = "\"$key\":\""
                val start = json.indexOf(marker) + marker.length
                val end = json.indexOf('"', start)
                return json.substring(start, end)
            }
            val version = json.substringAfter("\"version\":").substringBefore(",").trim().toInt()
            val salt = Base64.decode(extract("kekSalt"), Base64.NO_WRAP)
            val kek = Base64.decode(extract("dekWrappedByKek"), Base64.NO_WRAP)
            val rec = Base64.decode(extract("dekWrappedByRecovery"), Base64.NO_WRAP)
            return VaultKeyBundle(version, salt, kek, rec)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultKeyBundle) return false
        return version == other.version &&
            kekSalt.contentEquals(other.kekSalt) &&
            dekWrappedByKek.contentEquals(other.dekWrappedByKek) &&
            dekWrappedByRecovery.contentEquals(other.dekWrappedByRecovery)
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + kekSalt.contentHashCode()
        result = 31 * result + dekWrappedByKek.contentHashCode()
        result = 31 * result + dekWrappedByRecovery.contentHashCode()
        return result
    }
}
