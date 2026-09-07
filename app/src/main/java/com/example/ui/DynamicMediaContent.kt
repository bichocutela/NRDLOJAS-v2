package com.example.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun DynamicImageBlock(url: String, title: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        AsyncImage(
            model = normalizeRemoteMediaUrl(url),
            contentDescription = title,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 520.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun DynamicVideoBlock(url: String, title: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val youtubeId = remember(url) { extractYoutubeVideoId(url) }
    val isYoutube = youtubeId != null
    var isLoading by remember(url) { mutableStateOf(true) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var retryKey by remember(url) { mutableIntStateOf(0) }

    val exoPlayer = remember(url, isYoutube, retryKey) {
        if (!isYoutube && url.isNotBlank()) {
            runCatching {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(normalizeRemoteMediaUrl(url)))
                    prepare()
                    playWhenReady = false
                }
            }.getOrElse {
                error = "Não foi possível iniciar o vídeo."
                null
            }
        } else null
    }

    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) onDispose { }
        else {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isLoading = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                    if (playbackState == Player.STATE_READY) error = null
                }

                override fun onPlayerError(errorValue: androidx.media3.common.PlaybackException) {
                    isLoading = false
                    error = "Não foi possível reproduzir este vídeo."
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                exoPlayer.stop()
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                when {
                    error != null -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(error.orEmpty(), color = Color.White)
                        Button(onClick = {
                            error = null
                            isLoading = true
                            retryKey++
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Tentar novamente")
                        }
                    }

                    isYoutube && youtubeId != null -> {
                        val playerView = remember(youtubeId, retryKey) {
                            YouTubePlayerView(context).apply {
                                lifecycleOwner.lifecycle.addObserver(this)
                                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                                    override fun onReady(youTubePlayer: YouTubePlayer) {
                                        isLoading = false
                                        error = null
                                        youTubePlayer.cueVideo(youtubeId, 0f)
                                    }

                                    override fun onStateChange(
                                        youTubePlayer: YouTubePlayer,
                                        state: PlayerConstants.PlayerState
                                    ) {
                                        isLoading = state == PlayerConstants.PlayerState.BUFFERING
                                    }

                                    override fun onError(
                                        youTubePlayer: YouTubePlayer,
                                        errorValue: PlayerConstants.PlayerError
                                    ) {
                                        isLoading = false
                                        error = "O vídeo do YouTube não pôde ser reproduzido aqui."
                                    }
                                })
                            }
                        }
                        DisposableEffect(playerView, lifecycleOwner) {
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(playerView)
                                playerView.release()
                            }
                        }
                        AndroidView(
                            factory = { playerView },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    exoPlayer != null -> AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            controllerAutoShow = true
                        } },
                        update = { it.player = exoPlayer },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isLoading && error == null) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

@Composable
fun DynamicAudioBlock(url: String, title: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isPlaying by remember(url) { mutableStateOf(false) }
    var isBuffering by remember(url) { mutableStateOf(true) }
    var position by remember(url) { mutableLongStateOf(0L) }
    var duration by remember(url) { mutableLongStateOf(0L) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var retryKey by remember(url) { mutableIntStateOf(0) }

    val player = remember(url, retryKey) {
        runCatching {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(normalizeRemoteMediaUrl(url)))
                prepare()
            }
        }.getOrElse {
            error = "Não foi possível iniciar o áudio."
            null
        }
    }

    DisposableEffect(player) {
        if (player == null) onDispose { }
        else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                    if (playbackState == Player.STATE_READY) {
                        duration = player.duration.coerceAtLeast(0L)
                        error = null
                    }
                }

                override fun onPlayerError(errorValue: androidx.media3.common.PlaybackException) {
                    isBuffering = false
                    error = "Não foi possível reproduzir este áudio."
                }
            }
            player.addListener(listener)
            onDispose {
                player.stop()
                player.removeListener(listener)
                player.release()
            }
        }
    }

    LaunchedEffect(player, isPlaying) {
        while (player != null && isPlaying) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(500L)
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (error != null) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = {
                    error = null
                    isBuffering = true
                    position = 0L
                    duration = 0L
                    retryKey++
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Tentar novamente")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = {
                            player?.let { if (it.isPlaying) it.pause() else it.play() }
                        },
                        enabled = player != null && !isBuffering
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproduzir"
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Slider(
                            value = if (duration > 0L) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                            onValueChange = { value -> position = value.toLong() },
                            onValueChangeFinished = { player?.seekTo(position) },
                            valueRange = 0f..(duration.coerceAtLeast(1L).toFloat())
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatMediaTime(position), style = MaterialTheme.typography.bodySmall)
                            Text(formatMediaTime(duration), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DynamicPdfBlock(url: String, title: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var loading by remember(url) { mutableStateOf(true) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var file by remember(url) { mutableStateOf<File?>(null) }
    var retryKey by remember(url) { mutableIntStateOf(0) }

    LaunchedEffect(url, retryKey) {
        loading = true
        error = null
        file = withContext(Dispatchers.IO) {
            runCatching {
                loadValidatedPdfIntoCache(context.cacheDir, context, url)
            }.getOrElse {
                error = "Não foi possível carregar este PDF. Verifique o link e tente novamente."
                null
            }
        }
        loading = false
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(620.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    loading -> CircularProgressIndicator()
                    error != null -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = {
                            file = null
                            retryKey++
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Tentar novamente")
                        }
                    }
                    file != null -> DynamicPdfRenderer(file = file!!)
                }
            }
        }
    }
}

