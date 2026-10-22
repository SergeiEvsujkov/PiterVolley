package ru.example.pitervolley.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.example.pitervolley.ui.screens.CalendarScreen
import ru.example.pitervolley.ui.screens.DivisionScreen
import ru.example.pitervolley.ui.screens.DivisionsScreen
import ru.example.pitervolley.ui.screens.GameScreen
import ru.example.pitervolley.ui.screens.HomeScreen
import ru.example.pitervolley.ui.screens.PlayerScreen
import ru.example.pitervolley.ui.screens.SearchScreen
import ru.example.pitervolley.ui.screens.SettingsScreen
import ru.example.pitervolley.ui.screens.TeamScreen
import ru.example.pitervolley.ui.screens.VenueScreen
import ru.example.pitervolley.ui.screens.VenuesScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Главная", Icons.Filled.Home),
    Tab("calendar", "Календарь", Icons.Filled.DateRange),
    Tab("divisions", "Дивизионы", Icons.Filled.List),
)

@Composable
fun PiterVolleyApp(startRoute: String? = null, startQuery: String? = null) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route == "home" || route == "calendar" || route == "divisions"

    Scaffold(
        bottomBar = {
            if (showBar) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TABS.forEach { tab ->
                        val selected = route == tab.route
                        TextButton(
                            onClick = {
                                if (!selected) {
                                    // Без saveState/restoreState: восстановление стека
                                    // иногда возвращало на предыдущую вкладку вместо нужной.
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                            },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = startRoute ?: "home",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    onOpenGame = { nav.navigate("game/${it.divisionId}/${it.id}") },
                    onOpenDivision = { nav.navigate("division/$it") },
                    onOpenDivisions = {
                        nav.navigate("divisions") {
                            popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenTeam = { nav.navigate("team/$it") },
                    onOpenPlayer = { nav.navigate("player/$it") },
                    onOpenSearch = { nav.navigate("search") },
                    onOpenVenues = { nav.navigate("venues") },
                    onOpenCalendar = {
                        nav.navigate("calendar") {
                            popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = { nav.navigate("settings") },
                )
            }
            composable("calendar") {
                CalendarScreen(onOpenGame = { nav.navigate("game/${it.divisionId}/${it.id}") })
            }
            composable("divisions") {
                DivisionsScreen(onOpenDivision = { nav.navigate("division/$it") })
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
            composable("search") {                SearchScreen(
                    onBack = { nav.popBackStack() },
                    onOpenTeam = { nav.navigate("team/$it") },
                    onOpenPlayer = { nav.navigate("player/$it") },
                    onOpenDivision = { nav.navigate("division/$it") },
                    initialQuery = startQuery.orEmpty(),
                )
            }
            composable("venues") {
                VenuesScreen(
                    onBack = { nav.popBackStack() },
                    onOpenVenue = { nav.navigate("venue/${android.net.Uri.encode(it)}") },
                )
            }
            composable(
                "venue/{venue}",
                arguments = listOf(navArgument("venue") { type = NavType.StringType }),
            ) { entry ->
                VenueScreen(
                    venue = entry.arguments?.getString("venue").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenGame = { nav.navigate("game/${it.divisionId}/${it.id}") },
                )
            }
            composable(
                "division/{divisionId}",
                arguments = listOf(navArgument("divisionId") { type = NavType.IntType }),
            ) { entry ->
                val id = entry.arguments?.getInt("divisionId") ?: 0
                DivisionScreen(
                    divisionId = id,
                    onBack = { nav.popBackStack() },
                    onOpenGame = { nav.navigate("game/${it.divisionId}/${it.id}") },
                    onOpenTeam = { nav.navigate("team/$it") },
                )
            }
            composable(
                "team/{teamId}",
                arguments = listOf(navArgument("teamId") { type = NavType.StringType }),
            ) { entry ->
                TeamScreen(
                    teamId = entry.arguments?.getString("teamId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenPlayer = { nav.navigate("player/$it") },
                    onOpenGame = { nav.navigate("game/${it.divisionId}/${it.id}") },
                    onOpenDivision = { nav.navigate("division/$it") },
                )
            }
            composable(
                "player/{playerId}",
                arguments = listOf(navArgument("playerId") { type = NavType.StringType }),
            ) { entry ->
                PlayerScreen(
                    playerId = entry.arguments?.getString("playerId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenTeam = { nav.navigate("team/$it") },
                )
            }
            composable(
                "game/{divisionId}/{gameId}",
                arguments = listOf(
                    navArgument("divisionId") { type = NavType.IntType },
                    navArgument("gameId") { type = NavType.StringType },
                ),
            ) { entry ->
                GameScreen(
                    divisionId = entry.arguments?.getInt("divisionId") ?: 0,
                    gameId = entry.arguments?.getString("gameId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenTeam = { nav.navigate("team/$it") },
                    onOpenDivision = { nav.navigate("division/$it") },
                )
            }
        }
    }
}
