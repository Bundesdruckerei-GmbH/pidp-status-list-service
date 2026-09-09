/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service

import de.bdr.statuslist.config.AppConfiguration
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class FileBasedPoolLockAdapterTest {
    @MockK 
    lateinit var config: AppConfiguration

    private lateinit var adapter: FileBasedPoolLockAdapter

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        
        // Mock the storage directory to point to the JUnit TempDir
        every { config.storageDirectory } returns tempDir
        
        adapter = FileBasedPoolLockAdapter(config)
    }

    @Test
    fun `obtainPoolLock returns closeable when lock is successfully created`() {
        val poolId = "test-pool"
        val expectedLockFile = tempDir.resolve("$poolId.lock")

        val lock = adapter.obtainPoolLock(poolId)
        
        assertNotNull(lock, "Lock should be obtained")
        assertTrue(Files.exists(expectedLockFile), "Lock file should be created on disk")

        lock.close()
        assertFalse(Files.exists(expectedLockFile), "Lock file should be deleted after closing")
    }

    @Test
    fun `obtainPoolLock returns null when lock file already exists`() {
        val poolId = "busy-pool"
        val lockFile = tempDir.resolve("$poolId.lock")
        Files.createFile(lockFile)

        val lock = adapter.obtainPoolLock(poolId)
        
        assertNull(lock, "Lock should be null if file already exists")
        assertTrue(Files.exists(lockFile), "Existing lock file should still be there")
    }

    @Test
    fun `obtainPoolLock handles multiple pools independently`() {
        val pool1 = "pool-1"
        val pool2 = "pool-2"

        val lock1 = adapter.obtainPoolLock(pool1)
        val lock2 = adapter.obtainPoolLock(pool2)

        assertNotNull(lock1)
        assertNotNull(lock2)
        assertTrue(Files.exists(tempDir.resolve("$pool1.lock")))
        assertTrue(Files.exists(tempDir.resolve("$pool2.lock")))

        lock1.close()
        assertFalse(Files.exists(tempDir.resolve("$pool1.lock")))
        assertTrue(Files.exists(tempDir.resolve("$pool2.lock")))
    }
}
