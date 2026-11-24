package ru.lisurgut.oalab

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Both "Смотреть" and "Непрерывный просмотр" execute on tap and expose
 * the same two-way mode selector on long press, with no navigation or modal.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchModeButton(
    continuous: Boolean,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.primary)
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onLongPress,
                onLongClickLabel = "Выбрать режим просмотра"
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                if (continuous) Icons.Rounded.QueuePlayNext else Icons.Rounded.PlayArrow,
                null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (continuous) "Непрерывный просмотр" else "Смотреть",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(5.dp))
            Icon(
                Icons.Rounded.UnfoldMore, "Зажми для выбора режима",
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
fun PlaybackModeStrip(
    continuous: Boolean,
    onChoose: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    darkOverlay: Boolean = false
) {
    val surface = if (darkOverlay) Color(0xEC17171D)
        else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (darkOverlay) Color.White
        else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.clip(RoundedCornerShape(13.dp))
            .background(surface)
            .border(1.dp, textColor.copy(alpha = .13f), RoundedCornerShape(13.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(false to "Обычный", true to "Непрерывный").forEach { (value, title) ->
            val selected = continuous == value
            val background by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                label = "Режим просмотра"
            )
            Box(
                Modifier.weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(background)
                    .clickable { onChoose(value) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected) {
                        Icon(
                            Icons.Rounded.Check, null, modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        title, maxLines = 1,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else textColor
                    )
                }
            }
        }
    }
}
