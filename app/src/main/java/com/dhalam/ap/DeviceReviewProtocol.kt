package com.dhalam.ap

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom

internal object DeviceReviewProtocol {
    const val VERSION = 1
    const val MAX_BYTES = 1024 * 1024
    const val OFFER_MS = 120_000L
    val SERVICE_ID: java.util.UUID = java.util.UUID.fromString("c56bbab9-09e7-4af1-a60c-bd25309040b7")
    fun newCode(): String = (SecureRandom().nextInt(900_000) + 100_000).toString()

    fun validCode(expected: String, actual: String, createdAt: Long, now: Long): Boolean =
        now - createdAt in 0 until OFFER_MS &&
            actual.matches(Regex("[0-9]{6}")) &&
            MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), actual.toByteArray(Charsets.US_ASCII))

    fun read(input: InputStream): ByteArray {
        val stream = DataInputStream(input)
        val length = stream.readInt()
        require(length in 2..MAX_BYTES) { "Invalid review size." }
        return ByteArray(length).also { stream.readFully(it) }
    }

    fun write(output: OutputStream, bytes: ByteArray) {
        require(bytes.size in 2..MAX_BYTES) { "Review is too large." }
        DataOutputStream(output).apply { writeInt(bytes.size); write(bytes); flush() }
    }
}
