/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.test.utils

import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import de.bdr.statuslist.service.signing.Algorithm
import de.bdr.statuslist.service.signing.EcSignature
import de.bdr.statuslist.service.signing.Signature
import de.bdr.statuslist.service.signing.StatusListSigner
import java.security.cert.X509Certificate

class JwkSigner(jwk: JWK) : StatusListSigner {
    override val jwk: ECKey = jwk.toECKey()
    override val algorithm: Algorithm = Algorithm.entries.find { it.curve == this.jwk.curve }
            ?: throw IllegalArgumentException("Unsupported curve ${this.jwk.curve}")

    override val certificate: X509Certificate
        get() = jwk.parsedX509CertChain.first()
    override val x5cCertChain: List<X509Certificate>
        get() = jwk.parsedX509CertChain.dropLast(1)
    override fun sign(data: ByteArray): Signature {
        val signature = java.security.Signature.getInstance(algorithm.jcaAlgorithm)
        signature.initSign(jwk.toECPrivateKey())
        signature.update(data)
        return EcSignature.fromJcaSignature(signature.sign(), algorithm)
    }
}
