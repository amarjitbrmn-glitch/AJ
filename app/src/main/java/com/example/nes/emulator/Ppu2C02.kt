package com.example.nes.emulator

class Ppu2C02 {
    val frameBuffer = IntArray(256 * 240) { 0xFF000000.toInt() }

    // Memory
    val vram = ByteArray(2048) // 2KB Nametable RAM (2 screens)
    val paletteRam = ByteArray(32) // Palettes
    val oam = ByteArray(256) // 64 Sprites x 4 bytes

    // Registers
    var ppuCtrl: Int = 0   // $2000
    var ppuMask: Int = 0   // $2001
    var ppuStatus: Int = 0x80 // $2002 (Power-up with VBLANK set so initial wait loops pass)
    var oamAddr: Int = 0   // $2003
    var ppuScrollX: Int = 0
    var ppuScrollY: Int = 0
    private var writeToggle: Boolean = false // Shared write toggle for $2005 and $2006
    private var vramAddr: Int = 0
    private var tempVramAddr: Int = 0
    private var readBuffer: Int = 0

    var isNmiOccurred: Boolean = false
    var isNmiOutput: Boolean = false

    // Mirroring: 0 = Horizontal, 1 = Vertical, 2 = SingleScreenLower, 3 = SingleScreenUpper
    var mirroringMode: Int = 1

    var isVerticalMirroring: Boolean
        get() = mirroringMode == 1
        set(value) {
            mirroringMode = if (value) 1 else 0
        }

    var scanline: Int = 0
    var sprite0Hit: Boolean = false

    // CHR data (Pattern tables from Cartridge)
    var chrRom: ByteArray = ByteArray(8192)

    interface ChrBus {
        fun readChr(addr: Int): Int
        fun writeChr(addr: Int, value: Int)
    }

    var chrBus: ChrBus? = null
    var onScanlineHook: ((Int) -> Unit)? = null

    fun reset() {
        ppuCtrl = 0
        ppuMask = 0
        ppuStatus = 0x80
        oamAddr = 0
        ppuScrollX = 0
        ppuScrollY = 0
        writeToggle = false
        vramAddr = 0
        tempVramAddr = 0
        scanline = 0
        isNmiOccurred = false
        sprite0Hit = false
        paletteRam.fill(0)
    }

    fun writeRegister(address: Int, value: Int) {
        val reg = 0x2000 or (address and 0x0007)
        val v = value and 0xFF
        when (reg) {
            0x2000 -> {
                val oldNmi = (ppuCtrl and 0x80) != 0
                ppuCtrl = v
                isNmiOutput = (v and 0x80) != 0
                if (!oldNmi && isNmiOutput && (ppuStatus and 0x80) != 0) {
                    isNmiOccurred = true
                }
                tempVramAddr = (tempVramAddr and 0xF3FF) or ((v and 0x03) shl 10)
            }
            0x2001 -> ppuMask = v
            0x2003 -> oamAddr = v
            0x2004 -> {
                oam[oamAddr] = v.toByte()
                oamAddr = (oamAddr + 1) and 0xFF
            }
            0x2005 -> { // PPUSCROLL
                if (!writeToggle) {
                    ppuScrollX = v
                    writeToggle = true
                } else {
                    ppuScrollY = v
                    writeToggle = false
                }
            }
            0x2006 -> { // PPUADDR
                if (!writeToggle) {
                    tempVramAddr = ((v and 0x3F) shl 8) or (tempVramAddr and 0x00FF)
                    writeToggle = true
                } else {
                    tempVramAddr = (tempVramAddr and 0xFF00) or v
                    vramAddr = tempVramAddr
                    writeToggle = false
                }
            }
            0x2007 -> { // PPUDATA
                writeVram(vramAddr, v)
                val increment = if ((ppuCtrl and 0x04) != 0) 32 else 1
                vramAddr = (vramAddr + increment) and 0x3FFF
            }
        }
    }

    fun readRegister(address: Int): Int {
        val reg = 0x2000 or (address and 0x0007)
        return when (reg) {
            0x2002 -> {
                val status = ppuStatus
                ppuStatus = ppuStatus and 0x7F // Clear vblank flag on read
                writeToggle = false
                status
            }
            0x2004 -> oam[oamAddr].toInt() and 0xFF
            0x2007 -> {
                var value = readBuffer
                readBuffer = readVram(vramAddr)
                if (vramAddr >= 0x3F00) {
                    value = readBuffer // Palette reads are immediate
                }
                val increment = if ((ppuCtrl and 0x04) != 0) 32 else 1
                vramAddr = (vramAddr + increment) and 0x3FFF
                value
            }
            else -> 0
        }
    }

    fun readVram(addr: Int): Int {
        val a = addr and 0x3FFF
        return when {
            a < 0x2000 -> {
                chrBus?.readChr(a) ?: if (a < chrRom.size) chrRom[a].toInt() and 0xFF else 0
            }
            a < 0x3F00 -> {
                val mirror = getMirrorAddress(a)
                vram[mirror].toInt() and 0xFF
            }
            else -> {
                var palAddr = a and 0x1F
                if (palAddr == 0x10 || palAddr == 0x14 || palAddr == 0x18 || palAddr == 0x1C) {
                    palAddr -= 0x10
                }
                paletteRam[palAddr].toInt() and 0xFF
            }
        }
    }

