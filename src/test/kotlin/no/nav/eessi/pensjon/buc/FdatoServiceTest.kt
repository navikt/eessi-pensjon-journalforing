package no.nav.eessi.pensjon.buc

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.json.validateJson
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class FdatoServiceTest {

    @Mock
    private lateinit var mockEuxService: EuxService

    @Mock
    private lateinit var mockFagmodulService: FagmodulService

    private lateinit var service: FdatoService

    @BeforeEach
    fun setup() {
        service = FdatoService(mockFagmodulService, mockEuxService)

    }

    @Test
    fun `Calling getFDatoFromSed returns exception when foedselsdato is not found` () {
        val euxCaseId = "123456"
        val mock = listOf(Pair("04117b9f8374420e82a4d980a48df6b3","P2000"))

        doReturn( getTestJsonFile("EmptySED.json") )
                .whenever(mockEuxService)
                .hentSed( eq(euxCaseId), ArgumentMatchers.anyString())

        org.junit.jupiter.api.assertThrows<RuntimeException> {
            service.finnFDatoFraSed( eq(euxCaseId), eq(mock))
        }
    }

    @Test
    fun `Calling getFDatoFromSed returns valid fdato when found in first valid SED` () {
        val mockEuxCaseID = "123123"
        val mock = listOf(Pair("04117b9f8374420e82a4d980a48df6b3","P2100"),Pair("04117b9f8374420e82a4d980a48df6b3","P2100"),
                Pair("04117b9f8374420e82a4d980a48df6b3","P2100"), Pair("04117b9f8374420e82a4d980a48df6b3","P2100"),
                Pair("04117b9f8374420e82a4d980a48df6b3","P2000"),Pair("04117b9f8374420e82a4d980a48df6b3","P15000"))

        doReturn(getTestJsonFile("P2100-PinDK-NAV.json"))
                .doReturn(getTestJsonFile("P2100-PinDK-NAV.json"))
                .doReturn(getTestJsonFile("P2100-PinDK-NAV.json"))
                .doReturn(getTestJsonFile("P2100-PinDK-NAV.json"))
                .doReturn(getTestJsonFile("P2000-NAV.json"))
                .doReturn(getTestJsonFile("P15000-NAV.json"))
                .whenever(mockEuxService)
                    .hentSed(eq(mockEuxCaseID) , ArgumentMatchers.anyString()
                )

        val actual = service.finnFDatoFraSed(mockEuxCaseID, mock)
        val expected = "1969-09-11"
        assertEquals(expected, actual)
    }

    @Test
    fun `Calling getFDatoFromSed   returns valid resultset on BUC_01` () {
        val euxCaseId = "123456"
        val allDocumentsJson = "src/test/resources/fagmodul/allDocumentsBuc01.json"
        val bucJson = String(Files.readAllBytes(Paths.get(allDocumentsJson)))
        assertTrue(validateJson(bucJson))

        doReturn(bucJson)
                .whenever(mockFagmodulService)
                .hentAlleDokumenter(eq(euxCaseId))

        doReturn(getTestJsonFile("P2000-NAV.json"))
                .whenever(mockEuxService)
                .hentSed( eq(euxCaseId), ArgumentMatchers.anyString())

        assertEquals("1980-01-01", service.getFDatoFromSed(euxCaseId))
    }

    private fun getTestJsonFile(filename: String): String {
        val filepath = "src/test/resources/buc/${filename}"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))
        return json
    }

}