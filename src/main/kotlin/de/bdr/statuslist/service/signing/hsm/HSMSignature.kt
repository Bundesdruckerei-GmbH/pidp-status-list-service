/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service.signing.hsm

import de.bdr.statuslist.service.signing.Signature

class HSMSignature(private val jwsSignature: ByteArray): Signature {
    override fun toJwsFormat(): ByteArray {
        return jwsSignature
    }

    override fun toCoseFormat(): ByteArray {
        return jwsSignature
    }

    override fun toJcaFormat(): ByteArray {
        throw NotImplementedError("JCA format not supported")
    }
}
