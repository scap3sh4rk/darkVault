package com.darkvault.app.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.tech.NdefFormatable
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

enum class NfcTagType { WRITABLE, READONLY, UNKNOWN }

data class NfcTagEvent(val tag: Tag, val type: NfcTagType)

object NfcTagManager {

    // MIME type for secrets written to writable tags
    const val NFC_MIME_TYPE = "application/x-darkvault-nfc-key"

    // EMV PPSE SELECT APDU — gives card-specific response beyond just the UID
    private val PPSE_APDU = byteArrayOf(
        0x00, 0xA4.toByte(), 0x04, 0x00,
        0x0E,
        0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31,
        0x00
    )

    private val _tagFlow = MutableSharedFlow<NfcTagEvent>(extraBufferCapacity = 1)
    val tagFlow: SharedFlow<NfcTagEvent> = _tagFlow

    // NfcAdapter.getDefaultAdapter(Context) is deprecated on API 33+; use NfcManager instead.
    private fun adapter(ctx: Context): NfcAdapter? =
        ctx.getSystemService(NfcManager::class.java)?.defaultAdapter

    /** NFC hardware exists on this device (adapter non-null), regardless of whether it is on. */
    fun isHardwarePresent(ctx: Context): Boolean = adapter(ctx) != null

    /** NFC hardware is present AND currently enabled in system settings. */
    fun isAvailable(ctx: Context): Boolean = adapter(ctx)?.isEnabled == true

    fun enableForeground(activity: Activity) {
        val adapter = adapter(activity) ?: return
        val intent = Intent(activity, activity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flag = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getActivity(activity, 0, intent, flag)
        adapter.enableForegroundDispatch(activity, pi, null, null)
    }

    fun disableForeground(activity: Activity) {
        adapter(activity)?.disableForegroundDispatch(activity)
    }

    fun onNewIntent(intent: Intent, scope: CoroutineScope) {
        val tag = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        } ?: return
        val type = classifyTag(tag)
        scope.launch { _tagFlow.emit(NfcTagEvent(tag, type)) }
    }

    /**
     * Classifies the tag at scan time (no I/O needed — tech list is already available).
     * WRITABLE: NDEF writable, or formatable (blank tag the app can write to)
     * READONLY: IsoDep without writable NDEF — typical payment/bank cards
     */
    fun classifyTag(tag: Tag): NfcTagType {
        val ndef = Ndef.get(tag)
        val ndefFormatable = NdefFormatable.get(tag)
        val isoDep = IsoDep.get(tag)
        return when {
            ndef != null && ndef.isWritable -> NfcTagType.WRITABLE
            ndefFormatable != null -> NfcTagType.WRITABLE
            isoDep != null -> NfcTagType.READONLY
            else -> NfcTagType.UNKNOWN
        }
    }

    /**
     * Reads the 32-byte secret previously written by [writeSecret].
     * Returns null if the tag has no darkVault MIME record.
     * Must run on Dispatchers.IO.
     */
    suspend fun readSecret(tag: Tag): ByteArray? = withContext(Dispatchers.IO) {
        val ndef = Ndef.get(tag) ?: return@withContext null
        try {
            ndef.connect()
            val msg = ndef.ndefMessage ?: return@withContext null
            msg.records.firstOrNull { record ->
                record.tnf == NdefRecord.TNF_MIME_MEDIA &&
                    String(record.type, Charsets.US_ASCII) == NFC_MIME_TYPE
            }?.payload
        } catch (e: Exception) {
            null
        } finally {
            runCatching { ndef.close() }
        }
    }

    /**
     * Writes a 32-byte [secret] to a writable NFC tag as a darkVault MIME NDEF record.
     * Handles both already-formatted tags and blank (NdefFormatable) tags.
     * Must run on Dispatchers.IO.
     */
    suspend fun writeSecret(tag: Tag, secret: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val record = NdefRecord.createMime(NFC_MIME_TYPE, secret)
        val message = NdefMessage(record)

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return@withContext try {
                ndef.connect()
                if (!ndef.isWritable) return@withContext false
                ndef.writeNdefMessage(message)
                true
            } catch (e: Exception) {
                false
            } finally {
                runCatching { ndef.close() }
            }
        }

        val formatable = NdefFormatable.get(tag) ?: return@withContext false
        try {
            formatable.connect()
            formatable.format(message)
            true
        } catch (e: Exception) {
            false
        } finally {
            runCatching { formatable.close() }
        }
    }

    /**
     * Derives a stable identifier from any tag for use as the NFC secret in readonly mode.
     *
     * IsoDep tags (bank/payment cards): SHA-256(UID || PPSE_response) — PPSE bytes require the
     * physical card to obtain and are stable per card.
     *
     * Non-IsoDep tags (writable NFC tags enrolled as read-only): SHA-256(UID) — the UID is
     * factory-locked on NTAG/Mifare tags and unique enough when combined with the 32-byte
     * binding salt during PBKDF2 derivation.
     *
     * Must run on Dispatchers.IO.
     */
    fun readCardIdentifier(tag: Tag): ByteArray? {
        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            // Writable tag being used as read-only — identify by UID (factory-locked)
            val uid = tag.id ?: return null
            return MessageDigest.getInstance("SHA-256").digest(uid)
        }
        return try {
            isoDep.connect()
            isoDep.timeout = 3_000
            val ppseResponse = try {
                isoDep.transceive(PPSE_APDU)
            } catch (e: Exception) {
                "readonly-fallback".toByteArray(Charsets.UTF_8)
            }
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(tag.id)
            digest.update(ppseResponse)
            digest.digest()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { isoDep.close() }
        }
    }
}