private fun loadValidatedPdfIntoCache(cacheDir: File, context: android.content.Context, rawUrl: String): File {
    val normalizedUrl = normalizeRemoteMediaUrl(rawUrl)
    val cached = File(cacheDir, "nrd_dynamic_${normalizedUrl.hashCode()}.pdf")

    if (cached.exists() && isValidPdf(cached)) return cached
    if (cached.exists()) cached.delete()

    val temporary = File(cacheDir, "${cached.name}.part")
    if (temporary.exists()) temporary.delete()

    try {
        if (normalizedUrl.startsWith("http", ignoreCase = true)) {
            val connection = URL(normalizedUrl).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 20_000
                connection.readTimeout = 60_000
                connection.setRequestProperty("Accept", "application/pdf,*/*")
                connection.connect()
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                connection.inputStream.use { input ->
                    FileOutputStream(temporary).use { output -> input.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }
        } else {
            context.contentResolver.openInputStream(android.net.Uri.parse(normalizedUrl))?.use { input ->
                FileOutputStream(temporary).use { output -> input.copyTo(output) }
            } ?: error("Arquivo não encontrado")
        }

        if (!isValidPdf(temporary)) error("Arquivo recebido não é um PDF válido")
        if (!temporary.renameTo(cached)) {
            temporary.copyTo(cached, overwrite = true)
            temporary.delete()
        }
        return cached
    } catch (error: Throwable) {
        temporary.delete()
        cached.takeIf { it.exists() && !isValidPdf(it) }?.delete()
        throw error
    }
}

private fun isValidPdf(file: File): Boolean {
    if (!file.exists() || file.length() < 5L) return false
    val headerValid = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(5)
            input.read(header) == 5 && String(header, Charsets.US_ASCII) == "%PDF-"
        }
    }.getOrDefault(false)
    if (!headerValid) return false

    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount > 0 }
        }
    }.getOrDefault(false)
}

@Composable
private fun DynamicPdfRenderer(file: File) {
    var renderer by remember(file) { mutableStateOf<PdfRenderer?>(null) }
    var descriptor by remember(file) { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember(file) { mutableIntStateOf(0) }
    var openError by remember(file) { mutableStateOf(false) }

    DisposableEffect(file) {
        runCatching {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor!!)
            pageCount = renderer?.pageCount ?: 0
        }.onFailure {
            openError = true
            renderer = null
            descriptor?.close()
            descriptor = null
        }
        onDispose {
            renderer?.close()
            descriptor?.close()
        }
    }

    val activeRenderer = renderer
    if (openError || activeRenderer == null || pageCount <= 0) {
        Text("O PDF não pôde ser aberto ou não contém páginas disponíveis.")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE3E3E3)),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items((0 until pageCount).toList()) { index ->
            DynamicPdfPage(activeRenderer, index)
        }
    }
}

@Composable
private fun DynamicPdfPage(renderer: PdfRenderer, index: Int) {
    var bitmap by remember(renderer, index) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(renderer, index) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                synchronized(renderer) {
                    val page = renderer.openPage(index)
                    try {
                        Bitmap.createBitmap(
                            page.width * 2,
                            page.height * 2,
                            Bitmap.Config.ARGB_8888
                        ).also { output ->
                            output.eraseColor(android.graphics.Color.WHITE)
                            page.render(output, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    } finally {
                        page.close()
                    }
                }
            }.getOrNull()
        }
    }

    val pageBitmap = bitmap
    if (pageBitmap == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        Image(
            bitmap = pageBitmap.asImageBitmap(),
            contentDescription = "Página ${index + 1}",
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.FillWidth
        )
    }
}

private fun formatMediaTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0L) / 1000L
    val minutes = safe / 60L
    val seconds = safe % 60L
    return "%02d:%02d".format(minutes, seconds)
}

fun normalizeRemoteMediaUrl(raw: String): String {
    val url = raw.trim()
    if (url.isBlank()) return url

    val driveFileId = listOf(
        Regex("/d/([A-Za-z0-9_-]{10,})"),
        Regex("[?&]id=([A-Za-z0-9_-]{10,})"),
        Regex("/file/d/([A-Za-z0-9_-]{10,})")
    ).firstNotNullOfOrNull { pattern ->
        pattern.find(url)?.groupValues?.getOrNull(1)
    }

    return if (url.contains("drive.google.com", ignoreCase = true) && driveFileId != null) {
        "https://drive.google.com/uc?export=download&id=$driveFileId"
    } else {
        url
    }
}

private fun extractYoutubeVideoId(raw: String): String? {
    val value = raw.trim()
    if (value.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return value
    val patterns = listOf(
        Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
        Regex("youtube\\.com/watch\\?[^#]*v=([A-Za-z0-9_-]{11})"),
        Regex("youtube\\.com/(?:shorts|embed|live)/([A-Za-z0-9_-]{11})")
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        pattern.find(value)?.groupValues?.getOrNull(1)
    }
}
