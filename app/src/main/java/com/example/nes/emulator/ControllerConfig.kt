package com.example.nes.emulator

import android.content.Context
import android.content.SharedPreferences
import android.view.InputDevice
import android.view.KeyEvent

enum class ControllerDeviceType(val displayName: String) {
    AUTO("Auto-Detect"),
    TV_REMOTE("TV Remote"),
    GAMEPAD_1("Gamepad / Controller 1"),
    GAMEPAD_2("Gamepad / Controller 2"),
    KEYBOARD_WASD("Keyboard (WASD + J/K)"),
    KEYBOARD_ARROWS("Keyboard (Arrow Keys + N/M)")
}

enum class NesButtonTarget(val label: String, val description: String) {
    BUTTON_A("NES Button A", "Primary action / Jump / Select"),
    BUTTON_B("NES Button B", "Secondary action / Attack / Run"),
    TURBO_A("Turbo Button A", "Rapid-fire Button A"),
    TURBO_B("Turbo Button B", "Rapid-fire Button B"),
    DPAD_UP("D-Pad Up", "Move up / Climb / Aim"),
    DPAD_DOWN("D-Pad Down", "Duck / Crouch / Move down"),
    DPAD_LEFT("D-Pad Left", "Move left"),
    DPAD_RIGHT("D-Pad Right", "Move right"),
    START("NES Start", "Start game / Unpause"),
    SELECT("NES Select", "Select option / Item switch"),
    PAUSE_MENU("In-Game Pause", "Open Pause & Save Menu")
}

data class GamepadRemapConfig(
    var btnA: Int = KeyEvent.KEYCODE_BUTTON_A,
    var btnB: Int = KeyEvent.KEYCODE_BUTTON_B,
    var turboA: Int = KeyEvent.KEYCODE_BUTTON_X,
    var turboB: Int = KeyEvent.KEYCODE_BUTTON_Y,
    var dpadUp: Int = KeyEvent.KEYCODE_DPAD_UP,
    var dpadDown: Int = KeyEvent.KEYCODE_DPAD_DOWN,
    var dpadLeft: Int = KeyEvent.KEYCODE_DPAD_LEFT,
    var dpadRight: Int = KeyEvent.KEYCODE_DPAD_RIGHT,
    var start: Int = KeyEvent.KEYCODE_BUTTON_START,
    var select: Int = KeyEvent.KEYCODE_BUTTON_SELECT,
    var pauseMenu: Int = KeyEvent.KEYCODE_BUTTON_L1
) {
    fun getKeyCodeForTarget(target: NesButtonTarget): Int {
        return when (target) {
            NesButtonTarget.BUTTON_A -> btnA
            NesButtonTarget.BUTTON_B -> btnB
            NesButtonTarget.TURBO_A -> turboA
            NesButtonTarget.TURBO_B -> turboB
            NesButtonTarget.DPAD_UP -> dpadUp
            NesButtonTarget.DPAD_DOWN -> dpadDown
            NesButtonTarget.DPAD_LEFT -> dpadLeft
            NesButtonTarget.DPAD_RIGHT -> dpadRight
            NesButtonTarget.START -> start
            NesButtonTarget.SELECT -> select
            NesButtonTarget.PAUSE_MENU -> pauseMenu
        }
    }

    fun setKeyCodeForTarget(target: NesButtonTarget, keyCode: Int) {
        when (target) {
            NesButtonTarget.BUTTON_A -> btnA = keyCode
            NesButtonTarget.BUTTON_B -> btnB = keyCode
            NesButtonTarget.TURBO_A -> turboA = keyCode
            NesButtonTarget.TURBO_B -> turboB = keyCode
            NesButtonTarget.DPAD_UP -> dpadUp = keyCode
            NesButtonTarget.DPAD_DOWN -> dpadDown = keyCode
            NesButtonTarget.DPAD_LEFT -> dpadLeft = keyCode
            NesButtonTarget.DPAD_RIGHT -> dpadRight = keyCode
            NesButtonTarget.START -> start = keyCode
            NesButtonTarget.SELECT -> select = keyCode
            NesButtonTarget.PAUSE_MENU -> pauseMenu = keyCode
        }
    }
}

