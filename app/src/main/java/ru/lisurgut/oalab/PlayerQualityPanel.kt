package ru.lisurgut.oalab

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Only stream variants actually supplied by AniLibria for the current episode. */
@Composable
fun PlayerQualityPanel(
    available: List<Int>,
    preference: Int,
    current: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(Color(0xF21A1B21))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier.padding(start = 8.dp, top = 3.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.HighQuality, null,
                modifier = Modifier.size(17.dp),
                tint = Color.White.copy(alpha = .9f))
            Spacer(Modifier.width(7.dp))
            Text("Качество видео", color = Color.White,
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        (listOf(0) + available.distinct().filter { it > 0 }.sortedDescending())
            .forEach { quality ->
                val active = preference == quality
                val tint by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = .28f)
                    else Color.Transparent, label = "Quality choice"
                )
                Row(
                    Modifier.fillMaxWidth().height(37.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint)
                        .clickable { onSelect(quality) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (quality == 0) "Авто" else quality.toString() + "p",
                        color = Color.White, fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (quality == 0) {
                        Text("по сети", color = Color.White.copy(alpha = .56f),
                            fontSize = 10.sp)
                    } else if (quality == current) {
                        Text("сейчас", color = Color.White.copy(alpha = .62f),
                            fontSize = 10.sp)
                    }
                    if (active) {
                        Spacer(Modifier.width(7.dp))
                        Icon(Icons.Rounded.Check, "Выбрано",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        Text(
            "Только реально доступные потоки AniLibria",
            color = Color.White.copy(alpha = .6f),
            fontSize = 9.sp, lineHeight = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}
