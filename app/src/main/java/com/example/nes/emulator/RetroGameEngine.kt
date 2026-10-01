package com.example.nes.emulator

import android.graphics.Color
import kotlin.math.abs
import kotlin.random.Random

class RetroGameEngine(val apu: Apu2A03) {

    val width = 256
    val height = 240
    val frameBuffer = IntArray(width * height) { 0xFF000000.toInt() }

    var currentGameId: String = "retro_super_bros"
    var score: Int = 0
    var lives: Int = 3
    var level: Int = 1
    var isGameOver: Boolean = false
    var frameCount: Long = 0

    // Palette Colors
    private val cBlack = 0xFF000000.toInt()
    private val cSkyBlue = 0xFF64B0FF.toInt()
    private val cBrickRed = 0xFFB53120.toInt()
    private val cBrickTan = 0xFFEA9E22.toInt()
    private val cGrassGreen = 0xFF0C9300.toInt()
    private val cWhite = 0xFFFFFEFF.toInt()
    private val cYellow = 0xFFF7D8A5.toInt()
    private val cCoinGold = 0xFFEA9E22.toInt()
    private val cNavy = 0xFF002A88.toInt()
    private val cPipeGreen = 0xFF008F32.toInt()
    private val cSpaceBg = 0xFF0F172A.toInt()
    private val cLaserRed = 0xFFFE8170.toInt()
    private val cAlienCyan = 0xFF48CDDE.toInt()
    private val cAlienPink = 0xFFFE6ECC.toInt()

    // --- GAME 1: SUPER RETRO BROS STATE ---
    private var brosX = 40f
    private var brosY = 176f
    private var brosVx = 0f
    private var brosVy = 0f
    private var isBrosGrounded = true
    private var cameraX = 0f
    private var coinsCount = 0
    private val goombas = mutableListOf(Goomba(120f, 176f), Goomba(210f, 176f), Goomba(340f, 176f))
    private val coinBlocks = mutableListOf(
        CoinBlock(80f, 128f), CoinBlock(100f, 128f, hasCoin = true), CoinBlock(120f, 128f),
        CoinBlock(200f, 112f, hasCoin = true), CoinBlock(220f, 112f, hasCoin = true)
    )

    // --- GAME 2: STAR DEFENDER STATE ---
    private var shipX = 120f
    private var shipY = 200f
    private val lasers = mutableListOf<Laser>()
    private val enemies = mutableListOf<StarEnemy>()
    private val stars = Array(40) { Star(Random.nextInt(width).toFloat(), Random.nextInt(height).toFloat(), Random.nextInt(1, 3)) }
    private var enemySpawnTimer = 0

    // --- GAME 3: BRICK BREAKER STATE ---
    private var paddleX = 110f
    private var ballX = 128f
    private var ballY = 160f
    private var ballVx = 1.6f
    private var ballVy = -2.0f
    private val bricks = mutableListOf<Brick>()

    // --- GAME 4: RETRO MAZE RUNNER STATE ---
    private var pacX = 128
    private var pacY = 140
    private var pacDir = 0 // 0: Right, 1: Down, 2: Left, 3: Up
    private var pacMouth = 0
    private val dots = BooleanArray(20 * 16) { true }
    private val ghosts = mutableListOf(
        Ghost(110f, 90f, cAlienPink),
        Ghost(140f, 90f, cAlienCyan),
        Ghost(128f, 75f, cLaserRed)
    )

    // --- GAME 5: CYBER PIXEL 2048 STATE ---
    private val grid2048 = Array(4) { IntArray(4) }
    private var movedIn2048 = false

    // --- GAME 6: ALTER EGO STATE ---
    private var heroX = 60f
    private var heroY = 160f
    private var phantomX = 196f
    private var phantomY = 160f
    private val items = mutableListOf(Pair(128f, 130f), Pair(60f, 80f), Pair(196f, 80f))

    var nesConsole: NesConsole? = null

    data class Goomba(var x: Float, var y: Float, var vx: Float = -0.5f, var alive: Boolean = true)
    data class CoinBlock(val x: Float, val y: Float, var hasCoin: Boolean = false, var hit: Boolean = false)
    data class Laser(var x: Float, var y: Float, var vy: Float = -4f)
    data class StarEnemy(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Int, var hp: Int = 1)
    data class Star(var x: Float, var y: Float, val speed: Int)
    data class Brick(val x: Int, val y: Int, val w: Int, val h: Int, val color: Int, var active: Boolean = true)
    data class Ghost(var x: Float, var y: Float, val color: Int, var dir: Int = 0)

    init {
        loadGame(currentGameId)
    }

    fun loadRom(rom: NesRom) {
        nesConsole = NesConsole(rom, apu)
        currentGameId = "custom_nes_cartridge"
        score = 0
        lives = 3
        isGameOver = false
        frameCount = 0
        resetBgm()
    }

    fun loadGame(gameId: String) {
        nesConsole = null
        currentGameId = gameId
        score = 0
        lives = 3
        level = 1
        isGameOver = false
        frameCount = 0
        resetBgm()

        when (gameId) {
            "retro_super_bros" -> initSuperBros()
            "space_star_defender" -> initStarDefender()
            "brick_breaker_arcade" -> initBrickBreaker()
            "retro_maze_runner" -> initMazeRunner()
            "cyber_puzzle_2048" -> init2048()
            "alter_ego_retro" -> initAlterEgo()
            else -> initSuperBros()
        }
    }

