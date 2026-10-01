package com.example.nes.emulator

import java.io.InputStream

class NesRom(
    val prgRom: ByteArray,
    val chrRom: ByteArray,
    val mapperId: Int,
    val isVerticalMirroring: Boolean,
    val hasBatteryRam: Boolean,
    val prgRamSize: Int = 8192
) {
    companion object {
        fun parse(inputStream: InputStream): NesRom {
            val bytes = inputStream.readBytes()
            return parse(bytes)
        }

        fun parse(bytes: ByteArray): NesRom {
            if (bytes.size < 16) {
                throw IllegalArgumentException("ROM file is too small to be a valid NES ROM")
            }

            // Check "NES\x1A"
            if (bytes[0] != 'N'.code.toByte() ||
                bytes[1] != 'E'.code.toByte() ||
                bytes[2] != 'S'.code.toByte() ||
                bytes[3] != 0x1A.toByte()
            ) {
                throw IllegalArgumentException("Invalid NES header signature")
            }

            val prgRomBanks = bytes[4].toInt() and 0xFF
            val chrRomBanks = bytes[5].toInt() and 0xFF
            val flag6 = bytes[6].toInt() and 0xFF
            val flag7 = bytes[7].toInt() and 0xFF

            val isVerticalMirroring = (flag6 and 0x01) != 0
            val hasBatteryRam = (flag6 and 0x02) != 0
            val hasTrainer = (flag6 and 0x04) != 0

            val mapperLow = (flag6 ushr 4) and 0x0F
            var mapperHigh = flag7 and 0xF0

            // Check for dirty headers (e.g. DiskDude! stamp in bytes 7-15)
            val hasGarbageInHeader = bytes.size >= 16 && (bytes[12] != 0.toByte() || bytes[13] != 0.toByte() || bytes[14] != 0.toByte() || bytes[15] != 0.toByte())
            if (hasGarbageInHeader && (flag7 and 0x0C) != 0x08) {
                mapperHigh = 0
            }

            val mapperId = mapperHigh or mapperLow

            var offset = 16
            if (hasTrainer) {
                offset += 512
            }

            val prgSize = maxOf(16384, prgRomBanks * 16384)
            val chrSize = if (chrRomBanks == 0) 8192 else chrRomBanks * 8192

            val prgRom = ByteArray(prgSize)
            val availablePrg = minOf(prgSize, (bytes.size - offset).coerceAtLeast(0))
            if (availablePrg > 0) {
                System.arraycopy(bytes, offset, prgRom, 0, availablePrg)
            }
            offset += availablePrg

            val chrRom = ByteArray(chrSize)
            if (chrRomBanks > 0) {
                val availableChr = minOf(chrRomBanks * 8192, (bytes.size - offset).coerceAtLeast(0))
                if (availableChr > 0) {
                    System.arraycopy(bytes, offset, chrRom, 0, availableChr)
                }
            }

            return NesRom(
                prgRom = prgRom,
                chrRom = chrRom,
                mapperId = mapperId,
                isVerticalMirroring = isVerticalMirroring,
                hasBatteryRam = hasBatteryRam
            )
        }
    }
}
