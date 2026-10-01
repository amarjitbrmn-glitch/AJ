package com.example.nes.emulator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Sound Filter Settings for the NES APU synthesizer.
 * Provides authentic hardware filtering profiles, vintage CRT TV acoustics,
 * pure digital chiptune, enhanced bass, and nostalgic analog broadcast warmth.
 */
enum class SoundFilterType(
    val id: String,
    val displayName: String,
    val shortName: String,
    val badgeLabel: String,
    val description: String
) {
    AUTHENTIC_NES(
        "authentic_nes",
        "Authentic NES Hardware",
        "NES HW",
        "FILTER: NES HW",
        "Original 14kHz low-pass + 90Hz/440Hz analog RC filter as found on real Famicom / NES motherboard"
    ),
    CRT_TV(
        "crt_tv",
        "CRT TV Speaker",
        "CRT TV",
        "FILTER: CRT TV",
        "Warm 4.8kHz low-pass acoustic filtering replicating 1980s front-facing television speakers"
    ),
    RAW_CHIPTUNE(
        "raw_chiptune",
        "Raw Crisp Chiptune",
        "Crisp",
        "FILTER: CRISP",
        "Direct unfiltered 8-bit digital sound with razor-sharp square transients and maximum brightness"
    ),
    BASS_BOOST(
        "bass_boost",
        "Bass Boost & Heavy",
        "Bass+",
        "FILTER: BASS+",
        "Enriched sub-bass resonance and amplified Triangle bass channel for modern home theater TV speakers"
    ),
    FAMICOM_RF(
        "famicom_rf",
        "Famicom RF Modulator",
        "Lo-Fi",
        "FILTER: LO-FI",
        "Authentic 1983 analog RF broadcast channel 3/4 band-pass curve (~120Hz-3.6kHz) with vintage warmth"
    );

    companion object {
        fun fromId(id: String?): SoundFilterType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: AUTHENTIC_NES
        }
    }
}

/**
 * Ricoh 2A03 Audio Processing Unit (APU) Emulator.
 * Synthesizes two Pulse (Square) channels, one 32-step Triangle channel,
 * one 15-bit Linear Feedback Shift Register Noise channel, and one DMC channel.
 */
class Apu2A03 {
    private var audioTrack: AudioTrack? = null
    private val isRunning = AtomicBoolean(false)
    private var audioThread: Thread? = null

    var isMuted: Boolean = false
    var volume: Float = 0.85f

    // Master toggles for 8-bit APU chiptune channels (Pulse, Triangle, Noise)
    @Volatile var pulseChannelsEnabled: Boolean = true
    @Volatile var triangleChannelEnabled: Boolean = true
    @Volatile var noiseChannelEnabled: Boolean = true

    // Sound Filter Setting Profile
    @Volatile var soundFilterType: SoundFilterType = SoundFilterType.AUTHENTIC_NES

    var chiptuneEnabled: Boolean
        get() = pulseChannelsEnabled && triangleChannelEnabled && noiseChannelEnabled
        set(value) {
            pulseChannelsEnabled = value
            triangleChannelEnabled = value
            noiseChannelEnabled = value
        }

    // Timers indicating that an active sound effect is playing on a channel
    @Volatile var sfxTimerPulse1: Int = 0
    @Volatile var sfxTimerPulse2: Int = 0
    @Volatile var sfxTimerTriangle: Int = 0
    @Volatile var sfxTimerNoise: Int = 0

    // -------------------------------------------------------------
    // Pulse 1 Channel ($4000 - $4003)
    // -------------------------------------------------------------
    @Volatile var pulse1Enabled = false
    @Volatile var pulse1Duty = 0
    @Volatile var pulse1LengthHalt = false
    @Volatile var pulse1ConstantVol = true
    @Volatile var pulse1Volume = 0
    @Volatile var pulse1Timer = 0
    @Volatile var pulse1LengthCounter = 0

    // Pulse 1 Envelope & Sweep
    @Volatile var pulse1Envelope = 15
    private var pulse1EnvelopeCounter = 0
    @Volatile var pulse1SweepEnabled = false
    @Volatile var pulse1SweepPeriod = 0
    @Volatile var pulse1SweepNegate = false
    @Volatile var pulse1SweepShift = 0
    private var pulse1SweepCounter = 0

