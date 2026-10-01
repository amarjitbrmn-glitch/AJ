package com.example.nes.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PestControl
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.theme.NesAccentGold
import com.example.ui.theme.NesDarkBg
import com.example.ui.theme.NesEmerald
import com.example.ui.theme.NesPrimaryCyan
import com.example.ui.theme.NesSecondaryRuby
import com.example.ui.theme.NesSurface
import com.example.ui.theme.NesSurfaceVariant
import kotlinx.coroutines.launch

@Composable
fun TvHomeScreen(
    repository: GameRepository,
    onLaunchGame: (GameEntity) -> Unit,
    onOpenSettings: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val allGames by repository.allGames.collectAsState(initial = emptyList())
    val favoriteGames by repository.favoriteGames.collectAsState(initial = emptyList())
    val recentGames by repository.recentGames.collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) }
    var focusedGame by remember { mutableStateOf<GameEntity?>(null) }
    var showRomPickerDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // LazyListState for the main game library grid
    val gameListState = rememberLazyListState()

    // Initialize custom Focus Manager specifically for TV D-Pad navigation
    val focusManager = remember { TvFocusManager(coroutineScope, gameListState) }

    DisposableEffect(focusManager) {
        ActiveTvFocusManager.instance = focusManager
        onDispose {
            ActiveTvFocusManager.instance = null
        }
    }

    LaunchedEffect(showRomPickerDialog) {
        focusManager.isDialogOpen = showRomPickerDialog
    }

    // Custom ROM Picker Launcher (System File Picker / Storage Access Framework)
    val romPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val customGame = RomManager.importRomFromUri(context, uri, repository)
                showRomPickerDialog = false
                focusedGame = customGame
                onLaunchGame(customGame)
            }
        }
    }

    // Auto-scan storage on startup to ensure all .nes ROM cartridges are visible
    LaunchedEffect(Unit) {
        RomManager.scanStorageForRoms(context, repository)
    }

    val tabs = listOf(
        "All Games",
        "Favorites",
        "Recently Played",
        "Platformer",
        "Action",
        "Arcade",
        "Puzzle",
        "Custom ROMs"
    )

    val displayedGames = when (selectedTab) {
        0 -> allGames
        1 -> favoriteGames
        2 -> recentGames
        3 -> allGames.filter { it.category == "Platformer" }
        4 -> allGames.filter { it.category == "Action" }
        5 -> allGames.filter { it.category == "Arcade" }
        6 -> allGames.filter { it.category == "Puzzle" }
        7 -> allGames.filter { it.isCustomRom }
        else -> allGames
    }

    // Synchronize games and tabs with custom focus manager
    LaunchedEffect(displayedGames) {
        focusManager.syncGames(displayedGames)
        if (displayedGames.isNotEmpty()) {
            val idx = focusManager.selectedGameIndex.coerceIn(displayedGames.indices)
            focusedGame = displayedGames[idx]
        }
    }

    LaunchedEffect(tabs) {
        focusManager.syncTabs(tabs)
    }

    // Wire callbacks
    focusManager.onLaunchGame = { game -> onLaunchGame(game) }
    focusManager.onOpenSettings = onOpenSettings
    focusManager.onOpenRomPicker = { showRomPickerDialog = true }
    focusManager.onSelectTab = { tabIdx -> selectedTab = tabIdx }
    focusManager.onGameFocused = { game -> focusedGame = game }
    focusManager.onToggleFavorite = {
        focusedGame?.let { game ->
            coroutineScope.launch {
                repository.toggleFavorite(game)
                focusedGame = game.copy(isFavorite = !game.isFavorite)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NesDarkBg)
    ) {
        // Ambient background gradient matching focused game's banner color
        val ambientColor = focusedGame?.bannerColor?.let { Color(it) } ?: Color(0xFF1E293B)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(ambientColor.copy(alpha = 0.28f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(1200f, 300f),
                        radius = 1100f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TvSafeHorizontalPadding, vertical = TvSafeVerticalPadding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BAR: Logo Branding + Category Navigation Tabs + Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // NES TV Logo Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(NesSecondaryRuby, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "NES",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "TV",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = NesPrimaryCyan,
                        letterSpacing = 1.sp
                    )
                }

                // Category Tabs (TV D-Pad navigable row)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isCategorySelected = selectedTab == index
                        val isDpadFocused = focusManager.currentSection == TvHomeSection.TOP_BAR && focusManager.selectedTopButton == index

                        val tabScale by animateFloatAsState(
                            targetValue = if (isDpadFocused) 1.08f else 1.0f,
                            animationSpec = tween(140),
                            label = "tabScale"
                        )

                        Surface(
                            modifier = Modifier
                                .height(38.dp)
                                .scale(tabScale)
                                .then(
                                    if (isDpadFocused) {
                                        Modifier
                                            .shadow(12.dp, RoundedCornerShape(10.dp), spotColor = NesPrimaryCyan)
                                            .border(2.5.dp, NesPrimaryCyan, RoundedCornerShape(10.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        selectedTab = index
                                        focusManager.currentSection = TvHomeSection.TOP_BAR
                                        focusManager.selectedTopButton = index
                                    }
                                ),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDpadFocused) NesPrimaryCyan else if (isCategorySelected) NesSurfaceVariant else Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isDpadFocused || isCategorySelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isDpadFocused) Color.Black else if (isCategorySelected) NesPrimaryCyan else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                // Action Buttons: Load ROM & Settings
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val isLoadRomFocused = focusManager.currentSection == TvHomeSection.TOP_BAR && focusManager.selectedTopButton == 100
                    val isSettingsFocused = focusManager.currentSection == TvHomeSection.TOP_BAR && focusManager.selectedTopButton == 101

                    val loadRomScale by animateFloatAsState(
                        targetValue = if (isLoadRomFocused) 1.08f else 1.0f,
                        animationSpec = tween(140),
                        label = "loadRomScale"
                    )

                    Surface(
                        modifier = Modifier
                            .height(38.dp)
                            .scale(loadRomScale)
                            .testTag("load_rom_button")
                            .then(
                                if (isLoadRomFocused) {
                                    Modifier
                                        .shadow(14.dp, RoundedCornerShape(10.dp), spotColor = NesEmerald)
                                        .border(2.5.dp, NesEmerald, RoundedCornerShape(10.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    focusManager.currentSection = TvHomeSection.TOP_BAR
                                    focusManager.selectedTopButton = 100
                                    showRomPickerDialog = true
                                }
                            ),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isLoadRomFocused) NesEmerald else Color(0xFF065F46)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Load ROM", tint = if (isLoadRomFocused) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                            Text("Load .NES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isLoadRomFocused) Color.Black else Color.White)
                        }
                    }

                    val settingsScale by animateFloatAsState(
                        targetValue = if (isSettingsFocused) 1.08f else 1.0f,
                        animationSpec = tween(140),
                        label = "settingsScale"
                    )

                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .scale(settingsScale)
                            .testTag("settings_button")
                            .then(
                                if (isSettingsFocused) {
                                    Modifier
                                        .shadow(14.dp, RoundedCornerShape(10.dp), spotColor = NesPrimaryCyan)
                                        .border(2.5.dp, NesPrimaryCyan, RoundedCornerShape(10.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    focusManager.currentSection = TvHomeSection.TOP_BAR
                                    focusManager.selectedTopButton = 101
                                    onOpenSettings()
                                }
                            ),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSettingsFocused) NesPrimaryCyan else NesSurfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = if (isSettingsFocused) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // CENTER: Cinematic Hero Game Details Showcase
            AnimatedContent(
                targetState = focusedGame,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "heroGameTransition"
            ) { game ->
                if (game != null) {
                    HeroShowcase(
                        game = game,
                        isSectionFocused = focusManager.currentSection == TvHomeSection.HERO_ACTIONS,
                        selectedActionIndex = focusManager.selectedHeroActionIndex,
                        onPlay = {
                            focusManager.currentSection = TvHomeSection.HERO_ACTIONS
                            focusManager.selectedHeroActionIndex = 1
                            onLaunchGame(game)
                        },
                        onToggleFavorite = {
                            focusManager.currentSection = TvHomeSection.HERO_ACTIONS
                            focusManager.selectedHeroActionIndex = 0
                            coroutineScope.launch {
                                repository.toggleFavorite(game)
                                focusedGame = game.copy(isFavorite = !game.isFavorite)
                            }
                        }
                    )
                } else {
                    Box(modifier = Modifier.height(140.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // BOTTOM: Games Carousel / Main Library Grid (TV D-Pad Focus Enabled)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = tabs[selectedTab].uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = NesAccentGold,
                            letterSpacing = 1.sp
                        )
                        if (focusManager.currentSection == TvHomeSection.GAME_GRID) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = NesPrimaryCyan
                            ) {
                                Text(
                                    text = "D-PAD ACTIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "${displayedGames.size} Titles • [D-Pad] Browse • [Enter] Play",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (displayedGames.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Color(0xFF131B2E), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No games found in this category. Load a custom .NES ROM to get started!",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyRow(
                        state = gameListState,
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        itemsIndexed(displayedGames, key = { _, game -> game.id }) { index, game ->
                            val isSelected = focusManager.selectedGameIndex == index
                            val isGridActive = focusManager.currentSection == TvHomeSection.GAME_GRID

                            GameCardItem(
                                game = game,
                                isSelected = isSelected,
                                isSectionFocused = isGridActive,
                                onClick = {
                                    focusManager.selectedGameIndex = index
                                    focusManager.currentSection = TvHomeSection.GAME_GRID
                                    focusedGame = game
                                    onLaunchGame(game)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Android TV Native File Browser & ROM Scanner Modal
        if (showRomPickerDialog) {
            TvRomPickerDialog(
                repository = repository,
                onRomSelected = { game ->
                    showRomPickerDialog = false
                    focusedGame = game
                    onLaunchGame(game)
                },
                onDismiss = { showRomPickerDialog = false },
                onLaunchSystemPicker = {
                    try {
                        romPickerLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            context,
                            "No external file manager app installed on this TV. Use the built-in browser!",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }
}

@Composable
private fun HeroShowcase(
    game: GameEntity,
    isSectionFocused: Boolean,
    selectedActionIndex: Int, // 0: Favorite, 1: Play
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(165.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x99131B2E)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 24.dp)
            ) {
                // Category & Meta Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(NesSecondaryRuby, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = game.category.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    Text(
                        text = "${game.releaseYear} • ${game.developer}",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )

                    if (game.highScore > 0) {
                        Text(
                            text = "★ High Score: ${game.highScore}",
                            fontSize = 11.sp,
                            color = NesAccentGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Title
                Text(
                    text = game.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Description
                Text(
                    text = game.description,
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            // Quick Play & Favorite Actions (TV Focus Controlled)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val isFavFocused = isSectionFocused && selectedActionIndex == 0
                val favScale by animateFloatAsState(
                    targetValue = if (isFavFocused) 1.15f else 1.0f,
                    animationSpec = tween(150),
                    label = "favScale"
                )

                // Favorite Button
                Surface(
                    modifier = Modifier
                        .size(46.dp)
                        .scale(favScale)
                        .then(
                            if (isFavFocused) {
                                Modifier
                                    .shadow(16.dp, CircleShape, spotColor = NesSecondaryRuby)
                                    .border(3.dp, NesSecondaryRuby, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleFavorite
                        ),
                    shape = CircleShape,
                    color = Color(0xFF1E293B)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Toggle Favorite",
                            tint = if (game.isFavorite) NesSecondaryRuby else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                val isPlayFocused = isSectionFocused && selectedActionIndex == 1
                val playScale by animateFloatAsState(
                    targetValue = if (isPlayFocused) 1.12f else 1.0f,
                    animationSpec = tween(150),
                    label = "playScale"
                )

                // Play Button
                Surface(
                    modifier = Modifier
                        .height(46.dp)
                        .scale(playScale)
                        .then(
                            if (isPlayFocused) {
                                Modifier
                                    .shadow(20.dp, RoundedCornerShape(12.dp), spotColor = NesAccentGold)
                                    .border(3.dp, NesAccentGold, RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPlay
                        ),
                    shape = RoundedCornerShape(12.dp),
                    color = NesPrimaryCyan
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                        Text(
                            text = "PLAY GAME",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = Color.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Game Library Card Item with prominent TV D-Pad Focus Highlighting
 */
@Composable
private fun GameCardItem(
    game: GameEntity,
    isSelected: Boolean,
    isSectionFocused: Boolean,
    onClick: () -> Unit
) {
    val cardColor = Color(game.bannerColor)

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.14f else 1.0f,
        animationSpec = tween(150),
        label = "cardScale"
    )

    val borderAnimColor by animateColorAsState(
        targetValue = when {
            isSelected && isSectionFocused -> NesPrimaryCyan
            isSelected -> NesAccentGold
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "borderColor"
    )

    Card(
        modifier = Modifier
            .width(165.dp)
            .height(135.dp)
            .scale(scale)
            .then(
                if (isSelected) {
                    Modifier
                        .shadow(
                            elevation = 22.dp,
                            shape = RoundedCornerShape(14.dp),
                            spotColor = if (isSectionFocused) NesPrimaryCyan else NesAccentGold,
                            ambientColor = if (isSectionFocused) NesPrimaryCyan else NesAccentGold
                        )
                        .border(
                            width = 3.5.dp,
                            color = borderAnimColor,
                            shape = RoundedCornerShape(14.dp)
                        )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = Color(0x33FFFFFF),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xEE000000)
                        )
                    )
                )
                .padding(12.dp)
        ) {
            // Top Category icon
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color(0x55000000), CircleShape)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = getCategoryIcon(game.coverIconName),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Status badges in top-right
            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (game.isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = NesSecondaryRuby,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (isSelected) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSectionFocused) NesPrimaryCyan else NesAccentGold
                    ) {
                        Text(
                            text = if (isSectionFocused) "PLAY" else "SELECT",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Bottom Title & Year
            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Text(
                    text = game.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isSelected) NesAccentGold else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${game.category} • ${game.releaseYear}",
                    fontSize = 10.sp,
                    color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                )
            }
        }
    }
}

private fun getCategoryIcon(iconName: String): ImageVector {
    return when (iconName) {
        "rocket_launch" -> Icons.Default.RocketLaunch
        "grid_view" -> Icons.Default.GridView
        "pest_control" -> Icons.Default.PestControl
        "extension" -> Icons.Default.Extension
        "psychology" -> Icons.Default.Psychology
        else -> Icons.Default.SportsEsports
    }
}
