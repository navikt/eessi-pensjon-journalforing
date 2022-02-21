package no.nav.eessi.pensjon.integrasjonstest.sendt

import no.nav.eessi.pensjon.EessiPensjonJournalforingTestApplication
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.integrasjonstest.CustomMockServer
import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsBase
import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.integrasjonstest.OPPGAVE_TOPIC
import no.nav.eessi.pensjon.integrasjonstest.SED_SENDT_TOPIC
import no.nav.eessi.pensjon.json.toJson
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest( classes = [IntegrasjonsTestConfig::class, EessiPensjonJournalforingTestApplication::class], value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC])
internal class PBuc03P2200IntegrasjonsTest : IntegrasjonsBase() {

    @Test
    fun `Når en SED (P2200) hendelse blir konsumert skal det opprettes journalføringsoppgave`() {

        //server setup
        CustomMockServer()
            .medJournalforing(false, "429434379")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medOppdaterDistribusjonsinfo()
            .medEuxGetRequestWithJson(
                "/buc/148161", Buc(
                    id = "12312312312452345624355",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                ).toJson()
            )
            .medEuxGetRequest("/buc/148161/sed/44cb68f89a2f4e748934fb4722721018","/sed/P2000-ugyldigFNR-NAV.json")
            .medEuxGetRequest( "/buc/148161/sed/f899bf659ff04d20bc8b978b186f1ecc/filer","/pdf/pdfResonseMedP2000MedVedlegg.json" )

        //send msg
        initAndRunContainer(SED_SENDT_TOPIC, OPPGAVE_TOPIC).also {
            it.sendMsgOnDefaultTopic("/eux/hendelser/P_BUC_03_P2200.json")
            it.waitForlatch(sendtListener)
        }

        //then route to 4303
        OppgaveMeldingVerification("429434379")
            .medHendelsetype("SENDT")
            .medSedtype("P2200")
            .medtildeltEnhetsnr("4303")
    }
}
