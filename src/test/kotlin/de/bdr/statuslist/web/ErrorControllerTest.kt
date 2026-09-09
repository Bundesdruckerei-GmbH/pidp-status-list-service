/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.web

import jakarta.servlet.RequestDispatcher
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@WebMvcTest(ErrorController::class)
class ErrorControllerTest(@param:Autowired val mockMvc: MockMvc) {
    @Test
    fun `should return error status`() {
        arrayOf(HttpStatus.OK, HttpStatus.BAD_REQUEST).forEach {
            mockMvc
                .perform(
                    MockMvcRequestBuilders.get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, it.value())
                )
                .andExpect(MockMvcResultMatchers.status().`is`(it.value()))
                .andExpect(MockMvcResultMatchers.content().string(it.reasonPhrase))
        }
    }
}
