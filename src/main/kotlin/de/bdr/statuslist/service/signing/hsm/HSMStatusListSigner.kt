/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service.signing.hsm

import CryptoServerAPI.CryptoServerException
import CryptoServerCXI.CryptoServerCXI
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.util.X509CertUtils
import de.bdr.statuslist.config.HSMConfiguration
import de.bdr.statuslist.service.signing.Algorithm
import de.bdr.statuslist.service.signing.Signature
import de.bdr.statuslist.service.signing.StatusListSigner
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

class HSMStatusListSigner(
    hsmConfiguration: HSMConfiguration,
    private val connectionFactory: HSMConnectionFactory = HSMConnectionFactory(hsmConfiguration)
) : StatusListSigner {

    companion object {
        private const val KEY_NOT_FOUND = "Key by the given keyID not found"
    }

    private val keyId = KeyID(hsmConfiguration.keyId)
    private val keyValues = ConcurrentHashMap<VersionedKeyID, HSMKeyValue>()

    override val algorithm: Algorithm
        get() {
            val attributes = getLatestVersionKeyAttributes(keyId)
            return keyValues.computeIfAbsent(attributes.keyID) { getKeyValue(attributes) }.algorithm
        }
    override val certificate: X509Certificate
        get() {
            val attributes = getLatestVersionKeyAttributes(keyId)
            return keyValues.computeIfAbsent(attributes.keyID) { getKeyValue(attributes) }.certificate
        }
    override val jwk: JWK
        get() = certificate.toJwk()
    // chain without root CA (trust anchor)
    override val x5cCertChain: List<X509Certificate>
        get() = listOf(certificate)

    override fun sign(data: ByteArray): Signature {
        try {
            val cxi = connectionFactory.getAuthenticatedConnection()

            val attributes = getLatestVersionKeyAttributes(keyId)
            val md = keyValues.computeIfAbsent(attributes.keyID) { getKeyValue(attributes) }.messageDigest

            val keyAttributes: CryptoServerCXI.KeyAttributes = constructKeyAttributes(attributes.keyID)
            val key: CryptoServerCXI.Key =
                cxi.findKey(keyAttributes) ?: throw KeyNotFoundException(KEY_NOT_FOUND)

            val hashedDate = md.digest(data)
            val jwsSignature = cxi.sign(key, CryptoServerCXI.MECH_PAD_NONE, hashedDate)
            return HSMSignature(jwsSignature)
        } catch (e: CryptoServerException) {
            throw HSMKeyException(e)
        } catch (e: IOException) {
            throw HSMKeyException(e)
        }
    }

    private fun getLatestVersionKeyAttributes(keyID: KeyID): HSMKeyAttributes {
        val attributes: CryptoServerCXI.KeyAttributes = getKeyAttributes(keyID).stream()
            .reduce { k1: CryptoServerCXI.KeyAttributes, k2: CryptoServerCXI.KeyAttributes -> if (k1.getSpecifier() > k2.getSpecifier()) k1 else k2 }
            .orElseThrow<RuntimeException?>(Supplier { KeyNotFoundException(KEY_NOT_FOUND) })
        return HSMKeyAttributes(VersionedKeyID(keyID, attributes.getSpecifier()), getJWSAlgorithm(attributes))
    }

    private fun getKeyValue(attributes: HSMKeyAttributes) : HSMKeyValue {
        val certificate = getX509Certificate(attributes.keyID)
        val algorithm = Algorithm.entries.find { it.jwsAlgorithm == attributes.algorithm }
            ?: throw IllegalArgumentException("Unsupported algorithm ${attributes.algorithm.name}")
        val hashAlg = when(algorithm) {
            Algorithm.ES256 -> "SHA-256"
            Algorithm.ES384 -> "SHA-384"
            Algorithm.ES512 -> "SHA-512"
        }
        return HSMKeyValue(certificate, algorithm, MessageDigest.getInstance(hashAlg))
    }

    private fun getRawCertificate(keyID: VersionedKeyID): ByteArray {
        try {
            val cxi = connectionFactory.getAuthenticatedConnection()

            val keyAttributes: CryptoServerCXI.KeyAttributes = constructKeyAttributes(keyID)
            val key: CryptoServerCXI.Key =
                cxi.findKey(keyAttributes) ?: throw KeyNotFoundException(KEY_NOT_FOUND)
            val attributes = cxi.getKeyAttributes(key, true)
            val cert = attributes.certificate
            if (cert == null || cert.isEmpty()) {
                throw HSMKeyException("Key does not contain certificate")
            }
            return cert
        } catch (e: CryptoServerException) {
            throw HSMKeyException(e)
        } catch (e: IOException) {
            throw HSMKeyException(e)
        }
    }

    private fun getX509Certificate(versionedKeyID: VersionedKeyID): X509Certificate {
        val rawCert = getRawCertificate(versionedKeyID)
        try {
            return X509CertUtils.parseWithException(rawCert)
        } catch (e: CertificateException) {
            throw HSMKeyException("Certificate could not be parsed", e)
        }
    }

    private fun getKeyAttributes(kid: KeyID): MutableList<CryptoServerCXI.KeyAttributes> {
        try {
            val cxi = connectionFactory.getAuthenticatedConnection()

            val searchAttributes: CryptoServerCXI.KeyAttributes = constructKeyAttributes(kid)
            val attributes = cxi.listKeys(searchAttributes)

            return MutableList(attributes.size) { i -> attributes[i]}
        } catch (e: CryptoServerException) {
            throw HSMKeyException(e)
        } catch (e: IOException) {
            throw HSMKeyException(e)
        }
    }

    private fun constructKeyAttributes(keyID: VersionedKeyID): CryptoServerCXI.KeyAttributes {
        val keyAttributes: CryptoServerCXI.KeyAttributes = constructKeyAttributes(keyID.keyID)
        keyAttributes.setSpecifier(keyID.version)

        return keyAttributes
    }

    private fun constructKeyAttributes(kid: KeyID): CryptoServerCXI.KeyAttributes {
        val keyAttributes: CryptoServerCXI.KeyAttributes = CryptoServerCXI.KeyAttributes()
        keyAttributes.setName(kid.value)
        keyAttributes.setGroup(connectionFactory.group)

        return keyAttributes
    }

    private fun getJWSAlgorithm(keyAttributes: CryptoServerCXI.KeyAttributes): JWSAlgorithm {
        if (keyAttributes.getAlgo() != CryptoServerCXI.KEY_ALGO_ECDSA) {
            throw HSMKeyException("Unsupported HSM key")
        }
        return when (keyAttributes.getSize()) {
            256 -> JWSAlgorithm.ES256
            384 -> JWSAlgorithm.ES384
            512 -> JWSAlgorithm.ES512
            else -> throw HSMKeyException("Unsupported HSM key size")
        }
    }

    data class HSMKeyValue(
        val certificate: X509Certificate,
        val algorithm: Algorithm,
        val messageDigest: MessageDigest
    )
}
