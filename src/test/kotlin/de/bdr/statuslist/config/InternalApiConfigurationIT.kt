/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.config

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["spring.profiles.include=docker,api-port"],
)
@AutoConfigureTestRestTemplate
class InternalApiConfigurationIT(@param:Autowired val restTemplate: TestRestTemplate) {
    private val apiKey = "9a4cf395-25d6-4a82-8303-4b28a66cef20"
    private val listIdentifier = "verified-email"

    @Test
    fun `should not get reference on default port`() {
        val headers = HttpHeaders()
        headers.set("x-api-key", apiKey)
        val response =
            restTemplate.exchange(
                "/pools/$listIdentifier/new-references",
                HttpMethod.POST,
                HttpEntity<String>(null.toString()),
                String::class.java,
            )
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }
}