data class PlayerAssignment(
    var player1Type: ControllerDeviceType = ControllerDeviceType.AUTO,
    var player2Type: ControllerDeviceType = ControllerDeviceType.KEYBOARD_ARROWS,
    var isMultiplayerEnabled: Boolean = true
)

object ControllerManager {
    var config = PlayerAssignment()
    var gamepadRemap = GamepadRemapConfig()

    // Listener for active remapping screen
    var activeRemapTarget: NesButtonTarget? = null
    var onRemapCaptured: ((NesButtonTarget, Int) -> Unit)? = null

    // Keep track of detected gamepads by deviceId
    private val connectedGamepadIds = LinkedHashSet<Int>()

    // Current bitmasks for Player 1 and Player 2
    var buttonsP1: Int = 0
    var buttonsP2: Int = 0

    private const val PREFS_NAME = "nes_controller_prefs"

    fun reset() {
        buttonsP1 = 0
        buttonsP2 = 0
    }

    fun registerInputDevice(deviceId: Int, source: Int) {
        val isGamepad = (source and InputDevice.SOURCE_GAMEPAD) != 0 || (source and InputDevice.SOURCE_JOYSTICK) != 0
        if (isGamepad && deviceId > 0) {
            connectedGamepadIds.add(deviceId)
        }
    }

    fun getConnectedGamepadsCount(): Int {
        val devices = InputDevice.getDeviceIds()
        var count = 0
        for (id in devices) {
            val dev = InputDevice.getDevice(id)
            if (dev != null && !dev.isVirtual) {
                val sources = dev.sources
                if ((sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                ) {
                    count++
                }
            }
        }
        return count.coerceAtLeast(connectedGamepadIds.size)
    }

    fun loadMapping(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        gamepadRemap.btnA = prefs.getInt("btnA", KeyEvent.KEYCODE_BUTTON_A)
        gamepadRemap.btnB = prefs.getInt("btnB", KeyEvent.KEYCODE_BUTTON_B)
        gamepadRemap.turboA = prefs.getInt("turboA", KeyEvent.KEYCODE_BUTTON_X)
        gamepadRemap.turboB = prefs.getInt("turboB", KeyEvent.KEYCODE_BUTTON_Y)
        gamepadRemap.dpadUp = prefs.getInt("dpadUp", KeyEvent.KEYCODE_DPAD_UP)
        gamepadRemap.dpadDown = prefs.getInt("dpadDown", KeyEvent.KEYCODE_DPAD_DOWN)
        gamepadRemap.dpadLeft = prefs.getInt("dpadLeft", KeyEvent.KEYCODE_DPAD_LEFT)
        gamepadRemap.dpadRight = prefs.getInt("dpadRight", KeyEvent.KEYCODE_DPAD_RIGHT)
        gamepadRemap.start = prefs.getInt("start", KeyEvent.KEYCODE_BUTTON_START)
        gamepadRemap.select = prefs.getInt("select", KeyEvent.KEYCODE_BUTTON_SELECT)
        gamepadRemap.pauseMenu = prefs.getInt("pauseMenu", KeyEvent.KEYCODE_BUTTON_L1)
    }

    fun saveMapping(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("btnA", gamepadRemap.btnA)
            putInt("btnB", gamepadRemap.btnB)
            putInt("turboA", gamepadRemap.turboA)
            putInt("turboB", gamepadRemap.turboB)
            putInt("dpadUp", gamepadRemap.dpadUp)
            putInt("dpadDown", gamepadRemap.dpadDown)
            putInt("dpadLeft", gamepadRemap.dpadLeft)
            putInt("dpadRight", gamepadRemap.dpadRight)
            putInt("start", gamepadRemap.start)
            putInt("select", gamepadRemap.select)
            putInt("pauseMenu", gamepadRemap.pauseMenu)
            apply()
        }
    }

