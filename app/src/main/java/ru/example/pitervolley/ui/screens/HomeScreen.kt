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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.example.pitervolley.data.Division
import ru.example.pitervolley.data.Favorites
import ru.example.pitervolley.data.Game
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.data.SiteSync
import ru.example.pitervolley.ui.ClickableRow
import ru.example.pitervolley.ui.GameCard
import ru.example.pitervolley.ui.ScreenScaffold
import ru.example.pitervolley.ui.SectionTitle
import ru.example.pitervolley.ui.StatChip
import ru.example.pitervolley.ui.TeamLogo
import ru.example.pitervolley.ui.UpdateBar

@Composable
fun HomeScreen(
    onOpenGame: (Game) -> Unit,
    onOpenDivision: (Int) -> Unit,
    onOpenDivisions: () -> Unit,
    onOpenTeam: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenVenues: () -> Unit,
) {
    val revision by Repo.revision.collectAsState()
    val favoriteIds by Favorites.teams.collectAsState()
    val ds = remember(revision) { Repo.data }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    val favoriteTeams = remember(revision, favoriteIds) { favoriteIds.mapNotNull { Repo.team(it) } }
    val favoriteGames = remember(revision, favoriteIds) {
        val start = ru.example.pitervolley.data.today()
        ds.games
            .filter {
                (it.homeId in favoriteIds || it.awayId in favoriteIds) &&
                    it.localDate != null && !it.localDate!!.isBefore(start)
            }
            .sortedWith(compareBy({ it.localDate }, { it.time }))
            .take(5)
    }
    val upcoming = remember(revision) { Repo.upcoming(12) }

    ScreenScaffold(
        title = "PiterVolley",
        subtitle = "Сезон ${ds.season}",
        actions = {
            TextButton(onClick = onOpenSearch) {
                Text("Поиск", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatChip(ds.divisions.size.toString(), "дивизионы", Modifier.weight(1f))
                        StatChip(ds.teams.size.toString(), "команды", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatChip(ds.players.size.toString(), "игроки", Modifier.weight(1f))
                        StatChip(ds.games.count { it.hasResult }.toString(), "сыграно", Modifier.weight(1f))
                    }
                }
            }

            item {
                UpdateBar(
                    subtitle = SiteSync.lastSyncDateText(context) ?: "ещё не обновляли",
                    refreshing = refreshing,
                    isError = syncMessage != null,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            syncMessage = null
                            val result = SiteSync.refresh(context, force = true)
                            refreshing = false
                            syncMessage = if (result.isSuccess) null else "не удалось обновить"
                        }
                    },
                )
            }

            if (favoriteTeams.isNotEmpty()) {
                item { SectionTitle("Мои команды") }
                items(favoriteTeams, key = { "fav-${it.id}" }) { team ->
                    ClickableRow({ onOpenTeam(team.id) }) {
                        TeamLogo(team, 36)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            team.name,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("›", style = MaterialTheme.typography.titleLarge)
                    }
                }
                if (favoriteGames.isNotEmpty()) {
                    item { SectionTitle("Их игры") }
                    items(favoriteGames, key = { "fg-${it.divisionId}-${it.id}" }) { game ->
                        GameCard(game, onClick = { onOpenGame(game) }, showDivision = true)
                    }
                }
            }

            item { SectionTitle("Ближайшие игры") }
            if (upcoming.isEmpty()) {
                item { ru.example.pitervolley.ui.EmptyState("Ближайших игр пока нет") }
            }
            items(upcoming, key = { "${it.divisionId}-${it.id}" }) { game ->
                GameCard(game, onClick = { onOpenGame(game) }, showDivision = true)
            }

            item { SectionTitle("Залы") }
            item {
                val venuesCount = remember(revision) {
                    ds.games.map { it.venue }.filter { it.isNotBlank() }.distinct().size
                }
                Card(
                    onClick = onOpenVenues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Адреса игр", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "$venuesCount залов · список игр по каждому",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionTitle("Соревнования") }
            items(Repo.divisionsByCompetition().entries.toList()) { entry ->
                val divisions = entry.value
                val games = divisions.sumOf { d -> Repo.gamesOf(d.id).size }
                Card(
                    onClick = onOpenDivisions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(entry.key, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${divisions.size} дивизионов · $games игр",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionTitle("По годам рождения") }
            items(ds.divisions.sortedBy { it.birthYear ?: 0 }) { division ->
                DivisionRow(division) { onOpenDivision(division.id) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun DivisionRow(division: Division, onClick: () -> Unit) {
    ClickableRow(onClick) {
        Column(Modifier.weight(1f)) {
            Text(division.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
        Text(
            text = "${Repo.teamsOf(division.id).size} к.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(1.dp))
    ru.example.pitervolley.ui.DividerLine()
}