    // -------------------------------------------------------------
    // Pulse 2 Channel ($4004 - $4007)
    // -------------------------------------------------------------
    @Volatile var pulse2Enabled = false
    @Volatile var pulse2Duty = 0
    @Volatile var pulse2LengthHalt = false
    @Volatile var pulse2ConstantVol = true
    @Volatile var pulse2Volume = 0
    @Volatile var pulse2Timer = 0
    @Volatile var pulse2LengthCounter = 0

    // Pulse 2 Envelope & Sweep
    @Volatile var pulse2Envelope = 15
    private var pulse2EnvelopeCounter = 0
    @Volatile var pulse2SweepEnabled = false
    @Volatile var pulse2SweepPeriod = 0
    @Volatile var pulse2SweepNegate = false
    @Volatile var pulse2SweepShift = 0
    private var pulse2SweepCounter = 0

    // -------------------------------------------------------------
    // Triangle Channel ($4008 - $400B)
    // -------------------------------------------------------------
    @Volatile var triangleEnabled = false
    @Volatile var triangleControlHalt = false
    @Volatile var triangleLinearReload = 0
    @Volatile var triangleTimer = 0
    @Volatile var triangleLengthCounter = 0
    @Volatile var triangleLinearCounter = 0
    @Volatile var triangleReloadFlag = false

    // -------------------------------------------------------------
    // Noise Channel ($400C - $400F)
    // -------------------------------------------------------------
    @Volatile var noiseEnabled = false
    @Volatile var noiseLengthHalt = false
    @Volatile var noiseConstantVol = true
    @Volatile var noiseVolume = 0
    @Volatile var noisePeriod = 4
    @Volatile var noiseMode = false
    @Volatile var noiseLengthCounter = 0
    @Volatile var noiseEnvelope = 15
    private var noiseEnvelopeCounter = 0

    // -------------------------------------------------------------
    // DMC Channel ($4010 - $4013)
    // -------------------------------------------------------------
    @Volatile var dmcEnabled = false
    @Volatile var dmcOutputLevel = 0

