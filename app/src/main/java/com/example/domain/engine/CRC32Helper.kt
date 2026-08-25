package com.example.domain.engine

import java.util.zip.CRC32

object CRC32Helper {
    fun computeHex(data: ByteArray): String {
        val crc = CRC32()
        crc.update(data)
        val value = crc.value
        return String.format("%08X", value)
    }

    fun computeHex(str: String): String {
        return computeHex(str.toByteArray(Charsets.UTF_8))
    }
}
