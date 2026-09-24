package com.example.domain

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

enum class SoundscapeType(val id: String, val title: String, val icon: String, val description: String) {
    RAIN("rain", "Gentle Rain", "🌧️", "Soothing rainfall and distant droplets"),
    OCEAN("ocean", "Ocean Waves", "🌊", "Rolling rhythmic coastal surf swell"),
    FIRE("fire", "Cozy Campfire", "🔥", "Warm glowing logs and gentle crackling pops"),
    BREEZE("breeze", "Forest Breeze", "🍃", "Airy whispering wind through mountain pines"),
    BROWN_NOISE("brown", "Deep Space Noise", "🪐", "Low-frequency rumble for sleep & deep focus"),
    CAFE("cafe", "Acoustic Cafe", "☕", "Soft ambient room warmth & binaural hum")
}

data class SoundscapeState(
    val type: SoundscapeType,
    val enabled: Boolean = false,
    val volume: Float = 0.5f
)

class AmbientSoundscapeEngine private constructor() {
    companion object {
        @Volatile
        private var instance: AmbientSoundscapeEngine? = null

        fun getInstance(): AmbientSoundscapeEngine {
            return instance ?: synchronized(this) {
                instance ?: AmbientSoundscapeEngine().also { instance = it }
            }
        }
    }

