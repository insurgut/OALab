package ru.lisurgut.oalab

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate

/**
 * Full AniLibria week schedule on Home, grouped into 7 actual upcoming dates.
 * Does not pretend that weekly publish_day is a confirmed release date or time.
 */
@Composable
fun UpcomingScheduleSection(
    library: LocalLibrary,
    onOpen: (Anime) -> Unit
) {
    var schedule by remember(library) {
        mutableStateOf(library.cachedWeeklySchedule())
    }
    var refreshing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var requestId by remember { mutableIntStateOf(0) }
    var entireWeek by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableIntStateOf(0) }
    var expandedDay by remember { mutableStateOf(false) }

    // A successful schedule is cached for 30 minutes. Home works offline.
    LaunchedEffect(library, requestId) {
        var first = true
        while (isActive) {
            if (!first || requestId != 0 || !library.weeklyScheduleCacheFresh()) {
                refreshing = true
                failure = null
                try {
                    val fetched = AniLibertyApi.resolveWeeklySchedule()
                    schedule = fetched
                    if (fetched.isNotEmpty()) library.saveWeeklySchedule(fetched)
                } catch (e: Exception) {
                    failure = e.localizedMessage ?: "Нет соединения с AniLibria"
                    schedule = library.cachedWeeklySchedule()
                } finally {
                    refreshing = false
                }
            }
            first = false
            delay(30 * 60_000L)
        }
    }

    val today = LocalDate.now()
    val days = remember(schedule, today) { UpcomingSchedule.byDate(schedule, today) }
    val selected = days.getOrNull(selectedDay.coerceIn(0, 6))
    val itemsForDay = selected?.items.orEmpty()
    val selectedItems = if (expandedDay) itemsForDay else itemsForDay.take(3)

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Скоро выйдет",
                    color = Paper, fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Ближайшие 7 дней",
                    color = Muted, fontSize = 10.sp
                )
            }
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
            }
            IconButton(
                onClick = { requestId++ },
                enabled = !refreshing,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Rounded.Refresh, "Обновить расписание",
                    modifier = Modifier.size(19.dp), tint = Muted)
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(end = 3.dp)
        ) {
            items(days, key = { it.date.toString() }) { day ->
                val index = days.indexOfFirst { it.date == day.date }
                val selectedChip = !entireWeek && selectedDay == index
                Column(
                    Modifier.width(76.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            if (selectedChip) MaterialTheme.colorScheme.primaryContainer
                            else Panel
                        )
                        .clickable {
                            selectedDay = index.coerceAtLeast(0)
                            expandedDay = false
                            entireWeek = false
                        }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        if (index == 0) "ЗАВТРА" else UpcomingSchedule.shortDay(day.date),
                        color = if (selectedChip)
                            MaterialTheme.colorScheme.onPrimaryContainer else Muted,
                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        UpcomingSchedule.displayDate(day.date),
                        color = if (selectedChip)
                            MaterialTheme.colorScheme.onPrimaryContainer else Paper,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                    Text(
                        day.items.size.toString() + " сер.",
                        color = if (selectedChip)
                            MaterialTheme.colorScheme.onPrimaryContainer else Muted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (entireWeek) "Расписание на неделю"
                else selected?.let {
                    UpcomingSchedule.dayTitle(it.date, today) + " · " +
                        UpcomingSchedule.displayDate(it.date)
                } ?: "Скоро",
                modifier = Modifier.weight(1f),
                color = Paper,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = {
                entireWeek = !entireWeek
                expandedDay = false
            }) {
                Text(
                    if (entireWeek) "Один день" else "Вся неделя",
                    fontSize = 11.sp
                )
                Icon(
                    if (entireWeek) Icons.Rounded.ChevronLeft
                    else Icons.Rounded.ChevronRight,
                    null, modifier = Modifier.size(16.dp)
                )
            }
        }
        if (entireWeek) {
            days.forEach { day ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        UpcomingSchedule.dayTitle(day.date, today),
                        color = Paper, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        UpcomingSchedule.displayDate(day.date),
                        color = Muted, fontSize = 11.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        day.items.size.toString() + " сер.",
                        color = Muted, fontSize = 10.sp
                    )
                }
                if (day.items.isEmpty()) {
                    Text(
                        "Нет анонсированных серий",
                        color = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                } else {
                    day.items.forEach { item ->
                        ScheduledAnimeRow(item, onOpen = { onOpen(item.anime) })
                    }
                }
            }
        } else if (selectedItems.isNotEmpty()) {
            selectedItems.forEach { item ->
                ScheduledAnimeRow(item, onOpen = { onOpen(item.anime) })
            }
            if (!expandedDay && itemsForDay.size > selectedItems.size) {
                TextButton(
                    onClick = { expandedDay = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Показать ещё " + (itemsForDay.size - selectedItems.size),
                        fontSize = 12.sp
                    )
                    Icon(Icons.Rounded.ExpandMore, null, Modifier.size(17.dp))
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Panel)
                    .padding(horizontal = 15.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    if (refreshing) "Получаем расписание…"
                    else if (schedule.isEmpty())
                        "Расписание пока не загружено"
                    else "На этот день анонсов нет",
                    color = Paper, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (failure != null) "Ошибка сети: " + failure
                    else "Показываются только анонсы, опубликованные AniLibria",
                    color = Muted, fontSize = 11.sp
                )
            }
        }
        Text(
            "Ориентировочные дни выхода озвучки. Точное время не объявлено, " +
                "расписание может измениться.",
            color = Muted, fontSize = 10.sp, lineHeight = 14.sp,
            modifier = Modifier.padding(bottom = 7.dp, start = 3.dp)
        )
    }
}

@Composable
private fun ScheduledAnimeRow(item: ScheduledAnime, onOpen: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onOpen)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Cover(
                item.anime.cover,
                Modifier.width(45.dp).height(61.dp)
                    .clip(RoundedCornerShape(8.dp)),
                item.anime.title
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    item.anime.title,
                    color = Paper, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2, lineHeight = 15.sp,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.nextEpisode?.let { "Ожидается серия " + it }
                        ?: "Номер серии уточняется",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp
                )
            }
            Icon(
                Icons.Rounded.ChevronRight, "Подробнее",
                tint = Muted, modifier = Modifier.size(17.dp)
            )
        }
        HorizontalDivider(color = Edge.copy(alpha = .5f), thickness = .6.dp)
    }
}
