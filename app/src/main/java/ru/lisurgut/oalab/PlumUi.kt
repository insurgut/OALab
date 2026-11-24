package ru.lisurgut.oalab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
    val genres = library.preferredGenres().take(5)
    val sorted = if (genres.isEmpty()) catalog else catalog.sortedWith(
        compareByDescending<Anime> { a -> a.genres.count { it in genres } }
            .thenByDescending { it.score })
    val visible = (progress.map { it.anime } + sorted).distinctBy { it.id }.take(35)
    LazyColumn(Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        contentPadding = PaddingValues(start=16.dp,end=16.dp,bottom=105.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top=9.dp,bottom=11.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Text("OAlab",Modifier.weight(1f),color=MaterialTheme.colorScheme.onBackground,fontSize=27.sp,
                    fontWeight=FontWeight.Medium, letterSpacing=(-1.2).sp)
                IconButton(onClick=onCatalogue) {Icon(Icons.Rounded.Search,"Поиск")}
            }
        }
        if(featured!=null) item {
            Box(Modifier.fillMaxWidth().height(153.dp).clip(RoundedCornerShape(25.dp))
                .background(Panel).clickable {onOpen(featured)}) {
                Cover(featured.banner.ifBlank{featured.cover},
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.6f))
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    listOf(Ink, Ink.copy(alpha=.96f),Ink.copy(alpha=.4f),Color.Transparent))))
                Column(Modifier.fillMaxHeight().fillMaxWidth(.76f).padding(17.dp),
                    verticalArrangement=Arrangement.SpaceBetween) {
                    Text(if(progress.isNotEmpty())"Продолжить просмотр" else "В тренде",
                        color=Color.White.copy(alpha=.84f),fontSize=11.sp)
                    Column {
                        Text(featured.title,color=Color.White,maxLines=2,overflow=TextOverflow.Ellipsis,
                            fontSize=19.sp,lineHeight=23.sp,fontWeight=FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(9.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            AnimeYearMark(featured.year)
                            Text(featured.format.ifBlank{"Аниме"},color=Color.White.copy(alpha=.84f),fontSize=11.sp)
                        }
                    }
                    Button(onClick={onPlay(featured)},shape=CircleShape,
                        contentPadding=PaddingValues(horizontal=12.dp),
                        modifier=Modifier.height(35.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary)) {
                        Icon(Icons.Rounded.PlayArrow,null,modifier=Modifier.size(18.dp))
                        Text("Смотреть",fontSize=12.sp)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=13.dp),
                horizontalArrangement=Arrangement.SpaceBetween) {
                PlumMetric("Избранное",library.entries.count{it.favorite}.toString())
                PlumMetric("Смотрю",progress.size.toString())
                PlumMetric("Просмотрено",library.watched().size.toString())
            }
        }
        if(error!=null)item{ErrorPanel(error,onRetry)}
        if(loading && catalog.isEmpty())item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        item {
            Row(Modifier.fillMaxWidth().padding(vertical=5.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Text("Для тебя",color=MaterialTheme.colorScheme.onBackground,
                    fontSize=22.sp,fontWeight=FontWeight.Medium,
                    modifier=Modifier.weight(1f))
                TextButton(onClick=onCatalogue){Text("Каталог",fontSize=12.sp)}
            }
        }
        itemsIndexed(visible,key={_,a->a.id}){i,a->
            PlumAnimeLine(a,library,{onOpen(a)},{onPlay(a)},rank=i+1)
        }
        if(visible.isEmpty()&&!loading&&error==null)item{
            EmptyState(Icons.Rounded.Movie,"Каталог пуст","Повтори загрузку")
        }
    }
}

@Composable
private fun PlumMetric(label:String,value:String) {
    Column {Text(value,fontSize=23.sp);Text(label,fontSize=10.sp,color=Muted)}
}

