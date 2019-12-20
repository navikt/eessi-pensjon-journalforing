package no.nav.eessi.pensjon.buc

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.json.validateJson
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class FnrServiceTest {

    @Mock
    private lateinit var mockEuxService: EuxService

    @Mock
    private lateinit var mockFagmodulService: FagmodulService

    private lateinit var service: FnrService


    @BeforeEach
    fun setup() {
        service = FnrService(mockFagmodulService , mockEuxService)
    }

    private fun getTestJsonFile(filename: String): String {
        val filepath = "src/test/resources/buc/${filename}"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        Assertions.assertTrue(validateJson(json))
        return json
    }

    @Test
    fun `filtrer norsk pin annenperson med rolle 01`()   {
        val mapper = jacksonObjectMapper()
        val p2000json = getTestJsonFile("P2000-NAV.json")
        assertEquals(null, service.filterAnnenpersonPinNode(mapper.readTree(p2000json)))

        val p10000json = getTestJsonFile("P10000-01Gjenlevende-NAV.json")
        val expected = "287654321"
        val actual  = service.filterAnnenpersonPinNode(mapper.readTree(p10000json))
        assertEquals(expected, actual)

    }

    @Test
    fun `letter igjennom beste Sed på valgt buc P2100 også P2000 etter norsk personnr`() {
        val mockEuxCaseID = "123123"
        val mock = listOf(Pair("04117b9f8374420e82a4d980a48df6b3","P2100"), Pair("04117b9f8374420e82a4d980a48df6b3","P2000"))

                doReturn(getTestJsonFile("P2100-PinDK-NAV.json"))
                .doReturn(getTestJsonFile("P2000-NAV.json"))
                        .whenever(mockEuxService)
                        .hentSed(eq(mockEuxCaseID), ArgumentMatchers.anyString()
                )

        val actual = service.getFodselsnrFraSed(mockEuxCaseID, mock)
        val expected = "970970970"
        assertEquals(expected, actual)
    }

    @Test
    fun `letter igjennom beste Sed på valgt buc etter norsk personnr`() {
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

        val actual = service.getFodselsnrFraSed(mockEuxCaseID, mock)
        val expected = "970970970"
        assertEquals(expected, actual)
    }

    @Test
    fun `letter igjennom beste Sed på valgt buc P15000 alder eller ufor etter norsk personnr`() {
        val mockEuxCaseID = "123123"
        val mock = listOf(Pair("04117b9f8374420e82a4d980a48df6b3","P2100"),Pair("04117b9f8374420e82a4d980a48df6b3","P15000"))

        doReturn(getTestJsonFile("P2100-PinDK-NAV.json"))
                .doReturn(getTestJsonFile("P15000-NAV.json"))
                .whenever(mockEuxService)
                    .hentSed( eq(mockEuxCaseID), ArgumentMatchers.anyString()
                )

        val actual = service.getFodselsnrFraSed(mockEuxCaseID, mock)
        val expected = "21712"
        assertEquals(expected, actual)
    }

    @Test
    fun `leter igjennom beste Sed paa valgt buc P15000 gjenlevende etter norsk personnr`() {
        val mockEuxCaseID = "123123"
        val mock = listOf(Pair("04117b9f8374420e82a4d980a48df6b3","P2100"),Pair("04117b9f8374420e82a4d980a48df6b3","P15000"))


        doReturn(getTestJsonFile("P2100-PinDK-NAV.json"))
                .doReturn(getTestJsonFile("P15000Gjennlevende-NAV.json"))
                .whenever(mockEuxService)
                    .hentSed( eq(mockEuxCaseID), ArgumentMatchers.anyString()
                )

        val actual = service.getFodselsnrFraSed(mockEuxCaseID, mock)
        val expected = "21712"
        assertEquals(expected, actual)
    }
}