    private val sampleRate = 22050
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096)

    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _soundscapes = MutableStateFlow(
        SoundscapeType.values().associateWith { type ->
            SoundscapeState(type = type, enabled = false, volume = 0.5f)
        }
    )
    val soundscapes: StateFlow<Map<SoundscapeType, SoundscapeState>> = _soundscapes.asStateFlow()

    private val _isAnyActive = MutableStateFlow(false)
    val isAnyActive: StateFlow<Boolean> = _isAnyActive.asStateFlow()

    fun toggleSoundscape(type: SoundscapeType) {
        val current = _soundscapes.value[type] ?: SoundscapeState(type)
        setSoundscape(type, !current.enabled, current.volume)
    }

    fun setSoundscape(type: SoundscapeType, enabled: Boolean, volume: Float) {
        val updated = _soundscapes.value.toMutableMap()
        updated[type] = SoundscapeState(type, enabled, volume.coerceIn(0f, 1f))
        _soundscapes.value = updated
        checkPlaybackState()
    }

    fun applyPreset(name: String) {
        val updated = _soundscapes.value.toMutableMap()
        // Reset all first
        SoundscapeType.values().forEach {
            updated[it] = SoundscapeState(it, enabled = false, volume = 0.5f)
        }

        when (name) {
            "Deep Sleep" -> {
                updated[SoundscapeType.RAIN] = SoundscapeState(SoundscapeType.RAIN, true, 0.6f)
                updated[SoundscapeType.BROWN_NOISE] = SoundscapeState(SoundscapeType.BROWN_NOISE, true, 0.4f)
            }
            "Cozy Evening" -> {
                updated[SoundscapeType.FIRE] = SoundscapeState(SoundscapeType.FIRE, true, 0.7f)
                updated[SoundscapeType.RAIN] = SoundscapeState(SoundscapeType.RAIN, true, 0.4f)
            }
            "Study & Focus" -> {
                updated[SoundscapeType.CAFE] = SoundscapeState(SoundscapeType.CAFE, true, 0.5f)
                updated[SoundscapeType.BREEZE] = SoundscapeState(SoundscapeType.BREEZE, true, 0.3f)
            }
            "Coastal Peace" -> {
                updated[SoundscapeType.OCEAN] = SoundscapeState(SoundscapeType.OCEAN, true, 0.7f)
                updated[SoundscapeType.BREEZE] = SoundscapeState(SoundscapeType.BREEZE, true, 0.35f)
            }
        }
        _soundscapes.value = updated
        checkPlaybackState()
    }

    fun stopAll() {
        val updated = _soundscapes.value.toMutableMap()
        SoundscapeType.values().forEach {
            updated[it] = SoundscapeState(it, enabled = false, volume = updated[it]?.volume ?: 0.5f)
        }
        _soundscapes.value = updated
        checkPlaybackState()
    }

    private fun checkPlaybackState() {
        val anyActive = _soundscapes.value.values.any { it.enabled && it.volume > 0.01f }
        _isAnyActive.value = anyActive

        if (anyActive) {
            startSynthesizer()
        } else {
            stopSynthesizer()
        }
    }

    private fun startSynthesizer() {
        if (synthJob?.isActive == true) return

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        synthJob = scope.launch {
            val random = Random()
            val shortBuffer = ShortArray(1024) // Stereo: 512 frames (L, R pairs)

            // State variables for procedural filters
            var brownNoiseL = 0.0
            var brownNoiseR = 0.0
            var pinkB0 = 0.0
            var pinkB1 = 0.0
            var pinkB2 = 0.0
            var waveLfoPhase = 0.0
            var breezeLfoPhase = 0.0
            var cafeLfoPhase = 0.0

            while (isActive) {
                val stateMap = _soundscapes.value
                val rainState = stateMap[SoundscapeType.RAIN]
                val oceanState = stateMap[SoundscapeType.OCEAN]
                val fireState = stateMap[SoundscapeType.FIRE]
                val breezeState = stateMap[SoundscapeType.BREEZE]
                val brownState = stateMap[SoundscapeType.BROWN_NOISE]
                val cafeState = stateMap[SoundscapeType.CAFE]

                val rainVol = if (rainState?.enabled == true) rainState.volume else 0f
                val oceanVol = if (oceanState?.enabled == true) oceanState.volume else 0f
                val fireVol = if (fireState?.enabled == true) fireState.volume else 0f
                val breezeVol = if (breezeState?.enabled == true) breezeState.volume else 0f
                val brownVol = if (brownState?.enabled == true) brownState.volume else 0f
                val cafeVol = if (cafeState?.enabled == true) cafeState.volume else 0f

                var frameIdx = 0
                while (frameIdx < shortBuffer.size) {
                    var outL = 0.0
                    var outR = 0.0

                    val whiteL = (random.nextDouble() * 2.0 - 1.0)
                    val whiteR = (random.nextDouble() * 2.0 - 1.0)

                    // 1. Brown noise generator (integrated white noise)
                    brownNoiseL = (brownNoiseL + (0.02 * whiteL)) / 1.02
                    brownNoiseR = (brownNoiseR + (0.02 * whiteR)) / 1.02

                    // 2. Pink noise approximation (3-pole IIR)
                    pinkB0 = 0.99765 * pinkB0 + whiteL * 0.0990460
                    pinkB1 = 0.96300 * pinkB1 + whiteL * 0.2965164
                    pinkB2 = 0.57000 * pinkB2 + whiteL * 1.0526913
                    val pinkL = (pinkB0 + pinkB1 + pinkB2 + whiteL * 0.1848) * 0.15

                    // --- RAIN ---
                    if (rainVol > 0f) {
                        var drop = 0.0
                        // Intermittent droplet impulse
                        if (random.nextDouble() < 0.002) {
                            drop = (random.nextDouble() * 0.6 + 0.4) * (if (random.nextBoolean()) 1.0 else -1.0)
                        }
                        val rainSample = (pinkL * 0.45 + drop * 0.35 + whiteL * 0.1) * rainVol
                        outL += rainSample
                        outR += (pinkL * 0.42 + drop * 0.38 + whiteR * 0.1) * rainVol
                    }

                    // --- OCEAN WAVES ---
                    if (oceanVol > 0f) {
                        waveLfoPhase += (2.0 * PI * 0.08) / sampleRate // 0.08 Hz rhythmic swell
                        if (waveLfoPhase > 2.0 * PI) waveLfoPhase -= 2.0 * PI
                        val swell = (sin(waveLfoPhase) * 0.5 + 0.5).coerceIn(0.0, 1.0)
                        val swellPow = swell * swell * swell // natural wave rise
                        val oceanSampleL = (brownNoiseL * 3.5 * swellPow + pinkL * 0.3 * swell) * oceanVol
                        val oceanSampleR = (brownNoiseR * 3.5 * swellPow + pinkL * 0.3 * swell) * oceanVol
                        outL += oceanSampleL
                        outR += oceanSampleR
                    }

                    // --- CAMPFIRE ---
                    if (fireVol > 0f) {
                        var crackle = 0.0
                        if (random.nextDouble() < 0.0015) {
                            crackle = (random.nextDouble() * 1.5 - 0.75) // loud snap
                        } else if (random.nextDouble() < 0.005) {
                            crackle = (random.nextDouble() * 0.4 - 0.2) // quiet hiss pop
                        }
                        val fireBase = (brownNoiseL * 1.2 + crackle) * fireVol
                        outL += fireBase
                        outR += (brownNoiseR * 1.2 + crackle * 0.9) * fireVol
                    }

                    // --- FOREST BREEZE ---
                    if (breezeVol > 0f) {
                        breezeLfoPhase += (2.0 * PI * 0.04) / sampleRate // Slow 0.04 Hz gusting
                        if (breezeLfoPhase > 2.0 * PI) breezeLfoPhase -= 2.0 * PI
                        val gust = (sin(breezeLfoPhase) * 0.4 + 0.6)
                        val breezeSample = (pinkL * 0.6 * gust + brownNoiseL * 0.8 * gust) * breezeVol
                        outL += breezeSample
                        outR += (pinkL * 0.58 * gust + brownNoiseR * 0.82 * gust) * breezeVol
                    }

                    // --- BROWN NOISE ---
                    if (brownVol > 0f) {
                        outL += (brownNoiseL * 4.0) * brownVol
                        outR += (brownNoiseR * 4.0) * brownVol
                    }

                    // --- CAFE HUM ---
                    if (cafeVol > 0f) {
                        cafeLfoPhase += (2.0 * PI * 0.15) / sampleRate
                        if (cafeLfoPhase > 2.0 * PI) cafeLfoPhase -= 2.0 * PI
                        val cafeMod = (sin(cafeLfoPhase) * 0.2 + 0.8)
                        val cafeSampleL = (brownNoiseL * 2.0 * cafeMod + pinkL * 0.25) * cafeVol
                        val cafeSampleR = (brownNoiseR * 2.0 * cafeMod + pinkL * 0.22) * cafeVol
                        outL += cafeSampleL
                        outR += cafeSampleR
                    }

                    // Soft clip limiter to prevent harsh clipping
                    val scaledL = (outL * 18000.0).coerceIn(-32000.0, 32000.0).toInt().toShort()
                    val scaledR = (outR * 18000.0).coerceIn(-32000.0, 32000.0).toInt().toShort()

                    shortBuffer[frameIdx++] = scaledL
                    shortBuffer[frameIdx++] = scaledR
                }

                try {
                    audioTrack?.write(shortBuffer, 0, shortBuffer.size)
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    private fun stopSynthesizer() {
        synthJob?.cancel()
        synthJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null
    }

    fun release() {
        stopSynthesizer()
        scope.cancel()
    }
}
