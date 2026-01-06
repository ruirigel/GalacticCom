package com.rmrbranco.galacticcom

import android.os.Build
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

object CryptoManager {

    private const val KEY_AGREEMENT_ALGORITHM = "ECDH"
    private const val KEY_PAIR_ALGORITHM = "EC"
    private const val SECRET_KEY_ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12 // 12 bytes for GCM is standard and recommended

    // Generates a new Elliptic Curve key pair for Diffie-Hellman
    fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(KEY_PAIR_ALGORITHM)
        keyPairGenerator.initialize(256) // Using a common curve size
        return keyPairGenerator.generateKeyPair()
    }

    // Computes the shared secret using our private key and their public key
    fun getSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): SecretKey {
        val keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM)
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        val sharedSecretBytes = keyAgreement.generateSecret()
        
        // It's crucial to use a key derivation function (KDF) in a real-world scenario.
        // For simplicity here, we'll hash the secret to ensure it has the correct length for AES-256.
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(sharedSecretBytes)

        return SecretKeySpec(keyBytes, SECRET_KEY_ALGORITHM)
    }

    fun encrypt(data: String, secretKey: SecretKey): String? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv // GCM generates a unique IV
            val encryptedData = cipher.doFinal(data.toByteArray())

            // Prepend the IV to the encrypted data for use during decryption
            val combined = iv + encryptedData
            encodeBase64(combined)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decrypt(encryptedString: String, secretKey: SecretKey): String? {
        return try {
            val combined = decodeBase64(encryptedString)

            // Extract the IV and the encrypted data
            val iv = combined.copyOfRange(0, IV_SIZE)
            val encryptedData = combined.copyOfRange(IV_SIZE, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv) // 128 is the tag size
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedData = cipher.doFinal(encryptedData)
            String(decryptedData)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- Key Encoding/Decoding Helpers ---

    fun encodeKeyToBase64(key: Key): String {
        return encodeBase64(key.encoded)
    }

    fun decodePublicKeyFromBase64(encodedKey: String): PublicKey {
        val keyBytes = decodeBase64(encodedKey)
        val keyFactory = KeyFactory.getInstance(KEY_PAIR_ALGORITHM)
        return keyFactory.generatePublic(X509EncodedKeySpec(keyBytes))
    }

    // --- Base64 Helpers for Android Version Compatibility ---

    private fun encodeBase64(data: ByteArray): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString(data)
        } else {
            android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
        }
    }

    private fun decodeBase64(data: String): ByteArray {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Base64.getDecoder().decode(data)
        } else {
            android.util.Base64.decode(data, android.util.Base64.NO_WRAP)
        }
    }
}