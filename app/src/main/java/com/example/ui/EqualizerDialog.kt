package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EqualizerDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val equalizer = viewModel.playerManager.equalizer
    val bassBoost = viewModel.playerManager.bassBoost
    val loudnessEnhancer = viewModel.playerManager.loudnessEnhancer
    
    // Custom presets (assuming a standard 5-band EQ)
    val presets = mapOf(
        "Normal" to listOf(0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to listOf(1f, 0.5f, 0f, 0f, 0f),
        "Acoustic" to listOf(0.5f, 0f, 0f, 0.2f, 0.5f),
        "Pop" to listOf(-0.2f, 0.4f, 0.5f, 0.2f, -0.2f)
    )
    
    // State to hold current band levels so sliders update when preset is applied
    val numBands = equalizer?.numberOfBands?.toInt() ?: 0
    val bandLevels = remember(equalizer) {
        mutableStateListOf<Float>().apply {
            if (equalizer != null) {
                for (i in 0 until numBands) {
                    add(equalizer.getBandLevel(i.toShort()).toFloat())
                }
            }
        }
    }

    var currentBassBoost by remember { mutableFloatStateOf(bassBoost?.roundedStrength?.toFloat() ?: 0f) }
    var currentLoudness by remember { mutableFloatStateOf(loudnessEnhancer?.targetGain?.toFloat() ?: 0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio Effects") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (equalizer != null && equalizer.enabled && numBands > 0) {
                    val minEqLevel = equalizer.bandLevelRange[0]
                    val maxEqLevel = equalizer.bandLevelRange[1]
                    val rangeSpan = maxEqLevel - minEqLevel

                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(presets.keys.toList()) { presetName ->
                            SuggestionChip(
                                onClick = {
                                    val presetFactors = presets[presetName] ?: presets["Normal"]!!
                                    for (i in 0 until numBands) {
                                        val factor = if (i < presetFactors.size) presetFactors[i] else 0f
                                        val newLevel = (maxEqLevel * factor).coerceIn(minEqLevel.toFloat(), maxEqLevel.toFloat())
                                        
                                        bandLevels[i] = newLevel
                                        equalizer.setBandLevel(i.toShort(), newLevel.toInt().toShort())
                                    }
                                },
                                label = { Text(presetName) }
                            )
                        }
                    }
                    
                    for (i in 0 until numBands) {
                        val bandIndex = i.toShort()
                        val freq = equalizer.getCenterFreq(bandIndex) / 1000
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${freq}Hz", modifier = Modifier.weight(0.2f), style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = bandLevels[i],
                                onValueChange = { newValue ->
                                    bandLevels[i] = newValue
                                    equalizer.setBandLevel(bandIndex, newValue.toInt().toShort())
                                },
                                valueRange = minEqLevel.toFloat()..maxEqLevel.toFloat(),
                                modifier = Modifier.weight(0.8f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (bassBoost != null && bassBoost.strengthSupported) {
                        Text("Bass Boost", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = currentBassBoost,
                            onValueChange = {
                                currentBassBoost = it
                                bassBoost.setStrength(it.toInt().toShort())
                            },
                            valueRange = 0f..1000f
                        )
                    }

                    if (loudnessEnhancer != null) {
                        Text("Loudness Enhancer (Gain)", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = currentLoudness,
                            onValueChange = {
                                currentLoudness = it
                                loudnessEnhancer.setTargetGain(it.toInt())
                            },
                            valueRange = 0f..2000f
                        )
                    }

                } else {
                    Text("Equalizer not available or not active.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
