/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service.signing.hsm

import CryptoServerAPI.CryptoServerException
import CryptoServerCXI.CryptoServerCXI
import com.nimbusds.jose.util.X509CertUtils
import de.bdr.statuslist.config.HSMConfiguration
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows

class HSMStatusListSignerTest {

    private val hsmConfiguration = mockk<HSMConfiguration> {
        every { keyId } returns "test-key-id"
    }
    private val connectionFactory = mockk<HSMConnectionFactory>()
    private val cxi = mockk<CryptoServerCXI>()

    private val signer = HSMStatusListSigner(hsmConfiguration, connectionFactory)

    private fun setupMockKey() {
        every { connectionFactory.getAuthenticatedConnection() } returns cxi
        every { connectionFactory.group } returns "test-group"

        val keyAttributes = mockk<CryptoServerCXI.KeyAttributes>()
        every { keyAttributes.getSpecifier() } returns 1
        every { keyAttributes.getAlgo() } returns CryptoServerCXI.KEY_ALGO_ECDSA
        every { keyAttributes.getSize() } returns 256

        // Mock for listKeys to return the attribute
        every { cxi.listKeys(any()) } returns Array(1) {keyAttributes}

        // Mock for findKey to return a dummy key object
        val mockKey = mockk<CryptoServerCXI.Key>()
        every { cxi.findKey(any()) } returns mockKey

        // Mock for getting the certificate (used by getKeyValue called during sign)
        val certAttributes = mockk<CryptoServerCXI.KeyAttributes>()
        every { cxi.getKeyAttributes(mockKey, true) } returns certAttributes
        val dummyCertBytes = "mock-cert-bytes".toByteArray()
        every { certAttributes.certificate } returns dummyCertBytes

        // Mock the static utility to avoid CertificateException
        mockkStatic(X509CertUtils::class)
        val mockCert = mockk<java.security.cert.X509Certificate>()
        every { X509CertUtils.parseWithException(dummyCertBytes) } returns mockCert
    }

    @Test
    fun `sign should successfully sign data`() {
        setupMockKey()
        val data = "hello world".toByteArray()
        val expectedSignatureBytes = "signed-bytes".toByteArray()

        every { cxi.sign(any(), any(), any()) } returns expectedSignatureBytes

        val signature = signer.sign(data)

        assertArrayEquals(expectedSignatureBytes, signature.toJwsFormat())
        verify {
            connectionFactory.getAuthenticatedConnection()
            cxi.sign(any(), CryptoServerCXI.MECH_PAD_NONE, any())
        }
    }

    @Test
    fun `jwk should return valid jwk for certificate`() {
        val certBytes = getResourceAsBytes("/keys/valid-test.crt")
        setupMockKey()

        // Override the dummy cert with the actual test certificate
        val mockKey = mockk<CryptoServerCXI.Key>()
        every { cxi.findKey(any()) } returns mockKey
        val certAttributes = mockk<CryptoServerCXI.KeyAttributes>()
        every { cxi.getKeyAttributes(mockKey, true) } returns certAttributes
        every { certAttributes.certificate } returns certBytes

        // Use real X509CertUtils for parsing if we want to test the JWK conversion
        // But setupMockKey already does mockkStatic(X509CertUtils::class)
        // Let's just make sure the parser returns a real certificate for this test
        val realCert = X509CertUtils.parseWithException(certBytes)
        every { X509CertUtils.parseWithException(certBytes) } returns realCert

        val jwk = signer.jwk

        assertNotNull(jwk)
    }

    @Test
    fun `sign should throw HSMKeyException when CXI fails`() {
        setupMockKey()
        val data = "test".toByteArray()

        every { cxi.sign(any(), any(), any()) } throws CryptoServerException(4711)

        assertThrows<HSMKeyException> {
            signer.sign(data)
        }
    }

    @Test
    fun `sign should throw KeyNotFoundException when key is not found`() {
        every { connectionFactory.getAuthenticatedConnection() } returns cxi
        every { connectionFactory.group } returns "test-group"

        val keyAttributes = mockk<CryptoServerCXI.KeyAttributes>()
        every { keyAttributes.getSpecifier() } returns 1
        every { keyAttributes.getAlgo() } returns CryptoServerCXI.KEY_ALGO_ECDSA
        every { keyAttributes.getSize() } returns 256
        every { cxi.listKeys(any()) } returns Array(1) { keyAttributes }

        // Key is NOT found
        every { cxi.findKey(any()) } returns null

        assertThrows<KeyNotFoundException> {
            signer.sign("test".toByteArray())
        }
    }

    fun getResourceAsBytes(path: String): ByteArray? =
        object {}.javaClass.getResource(path)?.readBytes()
}
