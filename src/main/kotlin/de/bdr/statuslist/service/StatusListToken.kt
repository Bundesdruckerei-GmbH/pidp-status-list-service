/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service

import COSE.Sign1Message
import COSE.sign
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.upokecenter.cbor.CBORObject
import de.bdr.statuslist.service.signing.StatusListSigner
import java.time.Duration
import java.time.Instant
import java.util.Date

/** Represents a status list token as specified by IETF Token Status List draft 21. */
class StatusListToken(
    val statusListUri: String,
    val issuerUri: String,
    val issuedAt: Instant,
    val statusList: StatusList,
    val expiresAt: Instant? = null,
    val ttl: Duration? = null,
    val aggregationUri: String? = null,
) {

    companion object {

        const val STATUS_LIST_JWT_TYPE = "statuslist+jwt"

        const val STATUS_LIST_CWT_TYPE = "application/statuslist+cwt"

        const val STATUS_LIST_CLAIM = "status_list"

        const val TTL_CLAIM = "ttl"

        val CWT_ISS = CBORObject.FromObject(1)

        val CWT_SUB = CBORObject.FromObject(2)

        val CWT_IAT = CBORObject.FromObject(6)

        val CWT_EXP = CBORObject.FromObject(4)

        val CWT_TTL = CBORObject.FromObject(65534)

        val CWT_STATUS_LIST = CBORObject.FromObject(65533)

        val CWT_HEADER_ALG = CBORObject.FromObject(1)

        val CWT_HEADER_TYP = CBORObject.FromObject(16)
    }

    fun asJwt(
        signer: StatusListSigner,
        customizer: (header: JWSHeader.Builder, claimsSet: JWTClaimsSet.Builder) -> Unit = { _, _ ->
        },
    ): JWT {
        // header "alg" and "kid" set by signer
        val header =
            JWSHeader.Builder(signer.algorithm.jwsAlgorithm)
                .type(JOSEObjectType(STATUS_LIST_JWT_TYPE))

        val claimsSet = JWTClaimsSet.Builder()

        // mandatory claims
        claimsSet.subject(statusListUri)
        claimsSet.issuer(issuerUri)
        claimsSet.issueTime(Date(issuedAt.toEpochMilli()))
        claimsSet.claim(STATUS_LIST_CLAIM, statusList.toJsonObject(aggregationUri))
        ttl?.let { claimsSet.claim(TTL_CLAIM, ttl.seconds) }

        // optional claims
        if (expiresAt != null) claimsSet.expirationTime(Date(expiresAt.toEpochMilli()))

        // customize
        customizer(header, claimsSet)

        // build JWT
        val jwt = SignedJWT(header.build(), claimsSet.build())

        // sign JWT by external signer
        jwt.sign(signer)

        return jwt
    }

    fun asCwt(
        signer: StatusListSigner,
        customizer: (content: CBORObject, sign1: Sign1Message) -> Unit = { _, _ -> },
    ): CBORObject {
        val sign1 = Sign1Message()
        sign1.protectedAttributes[CWT_HEADER_ALG] = signer.algorithm.coseAlgorithm.AsCBOR()
        sign1.protectedAttributes[CWT_HEADER_TYP] = CBORObject.FromObject(STATUS_LIST_CWT_TYPE)

        val cbor = CBORObject.NewMap()
        cbor[CWT_ISS] = CBORObject.FromObject(issuerUri)
        cbor[CWT_SUB] = CBORObject.FromObject(statusListUri)
        cbor[CWT_IAT] = CBORObject.FromObject(issuedAt.epochSecond)
        expiresAt?.epochSecond?.let { cbor[CWT_EXP] = CBORObject.FromObject(it) }
        ttl?.seconds?.let { cbor[CWT_TTL] = CBORObject.FromObject(it) }
        cbor[CWT_STATUS_LIST] = statusList.toCborObject(aggregationUri)

        customizer(cbor, sign1)

        sign1.SetContent(cbor.EncodeToBytes())
        sign1.sign(signer)
        return sign1.EncodeToCBORObject()
    }
}
