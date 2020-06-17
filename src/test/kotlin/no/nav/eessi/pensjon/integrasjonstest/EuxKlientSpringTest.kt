package no.nav.eessi.pensjon.integrasjonstest

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.EessiPensjonJournalforingApplication
import no.nav.eessi.pensjon.buc.SedDokumentHelper
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.security.sts.STSService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockserver.integration.ClientAndServer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.util.*

private const val SED_SENDT_TOPIC = "eessi-basis-sedSendt-v1"
private const val SED_MOTTATT_TOPIC = "eessi-basis-sedMottatt-v1"
private const val OPPGAVE_TOPIC = "privat-eessipensjon-oppgave-v1"

private lateinit var mockServer: ClientAndServer

@SpringBootTest(classes = [EessiPensjonJournalforingApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["integrationtest"])
@AutoConfigureMockMvc
@EmbeddedKafka(controlledShutdown = true, partitions = 1, topics = [SED_SENDT_TOPIC, SED_MOTTATT_TOPIC, OPPGAVE_TOPIC], brokerProperties = ["log.dir=out/embedded-kafka"])
class EuxKlientSpringTest {

    @MockBean
    lateinit var sedDokumentHelper : SedDokumentHelper

    @MockBean
    lateinit var stsService: STSService

    @MockBean(name = "euxOidcRestTemplate")
    lateinit var euxOidcRestTemplate: RestTemplate

    @Autowired
    lateinit var euxKlient: EuxKlient

    @Test
    fun shouldReturnDefaultMessage() {
        val rinaNr = "123541254"
        val dokumentId = "65465fdghdfhgdfg6546"
        val path = "/buc/$rinaNr/sed/$dokumentId"

        doThrow(HttpClientErrorException.NotFound::class)
                .doReturn(ResponseEntity.ok().body("mockSed"))
                .whenever(euxOidcRestTemplate).exchange(
                        path,
                        HttpMethod.GET,
                        HttpEntity(""),
                        String::class.java
                )

        val actual = euxKlient.hentSed(rinaNr, dokumentId)
        Assertions.assertEquals("mockSed", actual)
        verify(euxOidcRestTemplate, times(2)).exchange(eq(path), eq(HttpMethod.GET), eq(HttpEntity("")), eq(String::class.java))

    }

    companion object {

        init {
            // Start Mockserver in memory
            val port = randomFrom()
            mockServer = ClientAndServer.startClientAndServer(port)
            System.setProperty("mockServerport", port.toString())
        }

        private fun randomFrom(from: Int = 1024, to: Int = 65535): Int {
            val random = Random()
            return random.nextInt(to - from) + from
        }
    }
}