    fun applyStandardPreset(context: Context? = null) {
        gamepadRemap = GamepadRemapConfig(
            btnA = KeyEvent.KEYCODE_BUTTON_A,
            btnB = KeyEvent.KEYCODE_BUTTON_B,
            turboA = KeyEvent.KEYCODE_BUTTON_X,
            turboB = KeyEvent.KEYCODE_BUTTON_Y,
            dpadUp = KeyEvent.KEYCODE_DPAD_UP,
            dpadDown = KeyEvent.KEYCODE_DPAD_DOWN,
            dpadLeft = KeyEvent.KEYCODE_DPAD_LEFT,
            dpadRight = KeyEvent.KEYCODE_DPAD_RIGHT,
            start = KeyEvent.KEYCODE_BUTTON_START,
            select = KeyEvent.KEYCODE_BUTTON_SELECT,
            pauseMenu = KeyEvent.KEYCODE_BUTTON_L1
        )
        context?.let { saveMapping(it) }
    }

    fun applyNintendoPreset(context: Context? = null) {
        // Nintendo layout: B is bottom (Button A physical on Xbox), A is right (Button B physical on Xbox)
        gamepadRemap = GamepadRemapConfig(
            btnA = KeyEvent.KEYCODE_BUTTON_B,
            btnB = KeyEvent.KEYCODE_BUTTON_A,
            turboA = KeyEvent.KEYCODE_BUTTON_Y,
            turboB = KeyEvent.KEYCODE_BUTTON_X,
            dpadUp = KeyEvent.KEYCODE_DPAD_UP,
            dpadDown = KeyEvent.KEYCODE_DPAD_DOWN,
            dpadLeft = KeyEvent.KEYCODE_DPAD_LEFT,
            dpadRight = KeyEvent.KEYCODE_DPAD_RIGHT,
            start = KeyEvent.KEYCODE_BUTTON_START,
            select = KeyEvent.KEYCODE_BUTTON_SELECT,
            pauseMenu = KeyEvent.KEYCODE_BUTTON_L1
        )
        context?.let { saveMapping(it) }
    }

    fun applyPlayStationPreset(context: Context? = null) {
        // Cross=A, Square=B, Circle=Turbo A, Triangle=Turbo B, R1=Menu
        gamepadRemap = GamepadRemapConfig(
            btnA = KeyEvent.KEYCODE_BUTTON_A, // Cross
            btnB = KeyEvent.KEYCODE_BUTTON_X, // Square
            turboA = KeyEvent.KEYCODE_BUTTON_B, // Circle
            turboB = KeyEvent.KEYCODE_BUTTON_Y, // Triangle
            dpadUp = KeyEvent.KEYCODE_DPAD_UP,
            dpadDown = KeyEvent.KEYCODE_DPAD_DOWN,
            dpadLeft = KeyEvent.KEYCODE_DPAD_LEFT,
            dpadRight = KeyEvent.KEYCODE_DPAD_RIGHT,
            start = KeyEvent.KEYCODE_BUTTON_START,
            select = KeyEvent.KEYCODE_BUTTON_SELECT,
            pauseMenu = KeyEvent.KEYCODE_BUTTON_R1
        )
        context?.let { saveMapping(it) }
    }

