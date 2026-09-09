/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.test.utils

import COSE.Sign1Message
import assertk.fail
import com.upokecenter.cbor.CBORObject
import java.time.Instant


private const val SIGN1_TAG = 18

private fun Sign1Message(cbor: CBORObject): Sign1Message = CborObjectBasedSign1Message(cbor)

/** Needed because DecodeFromCBORObject is protected */
private class CborObjectBasedSign1Message(cbor: CBORObject) : Sign1Message() {
    init {
        if (cbor.mostOuterTag.ToInt32Unchecked() == SIGN1_TAG) {
            cbor.UntagOne()
        }
        DecodeFromCBORObject(cbor)
    }
}

internal val CBORObject.sign1MessageAndClaims: Pair<Sign1Message, CBORObject>
    get() =
        try {
            val sign1 = Sign1Message(this)
            Pair(sign1, CBORObject.DecodeFromBytes(sign1.GetContent()))
        } catch (e: Exception) {
            fail("Expected a sign1 message but could not decode it.", null, null, e)
        }

internal fun CBORObject.claimAsInstant(key: CBORObject): Instant? {
    try {
        return Instant.ofEpochSecond((this[key] ?: return null).AsInt64Value())
    } catch (e: Exception) {
        fail("Expected a sign1 message with a claim $key of type Int but got '${this[key]}'")
    }
}

internal fun CBORObject.claimAsString(key: CBORObject): String? {
    try {
        return (this[key] ?: return null).AsString()
    } catch (e: Exception) {
        fail("Expected a sign1 message with a claim $key of type String but got '${this[key]}'")
    }
}
