package no.nav.eessi.pensjon.integrasjonstest.mottatt

import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.integrasjonstest.MottattOgSendtIntegrationBase
import no.nav.eessi.pensjon.json.toJson
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
internal class PBuc01MottattIntegrasjonsTest : MottattOgSendtIntegrationBase() {

    @Test
    fun `Sender gyldig Pensjon SED (P2000) og forventer routing til 9999`() {
        //given a person
        mockPerson()

        CustomMockServer()
            .medJournalforing(false, "429434378")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medEuxGetRequestWithJson(
                "/buc/147729", Buc(
                    id = "7477291",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                ).toJson()
            )
            .medEuxGetRequest("/buc/147729/sed/44cb68f89a2f4e748934fb4722721018", "/sed/P2000-NAV.json")
            .medEuxGetRequest("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646/filer","/pdf/pdfResponseMedVedlegg.json")

        //when receiving a P2000
        initAndRunContainer().apply {
            send(SED_MOTTATT_TOPIC, javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json").readText())
        }
        sedListener.getSendtLatch().await(10, TimeUnit.SECONDS)

        //then we expect automatisk journalforing
        OppgaveMeldingVerification("429434378")
            .medHendelsetype("MOTTATT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("9999")
    }

}