    private fun initSuperBros() {
        brosX = 40f
        brosY = 176f
        brosVx = 0f
        brosVy = 0f
        isBrosGrounded = true
        cameraX = 0f
        coinsCount = 0
        goombas.clear()
        goombas.addAll(listOf(Goomba(140f, 176f), Goomba(220f, 176f), Goomba(360f, 176f), Goomba(480f, 176f)))
        coinBlocks.clear()
        coinBlocks.addAll(listOf(
            CoinBlock(80f, 128f), CoinBlock(100f, 128f, hasCoin = true), CoinBlock(120f, 128f),
            CoinBlock(200f, 112f, hasCoin = true), CoinBlock(220f, 112f, hasCoin = true),
            CoinBlock(300f, 128f, hasCoin = true), CoinBlock(360f, 96f, hasCoin = true)
        ))
    }

    private fun initStarDefender() {
        shipX = 120f
        shipY = 200f
        lasers.clear()
        enemies.clear()
        enemySpawnTimer = 0
    }

    private fun initBrickBreaker() {
        paddleX = 110f
        ballX = 128f
        ballY = 160f
        ballVx = 1.6f
        ballVy = -2.0f
        bricks.clear()
        val rowColors = listOf(cBrickRed, cBrickTan, cCoinGold, cGrassGreen, cAlienCyan)
        for (r in 0 until 5) {
            for (c in 0 until 10) {
                bricks.add(Brick(20 + c * 22, 40 + r * 12, 20, 9, rowColors[r]))
            }
        }
    }

    private fun initMazeRunner() {
        pacX = 128
        pacY = 160
        pacDir = 0
        dots.fill(true)
    }

    private fun init2048() {
        for (r in 0..3) {
            for (c in 0..3) {
                grid2048[r][c] = 0
            }
        }
        spawnTile2048()
        spawnTile2048()
    }

    private fun spawnTile2048() {
        val empty = mutableListOf<Pair<Int, Int>>()
        for (r in 0..3) {
            for (c in 0..3) {
                if (grid2048[r][c] == 0) empty.add(Pair(r, c))
            }
        }
        if (empty.isNotEmpty()) {
            val (r, c) = empty.random()
            grid2048[r][c] = if (Random.nextFloat() < 0.9f) 2 else 4
        }
    }

    private fun initAlterEgo() {
        heroX = 60f
        heroY = 160f
        phantomX = 196f
        phantomY = 160f
        items.clear()
        items.addAll(listOf(Pair(128f, 140f), Pair(60f, 90f), Pair(196f, 90f), Pair(128f, 60f)))
    }

    // --- GAME LOOP & INPUT UPDATE ---
    fun update(buttons: Int, buttonsP2: Int = 0) {
        frameCount++

        // If a real NES ROM Cartridge is loaded into NesConsole, execute actual NES hardware!
        val console = nesConsole
        if (console != null) {
            // Player 1 controller ($4016)
            console.setControllerButton(0, NesConsole.BUTTON_A, (buttons and NesConsole.BUTTON_A) != 0)
            console.setControllerButton(0, NesConsole.BUTTON_B, (buttons and NesConsole.BUTTON_B) != 0)
            console.setControllerButton(0, NesConsole.BUTTON_SELECT, (buttons and NesConsole.BUTTON_SELECT) != 0)
            console.setControllerButton(0, NesConsole.BUTTON_START, (buttons and NesConsole.BUTTON_START) != 0)
            console.setControllerButton(0, NesConsole.BUTTON_UP, (buttons and NesConsole.BUTTON_UP) != 0)
            console.setControllerButton(0, NesConsole.BUTTON_DOWN, (buttons and NesConsole.BUTTON_DOWN) != 0)
            console.setControllerButton(0, NesConsole.BUTTON_LEFT, (buttons and NesConsole.BUTTON_LEFT) != 0)
            console.setControllerButton(0, NesConsole.BUTTON_RIGHT, (buttons and NesConsole.BUTTON_RIGHT) != 0)

            // Player 2 controller ($4017)
            console.setControllerButton(1, NesConsole.BUTTON_A, (buttonsP2 and NesConsole.BUTTON_A) != 0)
            console.setControllerButton(1, NesConsole.BUTTON_B, (buttonsP2 and NesConsole.BUTTON_B) != 0)
            console.setControllerButton(1, NesConsole.BUTTON_SELECT, (buttonsP2 and NesConsole.BUTTON_SELECT) != 0)
            console.setControllerButton(1, NesConsole.BUTTON_START, (buttonsP2 and NesConsole.BUTTON_START) != 0)
            console.setControllerButton(1, NesConsole.BUTTON_UP, (buttonsP2 and NesConsole.BUTTON_UP) != 0)
            console.setControllerButton(1, NesConsole.BUTTON_DOWN, (buttonsP2 and NesConsole.BUTTON_DOWN) != 0)
            console.setControllerButton(1, NesConsole.BUTTON_LEFT, (buttonsP2 and NesConsole.BUTTON_LEFT) != 0)
            console.setControllerButton(1, NesConsole.BUTTON_RIGHT, (buttonsP2 and NesConsole.BUTTON_RIGHT) != 0)

            console.stepFrame()
            System.arraycopy(console.ppu.frameBuffer, 0, frameBuffer, 0, width * height)
            return
        }

        val combinedButtons = buttons or buttonsP2
        if (isGameOver) {
            val pressed = (combinedButtons and (NesConsole.BUTTON_START or NesConsole.BUTTON_A or NesConsole.BUTTON_B)) != 0
            if (pressed || (frameCount % 300 == 0L)) {
                loadGame(currentGameId)
            }
            render()
            return
        }

        // Advance APU frame sequencer and synthesize authentic built-in 8-bit chiptune BGM
        apu.clockFrame()
        updateBgm()

        when (currentGameId) {
            "retro_super_bros" -> updateSuperBros(combinedButtons)
            "space_star_defender" -> updateStarDefender(combinedButtons)
            "brick_breaker_arcade" -> updateBrickBreaker(combinedButtons)
            "retro_maze_runner" -> updateMazeRunner(combinedButtons)
            "cyber_puzzle_2048" -> update2048(combinedButtons)
            "alter_ego_retro" -> updateAlterEgo(combinedButtons)
        }

        render()
    }