    private val sampleRate = 44100
    private val bufferSize = try {
        AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)
    } catch (_: Throwable) {
        2048
    }

    fun start() {
        if (isRunning.getAndSet(true)) return

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            audioTrack?.play()

            audioThread = thread(name = "NES-APU-Hardware-Synth", isDaemon = true) {
                renderAudioLoop()
            }
        } catch (_: Exception) {
            isRunning.set(false)
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            audioThread?.join(300)
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        audioThread = null
    }

    // -------------------------------------------------------------
    // Frame Sequencer (Authentic NES 240 Hz / 120 Hz)
    // -------------------------------------------------------------
    fun clockEnvelopesAndLinear() {
        // Decrement active SFX timers
        if (sfxTimerPulse1 > 0) sfxTimerPulse1--
        if (sfxTimerPulse2 > 0) sfxTimerPulse2--
        if (sfxTimerTriangle > 0) sfxTimerTriangle--
        if (sfxTimerNoise > 0) sfxTimerNoise--

        // Pulse 1 Envelope
        pulse1EnvelopeCounter++
        if (pulse1EnvelopeCounter > pulse1Volume) {
            pulse1EnvelopeCounter = 0
            if (pulse1Envelope > 0) {
                pulse1Envelope--
            } else if (pulse1LengthHalt) {
                pulse1Envelope = 15
            }
        }

        // Pulse 2 Envelope
        pulse2EnvelopeCounter++
        if (pulse2EnvelopeCounter > pulse2Volume) {
            pulse2EnvelopeCounter = 0
            if (pulse2Envelope > 0) {
                pulse2Envelope--
            } else if (pulse2LengthHalt) {
                pulse2Envelope = 15
            }
        }

        // Noise Envelope
        noiseEnvelopeCounter++
        if (noiseEnvelopeCounter > noiseVolume) {
            noiseEnvelopeCounter = 0
            if (noiseEnvelope > 0) {
                noiseEnvelope--
            } else if (noiseLengthHalt) {
                noiseEnvelope = 15
            }
        }

        // Triangle Linear Counter
        if (triangleReloadFlag) {
            triangleLinearCounter = triangleLinearReload
        } else if (triangleLinearCounter > 0) {
            triangleLinearCounter--
        }
        if (!triangleControlHalt) {
            triangleReloadFlag = false
        }
    }

    fun clockLengthCountersAndSweeps() {
        if (!pulse1LengthHalt && pulse1LengthCounter > 0) pulse1LengthCounter--
        if (!pulse2LengthHalt && pulse2LengthCounter > 0) pulse2LengthCounter--
        if (!triangleControlHalt && triangleLengthCounter > 0) triangleLengthCounter--
        if (!noiseLengthHalt && noiseLengthCounter > 0) noiseLengthCounter--

        // Pulse 1 Sweep
        if (pulse1SweepEnabled && pulse1SweepShift > 0 && pulse1Timer >= 8) {
            pulse1SweepCounter++
            if (pulse1SweepCounter > pulse1SweepPeriod) {
                pulse1SweepCounter = 0
                val delta = pulse1Timer ushr pulse1SweepShift
                pulse1Timer = if (pulse1SweepNegate) {
                    (pulse1Timer - delta - 1).coerceAtLeast(0)
                } else {
                    (pulse1Timer + delta).coerceAtMost(0x07FF)
                }
            }
        }

        // Pulse 2 Sweep
        if (pulse2SweepEnabled && pulse2SweepShift > 0 && pulse2Timer >= 8) {
            pulse2SweepCounter++
            if (pulse2SweepCounter > pulse2SweepPeriod) {
                pulse2SweepCounter = 0
                val delta = pulse2Timer ushr pulse2SweepShift
                pulse2Timer = if (pulse2SweepNegate) {
                    (pulse2Timer - delta).coerceAtLeast(0)
                } else {
                    (pulse2Timer + delta).coerceAtMost(0x07FF)
                }
            }
        }
    }

    /**
     * Clocks the APU 4-step sequence (called 4 times per frame by NesConsole / RetroGameEngine).
     */
    fun clockFrameSequence(step: Int) {
        when (step % 4) {
            0 -> clockEnvelopesAndLinear()
            1 -> {
                clockEnvelopesAndLinear()
                clockLengthCountersAndSweeps()
            }
            2 -> clockEnvelopesAndLinear()
            3 -> {
                clockEnvelopesAndLinear()
                clockLengthCountersAndSweeps()
            }
        }
    }

    /**
     * Legacy/fallback single-frame clock (executes full 4-step sequence).
     */
    fun clockFrame() {
        clockFrameSequence(0)
        clockFrameSequence(1)
        clockFrameSequence(2)
        clockFrameSequence(3)
    }

    // -------------------------------------------------------------
    // Hardware Register Write ($4000 - $4017)
    // -------------------------------------------------------------
    fun writeRegister(address: Int, value: Int) {
        val v = value and 0xFF
        when (address) {
            // Pulse 1
            0x4000 -> {
                pulse1Duty = (v ushr 6) and 0x03
                pulse1LengthHalt = (v and 0x20) != 0
                pulse1ConstantVol = (v and 0x10) != 0
                pulse1Volume = v and 0x0F
            }
            0x4001 -> {
                pulse1SweepEnabled = (v and 0x80) != 0
                pulse1SweepPeriod = (v ushr 4) and 0x07
                pulse1SweepNegate = (v and 0x08) != 0
                pulse1SweepShift = v and 0x07
                pulse1SweepCounter = 0
            }
            0x4002 -> {
                pulse1Timer = (pulse1Timer and 0x0700) or v
            }
            0x4003 -> {
                pulse1Timer = ((v and 0x07) shl 8) or (pulse1Timer and 0x00FF)
                if (pulse1Enabled) {
                    pulse1LengthCounter = LENGTH_TABLE[(v ushr 3) and 0x1F]
                }
                pulse1Envelope = 15
                pulse1EnvelopeCounter = 0
            }

            // Pulse 2
            0x4004 -> {
                pulse2Duty = (v ushr 6) and 0x03
                pulse2LengthHalt = (v and 0x20) != 0
                pulse2ConstantVol = (v and 0x10) != 0
                pulse2Volume = v and 0x0F
            }
            0x4005 -> {
                pulse2SweepEnabled = (v and 0x80) != 0
                pulse2SweepPeriod = (v ushr 4) and 0x07
                pulse2SweepNegate = (v and 0x08) != 0
                pulse2SweepShift = v and 0x07
                pulse2SweepCounter = 0
            }
            0x4006 -> {
                pulse2Timer = (pulse2Timer and 0x0700) or v
            }
            0x4007 -> {
                pulse2Timer = ((v and 0x07) shl 8) or (pulse2Timer and 0x00FF)
                if (pulse2Enabled) {
                    pulse2LengthCounter = LENGTH_TABLE[(v ushr 3) and 0x1F]
                }
                pulse2Envelope = 15
                pulse2EnvelopeCounter = 0
            }

            // Triangle
            0x4008 -> {
                triangleControlHalt = (v and 0x80) != 0
                triangleLinearReload = v and 0x7F
            }
            0x400A -> {
                triangleTimer = (triangleTimer and 0x0700) or v
            }
            0x400B -> {
                triangleTimer = ((v and 0x07) shl 8) or (triangleTimer and 0x00FF)
                if (triangleEnabled) {
                    triangleLengthCounter = LENGTH_TABLE[(v ushr 3) and 0x1F]
                }
                triangleReloadFlag = true
            }

            // Noise
            0x400C -> {
                noiseLengthHalt = (v and 0x20) != 0
                noiseConstantVol = (v and 0x10) != 0
                noiseVolume = v and 0x0F
            }
            0x400E -> {
                noiseMode = (v and 0x80) != 0
                noisePeriod = NOISE_PERIOD_TABLE[v and 0x0F]
            }
            0x400F -> {
                if (noiseEnabled) {
                    noiseLengthCounter = LENGTH_TABLE[(v ushr 3) and 0x1F]
                }
                noiseEnvelope = 15
                noiseEnvelopeCounter = 0
            }

            // DMC
            0x4010 -> {}
            0x4011 -> {
                dmcOutputLevel = v and 0x7F
            }

            // Status ($4015)
            0x4015 -> {
                pulse1Enabled = (v and 0x01) != 0
                if (!pulse1Enabled) pulse1LengthCounter = 0

                pulse2Enabled = (v and 0x02) != 0
                if (!pulse2Enabled) pulse2LengthCounter = 0

                triangleEnabled = (v and 0x04) != 0
                if (!triangleEnabled) triangleLengthCounter = 0

                noiseEnabled = (v and 0x08) != 0
                if (!noiseEnabled) noiseLengthCounter = 0

                dmcEnabled = (v and 0x10) != 0
            }

            // Frame counter ($4017)
            0x4017 -> {
                if ((v and 0x80) != 0) {
                    clockFrame()
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Hardware Register Read ($4015)
    // -------------------------------------------------------------
    fun readRegister(address: Int): Int {
        if (address == 0x4015) {
            var status = 0
            if (pulse1LengthCounter > 0) status = status or 0x01
            if (pulse2LengthCounter > 0) status = status or 0x02
            if (triangleLengthCounter > 0) status = status or 0x04
            if (noiseLengthCounter > 0) status = status or 0x08
            return status
        }
        return 0
    }

    // -------------------------------------------------------------
    // Real-Time Audio Synthesis & Sound Filter DSP Engine
    // -------------------------------------------------------------
    private fun renderAudioLoop() {
        val buffer = ShortArray(512)

        var p1Phase = 0.0
        var p2Phase = 0.0
        var triPhase = 0.0
        var noisePhase = 0.0
        var noiseShiftRegister = 1

        // High-pass filter states (DC-Blocker)
        var hpfPrevInput = 0f
        var hpfPrevOutput = 0f

        // Low-pass filter state (Anti-Aliasing & Tone Sculpting)
        var lpfOutput = 0f

        // Sub-bass resonance filter state (Bass Boost profile)
        var bassSubFilter = 0f

        while (isRunning.get()) {
            if (isMuted || volume <= 0f) {
                buffer.fill(0)
                audioTrack?.write(buffer, 0, buffer.size)
                continue
            }

            val filter = soundFilterType

            for (i in buffer.indices) {
                // 1. Pulse 1 Channel
                var p1Out = 0f
                if (pulseChannelsEnabled && pulse1Enabled && pulse1LengthCounter > 0 && pulse1Timer >= 8) {
                    val freq = 1789773.0 / (16.0 * (pulse1Timer + 1))
                    val phaseStep = freq / sampleRate
                    p1Phase = (p1Phase + phaseStep) % 1.0
                    val dutyIdx = (p1Phase * 8.0).toInt().coerceIn(0, 7)
                    if (DUTY_TABLE[pulse1Duty][dutyIdx] == 1) {
                        val vol = if (pulse1ConstantVol) pulse1Volume else pulse1Envelope
                        p1Out = vol.toFloat()
                    }
                }

                // 2. Pulse 2 Channel
                var p2Out = 0f
                if (pulseChannelsEnabled && pulse2Enabled && pulse2LengthCounter > 0 && pulse2Timer >= 8) {
                    val freq = 1789773.0 / (16.0 * (pulse2Timer + 1))
                    val phaseStep = freq / sampleRate
                    p2Phase = (p2Phase + phaseStep) % 1.0
                    val dutyIdx = (p2Phase * 8.0).toInt().coerceIn(0, 7)
                    if (DUTY_TABLE[pulse2Duty][dutyIdx] == 1) {
                        val vol = if (pulse2ConstantVol) pulse2Volume else pulse2Envelope
                        p2Out = vol.toFloat()
                    }
                }

                // 3. Triangle Channel
                var triOut = 0f
                if (triangleChannelEnabled && triangleEnabled && triangleLengthCounter > 0 && triangleLinearCounter > 0 && triangleTimer >= 2) {
                    val freq = 1789773.0 / (32.0 * (triangleTimer + 1))
                    if (freq < 16000.0) { // Filter out ultrasonic screech
                        val phaseStep = freq / sampleRate
                        triPhase = (triPhase + phaseStep) % 1.0
                        val stepIdx = (triPhase * 32.0).toInt().coerceIn(0, 31)
                        triOut = TRIANGLE_TABLE[stepIdx].toFloat()
                    }
                }

                // 4. Noise Channel (Authentic NES CPU clock divider)
                var noiseOut = 0f
                if (noiseChannelEnabled && noiseEnabled && noiseLengthCounter > 0) {
                    val shiftRate = 1789773.0 / noisePeriod.toDouble()
                    val phaseStep = shiftRate / sampleRate
                    noisePhase += phaseStep
                    while (noisePhase >= 1.0) {
                        noisePhase -= 1.0
                        val shift = if (noiseMode) 6 else 1
                        val b1 = noiseShiftRegister and 1
                        val b2 = (noiseShiftRegister ushr shift) and 1
                        noiseShiftRegister = (noiseShiftRegister ushr 1) or ((b1 xor b2) shl 14)
                    }
                    if ((noiseShiftRegister and 1) == 0) {
                        val vol = if (noiseConstantVol) noiseVolume else noiseEnvelope
                        noiseOut = vol.toFloat()
                    }
                }

                // 5. DMC Channel (Direct DAC level from $4011)
                val dmcOut = dmcOutputLevel.toFloat()

                // NES Audio Non-Linear Mixer
                val pulseSum = p1Out + p2Out
                val pulseMix = if (pulseSum > 0f) 95.88f / ((8128f / pulseSum) + 100f) else 0f

                val triMultiplier = if (filter == SoundFilterType.BASS_BOOST) 2.2f else 1.0f
                val tndSum = ((triOut * triMultiplier) / 8227f) + (noiseOut / 12241f) + (dmcOut / 22638f)
                val tndMix = if (tndSum > 0f) 159.79f / ((1f / tndSum) + 100f) else 0f

                val rawMix = pulseMix + tndMix

                // -------------------------------------------------------------
                // Dynamic Sound Filter DSP Profiles
                // -------------------------------------------------------------
                // Step 1: High-Pass Filter (DC-Blocker tailored to filter profile)
                val hpfPole = when (filter) {
                    SoundFilterType.AUTHENTIC_NES -> 0.988f // ~90Hz
                    SoundFilterType.CRT_TV -> 0.982f        // ~120Hz
                    SoundFilterType.FAMICOM_RF -> 0.975f    // ~180Hz (vintage analog lo-fi)
                    SoundFilterType.BASS_BOOST -> 0.996f    // ~30Hz (deep sub-bass retention)
                    SoundFilterType.RAW_CHIPTUNE -> 0.997f  // ~20Hz (ultra-flat)
                }
                val hpfOut = rawMix - hpfPrevInput + hpfPole * hpfPrevOutput
                hpfPrevInput = rawMix
                hpfPrevOutput = hpfOut

                // Step 2: Low-Pass Filter & Tone Sculpting
                val filteredSample: Float = when (filter) {
                    SoundFilterType.AUTHENTIC_NES -> {
                        // Original 14 kHz low-pass analog circuit smoothing
                        lpfOutput += 0.86f * (hpfOut - lpfOutput)
                        lpfOutput
                    }
                    SoundFilterType.CRT_TV -> {
                        // Warm 4.8 kHz vintage TV speaker low-pass filter
                        lpfOutput += 0.50f * (hpfOut - lpfOutput)
                        lpfOutput * 1.15f
                    }
                    SoundFilterType.FAMICOM_RF -> {
                        // Lo-Fi 3.6 kHz band-pass analog broadcast sound
                        lpfOutput += 0.38f * (hpfOut - lpfOutput)
                        lpfOutput * 1.25f
                    }
                    SoundFilterType.BASS_BOOST -> {
                        // Punchy low-end shelf boost + smooth 9 kHz top
                        lpfOutput += 0.75f * (hpfOut - lpfOutput)
                        bassSubFilter += 0.08f * (lpfOutput - bassSubFilter)
                        lpfOutput + 0.60f * bassSubFilter
                    }
                    SoundFilterType.RAW_CHIPTUNE -> {
                        // Unfiltered direct 8-bit digital square waves
                        hpfOut
                    }
                }

                // Step 3: Master gain and 16-bit PCM conversion with soft-limiting
                val gain = when (filter) {
                    SoundFilterType.RAW_CHIPTUNE -> 2.6f
                    SoundFilterType.BASS_BOOST -> 2.5f
                    SoundFilterType.CRT_TV -> 3.0f
                    SoundFilterType.FAMICOM_RF -> 3.1f
                    SoundFilterType.AUTHENTIC_NES -> 2.85f
                }
                val sampleValue = (filteredSample * volume * gain * 32767f).coerceIn(-32767f, 32767f)
                buffer[i] = sampleValue.toInt().toShort()
            }

            audioTrack?.write(buffer, 0, buffer.size)
        }
    }

    // -------------------------------------------------------------
    // Built-in Game Sound Effects & Chiptune Triggers
    // -------------------------------------------------------------
    fun playJump() {
        sfxTimerPulse1 = 18
        writeRegister(0x4015, 0x0F)
        writeRegister(0x4000, 0x9F)
        writeRegister(0x4001, 0x82) // Pitch sweep up
        writeRegister(0x4002, 0x70)
        writeRegister(0x4003, 0x08)
    }

    fun playCoin() {
        sfxTimerPulse2 = 24
        writeRegister(0x4015, 0x0F)
        writeRegister(0x4004, 0x8F)
        writeRegister(0x4005, 0x00)
        writeRegister(0x4006, 0x40)
        writeRegister(0x4007, 0x08)
    }

    fun playLaser() {
        sfxTimerNoise = 12
        writeRegister(0x4015, 0x0F)
        writeRegister(0x400C, 0x1F)
        writeRegister(0x400E, 0x04)
        writeRegister(0x400F, 0x08)
    }

    fun playExplosion() {
        sfxTimerNoise = 26
        writeRegister(0x4015, 0x0F)
        writeRegister(0x400C, 0x1F)
        writeRegister(0x400E, 0x0E)
        writeRegister(0x400F, 0x18)
    }

    fun playPowerup() {
        sfxTimerPulse1 = 28
        writeRegister(0x4015, 0x0F)
        writeRegister(0x4000, 0x8E)
        writeRegister(0x4001, 0x84)
        writeRegister(0x4002, 0x30)
        writeRegister(0x4003, 0x10)
    }

    fun playBump() {
        sfxTimerTriangle = 14
        writeRegister(0x4015, 0x0F)
        writeRegister(0x4008, 0x7F)
        writeRegister(0x400A, 0x90)
        writeRegister(0x400B, 0x08)
    }

    fun playSelect() {
        sfxTimerPulse1 = 12
        writeRegister(0x4015, 0x0F)
        writeRegister(0x4000, 0x88)
        writeRegister(0x4002, 0xA0)
        writeRegister(0x4003, 0x08)
    }

    fun playGameOver() {
        sfxTimerPulse1 = 45
        sfxTimerPulse2 = 45
        writeRegister(0x4015, 0x0F)
        writeRegister(0x4000, 0x8F)
        writeRegister(0x4001, 0x8A) // Sweep down
        writeRegister(0x4002, 0xD0)
        writeRegister(0x4003, 0x18)
    }

    companion object {
        val LENGTH_TABLE = intArrayOf(
            10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14,
            12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30
        )

        val DUTY_TABLE = arrayOf(
            intArrayOf(0, 1, 0, 0, 0, 0, 0, 0), // 12.5%
            intArrayOf(0, 1, 1, 0, 0, 0, 0, 0), // 25%
            intArrayOf(0, 1, 1, 1, 1, 0, 0, 0), // 50%
            intArrayOf(0, 0, 1, 1, 1, 1, 1, 1)  // 75% (inverted 25%)
        )

        val TRIANGLE_TABLE = intArrayOf(
            15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0,
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
        )

        val NOISE_PERIOD_TABLE = intArrayOf(
            4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068
        )
    }
}
