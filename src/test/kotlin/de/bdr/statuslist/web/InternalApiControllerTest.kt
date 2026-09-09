/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.web

import com.ninjasquad.springmockk.MockkBean
import de.bdr.statuslist.config.AppConfiguration
import de.bdr.statuslist.config.KeyStoreConfiguration
import de.bdr.statuslist.config.PrecreationConfiguration
import de.bdr.statuslist.config.PrefetchConfiguration
import de.bdr.statuslist.config.SignerConfiguration
import de.bdr.statuslist.config.StatusListPoolConfiguration
import de.bdr.statuslist.service.Reference
import de.bdr.statuslist.service.StatusListService
import io.mockk.every
import java.time.Duration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@WebMvcTest(InternalApiController::class)
class InternalApiControllerTest(@param:Autowired val mockMvc: MockMvc) {
    @MockkBean lateinit var appConfiguration: AppConfiguration
    @MockkBean(relaxed = true) lateinit var statusListService: StatusListService

    @Suppress("SameParameterValue")
    private fun statusListPoolConfiguration(apiKey: String, keystore: String, password: String) =
        StatusListPoolConfiguration(
            apiKey = apiKey,
            apiKeys = null,
            size = 128,
            bits = 2,
            issuer = "http://example.com",
            precreation = PrecreationConfiguration(Duration.ofSeconds(10), 1),
            prefetch = PrefetchConfiguration(16, 32),
            updateInterval = Duration.ofSeconds(10),
            listLifetime = Duration.ofSeconds(15),
            aggregationId = null,
            signer = SignerConfiguration(KeyStoreConfiguration(keystore, password), null),
        )

    private val poolId = "test-pool"
    private val apiKey = "9a4cf395-25d6-4a82-8303-4b28a66cef20"

    @Test
    fun `should return references without amount`() {
        every { statusListService.reserve(poolId, 1) } returns listOf(Reference("uri", 1))
        every { appConfiguration.statusListPools[poolId] } returns
            statusListPoolConfiguration(
                apiKey,
                "classpath:/keys/pid_issuer_single_chain.p12",
                "test",
            )

        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/pools/$poolId/new-references")
                    .header("x-api-key", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.jsonPath("$.references[0].uri").value("uri"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.references[0].index").value(1))
    }

    @Test
    fun `should return two references`() {
        val amount = 2
        every { statusListService.reserve(poolId, amount) } returns
            listOf(Reference("uri1", 1), Reference("uri2", 2))
        every { appConfiguration.statusListPools[poolId] } returns
            statusListPoolConfiguration(
                apiKey,
                "classpath:/keys/pid_issuer_single_chain.p12",
                "test",
            )

        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/pools/$poolId/new-references?amount=$amount")
                    .header("x-api-key", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.jsonPath("$.references[0].uri").value("uri1"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.references[0].index").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.references[1].uri").value("uri2"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.references[1].index").value(2))
    }

    @Test
    fun `should update status`() {
        val uri = "uri1"
        every { statusListService.poolId(uri) } returns poolId
        every { appConfiguration.statusListPools[poolId] } returns
            statusListPoolConfiguration(
                apiKey,
                "classpath:/keys/pid_issuer_single_chain.p12",
                "test",
            )

        mockMvc
            .perform(
                MockMvcRequestBuilders.patch("/status-lists/update")
                    .header("x-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"uri": "$uri",
                        "index": 1,
                        "value": 1}
                    """
                            .trimIndent()
                    )
            )
            .andExpect(MockMvcResultMatchers.status().isNoContent)
    }

    @Test
    fun `should get bad request on less than one reference`() {
        val amount = 0

        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/pools/$poolId/new-references?amount=$amount")
                    .header("x-api-key", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect {
                MockMvcResultMatchers.header().string("Content-Type", "application/problem+json")
            }
            .andExpect(MockMvcResultMatchers.jsonPath("code").value("VALUE_OUT_OF_RANGE"))
            .andExpect(
                MockMvcResultMatchers.jsonPath("message").value("Amount must be greater than 0")
            )
    }

    @Test
    fun `should get bad request on type mismatch`() {
        val amount = "abc"

        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/pools/$poolId/new-references?amount=$amount")
                    .header("x-api-key", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("message").isNotEmpty)
    }

    @Test
    fun `should get nothing if pool not exists`() {
        every { appConfiguration.statusListPools[poolId] } returns null

        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/pools/$poolId/new-references")
                    .header("x-api-key", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().isNotFound)
            .andExpect(MockMvcResultMatchers.jsonPath("code").value("NO_SUCH_POOL"))
            .andExpect(MockMvcResultMatchers.jsonPath("message").value("No pool with id $poolId"))
    }

    @Test
    fun `should unauthorized on invalid api-key`() {
        every { appConfiguration.statusListPools[poolId] } returns
            statusListPoolConfiguration(
                apiKey,
                "classpath:/keys/pid_issuer_single_chain.p12",
                "test",
            )

        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/pools/$poolId/new-references")
                    .header("x-api-key", "invalid api-key")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andExpect(MockMvcResultMatchers.jsonPath("code").value("UNAUTHORIZED"))
            .andExpect(MockMvcResultMatchers.jsonPath("message").value("Invalid api key"))
    }

    @Test
    fun `should unauthorized on missing api-key`() {
        every { appConfiguration.statusListPools[poolId] } returns
            statusListPoolConfiguration(
                apiKey,
                "classpath:/keys/pid_issuer_single_chain.p12",
                "test",
            )

        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/pools/$poolId/new-references")
                    //                    .header("x-api-key", "invalid api-key")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andExpect(MockMvcResultMatchers.jsonPath("code").value("UNAUTHORIZED"))
            .andExpect(MockMvcResultMatchers.jsonPath("message").value("Missing api key"))
    }
}
