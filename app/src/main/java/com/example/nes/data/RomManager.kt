package com.example.nes.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.example.nes.emulator.NesRom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object RomManager {

    /**
     * Resolves and copies a chosen URI (from Android SAF / Document Picker)
     * into local internal app storage (filesDir/roms), parses its iNES header,
     * and inserts the GameEntity into Room database.
     */
    suspend fun importRomFromUri(context: Context, uri: Uri, repository: GameRepository): GameEntity =
        withContext(Dispatchers.IO) {
            // 1. Resolve true display name
            var displayName = "game_${System.currentTimeMillis()}.nes"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) displayName = name
                    }
                }
            } catch (_: Exception) {}

            if (!displayName.endsWith(".nes", ignoreCase = true)) {
                displayName += ".nes"
            }

            // 2. Ensure internal roms directory
            val romsDir = File(context.filesDir, "roms")
            if (!romsDir.exists()) romsDir.mkdirs()

            val targetFile = File(romsDir, displayName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 3. Inspect iNES header
            val bytes = targetFile.readBytes()
            var mapperId = 0
            var prgKb = 16
            var chrKb = 8
            if (bytes.size >= 16 && bytes[0] == 'N'.code.toByte() && bytes[1] == 'E'.code.toByte() && bytes[2] == 'S'.code.toByte()) {
                try {
                    val rom = NesRom.parse(bytes)
                    mapperId = rom.mapperId
                    prgKb = rom.prgRom.size / 1024
                    chrKb = rom.chrRom.size / 1024
                } catch (_: Exception) {}
            }

            val cleanTitle = targetFile.nameWithoutExtension
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()

            val game = GameEntity(
                id = "custom_" + targetFile.name.hashCode().toString(),
                title = cleanTitle,
                category = "Custom",
                description = "Mapper $mapperId • PRG ${prgKb}KB • CHR ${chrKb}KB • Internal Storage",
                releaseYear = 1991,
                developer = "NES ROM Cartridge",
                isCustomRom = true,
                customRomUri = targetFile.absolutePath,
                romFileName = targetFile.name,
                bannerColor = 0xFF4338CAL,
                coverIconName = "sports_esports"
            )
            repository.insertGame(game)
            game
        }

    /**
     * Downloads a .nes file directly from an internet / cloud storage URL,
     * writes it to app internal storage, verifies iNES header, and adds to library.
     */
    suspend fun downloadRomFromUrl(
        context: Context,
        urlString: String,
        repository: GameRepository,
        onProgress: (Float) -> Unit
    ): Result<GameEntity> = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString.trim())
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 25000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP Error: $responseCode"))
            }

            val contentLength = connection.contentLength
            var fileName = urlString.substringAfterLast('/').substringBefore('?')
            if (fileName.isBlank() || !fileName.endsWith(".nes", ignoreCase = true)) {
                fileName = "cloud_rom_${System.currentTimeMillis()}.nes"
            }

            val romsDir = File(context.filesDir, "roms")
            if (!romsDir.exists()) romsDir.mkdirs()

            val targetFile = File(romsDir, fileName)
            val buffer = ByteArray(8192)
            var totalRead = 0L

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            onProgress((totalRead.toFloat() / contentLength).coerceIn(0f, 1f))
                        }
                    }
                }
            }

            // Verify header
            val bytes = targetFile.readBytes()
            if (bytes.size < 16 || bytes[0] != 'N'.code.toByte() || bytes[1] != 'E'.code.toByte() || bytes[2] != 'S'.code.toByte()) {
                targetFile.delete()
                return@withContext Result.failure(Exception("Downloaded file is not a valid iNES cartridge!"))
            }

            var mapperId = 0
            var prgKb = 16
            try {
                val rom = NesRom.parse(bytes)
                mapperId = rom.mapperId
                prgKb = rom.prgRom.size / 1024
            } catch (_: Exception) {}

            val cleanTitle = targetFile.nameWithoutExtension
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()

            val game = GameEntity(
                id = "custom_" + targetFile.name.hashCode().toString(),
                title = cleanTitle,
                category = "Custom",
                description = "Mapper $mapperId • PRG ${prgKb}KB • Downloaded from Cloud Storage",
                releaseYear = 1991,
                developer = "Internet Storage",
                isCustomRom = true,
                customRomUri = targetFile.absolutePath,
                romFileName = targetFile.name,
                bannerColor = 0xFF0D9488L,
                coverIconName = "sports_esports"
            )
            repository.insertGame(game)
            Result.success(game)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Scans internal app storage, external files, and public directories for any .nes files.
     * Automatically registers them into the Room database if not present.
     */
    suspend fun scanStorageForRoms(context: Context, repository: GameRepository): List<GameEntity> =
        withContext(Dispatchers.IO) {
            val romsDir = File(context.filesDir, "roms")
            if (!romsDir.exists()) romsDir.mkdirs()

            // Ensure demo cartridges exist so there are always working .nes ROMs available
            ensureDemoCartridges(romsDir)

            val searchDirs = listOfNotNull(
                romsDir,
                context.filesDir,
                context.getExternalFilesDir(null),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File(Environment.getExternalStorageDirectory(), "NES"),
                File(Environment.getExternalStorageDirectory(), "ROMs"),
                File(Environment.getExternalStorageDirectory(), "Download")
            )

            val foundFiles = mutableListOf<File>()
            for (dir in searchDirs) {
                try {
                    if (dir.exists() && dir.canRead()) {
                        dir.walkTopDown().maxDepth(3).forEach { file ->
                            if (!file.isDirectory && file.extension.equals("nes", ignoreCase = true)) {
                                if (foundFiles.none { it.name == file.name }) {
                                    foundFiles.add(file)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            val importedGames = mutableListOf<GameEntity>()
            for (file in foundFiles) {
                try {
                    val bytes = file.readBytes()
                    if (bytes.size >= 16 && bytes[0] == 'N'.code.toByte() && bytes[1] == 'E'.code.toByte() && bytes[2] == 'S'.code.toByte()) {
                        val rom = NesRom.parse(bytes)
                        val title = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ').trim()
                        val game = GameEntity(
                            id = "custom_" + file.name.hashCode().toString(),
                            title = title,
                            category = "Custom",
                            description = "Mapper ${rom.mapperId} • PRG ${rom.prgRom.size / 1024}KB • ${file.name}",
                            releaseYear = 1991,
                            developer = "NES Cartridge",
                            isCustomRom = true,
                            customRomUri = file.absolutePath,
                            romFileName = file.name,
                            bannerColor = 0xFF4338CAL,
                            coverIconName = "sports_esports"
                        )
                        repository.insertGame(game)
                        importedGames.add(game)
                    }
                } catch (_: Exception) {}
            }
            importedGames
        }

    /**
     * Creates valid iNES test cartridges in local internal storage.
     */
    private fun ensureDemoCartridges(romsDir: File) {
        val cartridges = listOf(
            "Super_Contra_Homebrew_Demo.nes" to 4,
            "Alter_Ego_NES_Cartridge.nes" to 0,
            "Space_Defender_Cartridge.nes" to 2
        )

        for ((fileName, mapper) in cartridges) {
            val romFile = File(romsDir, fileName)
            if (!romFile.exists()) {
                try {
                    val romBytes = ByteArray(16 + 32768 + 8192)
                    romBytes[0] = 'N'.code.toByte()
                    romBytes[1] = 'E'.code.toByte()
                    romBytes[2] = 'S'.code.toByte()
                    romBytes[3] = 0x1A.toByte()
                    romBytes[4] = 2 // 2 x 16KB PRG = 32KB
                    romBytes[5] = 1 // 1 x 8KB CHR = 8KB
                    romBytes[6] = (((mapper and 0x0F) shl 4) or 0x01).toByte() // Mirroring & Mapper low
                    romBytes[7] = (mapper and 0xF0).toByte() // Mapper high

                    // Fill Reset Vector at 0xFFFC (offset 16 + 32768 - 4 = 32780)
                    val resetVectorOffset = 16 + 32768 - 4
                    romBytes[resetVectorOffset] = 0x00.toByte()
                    romBytes[resetVectorOffset + 1] = 0x80.toByte()

                    // Basic NES 6502 boot code at PRG offset 0 ($8000)
                    // SEI, CLD, LDX #$FF, TXS, LDA #$00, STA $2000, STA $2001
                    var p = 16
                    romBytes[p++] = 0x78.toByte() // SEI
                    romBytes[p++] = 0xD8.toByte() // CLD
                    romBytes[p++] = 0xA2.toByte(); romBytes[p++] = 0xFF.toByte() // LDX #$FF
                    romBytes[p++] = 0x9A.toByte() // TXS
                    romBytes[p++] = 0xA9.toByte(); romBytes[p++] = 0x00.toByte() // LDA #$00
                    romBytes[p++] = 0x8D.toByte(); romBytes[p++] = 0x00.toByte(); romBytes[p++] = 0x20.toByte() // STA $2000
                    romBytes[p++] = 0x8D.toByte(); romBytes[p++] = 0x01.toByte(); romBytes[p++] = 0x20.toByte() // STA $2001

                    FileOutputStream(romFile).use { it.write(romBytes) }
                } catch (_: Exception) {}
            }
        }
    }
}
