package ru.example.pitervolley.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.example.pitervolley.data.Game
import ru.example.pitervolley.data.Favorites
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.data.formatDate
import ru.example.pitervolley.data.today
import ru.example.pitervolley.ui.EmptyState
import ru.example.pitervolley.ui.GameCard
import ru.example.pitervolley.ui.GenderChips
import ru.example.pitervolley.ui.ScreenScaffold
import ru.example.pitervolley.ui.SectionTitle
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val RU = Locale.forLanguageTag("ru-RU")
private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val MONTHS = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onOpenGame: (Game) -> Unit) {
    var gender by rememberSaveable { mutableStateOf("all") }
    var divisionId by rememberSaveable { mutableStateOf(0) }
    var monthMode by rememberSaveable { mutableStateOf(false) }
    var monthIso by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }

    val divisionGender = remember { Repo.data.divisions.associate { it.id to it.gender } }
    val favoriteTeams by Favorites.teams.collectAsState()
    val favoritePersons by Favorites.persons.collectAsState()
    val trackedTeamIds = remember(favoriteTeams, favoritePersons) {
        favoriteTeams + favoritePersons.flatMap { key ->
            Repo.teamsOfPerson(Repo.resolvePersonKey(key)).map { it.id }
        }
    }
    val hasFavorites = favoriteTeams.isNotEmpty() || favoritePersons.isNotEmpty()

    val genderDivisions = remember(gender) {
        when (gender) {
            "boys", "girls", "men", "women" -> Repo.data.divisions
                .filter { it.gender == gender }
                .sortedBy { it.birthYear ?: (it.teamLabel ?: 0) }
            else -> emptyList()
        }
    }

    val games = remember(gender, divisionId, trackedTeamIds) {
        Repo.data.games.filter { game ->
            val byGender = when (gender) {
                "all" -> true
                "fav" -> game.homeId in trackedTeamIds || game.awayId in trackedTeamIds
                else -> divisionGender[game.divisionId] == gender
            }
            byGender && (divisionId == 0 || game.divisionId == divisionId)
        }
    }
    val byDate = remember(games) { games.filter { it.date != null }.groupBy { it.date!! } }
    val undated = remember(games) {
        games.filter { it.date == null }.sortedWith(compareBy({ it.divisionId }, { it.round ?: 0 }))
    }

    val month = YearMonth.parse(monthIso)
    val monthCounts = remember(monthIso, byDate) {
        byDate.entries
            .filter { it.key.startsWith(monthIso) }
            .associate { it.key.substring(8).toInt() to it.value.size }
    }

    ScreenScaffold(
        title = "Календарь",
        subtitle = "Сезон ${Repo.data.season}",
        actions = {
            IconButton(onClick = { monthMode = !monthMode }) {
                Icon(
                    imageVector = if (monthMode) Icons.Filled.List else Icons.Filled.DateRange,
                    contentDescription = if (monthMode) "Списком" else "Календарь месяца",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            GenderChips(
                selected = gender,
                onSelect = { gender = it; divisionId = 0 },
                includeFavorites = true,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (genderDivisions.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                ) {
                    item {
                        FilterChip(
                            selected = divisionId == 0,
                            onClick = { divisionId = 0 },
                            label = { Text("Все", style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                    items(genderDivisions.size) { index ->
                        val division = genderDivisions[index]
                        FilterChip(
                            selected = divisionId == division.id,
                            onClick = { divisionId = division.id },
                            label = {
                                Text(
                                    text = division.birthYear?.toString()
                                        ?: division.teamLabel?.let { "$it-я" }.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }
            }

            if (gender == "fav" && !hasFavorites) {
                EmptyState(
                    "Здесь появятся игры ваших команд и игроков.\n" +
                        "Откройте команду или игрока и нажмите ☆ — они добавятся в избранное.",
                )
            } else {
                androidx.compose.runtime.key(monthMode) {
                if (monthMode) {
                    MonthView(
                        month = month,
                        counts = monthCounts,
                        selectedDay = selectedDay?.takeIf { it.startsWith(monthIso) }?.substring(8)?.toIntOrNull(),
                        onSelectDay = { day ->
                            selectedDay = monthIso + "-" + day.toString().padStart(2, '0')
                        },
                        onPrev = { monthIso = month.minusMonths(1).toString() },
                        onNext = { monthIso = month.plusMonths(1).toString() },
                        onToday = {
                            monthIso = YearMonth.now().toString()
                            selectedDay = today().toString()
                        },
                        byDate = byDate,
                        onOpenGame = onOpenGame,
                    )
                } else {
                    ListView(byDate = byDate, undated = undated, onOpenGame = onOpenGame)
                }
                }
            }
        }
    }
}

@Composable
private fun ListView(byDate: Map<String, List<Game>>, undated: List<Game>, onOpenGame: (Game) -> Unit) {
    val dates = remember(byDate) { byDate.keys.sorted() }
    if (dates.isEmpty() && undated.isEmpty()) {
        EmptyState("По выбранным фильтрам игр нет")
        return
    }
    val todayIso = today().toString()
    val initialIndex = remember(dates) {
        var index = 0
        for (d in dates) {
            if (d >= todayIso) break
            index += 1 + (byDate[d]?.size ?: 0)
        }
        index
    }
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(dates) {
        if (initialIndex > 0) state.scrollToItem(initialIndex)
    }

    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        dates.forEach { date ->
            item(key = "h-$date") {
                Text(
                    text = formatDate(date, withYear = true).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                )
            }
            items(byDate[date].orEmpty(), key = { "${it.divisionId}-${it.id}" }) { game ->
                GameCard(game, onClick = { onOpenGame(game) }, showDivision = true, showDate = false)
            }
        }
        if (undated.isNotEmpty()) {
            item {
                Text(
                    text = "Дата не назначена · ${undated.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
                )
            }
            item {
                Text(
                    text = "Федерация опубликует дату позже — игры появятся в календаре автоматически.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(undated, key = { "u-${it.divisionId}-${it.id}" }) { game ->
                GameCard(game, onClick = { onOpenGame(game) }, showDivision = true)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MonthView(
    month: YearMonth,
    counts: Map<Int, Int>,
    selectedDay: Int?,
    onSelectDay: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    byDate: Map<String, List<Game>>,
    onOpenGame: (Game) -> Unit,
) {
    val monthTitle = MONTHS[month.monthValue - 1] + " " + month.year
    val selectedIso = selectedDay?.let { month.atDay(it).toString() }
    val dayGames = selectedIso?.let { byDate[it] }.orEmpty().sortedBy { it.time }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPrev) {
                            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
                        }
                        Text(
                            text = monthTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onNext) {
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        WEEKDAYS.forEach { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                            )
                        }
                    }
                    MonthGrid(
                        month = month,
                        counts = counts,
                        selectedDay = selectedDay,
                        onSelectDay = onSelectDay,
                    )
                    TextButton(onClick = onToday, modifier = Modifier.align(Alignment.End)) {
                        Text("Сегодня", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        item {
            SectionTitle(
                text = if (selectedIso != null) {
                    "Игры ${formatDate(selectedIso, withYear = false)}"
                } else {
                    "Выберите день с отметкой"
                },
            )
        }

        if (selectedIso != null && dayGames.isEmpty()) {
            item { EmptyState("В этот день игр нет") }
        }
        items(dayGames, key = { "${it.divisionId}-${it.id}" }) { game ->
            GameCard(game, onClick = { onOpenGame(game) }, showDivision = true)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    counts: Map<Int, Int>,
    selectedDay: Int?,
    onSelectDay: (Int) -> Unit,
) {
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val cells = firstOffset + days
    val rows = (cells + 6) / 7
    val todayDay = today().takeIf { YearMonth.from(it) == month }?.dayOfMonth

    Column {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val day = cellIndex - firstOffset + 1
                    Box(Modifier.weight(1f).height(46.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..days) {
                            DayCell(
                                day = day,
                                count = counts[day] ?: 0,
                                selected = day == selectedDay,
                                isToday = day == todayDay,
                                onClick = { onSelectDay(day) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    count: Int,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    else -> MaterialTheme.colorScheme.surface
                },
            )
            .clickable(onClick = onClick)
            .padding(top = 5.dp),
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> MaterialTheme.colorScheme.onPrimary
                isToday -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 3.dp)) {
            repeat(minOf(count, 3)) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary,
                        ),
                )
            }
        }
    }
}
