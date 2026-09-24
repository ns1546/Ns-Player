package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.domain.AudioRepository
import com.example.domain.PlayerManager
import com.example.ui.MainViewModel
import com.example.ui.NSPlayerApp
import com.example.ui.theme.NSPlayerTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {
    private lateinit var appDb: AppDatabase
    private lateinit var audioRepo: AudioRepository
    private lateinit var playerManager: PlayerManager
    
    private lateinit var sessionToken: SessionToken
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    override fun onStart() {
        super.onStart()
        sessionToken = SessionToken(this, ComponentName(this, com.example.domain.PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
    }

    override fun onStop() {
        MediaController.releaseFuture(controllerFuture)
        super.onStop()
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appDb = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "nsplayer-db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
        audioRepo = AudioRepository(applicationContext)
        playerManager = PlayerManager.getInstance(applicationContext)

        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(audioRepo, playerManager, appDb.playlistDao(), applicationContext) as T
            }
        }

        setContent {
            val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
            val accentColor by viewModel.accentColor.collectAsState()
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()

            NSPlayerTheme(darkTheme = isDarkTheme, accentColor = accentColor) {
                val permissionsList = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsList.add(Manifest.permission.READ_MEDIA_AUDIO)
                    permissionsList.add(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                permissionsList.add(Manifest.permission.RECORD_AUDIO)
                permissionsList.add(Manifest.permission.MODIFY_AUDIO_SETTINGS)
                
                val permissionState = rememberMultiplePermissionsState(permissionsList)
                LaunchedEffect(Unit) {
                    if (!permissionState.allPermissionsGranted) {
                        permissionState.launchMultiplePermissionRequest()
                    }
                }
                
                val canStart = permissionState.permissions.any { 
                    (it.permission == Manifest.permission.READ_MEDIA_AUDIO || it.permission == Manifest.permission.READ_EXTERNAL_STORAGE) && it.status.isGranted
                }
                
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (canStart) {
                        NSPlayerApp(factory = factory)
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Audio Permission Required",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "NSPlayer needs access to your audio files to discover and play songs stored on your device.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { permissionState.launchMultiplePermissionRequest() }
                                ) {
                                    Text("Grant Permission")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
