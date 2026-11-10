package com.kadvii.vault.feature.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.kadvii.vault.domain.model.VaultFile
import com.kadvii.vault.domain.usecase.DecryptVaultFileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun SecureVideoPlayerScreen(
    file: VaultFile,
    decryptVaultFileUseCase: DecryptVaultFileUseCase,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var tempDecryptedFile by remember { mutableStateOf<File?>(null) }
    var isPreparing by remember { mutableStateOf(true) }

    LaunchedEffect(file.id) {
        withContext(Dispatchers.IO) {
            try {
                // Decrypt into a short-lived ephemeral cache file
                val cacheDir = File(context.cacheDir, "ephemeral_video")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val tempFile = File.createTempFile("vault_stream_", ".tmp", cacheDir)
                tempFile.deleteOnExit()

                decryptVaultFileUseCase.getStream(file).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                tempDecryptedFile = tempFile

                withContext(Dispatchers.Main) {
                    val player = ExoPlayer.Builder(context).build().apply {
                        val mediaItem = MediaItem.fromUri(Uri.fromFile(tempFile))
                        setMediaItem(mediaItem)
                        prepare()
                        playWhenReady = true
                    }
                    exoPlayer = player
                    isPreparing = false
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isPreparing = false
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
            // Securely wipe ephemeral playback file
            tempDecryptedFile?.let { temp ->
                if (temp.exists()) {
                    try {
                        temp.delete()
                    } catch (_: Exception) { }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = file.fileName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isPreparing) {
                CircularProgressIndicator(color = Color.White)
            } else if (exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "Unable to play video",
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

