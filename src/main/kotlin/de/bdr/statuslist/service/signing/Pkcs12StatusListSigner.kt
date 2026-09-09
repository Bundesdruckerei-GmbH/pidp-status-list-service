/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service.signing

import com.nimbusds.jose.jwk.Curve
import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.interfaces.ECKey

class Pkcs12StatusListSigner(private val keystore: KeyStore, password: String) : StatusListSigner {
    constructor(
        keystore: InputStream,
        password: String,
    ) : this(
        KeyStore.getInstance("pkcs12").apply { this.load(keystore, password.toCharArray()) },
        password,
    )

    private val keyAndCerts = run {
        val aliases = keystore.aliases().toList()
        require(aliases.size == 1) { "Expected a keystore with a key entry" }
        val privateKey =
            keystore.getKey(aliases.first(), password.toCharArray()) as PrivateKey?
                ?: throw IllegalArgumentException("Expected a keystore with a key entry")
        val chain =
            keystore.getCertificateChain(aliases.first())?.map { it as X509Certificate }
                ?: throw IllegalArgumentException("Expected a keystore with a certificate chain")
        Pair(privateKey, chain)
    }

    val privateKey = keyAndCerts.first
    val certificateChain = keyAndCerts.second

    override val certificate = certificateChain.first()
    override val jwk = certificate.toJwk()
    // drop root CA (trust anchor)
    override val x5cCertChain = certificateChain.dropLast(1)

    override val algorithm = run {
        val ecKey = certificate.publicKey as? ECKey ?: throw IllegalArgumentException("Only EC certificates are supported")
        val curve = Curve.forECParameterSpec(ecKey.params)
        Algorithm.entries.find { it.curve == curve }
            ?: throw IllegalArgumentException("Unsupported curve $curve")
    }

    override fun sign(data: ByteArray): Signature {
        val signature = java.security.Signature.getInstance(algorithm.jcaAlgorithm)
        signature.initSign(privateKey)
        signature.update(data)
        return EcSignature.fromJcaSignature(signature.sign(), algorithm)
    }
}
