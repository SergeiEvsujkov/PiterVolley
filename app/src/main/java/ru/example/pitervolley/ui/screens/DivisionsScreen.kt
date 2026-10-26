package ru.example.pitervolley.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import ru.example.pitervolley.data.Division
import ru.example.pitervolley.data.Favorites
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.ui.EmptyState
import ru.example.pitervolley.ui.GenderChips
import ru.example.pitervolley.ui.ScreenScaffold
import ru.example.pitervolley.ui.genderColor

@Composable
fun DivisionsScreen(onOpenDivision: (Int) -> Unit) {
    var gender by rememberSaveable { mutableStateOf(ru.example.pitervolley.data.UiState.divisionsGender) }
    var year by rememberSaveable { mutableStateOf(ru.example.pitervolley.data.UiState.divisionsYear) }
    androidx.compose.runtime.LaunchedEffect(gender, year) {
        ru.example.pitervolley.data.UiState.divisionsGender = gender
        ru.example.pitervolley.data.UiState.divisionsYear = year
    }

    val favoriteTeams by Favorites.teams.collectAsState()
    val favoritePersons by Favorites.persons.collectAsState()
    val tracked = remember(favoriteTeams, favoritePersons) { Favorites.trackedTeamIds() }
    val favoriteDivisionIds = remember(tracked) {
        Repo.data.teams.filter { it.id in tracked }.map { it.divisionId }.toSet()
    }

    val divisions = remember(gender, favoriteDivisionIds) {
        when (gender) {
            "fav" -> Repo.data.divisions.filter { it.id in favoriteDivisionIds }
            "all" -> Repo.data.divisions
            else -> Repo.data.divisions.filter { it.gender == gender }
        }
    }
    val years = remember(divisions, gender) {
        if (gender == "boys" || gender == "girls") divisions.mapNotNull { it.birthYear }.distinct().sorted()
        else emptyList()
    }
    val shown = remember(gender, year, divisions) {
        val genderRank = mapOf("boys" to 0, "girls" to 1, "men" to 2, "women" to 3)
        divisions.filter { year == 0 || it.birthYear == year }
            .sortedWith(
                compareBy(
                    { if (it.competition.contains("Чемпионат", ignoreCase = true)) 1 else 0 },
                    { genderRank[it.gender] ?: 4 },
                    { it.birthYear ?: 0 },
                    { it.teamLabel ?: 0 },
                ),
            )
    }

    ScreenScaffold(
        title = "Дивизионы",
        subtitle = "Сезон ${Repo.data.season}",
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            GenderChips(
                selected = gender,
                onSelect = { gender = it; year = 0 },
                includeAll = true,
                includeFavorites = true,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            if (years.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    item {
                        FilterChip(
                            selected = year == 0,
                            onClick = { year = 0 },
                            label = { Text("Все годы", style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                        )
                    }
                    items(years) { y ->
                        FilterChip(
                            selected = year == y,
                            onClick = { year = y },
                            label = { Text("$y", style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                        )
                    }
                }
            }

            if (shown.isEmpty()) {
                EmptyState(
                    if (gender == "fav") "Пока ничего не выбрано. Добавьте команду или игрока звёздочкой — их дивизионы появятся здесь."
                    else "Дивизионов нет",
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(shown, key = { it.id }) { division ->
                        DivisionCard(division) { onOpenDivision(division.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DivisionCard(division: Division, onClick: () -> Unit) {
    val teams = Repo.teamsOf(division.id)
    val games = Repo.gamesOf(division.id)
    val played = games.count { it.hasResult }
    val accent = genderColor(division.gender)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = division.subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = division.competition,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = accent)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ru.example.pitervolley.data.countTeams(teams.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text("·", color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(10.dp))
                Text(
                    "$played из ${ru.example.pitervolley.data.countGames(games.size)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            val fraction = if (games.isEmpty()) 0f else played.toFloat() / games.size
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent),
                )
            }
        }
    }
}
