/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.data

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("app.storage-type", havingValue = "postgres")
class PoolAdapter(private val jdbcTemplate: JdbcTemplate) {
    fun findPoolIdByName(poolName: String): Long? {
        return try {
            jdbcTemplate.queryForObject(
                "SELECT id FROM pools WHERE name = ?",
                Long::class.java,
                poolName,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    fun findPoolAttributesByName(poolName: String): PoolAttributes? {
        return try {
            jdbcTemplate
                .query(
                    "SELECT id, bits, size, name, final_status FROM pools WHERE name = ?",
                    PoolAttributes.indexMapper,
                    poolName,
                )
                .firstOrNull()
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    fun existsByName(poolname: String): Boolean {
        return jdbcTemplate.queryForObject(
            "SELECT exists(SELECT id FROM pools WHERE name = ?)",
            Boolean::class.java,
            poolname,
        ) ?: false
    }

    fun createPool(bits: Int, size: Int, name: String, finalStatus: Int? = null) {
        jdbcTemplate.update(
            "INSERT INTO pools (bits, size, name, final_status) VALUES (?, ?, ?, ?)",
            bits,
            size,
            name,
            finalStatus,
        )
    }

    fun obtainAdvisoryLock(poolName: String): Boolean {
        return jdbcTemplate.queryForObject(
            "SELECT pg_try_advisory_xact_lock(id) FROM pools WHERE name = ?",
            Boolean::class.java,
            poolName,
        ) ?: false
    }

    class PoolAttributes(
        val id: Long,
        val bits: Int,
        val size: Int,
        val name: String,
        val finalStatus: Int? = null,
    ) {
        companion object {
            val indexMapper: RowMapper<PoolAttributes> = RowMapper { rs, _ ->
                PoolAttributes(
                    rs.getLong("id"),
                    rs.getInt("bits"),
                    rs.getInt("size"),
                    rs.getString("name"),
                    rs.getString("final_status")?.toInt(),
                )
            }
        }
    }
}
