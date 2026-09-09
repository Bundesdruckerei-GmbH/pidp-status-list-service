/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service.signing.hsm

class HSMKeyException : RuntimeException {
    internal constructor(throwable: Throwable?) : super(throwable)

    internal constructor(message: String?) : super(message)

    internal constructor(message: String?, throwable: Throwable?) : super(message, throwable)
}