    fun getKeyName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> "Button A (Cross)"
            KeyEvent.KEYCODE_BUTTON_B -> "Button B (Circle)"
            KeyEvent.KEYCODE_BUTTON_C -> "Button C"
            KeyEvent.KEYCODE_BUTTON_X -> "Button X (Square)"
            KeyEvent.KEYCODE_BUTTON_Y -> "Button Y (Triangle)"
            KeyEvent.KEYCODE_BUTTON_Z -> "Button Z"
            KeyEvent.KEYCODE_BUTTON_L1 -> "Bumper L1"
            KeyEvent.KEYCODE_BUTTON_R1 -> "Bumper R1"
            KeyEvent.KEYCODE_BUTTON_L2 -> "Trigger L2"
            KeyEvent.KEYCODE_BUTTON_R2 -> "Trigger R2"
            KeyEvent.KEYCODE_BUTTON_THUMBL -> "Thumbstick Left (L3)"
            KeyEvent.KEYCODE_BUTTON_THUMBR -> "Thumbstick Right (R3)"
            KeyEvent.KEYCODE_BUTTON_START -> "Start Button"
            KeyEvent.KEYCODE_BUTTON_SELECT -> "Select / Back Button"
            KeyEvent.KEYCODE_BUTTON_MODE -> "Home / Guide Button"
            KeyEvent.KEYCODE_DPAD_UP -> "D-Pad Up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "D-Pad Down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "D-Pad Left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "D-Pad Right"
            KeyEvent.KEYCODE_DPAD_CENTER -> "D-Pad Center"
            KeyEvent.KEYCODE_ENTER -> "Enter"
            KeyEvent.KEYCODE_SPACE -> "Space"
            KeyEvent.KEYCODE_MENU -> "Menu Button"
            KeyEvent.KEYCODE_BACK -> "Back Button"
            else -> KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "")
        }
    }

    /**
     * Determines whether a KeyEvent belongs to Player 1 or Player 2,
     * updates the respective button mask, and returns true if consumed.
     */
    fun handleKeyEvent(event: KeyEvent, onMenuTriggered: (Boolean) -> Unit): Boolean {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val keyCode = event.keyCode
        val deviceId = event.deviceId
        val source = event.source

        registerInputDevice(deviceId, source)

        // Check if remap listener is actively capturing
        val target = activeRemapTarget
        if (target != null && isDown) {
            // Ignore Back if user wants to cancel
            if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_ESCAPE) {
                gamepadRemap.setKeyCodeForTarget(target, keyCode)
                onRemapCaptured?.invoke(target, keyCode)
                activeRemapTarget = null
                return true
            } else {
                activeRemapTarget = null
                return true
            }
        }

        // Custom Gamepad Pause Menu trigger or default Menu keys
        if (keyCode == gamepadRemap.pauseMenu || keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            onMenuTriggered(isDown)
            return true
        }

        // Determine if this is Player 2 keyboard input
        val isP2Keyboard = isPlayer2KeyboardKey(keyCode)
        val isP1Keyboard = isPlayer1KeyboardKey(keyCode)

        // Determine device classification
        val isGamepadDevice = (source and InputDevice.SOURCE_GAMEPAD) != 0 ||
                (source and InputDevice.SOURCE_JOYSTICK) != 0 ||
                KeyEvent.isGamepadButton(keyCode)

        // Check if multiple gamepads are plugged in
        val isSecondGamepad = isGamepadDevice && connectedGamepadIds.size >= 2 &&
                connectedGamepadIds.toList().indexOf(deviceId) >= 1

        val targetPlayer: Int = when {
            !config.isMultiplayerEnabled -> 1
            isP2Keyboard -> 2
            isP1Keyboard -> 1
            isSecondGamepad || config.player2Type == ControllerDeviceType.GAMEPAD_1 && isGamepadDevice -> 2
            config.player1Type == ControllerDeviceType.TV_REMOTE && isGamepadDevice -> 2
            config.player2Type == ControllerDeviceType.TV_REMOTE && isTvRemoteKey(event) -> 2
            else -> 1
        }

        // Map to NES Button
        val nesButton = mapKeyToNesButton(keyCode, targetPlayer) ?: return false

        if (targetPlayer == 1) {
            buttonsP1 = if (isDown) buttonsP1 or nesButton else buttonsP1 and nesButton.inv()
        } else {
            buttonsP2 = if (isDown) buttonsP2 or nesButton else buttonsP2 and nesButton.inv()
        }

        return true
    }

    private fun isTvRemoteKey(event: KeyEvent): Boolean {
        val dev = event.device
        if (dev != null && (dev.keyboardType == InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC)) {
            return true
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_PROG_YELLOW,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> true
            else -> false
        }
    }

    private fun isPlayer1KeyboardKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I -> true
            else -> false
        }
    }

    private fun isPlayer2KeyboardKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_NUMPAD_8, KeyEvent.KEYCODE_NUMPAD_2, KeyEvent.KEYCODE_NUMPAD_4, KeyEvent.KEYCODE_NUMPAD_6,
            KeyEvent.KEYCODE_NUMPAD_1, KeyEvent.KEYCODE_NUMPAD_3, KeyEvent.KEYCODE_NUMPAD_0, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P -> true
            else -> false
        }
    }

    private fun mapKeyToNesButton(keyCode: Int, player: Int): Int? {
        if (player == 2) {
            return when (keyCode) {
                // Arrow keys or Numpad navigation for Player 2
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_NUMPAD_8 -> NesConsole.BUTTON_UP
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_NUMPAD_2 -> NesConsole.BUTTON_DOWN
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_NUMPAD_4 -> NesConsole.BUTTON_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_NUMPAD_6 -> NesConsole.BUTTON_RIGHT

                // Action buttons for Player 2 (M/N or Numpad 2/1 or Controller B/A)
                KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_NUMPAD_3 -> NesConsole.BUTTON_A
                KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_NUMPAD_1 -> NesConsole.BUTTON_B
                KeyEvent.KEYCODE_O -> NesConsole.BUTTON_A // Turbo A
                KeyEvent.KEYCODE_P -> NesConsole.BUTTON_B // Turbo B

                // Gamepad 2 uses the configured gamepad remapping
                gamepadRemap.btnA -> NesConsole.BUTTON_A
                gamepadRemap.btnB -> NesConsole.BUTTON_B
                gamepadRemap.turboA -> NesConsole.BUTTON_A
                gamepadRemap.turboB -> NesConsole.BUTTON_B
                gamepadRemap.dpadUp -> NesConsole.BUTTON_UP
                gamepadRemap.dpadDown -> NesConsole.BUTTON_DOWN
                gamepadRemap.dpadLeft -> NesConsole.BUTTON_LEFT
                gamepadRemap.dpadRight -> NesConsole.BUTTON_RIGHT
                gamepadRemap.start -> NesConsole.BUTTON_START
                gamepadRemap.select -> NesConsole.BUTTON_SELECT

                // Start & Select for Player 2
                KeyEvent.KEYCODE_NUMPAD_ENTER -> NesConsole.BUTTON_START
                KeyEvent.KEYCODE_NUMPAD_0, KeyEvent.KEYCODE_SHIFT_RIGHT -> NesConsole.BUTTON_SELECT

                else -> null
            }
        }

        // Player 1 mapping: Check custom Gamepad mapping first!
        when (keyCode) {
            gamepadRemap.btnA -> return NesConsole.BUTTON_A
            gamepadRemap.btnB -> return NesConsole.BUTTON_B
            gamepadRemap.turboA -> return NesConsole.BUTTON_A
            gamepadRemap.turboB -> return NesConsole.BUTTON_B
            gamepadRemap.dpadUp -> return NesConsole.BUTTON_UP
            gamepadRemap.dpadDown -> return NesConsole.BUTTON_DOWN
            gamepadRemap.dpadLeft -> return NesConsole.BUTTON_LEFT
            gamepadRemap.dpadRight -> return NesConsole.BUTTON_RIGHT
            gamepadRemap.start -> return NesConsole.BUTTON_START
            gamepadRemap.select -> return NesConsole.BUTTON_SELECT
        }

        // Keyboard & TV Remote Fallbacks for Player 1
        return when (keyCode) {
            // Directional: Arrow keys, WASD, TV D-Pad
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> NesConsole.BUTTON_UP
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> NesConsole.BUTTON_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> NesConsole.BUTTON_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> NesConsole.BUTTON_RIGHT

            // Action Buttons A and B
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_X -> NesConsole.BUTTON_A

            KeyEvent.KEYCODE_J,
            KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_BACK -> NesConsole.BUTTON_B

            // Turbo
            KeyEvent.KEYCODE_I -> NesConsole.BUTTON_A
            KeyEvent.KEYCODE_U -> NesConsole.BUTTON_B

            // Start & Select
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> NesConsole.BUTTON_START

            KeyEvent.KEYCODE_TAB,
            KeyEvent.KEYCODE_SHIFT_LEFT -> NesConsole.BUTTON_SELECT

            else -> null
        }
    }
}
