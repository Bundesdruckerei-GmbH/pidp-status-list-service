/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service.signing.hsm

class HSMServerException : RuntimeException {
    companion object {
        private const val DEFAULT_MESSAGE = "Could not init HSM connection"
    }
    internal constructor(throwable: Throwable?) : this(DEFAULT_MESSAGE, throwable)
    internal constructor(message: String?, throwable: Throwable?) : super(message, throwable)
}
