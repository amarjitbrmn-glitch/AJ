package com.example.nes.emulator

class Cpu6502(private val bus: MemoryBus) {
    interface MemoryBus {
        fun read(address: Int): Int
        fun write(address: Int, value: Int)
    }

    var regA: Int = 0
    var regX: Int = 0
    var regY: Int = 0
    var regSP: Int = 0xFD
    var regPC: Int = 0
    var regStatus: Int = 0x34 // Unused bit (0x20) always set, Interrupt disabled (0x04)

    var cycles: Long = 0

    companion object {
        const val CARRY = 0x01
        const val ZERO = 0x02
        const val INTERRUPT = 0x04
        const val DECIMAL = 0x08
        const val BREAK = 0x10
        const val UNUSED = 0x20
        const val OVERFLOW = 0x40
        const val NEGATIVE = 0x80
    }

    fun reset() {
        regA = 0
        regX = 0
        regY = 0
        regSP = 0xFD
        regStatus = 0x34
        val lo = bus.read(0xFFFC)
        val hi = bus.read(0xFFFD)
        regPC = (hi shl 8) or lo
        cycles = 7
    }

    fun triggerNmi() {
        push16(regPC)
        push((regStatus and BREAK.inv()) or UNUSED)
        regStatus = regStatus or INTERRUPT
        val lo = bus.read(0xFFFA)
        val hi = bus.read(0xFFFB)
        regPC = (hi shl 8) or lo
        cycles += 7
    }

    fun triggerIrq() {
        if ((regStatus and INTERRUPT) == 0) {
            push16(regPC)
            push((regStatus and BREAK.inv()) or UNUSED)
            regStatus = regStatus or INTERRUPT
            val lo = bus.read(0xFFFE)
            val hi = bus.read(0xFFFF)
            regPC = (hi shl 8) or lo
            cycles += 7
        }
    }

    private fun push(value: Int) {
        bus.write(0x0100 or (regSP and 0xFF), value and 0xFF)
        regSP = (regSP - 1) and 0xFF
    }

    private fun push16(value: Int) {
        push((value ushr 8) and 0xFF)
        push(value and 0xFF)
    }

    private fun pop(): Int {
        regSP = (regSP + 1) and 0xFF
        return bus.read(0x0100 or regSP)
    }

    private fun pop16(): Int {
        val lo = pop()
        val hi = pop()
        return (hi shl 8) or lo
    }

    private fun setFlag(flag: Int, condition: Boolean) {
        regStatus = if (condition) regStatus or flag else regStatus and flag.inv()
    }

    fun getFlag(flag: Int): Boolean = (regStatus and flag) != 0

    private fun updateZeroNegative(value: Int) {
        val v = value and 0xFF
        setFlag(ZERO, v == 0)
        setFlag(NEGATIVE, (v and 0x80) != 0)
    }

    private fun fetch(): Int {
        val v = bus.read(regPC)
        regPC = (regPC + 1) and 0xFFFF
        return v
    }

    private fun fetch16(): Int {
        val lo = fetch()
        val hi = fetch()
        return (hi shl 8) or lo
    }

    private fun addrZp(): Int = fetch()
    private fun addrZpX(): Int = (fetch() + regX) and 0xFF
    private fun addrZpY(): Int = (fetch() + regY) and 0xFF
    private fun addrAbs(): Int = fetch16()
    private fun addrAbsX(): Int = (fetch16() + regX) and 0xFFFF
    private fun addrAbsY(): Int = (fetch16() + regY) and 0xFFFF
    private fun addrIndX(): Int {
        val base = (fetch() + regX) and 0xFF
        val lo = bus.read(base)
        val hi = bus.read((base + 1) and 0xFF)
        return (hi shl 8) or lo
    }
    private fun addrIndY(): Int {
        val base = fetch()
        val lo = bus.read(base)
        val hi = bus.read((base + 1) and 0xFF)
        return (((hi shl 8) or lo) + regY) and 0xFFFF
    }

    private fun adc(value: Int) {
        val v = value and 0xFF
        val c = if (getFlag(CARRY)) 1 else 0
        val sum = regA + v + c
        setFlag(CARRY, sum > 0xFF)
        setFlag(OVERFLOW, ((regA xor sum) and (v xor sum) and 0x80) != 0)
        regA = sum and 0xFF
        updateZeroNegative(regA)
    }

