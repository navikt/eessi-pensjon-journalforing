package no.nav.eessi.pensjon.klienter.pesys

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.YtelseType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class BestemSakKlientTest {

    @Mock
    private lateinit var mockrestTemplate: RestTemplate

    @Mock
    private lateinit var toggleBestemSak: ToggleBestemSak

    private lateinit var bestemSakKlient: BestemSakKlient
    @BeforeEach
    fun setup() {
        bestemSakKlient = BestemSakKlient(mockrestTemplate, toggleBestemSak)
        bestemSakKlient.initMetrics()
    }


    @Test
    fun `Gitt en P_BUC_01 med kjent aktørId når journalføring utføres så kall bestemSak`() {

        val responseBody = String(Files.readAllBytes(Paths.get("src/test/resources/pen/bestemSakResponse.json")))

        Mockito.doReturn(
                ResponseEntity.ok(responseBody))
                .`when`(mockrestTemplate).exchange(
                        ArgumentMatchers.contains("/"),
                        eq(HttpMethod.POST),
                        any(),
                        eq(String::class.java))

        assertEquals("22873157", bestemSakKlient.hentSakId("12345678901", BucType.P_BUC_01, null))
    }

    @Test
    fun `Gitt en P_BUC_02 med gjenlevende en kjent aktørId og toggle er på skal det returneres et saksnummer`(){
        val responseBody = String(Files.readAllBytes(Paths.get("src/test/resources/pen/bestemSakGjenlevendeResponse.json")))

        Mockito.doReturn(
                ResponseEntity.ok(responseBody))
                .`when`(mockrestTemplate).exchange(
                        ArgumentMatchers.contains("/"),
                        eq(HttpMethod.POST),
                        any(),
                        eq(String::class.java))

        doReturn(true).whenever(toggleBestemSak).toggleGjenlevende()

        assertEquals("22873157", bestemSakKlient.hentSakId("12345678901", BucType.P_BUC_02, YtelseType.GJENLEV))

    }

    @Test
    fun `Gitt en P_BUC_02 med barnep en kjent aktørId og toggle er på skal det returneres et saksnummer`(){
        val responseBody = """
            {
              "sakInformasjonListe": [
                {
                  "sakId": "2345678975414",
                  "sakType": "BARNEP",
                  "sakStatus": "LOPENDE",
                  "saksbehandlendeEnhetId": "4808",
                  "nyopprettet": false
                }
              ]
            }
        """.trimIndent()

        Mockito.doReturn(
                ResponseEntity.ok(responseBody))
                .`when`(mockrestTemplate).exchange(
                        ArgumentMatchers.contains("/"),
                        eq(HttpMethod.POST),
                        any(),
                        eq(String::class.java))

        doReturn(true).whenever(toggleBestemSak).toggleGjenlevende()

        assertEquals("2345678975414", bestemSakKlient.hentSakId("12345678901", BucType.P_BUC_02, YtelseType.BARNEP))
    }

    @Test
    fun `Gitt en P_BUC_02 med utkjent ytelse en kjent aktørId og toggle er på aå returneres null`(){

        assertEquals(null, bestemSakKlient.hentSakId("12345678901", BucType.P_BUC_02, null))

    }

    @Test
    fun `Gitt en P_BUC_02 med en kjent aktørID og toggle er av så skal det returneres null`(){
        doReturn(false).whenever(toggleBestemSak).toggleGjenlevende()

        assertEquals(null, bestemSakKlient.hentSakId("12345678901", BucType.P_BUC_02, null))

    }
}
