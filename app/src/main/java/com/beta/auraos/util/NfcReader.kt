package com.beta.auraos.util

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag

/** Foreground NFC reading for the screens that take a member's card. */
object NfcReader {

    private val techLists = arrayOf(
        arrayOf("android.nfc.tech.NfcA"),
        arrayOf("android.nfc.tech.NfcB"),
        arrayOf("android.nfc.tech.NfcF"),
        arrayOf("android.nfc.tech.NfcV"),
        arrayOf("android.nfc.tech.IsoDep"),
        arrayOf("android.nfc.tech.MifareClassic"),
        arrayOf("android.nfc.tech.MifareUltralight"),
        arrayOf("android.nfc.tech.Ndef")
    )

    /** Routes taps to [activity]'s onNewIntent while it is in the foreground. */
    fun enable(activity: Activity, adapter: NfcAdapter?) {
        val intent = Intent(activity, activity.javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            activity, 0, intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        adapter?.enableForegroundDispatch(activity, pendingIntent, null, techLists)
    }

    fun disable(activity: Activity, adapter: NfcAdapter?) {
        adapter?.disableForegroundDispatch(activity)
    }

    /** The card's UID in hex, as memberships store it, or null when [intent] is not a tap. */
    fun cardUuid(intent: Intent): String? {
        if (NfcAdapter.ACTION_TECH_DISCOVERED != intent.action &&
            NfcAdapter.ACTION_TAG_DISCOVERED != intent.action
        ) return null
        @Suppress("DEPRECATION")
        val tag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return null
        return tag.id.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }
}
