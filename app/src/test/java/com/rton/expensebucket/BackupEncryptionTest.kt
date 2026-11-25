package com.rton.expensebucket

import com.rton.expensebucket.data.backup.BackupEncryption
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class BackupEncryptionTest {
    @Test
    fun encryptedBackup_canBeRestoredUsingOnlyThePasswordAndFile() {
        val payload = """{"note":"台北午餐"}""".toByteArray()
        val salt = BackupEncryption.newSalt()
        val key = BackupEncryption.deriveKey("備份專用的一組長密碼".toCharArray(), salt)
        val first = BackupEncryption.encrypt(payload, salt, key)
        val second = BackupEncryption.encrypt(payload, salt, key)

        val recoveredKey = BackupEncryption.deriveKey(
            "備份專用的一組長密碼".toCharArray(), BackupEncryption.salt(first)
        )
        assertFalse(first.contentEquals(second))
        assertArrayEquals(payload, BackupEncryption.decrypt(first, recoveredKey))
    }

    @Test
    fun encryptedBackup_rejectsInvalidPasswordAndModifiedContent() {
        val salt = BackupEncryption.newSalt()
        val key = BackupEncryption.deriveKey("correct password".toCharArray(), salt)
        val wrongKey = BackupEncryption.deriveKey("different password".toCharArray(), salt)
        val encrypted = BackupEncryption.encrypt(byteArrayOf(1, 2, 3), salt, key)

        assertThrows(AEADBadTagException::class.java) { BackupEncryption.decrypt(encrypted, wrongKey) }
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) { BackupEncryption.decrypt(encrypted, key) }
        assertThrows(IllegalArgumentException::class.java) { BackupEncryption.salt(byteArrayOf(1, 2, 3)) }
    }
}
