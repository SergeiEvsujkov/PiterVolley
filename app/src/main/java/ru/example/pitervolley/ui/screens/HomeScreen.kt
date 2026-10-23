package ru.example.pitervolley.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalDensity
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
import ru.example.pitervolley.ui.PlayerAvatar
import ru.example.pitervolley.ui.ScreenScaffold
import ru.example.pitervolley.ui.SectionTitle
import ru.example.pitervolley.ui.StatChip
import ru.example.pitervolley.ui.TeamLogo
import ru.example.pitervolley.ui.UpdateBar
import ru.example.pitervolley.ui.theme.BrandOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenGame: (Game) -> Unit,
    onOpenDivision: (Int) -> Unit,
    onOpenDivisions: () -> Unit,
    onOpenTeam: (String) -> Unit,
    onOpenPlayer: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenVenues: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val revision by Repo.revision.collectAsState()
    val favoriteIds by Favorites.teams.collectAsState()
    val favoritePersonKeys by Favorites.persons.collectAsState()
    val ds = remember(revision) { Repo.data }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    val doRefresh: () -> Unit = {
        scope.launch {
            refreshing = true
            syncMessage = null
            val result = SiteSync.refresh(context, force = true)
            refreshing = false
            syncMessage = if (result.isSuccess) null else "ошибка"
        }
    }

    val favoriteTeams = remember(revision, favoriteIds) { favoriteIds.mapNotNull { Repo.team(it) } }
    val favoritePersons = remember(revision, favoritePersonKeys) {
        favoritePersonKeys.mapNotNull { Repo.person(Repo.resolvePersonKey(it)) }
    }
    val trackedTeamIds = remember(revision, favoriteIds, favoritePersons) {
        favoriteIds + favoritePersons.flatMap { person -> person.teams.map { it.id } }
    }
    val favoriteGames = remember(revision, trackedTeamIds) {
        val start = ru.example.pitervolley.data.today()
        ds.games
            .filter {
                (it.homeId in trackedTeamIds || it.awayId in trackedTeamIds) &&
                    it.localDate != null && !it.localDate!!.isBefore(start)
            }
            .sortedWith(compareBy({ it.localDate }, { it.time }))
            .take(5)
    }
    val upcoming = remember(revision) { Repo.upcoming(6) }
    val played = remember(revision) { ds.games.count { it.hasResult } }
    val venuesCount = remember(revision) { ds.games.map { it.venue }.filter { it.isNotBlank() }.distinct().size }

    ScreenScaffold(
        title = "PiterVolley",
        subtitle = "Волейбол Санкт-Петербурга · сезон ${ds.season}",
        actions = {
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Поиск", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = MaterialTheme.colorScheme.primary)
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = doRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatChip(ds.divisions.size.toString(), "дивизионов", Modifier.weight(1f))
                            StatChip(ds.teams.size.toString(), "команд", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatChip(Repo.personCount().toString(), "игроков", Modifier.weight(1f))
                            StatChip(played.toString(), "сыграно", Modifier.weight(1f))
                        }
                    }
                }

                item {
                    UpdateBar(
                        subtitle = SiteSync.lastSyncDateText(context) ?: "ещё не обновляли",
                        refreshing = refreshing,
                        isError = syncMessage != null,
                        onRefresh = doRefresh,
                    )
                }

                if (favoriteTeams.isNotEmpty()) {
                    item { SectionTitle("Мои команды") }
                    items(favoriteTeams, key = { "fav-${it.id}" }) { team ->
                        ClickableRow({ onOpenTeam(team.id) }) {
                            TeamLogo(team, 36)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    team.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Repo.division(team.divisionId)?.let { division ->
                                    Text(
                                        text = "${division.competition} · ${division.subtitle}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text("›", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }

                if (favoritePersons.isNotEmpty()) {
                    item { SectionTitle("Мои игроки") }
                    item {
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(favoritePersons, key = { "p-${it.key}" }) { person ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(104.dp)
                                        .clickable { onOpenPlayer(person.players.first().id) },
                                ) {
                                    PlayerAvatar(person.photo, person.name, 56)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = person.name.substringBefore(' '),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (person.teams.size > 1) "${person.teams.size} команды" else person.teamNames,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                if (favoriteGames.isNotEmpty()) {
                    item { SectionTitle("Их игры") }
                    items(favoriteGames, key = { "fg-${it.divisionId}-${it.id}" }) { game ->
                        GameCard(game, onClick = { onOpenGame(game) }, showDivision = true)
                    }
                }

                item {
                    SectionTitle(
                        text = "Ближайшие игры",
                        trailing = {
                            TextButton(onClick = onOpenCalendar) {
                                Text("Все игры", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                    )
                }
                if (upcoming.isEmpty()) {
                    item { ru.example.pitervolley.ui.EmptyState("Ближайших игр пока нет") }
                }
                items(upcoming, key = { "${it.divisionId}-${it.id}" }) { game ->
                    GameCard(game, onClick = { onOpenGame(game) }, showDivision = true)
                }

                item { SectionTitle("Соревнования") }
                item {
                    CompetitionCards(
                        onOpenDivisions = onOpenDivisions,
                        onOpenDivision = onOpenDivision,
                    )
                }

                item { SectionTitle("Залы") }
                item {
                    Card(
                        onClick = onOpenVenues,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Place, contentDescription = null, tint = BrandOrange)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Адреса игр", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "$venuesCount залов · расписание по каждому",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("›", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }

                item { SectionTitle("Дивизионы") }
                item {
                    val boys = remember(revision) {
                        ds.divisions.filter { it.gender == "boys" && it.birthYear != null }
                            .sortedBy { it.birthYear }
                    }
                    val girls = remember(revision) {
                        ds.divisions.filter { it.gender == "girls" && it.birthYear != null }
                            .sortedBy { it.birthYear }
                    }
                    val men = remember(revision) {
                        ds.divisions.filter { it.gender == "men" }.sortedBy { it.teamLabel ?: 0 }
                    }
                    val women = remember(revision) {
                        ds.divisions.filter { it.gender == "women" }.sortedBy { it.teamLabel ?: 0 }
                    }
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        YearRow("Юноши", boys, { it.birthYear?.toString().orEmpty() }, onOpenDivision)
                        Spacer(Modifier.height(8.dp))
                        YearRow("Девушки", girls, { it.birthYear?.toString().orEmpty() }, onOpenDivision)
                        Spacer(Modifier.height(8.dp))
                        YearRow("Мужчины", men, { it.teamLabel?.let { n -> "$n-я" }.orEmpty() }, onOpenDivision)
                        Spacer(Modifier.height(8.dp))
                        YearRow("Женщины", women, { it.teamLabel?.let { n -> "$n-я" }.orEmpty() }, onOpenDivision)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun CompetitionCards(onOpenDivisions: () -> Unit, onOpenDivision: (Int) -> Unit) {
    val divisions = Repo.data.divisions
    val championship = divisions.filter { it.birthYear == null }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CompetitionCard(
            title = "Первенство",
            subtitle = "Юноши и девушки",
            count = divisions.count { it.birthYear != null },
            modifier = Modifier.weight(1f),
            onClick = onOpenDivisions,
        )
        CompetitionCard(
            title = "Чемпионат",
            subtitle = "Мужчины и женщины",
            count = championship.size,
            modifier = Modifier.weight(1f),
            onClick = { championship.firstOrNull()?.let { onOpenDivision(it.id) } ?: onOpenDivisions() },
        )
    }
}

@Composable
private fun CompetitionCard(
    title: String,
    subtitle: String,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "$count дивизионов",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun YearRow(
    label: String,
    divisions: List<Division>,
    chipText: (Division) -> String,
    onOpenDivision: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(80.dp)
                .padding(start = 16.dp, top = 10.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(end = 16.dp),
        ) {
            divisions.chunked(if (LocalDensity.current.fontScale > 1.15f) 3 else 4).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chunk.forEach { division ->
                        Card(
                            onClick = { onOpenDivision(division.id) },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Text(
                                text = chipText(division),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }
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
