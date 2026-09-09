/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service

import java.time.Instant
import java.util.UUID

interface StatusListTokenStorage {
    fun metadata(listId: UUID): ListStorageMetadata

    fun storeMetadata(metadata: ListStorageMetadata)

    fun store(listId: UUID, tokenFormat: TokenFormat, serialized: ByteArray)
}

interface StatusListTokenSource {
    fun load(listId: UUID, tokenFormat: TokenFormat): TokenData?

    fun lastModified(listId: UUID, tokenFormat: TokenFormat): Instant
}

class TokenData(val data: ByteArray, val lastModified: Instant)

interface StatusListTokenRepository : StatusListTokenStorage, StatusListTokenSource