    private fun updateSuperBros(buttons: Int) {
        val speed = if ((buttons and NesConsole.BUTTON_B) != 0) 2.2f else 1.4f
        brosVx = 0f
        if ((buttons and NesConsole.BUTTON_LEFT) != 0) brosVx = -speed
        if ((buttons and NesConsole.BUTTON_RIGHT) != 0) brosVx = speed

        // Jump
        if ((buttons and NesConsole.BUTTON_A) != 0 && isBrosGrounded) {
            brosVy = -5.0f
            isBrosGrounded = false
            apu.playJump()
        }

        // Apply physics
        brosVy += 0.28f // Gravity
        brosX += brosVx
        brosY += brosVy

        // Ground collision
        if (brosY >= 176f) {
            brosY = 176f
            brosVy = 0f
            isBrosGrounded = true
        }

        // Camera follow
        if (brosX - cameraX > 140f) cameraX = brosX - 140f
        if (brosX < cameraX + 8f) brosX = cameraX + 8f

        // Check Block Hit
        for (block in coinBlocks) {
            if (!block.hit && brosVy < 0f &&
                brosX + 12 > block.x && brosX < block.x + 16 &&
                brosY >= block.y && brosY <= block.y + 16
            ) {
                block.hit = true
                brosVy = 1.0f
                if (block.hasCoin) {
                    coinsCount++
                    score += 200
                    apu.playCoin()
                } else {
                    score += 50
                    apu.playBump()
                }
            }
        }

        // Update Goombas
        for (g in goombas) {
            if (!g.alive) continue
            g.x += g.vx
            if (g.x < cameraX - 30f) g.vx = 0.5f
            if (g.x > cameraX + 300f) g.vx = -0.5f

            // Collision with Mario
            if (abs(brosX - g.x) < 14f && abs(brosY - g.y) < 14f) {
                if (brosVy > 0.5f) { // Stomp!
                    g.alive = false
                    brosVy = -3.5f
                    score += 100
                    apu.playBump()
                } else { // Hit!
                    lives--
                    apu.playExplosion()
                    if (lives <= 0) {
                        isGameOver = true
                    } else {
                        brosX = cameraX + 20f
                        brosY = 176f
                        brosVy = 0f
                    }
                }
            }
        }
    }

    private fun updateStarDefender(buttons: Int) {
        val speed = 2.4f
        if ((buttons and NesConsole.BUTTON_LEFT) != 0) shipX = (shipX - speed).coerceAtLeast(16f)
        if ((buttons and NesConsole.BUTTON_RIGHT) != 0) shipX = (shipX + speed).coerceAtMost(width - 24f)
        if ((buttons and NesConsole.BUTTON_UP) != 0) shipY = (shipY - speed).coerceAtLeast(30f)
        if ((buttons and NesConsole.BUTTON_DOWN) != 0) shipY = (shipY + speed).coerceAtMost(height - 24f)

        // Fire Laser
        if ((buttons and NesConsole.BUTTON_A) != 0 && frameCount % 9 == 0L) {
            lasers.add(Laser(shipX + 4f, shipY - 4f))
            lasers.add(Laser(shipX + 12f, shipY - 4f))
            apu.playLaser()
        }

        // Update Lasers
        val laserIter = lasers.iterator()
        while (laserIter.hasNext()) {
            val l = laserIter.next()
            l.y += l.vy
            if (l.y < 10) laserIter.remove()
        }

        // Update Stars
        for (s in stars) {
            s.y += s.speed
            if (s.y >= height) {
                s.y = 0f
                s.x = Random.nextInt(width).toFloat()
            }
        }

        // Spawn Enemies
        enemySpawnTimer++
        if (enemySpawnTimer > 35) {
            enemySpawnTimer = 0
            val ex = Random.nextInt(20, width - 40).toFloat()
            val color = if (Random.nextBoolean()) cAlienCyan else cAlienPink
            enemies.add(StarEnemy(ex, 10f, Random.nextFloat() * 1.5f - 0.75f, 1.2f, color))
        }

        // Update Enemies & Laser Collision
        val enemyIter = enemies.iterator()
        while (enemyIter.hasNext()) {
            val e = enemyIter.next()
            e.x += e.vx
            e.y += e.vy
            if (e.x < 10 || e.x > width - 20) e.vx = -e.vx

            // Collision with Lasers
            var enemyDead = false
            val lIter = lasers.iterator()
            while (lIter.hasNext()) {
                val l = lIter.next()
                if (abs(l.x - (e.x + 8)) < 10 && abs(l.y - (e.y + 8)) < 10) {
                    lIter.remove()
                    enemyDead = true
                    score += 150
                    apu.playExplosion()
                    break
                }
            }

            // Collision with Ship
            if (!enemyDead && abs(shipX - e.x) < 14 && abs(shipY - e.y) < 14) {
                enemyDead = true
                lives--
                apu.playExplosion()
                if (lives <= 0) isGameOver = true
            }

            if (enemyDead || e.y > height + 10) {
                enemyIter.remove()
            }
        }
    }

