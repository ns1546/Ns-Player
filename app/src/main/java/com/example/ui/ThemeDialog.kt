package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ThemePrimary

val accentColors = listOf(
    Color(0xFFD0BCFF), // Original Purple
    Color(0xFFF2B8B5), // Pink/Red
    Color(0xFF81DEEA), // Cyan
    Color(0xFFC3E88D), // Light Green
    Color(0xFFFDB924), // Yellow
    Color(0xFFFF9800), // Orange
    Color(0xFFE91E63), // Magenta
    Color(0xFF8E24AA)  // Deep Purple
)

@Composable
fun ThemeDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val currentColor by viewModel.accentColor.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Theme Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Dark Mode", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { viewModel.setDarkTheme(it) }
                    )
                }
                
                Text("Select Accent Color", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(accentColors) { color ->
                        val isSelected = currentColor == color
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    viewModel.setAccentColor(color)
                                }
                                .then(
                                    if (isSelected) {
                                        Modifier.border(4.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
