package ru.lisurgut.oalab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// Exact year hue formula ported from user's Plum 0.29 source.
fun yearTint(year: Int): Color = Color.hsv((year * 137.508f) % 360f, .42f, .99f)

@Composable
fun AnimeYearMark(year: Int) {
    if (year > 0) Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Spacer(Modifier.size(7.dp).clip(CircleShape).background(yearTint(year)))
        Text(year.toString(), color = Muted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun PlumAnimeLine(
    anime: Anime, library: LocalLibrary, onOpen: () -> Unit, onPlay: () -> Unit,
    modifier: Modifier = Modifier, rank: Int? = null
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (rank != null) Text(rank.toString().padStart(2,'0'), Modifier.width(22.dp),
                fontSize = 12.sp, color = Muted)
            Cover(anime.cover, Modifier.size(width = 46.dp, height = 55.dp)
                .clip(RoundedCornerShape(9.dp)), description = anime.title)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(anime.title, color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp, lineHeight = 19.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AnimeYearMark(anime.year)
                    Text(anime.format.ifBlank { "ANIME" }.replace("_", " "),
                        color = Muted, fontSize = 12.sp, maxLines = 1)
                    if (library.status(anime) != "Не смотрел")
                        Icon(Icons.Rounded.Check, "В библиотеке", tint = Paper,
                            modifier = Modifier.size(17.dp))
                }
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(PanelAlt)
                .clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, "Смотреть", tint = Paper,
                    modifier = Modifier.size(28.dp))
            }
        }
        HorizontalDivider(color = Edge.copy(alpha = .8f), thickness = .7.dp)
    }
}

