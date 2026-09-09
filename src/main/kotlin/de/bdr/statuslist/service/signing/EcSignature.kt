/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.service.signing

import java.nio.ByteBuffer

class EcSignature(
    val r: ByteArray,
    val s: ByteArray,
    private val jwsSignatureSize: Int,
    private val jcaSignatureBytes: ByteArray,
): Signature {

    companion object {

        private const val DER_SEQUENCE_TAG = 0x30.toByte()
        private const val DER_INTEGER_TAG = 0x02.toByte()
        private const val ZERO_BYTE = 0x00.toByte()

        // Internal limit to abort early on malformed input
        private const val MAXIMUM_LENGTH_SUPPORTED = 256

        fun fromJcaSignature(jcaSignatureBytes: ByteArray, algorithm: Algorithm) =
            fromJcaSignature(
                jcaSignatureBytes,
                algorithm.jwsSignatureSize,
            )

        fun fromJcaSignature(jcaSignatureBytes: ByteArray, jwsSignatureSize: Int): EcSignature {
            val buffer = ByteBuffer.wrap(jcaSignatureBytes)
            validateJcaSignature(buffer.remaining() >= 8 && buffer.get() == DER_SEQUENCE_TAG)
            val size = buffer.readLength()
            validateJcaSignature(buffer.remaining() == size)
            val r = buffer.readInteger()
            val s = buffer.readInteger()
            validateJcaSignature(buffer.remaining() == 0)
            return EcSignature(r, s, jwsSignatureSize, jcaSignatureBytes)
        }

        private fun ByteBuffer.readInteger(): ByteArray {
            validateJcaSignature(get() == DER_INTEGER_TAG)
            var size = readLength()
            validateJcaSignature(remaining() >= size)
            while (size > 0 && get(position()) == ZERO_BYTE) {
                get()
                size--
            }
            val result = ByteArray(size)
            get(result)
            return result
        }

        private fun ByteBuffer.readLength(): Int {
            val first = get().toUByte().toInt()
            return if (first > 127) {
                val lengthOfLength = first - 128
                var result = 0
                repeat(lengthOfLength) {
                    val next = get().toUByte().toInt()
                    result = result * 256 + next
                    require(result <= MAXIMUM_LENGTH_SUPPORTED) {
                        "Length is larger than maximum length supported: $MAXIMUM_LENGTH_SUPPORTED"
                    }
                }
                result
            } else {
                first
            }
        }

        private fun validateJcaSignature(value: Boolean) {
            require(value) { "Invalid JCA signature" }
        }
    }

    init {
        require(jwsSignatureSize % 2 == 0) { "Invalid jwsSignatureSize" }
        require(r.size <= jwsSignatureSize / 2) { "r too large or invalid jwsSignatureSize" }
        require(s.size <= jwsSignatureSize / 2) { "s too large or invalid jwsSignatureSize" }
    }

    override fun hashCode(): Int {
        var hash = 55057
        hash = hash * 31 + r.contentHashCode()
        hash = hash * 31 + s.contentHashCode()
        return hash
    }

    override fun equals(other: Any?) =
        other is EcSignature && r.contentEquals(other.r) && s.contentEquals(other.s)

    override fun toJwsFormat(): ByteArray {
        val buffer = ByteBuffer.wrap(ByteArray(jwsSignatureSize))
        repeat(jwsSignatureSize / 2 - r.size) { buffer.put(0) }
        buffer.put(r)
        repeat(jwsSignatureSize / 2 - s.size) { buffer.put(0) }
        buffer.put(s)
        return buffer.array()
    }

    override fun toCoseFormat() = toJwsFormat()

    override fun toJcaFormat() = jcaSignatureBytes
}
