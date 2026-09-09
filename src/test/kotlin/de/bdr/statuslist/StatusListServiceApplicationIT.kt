/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist

import COSE.OneKey
import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.startsWith
import com.fasterxml.jackson.annotation.JsonProperty
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import com.upokecenter.cbor.CBORObject
import de.bdr.statuslist.service.StatusList
import de.bdr.statuslist.service.TokenFormat
import de.bdr.statuslist.service.signing.Pkcs12StatusListSigner
import de.bdr.statuslist.test.utils.StatusListCwt.hasAggregationUri
import de.bdr.statuslist.test.utils.StatusListCwt.hasBeenIssuedInThePast
import de.bdr.statuslist.test.utils.StatusListCwt.hasIssuer
import de.bdr.statuslist.test.utils.StatusListCwt.hasStatusList
import de.bdr.statuslist.test.utils.StatusListCwt.hasStatusListJwtTyp
import de.bdr.statuslist.test.utils.StatusListCwt.hasStatusListUri
import de.bdr.statuslist.test.utils.StatusListCwt.isNotExpired
import de.bdr.statuslist.test.utils.StatusListCwt.verifiesWith
import de.bdr.statuslist.test.utils.StatusListJwt.hasAggregationUri
import de.bdr.statuslist.test.utils.StatusListJwt.hasBeenIssuedInThePast
import de.bdr.statuslist.test.utils.StatusListJwt.hasIssuer
import de.bdr.statuslist.test.utils.StatusListJwt.hasStatusList
import de.bdr.statuslist.test.utils.StatusListJwt.hasStatusListJwtTyp
import de.bdr.statuslist.test.utils.StatusListJwt.hasStatusListUri
import de.bdr.statuslist.test.utils.StatusListJwt.isNotExpired
import de.bdr.statuslist.test.utils.StatusListJwt.verifiesWith
import de.bdr.statuslist.test.utils.sign1MessageAndClaims
import de.bdr.statuslist.web.api.model.Reference
import de.bdr.statuslist.web.api.model.References
import de.bdr.statuslist.web.api.model.UpdateStatusRequest
import java.util.concurrent.TimeUnit
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.resttestclient.getForObject
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity

