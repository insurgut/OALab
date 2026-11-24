package ru.lisurgut.oalab

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Small, legible tabs, no second navigation bar and no tall cards. */
@Composable
fun RecommendationTabs(
    selected: RecommendationMode,
    onChange: (RecommendationMode) -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Panel)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(
            RecommendationMode.PERSONAL to "Для тебя",
            RecommendationMode.GENRES to "По жанрам",
            RecommendationMode.DISCOVER to "Открытия"
        ).forEach { (mode, title) ->
            val active = mode == selected
            val color by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primaryContainer else Panel,
                label = "Recommendation selection"
            )
            Box(
                Modifier.weight(1f).height(39.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color)
                    .clickable { onChange(mode) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    title,
                    color = if (active)
                        MaterialTheme.colorScheme.onPrimaryContainer else Muted,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/** Reasons are actual matched library signals; source anime can be opened. */
@Composable
fun RecommendationCompactItem(
    recommendation: AnimeRecommendation,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onHide: () -> Unit,
    onSource: ((Anime) -> Unit)? = null
) {
    val anime = recommendation.anime
    var expanded by remember(anime.id) { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onOpen)
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Cover(
                anime.cover,
                Modifier.width(58.dp).height(80.dp)
                    .clip(RoundedCornerShape(9.dp)),
                anime.title
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    anime.title,
                    color = Paper, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, lineHeight = 18.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    anime.year.takeIf { it > 0 }?.toString(),
                    anime.score.takeIf { it > 0 }?.let {
                        "★ " + String.format(Locale.ROOT, "%.1f", it / 10.0)
                    },
                    anime.genres.firstOrNull()
                )
                if (meta.isNotEmpty()) {
                    Text(
                        meta.joinToString(" · "), color = Muted,
                        fontSize = 10.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    recommendation.reason,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp, lineHeight = 14.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Rounded.MoreVert, "Действия с рекомендацией",
                        modifier = Modifier.size(19.dp), tint = Muted
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (favorite) "Убрать из избранного" else "В избранное") },
                        leadingIcon = {
                            Icon(
                                if (favorite) Icons.Rounded.Favorite
                                else Icons.Rounded.FavoriteBorder,
                                null, modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = { expanded = false; onFavorite() }
                    )
                    recommendation.sourceAnime?.let { source ->
                        if (onSource != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Открыть аниме-основу",
                                        maxLines = 1
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.NorthEast, null,
                                        modifier = Modifier.size(18.dp))
                                },
                                onClick = { expanded = false; onSource(source) }
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text("Не интересно") },
                        leadingIcon = {
                            Icon(Icons.Rounded.VisibilityOff, null,
                                modifier = Modifier.size(18.dp))
                        },
                        onClick = { expanded = false; onHide() }
                    )
                }
            }
        }
        HorizontalDivider(color = Edge.copy(alpha = .48f), thickness = .6.dp)
    }
}
