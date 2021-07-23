package no.nav.eessi.pensjon.integrasjonstest.sendt

import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsBase
import no.nav.eessi.pensjon.json.toJson
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.TimeUnit

private const val SED_SENDT_TOPIC = "eessi-basis-sedSendt-v1"
private const val OPPGAVE_TOPIC = "eessi-pensjon-oppgave-v1"

@SpringBootTest(classes = [IntegrasjonsBase.TestConfig::class],  value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC])
internal class PBuc01P2000IntegrasjonsTest : IntegrasjonsBase() {

    @Test
    fun `Når en sedSendt hendelse blir konsumert skal det opprettes journalføringsoppgave for pensjon SEDer`() {
        CustomMockServer()
            .medJournalforing(false, "429434378")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medOppdaterDistribusjonsinfo()
            .medEuxGetRequestWithJson(
                "/buc/147729", Buc(
                    id = "7477291",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                ).toJson()
            )
            .medEuxGetRequest("/buc/147729/sed/44cb68f89a2f4e748934fb4722721018", "/sed/P2000-NAV.json")
            .medEuxGetRequest(
                "/buc/147729/sed/b12e06dda2c7474b9998c7139c841646/filer",
                "/pdf/pdfResponseUtenVedlegg.json"
            )

        initAndRunContainer(SED_SENDT_TOPIC, OPPGAVE_TOPIC).also {
            it.kafkaTemplate.send(SED_SENDT_TOPIC, javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json").readText())
        }
        sedListener.getSendtLatch().await(10, TimeUnit.SECONDS)

        //then we expect automatisk journalforing
        OppgaveMeldingVerification("429434378")
            .medHendelsetype("SENDT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("4303")
    }
}
