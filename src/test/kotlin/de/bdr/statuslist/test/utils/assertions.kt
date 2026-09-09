/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.test.utils

import COSE.AlgorithmID
import COSE.OneKey
import COSE.Sign1Message
import assertk.Assert
import assertk.assertions.support.expected
import assertk.fail
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jwt.SignedJWT
import com.upokecenter.cbor.CBORObject
import com.upokecenter.cbor.CBORType
import de.bdr.statuslist.service.StatusList
import de.bdr.statuslist.service.StatusListToken.Companion.STATUS_LIST_JWT_TYPE
import java.sql.Date
import java.time.Instant

object StatusListJwt {

    fun Assert<SignedJWT>.hasIssuer(expected: String): Unit = given { actual ->
        val issuer = actual.jwtClaimsSet.issuer
        if (issuer == expected) return@given
        expected("A signed JWT that has issuer '$expected' but had '$issuer'")
    }

    fun Assert<SignedJWT>.hasStatusListJwtTyp(): Unit = given { actual ->
        if (actual.header.type == JOSEObjectType(STATUS_LIST_JWT_TYPE)) return@given
        expected("A signed JWT that has typ header '$STATUS_LIST_JWT_TYPE' but had '${actual.header.type.type}'")
    }

    fun Assert<SignedJWT>.hasStatusListUri(): Unit = given { actual ->
        if (actual.jwtClaimsSet.subject != null) return@given
        expected("A signed JWT that has a subject (status list URI) but had none")
    }

    fun Assert<SignedJWT>.hasBeenIssuedInThePast(): Unit = given { actual ->
        if (actual.jwtClaimsSet.issueTime != null
            && actual.jwtClaimsSet.issueTime.before(Date.from(Instant.now()))) return
        expected("A signed JWT that has been issued in the past")
    }

    fun Assert<SignedJWT>.isNotExpired(): Unit = given { actual ->
        if (actual.jwtClaimsSet.expirationTime == null
            || actual.jwtClaimsSet.expirationTime.after(Date.from(Instant.now()))) return
        expected("A signed JWT that is not expired but was expired")
    }

    fun Assert<SignedJWT>.hasAggregationUri(expected: String): Unit = given { actual ->
        val slClaim = actual.jwtClaimsSet.getJSONObjectClaim("status_list") ?: expected("A signed JWT that has a status_list claim but had none")
        val aggregationUri = slClaim["aggregation_uri"]
        if (aggregationUri == expected) return
        expected("A signed JWT with an aggregation_uri of '$expected' but was '$aggregationUri'")
    }

    fun Assert<SignedJWT>.hasStatusList(withSize: Int, withBits: Int): Unit = given { actual ->
        val slClaim = actual.jwtClaimsSet.claims["status_list"] ?: expected("A signed JWT that has a status_list claim but had none")
        if (slClaim !is Map<*, *>) expected("A signed JWT that has a status_list claim of type object (kotlin Map) but was ${slClaim::class.qualifiedName}")
        val lBits = slClaim["bits"] as? Long ?: expected("A signed JWT that has a status_list.bits claim of type Int but had ${slClaim["bits"]}")
        val bits = lBits.toInt()
        if (bits != withBits) expected("A signed JWT containing a status list with $withBits bits but had $bits")
        val lst = slClaim["lst"] as? String ?: expected("A signed JWT that has a status_list.lst claim of type String but had ${slClaim["lst"]}")
        val statusList = try {
            StatusList.fromEncoded(bits, lst)
        } catch (e: Throwable) {
            fail("A signed JWT that has a status_list.lst claim that can be parsed and deflated to a byte array but that failed.", null, null, e)
        }
        if (statusList.list.size != withSize) expected("A signed JWT containing a status list of size $withSize but had size ${statusList.list.size}")
    }

    fun Assert<SignedJWT>.verifiesWith(verifier: JWSVerifier): Unit = given { actual ->
        try {
            actual.verify(verifier)
        } catch (e: Throwable) {
            fail("A signed JWT that verifies, but did not verify.", null, null, e)
        }
    }

}

object StatusListCwt {

    private val CWT_ISS = CBORObject.FromObject(1)

    private val CWT_SUB = CBORObject.FromObject(2)

    private val CWT_IAT = CBORObject.FromObject(6)

    private val CWT_EXP = CBORObject.FromObject(4)

    private val CWT_STATUS_LIST = CBORObject.FromObject(65533)

    private val CWT_HEADER_ALG = CBORObject.FromObject(1)

