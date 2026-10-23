package ru.example.pitervolley.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ru.example.pitervolley.data.Favorites
import ru.example.pitervolley.data.Game
import ru.example.pitervolley.data.PersonOverrides
import ru.example.pitervolley.data.Player
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.ui.DividerLine
import ru.example.pitervolley.ui.EmptyState
import ru.example.pitervolley.ui.GameCard
import ru.example.pitervolley.ui.PlayerAvatar
import ru.example.pitervolley.ui.ScreenScaffold
import ru.example.pitervolley.ui.TeamLogo

@Composable
fun TeamScreen(
    teamId: String,
    onBack: () -> Unit,
    onOpenPlayer: (String) -> Unit,
    onOpenGame: (Game) -> Unit,
    onOpenDivision: (Int) -> Unit,
) {
    val team = Repo.team(teamId)
    if (team == null) {
        ScreenScaffold(title = "Команда", onBack = onBack) { p ->
            Column(Modifier.padding(p)) { EmptyState("Команда не найдена") }
        }
        return
    }
    val division = Repo.division(team.divisionId)
    val players = Repo.playersOf(teamId)
    val games = Repo.gamesOfTeam(teamId)
    val favorites by Favorites.teams.collectAsState()
    val isFavorite = teamId in favorites

    ScreenScaffold(
        title = team.name,
        subtitle = division?.let { "${it.competition} · ${it.subtitle}" },
        onBack = onBack,
        actions = {
            TextButton(onClick = { Favorites.toggle(team.id) }) {
                Text(
                    text = if (isFavorite) "★" else "☆",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!team.logo.isNullOrBlank()) {
                        AsyncImage(
                            model = team.logo,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(team.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (division != null) {
                            Text(
                                division.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Перейти в дивизион ›",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable(onClick = { onOpenDivision(division.id) }),
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Состав · ${players.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (players.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "Заявка ещё не опубликована",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Федерация заполняет составы постепенно — они появляются на pitervolley.ru. " +
                                    "Обновите данные кнопкой «Обновить» на главной позже, и состав подтянется.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(players.chunked(2), key = { it.joinToString { p -> p.id } }) { pair ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { player ->
                        PlayerCardSmall(player, Modifier.weight(1f)) { onOpenPlayer(player.id) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            item {
                Text(
                    "Игры команды",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(games, key = { "${it.divisionId}-${it.id}" }) { game ->
                GameCard(game, onClick = { onOpenGame(game) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PlayerCardSmall(player: Player, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(player.photo, player.name, 40)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    player.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                Text(
                    player.birthYear?.let { "$it г.р." } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun PlayerScreen(playerId: String, onBack: () -> Unit, onOpenTeam: (String) -> Unit) {
    val player = Repo.data.players.firstOrNull { it.id == playerId }
    if (player == null) {
        ScreenScaffold(title = "Игрок", onBack = onBack) { p ->
            Column(Modifier.padding(p)) { EmptyState("Игрок не найден") }
        }
        return
    }
    val revision by Repo.revision.collectAsState()
    val person = remember(revision, playerId) { Repo.person(Repo.personKeyOf(player)) }
    val baseKey = remember(revision, playerId) { Repo.baseKeyOf(player) }
    val teams = person?.teams.orEmpty()
    val favorites by Favorites.persons.collectAsState()
    val isFavorite = person != null && Favorites.isPersonFavorite(person.key)
    var mergeDialog by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = player.name,
        onBack = onBack,
        actions = {
            if (person != null) {
                TextButton(onClick = { Favorites.togglePerson(person.key) }) {
                    Text(
                        text = if (isFavorite) "★" else "☆",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(player.photo, player.name, 120)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(player.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    if (player.birthYear != null) {
                        Text(
                            "${player.birthYear} г.р.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            DividerLine()
            Spacer(Modifier.height(16.dp))
            if (teams.isNotEmpty()) {
                Text(
                    text = if (teams.size > 1) "Заявлен за ${teams.size} команды" else "Команда",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                teams.forEach { team ->
                    val division = Repo.division(team.divisionId)
                    Card(
                        onClick = { onOpenTeam(team.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TeamLogo(team, 44)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(team.name, style = MaterialTheme.typography.bodyLarge)
                                if (division != null) {
                                    Text(
                                        "${division.competition} · ${division.subtitle}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text("›", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                if (teams.size > 1) {
                    Text(
                        text = "Игры всех команд учитываются в общем списке избранного.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (person != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (person.players.size > 1) {
                            OutlinedButton(
                                onClick = {
                                    person.players
                                        .filter { it.id != player.id }
                                        .forEach { PersonOverrides.split(it.id) }
                                    Repo.rebuildPersons()
                                },
                            ) {
                                Text("Это разные люди", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        OutlinedButton(onClick = { mergeDialog = true }) {
                            Text("Связать с игроком", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (person != null && Repo.hasManualEdits(person.key, baseKey)) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Группировка изменена вручную",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Вы разделили или объединили записи этого игрока. От этого зависят " +
                                    "счётчик уникальных игроков, его команды и список игр.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { Repo.resetManualEdits(baseKey) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) {
                                Text("Вернуть как в данных федерации", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Статистика игрока появится, когда федерация начнёт публиковать протоколы.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (mergeDialog && person != null) {
        MergePlayerDialog(
            excludePerson = person.key,
            onDismiss = { mergeDialog = false },
            onPick = { other ->
                PersonOverrides.merge(person.key, Repo.personKeyOf(other))
                Repo.rebuildPersons()
                mergeDialog = false
            },
        )
    }
}

@Composable
private fun MergePlayerDialog(
    excludePerson: String,
    onDismiss: () -> Unit,
    onPick: (Player) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        val q = query.trim().lowercase()
        if (q.length < 2) emptyList()
        else Repo.data.players
            .filter { it.name.lowercase().contains(q) && Repo.personKeyOf(it) != excludePerson }
            .take(25)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Связать с игроком") },
        text = {
            Column {
                Text(
                    "Выберите запись, которая относится к тому же человеку — даже если имя написано иначе.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Фамилия или имя") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(results, key = { "m-${it.id}" }) { candidate ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(candidate) }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(candidate.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(
                                    candidate.birthYear?.let { "$it г.р." },
                                    Repo.team(candidate.teamId)?.name,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
