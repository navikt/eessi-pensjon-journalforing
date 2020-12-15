package no.nav.eessi.pensjon.models.sed

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.SedType
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class DocumentTest {


    @Test
    fun `Verify serde from file`() {
        val json = javaClass.getResource("/fagmodul/alldocumentsids.json").readText()
        val documents = mapJsonToAny(json, typeRefs<List<Document>>())

        assertEquals(3, documents.size)
        assertEquals(1, documents.count { it.validStatus() })
    }

    @Test
    fun `Verify document status empty`() {
        val doc = deserializeDoc("1", "P2000", "empty")

        assertEquals("1", doc.id)
        assertEquals(SedType.P2000, doc.type)
        assertEquals(DocStatus.EMPTY, doc.status)
        assertFalse(doc.validStatus())
    }

    @Test
    fun `Verify document status sent`() {
        val doc = deserializeDoc("2", "P8000", "sent")

        assertEquals("2", doc.id)
        assertEquals(SedType.P8000, doc.type)
        assertEquals(DocStatus.SENT, doc.status)
        assertTrue(doc.validStatus())
    }

    @Test
    fun `Verify document status received`() {
        val doc = deserializeDoc("3", "P2100", "received")

        assertEquals("3", doc.id)
        assertEquals(SedType.P2100, doc.type)
        assertEquals(DocStatus.RECEIVED, doc.status)
        assertTrue(doc.validStatus())
    }

    @Test
    fun `Verify document status null`() {
        val doc = deserializeDoc("4", "P15000", null)

        assertEquals("4", doc.id)
        assertEquals(SedType.P15000, doc.type)
        assertNull(doc.status)
        assertFalse(doc.validStatus())
    }

    @Test
    fun `Document deserialized OK with SedType null`() {
        val doc = deserializeDoc("1", null, "received")

        assertEquals("1", doc.id)
        assertNull(doc.type)
        assertNotNull(doc.status)
        assertTrue(doc.validStatus())
    }

    @Test
    fun `Document deserialized OK with invalid SedType`() {
        val doc = deserializeDoc("1", "XXX12345", "received")

        assertEquals("1", doc.id)
        assertNull(doc.type)
        assertNotNull(doc.status)
        assertTrue(doc.validStatus())
    }

    @ParameterizedTest
    @EnumSource(SedType::class)
    fun `Test with all SedTypes`(sedType: SedType) {
        val doc = deserializeDoc("4", sedType.name, null)

        assertEquals("4", doc.id)
        assertEquals(sedType, doc.type)
        assertNull(doc.status)
        assertFalse(doc.validStatus())
    }

    private fun deserializeDoc(id: String, type: String?, status: String?): Document {
        val json = """
            {
                "id":"$id",
                "type": ${type?.let { "\"$it\"" }},
                "status": ${status?.let { "\"$it\"" }}
            }
        """.trimIndent()

        return mapJsonToAny(json, typeRefs())
    }
}
