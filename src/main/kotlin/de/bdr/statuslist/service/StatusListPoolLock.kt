/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service

import java.io.Closeable

fun interface StatusListPoolLock {
    fun obtainPoolLock(poolId: String): Closeable?
}
