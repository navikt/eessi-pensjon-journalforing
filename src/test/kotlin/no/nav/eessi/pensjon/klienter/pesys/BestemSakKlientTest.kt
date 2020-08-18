package no.nav.eessi.pensjon.klienter.pesys

import no.nav.eessi.pensjon.json.toJson
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

    private lateinit var bestemSakKlient: BestemSakKlient
    @BeforeEach
    fun setup() {
        bestemSakKlient = BestemSakKlient(mockrestTemplate)
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

        assertEquals("22873157", bestemSakKlient.hentSakInformasjon("12345678901", BucType.P_BUC_01, null)?.sakId)
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


        assertEquals("22873157", bestemSakKlient.hentSakInformasjon("12345678901", BucType.P_BUC_02, YtelseType.GJENLEV)?.sakId)

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

        assertEquals("2345678975414", bestemSakKlient.hentSakInformasjon("12345678901", BucType.P_BUC_02, YtelseType.BARNEP)?.sakId)
    }

    @Test
    fun `Gitt en P_BUC_02 med UFOREP med en kjent aktørId så skal det returneres et saksnummer og sakstype UFOREP`(){
        val responseBody = """
            {
              "sakInformasjonListe": [
                {
                  "sakId": "2345678975414",
                  "sakType": "UFOREP",
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

        val expectedSakInformasjon = SakInformasjon("2345678975414", YtelseType.UFOREP, SakStatus.LOPENDE, "4808", false)

        assertEquals(expectedSakInformasjon.toJson(), bestemSakKlient.hentSakInformasjon("12345678901", BucType.P_BUC_02, YtelseType.GJENLEV)?.toJson())
    }

    @Test
    fun `Gitt en P_BUC_02 med ukjent ytelse og en kjent aktørId så skal det returneres null`(){

        assertEquals(null, bestemSakKlient.hentSakInformasjon("12345678901", BucType.P_BUC_02, null))

    }

    @Test
    fun `Gitt at vi har en P_BUC_02 med uførep og en med Alder og aktørId så skal enhet behandlende enhet null returneres`(){

        val responseBody = """
            {
              "sakInformasjonListe": [
                {
                  "sakId": "2345678975414",
                  "sakType": "UFOREP",
                  "sakStatus": "AVSLUTTET",
                  "saksbehandlendeEnhetId": "4476",
                  "nyopprettet": false
                }
              ,
                {
                  "sakId": "2345123975414",
                  "sakType": "ALDER",
                  "sakStatus": "LOPENDE",
                  "saksbehandlendeEnhetId": "4303",
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


        assertEquals(null, bestemSakKlient.hentSakInformasjon("12345678901", BucType.P_BUC_02, YtelseType.GJENLEV)?.saksbehandlendeEnhetId)
    }

    @Test
    fun `Gitt at vi har en P_BUC_02 med uførep og aktørId så skal enhet behandlende enhet 4476 returneres`(){

        val responseBody = """
            {
              "sakInformasjonListe": [
                {
                  "sakId": "2345678975414",
                  "sakType": "UFOREP",
                  "sakStatus": "LOPENDE",
                  "saksbehandlendeEnhetId": "4476",
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


        assertEquals("4476", bestemSakKlient.hentSakInformasjon("12345678901", BucType.P_BUC_02, YtelseType.GJENLEV)?.saksbehandlendeEnhetId)
    }

    @Test
    fun `Gitt at vi har en P_BUC_02 med kjent aktørId bosatt utland så skal behandlende enhet 0001 returneres`(){

        val responseBody = """
            {
              "sakInformasjonListe": [
                {
                  "sakId": "2345678975414",
                  "sakType": "BARNEP",
                  "sakStatus": "LOPENDE",
                  "saksbehandlendeEnhetId": "0001",
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


        assertEquals("0001", bestemSakKlient.hentSakInformasjon("12345678901", BucType.P_BUC_02, YtelseType.GJENLEV)?.saksbehandlendeEnhetId)
    }




}
