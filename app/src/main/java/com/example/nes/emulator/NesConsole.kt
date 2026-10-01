package com.example.nes.emulator

class NesConsole(val rom: NesRom? = null, val apu: Apu2A03 = Apu2A03()) : Cpu6502.MemoryBus, Ppu2C02.ChrBus {
    val ram = ByteArray(2048) // 2KB internal RAM ($0000 - $07FF, mirrored to $1FFF)
    val prgRam = ByteArray(8192) // 8KB PRG RAM ($6000 - $7FFF)

    val ppu = Ppu2C02()
    val cpu = Cpu6502(this)

    // Controller 1 and 2 state (bitmask of buttons currently pressed)
    var controller1State: Int = 0
    var controller2State: Int = 0

    private var controller1Shift: Int = 0
    private var controller2Shift: Int = 0
    private var controllerStrobe: Boolean = false

    // Mapper ID
    var mapperId: Int = 0

    // Mapper 1 (MMC1) registers
    private var mmc1Shift: Int = 0x10
    private var mmc1Control: Int = 0x0C
    private var mmc1ChrBank0: Int = 0
    private var mmc1ChrBank1: Int = 0
    private var mmc1PrgBank: Int = 0

    // Mapper 2 (UxROM) & Mapper 71 (Camerica) registers
    private var uxromPrgBank: Int = 0

    // Mapper 3 (CNROM) registers
    private var cnromChrBank: Int = 0

    // Mapper 4 (MMC3 - Super Contra, SMB3, Mega Man, etc.) registers
    private var mmc3BankSelect: Int = 0
    private var mmc3PrgMode: Boolean = false
    private var mmc3ChrMode: Boolean = false
    private val mmc3Registers = IntArray(8)
    private var mmc3IrqLatch: Int = 0
    private var mmc3IrqCounter: Int = 0
    private var mmc3IrqReload: Boolean = false
    private var mmc3IrqEnabled: Boolean = false

    // Mapper 7 (AxROM) registers
    private var axromPrgBank: Int = 0

    // Mapper 9 (MMC2 - Punch Out!!) & Mapper 10 (MMC4) registers
    private var mmc2PrgBank: Int = 0
    private var mmc2ChrBank0Fd: Int = 0
    private var mmc2ChrBank0Fe: Int = 0
    private var mmc2ChrBank1Fd: Int = 0
    private var mmc2ChrBank1Fe: Int = 0
    private var mmc2Latch0: Int = 0xFE
    private var mmc2Latch1: Int = 0xFE

    // Mapper 66 (GxROM) registers
    private var gxromPrgBank: Int = 0
    private var gxromChrBank: Int = 0

    init {
        ppu.chrBus = this
        rom?.let {
            mapperId = it.mapperId
            ppu.chrRom = it.chrRom
            ppu.mirroringMode = if (it.isVerticalMirroring) 1 else 0
        }

        // MMC3 scanline IRQ counter
        ppu.onScanlineHook = { scanline ->
            if (mapperId == 4 && scanline in 0..239) {
                if ((ppu.ppuMask and 0x18) != 0) {
                    if (mmc3IrqCounter == 0 || mmc3IrqReload) {
                        mmc3IrqCounter = mmc3IrqLatch
                        mmc3IrqReload = false
                    } else {
                        mmc3IrqCounter--
                    }
                    if (mmc3IrqCounter == 0 && mmc3IrqEnabled) {
                        cpu.triggerIrq()
                    }
                }
            }
        }

        reset()
    }

    fun reset() {
        ram.fill(0)
        prgRam.fill(0)
        ppu.reset()
        rom?.let {
            ppu.mirroringMode = if (it.isVerticalMirroring) 1 else 0
        }

        // Reset mapper registers
        mmc1Shift = 0x10
        mmc1Control = 0x0C
        mmc1PrgBank = 0
        mmc1ChrBank0 = 0
        mmc1ChrBank1 = 0
        uxromPrgBank = 0
        cnromChrBank = 0

        mmc3BankSelect = 0
        mmc3PrgMode = false
        mmc3ChrMode = false
        mmc3Registers.fill(0)
        mmc3IrqLatch = 0
        mmc3IrqCounter = 0
        mmc3IrqReload = false
        mmc3IrqEnabled = false

        axromPrgBank = 0
        mmc2PrgBank = 0
        mmc2ChrBank0Fd = 0
        mmc2ChrBank0Fe = 0
        mmc2ChrBank1Fd = 0
        mmc2ChrBank1Fe = 0
        mmc2Latch0 = 0xFE
        mmc2Latch1 = 0xFE
        gxromPrgBank = 0
        gxromChrBank = 0

        cpu.reset()
    }