    private fun updateBrickBreaker(buttons: Int) {
        val speed = 3.5f
        if ((buttons and NesConsole.BUTTON_LEFT) != 0) paddleX = (paddleX - speed).coerceAtLeast(10f)
        if ((buttons and NesConsole.BUTTON_RIGHT) != 0) paddleX = (paddleX + speed).coerceAtMost(width - 50f)

        ballX += ballVx
        ballY += ballVy

        // Wall collisions
        if (ballX <= 10f) { ballX = 10f; ballVx = abs(ballVx); apu.playBump() }
        if (ballX >= width - 14f) { ballX = width - 14f; ballVx = -abs(ballVx); apu.playBump() }
        if (ballY <= 24f) { ballY = 24f; ballVy = abs(ballVy); apu.playBump() }

        // Paddle collision
        if (ballY in 204f..214f && ballX + 4f >= paddleX && ballX <= paddleX + 40f) {
            ballVy = -abs(ballVy)
            val hitOffset = ((ballX - (paddleX + 20f)) / 20f).coerceIn(-1.5f, 1.5f)
            ballVx = hitOffset * 2.2f
            apu.playBump()
        }

        // Bottom fell
        if (ballY > height) {
            lives--
            apu.playExplosion()
            if (lives <= 0) {
                isGameOver = true
            } else {
                ballX = paddleX + 20f
                ballY = 180f
                ballVx = 1.6f
                ballVy = -2.0f
            }
        }

        // Brick collision
        for (b in bricks) {
            if (b.active && ballX + 4 >= b.x && ballX <= b.x + b.w && ballY + 4 >= b.y && ballY <= b.y + b.h) {
                b.active = false
                ballVy = -ballVy
                score += 50
                apu.playCoin()
                break
            }
        }
    }

    private fun updateMazeRunner(buttons: Int) {
        val step = 1
        if ((buttons and NesConsole.BUTTON_RIGHT) != 0) { pacDir = 0; pacX = (pacX + step).coerceAtMost(width - 20) }
        if ((buttons and NesConsole.BUTTON_DOWN) != 0) { pacDir = 1; pacY = (pacY + step).coerceAtMost(height - 25) }
        if ((buttons and NesConsole.BUTTON_LEFT) != 0) { pacDir = 2; pacX = (pacX - step).coerceAtLeast(20) }
        if ((buttons and NesConsole.BUTTON_UP) != 0) { pacDir = 3; pacY = (pacY - step).coerceAtLeast(30) }

        pacMouth = ((frameCount / 6) % 2).toInt()

        // Eat dots
        val col = ((pacX - 20) / 11).coerceIn(0, 19)
        val row = ((pacY - 30) / 11).coerceIn(0, 15)
        val index = row * 20 + col
        if (dots[index]) {
            dots[index] = false
            score += 10
            if (frameCount % 4 == 0L) apu.playSelect()
        }

        // Move Ghosts
        for (g in ghosts) {
            val dx = if (pacX > g.x) 0.6f else -0.6f
            val dy = if (pacY > g.y) 0.6f else -0.6f
            g.x += dx
            g.y += dy

            if (abs(pacX - g.x) < 8 && abs(pacY - g.y) < 8) {
                lives--
                apu.playExplosion()
                if (lives <= 0) isGameOver = true else {
                    pacX = 128
                    pacY = 160
                }
            }
        }
    }

    private fun update2048(buttons: Int) {
        val isUp = (buttons and NesConsole.BUTTON_UP) != 0
        val isDown = (buttons and NesConsole.BUTTON_DOWN) != 0
        val isLeft = (buttons and NesConsole.BUTTON_LEFT) != 0
        val isRight = (buttons and NesConsole.BUTTON_RIGHT) != 0

        if (frameCount % 12 == 0L && (isUp || isDown || isLeft || isRight)) {
            var shifted = false
            if (isLeft) shifted = slide2048(0)
            if (isRight) shifted = slide2048(1)
            if (isUp) shifted = slide2048(2)
            if (isDown) shifted = slide2048(3)

            if (shifted) {
                spawnTile2048()
                apu.playBump()
            }
        }
    }