private const val BITS_PER_BYTE = 8

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.profiles.include=docker"],
)
@AutoConfigureTestRestTemplate
class StatusListServiceApplicationIT(
    @param:Value("\${oid4vc.issuer.signer.pkcs12.keystore}") private val keystore: String,
    @param:Value("\${oid4vc.issuer.signer.pkcs12.password}") private val password: String,
    @param:Value("\${app.public-url}") private val publicUrl: String,
    @param:Autowired val restTemplate: TestRestTemplate,
) {
    private val apiKey = "9a4cf395-25d6-4a82-8303-4b28a66cef20"
    private val verifier: ECDSAVerifier
    private val oneKey: OneKey

    init {
        val str = DefaultResourceLoader().getResource(keystore).inputStream
        val eck = Pkcs12StatusListSigner(str, password).jwk.toECKey()
        this.verifier = ECDSAVerifier(eck)
        this.oneKey = OneKey(eck.toPublicKey(), null)
    }

    @Test fun contextLoads() {}

    @Nested
    inner class SingleBitList(
        @param:Value("\${app.status-list-pools.verified-email.issuer}")
        private val issuerUri: String
    ) {
        private val listIdentifier = "verified-email"
        private val listSize = 128
        private val bits = 1

        @Test
        fun `should get reference, get status-list and get aggregation`() {
            val reference = postForReference(listIdentifier)
            val list = getStatusList(reference, TokenFormat.JSON, ListUri::class.java)
            val aggregationUri = list.aggregationUri

            val aggregation = getAggregation(aggregationUri)

            with(aggregation) {
                assertThat(statusList).isNotEmpty()
                assertThat(statusList).contains(reference.uri)
            }
        }

        @Test
        fun `should get some references`() {
            val count = 10
            val headers = HttpHeaders()
            headers.set("x-api-key", apiKey)
            headers.accept = listOf(MediaType.APPLICATION_JSON)
            val referenceList =
                restTemplate.postForObject(
                    "/pools/$listIdentifier/new-references?amount=$count",
                    HttpEntity<References>(headers),
                    References::class.java,
                )

            assertThat(referenceList).isNotNull()
            assertThat(referenceList?.references?.size).isEqualTo(count)
            (0..<count).forEach {
                assertThat(referenceList?.references?.get(it)?.uri)
                    .isNotNull()
                    .startsWith(publicUrl)
                assertThat(referenceList?.references?.get(it)?.index)
                    .isNotNull()
                    .isGreaterThanOrEqualTo(0)
            }
            assertThat(referenceList?.references?.last()?.index)
                .isNotEqualTo(referenceList?.references?.first()?.index)
        }

        @Test
        fun `should get valid distinct status-list as jwt`() {
            val reference = postForReference(listIdentifier)
            val listResponse = getStatusList(reference, TokenFormat.JWT, String::class.java)
            val token = SignedJWT.parse(listResponse)

            assertThat(token).all {
                hasIssuer(issuerUri)
                hasStatusListJwtTyp()
                hasStatusListUri()
                hasBeenIssuedInThePast()
                isNotExpired()
                hasAggregationUri("$publicUrl/aggregation/$listIdentifier")
                hasStatusList(
                    withSize = listSize / BITS_PER_BYTE,
                    withBits = bits
                )
                verifiesWith(verifier)
            }
        }

        @Test
        fun `should get valid distinct status-list as cwt`() {
            val reference = postForReference(listIdentifier)
            val listResponse = getStatusList(reference, TokenFormat.CWT, ByteArray::class.java)
            val token = CBORObject.DecodeFromBytes(listResponse)

            assertThat(token).transform { it.sign1MessageAndClaims }.all {
                hasIssuer(issuerUri)
                hasStatusListJwtTyp()
                hasStatusListUri()
                hasBeenIssuedInThePast()
                isNotExpired()
                hasAggregationUri("$publicUrl/aggregation/$listIdentifier")
                hasStatusList(
                    withSize = listSize / BITS_PER_BYTE,
                    withBits = bits
                )
                verifiesWith(oneKey)
            }
        }

        @Test
        fun `should get valid distinct status-list as json`() {
            val reference = postForReference(listIdentifier)
            val jsonList = getStatusList(reference, TokenFormat.JSON, ListUri::class.java)
            val statusList = StatusList.fromEncoded(jsonList.bits, jsonList.lst)

            assertThat(jsonList.bits).isEqualTo(bits)
            assertThat(jsonList.aggregationUri).isEqualTo("$publicUrl/aggregation/$listIdentifier")
            assertThat(statusList.list.size).isEqualTo(listSize / BITS_PER_BYTE)
        }

        @Test
        fun `should get valid distinct status-list as cbor`() {
            val reference = postForReference(listIdentifier)
            val listResponse = getStatusList(reference, TokenFormat.CBOR, ByteArray::class.java)
            val cborList = CBORObject.DecodeFromBytes(listResponse)
            val statusList = StatusList.fromCbor(cborList)

            assertThat(cborList.get("bits").AsInt32()).isEqualTo(bits)
            assertThat(cborList.get("aggregation_uri").AsString())
                .isEqualTo("$publicUrl/aggregation/$listIdentifier")
            assertThat(statusList.list.size).isEqualTo(listSize / BITS_PER_BYTE)
        }
    }

    @Nested
    inner class MultiBitList {
        private val listIdentifier = "subscriptions-email"
        private val listSize = 128
        private val bits = 4

        @Test
        fun `should get valid distinct status-list as json`() {
            val reference = postForReference(listIdentifier)
            val jsonList = getStatusList(reference, TokenFormat.JSON, ListUri::class.java)
            val statusList = StatusList.fromEncoded(jsonList.bits, jsonList.lst)

            assertThat(jsonList.bits).isEqualTo(bits)
            assertThat(jsonList.aggregationUri).isEqualTo("$publicUrl/aggregation/$listIdentifier")
            assertThat(statusList.list.size).isEqualTo(listSize * bits / BITS_PER_BYTE)
        }
    }

    private fun postForReference(listIdentifier: String): Reference {
        val headers = HttpHeaders()
        headers.set("x-api-key", apiKey)
        val referenceResponse =
            restTemplate.postForObject(
                "/pools/$listIdentifier/new-references",
                HttpEntity<References>(headers),
                References::class.java,
            )

        assertThat(referenceResponse?.references).isNotNull()
        with(referenceResponse?.references!!.first()) {
            assertThat(uri).startsWith(publicUrl)
            assertThat(index).isGreaterThanOrEqualTo(0)
        }
        return referenceResponse.references.first()
    }

    private fun updateStatus(reference: Reference, value: Int) {
        val headers = HttpHeaders()
        headers.set("x-api-key", apiKey)
        val updateRequest = UpdateStatusRequest(reference.uri, reference.index, value)
        val updateResponse =
            restTemplate.patchForObject(
                "/status-lists/update",
                HttpEntity(updateRequest, headers),
                Void::class.java,
            )
        assertThat(updateResponse).isNotNull()
        //        assertThat(updateResponse.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
    }

    private fun <T : Any> getStatusList(
        reference: Reference,
        tokenFormat: TokenFormat,
        responseFormat: Class<T>,
    ): T {
        val listUri = reference.uri.removePrefix(publicUrl)
        val entity = RequestEntity.get(listUri).accept(tokenFormat.mediaType).build()
        val listResponse = restTemplate.exchange(entity, responseFormat).body
        assertThat(listResponse).isNotNull()
        return listResponse ?: error("null response")
    }

    private fun getAggregation(aggregationUri: String): Aggregation {
        val aggregation =
            restTemplate.getForObject<Aggregation>(aggregationUri.removePrefix(publicUrl))
        assertThat(aggregation).isNotNull()
        return aggregation ?: error("null response")
    }
}

private fun Byte.toBitString(): String {
    return this.toUByte().toString(2).padStart(BITS_PER_BYTE, '0')
}

data class ListUri(
    val bits: Int,
    val lst: String,
    @param:JsonProperty("aggregation_uri") val aggregationUri: String,
)

data class Aggregation(@param:JsonProperty("status_lists") val statusList: List<String>)
