/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import de.bdr.statuslist.service.TestVectorsFromSpec.TestVector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class TestVectorsFromSpecTest {

    @ParameterizedTest
    @MethodSource("de.bdr.statuslist.service.TestVectorsFromSpec#get")
    fun testJson(testVector: TestVector) {
        test(testVector, StatusList.fromJson(testVector.readJson()))
    }

    @ParameterizedTest
    @MethodSource("de.bdr.statuslist.service.TestVectorsFromSpec#get")
    fun testCbor(testVector: TestVector) {
        test(testVector, StatusList.fromCbor(testVector.readCbor()))
    }

    private fun StatusList.Companion.fromJson(json: JsonObject) =
        fromEncoded(
            (json["bits"] as JsonPrimitive).content.toInt(),
            (json["lst"] as JsonPrimitive).content,
        )

    private fun test(testVector: TestVector, statusList: StatusList) {
        val size = statusList.size

        for (i in 0 until size) {
            val value = statusList.get(i).toUByte()
            assertThat(value, "status $i").isEqualTo(testVector.expectedStatus(i))
        }
    }
}
