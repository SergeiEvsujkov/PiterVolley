package ru.example.pitervolley

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.example.pitervolley.data.Dataset
import ru.example.pitervolley.data.Division
import ru.example.pitervolley.data.Player
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.data.Team

/** Склейка записей об одном игроке из разных команд. */
class PersonMatchingTest {

    private val division = Division(1, "Первенство", "Первенство СПб", "boys", 2010, null)

    private fun setup(players: List<Player>, teams: List<Team>) {
        Repo.apply(Dataset(1, "", "2026-27", listOf(division), teams, players, emptyList(), emptyList()))
    }

    @Test
    fun `key ignores word order and yo letter`() {
        assertEquals(Repo.personKey("Иванов Пётр", 2010), Repo.personKey("Петр Иванов", 2010))
        assertEquals(Repo.personKey("Иванов Пётр", 2010), Repo.personKey("ИВАНОВ ПЁТР", 2010))
        assertNotEquals(Repo.personKey("Иванов Пётр", 2010), Repo.personKey("Иванов Пётр", 2011))
    }

    @Test
    fun `same person in two teams is one person with two teams`() {
        val t1 = Team("t1", "Команда-1", 1)
        val t2 = Team("t2", "Команда-2", 1)
        setup(
            listOf(
                Player("p1", "Иванов Пётр", 2010, null, "t1", 1),
                Player("p2", "Пётр Иванов", 2010, "http://photo2", "t2", 1),
                Player("p3", "Сидоров Иван", 2010, null, "t1", 1),
            ),
            listOf(t1, t2),
        )
        assertEquals(2, Repo.personCount())
        val person = Repo.person(Repo.personKey("Иванов Пётр", 2010))!!
        assertEquals(2, person.players.size)
        assertEquals(2, person.teams.size)
        assertEquals("http://photo2", person.photo)
    }

    @Test
    fun `different people with different birth years stay apart`() {
        val t1 = Team("t1", "Команда-1", 1)
        setup(
            listOf(
                Player("p1", "Иванов Пётр", 2010, null, "t1", 1),
                Player("p2", "Иванов Пётр", 2011, null, "t1", 1),
            ),
            listOf(t1),
        )
        assertEquals(2, Repo.personCount())
        assertTrue(Repo.person(Repo.personKey("Иванов Пётр", 2010))!!.teams.isNotEmpty())
    }
}
