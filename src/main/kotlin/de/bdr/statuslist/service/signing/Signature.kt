/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service.signing

interface Signature {
    fun toJwsFormat(): ByteArray

    fun toCoseFormat(): ByteArray

    fun toJcaFormat(): ByteArray
}
