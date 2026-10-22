package ru.example.pitervolley.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.example.pitervolley.data.Game
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.data.formatDate
import ru.example.pitervolley.ui.ClickableRow
import ru.example.pitervolley.ui.DividerLine
import ru.example.pitervolley.ui.EmptyState
import ru.example.pitervolley.ui.GameCard
import ru.example.pitervolley.ui.ScreenScaffold

@Composable
fun VenuesScreen(onBack: () -> Unit, onOpenVenue: (String) -> Unit) {
    val venues = remember {
        Repo.data.games
            .map { it.venue }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
    }

    ScreenScaffold(title = "Залы", subtitle = "${venues.size} адресов сезона ${Repo.data.season}", onBack = onBack) { padding ->
        if (venues.isEmpty()) {
            Column(Modifier.padding(padding)) { EmptyState("Адресов нет") }
            return@ScreenScaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(venues, key = { it.key }) { entry ->
                ClickableRow({ onOpenVenue(entry.key) }) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.key, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${entry.value} игр",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge)
                }
                DividerLine()
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun VenueScreen(venue: String, onBack: () -> Unit, onOpenGame: (Game) -> Unit) {
    val games = remember(venue) {
        Repo.data.games
            .filter { it.venue == venue }
            .sortedWith(compareBy({ it.localDate == null }, { it.localDate }, { it.time }))
    }

    ScreenScaffold(title = venue, subtitle = "${games.size} игр", onBack = onBack) { padding ->
        if (games.isEmpty()) {
            Column(Modifier.padding(padding)) { EmptyState("Игр нет") }
            return@ScreenScaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(games, key = { "${it.divisionId}-${it.id}" }) { game ->
                GameCard(game, onClick = { onOpenGame(game) }, showDivision = true)
            }
            item { Spacer(Modifier.height(24.dp)) }
            item {
                Text(
                    text = "Игры отсортированы по дате; матчи без даты — в конце.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
fun VenueHeaderHint(date: String?) {
    Text(formatDate(date, withYear = true), style = MaterialTheme.typography.labelSmall)
}
