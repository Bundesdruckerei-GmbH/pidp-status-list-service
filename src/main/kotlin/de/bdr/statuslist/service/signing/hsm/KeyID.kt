/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service.signing.hsm

import java.util.regex.Pattern

@JvmRecord
data class KeyID(val value: String) {
    init {
        require(KID_FORMAT.matcher(value).matches()) { "Invalid KeyID" }
    }

    companion object {
        private val KID_FORMAT: Pattern = Pattern.compile("^[A-Za-z0-9.\\-_]{1,64}$")
    }
}
