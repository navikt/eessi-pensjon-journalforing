package no.nav.eessi.pensjon.integrasjonstest.mottatt

import no.nav.eessi.pensjon.integrasjonstest.MottattOgSendtIntegrationBase
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.TimeUnit

private const val SED_MOTTATT_TOPIC = "eessi-basis-sedMottatt-v1"
private const val OPPGAVE_TOPIC = "eessi-pensjon-oppgave-v1"

@SpringBootTest( classes = [MottattOgSendtIntegrationBase.TestConfig::class], value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(topics = [SED_MOTTATT_TOPIC, OPPGAVE_TOPIC])
internal class PBuc01UgyldigVedleggIntegrasjonsTest : MottattOgSendtIntegrationBase() {

    @Test
    fun `Sender Pensjon SED (P2000) med ugyldig vedlegg og skal routes til 9999`() {

        //gitt en person med frn og aktorid
        mockPerson()

        CustomMockServer()
            .medJournalforing()
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medEuxGetRequest("/buc/147666", "/eux/hendelser/P_BUC_01_P2000_MedUgyldigVedlegg.json")
            .medEuxGetRequest("/buc/147666/sed/b12e06dda2c7474b9998c7139c666666/filer","/pdf/pdfResponseMedUgyldigVedlegg.json" )

        //when
        initAndRunContainer().apply {
            send(SED_MOTTATT_TOPIC,javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_MedUgyldigVedlegg.json").readText())
        }
        sedListener.getSendtLatch().await(10, TimeUnit.SECONDS)

        //then
        OppgaveMeldingVerification("429434378")
            .medHendelsetype("MOTTATT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("9999")
    }

}