    fun writeVram(addr: Int, value: Int) {
        val a = addr and 0x3FFF
        when {
            a < 0x2000 -> {
                if (chrBus != null) {
                    chrBus?.writeChr(a, value)
                } else if (a < chrRom.size) {
                    chrRom[a] = value.toByte()
                }
            }
            a < 0x3F00 -> {
                val mirror = getMirrorAddress(a)
                vram[mirror] = value.toByte()
            }
            else -> {
                var palAddr = a and 0x1F
                if (palAddr == 0x10 || palAddr == 0x14 || palAddr == 0x18 || palAddr == 0x1C) {
                    palAddr -= 0x10
                }
                paletteRam[palAddr] = value.toByte()
            }
        }
    }

    private fun getMirrorAddress(addr: Int): Int {
        val offset = (addr - 0x2000) and 0x0FFF
        val table = offset / 0x0400
        val sub = offset % 0x0400

        return when (mirroringMode) {
            0 -> { // Horizontal: Table 0 & 1 -> VRAM 0, Table 2 & 3 -> VRAM 1
                (((table ushr 1) and 1) * 0x0400 + sub) and 0x07FF
            }
            1 -> { // Vertical: Table 0 & 2 -> VRAM 0, Table 1 & 3 -> VRAM 1
                (((table and 1)) * 0x0400 + sub) and 0x07FF
            }
            2 -> { // Single Screen Lower: all map to Table 0
                sub and 0x03FF
            }
            3 -> { // Single Screen Upper: all map to Table 1
                (0x0400 + sub) and 0x07FF
            }
            else -> { // Default vertical
                (((table and 1)) * 0x0400 + sub) and 0x07FF
            }
        }
    }

    // Step a full scanline and render visible line if applicable
    fun stepScanline(): Boolean {
        var triggerNmi = false

        if (scanline in 0..239) {
            renderScanline(scanline)
            onScanlineHook?.invoke(scanline)
        } else if (scanline == 241) {
            // Start of VBLANK
            ppuStatus = ppuStatus or 0x80
            if (isNmiOutput) {
                isNmiOccurred = true
                triggerNmi = true
            }
        } else if (scanline == 261) {
            // Pre-render scanline: clear VBLANK & Sprite 0 Hit
            ppuStatus = ppuStatus and 0x1F
            sprite0Hit = false
            isNmiOccurred = false
        }

        scanline++
        if (scanline > 261) {
            scanline = 0
        }

        return triggerNmi
    }