@Composable
fun YearSpectrumPicker(first: Int?, last: Int?, onChange: (Int?, Int?) -> Unit) {
    val minYear = 1950
    val maxYear = 2027
    val from = (first ?: minYear).coerceIn(minYear,maxYear)
    val until = (last ?: maxYear).coerceIn(from,maxYear)
    Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Годы выпуска", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.weight(1f))
            if (first != null || last != null) TextButton(onClick = {onChange(null,null)}) {
                Text("Сбросить", fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(from.toString(), color = yearTint(from), fontWeight = FontWeight.Bold)
            Text(until.toString(), color = yearTint(until), fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                .background(Brush.horizontalGradient((0..24).map {
                    yearTint(minYear + ((maxYear - minYear)*it/24f).roundToInt())
                })))
            RangeSlider(
                value = from.toFloat()..until.toFloat(),
                onValueChange = {
                    val a = it.start.roundToInt().coerceIn(minYear,maxYear)
                    val b = it.endInclusive.roundToInt().coerceIn(a,maxYear)
                    onChange(if (a==minYear && b==maxYear) null else a,
                        if (a==minYear && b==maxYear) null else b)
                },
                valueRange = minYear.toFloat()..maxYear.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = lerp(yearTint(from),yearTint(until),.5f),
                    activeTrackColor = lerp(yearTint(from),yearTint(until),.5f),
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("1950","1980","2000","2027").forEach {
                Text(it,fontSize=10.sp,color=Muted)
            }
        }
    }
}

@Composable
fun PlumHomeScreen(
    catalog: List<Anime>, library: LocalLibrary, loading: Boolean, error: String?,
    onOpen: (Anime)->Unit, onPlay: (Anime)->Unit,
    onCatalogue: ()->Unit, onRetry: ()->Unit
) {
    val progress = library.inProgress()
    val featured = progress.firstOrNull()?.anime ?: catalog.firstOrNull()
    val preferred = library.preferredGenres().take(5)
    val ranked = if (preferred.isEmpty()) catalog else catalog.sortedWith(
        compareByDescending<Anime> { anime ->
            anime.genres.count { it in preferred }
        }.thenByDescending { it.score }
    )
    val visible = (progress.map { it.anime } + ranked)
        .filter { it.id != featured?.id }
        .distinctBy { it.id }.take(9)
    val watching = progress.size
    val finished = library.watched().size

    LazyColumn(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 12.dp, bottom = 114.dp
        )
    ) {
        item(key = "home-header") {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "OAlab", color = Paper, fontSize = 27.sp,
                        letterSpacing = (-1.1).sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (watching + finished > 0)
                            watching.toString() + " смотрю · " +
                                finished.toString() + " просмотрено"
                        else "Твоя коллекция аниме",
                        color = Muted, fontSize = 11.sp
                    )
                }
                IconButton(
                    onClick = onCatalogue,
                    modifier = Modifier.size(41.dp)
                        .clip(CircleShape).background(Panel)
                ) {
                    Icon(
                        Icons.Rounded.Search, "Поиск аниме",
                        tint = Paper, modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        if (featured != null) {
            item(key = "home-featured") {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Panel)
                        .clickable { onOpen(featured) }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cover(
                        featured.cover,
                        Modifier.width(75.dp).height(101.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        featured.title
                    )
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            if (progress.isNotEmpty()) "ПРОДОЛЖИТЬ"
                            else "ПОПУЛЯРНОЕ",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = .7.sp
                        )
                        Text(
                            featured.title, color = Paper, fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 19.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (progress.isNotEmpty()) {
                                Text(
                                    "Серия " +
                                        (library.entry(featured)?.lastEpisode ?: 1),
                                    color = Muted, fontSize = 11.sp
                                )
                            } else AnimeYearMark(featured.year)
                            TextButton(
                                onClick = { onPlay(featured) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow, null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text("Смотреть", fontSize = 12.sp)
                            }
                        }
                    }
                    Icon(
                        Icons.Rounded.ChevronRight, "Подробнее",
                        tint = Muted, modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.height(22.dp))
            }
        }
        item(key = "weekly-upcoming-releases") {
            UpcomingScheduleSection(library = library, onOpen = onOpen)
            Spacer(Modifier.height(17.dp))
        }
        if (error != null) item(key = "home-error") {
            ErrorPanel(error, onRetry)
            Spacer(Modifier.height(9.dp))
        }
        if (loading && catalog.isEmpty()) item(key = "home-loading") {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        item(key = "home-recommend-title") {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (preferred.isEmpty()) "Что посмотреть" else "По твоим интересам",
                    color = Paper, fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCatalogue) {
                    Text("Каталог", fontSize = 11.sp)
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        items(visible, key = { it.id }) { anime ->
            PlumAnimeLine(
                anime, library,
                onOpen = { onOpen(anime) },
                onPlay = { onPlay(anime) }
            )
        }
        if (visible.isEmpty() && featured == null && !loading && error == null) {
            item(key = "home-empty") {
                EmptyState(
                    Icons.Rounded.Movie,
                    "Здесь появится аниме",
                    "Открой каталог, чтобы найти что-нибудь интересное"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlumCatalogScreen(
    catalog: List<Anime>, library: LocalLibrary,
    search: String, onSearch: (String)->Unit,
    category: String, onCategory: (String)->Unit,
    sort: String, onSort: (String)->Unit,
    fromYear: Int?, toYear: Int?, onYears: (Int?,Int?)->Unit,
    loading: Boolean, error: String?,
    onOpen: (Anime)->Unit, onPlay: (Anime)->Unit,
    onMore: ()->Unit, onRetry: ()->Unit
) {
    var showFilters by remember { mutableStateOf(false) }
    var showRange by remember { mutableStateOf(false) }
    var draftSort by remember(showFilters) { mutableStateOf(sort) }
    var draftCategory by remember(showFilters) { mutableStateOf(category) }
    var draftFrom by remember(showFilters) { mutableStateOf(fromYear) }
    var draftTo by remember(showFilters) { mutableStateOf(toYear) }
    val activeFilters = listOf(
        category.takeIf { it != "Все" },
        sort.takeIf { it != "Популярные" },
        (fromYear ?: toYear)?.let { "Годы" }
    ).count { it != null }
    val visible = if (fromYear == null && toYear == null) catalog
        else catalog.filter { it.year in (fromYear ?: 1950)..(toYear ?: 2027) }
    val clear: () -> Unit = {
        onCategory("Все")
        onSort("Популярные")
        onYears(null, null)
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 13.dp, bottom = 115.dp
        )
    ) {
        item(key = "catalog-heading") {
            Text(
                "Каталог", color = Paper, fontSize = 27.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-.8).sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            OutlinedTextField(
                value = search,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(15.dp),
                placeholder = { Text("Поиск аниме", color = Muted) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, null, Modifier.size(21.dp))
                },
                trailingIcon = if (search.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearch("") }) {
                            Icon(Icons.Rounded.Close, "Очистить поиск")
                        }
                    }
                } else null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Panel,
                    unfocusedContainerColor = Panel,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Edge.copy(alpha = .55f)
                )
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.clip(CircleShape)
                        .background(Panel)
                        .clickable { showFilters = true }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Tune, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Фильтры" +
                            if (activeFilters > 0) " · " + activeFilters else "",
                        fontSize = 12.sp, color = Paper,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (activeFilters > 0) {
                    TextButton(onClick = clear) {
                        Text("Сбросить", fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    visible.size.toString() + " аниме",
                    fontSize = 11.sp, color = Muted
                )
            }
            if (activeFilters > 0) {
                Text(
                    listOfNotNull(
                        category.takeIf { it != "Все" },
                        sort.takeIf { it != "Популярные" },
                        (fromYear ?: toYear)?.let {
                            (fromYear ?: 1950).toString() +
                                "–" + (toYear ?: 2027).toString()
                        }
                    ).joinToString(" · "),
                    color = Muted, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        if (error != null) item(key = "catalog-error") {
            ErrorPanel(error, onRetry)
        }
        items(visible, key = { it.id }) { anime ->
            PlumAnimeLine(
                anime, library,
                onOpen = { onOpen(anime) },
                onPlay = { onPlay(anime) }
            )
        }
        if (loading) item(key = "catalog-loading") {
            Box(
                Modifier.fillMaxWidth().padding(19.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp)
                )
            }
        } else if (error == null && visible.isEmpty()) {
            item(key = "catalog-empty") {
                EmptyState(Icons.Rounded.SearchOff,
                    "Ничего не нашлось", "Попробуй убрать часть фильтров")
            }
        } else if (visible.isNotEmpty()) {
            item(key = "catalog-more") {
                OutlinedButton(
                    onClick = onMore,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    shape = CircleShape
                ) {
                    Text("Показать ещё")
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, bottom = 24.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Фильтры каталога",
                        fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                        color = Paper, modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            draftCategory = "Все"
                            draftSort = "Популярные"
                            draftFrom = null
                            draftTo = null
                            showRange = false
                        }
                    ) {
                        Text("Очистить", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Сортировать", color = Paper,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    AnimeApi.sorting.forEach { choice ->
                        FilterChip(
                            selected = draftSort == choice,
                            onClick = { draftSort = choice },
                            label = { Text(choice, fontSize = 11.sp) },
                            shape = RoundedCornerShape(11.dp)
                        )
                    }
                }
                Spacer(Modifier.height(11.dp))
                Text(
                    "Жанр", color = Paper,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    AnimeApi.categories.forEach { choice ->
                        FilterChip(
                            selected = draftCategory == choice,
                            onClick = { draftCategory = choice },
                            label = { Text(choice, fontSize = 11.sp) },
                            shape = RoundedCornerShape(11.dp)
                        )
                    }
                }
                Spacer(Modifier.height(11.dp))
                Text(
                    "Год выпуска", color = Paper,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    val quick = listOf(
                        Triple("Любой", null, null),
                        Triple("2025+", 2025, null),
                        Triple("2020+", 2020, null),
                        Triple("2010-е", 2010, 2019),
                        Triple("До 2000", null, 1999)
                    )
                    quick.forEach { (title, earliest, latest) ->
                        FilterChip(
                            selected = draftFrom == earliest && draftTo == latest,
                            onClick = {
                                draftFrom = earliest
                                draftTo = latest
                                showRange = false
                            },
                            label = { Text(title, fontSize = 11.sp) },
                            shape = RoundedCornerShape(11.dp)
                        )
                    }
                    FilterChip(
                        selected = showRange,
                        onClick = { showRange = !showRange },
                        label = { Text("Диапазон", fontSize = 11.sp) },
                        shape = RoundedCornerShape(11.dp)
                    )
                }
                if (showRange) {
                    val first = (draftFrom ?: 1950).coerceIn(1950, 2027)
                    val last = (draftTo ?: 2027).coerceIn(first, 2027)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(first.toString(), color = Muted, fontSize = 12.sp)
                        Text(last.toString(), color = Muted, fontSize = 12.sp)
                    }
                    RangeSlider(
                        value = first.toFloat()..last.toFloat(),
                        onValueChange = { range ->
                            draftFrom = range.start.roundToInt()
                            draftTo = range.endInclusive.roundToInt()
                        },
                        valueRange = 1950f..2027f
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        onCategory(draftCategory)
                        onSort(draftSort)
                        onYears(draftFrom, draftTo)
                        showFilters = false
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Применить фильтры") }
            }
        }
    }
}

@Composable
fun PlumLibraryScreen(library:LocalLibrary,onOpen:(Anime)->Unit,onPlay:(Anime)->Unit){
    var mode by remember{mutableStateOf("Избранное")}
    val records=when(mode){
        "Избранное"->library.entries.filter{it.favorite && it.anime.id.startsWith("aniliberty:")}
        "Смотрю"->library.entries.filter{it.status=="Смотрю" && it.anime.id.startsWith("aniliberty:")}
        "Просмотрено"->library.entries.filter{it.status=="Просмотрено" && it.anime.id.startsWith("aniliberty:")}
        "В планах"->library.entries.filter{it.status=="В планах" && it.anime.id.startsWith("aniliberty:")}
        else->library.entries.filter{it.anime.id.startsWith("aniliberty:")}
    }
    LazyColumn(Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=110.dp)){
        item {
            Text("Библиотека",color=MaterialTheme.colorScheme.onBackground,
                fontSize=30.sp,fontWeight=FontWeight.Medium,
                modifier=Modifier.padding(top=14.dp,bottom=13.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(7.dp)){
                listOf("Избранное","Смотрю","Просмотрено","В планах","Все").forEach{
                    FlatChip(it,mode==it){mode=it}
                }
            }
            Spacer(Modifier.height(9.dp))
        }
        items(records,key={it.anime.id}){e->
            PlumAnimeLine(e.anime,library,{onOpen(e.anime)},{onPlay(e.anime)})
        }
        if(records.isEmpty())item{
            EmptyState(Icons.Rounded.BookmarkBorder,"Пока пусто","Добавляй аниме в библиотеку")
        }
    }
}
