/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test

class ListStorageMetadataTest {

    @Test
    fun `should serialize and deserialize metadata`() {
        val now = Instant.now()
        val listId = UUID.randomUUID()

        val metadata = ListStorageMetadata(listId, 1, now.plusSeconds(10))
        val s = metadata.serialize()
        assertThat(s).isNotNull()

        val storageMetadata = ListStorageMetadata.parse(s!!)
        assertThat(storageMetadata).isNotNull()
        assertThat(storageMetadata.version).isEqualTo(1)
        assertThat(storageMetadata.listId).isEqualTo(listId)
        assertThat(storageMetadata.expires).isEqualTo(now.plusSeconds(10))
    }
}