    private fun sbc(value: Int) {
        adc((value and 0xFF) xor 0xFF)
    }

    private fun cmp(reg: Int, value: Int) {
        val diff = (reg and 0xFF) - (value and 0xFF)
        setFlag(CARRY, (reg and 0xFF) >= (value and 0xFF))
        updateZeroNegative(diff)
    }

    private fun branch(condition: Boolean): Int {
        val offset = fetch().toByte().toInt()
        return if (condition) {
            regPC = (regPC + offset) and 0xFFFF
            3
        } else {
            2
        }
    }

    private fun rol(value: Int): Int {
        val c = if (getFlag(CARRY)) 1 else 0
        val v = value and 0xFF
        setFlag(CARRY, (v and 0x80) != 0)
        val res = ((v shl 1) or c) and 0xFF
        updateZeroNegative(res)
        return res
    }

    private fun ror(value: Int): Int {
        val c = if (getFlag(CARRY)) 0x80 else 0
        val v = value and 0xFF
        setFlag(CARRY, (v and 0x01) != 0)
        val res = ((v ushr 1) or c) and 0xFF
        updateZeroNegative(res)
        return res
    }

    fun step(): Int {
        val opcode = fetch()
        var cost = 2

        when (opcode) {
            // NOP
            0xEA -> cost = 2

            // Undocumented NOPs (1 byte)
            0x1A, 0x3A, 0x5A, 0x7A, 0xDA, 0xFA -> cost = 2
            // Undocumented NOPs (2 bytes)
            0x04, 0x44, 0x64, 0x14, 0x34, 0x54, 0x74, 0xD4, 0xF4, 0x80, 0x82, 0x89, 0xC2, 0xE2 -> {
                fetch(); cost = 3
            }
            // Undocumented NOPs (3 bytes)
            0x0C, 0x1C, 0x3C, 0x5C, 0x7C, 0xDC, 0xFC -> {
                fetch16(); cost = 4
            }

            // LDA
            0xA9 -> { regA = fetch(); updateZeroNegative(regA); cost = 2 }
            0xA5 -> { regA = bus.read(addrZp()); updateZeroNegative(regA); cost = 3 }
            0xB5 -> { regA = bus.read(addrZpX()); updateZeroNegative(regA); cost = 4 }
            0xAD -> { regA = bus.read(addrAbs()); updateZeroNegative(regA); cost = 4 }
            0xBD -> { regA = bus.read(addrAbsX()); updateZeroNegative(regA); cost = 4 }
            0xB9 -> { regA = bus.read(addrAbsY()); updateZeroNegative(regA); cost = 4 }
            0xA1 -> { regA = bus.read(addrIndX()); updateZeroNegative(regA); cost = 6 }
            0xB1 -> { regA = bus.read(addrIndY()); updateZeroNegative(regA); cost = 5 }

            // LDX
            0xA2 -> { regX = fetch(); updateZeroNegative(regX); cost = 2 }
            0xA6 -> { regX = bus.read(addrZp()); updateZeroNegative(regX); cost = 3 }
            0xB6 -> { regX = bus.read(addrZpY()); updateZeroNegative(regX); cost = 4 }
            0xAE -> { regX = bus.read(addrAbs()); updateZeroNegative(regX); cost = 4 }
            0xBE -> { regX = bus.read(addrAbsY()); updateZeroNegative(regX); cost = 4 }

            // LDY
            0xA0 -> { regY = fetch(); updateZeroNegative(regY); cost = 2 }
            0xA4 -> { regY = bus.read(addrZp()); updateZeroNegative(regY); cost = 3 }
            0xB4 -> { regY = bus.read(addrZpX()); updateZeroNegative(regY); cost = 4 }
            0xAC -> { regY = bus.read(addrAbs()); updateZeroNegative(regY); cost = 4 }
            0xBC -> { regY = bus.read(addrAbsX()); updateZeroNegative(regY); cost = 4 }

            // LAX (Unofficial: LDA + LDX)
            0xA7 -> { val v = bus.read(addrZp()); regA = v; regX = v; updateZeroNegative(v); cost = 3 }
            0xB7 -> { val v = bus.read(addrZpY()); regA = v; regX = v; updateZeroNegative(v); cost = 4 }
            0xAF -> { val v = bus.read(addrAbs()); regA = v; regX = v; updateZeroNegative(v); cost = 4 }
            0xBF -> { val v = bus.read(addrAbsY()); regA = v; regX = v; updateZeroNegative(v); cost = 4 }
            0xA3 -> { val v = bus.read(addrIndX()); regA = v; regX = v; updateZeroNegative(v); cost = 6 }
            0xB3 -> { val v = bus.read(addrIndY()); regA = v; regX = v; updateZeroNegative(v); cost = 5 }

            // STA
            0x85 -> { bus.write(addrZp(), regA); cost = 3 }
            0x95 -> { bus.write(addrZpX(), regA); cost = 4 }
            0x8D -> { bus.write(addrAbs(), regA); cost = 4 }
            0x9D -> { bus.write(addrAbsX(), regA); cost = 5 }
            0x99 -> { bus.write(addrAbsY(), regA); cost = 5 }
            0x81 -> { bus.write(addrIndX(), regA); cost = 6 }
            0x91 -> { bus.write(addrIndY(), regA); cost = 6 }

            // STX
            0x86 -> { bus.write(addrZp(), regX); cost = 3 }
            0x96 -> { bus.write(addrZpY(), regX); cost = 4 }
            0x8E -> { bus.write(addrAbs(), regX); cost = 4 }

            // STY
            0x84 -> { bus.write(addrZp(), regY); cost = 3 }
            0x94 -> { bus.write(addrZpX(), regY); cost = 4 }
            0x8C -> { bus.write(addrAbs(), regY); cost = 4 }

            // SAX (Unofficial: STA & STX)
            0x87 -> { bus.write(addrZp(), regA and regX); cost = 3 }
            0x97 -> { bus.write(addrZpY(), regA and regX); cost = 4 }
            0x8F -> { bus.write(addrAbs(), regA and regX); cost = 4 }
            0x83 -> { bus.write(addrIndX(), regA and regX); cost = 6 }

            // Transfers
            0xAA -> { regX = regA; updateZeroNegative(regX); cost = 2 } // TAX
            0x8A -> { regA = regX; updateZeroNegative(regA); cost = 2 } // TXA
            0xA8 -> { regY = regA; updateZeroNegative(regY); cost = 2 } // TAY
            0x98 -> { regA = regY; updateZeroNegative(regA); cost = 2 } // TYA
            0xBA -> { regX = regSP; updateZeroNegative(regX); cost = 2 } // TSX
            0x9A -> { regSP = regX; cost = 2 } // TXS

            // Stack
            0x48 -> { push(regA); cost = 3 } // PHA
            0x68 -> { regA = pop(); updateZeroNegative(regA); cost = 4 } // PLA
            0x08 -> { push((regStatus or BREAK or UNUSED)); cost = 3 } // PHP
            0x28 -> { regStatus = (pop() and BREAK.inv()) or UNUSED; cost = 4 } // PLP

            // Increment / Decrement
            0xE8 -> { regX = (regX + 1) and 0xFF; updateZeroNegative(regX); cost = 2 } // INX
            0xC8 -> { regY = (regY + 1) and 0xFF; updateZeroNegative(regY); cost = 2 } // INY
            0xCA -> { regX = (regX - 1) and 0xFF; updateZeroNegative(regX); cost = 2 } // DEX
            0x88 -> { regY = (regY - 1) and 0xFF; updateZeroNegative(regY); cost = 2 } // DEY

            0xE6 -> { val a = addrZp(); val v = (bus.read(a) + 1) and 0xFF; bus.write(a, v); updateZeroNegative(v); cost = 5 }
            0xF6 -> { val a = addrZpX(); val v = (bus.read(a) + 1) and 0xFF; bus.write(a, v); updateZeroNegative(v); cost = 6 }
            0xEE -> { val a = addrAbs(); val v = (bus.read(a) + 1) and 0xFF; bus.write(a, v); updateZeroNegative(v); cost = 6 }
            0xFE -> { val a = addrAbsX(); val v = (bus.read(a) + 1) and 0xFF; bus.write(a, v); updateZeroNegative(v); cost = 7 }

            0xC6 -> { val a = addrZp(); val v = (bus.read(a) - 1) and 0xFF; bus.write(a, v); updateZeroNegative(v); cost = 5 }
            0xD6 -> { val a = addrZpX(); val v = (bus.read(a) - 1) and 0xFF; bus.write(a, v); updateZeroNegative(v); cost = 6 }
            0xCE -> { val a = addrAbs(); val v = (bus.read(a) - 1) and 0xFF; bus.write(a, v); updateZeroNegative(v); cost = 6 }
            0xDE -> { val a = addrAbsX(); val v = (bus.read(a) - 1) and 0xFF; bus.write(a, v); updateZeroNegative(v); cost = 7 }

            // ADC
            0x69 -> { adc(fetch()); cost = 2 }
            0x65 -> { adc(bus.read(addrZp())); cost = 3 }
            0x75 -> { adc(bus.read(addrZpX())); cost = 4 }
            0x6D -> { adc(bus.read(addrAbs())); cost = 4 }
            0x7D -> { adc(bus.read(addrAbsX())); cost = 4 }
            0x79 -> { adc(bus.read(addrAbsY())); cost = 4 }
            0x61 -> { adc(bus.read(addrIndX())); cost = 6 }
            0x71 -> { adc(bus.read(addrIndY())); cost = 5 }

            // SBC
            0xE9, 0xEB -> { sbc(fetch()); cost = 2 }
            0xE5 -> { sbc(bus.read(addrZp())); cost = 3 }
            0xF5 -> { sbc(bus.read(addrZpX())); cost = 4 }
            0xED -> { sbc(bus.read(addrAbs())); cost = 4 }
            0xFD -> { sbc(bus.read(addrAbsX())); cost = 4 }
            0xF9 -> { sbc(bus.read(addrAbsY())); cost = 4 }
            0xE1 -> { sbc(bus.read(addrIndX())); cost = 6 }
            0xF1 -> { sbc(bus.read(addrIndY())); cost = 5 }

            // Comparisons: CMP
            0xC9 -> { cmp(regA, fetch()); cost = 2 }
            0xC5 -> { cmp(regA, bus.read(addrZp())); cost = 3 }
            0xD5 -> { cmp(regA, bus.read(addrZpX())); cost = 4 }
            0xCD -> { cmp(regA, bus.read(addrAbs())); cost = 4 }
            0xDD -> { cmp(regA, bus.read(addrAbsX())); cost = 4 }
            0xD9 -> { cmp(regA, bus.read(addrAbsY())); cost = 4 }
            0xC1 -> { cmp(regA, bus.read(addrIndX())); cost = 6 }
            0xD1 -> { cmp(regA, bus.read(addrIndY())); cost = 5 }

            // CPX
            0xE0 -> { cmp(regX, fetch()); cost = 2 }
            0xE4 -> { cmp(regX, bus.read(addrZp())); cost = 3 }
            0xEC -> { cmp(regX, bus.read(addrAbs())); cost = 4 }

            // CPY
            0xC0 -> { cmp(regY, fetch()); cost = 2 }
            0xC4 -> { cmp(regY, bus.read(addrZp())); cost = 3 }
            0xCC -> { cmp(regY, bus.read(addrAbs())); cost = 4 }

            // Logical: AND
            0x29 -> { regA = regA and fetch(); updateZeroNegative(regA); cost = 2 }
            0x25 -> { regA = regA and bus.read(addrZp()); updateZeroNegative(regA); cost = 3 }
            0x35 -> { regA = regA and bus.read(addrZpX()); updateZeroNegative(regA); cost = 4 }
            0x2D -> { regA = regA and bus.read(addrAbs()); updateZeroNegative(regA); cost = 4 }
            0x3D -> { regA = regA and bus.read(addrAbsX()); updateZeroNegative(regA); cost = 4 }
            0x39 -> { regA = regA and bus.read(addrAbsY()); updateZeroNegative(regA); cost = 4 }
            0x21 -> { regA = regA and bus.read(addrIndX()); updateZeroNegative(regA); cost = 6 }
            0x31 -> { regA = regA and bus.read(addrIndY()); updateZeroNegative(regA); cost = 5 }

            // Logical: ORA
            0x09 -> { regA = regA or fetch(); updateZeroNegative(regA); cost = 2 }
            0x05 -> { regA = regA or bus.read(addrZp()); updateZeroNegative(regA); cost = 3 }
            0x15 -> { regA = regA or bus.read(addrZpX()); updateZeroNegative(regA); cost = 4 }
            0x0D -> { regA = regA or bus.read(addrAbs()); updateZeroNegative(regA); cost = 4 }
            0x1D -> { regA = regA or bus.read(addrAbsX()); updateZeroNegative(regA); cost = 4 }
            0x19 -> { regA = regA or bus.read(addrAbsY()); updateZeroNegative(regA); cost = 4 }
            0x01 -> { regA = regA or bus.read(addrIndX()); updateZeroNegative(regA); cost = 6 }
            0x11 -> { regA = regA or bus.read(addrIndY()); updateZeroNegative(regA); cost = 5 }

            // Logical: EOR
            0x49 -> { regA = regA xor fetch(); updateZeroNegative(regA); cost = 2 }
            0x45 -> { regA = regA xor bus.read(addrZp()); updateZeroNegative(regA); cost = 3 }
            0x55 -> { regA = regA xor bus.read(addrZpX()); updateZeroNegative(regA); cost = 4 }
            0x4D -> { regA = regA xor bus.read(addrAbs()); updateZeroNegative(regA); cost = 4 }
            0x5D -> { regA = regA xor bus.read(addrAbsX()); updateZeroNegative(regA); cost = 4 }
            0x59 -> { regA = regA xor bus.read(addrAbsY()); updateZeroNegative(regA); cost = 4 }
            0x41 -> { regA = regA xor bus.read(addrIndX()); updateZeroNegative(regA); cost = 6 }
            0x51 -> { regA = regA xor bus.read(addrIndY()); updateZeroNegative(regA); cost = 5 }

            // BIT
            0x24 -> {
                val v = bus.read(addrZp())
                setFlag(ZERO, (regA and v) == 0)
                setFlag(OVERFLOW, (v and 0x40) != 0)
                setFlag(NEGATIVE, (v and 0x80) != 0)
                cost = 3
            }
            0x2C -> {
                val v = bus.read(addrAbs())
                setFlag(ZERO, (regA and v) == 0)
                setFlag(OVERFLOW, (v and 0x40) != 0)
                setFlag(NEGATIVE, (v and 0x80) != 0)
                cost = 4
            }

            // Shifts: ASL
            0x0A -> {
                setFlag(CARRY, (regA and 0x80) != 0)
                regA = (regA shl 1) and 0xFF
                updateZeroNegative(regA)
                cost = 2
            }
            0x06 -> {
                val a = addrZp()
                val v = bus.read(a)
                setFlag(CARRY, (v and 0x80) != 0)
                val res = (v shl 1) and 0xFF
                bus.write(a, res)
                updateZeroNegative(res)
                cost = 5
            }
            0x16 -> {
                val a = addrZpX()
                val v = bus.read(a)
                setFlag(CARRY, (v and 0x80) != 0)
                val res = (v shl 1) and 0xFF
                bus.write(a, res)
                updateZeroNegative(res)
                cost = 6
            }
            0x0E -> {
                val a = addrAbs()
                val v = bus.read(a)
                setFlag(CARRY, (v and 0x80) != 0)
                val res = (v shl 1) and 0xFF
                bus.write(a, res)
                updateZeroNegative(res)
                cost = 6
            }
            0x1E -> {
                val a = addrAbsX()
                val v = bus.read(a)
                setFlag(CARRY, (v and 0x80) != 0)
                val res = (v shl 1) and 0xFF
                bus.write(a, res)
                updateZeroNegative(res)
                cost = 7
            }

            // Shifts: LSR
            0x4A -> {
                setFlag(CARRY, (regA and 0x01) != 0)
                regA = (regA ushr 1) and 0xFF
                updateZeroNegative(regA)
                cost = 2
            }
            0x46 -> {
                val a = addrZp()
                val v = bus.read(a)
                setFlag(CARRY, (v and 0x01) != 0)
                val res = (v ushr 1) and 0xFF
                bus.write(a, res)
                updateZeroNegative(res)
                cost = 5
            }
            0x56 -> {
                val a = addrZpX()
                val v = bus.read(a)
                setFlag(CARRY, (v and 0x01) != 0)
                val res = (v ushr 1) and 0xFF
                bus.write(a, res)
                updateZeroNegative(res)
                cost = 6
            }
            0x4E -> {
                val a = addrAbs()
                val v = bus.read(a)
                setFlag(CARRY, (v and 0x01) != 0)
                val res = (v ushr 1) and 0xFF
                bus.write(a, res)
                updateZeroNegative(res)
                cost = 6
            }
            0x5E -> {
                val a = addrAbsX()
                val v = bus.read(a)
                setFlag(CARRY, (v and 0x01) != 0)
                val res = (v ushr 1) and 0xFF
                bus.write(a, res)
                updateZeroNegative(res)
                cost = 7
            }

            // Rotates: ROL
            0x2A -> { regA = rol(regA); cost = 2 }
            0x26 -> { val a = addrZp(); bus.write(a, rol(bus.read(a))); cost = 5 }
            0x36 -> { val a = addrZpX(); bus.write(a, rol(bus.read(a))); cost = 6 }
            0x2E -> { val a = addrAbs(); bus.write(a, rol(bus.read(a))); cost = 6 }
            0x3E -> { val a = addrAbsX(); bus.write(a, rol(bus.read(a))); cost = 7 }

            // Rotates: ROR
            0x6A -> { regA = ror(regA); cost = 2 }
            0x66 -> { val a = addrZp(); bus.write(a, ror(bus.read(a))); cost = 5 }
            0x76 -> { val a = addrZpX(); bus.write(a, ror(bus.read(a))); cost = 6 }
            0x6E -> { val a = addrAbs(); bus.write(a, ror(bus.read(a))); cost = 6 }
            0x7E -> { val a = addrAbsX(); bus.write(a, ror(bus.read(a))); cost = 7 }

            // Jumps and Calls
            0x4C -> { regPC = addrAbs(); cost = 3 } // JMP abs
            0x6C -> {
                val ptr = addrAbs()
                val lo = bus.read(ptr)
                val hi = if ((ptr and 0xFF) == 0xFF) bus.read(ptr and 0xFF00) else bus.read(ptr + 1)
                regPC = (hi shl 8) or lo
                cost = 5
            } // JMP ind
            0x20 -> {
                val target = fetch16()
                push16((regPC - 1) and 0xFFFF)
                regPC = target
                cost = 6
            } // JSR
            0x60 -> { regPC = (pop16() + 1) and 0xFFFF; cost = 6 } // RTS
            0x40 -> { regStatus = (pop() and BREAK.inv()) or UNUSED; regPC = pop16(); cost = 6 } // RTI

            // Branches
            0xF0 -> cost = branch(getFlag(ZERO))       // BEQ
            0xD0 -> cost = branch(!getFlag(ZERO))      // BNE
            0x90 -> cost = branch(!getFlag(CARRY))     // BCC
            0xB0 -> cost = branch(getFlag(CARRY))      // BCS
            0x30 -> cost = branch(getFlag(NEGATIVE))   // BMI
            0x10 -> cost = branch(!getFlag(NEGATIVE))  // BPL
            0x50 -> cost = branch(!getFlag(OVERFLOW))  // BVC
            0x70 -> cost = branch(getFlag(OVERFLOW))   // BVS

            // Status flags
            0x18 -> { setFlag(CARRY, false); cost = 2 }     // CLC
            0x38 -> { setFlag(CARRY, true); cost = 2 }      // SEC
            0x58 -> { setFlag(INTERRUPT, false); cost = 2 }  // CLI
            0x78 -> { setFlag(INTERRUPT, true); cost = 2 }   // SEI
            0xD8 -> { setFlag(DECIMAL, false); cost = 2 }    // CLD
            0xF8 -> { setFlag(DECIMAL, true); cost = 2 }     // SED
            0xB8 -> { setFlag(OVERFLOW, false); cost = 2 }   // CLV

            0x00 -> { // BRK
                fetch()
                push16(regPC)
                push(regStatus or BREAK or UNUSED)
                setFlag(INTERRUPT, true)
                val lo = bus.read(0xFFFE)
                val hi = bus.read(0xFFFF)
                regPC = (hi shl 8) or lo
                cost = 7
            }

            else -> {
                // Safely advance PC for any remaining unhandled opcode
                cost = 2
            }
        }

        cycles += cost
        return cost
    }
}