    fun setControllerButton(controllerIndex: Int, buttonBit: Int, isPressed: Boolean) {
        if (controllerIndex == 0) {
            controller1State = if (isPressed) {
                controller1State or buttonBit
            } else {
                controller1State and buttonBit.inv()
            }
        } else {
            controller2State = if (isPressed) {
                controller2State or buttonBit
            } else {
                controller2State and buttonBit.inv()
            }
        }
    }

    // Executes one full NES video frame (262 scanlines, ~29,780 CPU cycles)
    fun stepFrame() {
        val cpuCyclesPerScanline = 113.66
        var accumulatedCycles = 0.0

        for (scanline in 0 until 262) {
            accumulatedCycles += cpuCyclesPerScanline
            val cyclesToRun = accumulatedCycles.toInt()
            accumulatedCycles -= cyclesToRun

            var ran = 0
            while (ran < cyclesToRun) {
                val c = cpu.step()
                ran += c
            }

            // Step PPU for this scanline
            val nmi = ppu.stepScanline()
            if (nmi) {
                cpu.triggerNmi()
            }

            // Clock APU 4-step frame sequencer across scanlines (authentic 240 Hz / 120 Hz)
            when (scanline) {
                60 -> apu.clockFrameSequence(0)
                120 -> apu.clockFrameSequence(1)
                180 -> apu.clockFrameSequence(2)
                240 -> apu.clockFrameSequence(3)
            }
        }
    }

    override fun read(address: Int): Int {
        val addr = address and 0xFFFF
        return when {
            // 2KB Internal RAM + Mirrors
            addr < 0x2000 -> {
                ram[addr and 0x07FF].toInt() and 0xFF
            }
            // PPU Registers + Mirrors ($2000 - $3FFF)
            addr in 0x2000..0x3FFF -> {
                ppu.readRegister(addr)
            }
            // APU Status Register ($4015)
            addr == 0x4015 -> {
                apu.readRegister(addr)
            }
            // Other APU & I/O
            addr in 0x4000..0x4013 -> {
                0
            }
            // Controller 1
            addr == 0x4016 -> {
                val value = (controller1Shift and 0x01)
                controller1Shift = (controller1Shift ushr 1) or 0x80
                value
            }
            // Controller 2
            addr == 0x4017 -> {
                val value = (controller2Shift and 0x01)
                controller2Shift = (controller2Shift ushr 1) or 0x80
                value
            }
            // PRG RAM ($6000 - $7FFF)
            addr in 0x6000..0x7FFF -> {
                prgRam[addr - 0x6000].toInt() and 0xFF
            }
            // PRG ROM ($8000 - $FFFF)
            addr >= 0x8000 -> {
                readPrgRom(addr)
            }
            else -> 0
        }
    }

