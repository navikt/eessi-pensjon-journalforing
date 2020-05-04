package no.nav.eessi.pensjon.klienter.pesys

import no.nav.eessi.pensjon.models.BucType
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

        assertEquals("22873157", bestemSakKlient.hentSakId("12345678901", BucType.P_BUC_01))
    }
}
