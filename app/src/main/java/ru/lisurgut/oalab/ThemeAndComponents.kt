package ru.lisurgut.oalab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

val Ink = Color(0xFF09090B)
val Panel = Color(0xFF17171A)
val PanelAlt = Color(0xFF222226)
val Muted = Color(0xFF99999F)
val Edge = Color(0xFF343438)
val Paper = Color(0xFFF5F5F5)

@Composable
fun OALabTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Paper,
        onPrimary = Ink,
        primaryContainer = PanelAlt,
        onPrimaryContainer = Paper,
        secondary = Color(0xFFCACACC),
        onSecondary = Ink,
        tertiary = Color(0xFFAAAAAF),
        onTertiary = Ink,
        background = Ink,
        onBackground = Paper,
        surface = Panel,
        onSurface = Paper,
        surfaceVariant = PanelAlt,
        onSurfaceVariant = Muted,
        outline = Edge,
        outlineVariant = Edge,
        error = Color(0xFFFFABAB)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun Cover(url: String, modifier: Modifier = Modifier, description: String? = null, crop: ContentScale = ContentScale.Crop) {
    Box(modifier.background(PanelAlt)) {
        if (url.startsWith("http")) {
            AsyncImage(
                model = url,
                contentDescription = description,
                contentScale = crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Rounded.Movie,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(40.dp).align(Alignment.Center)
            )
        }
    }
}

@Composable
fun Eyebrow(label: String) {
    Text(
        label.uppercase(),
        fontSize = 10.sp,
        color = Muted,
        letterSpacing = 2.2.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, action: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Muted)
        }
        if (action != null) {
            TextButton(onClick = action) {
                Text("Все", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun PosterCard(
    anime: Anime,
    favorite: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onFavorite: () -> Unit
) {
    Column(modifier.clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(18.dp))) {
            Cover(anime.cover, Modifier.fillMaxSize(), anime.title)
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp)
                    .size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = .65f))
                    .clickable(onClick = onFavorite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    "В избранное",
                    tint = Paper,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (anime.score > 0) {
                Text(
                    (anime.score / 10f).toString(),
                    color = Paper,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        .background(Color.Black.copy(alpha = .75f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(anime.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(3.dp))
        Text(
            listOfNotNull(anime.year.takeIf { it > 0 }?.toString(), anime.format.takeIf { it.isNotBlank() }).joinToString(" · "),
            color = Muted,
            maxLines = 1,
            fontSize = 11.sp
        )
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(PanelAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(30.dp), tint = Muted)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(hint, fontSize = 13.sp, color = Muted, lineHeight = 19.sp)
    }
}

@Composable
fun BottomPill(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        Triple("Главная", Icons.Rounded.Home, 0),
        Triple("Каталог", Icons.Rounded.Search, 1),
        Triple("Моё", Icons.Rounded.Bookmarks, 2),
        Triple("Источники", Icons.Rounded.Dns, 3),
        Triple("Настройки", Icons.Rounded.Settings, 4)
    )
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.shadow(12.dp, CircleShape)
                .clip(CircleShape).border(1.dp, Edge, CircleShape)
                .background(PanelAlt).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (name, icon, index) ->
                val active = selected == index
                val background by animateColorAsState(
                    if (active) Paper else PanelAlt, label = "Dock selection"
                )
                Row(
                    Modifier.clip(CircleShape).background(background)
                        .clickable { onSelect(index) }
                        .height(47.dp).widthIn(min = 47.dp)
                        .padding(horizontal = if (active) 11.dp else 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(icon, if (active) null else name,
                        tint = if (active) Ink else Muted,
                        modifier = Modifier.size(20.dp))
                    AnimatedVisibility(active) {
                        Text(name, color = Ink,
                            modifier = Modifier.padding(start = 6.dp),
                            fontSize = 11.sp, maxLines = 1,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun FlatChip(label: String, selected: Boolean = false, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp, maxLines = 1) },
        border = BorderStroke(1.dp, if (selected) Paper else Edge),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Panel,
            labelColor = Muted,
            selectedContainerColor = Paper,
            selectedLabelColor = Ink
        ),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
fun MetricChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(11.dp))
            .background(PanelAlt)
            .padding(horizontal = 11.dp, vertical = 8.dp)
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = Paper)
        Spacer(Modifier.width(6.dp))
        Text(text, color = Paper, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