    private fun readPrgRom(addr: Int): Int {
        val rom = this.rom ?: return 0
        val prg = rom.prgRom
        if (prg.isEmpty()) return 0

        val offset = when (mapperId) {
            0 -> {
                // Mapper 0 (NROM): 16KB mirrored or 32KB flat
                val rel = addr - 0x8000
                if (prg.size == 16384) rel and 0x3FFF else rel % prg.size
            }
            1 -> {
                // Mapper 1 (MMC1): 16KB switchable or 32KB
                val prgMode = (mmc1Control ushr 2) and 0x03
                when (prgMode) {
                    0, 1 -> { // 32KB at $8000
                        val bank = (mmc1PrgBank and 0x0E) * 0x4000
                        (bank + (addr - 0x8000)) % prg.size
                    }
                    2 -> { // Fixed first bank at $8000, switchable at $C000
                        if (addr < 0xC000) (addr - 0x8000) % prg.size
                        else ((mmc1PrgBank and 0x0F) * 0x4000 + (addr - 0xC000)) % prg.size
                    }
                    else -> { // Switchable at $8000, fixed last bank at $C000
                        if (addr < 0xC000) ((mmc1PrgBank and 0x0F) * 0x4000 + (addr - 0x8000)) % prg.size
                        else ((prg.size - 0x4000) + (addr - 0xC000)) % prg.size
                    }
                }
            }
            2, 71 -> {
                // Mapper 2 (UxROM) & Mapper 71 (Camerica): 16KB switchable at $8000, fixed last 16KB at $C000
                if (addr < 0xC000) {
                    (uxromPrgBank * 16384 + (addr - 0x8000)) % prg.size
                } else {
                    val lastBankOffset = (prg.size - 16384).coerceAtLeast(0)
                    (lastBankOffset + (addr - 0xC000)) % prg.size
                }
            }
            3 -> {
                // Mapper 3 (CNROM): 32KB PRG ROM
                (addr - 0x8000) % prg.size
            }
            4 -> {
                // Mapper 4 (Nintendo MMC3 - Super Contra, SMB3, Mega Man):
                // 8KB banks at $8000, $A000, $C000, $E000
                val totalBanks = maxOf(1, prg.size / 8192)
                val lastBank = totalBanks - 1
                val secondToLastBank = (totalBanks - 2).coerceAtLeast(0)

                val bank = when (addr) {
                    in 0x8000..0x9FFF -> if (!mmc3PrgMode) mmc3Registers[6] else secondToLastBank
                    in 0xA000..0xBFFF -> mmc3Registers[7]
                    in 0xC000..0xDFFF -> if (mmc3PrgMode) mmc3Registers[6] else secondToLastBank
                    else -> lastBank
                }
                ((bank % totalBanks) * 8192) + (addr and 0x1FFF)
            }
            7 -> {
                // Mapper 7 (AxROM - Battletoads): 32KB switchable bank
                val total32k = maxOf(1, prg.size / 32768)
                ((axromPrgBank % total32k) * 32768) + (addr - 0x8000)
            }
            9 -> {
                // Mapper 9 (MMC2 - Punch-Out!!): 8KB switchable at $8000, fixed 24KB at $A000-$FFFF
                if (addr < 0xA000) {
                    (mmc2PrgBank * 8192 + (addr - 0x8000)) % prg.size
                } else {
                    val fixedOffset = (prg.size - 24576).coerceAtLeast(0)
                    (fixedOffset + (addr - 0xA000)) % prg.size
                }
            }
            10 -> {
                // Mapper 10 (MMC4 - Fire Emblem): 16KB switchable at $8000, fixed 16KB at $C000
                if (addr < 0xC000) {
                    (mmc2PrgBank * 16384 + (addr - 0x8000)) % prg.size
                } else {
                    val fixedOffset = (prg.size - 16384).coerceAtLeast(0)
                    (fixedOffset + (addr - 0xC000)) % prg.size
                }
            }
            66 -> {
                // Mapper 66 (GxROM - Super Mario Bros/Duck Hunt): 32KB switchable
                val total32k = maxOf(1, prg.size / 32768)
                ((gxromPrgBank % total32k) * 32768) + (addr - 0x8000)
            }
            else -> {
                (addr - 0x8000) % prg.size
            }
        }

        return prg[offset.coerceIn(prg.indices)].toInt() and 0xFF
    }

