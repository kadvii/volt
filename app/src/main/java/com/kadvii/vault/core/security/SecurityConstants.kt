package com.kadvii.vault.core.security

object SecurityConstants {
    const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    const val MASTER_KEY_ALIAS = "vault_master_key_alias"
    const val DECOY_KEY_ALIAS = "vault_decoy_key_alias"

    const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
    const val GCM_TAG_LENGTH_BITS = 128
    const val GCM_IV_LENGTH_BYTES = 12

    const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    const val PBKDF2_ITERATION_COUNT = 100_000
    const val SALT_LENGTH_BYTES = 32
}

