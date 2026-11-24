package ru.lisurgut.oalab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

val Ink: Color
    @Composable get() = MaterialTheme.colorScheme.background
val Panel: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
val PanelAlt: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
val Muted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Edge: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant
val Paper: Color
    @Composable get() = MaterialTheme.colorScheme.onBackground

@Composable
fun OALabTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val palette = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme() else lightColorScheme()
    // Surface supplies correct onBackground as LocalContentColor to every screen.
    MaterialTheme(colorScheme = palette) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = palette.background,
            contentColor = palette.onBackground
        ) { content() }
    }
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
        Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
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
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (anime.score > 0) {
                Text(
                    (anime.score / 10f).toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        .background(Color.Black.copy(alpha = .75f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(anime.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp)
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
fun BottomPill(
    selected: Int,
    onSelect: (Int) -> Unit,
    updateAvailable: Boolean = false,
    onOpenUpdate: () -> Unit = {}
) {
    val tabs = listOf(
        Triple("Главная", Icons.Rounded.Home, 0),
        Triple("Каталог", Icons.Rounded.Search, 1),
        Triple("Моё", Icons.Rounded.Bookmarks, 2),
        Trip    val compact = LocalConfiguration.current.screenWidthDp < 390
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .border(1.dp, Edge.copy(alpha = .65f), CircleShape)
                .background(PanelAlt)
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            tabs.forEach { (name, icon, index) ->
                val active = selected == index
                val background by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent,
                    label = "Selected navigation capsule"
                )
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(background)
                        .clickable { onSelect(index) }
                        .height(44.dp)
                        .widthIn(min = 41.dp)
                        .padding(horizontal = if (active) 11.dp else 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        icon, name,
                        tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else Muted,
                        modifier = Modifier.size(19.dp)
                    )
                    // The selected tab ALWAYS shows its name, even on narrow phones
                    // and when the update chip is visible.
                    AnimatedVisibility(visible = active) {
                        Text(
                            name,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(start = 6.dp),
                            fontSize = if (compact) 10.sp else 11.sp,
                            maxLines = 1,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            AnimatedVisibility(visible = updateAvailable) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .clickable(onClick = onOpenUpdate)
                        .size(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.SystemUpdate, "Доступно обновление",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }sp, fontWeight = FontWeight.SemiBold)
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
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else Edge),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Panel,
            labelColor = Muted,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
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
