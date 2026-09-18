package eu.kanade.tachiyomi.lib.cryptoaes

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoAES {

    fun decrypt(cipherText: String, password: String): String {
        val cipherData = Base64.decode(cipherText, Base64.DEFAULT)
        val saltHeader = "Salted__".toByteArray(Charsets.US_ASCII)

        // Check if the data starts with the "Salted__" header
        if (cipherData.size < 16 || !cipherData.copyOfRange(0, 8).contentEquals(saltHeader)) {
            throw IllegalArgumentException("Invalid CryptoJS encrypted data (missing Salted__ header)")
        }

        val salt = cipherData.copyOfRange(8, 16)
        val encryptedData = cipherData.copyOfRange(16, cipherData.size)

        // EvpKDF key derivation
        val (key, iv) = evpKDF(password.toByteArray(Charsets.UTF_8), salt)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val decryptedBytes = cipher.doFinal(encryptedData)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun evpKDF(password: ByteArray, salt: ByteArray, keySize: Int = 32, ivSize: Int = 16): Pair<ByteArray, ByteArray> {
        val md = MessageDigest.getInstance("MD5")
        val derivedKeyData = ByteArray(keySize + ivSize)

        var lastHash = ByteArray(0)
        var offset = 0
        val totalSize = keySize + ivSize

        while (offset < totalSize) {
            md.reset()
            md.update(lastHash)
            md.update(password)
            md.update(salt)
            lastHash = md.digest()

            val bytesToCopy = minOf(lastHash.size, totalSize - offset)
            System.arraycopy(lastHash, 0, derivedKeyData, offset, bytesToCopy)
            offset += bytesToCopy
        }

        val key = derivedKeyData.copyOfRange(0, keySize)
        val iv = derivedKeyData.copyOfRange(keySize, keySize + ivSize)
        return Pair(key, iv)
    }
}
