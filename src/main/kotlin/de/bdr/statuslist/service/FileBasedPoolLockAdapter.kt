/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service

import de.bdr.statuslist.config.AppConfiguration
import de.bdr.statuslist.util.log
import java.io.Closeable
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("app.storage-type", havingValue = "redis")
class FileBasedPoolLockAdapter(val config: AppConfiguration): StatusListPoolLock {
    override fun obtainPoolLock(poolId: String): Closeable? {
        try {
            val poolLockfile = StorageFiles.poolLockfile(config, poolId)
            Files.createFile(poolLockfile)
            return Closeable { Files.deleteIfExists(poolLockfile) }
        } catch (e: FileAlreadyExistsException) {
            log.debug("lock is already claimed", e)
            return null
        }
    }
}
