package no.nav.eessi.pensjon.integrasjonstest.sendt

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

private const val SED_SENDT_TOPIC = "eessi-basis-sedSendt-v1"
private const val OPPGAVE_TOPIC = "eessi-pensjon-oppgave-v1"
@SpringBootTest(classes = [MottattOgSendtIntegrationBase.TestConfig::class], value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC])
internal class PBuc05X008IntegrasjonsTest : MottattOgSendtIntegrationBase() {

    @Test
    fun `Når en sed (X008) hendelse blir konsumert skal det opprettes journalføringsoppgave`() {
        //given a person
        val person = mockPerson(fnr ="09035225916", aktorId = "1000101917358")

        //given a http service with buc and sed
        CustomMockServer()
            .medJournalforing(false, "429434379")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medOppdaterDistribusjonsinfo()
            .medEuxGetRequestWithJson(
                "/buc/161558", Buc(
                    id = "12312312312452345624355",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                ).toJson()
            )
            .medEuxGetRequest("/buc/161558/sed/44cb68f89a2f4e748934fb4722721018","/sed/P2000-ugyldigFNR-NAV.json")
            .medEuxGetRequest( "/buc/161558/sed/40b5723cd9284af6ac0581f3981f3044/filer","/pdf/pdfResonseMedP2000MedVedlegg.json" )

        //when receiving a p2000 with invalid fnr
        initAndRunContainer().apply {
            send(SED_SENDT_TOPIC , javaClass.getResource("/eux/hendelser/P_BUC_05_X008.json").readText())
            sedListener.getSendtLatch().await(10, TimeUnit.SECONDS)
        }

        //then route to 4303
        OppgaveMeldingVerification("429434379")
            .medHendelsetype("SENDT")
            .medSedtype("X008")
            .medtildeltEnhetsnr("4303")
    }
}
