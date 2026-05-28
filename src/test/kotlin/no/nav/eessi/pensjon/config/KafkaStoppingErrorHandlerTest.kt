package no.nav.eessi.pensjon.config

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class KafkaStoppingErrorHandlerTest {

    private val errorHandler = KafkaStoppingErrorHandler()

    @Test
    fun `textListingOf maskerer fnr i record output`() {
        val records = listOf(
            ConsumerRecord("topic", 0, 1L, "key", "fnr 12345678910"),
            ConsumerRecord("topic", 0, 2L, "key", "annen fnr 10987654321")
        )

        val listing = errorHandler.textListingOf(records)

        assertTrue(listing.contains("***"))
        assertFalse(listing.contains("12345678910"))
        assertFalse(listing.contains("10987654321"))
        assertEquals(2, "-".repeat(20).toRegex().findAll(listing).count())
    }

    @Test
    fun `textListingOf beholder tall som ikke matcher fnr format`() {
        val record = ConsumerRecord("topic", 0, 1L, "key", "kort 1234567890 og lang x12345678910y")

        val listing = errorHandler.textListingOf(listOf(record))

        assertTrue(listing.contains("1234567890"))
        assertTrue(listing.contains("x12345678910y"))
    }

    @Test
    fun `textListingOf returnerer tom streng for tom record liste`() {
        assertEquals("", errorHandler.textListingOf(emptyList()))
    }
}
