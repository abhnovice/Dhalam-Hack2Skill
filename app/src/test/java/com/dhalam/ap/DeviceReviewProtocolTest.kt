package com.dhalam.ap

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.EOFException
import org.junit.Assert.*
import org.junit.Test

class DeviceReviewProtocolTest {
    @Test fun framesRoundTripWithoutConsumingNextFrame() {
        val output = ByteArrayOutputStream()
        DeviceReviewProtocol.write(output, "{}".toByteArray())
        DeviceReviewProtocol.write(output, "next".toByteArray())
        val input = ByteArrayInputStream(output.toByteArray())
        assertEquals("{}", String(DeviceReviewProtocol.read(input)))
        assertEquals("next", String(DeviceReviewProtocol.read(input)))
    }

    @Test fun rejectsUntrustedLengthsBeforeAllocating() {
        for (size in listOf(-1, 0, 1, DeviceReviewProtocol.MAX_BYTES + 1, Int.MAX_VALUE)) {
            val output = ByteArrayOutputStream()
            DataOutputStream(output).writeInt(size)
            assertThrows(IllegalArgumentException::class.java) {
                DeviceReviewProtocol.read(ByteArrayInputStream(output.toByteArray()))
            }
        }
    }

    @Test fun truncatedFramesFail() {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).apply { writeInt(20); writeByte(1) }
        assertThrows(EOFException::class.java) {
            DeviceReviewProtocol.read(ByteArrayInputStream(output.toByteArray()))
        }
    }

    @Test fun codeRequiresExactMatchWithinOfferWindow() {
        assertTrue(DeviceReviewProtocol.validCode("123456", "123456", 100, 100))
        assertTrue(DeviceReviewProtocol.validCode("123456", "123456", 100, 120099))
        assertFalse(DeviceReviewProtocol.validCode("123456", "123456", 100, 120100))
        assertFalse(DeviceReviewProtocol.validCode("123456", "123456", 100, 99))
        assertFalse(DeviceReviewProtocol.validCode("123456", "654321", 100, 101))
        assertFalse(DeviceReviewProtocol.validCode("123456", "123456 ", 100, 101))
        repeat(20) { assertTrue(DeviceReviewProtocol.newCode().matches(Regex("[0-9]{6}"))) }
    }
}
