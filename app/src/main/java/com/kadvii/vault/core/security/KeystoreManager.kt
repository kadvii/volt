package com.kadvii.vault.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

interface KeystoreManager {
    fun getOrCreateKey(alias: String): SecretKey
    fun deleteKey(alias: String)
}

class AndroidKeystoreManager : KeystoreManager {

    companion object {
        const val MASTER_VAULT_KEY_ALIAS = "MasterVaultKey_v1"
        const val DECOY_VAULT_KEY_ALIAS = "DecoyVaultKey_v1"
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val AES_KEY_SIZE_BITS = 256

        // In-memory fallback cache for JVM unit test execution where AndroidKeyStore is absent
        private val jvmFallbackKeys = ConcurrentHashMap<String, SecretKey>()
    }

    private val isAndroidKeyStoreAvailable: Boolean by lazy {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER)
            ks.load(null)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun getOrCreateKey(alias: String): SecretKey {
        return if (isAndroidKeyStoreAvailable) {
            getOrCreateKeyFromAndroidKeyStore(alias)
        } else {
            getOrCreateKeyFromJvmFallback(alias)
        }
    }

    private fun getOrCreateKeyFromAndroidKeyStore(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }

        if (keyStore.containsAlias(alias)) {
            try {
                val existingKey = keyStore.getKey(alias, null) as? SecretKey
                if (existingKey != null) {
                    return existingKey
                }
            } catch (_: Exception) { }
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(false) // Allows caller-provided IV for GCM streaming
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    private fun getOrCreateKeyFromJvmFallback(alias: String): SecretKey {
        return jvmFallbackKeys.computeIfAbsent(alias) {
            val random = SecureRandom()
            val rawKey = ByteArray(32) // 256 bits
            random.nextBytes(rawKey)
            SecretKeySpec(rawKey, "AES")
        }
    }

    override fun deleteKey(alias: String) {
        if (isAndroidKeyStoreAvailable) {
            try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
                if (keyStore.containsAlias(alias)) {
                    keyStore.deleteEntry(alias)
                }
            } catch (_: Exception) { }
        } else {
            jvmFallbackKeys.remove(alias)
        }
    }
}
