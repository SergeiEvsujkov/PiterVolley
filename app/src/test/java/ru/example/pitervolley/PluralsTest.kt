package ru.example.pitervolley

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.example.pitervolley.data.plural

class PluralsTest {

    @Test
    fun `russian plural forms`() {
        assertEquals("игра", plural(1, "игра", "игры", "игр"))
        assertEquals("игры", plural(2, "игра", "игры", "игр"))
        assertEquals("игры", plural(4, "игра", "игры", "игр"))
        assertEquals("игр", plural(5, "игра", "игры", "игр"))
        assertEquals("игр", plural(11, "игра", "игры", "игр"))
        assertEquals("игр", plural(12, "игра", "игры", "игр"))
        assertEquals("игра", plural(21, "игра", "игры", "игр"))
        assertEquals("игры", plural(22, "игра", "игры", "игр"))
        assertEquals("игр", plural(111, "игра", "игры", "игр"))
        assertEquals("игр", plural(0, "игра", "игры", "игр"))
    }
}