    private val CWT_HEADER_TYP = CBORObject.FromObject(16)

    private const val STATUS_LIST_CWT_TYPE = "application/statuslist+cwt"

    private val CBOR_AGGREGATION_URI_CLAIM = CBORObject.FromObject("aggregation_uri")

    private val CBOR_BITS_CLAIM = CBORObject.FromObject("bits")

    private val CBOR_LIST_CLAIM = CBORObject.FromObject("lst")

    private val CWT_SIGNATURE_ALGORITHMS =
        setOf(
            AlgorithmID.ECDSA_256,
            AlgorithmID.ECDSA_384,
            AlgorithmID.ECDSA_512,
            AlgorithmID.RSA_PSS_256,
            AlgorithmID.RSA_PSS_384,
            AlgorithmID.RSA_PSS_512,
        )

    fun Assert<Pair<Sign1Message, CBORObject>>.hasIssuer(expected: String): Unit = given { actual ->
        val issuer = actual.second.claimAsString(CWT_ISS)
        if (issuer == expected) return@given
        expected("A Sign1Message that has issuer '$expected' but had '$issuer'")
    }

    fun Assert<Pair<Sign1Message, CBORObject>>.hasValidAlgorithm(): Unit = given { actual ->
        val alg = AlgorithmID.FromCBOR(actual.first.protectedAttributes[CWT_HEADER_ALG])
        if (CWT_SIGNATURE_ALGORITHMS.contains(alg)) return@given
        expected("A Sign1Message with valid alg but had $alg")
    }

    fun Assert<Pair<Sign1Message, CBORObject>>.hasStatusListJwtTyp(): Unit = given { actual ->
        val typ = actual.first.protectedAttributes.claimAsString(CWT_HEADER_TYP)
        if (typ == STATUS_LIST_CWT_TYPE) return@given
        expected("A Sign1Message that has typ header '$STATUS_LIST_CWT_TYPE' but had '$typ'")
    }

    fun Assert<Pair<Sign1Message, CBORObject>>.hasStatusListUri(): Unit = given { actual ->
        val statusListUri = actual.second.claimAsString(CWT_SUB)
        if (statusListUri != null) return@given
        expected("A Sign1Message that has a subject (status list URI) but had none")
    }

    fun Assert<Pair<Sign1Message, CBORObject>>.hasBeenIssuedInThePast(): Unit = given { actual ->
        val iat = actual.second.claimAsInstant(CWT_IAT)
        if (iat != null
            && iat.isBefore(Instant.now())) return
        expected("A Sign1Message that has been issued in the past")
    }

    fun Assert<Pair<Sign1Message, CBORObject>>.isNotExpired(): Unit = given { actual ->
        val exp = actual.second.claimAsInstant(CWT_EXP)
        if (exp != null
            && exp.isAfter(Instant.now())) return
        expected("A Sign1Message that is not expired but was expired")
    }

    fun Assert<Pair<Sign1Message, CBORObject>>.hasAggregationUri(expected: String): Unit = given { actual ->
        val slClaim = actual.second[CWT_STATUS_LIST] ?: expected("A Sign1Message that has a status list claim but had none")
        val aggregationUri = slClaim.claimAsString(CBOR_AGGREGATION_URI_CLAIM)
        if (aggregationUri == expected) return@given
        expected("A Sign1Message that has an aggregation uri '$expected' but was '$aggregationUri'")
    }

    fun Assert<Pair<Sign1Message, CBORObject>>.hasStatusList(withSize: Int, withBits: Int): Unit = given { actual ->
        val slClaim = actual.second[CWT_STATUS_LIST] ?: expected("A Sign1Message that has a status list claim but had none")
        if (slClaim.type != CBORType.Map) expected("A Sign1Message that has a status list claim of type Map but was ${slClaim.type}")
        val statusList = try {
            StatusList.fromCbor(slClaim)
        } catch (e: Throwable) {
            fail("A Sign1Message that has a status list claim that can be deflated to a byte array but that failed.", null, null, e)
        }
        if (statusList.list.size != withSize) expected("A Sign1Message containing a status list of size $withSize but had size ${statusList.size}")
    }

    fun Assert<Pair<Sign1Message, CBORObject>>.verifiesWith(oneKey: OneKey): Unit = given { actual ->
        val e = try {
            if (actual.first.validate(oneKey)) return@given
            null
        } catch (e: Throwable) {
            e
        }
        fail("A Sign1Message that verifies, but did not verify.", null, null, e)
    }

}
