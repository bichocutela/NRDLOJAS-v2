package com.example.ui

import android.animation.ValueAnimator
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.R
import kotlinx.coroutines.delay

/** Local approved ribbon/letter animation. Navigation initializes beneath the overlay. */
@Suppress("UNUSED_PARAMETER")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun NrdLaunchAnimation(appTheme: String) {
    var completed by rememberSaveable { mutableStateOf(false) }
    if (completed) return
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled()
        else Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    if (!animationsEnabled) {
        LaunchedEffect(Unit) { completed = true }
        return
    }
    val owner = LocalLifecycleOwner.current
    val opacity = remember { Animatable(1f) }
    var finished by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState<() -> Unit>({ finished = true })
    val player = remember(context) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    DisposableEffect(player, owner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) finish()
            }
            override fun onPlayerError(error: PlaybackException) { finish() }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> player.play()
                Lifecycle.Event.ON_PAUSE -> player.pause()
                else -> Unit
            }
        }
        player.addListener(listener)
        owner.lifecycle.addObserver(observer)
        try {
            player.setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/${R.raw.nrd_launch_ribbons}"))
            player.prepare()
            player.playWhenReady = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        } catch (_: Exception) { finish() }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    // A missing/unsupported decoder must never trap the user at launch.
    LaunchedEffect(Unit) {
        delay(4_500)
        finished = true
    }
    LaunchedEffect(finished) {
        if (finished) {
            opacity.animateTo(0f, tween(180))
            completed = true
        }
    }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = opacity.value }
            .background(Color(0xFFF7FAFF))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.nrd_launch_player, null) as PlayerView).apply {
                    this.player = player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.player = null }
        )
    }
}
