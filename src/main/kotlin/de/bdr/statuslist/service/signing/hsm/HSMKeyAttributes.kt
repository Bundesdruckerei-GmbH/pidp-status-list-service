/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service.signing.hsm

import com.nimbusds.jose.JWSAlgorithm

@JvmRecord
data class HSMKeyAttributes(val keyID: VersionedKeyID, val algorithm: JWSAlgorithm)