    // Render one 256-pixel horizontal scanline
    private fun renderScanline(y: Int) {
        val showBg = (ppuMask and 0x08) != 0
        val showSprites = (ppuMask and 0x10) != 0
        val universalBgColorIndex = paletteRam[0].toInt() and 0xFF
        val universalBgRgb = NES_PALETTE[universalBgColorIndex and 0x3F]

        val lineOffset = y * 256
        val bgPixelMask = BooleanArray(256)

        // 1. Render Background
        if (showBg) {
            val baseNametable = ppuCtrl and 0x03
            val bgPatternTable = if ((ppuCtrl and 0x10) != 0) 0x1000 else 0x0000

            val scrollX = (ppuScrollX + ((baseNametable and 1) * 256)) % 512
            val scrollY = (y + ppuScrollY + (((baseNametable ushr 1) and 1) * 240)) % 480

            val fineY = scrollY % 8
            val tileY = (scrollY / 8) % 30

            for (x in 0 until 256) {
                val totalX = (scrollX + x) % 512
                val fineX = totalX % 8
                val tileX = (totalX / 8) % 32
                val currentTable = ((totalX / 256) and 1) or (((scrollY / 240) and 1) shl 1)

                val ntBase = 0x2000 + (currentTable * 0x0400)
                val tileIndexAddr = ntBase + (tileY * 32) + tileX
                val tileIndex = readVram(tileIndexAddr)

                // Fetch pattern table tile
                val patternAddr = bgPatternTable + (tileIndex * 16) + fineY
                val pLo = readVram(patternAddr)
                val pHi = readVram(patternAddr + 8)

                val bitShift = 7 - fineX
                val colorBit0 = (pLo ushr bitShift) and 1
                val colorBit1 = (pHi ushr bitShift) and 1
                val pixelColor = (colorBit1 shl 1) or colorBit0

                if (pixelColor != 0) {
                    val attrAddr = ntBase + 0x03C0 + ((tileY / 4) * 8) + (tileX / 4)
                    val attrByte = readVram(attrAddr)
                    val attrShift = ((tileY and 2) shl 1) or (tileX and 2)
                    val palIndex = (attrByte ushr attrShift) and 3

                    val paletteAddr = 0x3F00 + (palIndex * 4) + pixelColor
                    val colorIndex = readVram(paletteAddr)
                    frameBuffer[lineOffset + x] = NES_PALETTE[colorIndex and 0x3F]
                    bgPixelMask[x] = true
                } else {
                    frameBuffer[lineOffset + x] = universalBgRgb
                }
            }
        } else {
            frameBuffer.fill(universalBgRgb, lineOffset, lineOffset + 256)
        }

        // 2. Render Sprites (64 sprites from OAM)
        if (showSprites) {
            val spriteSize = if ((ppuCtrl and 0x20) != 0) 16 else 8
            val spritePatternTable = if ((ppuCtrl and 0x08) != 0) 0x1000 else 0x0000

            // Render from sprite 63 down to 0 for correct priority
            for (i in 63 downTo 0) {
                val oamBase = i * 4
                val spriteY = (oam[oamBase].toInt() and 0xFF) + 1
                val tileIndex = oam[oamBase + 1].toInt() and 0xFF
                val attributes = oam[oamBase + 2].toInt() and 0xFF
                val spriteX = oam[oamBase + 3].toInt() and 0xFF

                if (y in spriteY until (spriteY + spriteSize)) {
                    var row = y - spriteY
                    val flipH = (attributes and 0x40) != 0
                    val flipV = (attributes and 0x80) != 0
                    val priority = (attributes and 0x20) != 0
                    val palIndex = (attributes and 0x03) + 4

                    if (flipV) row = (spriteSize - 1) - row

                    val patternAddr = if (spriteSize == 8) {
                        spritePatternTable + (tileIndex * 16) + row
                    } else {
                        val table = if ((tileIndex and 1) != 0) 0x1000 else 0x0000
                        val actualTile = tileIndex and 0xFE
                        table + (actualTile * 16) + (if (row >= 8) 16 else 0) + (row % 8)
                    }

                    val pLo = readVram(patternAddr)
                    val pHi = readVram(patternAddr + 8)

                    for (x in 0 until 8) {
                        val px = spriteX + x
                        if (px in 0..255) {
                            val col = if (flipH) x else (7 - x)
                            val bit0 = (pLo ushr col) and 1
                            val bit1 = (pHi ushr col) and 1
                            val pixelColor = (bit1 shl 1) or bit0

                            if (pixelColor != 0) {
                                // Sprite 0 Hit detection
                                if (i == 0 && bgPixelMask[px] && !sprite0Hit && px < 255) {
                                    sprite0Hit = true
                                    ppuStatus = ppuStatus or 0x40
                                }

                                if (!priority || !bgPixelMask[px]) {
                                    val paletteAddr = 0x3F00 + (palIndex * 4) + pixelColor
                                    val colorIndex = readVram(paletteAddr)
                                    frameBuffer[lineOffset + px] = NES_PALETTE[colorIndex and 0x3F]
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        val NES_PALETTE = intArrayOf(
            0xFF666666.toInt(), 0xFF002A88.toInt(), 0xFF1412A7.toInt(), 0xFF3B00A4.toInt(),
            0xFF5C007E.toInt(), 0xFF6E0040.toInt(), 0xFF6C0600.toInt(), 0xFF561D00.toInt(),
            0xFF333500.toInt(), 0xFF0B4800.toInt(), 0xFF005200.toInt(), 0xFF004F08.toInt(),
            0xFF00404D.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(),

            0xFFADADAD.toInt(), 0xFF155FD9.toInt(), 0xFF4240FF.toInt(), 0xFF7527FE.toInt(),
            0xFFA01ACC.toInt(), 0xFFB71E7B.toInt(), 0xFFB53120.toInt(), 0xFF994E00.toInt(),
            0xFF6B6D00.toInt(), 0xFF388700.toInt(), 0xFF0C9300.toInt(), 0xFF008F32.toInt(),
            0xFF007C8D.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(),

            0xFFFFFEFF.toInt(), 0xFF64B0FF.toInt(), 0xFF9290FF.toInt(), 0xFFC676FF.toInt(),
            0xFFF36AFF.toInt(), 0xFFFE6ECC.toInt(), 0xFFFE8170.toInt(), 0xFFEA9E22.toInt(),
            0xFFBCBE00.toInt(), 0xFF88D800.toInt(), 0xFF5CE430.toInt(), 0xFF45E082.toInt(),
            0xFF48CDDE.toInt(), 0xFF4F4F4F.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(),

            0xFFFFFEFF.toInt(), 0xFFC0DFFF.toInt(), 0xFFD3D2FF.toInt(), 0xFFE8C8FF.toInt(),
            0xFFFBC2FF.toInt(), 0xFFFEC4EA.toInt(), 0xFFFECCC5.toInt(), 0xFFF7D8A5.toInt(),
            0xFFE4E594.toInt(), 0xFFCFEF96.toInt(), 0xFFBDF4AB.toInt(), 0xFFB3F3CC.toInt(),
            0xFFB5EBF2.toInt(), 0xFFB8B8B8.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt()
        )
    }
}
