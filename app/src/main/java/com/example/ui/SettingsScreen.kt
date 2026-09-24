package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showEarShieldDialog by remember { mutableStateOf(false) }
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val isLoudnessEnabled by viewModel.loudnessEnhancerEnabled.collectAsState()
    val includeShortAudio by viewModel.includeShortAudio.collectAsState()
    val earFatigueEnabled by viewModel.playerManager.earFatigueShieldEnabled.collectAsState()
    val nightGuardEnabled by viewModel.playerManager.nightGuardEnabled.collectAsState()
    val smartRewindSec by viewModel.playerManager.smartRewindSeconds.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) }
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                SettingsCategory("Hearing Health & Ear Care")
                SettingsItem(
                    icon = Icons.Default.Hearing,
                    title = "Ear Fatigue & Sound Dose Meter",
                    subtitle = "Monitor continuous acoustic exposure & safe limits"
                ) {
                    showEarShieldDialog = true
                }
                SettingsSwitchItem(
                    icon = Icons.Default.Shield,
                    title = "Auto Ear-Fatigue Shield",
                    subtitle = "Rolls off harsh treble (>3.5kHz) after 45m continuous use",
                    checked = earFatigueEnabled,
                    onCheckedChange = { viewModel.playerManager.setEarFatigueShieldEnabled(it) }
                )
                SettingsSwitchItem(
                    icon = Icons.Default.Nightlight,
                    title = "Anti-Jump-Scare & Night Guard",
                    subtitle = "Micro-fade on track transitions & volume cap 10PM - 7AM",
                    checked = nightGuardEnabled,
                    onCheckedChange = { viewModel.playerManager.setNightGuardEnabled(it) }
                )
                SettingsItem(
                    icon = Icons.Default.Replay,
                    title = "Smart Context Rewind",
                    subtitle = if (smartRewindSec > 0) "Auto-rewind ${smartRewindSec}s when resuming after interruption" else "Disabled"
                ) {
                    showEarShieldDialog = true
                }
            }

            item {
                SettingsCategory("Playback")
                SettingsItem(
                    icon = Icons.Default.Equalizer,
                    title = "Equalizer",
                    subtitle = "Adjust audio frequencies & bass"
                ) {
                    showEqualizerDialog = true
                }
                SettingsSwitchItem(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Volume normalization",
                    subtitle = "Maintain consistent volume gain",
                    checked = isLoudnessEnabled,
                    onCheckedChange = { viewModel.setLoudnessEnhancerEnabled(it) }
                )
            }
            
            item {
                SettingsCategory("Library")
                SettingsItem(
                    icon = Icons.Default.Sync,
                    title = "Scan for media",
                    subtitle = "Manually refresh media library"
                ) {
                    viewModel.triggerSync()
                }
                SettingsSwitchItem(
                    icon = Icons.Default.Folder,
                    title = "Include short audio",
                    subtitle = "Show audio files shorter than 30s",
                    checked = includeShortAudio,
                    onCheckedChange = { viewModel.setIncludeShortAudio(it) }
                )
            }
            
            item {
                SettingsCategory("Appearance")
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "Theme Color",
                    subtitle = "Customize app accent color"
                ) {
                    showThemeDialog = true
                }
                SettingsSwitchItem(
                    icon = Icons.Default.DarkMode,
                    title = "Dark Mode",
                    subtitle = "Use dark theme",
                    checked = isDarkTheme,
                    onCheckedChange = { viewModel.setDarkTheme(it) }
                )
            }

            item {
                val context = androidx.compose.ui.platform.LocalContext.current
                var isOptimizing by remember { mutableStateOf(false) }

                SettingsCategory("Maintenance & Speed")
                SettingsItem(
                    icon = Icons.Default.CleaningServices,
                    title = if (isOptimizing) "Optimizing storage..." else "Clean Cache & Optimize Database",
                    subtitle = "Maintains top speed over months of use & frees storage"
                ) {
                    if (!isOptimizing) {
                        isOptimizing = true
                        viewModel.optimizeStorageAndCache { message ->
                            isOptimizing = false
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            
            item {
                SettingsCategory("About")
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "1.0.0 (High Performance)"
                ) {}
            }
        }
        
        if (showThemeDialog) {
            ThemeDialog(viewModel = viewModel, onDismiss = { showThemeDialog = false })
        }
        if (showEqualizerDialog) {
            EqualizerDialog(viewModel = viewModel, onDismiss = { showEqualizerDialog = false })
        }
        if (showEarShieldDialog) {
            EarShieldDialog(viewModel = viewModel, onDismiss = { showEarShieldDialog = false })
        }
    }
}

@Composable
fun SettingsCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    title: String, 
    subtitle: String, 
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
