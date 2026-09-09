/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service

import org.springframework.http.MediaType

enum class TokenFormat(val mediaType: MediaType) {
    JWT(MediaType.parseMediaType("application/statuslist+jwt")),
    CWT(MediaType.parseMediaType("application/statuslist+cwt")),
    JSON(MediaType.parseMediaType("application/statuslist+json")),
    CBOR(MediaType.parseMediaType("application/statuslist+cbor")),
}
