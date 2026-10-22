package ru.example.pitervolley.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.example.pitervolley.data.Game
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.data.StandingRow
import ru.example.pitervolley.ui.ClickableRow
import ru.example.pitervolley.ui.DividerLine
import ru.example.pitervolley.ui.EmptyState
import ru.example.pitervolley.ui.GameCard
import ru.example.pitervolley.ui.ScreenScaffold
import ru.example.pitervolley.ui.TeamLogo

private val TABS = listOf("Таблица", "Игры", "Команды")

@Composable
fun DivisionScreen(
    divisionId: Int,
    onBack: () -> Unit,
    onOpenGame: (Game) -> Unit,
    onOpenTeam: (String) -> Unit,
) {
    val division = Repo.division(divisionId)
    if (division == null) {
        ScreenScaffold(title = "Дивизион", onBack = onBack) { p ->
            Column(Modifier.padding(p)) { EmptyState("Дивизион не найден") }
        }
        return
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val standings = remember(divisionId) { Repo.standings(divisionId) }
    val games = remember(divisionId) { Repo.gamesOf(divisionId) }
    val teams = remember(divisionId) { Repo.teamsOf(divisionId) }

    ScreenScaffold(
        title = division.competition,
        subtitle = division.subtitle,
        onBack = onBack,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TABS.forEachIndexed { index, label ->
                    FilterChip(
                        selected = tab == index,
                        onClick = { tab = index },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            when (tab) {
                0 -> StandingsTab(standings, onOpenTeam)
                1 -> GamesTab(games, onOpenGame)
                else -> TeamsTab(teams, onOpenTeam)
            }
        }
    }
}

@Composable
private fun StandingsTab(rows: List<StandingRow>, onOpenTeam: (String) -> Unit) {
    if (rows.isEmpty()) {
        EmptyState("Таблица пуста")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#", Modifier.width(22.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Команда",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf("И", "В", "П", "Оч", "Пар").forEach {
                    Text(
                        it,
                        Modifier.width(if (it == "Пар") 46.dp else 26.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            DividerLine()
        }
        items(rows, key = { it.team.id }) { row ->
            ClickableRow({ onOpenTeam(row.team.id) }) {
                Text(
                    text = (rows.indexOf(row) + 1).toString(),
                    Modifier.width(22.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TeamLogo(row.team, 22)
                Spacer(Modifier.width(8.dp))
                Text(
                    row.team.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(row.played.toString(), Modifier.width(26.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text(row.wins.toString(), Modifier.width(26.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text(row.losses.toString(), Modifier.width(26.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text(
                    row.points.toString(),
                    Modifier.width(26.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(row.setsText, Modifier.width(46.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            DividerLine()
        }
    }
}

@Composable
private fun GamesTab(games: List<Game>, onOpenGame: (Game) -> Unit) {
    if (games.isEmpty()) {
        EmptyState("Игр нет")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(games, key = { "${it.divisionId}-${it.id}" }) { game ->
            GameCard(game, onClick = { onOpenGame(game) })
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TeamsTab(teams: List<ru.example.pitervolley.data.Team>, onOpenTeam: (String) -> Unit) {
    if (teams.isEmpty()) {
        EmptyState("Команд нет")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(teams, key = { it.id }) { team ->
            ClickableRow({ onOpenTeam(team.id) }) {
                TeamLogo(team, 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(team.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${Repo.playersOf(team.id).size} игроков",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", style = MaterialTheme.typography.titleLarge)
            }
            DividerLine()
        }
    }
}
