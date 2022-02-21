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

@SpringBootTest(classes = [IntegrasjonsTestConfig::class, EessiPensjonJournalforingTestApplication::class], value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka( topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC])
internal class PBuc01P2000UgyldigFNRIntegrasjonsIntegrasjons : IntegrasjonsBase() {

    @Test
    fun `Når en p2000 med ugyldig FNR blir konsumert så skal den rutes til 4303`() {

        //server setup
        CustomMockServer()
            .medJournalforing(false, "429434379")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medOppdaterDistribusjonsinfo()
            .medEuxGetRequestWithJson(
                "/buc/7477291", Buc(
                    id = "7477291",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocuments_ugyldigFNR_ids.json")
                ).toJson()
            )
            .medEuxGetRequest("/buc/7477291/sed/b12e06dda2c7474b9998c7139c841646fffx","/sed/P2000-ugyldigFNR-NAV.json")
            .medEuxGetRequest( "/buc/7477291/sed/b12e06dda2c7474b9998c7139c841646fffx/filer","/pdf/pdfResonseMedP2000MedVedlegg.json" )

        //send msg
        initAndRunContainer(SED_SENDT_TOPIC, OPPGAVE_TOPIC).also {
            it.sendMsgOnDefaultTopic("/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json")
            it.waitForlatch(sendtListener)
        }

        //then route to 4303
        OppgaveMeldingVerification("429434379")
            .medHendelsetype("SENDT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("4303")
    }
}
