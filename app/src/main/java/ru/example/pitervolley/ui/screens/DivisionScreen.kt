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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import ru.example.pitervolley.data.ClubRow
import ru.example.pitervolley.data.Game
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.data.StandingRow
import ru.example.pitervolley.ui.ClickableRow
import ru.example.pitervolley.ui.DividerLine
import ru.example.pitervolley.ui.EmptyState
import ru.example.pitervolley.ui.GameCard
import ru.example.pitervolley.ui.ScreenScaffold
import ru.example.pitervolley.ui.TeamLogo

private val PREMIER_TABS = listOf("Таблица", "Игры", "Команды")
private val CHAMPIONSHIP_TABS = listOf("Таблица", "Общая", "Игры", "Команды")

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
    val isChampionship = Repo.isChampionship(division)
    val tabs = if (isChampionship) CHAMPIONSHIP_TABS else PREMIER_TABS
    val revision = Repo.revision.collectAsState().value
    val standings = remember(divisionId, revision) { Repo.standings(divisionId) }
    val combined = remember(divisionId, isChampionship) {
        if (isChampionship) Repo.combinedStandings(divisionId) else emptyList()
    }
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
                tabs.forEachIndexed { index, label ->
                    FilterChip(
                        selected = tab == index,
                        onClick = { tab = index },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            when (tabs.getOrNull(tab) ?: "Таблица") {
                "Таблица" -> StandingsTab(standings, onOpenTeam, isChampionship)
                "Общая" -> CombinedTab(combined, onOpenTeam)
                "Игры" -> GamesTab(games, onOpenGame)
                else -> TeamsTab(teams, onOpenTeam)
            }
        }
    }
}

@Composable
private fun StandingsTab(rows: List<StandingRow>, onOpenTeam: (String) -> Unit, championship: Boolean) {
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
                        Modifier.widthIn(min = if (it == "Пар") 46.dp else 26.dp),
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
                Column(Modifier.weight(1f)) {
                    Text(
                        row.team.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (row.note.isNotBlank()) {
                        Text(
                            row.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(row.played.toString(), Modifier.widthIn(min = 26.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text(row.wins.toString(), Modifier.widthIn(min = 26.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text(row.losses.toString(), Modifier.widthIn(min = 26.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text(
                    row.points.toString(),
                    Modifier.widthIn(min = 26.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(row.setsText, Modifier.widthIn(min = 46.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            DividerLine()
        }
        item {
            Text(
                text = if (championship) {
                    "Места: победы → очки → партии → мячи → личные встречи.\n" +
                        "Очки: у первой команды 6 за 3:0–3:1, 4 за 3:2, 2 за 2:3; у второй — 3, 2, 1."
                } else {
                    "Места: победы → очки → партии → мячи → личные встречи.\n" +
                        "Очки: 3 за 3:0–3:1, 2 за 3:2, 1 за поражение 2:3."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun CombinedTab(rows: List<ClubRow>, onOpenTeam: (String) -> Unit) {
    if (rows.isEmpty()) {
        EmptyState("Общая таблица пуста")
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
                    "Коллектив",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf("И", "В", "П", "Оч", "Пар").forEach {
                    Text(
                        it,
                        Modifier.widthIn(min = if (it == "Пар") 46.dp else 26.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            DividerLine()
        }
        itemsIndexed(rows, key = { _, row -> row.name }) { index, row ->
            ClickableRow({ row.teams.firstOrNull()?.let { onOpenTeam(it.id) } }) {
                Text(
                    text = (index + 1).toString(),
                    Modifier.width(22.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TeamLogo(row.teams.firstOrNull(), 22)
                Spacer(Modifier.width(8.dp))
                Text(
                    row.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(row.played.toString(), Modifier.widthIn(min = 26.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text(row.wins.toString(), Modifier.widthIn(min = 26.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text(row.losses.toString(), Modifier.widthIn(min = 26.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text(
                    row.points.toString(),
                    Modifier.widthIn(min = 26.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(row.setsText, Modifier.widthIn(min = 46.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            DividerLine()
        }
        item {
            Text(
                text = "Сумма показателей 1-й и 2-й команд коллектива.\n" +
                    "Очки: у первой команды 6 за 3:0–3:1, 4 за 3:2, 2 за 2:3; у второй — 3, 2, 1.\n" +
                    "Места: победы → очки → личные встречи → партии → мячи.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun GamesTab(games: List<Game>, onOpenGame: (Game) -> Unit) {
    if (games.isEmpty()) {
        EmptyState("Игр нет")
        return
    }
    val dated = games.filter { it.date != null }
    val undated = games.filter { it.date == null }
    LazyColumn(Modifier.fillMaxSize()) {
        items(dated, key = { "${it.divisionId}-${it.id}" }) { game ->
            GameCard(game, onClick = { onOpenGame(game) })
        }
        if (undated.isNotEmpty()) {
            item {
                ru.example.pitervolley.ui.SectionTitle("Дата не назначена · ${undated.size}")
            }
            items(undated, key = { "u-${it.divisionId}-${it.id}" }) { game ->
                GameCard(game, onClick = { onOpenGame(game) })
            }
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
                        ru.example.pitervolley.data.countPlayers(Repo.playersOf(team.id).size),
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
