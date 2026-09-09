/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service.signing

import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.jca.JCAContext
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.util.Base64URL
import java.security.cert.X509Certificate
import java.util.Base64
import org.bouncycastle.asn1.x509.Certificate
import org.bouncycastle.asn1.x509.IssuerSerial

interface StatusListSigner : JWSSigner {
    val algorithm: Algorithm
    val jwk: JWK
    val x5cCertChain: List<X509Certificate>
    val certificate: X509Certificate

    fun sign(data: ByteArray): Signature

    override fun getJCAContext() = JCAContext()

    override fun supportedJWSAlgorithms() = setOf(algorithm.jwsAlgorithm)

    override fun sign(header: JWSHeader, signingInput: ByteArray): Base64URL {
        return Base64URL.encode(sign(signingInput).toJwsFormat())
    }

    fun X509Certificate.toJwk() =
        JWK.parse(
            JWK.parse(this).toJSONObject().apply {
                set("kid", kid(this@toJwk))
            }
        )

    private fun kid(first: X509Certificate): String {
        val cert = Certificate.getInstance(first.encoded)
        return Base64.getEncoder()
            .encodeToString(IssuerSerial(cert.issuer, cert.serialNumber.value).encoded)
    }
}
