/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service

import de.bdr.statuslist.util.log
import java.io.Closeable
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("app.storage-type", havingValue = "postgres")
class PostgreSqlPoolLockAdapter(private val jdbcTemplate: JdbcTemplate): StatusListPoolLock {
    override fun obtainPoolLock(poolId: String): Closeable? {
        val locked = jdbcTemplate.queryForObject(
            "SELECT pg_try_advisory_lock(-id) FROM pools WHERE name = ?",
            Boolean::class.java,
            poolId,
        ) ?: false
        log.debug("Lock obtained for {}: {}", poolId, locked)
        return if (locked) Closeable {
            val unlocked = jdbcTemplate.queryForObject(
                "select pg_advisory_unlock(-id) from pools where name = ?", Boolean::class.java, poolId) ?: false
            if (!unlocked) log.warn("Pool lock could not be unlocked for $poolId")
        } else null
    }
}
