/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service.signing.hsm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VersionedKeyIDTest {

    @Test
    fun `should create VersionedKeyID with valid parameters`() {
        val keyId = KeyID("test-key-123")
        val version = 1
        val versionedKeyId = VersionedKeyID(keyId, version)

        assertEquals(keyId, versionedKeyId.keyID)
        assertEquals(version, versionedKeyId.version)
    }

    @Test
    fun `should throw exception when version is negative`() {
        val keyId = KeyID("test-key-123")
        val version = -1

        val exception = assertThrows<IllegalArgumentException> {
            VersionedKeyID(keyId, version)
        }
        assertEquals("Invalid version", exception.message)
    }

    @Test
    fun `should return correct string representation`() {
        val keyId = KeyID("test-key-123")
        val version = 5
        val versionedKeyId = VersionedKeyID(keyId, version)

        assertEquals("test-key-123/5", versionedKeyId.toString())
    }
}
