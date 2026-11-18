package com.rton.expensebucket.nfc

import android.nfc.Tag
import android.nfc.tech.NfcF
import java.io.IOException

object TransitCardReader {
    fun read(tag: Tag): TransitCardScan {
        val technology = NfcF.get(tag) ?: throw IOException("NFC-F card required")
        return technology.use {
            it.connect()
            TransitFelicaProtocol.read(it.systemCode, tag.id, it::transceive)
        }
    }
}
