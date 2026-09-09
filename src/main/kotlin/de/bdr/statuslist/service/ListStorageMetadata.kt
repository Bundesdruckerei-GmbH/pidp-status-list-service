/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service

import java.time.Duration
import java.time.Instant
import java.util.UUID
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue

class ListStorageMetadata(val listId: UUID, val version: Int, val expires: Instant) {

    companion object {

        private val jsonMapper = jsonMapper {
            addModule(kotlinModule()).enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        }

        fun parse(value: String) = jsonMapper.readValue<ListStorageMetadata>(value)
    }

    fun serialize(): String? = jsonMapper.writeValueAsString(this)

    fun isUpdated(version: Int) = version != this.version

    fun mayExpireAfterTwo(interval: Duration) =
        expires.isBefore(Instant.now().plus(interval.multipliedBy(2)))
}
