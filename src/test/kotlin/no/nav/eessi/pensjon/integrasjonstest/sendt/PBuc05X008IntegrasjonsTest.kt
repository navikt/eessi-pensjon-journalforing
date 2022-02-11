package no.nav.eessi.pensjon.integrasjonstest.sendt

import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.integrasjonstest.CustomMockServer
import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsBase
import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.integrasjonstest.OPPGAVE_TOPIC
import no.nav.eessi.pensjon.integrasjonstest.SED_SENDT_TOPIC
import no.nav.eessi.pensjon.json.toJson
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(classes = [IntegrasjonsTestConfig::class], value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC], partitions = 1)
@Disabled
internal class PBuc05X008IntegrasjonsIntegrasjons : IntegrasjonsBase() {

    @Test
    fun `Når en sed (X008) hendelse blir konsumert skal det opprettes journalføringsoppgave`() {

        //server setup
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

        //send msg
        initAndRunContainer(SED_SENDT_TOPIC, OPPGAVE_TOPIC).also {
            it.sendMsgOnDefaultTopic("/eux/hendelser/P_BUC_05_X008.json")
            it.waitForlatch(sendtListener)
        }

        //verify route
        OppgaveMeldingVerification("429434379")
            .medHendelsetype("SENDT")
            .medSedtype("X008")
            .medtildeltEnhetsnr("4303")
    }
}
