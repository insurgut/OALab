package ru.lisurgut.oalab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun RecommendationTabs(
    selected: RecommendationMode,
    onChange: (RecommendationMode) -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Panel)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(
            RecommendationMode.PERSONAL to "Для тебя",
            RecommendationMode.GENRES to "По жанрам",
            RecommendationMode.DISCOVER to "Открытия"
        ).forEach { (mode, title) ->
            val active = mode == selected
            val background by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
                label = "Recommendation tab"
            )
            Box(
                Modifier.weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(background)
                    .clickable { onChange(mode) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    title, fontSize = 12.sp, maxLines = 1,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else Muted
                )
            }
        }
    }
}

@Composable
fun RecommendationTasteEditor(
    genres: List<String>,
    chosen: Set<String>,
    onToggle: (String) -> Unit,
    onReset: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .animateContentSize()
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Tune, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Настроить вкусы", color = Paper,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Text(
                    if (chosen.isEmpty()) "Уточни подборку по жанрам"
                    else chosen.sorted().take(3).joinToString(" · ") +
                        if (chosen.size > 3) "  +" + (chosen.size - 3) else "",
                    color = Muted, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess
                else Icons.Rounded.ExpandMore,
                if (expanded) "Свернуть" else "Раскрыть",
                tint = Muted, modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            ) {
                HorizontalDivider(color = Edge.copy(alpha = .55f))
                Spacer(Modifier.height(7.dp))
                if (genres.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        genres.forEach { genre ->
                            FilterChip(
                                selected = genre in chosen,
                                onClick = { onToggle(genre) },
                                label = { Text(genre, fontSize = 11.sp, maxLines = 1) },
                                leadingIcon = if (genre in chosen) {
                                    {
                                        Icon(
                                            Icons.Rounded.Check, null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(34.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        "Жанры появятся после загрузки каталога",
                        color = Muted, fontSize = 11.sp
                    )
                }
                if (chosen.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = onReset,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Сбросить жанры", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendationSpotlightCard(
    recommendation: AnimeRecommendation,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit
) {
    val anime = recommendation.anime
    Box(
        Modifier.fillMaxWidth().height(205.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(PanelAlt)
            .clickable(onClick = onOpen)
    ) {
        Cover(
            anime.banner.ifBlank { anime.cover },
            Modifier.fillMaxSize(), anime.title
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .15f),
                    .35f to Color.Black.copy(alpha = .23f),
                    1f to Color.Black.copy(alpha = .94f)
                )
            )
        )
        Row(
            Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.AutoAwesome, null,
                modifier = Modifier.size(14.dp), tint = Color.White
            )
            Spacer(Modifier.width(5.dp))
            Text(
                "ПОДХОДИТ ТЕБЕ", fontSize = 10.sp,
                color = Color.White, fontWeight = FontWeight.SemiBold,
                letterSpacing = .9.sp
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(9.dp)
                .size(37.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = .51f))
                .clickable(onClick = onFavorite),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (favorite) "Убрать из избранного" else "Добавить в избранное",
                tint = Color.White, modifier = Modifier.size(19.dp)
            )
        }
        Column(
            Modifier.align(Alignment.BottomStart)
                .fillMaxWidth().padding(start = 15.dp, end = 15.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                anime.title, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp, lineHeight = 24.sp
            )
            Text(
                recommendation.reason, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp, color = Color.White.copy(alpha = .86f)
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val details = listOfNotNull(
                    anime.year.takeIf { it > 0 }?.toString(),
                    anime.score.takeIf { it > 0 }?.let {
                        "★ " + String.format(Locale.ROOT, "%.1f", it / 10.0)
                    },
                    anime.genres.firstOrNull()
                )
                Text(
                    details.joinToString(" · "),
                    color = Color.White.copy(alpha = .85f), fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(
                    Modifier.clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Открыть", fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RecommendationCompactItem(
    recommendation: AnimeRecommendation,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onHide: () -> Unit
) {
    val anime = recommendation.anime
    var menu by remember(anime.id) { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpen)
                .padding(vertical = 9.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Cover(
                anime.cover,
                Modifier.width(70.dp).height(94.dp)
                    .clip(RoundedCornerShape(10.dp)),
                anime.title
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    anime.title, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Paper, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, lineHeight = 18.sp
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
                        meta.joinToString(" · "),
                        color = Muted, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.AutoAwesome, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp).size(12.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        recommendation.reason, maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp, lineHeight = 14.sp
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onFavorite, modifier = Modifier.size(33.dp)) {
                    Icon(
                        if (favorite) Icons.Rounded.Favorite
                        else Icons.Rounded.FavoriteBorder,
                        if (favorite) "Убрать из избранного" else "В избранное",
                        tint = if (favorite) MaterialTheme.colorScheme.primary else Muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box {
                    IconButton(
                        onClick = { menu = true },
                        modifier = Modifier.size(33.dp)
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert, "Другие действия",
                            modifier = Modifier.size(18.dp), tint = Muted
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Не интересно", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.VisibilityOff, null,
                                    modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                menu = false
                                onHide()
                            }
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = Edge.copy(alpha = .55f), thickness = .7.dp)
    }
}