    override fun readChr(addr: Int): Int {
        val chr = rom?.chrRom ?: ppu.chrRom
        if (chr.isEmpty()) return 0

        val offset = when (mapperId) {
            1 -> {
                // Mapper 1 (MMC1) CHR banking
                val total4k = maxOf(1, chr.size / 4096)
                if ((mmc1Control and 0x10) != 0) { // 4KB mode
                    if (addr < 0x1000) {
                        ((mmc1ChrBank0 % total4k) * 4096) + addr
                    } else {
                        ((mmc1ChrBank1 % total4k) * 4096) + (addr - 0x1000)
                    }
                } else { // 8KB mode
                    val total8k = maxOf(1, chr.size / 8192)
                    (((mmc1ChrBank0 and 0x1E) % (total8k * 2)) * 4096) + addr
                }
            }
            3 -> {
                // Mapper 3 (CNROM): 8KB CHR banking
                val total8k = maxOf(1, chr.size / 8192)
                ((cnromChrBank % total8k) * 8192) + addr
            }
            4 -> {
                // Mapper 4 (MMC3 - Super Contra) CHR banking
                val total1k = maxOf(1, chr.size / 1024)
                val bank = if (!mmc3ChrMode) {
                    when (addr) {
                        in 0x0000..0x07FF -> (mmc3Registers[0] and 0xFE) + ((addr / 1024) and 1)
                        in 0x0800..0x0FFF -> (mmc3Registers[1] and 0xFE) + (((addr - 0x0800) / 1024) and 1)
                        in 0x1000..0x13FF -> mmc3Registers[2]
                        in 0x1400..0x17FF -> mmc3Registers[3]
                        in 0x1800..0x1BFF -> mmc3Registers[4]
                        else -> mmc3Registers[5]
                    }
                } else {
                    when (addr) {
                        in 0x0000..0x03FF -> mmc3Registers[2]
                        in 0x0400..0x07FF -> mmc3Registers[3]
                        in 0x0800..0x0BFF -> mmc3Registers[4]
                        in 0x0C00..0x0FFF -> mmc3Registers[5]
                        in 0x1000..0x17FF -> (mmc3Registers[0] and 0xFE) + (((addr - 0x1000) / 1024) and 1)
                        else -> (mmc3Registers[1] and 0xFE) + (((addr - 0x1800) / 1024) and 1)
                    }
                }
                ((bank % total1k) * 1024) + (addr and 0x03FF)
            }
            9, 10 -> {
                // Mapper 9/10 (Punch-Out!!): latch-based 4KB switching
                val total4k = maxOf(1, chr.size / 4096)
                val bank = if (addr < 0x1000) {
                    val b = if (mmc2Latch0 == 0xFD) mmc2ChrBank0Fd else mmc2ChrBank0Fe
                    if (addr in 0x0FD8..0x0FDF) mmc2Latch0 = 0xFD
                    else if (addr in 0x0FE8..0x0FEF) mmc2Latch0 = 0xFE
                    b
                } else {
                    val b = if (mmc2Latch1 == 0xFD) mmc2ChrBank1Fd else mmc2ChrBank1Fe
                    if (addr in 0x1FD8..0x1FDF) mmc2Latch1 = 0xFD
                    else if (addr in 0x1FE8..0x1FEF) mmc2Latch1 = 0xFE
                    b
                }
                ((bank % total4k) * 4096) + (addr and 0x0FFF)
            }
            66 -> {
                // Mapper 66 (GxROM): 8KB CHR banking
                val total8k = maxOf(1, chr.size / 8192)
                ((gxromChrBank % total8k) * 8192) + (addr and 0x1FFF)
            }
            else -> {
                addr % chr.size
            }
        }

        return chr[offset.coerceIn(chr.indices)].toInt() and 0xFF
    }

    override fun writeChr(addr: Int, value: Int) {
        val chr = rom?.chrRom ?: ppu.chrRom
        if (chr.isEmpty()) return
        // Allow writing to CHR-RAM if cart has RAM or small CHR
        if (rom == null || rom.chrRom.size <= 8192) {
            val offset = addr % chr.size
            chr[offset] = value.toByte()
        }
    }

    override fun write(address: Int, value: Int) {
        val addr = address and 0xFFFF
        val v = value and 0xFF
        when {
            // Internal RAM
            addr < 0x2000 -> {
                ram[addr and 0x07FF] = v.toByte()
            }
            // PPU Registers ($2000 - $3FFF)
            addr in 0x2000..0x3FFF -> {
                ppu.writeRegister(addr, v)
            }
            // OAM DMA ($4014) - Transferred by ALL commercial NES games!
            addr == 0x4014 -> {
                val page = v shl 8
                for (i in 0 until 256) {
                    ppu.oam[i] = read(page or i).toByte()
                }
            }
            // APU Sound Registers ($4000 - $4013, $4015, $4017)
            (addr in 0x4000..0x4013) || addr == 0x4015 || addr == 0x4017 -> {
                apu.writeRegister(addr, v)
            }
            // Controller strobe ($4016)
            addr == 0x4016 -> {
                val strobe = (v and 0x01) != 0
                if (controllerStrobe && !strobe) {
                    controller1Shift = controller1State
                    controller2Shift = controller2State
                }
                controllerStrobe = strobe
            }
            // PRG RAM ($6000 - $7FFF)
            addr in 0x6000..0x7FFF -> {
                prgRam[addr - 0x6000] = v.toByte()
            }
            // Mapper writes ($8000 - $FFFF)
            addr >= 0x8000 -> {
                writeMapper(addr, v)
            }
        }
    }

