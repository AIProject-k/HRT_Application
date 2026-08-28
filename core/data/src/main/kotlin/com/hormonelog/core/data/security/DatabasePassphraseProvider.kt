package com.hormonelog.core.data.security

import java.security.SecureRandom

interface EncryptedSecretStore {
    fun read(alias: String): ByteArray?
    fun write(alias: String, value: ByteArray)
}

class DatabasePassphraseProvider(
    private val store: EncryptedSecretStore,
    private val random: SecureRandom = SecureRandom(),
) {
    fun getOrCreate(): ByteArray = store.read(ALIAS) ?: ByteArray(PASSPHRASE_SIZE_BYTES).also { generated ->
        random.nextBytes(generated)
        store.write(ALIAS, generated)
    }

    private companion object {
        const val ALIAS = "hormonelog.sqlcipher.passphrase.v1"
        const val PASSPHRASE_SIZE_BYTES = 32
    }
}
