/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service.signing.hsm

import CryptoServerAPI.CryptoServerException
import CryptoServerCXI.CryptoServerCXI
import de.bdr.statuslist.config.HSMConfiguration
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class HSMConnectionFactory(val hsmConfiguration: HSMConfiguration) {
    private val log: Logger = LoggerFactory.getLogger(HSMConnectionFactory::class.java)

    private var cxi: CryptoServerCXI = run {
        try {
            createHsmConnection()
        } catch (e: IOException) {
            throw HSMServerException(e)
        } catch (e: CryptoServerException) {
            throw HSMServerException(e)
        }
    }

    @Synchronized
    @Throws(CryptoServerException::class, IOException::class)
    fun getAuthenticatedConnection(): CryptoServerCXI {
        var notAuthenticated: Boolean
        try {
            notAuthenticated = (cxi.getAuthState() == 0)
        } catch (e: CryptoServerException) {
            log.warn("Could not get AuthState: {}", e.message)
            notAuthenticated = true
            cxi = createHsmConnection()
        }

        if (notAuthenticated) {
            val user = hsmConfiguration.user
            cxi.logon(user.name, null, user.password.toByteArray(StandardCharsets.UTF_8))
            cxi.setKeepSessionAlive(true)
        }
        return cxi
    }

    val group: String
        get() = hsmConfiguration.user.group

    private fun createHsmConnection(): CryptoServerCXI {
        var initialized = "not initialized"
        try {
            val cryptoServerCXI = CryptoServerCXI(
                hsmConfiguration.cxiDevice,
                hsmConfiguration.cxiConnectionTimeout.toMillis().toInt()
            )
            cryptoServerCXI.setTimeout(hsmConfiguration.cxiCommandTimeout.toMillis().toInt())
            initialized = "initialized"
            return cryptoServerCXI
        } finally {
            log.info("CryptoServerCXI ${initialized} for group {}", this.group)
        }
    }
}