    private fun writeMapper(addr: Int, value: Int) {
        when (mapperId) {
            1 -> { // MMC1
                if ((value and 0x80) != 0) {
                    mmc1Shift = 0x10
                    mmc1Control = mmc1Control or 0x0C
                } else {
                    val complete = (mmc1Shift and 1) != 0
                    mmc1Shift = (mmc1Shift ushr 1) or ((value and 1) shl 4)
                    if (complete) {
                        val regVal = mmc1Shift
                        mmc1Shift = 0x10
                        when (addr) {
                            in 0x8000..0x9FFF -> {
                                mmc1Control = regVal
                                val mirrorMode = regVal and 0x03
                                ppu.mirroringMode = when (mirrorMode) {
                                    0 -> 2 // Single-screen lower
                                    1 -> 3 // Single-screen upper
                                    2 -> 1 // Vertical
                                    else -> 0 // Horizontal
                                }
                            }
                            in 0xA000..0xBFFF -> mmc1ChrBank0 = regVal
                            in 0xC000..0xDFFF -> mmc1ChrBank1 = regVal
                            in 0xE000..0xFFFF -> mmc1PrgBank = regVal and 0x0F
                        }
                    }
                }
            }
            2 -> { // UxROM (Contra, Castlevania)
                uxromPrgBank = value and 0x0F
            }
            3 -> { // CNROM
                cnromChrBank = value and 0x03
            }
            4 -> { // MMC3 (Super Contra, SMB3, Mega Man 3-6)
                when (addr) {
                    in 0x8000..0x9FFF -> {
                        if ((addr and 1) == 0) { // $8000 even: Bank Select
                            mmc3BankSelect = value and 0x07
                            mmc3PrgMode = (value and 0x40) != 0
                            mmc3ChrMode = (value and 0x80) != 0
                        } else { // $8001 odd: Bank Data
                            mmc3Registers[mmc3BankSelect] = value
                        }
                    }
                    in 0xA000..0xBFFF -> {
                        if ((addr and 1) == 0) { // $A000 even: Mirroring
                            ppu.mirroringMode = if ((value and 0x01) == 0) 1 else 0
                        }
                    }
                    in 0xC000..0xDFFF -> {
                        if ((addr and 1) == 0) { // $C000 even: IRQ Latch
                            mmc3IrqLatch = value
                        } else { // $C001 odd: IRQ Reload
                            mmc3IrqReload = true
                        }
                    }
                    in 0xE000..0xFFFF -> {
                        if ((addr and 1) == 0) { // $E000 even: IRQ Disable
                            mmc3IrqEnabled = false
                        } else { // $E001 odd: IRQ Enable
                            mmc3IrqEnabled = true
                        }
                    }
                }
            }
            7 -> { // AxROM (Battletoads, Marble Madness)
                axromPrgBank = value and 0x07
                ppu.mirroringMode = if ((value and 0x10) == 0) 2 else 3
            }
            9, 10 -> { // MMC2 (Punch Out) / MMC4
                when (addr) {
                    in 0xA000..0xAFFF -> mmc2PrgBank = value and 0x0F
                    in 0xB000..0xBFFF -> mmc2ChrBank0Fd = value and 0x1F
                    in 0xC000..0xCFFF -> mmc2ChrBank0Fe = value and 0x1F
                    in 0xD000..0xDFFF -> mmc2ChrBank1Fd = value and 0x1F
                    in 0xE000..0xEFFF -> mmc2ChrBank1Fe = value and 0x1F
                    in 0xF000..0xFFFF -> ppu.mirroringMode = if ((value and 1) == 0) 1 else 0
                }
            }
            66 -> { // GxROM (SMB / Duck Hunt)
                gxromPrgBank = (value ushr 4) and 0x03
                gxromChrBank = value and 0x03
            }
            71 -> { // Camerica
                if (addr in 0xC000..0xFFFF) {
                    uxromPrgBank = value and 0x0F
                }
            }
        }
    }

    companion object {
        const val BUTTON_A = 1 shl 0
        const val BUTTON_B = 1 shl 1
        const val BUTTON_SELECT = 1 shl 2
        const val BUTTON_START = 1 shl 3
        const val BUTTON_UP = 1 shl 4
        const val BUTTON_DOWN = 1 shl 5
        const val BUTTON_LEFT = 1 shl 6
        const val BUTTON_RIGHT = 1 shl 7
    }
}
