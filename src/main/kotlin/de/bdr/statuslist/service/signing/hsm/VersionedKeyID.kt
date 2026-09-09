/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service.signing.hsm

@JvmRecord
data class VersionedKeyID(val keyID: KeyID, val version: Int) {
    override fun toString(): String {
        return "${keyID.value}/${version}"
    }

    init {
        require(version >= 0) { "Invalid version" }
    }
}
