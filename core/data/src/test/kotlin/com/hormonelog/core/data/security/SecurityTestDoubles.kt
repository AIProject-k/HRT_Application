package com.hormonelog.core.data.security

import java.security.SecureRandom

class InMemoryEncryptedSecretStore : EncryptedSecretStore {
    private val values = mutableMapOf<String, ByteArray>()

    override fun read(alias: String): ByteArray? = values[alias]?.copyOf()

    override fun write(alias: String, value: ByteArray) {
        values[alias] = value.copyOf()
    }
}

class FixedSecureRandom(private val value: Byte) : SecureRandom() {
    override fun nextBytes(bytes: ByteArray) {
        bytes.fill(value)
    }
}
