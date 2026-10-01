package com.example.nes.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Environment
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nes.data.GameEntity
import com.example.nes.data.GameRepository
import com.example.nes.data.RomManager
import com.example.nes.emulator.NesRom
import com.example.ui.theme.NesAccentGold
import com.example.ui.theme.NesDarkBg
import com.example.ui.theme.NesEmerald
import com.example.ui.theme.NesPrimaryCyan
import com.example.ui.theme.NesSecondaryRuby
import com.example.ui.theme.NesSurface
import com.example.ui.theme.NesSurfaceVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TvRomPickerDialog(
    repository: GameRepository,
    onRomSelected: (GameEntity) -> Unit,
    onDismiss: () -> Unit,
    onLaunchSystemPicker: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Device Browser, 1: Internet Storage URL, 2: Auto Scan
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isOperating by remember { mutableStateOf(false) }

    // Download URL State
    var downloadUrlInput by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableStateOf(0f) }

    // Initial folder
    val internalRomsDir = remember {
        val dir = File(context.filesDir, "roms")
        if (!dir.exists()) dir.mkdirs()
        dir
    }
    var currentDir by remember { mutableStateOf(internalRomsDir) }
    val filesList = remember { mutableStateListOf<FileItem>() }
    val scannedRomsList = remember { mutableStateListOf<GameEntity>() }

    val fileListState = rememberLazyListState()
    var selectedFileIndex by remember { mutableIntStateOf(0) }

    // Synchronize D-pad focus index with directory contents
    LaunchedEffect(filesList.size, currentDir, activeTab) {
        if (selectedFileIndex >= filesList.size) {
            selectedFileIndex = (filesList.size - 1).coerceAtLeast(0)
        }
    }

    // Connect TV D-Pad key events while ROM dialog is visible
    DisposableEffect(Unit) {
        val focusMgr = ActiveTvFocusManager.instance
        focusMgr?.onDialogKeyEvent = { event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                false
            } else {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> {
                        if (filesList.isNotEmpty()) {
                            selectedFileIndex = (selectedFileIndex + 1).coerceAtMost(filesList.size - 1)
                            coroutineScope.launch {
                                try { fileListState.animateScrollToItem(selectedFileIndex) } catch (_: Exception) {}
                            }
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> {
                        if (filesList.isNotEmpty()) {
                            selectedFileIndex = (selectedFileIndex - 1).coerceAtLeast(0)
                            coroutineScope.launch {
                                try { fileListState.animateScrollToItem(selectedFileIndex) } catch (_: Exception) {}
                            }
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_SPACE -> {
                        if (filesList.isNotEmpty() && selectedFileIndex in filesList.indices) {
                            val item = filesList[selectedFileIndex]
                            if (item.isDirectory) {
                                currentDir = item.file
                                selectedFileIndex = 0
                            } else {
                                val isRom = item.extension.equals("NES", ignoreCase = true) || item.extension.equals("ZIP", ignoreCase = true)
                                if (isRom) {
                                    coroutineScope.launch {
                                        val game = importNesFile(item.file, repository)
                                        onRomSelected(game)
                                    }
                                } else {
                                    try {
                                        val bytes = item.file.readBytes()
                                        if (bytes.size >= 16 && bytes[0] == 'N'.code.toByte() && bytes[1] == 'E'.code.toByte() && bytes[2] == 'S'.code.toByte()) {
                                            coroutineScope.launch {
                                                val game = importNesFile(item.file, repository)
                                                onRomSelected(game)
                                            }
                                        } else {
                                            Toast.makeText(context, "Selected: ${item.name}. Only NES ROM cartridges (.nes, .zip) can be booted.", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B -> {
                        if (currentDir.parentFile != null && currentDir.parentFile?.canRead() == true && currentDir != internalRomsDir) {
                            currentDir = currentDir.parentFile!!
                            selectedFileIndex = 0
                            true
                        } else {
                            onDismiss()
                            true
                        }
                    }
                    else -> false
                }
            }
        }
        onDispose {
            focusMgr?.onDialogKeyEvent = null
        }
    }

    // Quick Directory Locations
    val quickLocations = remember {
        val list = mutableListOf<StorageLocation>()
        list.add(StorageLocation("App ROMs", internalRomsDir, Icons.Default.FolderOpen))
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloads != null) {
            list.add(StorageLocation("Downloads", downloads, Icons.Default.Download))
        }
        val nesFolder = File(Environment.getExternalStorageDirectory(), "NES")
        list.add(StorageLocation("NES Folder", nesFolder, Icons.Default.SportsEsports))
        val romsFolder = File(Environment.getExternalStorageDirectory(), "ROMs")
        list.add(StorageLocation("ROMs Folder", romsFolder, Icons.Default.Folder))
        val internalStorage = Environment.getExternalStorageDirectory()
        if (internalStorage != null) {
            list.add(StorageLocation("Internal Storage", internalStorage, Icons.Default.Storage))
        }
        context.getExternalFilesDir(null)?.let {
            list.add(StorageLocation("External Files", it, Icons.Default.FolderOpen))
        }
        list
    }

    // Refresh files in current directory
    fun refreshCurrentDirectory() {
        coroutineScope.launch(Dispatchers.IO) {
            isOperating = true
            val items = mutableListOf<FileItem>()
            try {
                if (currentDir.parentFile != null && currentDir.parentFile?.canRead() == true) {
                    items.add(FileItem(name = ".. (Parent Directory)", file = currentDir.parentFile!!, isDirectory = true))
                }

                val list = currentDir.listFiles()
                if (list != null) {
                    val dirs = list.filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }
                    // In Storage Browser, show all types of files as requested!
                    val files = list.filter { !it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }

                    dirs.forEach { items.add(FileItem(it.name, it, isDirectory = true)) }
                    files.forEach {
                        items.add(
                            FileItem(
                                name = it.name,
                                file = it,
                                isDirectory = false,
                                size = formatFileSize(it.length()),
                                extension = it.extension.uppercase()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusMessage = "Cannot access directory: ${e.message}"
                }
            }
            withContext(Dispatchers.Main) {
                filesList.clear()
                filesList.addAll(items)
                isOperating = false
            }
        }
    }

    // Auto-Scan all storage for .nes files
    fun scanDevice() {
        coroutineScope.launch(Dispatchers.IO) {
            isOperating = true
            withContext(Dispatchers.Main) {
                statusMessage = "Scanning storage for all .NES ROM cartridges..."
            }
            val results = RomManager.scanStorageForRoms(context, repository)
            withContext(Dispatchers.Main) {
                scannedRomsList.clear()
                scannedRomsList.addAll(results)
                isOperating = false
                statusMessage = if (results.isNotEmpty()) "Found ${results.size} .NES cartridge ROMs!" else "No ROMs found in public folders. Try Internet Download or System Picker!"
            }
        }
    }

    // Download from URL
    fun startUrlDownload() {
        if (downloadUrlInput.isBlank()) {
            statusMessage = "Please enter a valid HTTP / HTTPS ROM URL"
            return
        }
        coroutineScope.launch {
            isOperating = true
            downloadProgress = 0f
            statusMessage = "Downloading .NES cartridge from cloud storage..."
            val result = RomManager.downloadRomFromUrl(context, downloadUrlInput, repository) { progress ->
                downloadProgress = progress
            }
            isOperating = false
            if (result.isSuccess) {
                val game = result.getOrThrow()
                statusMessage = "Downloaded ${game.title} successfully!"
                onRomSelected(game)
            } else {
                statusMessage = "Download failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    // Initial load
    LaunchedEffect(currentDir, activeTab) {
        if (activeTab == 0) {
            refreshCurrentDirectory()
        } else if (activeTab == 2 && scannedRomsList.isEmpty()) {
            scanDevice()
        }
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE0B0F19))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(720.dp)
                .fillMaxHeight(0.92f)
                .clickable(enabled = false) {}
                .border(2.dp, NesPrimaryCyan, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = NesSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // HEADER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(NesPrimaryCyan, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.SportsEsports, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = "NES ROM CARTRIDGE LOADER",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "Internal Storage • Cloud Internet Storage • System File Picker",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .tvFocusable(shape = CircleShape, focusBorderColor = NesSecondaryRuby, onClick = onDismiss),
                        shape = CircleShape,
                        color = NesSurfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // MODE TABS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabItems = listOf("Storage Browser", "Download from Internet", "Auto-Scan All ROMs")
                    tabItems.forEachIndexed { idx, label ->
                        val isSel = activeTab == idx
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .tvFocusable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusBorderColor = NesPrimaryCyan,
                                    onClick = { activeTab = idx }
                                ),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) NesPrimaryCyan else NesSurfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // TAB 0: STORAGE BROWSER
                if (activeTab == 0) {
                    // Quick location chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(quickLocations) { loc ->
                            val isCurrent = currentDir.absolutePath == loc.file.absolutePath
                            Surface(
                                modifier = Modifier
                                    .height(30.dp)
                                    .tvFocusable(
                                        shape = RoundedCornerShape(6.dp),
                                        focusBorderColor = NesAccentGold,
                                        onClick = {
                                            if (!loc.file.exists()) loc.file.mkdirs()
                                            currentDir = loc.file
                                        }
                                    ),
                                shape = RoundedCornerShape(6.dp),
                                color = if (isCurrent) NesEmerald else Color(0xFF1E293B)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(loc.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    Text(loc.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ACTION BAR: SYSTEM FILE PICKER (SAF)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Path: ${currentDir.path.takeLast(40)}",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Big System File Picker Button
                        Surface(
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("system_picker_button")
                                .tvFocusable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusBorderColor = NesAccentGold,
                                    onClick = {
                                        try {
                                            onLaunchSystemPicker()
                                        } catch (e: ActivityNotFoundException) {
                                            Toast.makeText(context, "Cannot open system file picker.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ),
                            shape = RoundedCornerShape(8.dp),
                            color = NesSecondaryRuby
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Text("Select File via System Picker", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // FILE LIST
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF0B0F19), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        if (isOperating) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = NesPrimaryCyan, modifier = Modifier.size(32.dp))
                            }
                        } else if (filesList.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("No files found in this folder.", fontSize = 12.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Click 'Select File via System Picker' above to pick any file from your device!", fontSize = 10.sp, color = NesAccentGold)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(filesList) { item ->
                                    FileListItem(
                                        item = item,
                                        onClick = {
                                            if (item.isDirectory) {
                                                currentDir = item.file
                                            } else {
                                                val isRom = item.extension.equals("NES", ignoreCase = true) || item.extension.equals("ZIP", ignoreCase = true)
                                                if (isRom) {
                                                    coroutineScope.launch {
                                                        val game = importNesFile(item.file, repository)
                                                        onRomSelected(game)
                                                    }
                                                } else {
                                                    try {
                                                        val bytes = item.file.readBytes()
                                                        if (bytes.size >= 16 && bytes[0] == 'N'.code.toByte() && bytes[1] == 'E'.code.toByte() && bytes[2] == 'S'.code.toByte()) {
                                                            coroutineScope.launch {
                                                                val game = importNesFile(item.file, repository)
                                                                onRomSelected(game)
                                                            }
                                                        } else {
                                                            Toast.makeText(context, "Selected: ${item.name}. Only NES ROM cartridges (.nes, .zip) can be booted.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // TAB 1: INTERNET / CLOUD STORAGE URL
                if (activeTab == 1) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF0B0F19), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Download .NES ROM from Internet / Cloud URL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Enter any direct download link from Google Drive, Dropbox, Internet Archive, or your web server:",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )

                        OutlinedTextField(
                            value = downloadUrlInput,
                            onValueChange = { downloadUrlInput = it },
                            placeholder = { Text("https://example.com/games/super_contra.nes", color = Color(0xFF64748B), fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rom_url_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NesPrimaryCyan,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        if (isOperating && downloadProgress > 0f) {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = NesEmerald
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .tvFocusable(
                                        shape = RoundedCornerShape(8.dp),
                                        focusBorderColor = NesEmerald,
                                        onClick = { startUrlDownload() }
                                    ),
                                shape = RoundedCornerShape(8.dp),
                                color = if (isOperating) Color(0xFF334155) else NesEmerald
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download & Play ROM", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Black)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Pre-loaded Test Cartridges (Ready to Install):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NesAccentGold)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .tvFocusable(
                                        shape = RoundedCornerShape(6.dp),
                                        focusBorderColor = NesPrimaryCyan,
                                        onClick = {
                                            coroutineScope.launch {
                                                RomManager.scanStorageForRoms(context, repository)
                                                statusMessage = "Installed cartridges into internal storage!"
                                                activeTab = 2
                                                scanDevice()
                                            }
                                        }
                                    ),
                                shape = RoundedCornerShape(6.dp),
                                color = NesSurfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("Install Super Contra & Homebrew Demos", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // TAB 2: AUTO-SCAN ALL ROMS
                if (activeTab == 2) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF0B0F19), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Found ${scannedRomsList.size} .NES Cartridges in Storage",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NesAccentGold
                            )

                            Surface(
                                modifier = Modifier
                                    .height(28.dp)
                                    .tvFocusable(shape = RoundedCornerShape(6.dp), focusBorderColor = NesPrimaryCyan, onClick = { scanDevice() }),
                                shape = RoundedCornerShape(6.dp),
                                color = NesPrimaryCyan
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                    Text("Re-Scan Storage", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isOperating) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = NesPrimaryCyan, modifier = Modifier.size(32.dp))
                            }
                        } else if (scannedRomsList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No cartridges found. Use 'Select File via System Picker' on Tab 1!", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(scannedRomsList) { game ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .tvFocusable(
                                                shape = RoundedCornerShape(8.dp),
                                                focusBorderColor = NesAccentGold,
                                                onClick = { onRomSelected(game) }
                                            ),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1E293B)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.SportsEsports, contentDescription = null, tint = NesPrimaryCyan, modifier = Modifier.size(18.dp))
                                                Column {
                                                    Text(game.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                                    Text(game.description, fontSize = 9.sp, color = Color(0xFF94A3B8), maxLines = 1)
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NesAccentGold, modifier = Modifier.size(16.dp))
                                                Text("PLAY", fontSize = 11.sp, fontWeight = FontWeight.Black, color = NesAccentGold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // STATUS MESSAGE BAR
                statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = msg, fontSize = 11.sp, color = NesAccentGold, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // FOOTER
                Text(
                    text = "Supports all NES Mappers (0, 1, 2, 3, 4 MMC3, 7, 9, 10, 66, 71) • Saves to Internal Storage",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
private fun FileListItem(
    item: FileItem,
    onClick: () -> Unit
) {
    val isRom = item.extension.equals("NES", ignoreCase = true) || item.extension.equals("ZIP", ignoreCase = true)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .tvFocusable(
                shape = RoundedCornerShape(8.dp),
                focusBorderColor = if (item.isDirectory) NesPrimaryCyan else if (isRom) NesAccentGold else Color(0xFF64748B),
                onClick = onClick
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (item.isDirectory) Color(0xFF131B2E) else Color(0xFF1E293B)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when {
                        item.isDirectory -> Icons.Default.Folder
                        isRom -> Icons.Default.SportsEsports
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = when {
                        item.isDirectory -> NesPrimaryCyan
                        isRom -> NesAccentGold
                        else -> Color(0xFF94A3B8)
                    },
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = item.name,
                    fontSize = 12.sp,
                    fontWeight = if (item.isDirectory || isRom) FontWeight.Bold else FontWeight.Normal,
                    color = if (isRom) NesAccentGold else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!item.isDirectory) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.extension.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isRom) NesEmerald else Color(0xFF334155)
                        ) {
                            Text(
                                text = item.extension,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRom) Color.Black else Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(text = item.size, fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
            }
        }
    }
}

private data class FileItem(
    val name: String,
    val file: File,
    val isDirectory: Boolean,
    val size: String = "",
    val extension: String = ""
)

private data class StorageLocation(
    val name: String,
    val file: File,
    val icon: ImageVector
)

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
    }
}

private suspend fun importNesFile(file: File, repository: GameRepository): GameEntity {
    val title = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ').trim()
    var mapperInfo = "NES Cartridge"
    try {
        val bytes = file.readBytes()
        if (bytes.size >= 16 && bytes[0] == 'N'.code.toByte()) {
            val rom = NesRom.parse(bytes)
            mapperInfo = "Mapper ${rom.mapperId} • PRG ${rom.prgRom.size / 1024}KB"
        }
    } catch (_: Exception) {}

    val game = GameEntity(
        id = "custom_" + file.name.hashCode().toString(),
        title = title,
        category = "Custom",
        description = "$mapperInfo • Saved in Internal Storage",
        releaseYear = 1991,
        developer = "NES Cartridge",
        isCustomRom = true,
        customRomUri = file.absolutePath,
        romFileName = file.name,
        bannerColor = 0xFF4F46E5L,
        coverIconName = "sports_esports"
    )
    repository.insertGame(game)
    return game
}