    private fun slide2048(dir: Int): Boolean {
        var changed = false
        // Simplified 2048 slide & merge logic
        for (i in 0..3) {
            val line = IntArray(4)
            for (j in 0..3) {
                line[j] = when (dir) {
                    0 -> grid2048[i][j]
                    1 -> grid2048[i][3 - j]
                    2 -> grid2048[j][i]
                    else -> grid2048[3 - j][i]
                }
            }
            val compact = line.filter { it != 0 }.toMutableList()
            var idx = 0
            while (idx < compact.size - 1) {
                if (compact[idx] == compact[idx + 1]) {
                    compact[idx] *= 2
                    score += compact[idx]
                    compact.removeAt(idx + 1)
                    changed = true
                }
                idx++
            }
            while (compact.size < 4) compact.add(0)
            for (j in 0..3) {
                val newVal = compact[j]
                when (dir) {
                    0 -> { if (grid2048[i][j] != newVal) changed = true; grid2048[i][j] = newVal }
                    1 -> { if (grid2048[i][3 - j] != newVal) changed = true; grid2048[i][3 - j] = newVal }
                    2 -> { if (grid2048[j][i] != newVal) changed = true; grid2048[j][i] = newVal }
                    else -> { if (grid2048[3 - j][i] != newVal) changed = true; grid2048[3 - j][i] = newVal }
                }
            }
        }
        return changed
    }

    private fun updateAlterEgo(buttons: Int) {
        val speed = 1.6f
        if ((buttons and NesConsole.BUTTON_LEFT) != 0) {
            heroX = (heroX - speed).coerceAtLeast(30f)
            phantomX = (phantomX + speed).coerceAtMost(226f)
        }
        if ((buttons and NesConsole.BUTTON_RIGHT) != 0) {
            heroX = (heroX + speed).coerceAtMost(226f)
            phantomX = (phantomX - speed).coerceAtLeast(30f)
        }
        if ((buttons and NesConsole.BUTTON_UP) != 0) {
            heroY = (heroY - speed).coerceAtLeast(40f)
            phantomY = (phantomY - speed).coerceAtLeast(40f)
        }
        if ((buttons and NesConsole.BUTTON_DOWN) != 0) {
            heroY = (heroY + speed).coerceAtMost(190f)
            phantomY = (phantomY + speed).coerceAtMost(190f)
        }

        // Swap positions with A button
        if ((buttons and NesConsole.BUTTON_A) != 0 && frameCount % 15 == 0L) {
            val tx = heroX; val ty = heroY
            heroX = phantomX; heroY = phantomY
            phantomX = tx; phantomY = ty
            apu.playPowerup()
        }

        // Collect items
        val iter = items.iterator()
        while (iter.hasNext()) {
            val item = iter.next()
            if ((abs(heroX - item.first) < 12 && abs(heroY - item.second) < 12) ||
                (abs(phantomX - item.first) < 12 && abs(phantomY - item.second) < 12)
            ) {
                iter.remove()
                score += 250
                apu.playCoin()
            }
        }
    }

    // --- RENDERING ROUTINES ---
    private fun render() {
        when (currentGameId) {
            "retro_super_bros" -> renderSuperBros()
            "space_star_defender" -> renderStarDefender()
            "brick_breaker_arcade" -> renderBrickBreaker()
            "retro_maze_runner" -> renderMazeRunner()
            "cyber_puzzle_2048" -> render2048()
            "alter_ego_retro" -> renderAlterEgo()
        }

        // Draw HUD overlay on top (24px top status bar)
        drawHud()
    }

    private fun drawRect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        val x0 = x.coerceIn(0, width)
        val x1 = (x + w).coerceIn(0, width)
        val y0 = y.coerceIn(0, height)
        val y1 = (y + h).coerceIn(0, height)