@Composable
fun PlumCatalogScreen(
    catalog:List<Anime>,library:LocalLibrary,
    search:String,onSearch:(String)->Unit,category:String,onCategory:(String)->Unit,
    sort:String,onSort:(String)->Unit,
    fromYear:Int?,toYear:Int?,onYears:(Int?,Int?)->Unit,
    loading:Boolean,error:String?,onOpen:(Anime)->Unit,onPlay:(Anime)->Unit,
    onMore:()->Unit,onRetry:()->Unit
) {
    var filters by remember {mutableStateOf(false)}
    val visible = if(fromYear==null && toYear==null)catalog else
        catalog.filter{it.year in (fromYear?:1950)..(toYear?:2027)}
    LazyColumn(Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=110.dp)) {
        item {
            Text("Каталог",color=MaterialTheme.colorScheme.onBackground,
                fontSize=30.sp,fontWeight=FontWeight.Medium,
                modifier=Modifier.padding(top=12.dp,bottom=12.dp))
            OutlinedTextField(value=search,onValueChange=onSearch,
                modifier=Modifier.fillMaxWidth(),singleLine=true,shape=CircleShape,
                placeholder={Text("Найти аниме",color=Muted)},
                leadingIcon={Icon(Icons.Rounded.Search,null)},
                trailingIcon=if(search.isNotEmpty()){{IconButton(onClick={onSearch("")}){
                    Icon(Icons.Rounded.Close,"Очистить")}}}else null,
                colors=OutlinedTextFieldDefaults.colors(
                    focusedContainerColor=Panel,unfocusedContainerColor=Panel,
                    focusedTextColor=MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor=MaterialTheme.colorScheme.onSurface,
                    cursorColor=MaterialTheme.colorScheme.primary,
                    focusedBorderColor=MaterialTheme.colorScheme.primary,unfocusedBorderColor=Edge))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={filters=!filters}){
                    Icon(Icons.Rounded.Tune,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if(filters)"Скрыть фильтры" else "Фильтры")
                }
                Spacer(Modifier.weight(1f))
                Text(catalog.size.toString()+" аниме",fontSize=12.sp,color=Muted)
            }
            if(filters){
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp))
                    .background(Panel)) {
                    YearSpectrumPicker(fromYear,toYear,onYears)
                    HorizontalDivider(color=Edge)
                    Row(Modifier.horizontalScroll(rememberScrollState())
                        .padding(horizontal=13.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                        AnimeApi.sorting.forEach{FlatChip(it,it==sort){onSort(it)}}
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState())
                        .padding(horizontal=13.dp,vertical=8.dp),
                        horizontalArrangement=Arrangement.spacedBy(7.dp)){
                        AnimeApi.categories.forEach{FlatChip(it,it==category){onCategory(it)}}
                    }
                }
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    FlatChip(sort,true){filters=true}
                    FlatChip(category,true){filters=true}
                    FlatChip(if(fromYear==null)"Все годы" else fromYear.toString()+" — "+(toYear?:2027),
                        fromYear!=null){filters=true}
                }
            }
        }
        if(error!=null)item{ErrorPanel(error,onRetry)}
        items(visible,key={it.id}){a->PlumAnimeLine(a,library,{onOpen(a)},{onPlay(a)})}
        if(loading)item{
            Box(Modifier.fillMaxWidth().padding(22.dp),contentAlignment=Alignment.Center){
                CircularProgressIndicator(color=Paper,modifier=Modifier.size(23.dp))
            }
        }else if(error==null && visible.isEmpty())item{
            EmptyState(Icons.Rounded.SearchOff,"Ничего не найдено","Измени условия поиска")
        }else if(catalog.isNotEmpty())item{
            OutlinedButton(onClick=onMore,modifier=Modifier.fillMaxWidth().padding(vertical=13.dp),
                shape=CircleShape,border=BorderStroke(1.dp,Edge)){Text("Показать ещё")}
        }
    }
}

@Composable
fun PlumLibraryScreen(library:LocalLibrary,onOpen:(Anime)->Unit,onPlay:(Anime)->Unit){
    var mode by remember{mutableStateOf("Избранное")}
    val records=when(mode){
        "Избранное"->library.entries.filter{it.favorite}
        "Смотрю"->library.entries.filter{it.status=="Смотрю"}
        "Просмотрено"->library.entries.filter{it.status=="Просмотрено"}
        "В планах"->library.entries.filter{it.status=="В планах"}
        else->library.entries.toList()
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
