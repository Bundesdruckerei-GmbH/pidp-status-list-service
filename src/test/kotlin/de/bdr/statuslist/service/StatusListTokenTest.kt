/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service

import com.nimbusds.jose.JWSAlgorithm.ES256
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.KeyUse.SIGNATURE
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import de.bdr.statuslist.test.utils.JwkSigner
import java.time.Instant
import org.junit.jupiter.api.Test

internal class StatusListTokenTest {

    private val issuer = "https://example.com"
    private val subject = "https://example.com/statuslists/1"

    private val issuerKey = ECKeyGenerator(Curve.P_256)
        .keyID("test-key-id")
        .keyUse(SIGNATURE)
        .algorithm(ES256)
        .generate()

    @Test
    fun `create status list token`() {
        val statusList = StatusList(16, 1)
        statusList.set(0, 1)
        statusList.set(3, 1)
        statusList.set(4, 1)
        statusList.set(5, 1)
        statusList.set(7, 1)
        statusList.set(8, 1)
        statusList.set(9, 1)
        statusList.set(13, 1)
        statusList.set(15, 1)

        println("Status List: $statusList")

        val signer = JwkSigner(issuerKey)

        val statusListJwt =
            StatusListToken(subject, issuer, Instant.now(), statusList).asJwt(signer)

        println("Status List Token: $statusListJwt")
    }


    @Test
    fun `create status list token2`() {
        val statusList = StatusList(12, 2)
        statusList.set(0, 1)
        statusList.set(1, 2)
        statusList.set(2, 0)
        statusList.set(3, 3)
        statusList.set(4, 0)
        statusList.set(5, 1)
        statusList.set(6, 0)
        statusList.set(7, 1)
        statusList.set(8, 1)
        statusList.set(9, 2)
        statusList.set(10, 3)
        statusList.set(11, 3)

        println("Status List: $statusList")

        val signer = JwkSigner(issuerKey)

        val statusListJwt =
            StatusListToken(subject, issuer, Instant.now(), statusList).asJwt(signer)

        println("Status List Token: $statusListJwt")
    }
}
