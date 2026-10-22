package ru.example.pitervolley.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.example.pitervolley.data.Game
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.data.formatDate
import ru.example.pitervolley.data.today
import ru.example.pitervolley.ui.EmptyState
import ru.example.pitervolley.ui.GameCard
import ru.example.pitervolley.ui.ScreenScaffold

private val GENDER_FILTERS = listOf(
    "all" to "Все",
    "boys" to "Юноши",
    "girls" to "Девушки",
    "men" to "Мужчины",
    "women" to "Женщины",
)

@Composable
fun CalendarScreen(onOpenGame: (Game) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("all") }

    val groups: Map<String, List<Game>> = remember(filter) {
        val divisionGender = Repo.data.divisions.associate { it.id to it.gender }
        Repo.calendarGames()
            .filter { filter == "all" || divisionGender[it.divisionId] == filter }
            .groupBy { it.date ?: "" }
            .toSortedMap()
    }

    val initialIndex = remember(filter, groups) {
        val todayIso = today().toString()
        var index = 0
        for ((date, games) in groups) {
            if (date >= todayIso) break
            index += 1 + games.size
        }
        index
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(filter, groups) {
        if (initialIndex > 0) listState.scrollToItem(initialIndex)
    }

    ScreenScaffold(title = "Календарь", subtitle = "Игры сезона ${Repo.data.season}") { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            ) {
                items(GENDER_FILTERS) { pair ->
                    FilterChip(
                        selected = filter == pair.first,
                        onClick = { filter = pair.first },
                        label = { Text(pair.second, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            if (groups.isEmpty()) {
                EmptyState("Игр нет")
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    groups.forEach { (date, games) ->
                        item(key = "h-$date") {
                            Text(
                                text = formatDate(date, withYear = true).replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(games, key = { "${it.divisionId}-${it.id}" }) { game ->
                            GameCard(game, onClick = { onOpenGame(game) }, showDivision = true)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