        for (py in y0 until y1) {
            val rowOffset = py * width
            for (px in x0 until x1) {
                frameBuffer[rowOffset + px] = color
            }
        }
    }

    private fun renderSuperBros() {
        // Sky
        frameBuffer.fill(cSkyBlue)

        // Hills and Clouds
        drawRect(0, 192, width, 48, cBrickRed) // Ground
        drawRect(0, 192, width, 4, cGrassGreen)

        // Question mark blocks
        for (b in coinBlocks) {
            val screenX = (b.x - cameraX).toInt()
            if (screenX in -20..width) {
                val color = if (b.hit) cBrickRed else cCoinGold
                drawRect(screenX, b.y.toInt(), 16, 16, color)
                drawRect(screenX + 2, b.y.toInt() + 2, 12, 12, if (b.hit) 0xFF6C0600.toInt() else 0xFFF7D8A5.toInt())
            }
        }

        // Pipes
        val pipeScreenX = (170f - cameraX).toInt()
        if (pipeScreenX in -40..width) {
            drawRect(pipeScreenX, 160, 24, 32, cPipeGreen)
            drawRect(pipeScreenX - 2, 154, 28, 6, cPipeGreen)
        }

        // Goombas
        for (g in goombas) {
            if (!g.alive) continue
            val gx = (g.x - cameraX).toInt()
            if (gx in -20..width) {
                drawRect(gx, g.y.toInt(), 14, 14, cBrickTan)
                drawRect(gx + 2, g.y.toInt() + 4, 3, 3, cWhite)
                drawRect(gx + 9, g.y.toInt() + 4, 3, 3, cWhite)
            }
        }

        // Mario Character
        val mx = (brosX - cameraX).toInt()
        val my = brosY.toInt()
        drawRect(mx + 2, my - 2, 10, 5, cBrickRed) // Cap
        drawRect(mx + 3, my + 3, 8, 5, 0xFFF7D8A5.toInt()) // Face
        drawRect(mx + 1, my + 8, 12, 8, cBrickRed) // Shirt
        drawRect(mx + 3, my + 10, 8, 6, cNavy) // Overalls
    }

    private fun renderStarDefender() {
        // Space Background
        frameBuffer.fill(cSpaceBg)

        // Stars
        for (s in stars) {
            val idx = (s.y.toInt() * width) + s.x.toInt()
            if (idx in frameBuffer.indices) {
                frameBuffer[idx] = if (s.speed == 2) cWhite else 0xFF64B0FF.toInt()
            }
        }

        // Lasers
        for (l in lasers) {
            drawRect(l.x.toInt(), l.y.toInt(), 2, 6, cLaserRed)
        }

        // Enemies
        for (e in enemies) {
            drawRect(e.x.toInt() + 2, e.y.toInt(), 12, 10, e.color)
            drawRect(e.x.toInt(), e.y.toInt() + 4, 16, 4, e.color)
        }

        // Player Ship
        val sx = shipX.toInt()
        val sy = shipY.toInt()
        drawRect(sx + 7, sy, 2, 6, cWhite)
        drawRect(sx + 5, sy + 6, 6, 8, cAlienCyan)
        drawRect(sx + 1, sy + 10, 14, 4, cNavy)
        // Thruster flicker
        if (frameCount % 2 == 0L) {
            drawRect(sx + 6, sy + 14, 4, 4, cCoinGold)
        }
    }

    private fun renderBrickBreaker() {
        frameBuffer.fill(cBlack)
        // Border
        drawRect(8, 20, width - 16, 4, 0xFF666666.toInt())
        drawRect(8, 20, 4, height - 20, 0xFF666666.toInt())
        drawRect(width - 12, 20, 4, height - 20, 0xFF666666.toInt())

        // Bricks
        for (b in bricks) {
            if (b.active) {
                drawRect(b.x, b.y, b.w, b.h, b.color)
                drawRect(b.x + 1, b.y + 1, b.w - 2, 2, cWhite)
            }
        }

        // Paddle
        drawRect(paddleX.toInt(), 208, 38, 6, cAlienCyan)
        drawRect(paddleX.toInt() + 2, 209, 34, 2, cWhite)

        // Ball
        drawRect(ballX.toInt(), ballY.toInt(), 5, 5, cWhite)
    }

    private fun renderMazeRunner() {
        frameBuffer.fill(cBlack)
        // Maze Border Walls
        drawRect(16, 26, width - 32, 4, cNavy)
        drawRect(16, 26, 4, height - 40, cNavy)
        drawRect(width - 20, 26, 4, height - 40, cNavy)
        drawRect(16, height - 16, width - 32, 4, cNavy)

        // Inner Obstacles
        drawRect(45, 55, 30, 20, cNavy)
        drawRect(100, 55, 56, 20, cNavy)
        drawRect(180, 55, 30, 20, cNavy)
        drawRect(70, 100, 40, 24, cNavy)
        drawRect(146, 100, 40, 24, cNavy)

        // Dots
        for (r in 0 until 16) {
            for (c in 0 until 20) {
                if (dots[r * 20 + c]) {
                    drawRect(20 + c * 11 + 4, 30 + r * 11 + 4, 2, 2, cYellow)
                }
            }
        }

        // Pac-Man
        drawRect(pacX, pacY, 10, 10, cCoinGold)

        // Ghosts
        for (g in ghosts) {
            drawRect(g.x.toInt(), g.y.toInt(), 10, 10, g.color)
            drawRect(g.x.toInt() + 2, g.y.toInt() + 2, 2, 3, cWhite)
            drawRect(g.x.toInt() + 6, g.y.toInt() + 2, 2, 3, cWhite)
        }
    }

    private fun render2048() {
        frameBuffer.fill(0xFF1E293B.toInt())
        val startX = 48
        val startY = 40
        val tileSize = 36
        val gap = 4

        for (r in 0..3) {
            for (c in 0..3) {
                val tx = startX + c * (tileSize + gap)
                val ty = startY + r * (tileSize + gap)
                val value = grid2048[r][c]
                val color = when (value) {
                    0 -> 0xFF334155.toInt()
                    2 -> 0xFFE2E8F0.toInt()
                    4 -> 0xFFFDE68A.toInt()
                    8 -> 0xFFF59E0B.toInt()
                    16 -> 0xFFEF4444.toInt()
                    32 -> 0xFFEC4899.toInt()
                    64 -> 0xFF8B5CF6.toInt()
                    128 -> 0xFF3B82F6.toInt()
                    256 -> 0xFF10B981.toInt()
                    512 -> 0xFF06B6D4.toInt()
                    1024 -> 0xFFF43F5E.toInt()
                    2048 -> 0xFFFFD700.toInt()
                    else -> 0xFF6366F1.toInt()
                }
                drawRect(tx, ty, tileSize, tileSize, color)
            }
        }
    }

    private fun renderAlterEgo() {
        frameBuffer.fill(0xFF0F172A.toInt())
        // Mirror Center Line
        for (y in 24 until height step 8) {
            drawRect(127, y, 2, 4, 0xFF38BDF8.toInt())
        }

        // Items (Gems)
        for (item in items) {
            drawRect(item.first.toInt(), item.second.toInt(), 8, 8, cCoinGold)
            drawRect(item.first.toInt() + 2, item.second.toInt() + 2, 4, 4, cWhite)
        }

        // Hero (Red)
        drawRect(heroX.toInt(), heroY.toInt(), 12, 14, cLaserRed)
        drawRect(heroX.toInt() + 3, heroY.toInt() + 3, 6, 4, cWhite)

        // Phantom (Blue Mirror)
        drawRect(phantomX.toInt(), phantomY.toInt(), 12, 14, cAlienCyan)
        drawRect(phantomX.toInt() + 3, phantomY.toInt() + 3, 6, 4, cWhite)
    }

    private fun drawHud() {
        // Black top bar
        drawRect(0, 0, width, 20, cBlack)
        // Draw 8-bit text indicator bar (Score, Lives, Stage)
        // Score representation
        val scoreBars = (score / 100).coerceIn(1, 30)
        for (i in 0 until scoreBars) {
            drawRect(10 + i * 4, 8, 2, 6, cCoinGold)
        }
        // Lives dots
        for (l in 0 until lives) {
            drawRect(width - 30 + l * 8, 8, 5, 5, cLaserRed)
        }

        if (isGameOver) {
            // Draw Center GAME OVER Banner
            drawRect(60, 100, 136, 40, cBlack)
            drawRect(62, 102, 132, 36, cBrickRed)
        }
    }

    // Save State serializing
    fun serializeState(): String {
        return "$currentGameId,$score,$lives,$level,$brosX,$brosY,$cameraX,$paddleX,$ballX,$ballY,$pacX,$pacY"
    }

    fun deserializeState(data: String) {
        try {
            val parts = data.split(",")
            if (parts.size >= 12) {
                currentGameId = parts[0]
                score = parts[1].toInt()
                lives = parts[2].toInt()
                level = parts[3].toInt()
                brosX = parts[4].toFloat()
                brosY = parts[5].toFloat()
                cameraX = parts[6].toFloat()
                paddleX = parts[7].toFloat()
                ballX = parts[8].toFloat()
                ballY = parts[9].toFloat()
                pacX = parts[10].toInt()
                pacY = parts[11].toInt()
            }
        } catch (_: Exception) {}
    }

    // --- 8-BIT ORIGINAL SOUND CHIPTUNE SEQUENCER ---
    private var bgmStep = 0
    private var bgmTick = 0

    private fun resetBgm() {
        bgmStep = 0
        bgmTick = 0
    }

    private fun updateBgm() {
        if (!apu.chiptuneEnabled || apu.isMuted) return

        bgmTick++
        val stepInterval = when (currentGameId) {
            "retro_super_bros" -> 8
            "space_star_defender" -> 6
            "brick_breaker_arcade" -> 7
            "retro_maze_runner" -> 9
            else -> 10
        }

        if (bgmTick < stepInterval) return
        bgmTick = 0

        val (melodySeq, harmonySeq, bassSeq) = when (currentGameId) {
            "space_star_defender" -> Triple(BgmDefenderMelody, BgmDefenderHarmony, BgmDefenderBass)
            "brick_breaker_arcade" -> Triple(BgmBreakerMelody, BgmBreakerHarmony, BgmBreakerBass)
            "retro_maze_runner" -> Triple(BgmMazeMelody, BgmMazeHarmony, BgmMazeBass)
            "cyber_puzzle_2048" -> Triple(BgmPuzzleMelody, BgmPuzzleHarmony, BgmPuzzleBass)
            "alter_ego_retro" -> Triple(BgmAlterEgoMelody, BgmAlterEgoHarmony, BgmAlterEgoBass)
            else -> Triple(BgmBrosMelody, BgmBrosHarmony, BgmBrosBass)
        }

        val step = bgmStep
        bgmStep = (bgmStep + 1) % melodySeq.size

        // Pulse 1 Lead Melody
        if (apu.sfxTimerPulse1 == 0 && apu.pulseChannelsEnabled) {
            val note = melodySeq[step % melodySeq.size]
            if (note > 0) {
                apu.writeRegister(0x4015, apu.readRegister(0x4015) or 0x01)
                apu.writeRegister(0x4000, 0x89) // 50% duty, constant volume 9
                apu.writeRegister(0x4002, note and 0xFF)
                apu.writeRegister(0x4003, ((note ushr 8) and 0x07) or 0x08)
            } else {
                apu.writeRegister(0x4000, 0x90) // Mute Pulse 1
            }
        }

        // Pulse 2 Harmony / Counter-Melody
        if (apu.sfxTimerPulse2 == 0 && apu.pulseChannelsEnabled) {
            val note = harmonySeq[step % harmonySeq.size]
            if (note > 0) {
                apu.writeRegister(0x4015, apu.readRegister(0x4015) or 0x02)
                apu.writeRegister(0x4004, 0x47) // 25% duty, constant volume 7
                apu.writeRegister(0x4006, note and 0xFF)
                apu.writeRegister(0x4007, ((note ushr 8) and 0x07) or 0x08)
            } else {
                apu.writeRegister(0x4004, 0x50) // Mute Pulse 2
            }
        }

        // Triangle Walking Bass
        if (apu.sfxTimerTriangle == 0 && apu.triangleChannelEnabled) {
            val note = bassSeq[step % bassSeq.size]
            if (note > 0) {
                apu.writeRegister(0x4015, apu.readRegister(0x4015) or 0x04)
                apu.writeRegister(0x4008, 0x7F) // Linear counter reload
                apu.writeRegister(0x400A, note and 0xFF)
                apu.writeRegister(0x400B, ((note ushr 8) and 0x07) or 0x08)
            } else {
                apu.writeRegister(0x4008, 0x80) // Mute Triangle
            }
        }
    }

    companion object {
        private const val R = 0
        // Standard NES Timer values for notes
        private const val C3 = 854; private const val D3 = 761; private const val E3 = 678; private const val F3 = 640
        private const val G3 = 570; private const val A3 = 507; private const val B3 = 452; private const val C4 = 426
        private const val D4 = 380; private const val E4 = 338; private const val F4 = 319; private const val G4 = 284
        private const val A4 = 253; private const val B4 = 225; private const val C5 = 213; private const val D5 = 190
        private const val E5 = 169; private const val F5 = 159; private const val G5 = 142; private const val A5 = 126
        private const val B5 = 112; private const val C6 = 106
        private const val D6 = 94; private const val E6 = 84

        // Triangle bass timers
        private const val TC2 = 854; private const val TD2 = 761; private const val TE2 = 678; private const val TF2 = 640
        private const val TG2 = 570; private const val TA2 = 507; private const val TB2 = 452
        private const val TC3 = 426; private const val TD3 = 380; private const val TE3 = 338; private const val TF3 = 319
        private const val TG3 = 284; private const val TA3 = 253; private const val TB3 = 225
        private const val TC4 = 213; private const val TD4 = 190; private const val TE4 = 169; private const val TG4 = 142

        // Super Retro Bros
        private val BgmBrosMelody = intArrayOf(
            E5, E5, R, E5, R, C5, E5, R, G5, R, R, R, G4, R, R, R,
            C5, R, R, G4, R, R, E4, R, R, A4, R, B4, R, A4, G4, E5
        )
        private val BgmBrosHarmony = intArrayOf(
            C5, C5, R, C5, R, A4, C5, R, E5, R, R, R, C4, R, R, R,
            E4, R, R, E4, R, R, C4, R, R, F4, R, G4, R, F4, E4, C5
        )
        private val BgmBrosBass = intArrayOf(
            TG3, TG3, R, TG3, R, TF3, TG3, R, TC3, R, R, R, TG2, R, R, R,
            TC3, R, R, TG2, R, R, TE2, R, R, TA2, R, TB2, R, TA2, TG2, TC3
        )

        // Space Star Defender
        private val BgmDefenderMelody = intArrayOf(
            A4, C5, E5, A5, G5, E5, D5, E5, A4, C5, E5, G5, F5, D5, C5, D5
        )
        private val BgmDefenderHarmony = intArrayOf(
            E4, A4, C5, E5, D5, C5, B4, C5, E4, A4, C5, D5, C5, B4, A4, B4
        )
        private val BgmDefenderBass = intArrayOf(
            TA2, TE3, TA3, TE3, TG2, TD3, TG3, TD3, TF2, TC3, TF3, TC3, TE2, TB2, TE3, TB2
        )

        // Brick Breaker Arcade
        private val BgmBreakerMelody = intArrayOf(
            C5, E5, G5, C6, G5, E5, D5, F5, A5, D6, A5, F5, E5, G5, B5, E6
        )
        private val BgmBreakerHarmony = intArrayOf(
            G4, C5, E5, G5, E5, C5, A4, D5, F5, A5, F5, D5, B4, E5, G5, B5
        )
        private val BgmBreakerBass = intArrayOf(
            TC3, TC3, TG3, TG3, TD3, TD3, TA3, TA3, TE3, TE3, TB3, TB3, TC3, TC3, TG3, TG3
        )

        // Retro Maze Runner
        private val BgmMazeMelody = intArrayOf(
            B4, B5, F5, D5, B5, F5, D5, C5, C6, G5, E5, C6, G5, E5, B4, B5
        )
        private val BgmMazeHarmony = intArrayOf(
            D4, D5, A4, F4, D5, A4, F4, E4, E5, B4, G4, E5, B4, G4, D4, D5
        )
        private val BgmMazeBass = intArrayOf(
            TB2, TF3, TB3, TF3, TC3, TG3, TC4, TG3, TB2, TF3, TB3, TF3, TC3, TG3, TC4, TG3
        )

        // Cyber Puzzle 2048
        private val BgmPuzzleMelody = intArrayOf(
            C5, D5, E5, G5, A5, G5, E5, D5, C5, E5, G5, A5, C6, A5, G5, E5
        )
        private val BgmPuzzleHarmony = intArrayOf(
            G4, A4, C5, E5, F5, E5, C5, A4, G4, C5, E5, F5, A5, F5, E5, C5
        )
        private val BgmPuzzleBass = intArrayOf(
            TC3, TE3, TG3, TE3, TF3, TA3, TC4, TA3, TG3, TB3, TD4, TB3, TC3, TE3, TG3, TE3
        )

        // Alter Ego
        private val BgmAlterEgoMelody = intArrayOf(
            E5, G5, B5, A5, G5, E5, D5, E5, C5, E5, G5, A5, G5, E5, D5, C5
        )
        private val BgmAlterEgoHarmony = intArrayOf(
            B4, E5, G5, F5, E5, B4, A4, B4, G4, C5, E5, F5, E5, C5, A4, G4
        )
        private val BgmAlterEgoBass = intArrayOf(
            TE3, TB3, TE4, TB3, TC3, TG3, TC4, TG3, TD3, TA3, TD4, TA3, TE3, TB3, TE4, TB3
        )
    }
}
