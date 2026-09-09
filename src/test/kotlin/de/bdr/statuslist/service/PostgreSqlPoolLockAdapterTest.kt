/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

class PostgreSqlPoolLockAdapterTest {
    @MockK lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var adapter: PostgreSqlPoolLockAdapter

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        adapter = PostgreSqlPoolLockAdapter(jdbcTemplate)
    }

    @Test
    fun `obtainPoolLock returns closeable when lock acquired`() {
        val poolId = "test-pool"
        // First query tries to obtain the advisory lock – return true
        every {
            jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(-id) FROM pools WHERE name = ?",
                Boolean::class.java,
                poolId
            )
        } returns true
        // Unlock query – return true to indicate successful unlock
        every {
            jdbcTemplate.queryForObject(
                "select pg_advisory_unlock(-id) from pools where name = ?",
                Boolean::class.java,
                poolId
            )
        } returns true

        val lock = adapter.obtainPoolLock(poolId)
        assertNotNull(lock, "Lock should be obtained and not null")
        // Closing should trigger the unlock query
        lock.close()
        verify {
            jdbcTemplate.queryForObject(
                "select pg_advisory_unlock(-id) from pools where name = ?",
                Boolean::class.java,
                poolId
            )
        }
    }

    @Test
    fun `obtainPoolLock returns null when lock not acquired`() {
        val poolId = "busy-pool"
        // Advisor lock attempt returns false
        every {
            jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(-id) FROM pools WHERE name = ?",
                Boolean::class.java,
                poolId
            )
        } returns false

        val lock = adapter.obtainPoolLock(poolId)
        assertNull(lock, "Lock should be null when advisory lock cannot be obtained")
        // Ensure unlock is never called
        verify(exactly = 0) {
            jdbcTemplate.queryForObject(
                "select pg_advisory_unlock(-id) from pools where name = ?",
                Boolean::class.java,
                any<String>()
            )
        }
    }
}
