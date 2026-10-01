package com.example

import com.example.nes.emulator.Cpu6502
import com.example.nes.emulator.NesConsole
import com.example.nes.emulator.NesRom
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testNesRomParser() {
        val header = ByteArray(16)
        header[0] = 'N'.code.toByte()
        header[1] = 'E'.code.toByte()
        header[2] = 'S'.code.toByte()
        header[3] = 0x1A.toByte()
        header[4] = 1 // 1 x 16KB PRG
        header[5] = 1 // 1 x 8KB CHR
        header[6] = 0x01 // Vertical mirroring, Mapper 0 low
        header[7] = 0x00 // Mapper 0 high

        val romBytes = ByteArray(16 + 16384 + 8192)
        System.arraycopy(header, 0, romBytes, 0, 16)
        romBytes[16] = 0xEA.toByte() // NOP in PRG
        romBytes[16 + 16384] = 0x55.toByte() // CHR

        val rom = NesRom.parse(romBytes)
        assertEquals(16384, rom.prgRom.size)
        assertEquals(8192, rom.chrRom.size)
        assertEquals(0, rom.mapperId)
        assertTrue(rom.isVerticalMirroring)
        assertEquals(0xEA.toByte(), rom.prgRom[0])
    }

    @Test
    fun testCpu6502Instructions() {
        val memory = ByteArray(65536)
        val bus = object : Cpu6502.MemoryBus {
            override fun read(address: Int): Int = memory[address and 0xFFFF].toInt() and 0xFF
            override fun write(address: Int, value: Int) {
                memory[address and 0xFFFF] = (value and 0xFF).toByte()
            }
        }
        val cpu = Cpu6502(bus)

        // Set Reset vector to $8000
        memory[0xFFFC] = 0x00
        memory[0xFFFD] = 0x80.toByte()

        // Write program at $8000:
        // LDA #$42 (A9 42)
        // TAX      (AA)
        // INX      (E8)
        // STA $02  (85 02)
        var pc = 0x8000
        memory[pc++] = 0xA9.toByte(); memory[pc++] = 0x42
        memory[pc++] = 0xAA.toByte()
        memory[pc++] = 0xE8.toByte()
        memory[pc++] = 0x85.toByte(); memory[pc++] = 0x02

        cpu.reset()
        assertEquals(0x8000, cpu.regPC)

        cpu.step() // LDA #$42
        assertEquals(0x42, cpu.regA)

        cpu.step() // TAX
        assertEquals(0x42, cpu.regX)

        cpu.step() // INX
        assertEquals(0x43, cpu.regX)

        cpu.step() // STA $02
        assertEquals(0x42, memory[2].toInt() and 0xFF)
    }

    @Test
    fun testControllerBitmask() {
        val console = NesConsole()
        console.setControllerButton(0, NesConsole.BUTTON_A, true)
        console.setControllerButton(0, NesConsole.BUTTON_START, true)

        val hasA = (console.controller1State and NesConsole.BUTTON_A) != 0
        val hasB = (console.controller1State and NesConsole.BUTTON_B) != 0
        val hasStart = (console.controller1State and NesConsole.BUTTON_START) != 0

        assertTrue(hasA)
        assertFalse(hasB)
        assertTrue(hasStart)

        console.setControllerButton(0, NesConsole.BUTTON_A, false)
        assertFalse((console.controller1State and NesConsole.BUTTON_A) != 0)
    }

    @Test
    fun testMapper4Mmc3() {
        // Create synthetic MMC3 ROM with 128KB PRG (16 x 8KB banks) and 128KB CHR (128 x 1KB banks)
        val prg = ByteArray(128 * 1024)
        for (b in 0 until 16) {
            prg[b * 8192] = (b + 1).toByte()
        }

        val chr = ByteArray(128 * 1024)
        for (b in 0 until 128) {
            chr[b * 1024] = (b + 10).toByte()
        }

        val rom = NesRom(
            prgRom = prg,
            chrRom = chr,
            mapperId = 4,
            isVerticalMirroring = true,
            hasBatteryRam = false
        )

        val console = NesConsole(rom)

        // Select R6 (PRG Bank at $8000) and set to Bank 5
        console.write(0x8000, 6) // Bank Select: R6
        console.write(0x8001, 5) // Bank Data: 5
        assertEquals(6, console.read(0x8000)) // Bank 5 has byte (5 + 1) = 6

        // Select R7 (PRG Bank at $A000) and set to Bank 8
        console.write(0x8000, 7) // Bank Select: R7
        console.write(0x8001, 8) // Bank Data: 8
        assertEquals(9, console.read(0xA000)) // Bank 8 has byte (8 + 1) = 9

        // Test CHR Banking: Select R2 (1KB CHR at $1000) and set to Bank 25
        console.write(0x8000, 2)
        console.write(0x8001, 25)
        assertEquals(35, console.readChr(0x1000)) // Bank 25 has byte (25 + 10) = 35

        // Test Mirroring: $A000 even
        console.write(0xA000, 1) // 1 = Horizontal
        assertFalse(console.ppu.isVerticalMirroring)
        console.write(0xA000, 0) // 0 = Vertical
        assertTrue(console.ppu.isVerticalMirroring)
    }

    @Test
    fun testApuRegisters() {
        val apu = com.example.nes.emulator.Apu2A03()
        val console = com.example.nes.emulator.NesConsole(apu = apu)

        // Enable Pulse 1 and Triangle channels via $4015
        console.write(0x4015, 0x05)
        assertTrue(apu.pulse1Enabled)
        assertFalse(apu.pulse2Enabled)
        assertTrue(apu.triangleEnabled)

        // Write Pulse 1 parameters ($4000, $4002, $4003)
        console.write(0x4000, 0xBF) // Duty 50%, Halt length, Constant vol, Vol 15
        assertEquals(2, apu.pulse1Duty)
        assertTrue(apu.pulse1LengthHalt)
        assertEquals(15, apu.pulse1Volume)

        console.write(0x4002, 0xFD) // Timer low
        console.write(0x4003, 0x08) // Length counter load 1 (index 1 = 254), timer high 0
        assertEquals(0x00FD, apu.pulse1Timer)
        assertEquals(254, apu.pulse1LengthCounter)

        // Check $4015 status register read: Pulse 1 length > 0
        val status = console.read(0x4015)
        assertEquals(0x01, status and 0x01) // Pulse 1 active
    }
}
