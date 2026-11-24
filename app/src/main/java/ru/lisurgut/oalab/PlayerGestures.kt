package ru.lisurgut.oalab

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize

/**
 * Gesture layer rendered between the video and the control overlay.
 * Double tap seeks, hold accelerates to 2x, vertical edge drags adjust
 * brightness and media volume. No interception of UI buttons above it.
 */
@Composable
fun PlayerGestureLayer(
    playerKey: String,
    seekSeconds: Int,
    onTap: () -> Unit,
    onSeek: (Long) -> Unit,
    onHold: (Boolean) -> Unit,
    enabled: Boolean = true
): Modifier {
    val context = LocalContext.current
    val host = context as? Activity
    val sound = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val onTapCurrent by rememberUpdatedState(onTap)
    val onSeekCurrent by rememberUpdatedState(onSeek)
    val onHoldCurrent by rememberUpdatedState(onHold)
    var leftSide by remember { mutableStateOf(false) }

    return Modifier.fillMaxSize()
        .pointerInput(playerKey, seekSeconds, enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onTap = { onTapCurrent() },
                onDoubleTap = { tap ->
                    val amount = seekSeconds * 1_000L
                    onSeekCurrent(if (tap.x < size.width / 2f) -amount else amount)
                },
                onLongPress = { onHoldCurrent(true) },
                onPress = {
                    tryAwaitRelease()
                    onHoldCurrent(false)
                }
            )
        }
        .pointerInput(playerKey, enabled) {
            if (!enabled) return@pointerInput
            detectVerticalDragGestures(
                onDragStart = { point -> leftSide = point.x < size.width / 2f },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    val ratio = (-dragAmount / size.height.coerceAtLeast(1)).coerceIn(-1f, 1f)
                    if (leftSide && host != null) {
                        val attributes = host.window.attributes
                        val current = attributes.screenBrightness
                            .takeIf { it in 0f..1f } ?: .5f
                        attributes.screenBrightness = (current + ratio * 1.15f)
                            .coerceIn(.02f, 1f)
                        host.window.attributes = attributes
                    } else {
                        val max = sound.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val current = sound.getStreamVolume(AudioManager.STREAM_MUSIC)
                        sound.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            (current + ratio * max).toInt().coerceIn(0, max),
                            0
                        )
                    }
                }
            )
        }
}
