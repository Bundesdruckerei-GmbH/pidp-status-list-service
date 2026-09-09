/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service.signing

import COSE.AlgorithmID
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.Curve

enum class Algorithm(
    val jwsAlgorithm: JWSAlgorithm,
    val jwsSignatureSize: Int,
    val coseAlgorithm: AlgorithmID,
    val jcaAlgorithm: String,
    val curve: Curve,
) {
    ES256(
        jwsAlgorithm = JWSAlgorithm.ES256,
        64,
        coseAlgorithm = AlgorithmID.ECDSA_256,
        "SHA256WithECDSA",
        Curve.P_256,
    ),
    ES384(
        jwsAlgorithm = JWSAlgorithm.ES384,
        96,
        coseAlgorithm = AlgorithmID.ECDSA_384,
        "SHA384WithECDSA",
        Curve.P_384,
    ),
    ES512(
        jwsAlgorithm = JWSAlgorithm.ES512,
        132,
        coseAlgorithm = AlgorithmID.ECDSA_512,
        "SHA512WithECDSA",
        Curve.P_521,
    ),
}
