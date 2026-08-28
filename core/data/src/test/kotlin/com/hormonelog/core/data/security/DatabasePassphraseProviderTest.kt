package com.hormonelog.core.data.security

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class DatabasePassphraseProviderTest {
    @Test
    fun sameStoredSecretReturnsSamePassphrase() {
        val provider = DatabasePassphraseProvider(InMemoryEncryptedSecretStore(), FixedSecureRandom(7))

        assertArrayEquals(provider.getOrCreate(), provider.getOrCreate())
    }
}
