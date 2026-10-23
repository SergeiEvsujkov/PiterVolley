package ru.example.pitervolley

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.example.pitervolley.data.VenueNormalizer

class VenueNormalizerTest {

    @Test
    fun `comma and spaces do not create a new venue`() {
        assertEquals(
            VenueNormalizer.normalize("пр. Энгельса д.23"),
            VenueNormalizer.normalize("пр. Энгельса, д. 23"),
        )
        assertEquals(
            VenueNormalizer.key("пр. Энгельса д.23"),
            VenueNormalizer.key("пр. Энгельса, д. 23"),
        )
    }

    @Test
    fun `vo spelling is unified`() {
        assertEquals(
            VenueNormalizer.normalize("Малый пр. ВО, д.66"),
            VenueNormalizer.normalize("Малый пр. В.О., д. 66"),
        )
    }

    @Test
    fun `house letter is attached to number`() {
        assertEquals(
            VenueNormalizer.normalize("Приморский пр. д. 50 Б"),
            VenueNormalizer.normalize("Приморский пр., д.50Б"),
        )
    }

    @Test
    fun `street names are not damaged by abbreviation rules`() {
        assertEquals(
            "Подвойского, д.31, стр. 2",
            VenueNormalizer.normalize("Подвойского, д. 31, стр. 2"),
        )
    }

    @Test
    fun `empty and placeholder addresses stay empty`() {
        assertEquals("", VenueNormalizer.normalize("..."))
        assertEquals("", VenueNormalizer.normalize("  "))
    }
}
