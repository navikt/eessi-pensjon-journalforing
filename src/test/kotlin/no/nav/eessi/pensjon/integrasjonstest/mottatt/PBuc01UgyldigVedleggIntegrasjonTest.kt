package no.nav.eessi.pensjon.integrasjonstest.mottatt

import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.integrasjonstest.CustomMockServer
import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsBase
import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.integrasjonstest.OPPGAVE_TOPIC
import no.nav.eessi.pensjon.integrasjonstest.SED_MOTTATT_TOPIC
import no.nav.eessi.pensjon.json.toJson
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles


@SpringBootTest( classes = [IntegrasjonsTestConfig::class], value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(topics = [SED_MOTTATT_TOPIC, OPPGAVE_TOPIC])
internal class PBuc01UgyldigVedleggIntegrasjonTest : IntegrasjonsBase() {

    @Test
    fun `Sender Pensjon SED (P2000) med ugyldig vedlegg og skal routes til 9999`() {

        //setup server
        CustomMockServer()
            .medJournalforing()
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medEuxGetRequestWithJson(
                "/buc/147666", Buc(
                    id = "147666",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                ).toJson()
            )
            .medEuxGetRequest("/buc/147666/sed/44cb68f89a2f4e748934fb4722721018","/sed/P2000-NAV.json")
            .medEuxGetRequest("/buc/147666/sed/b12e06dda2c7474b9998c7139c666666/filer","/pdf/pdfResponseMedUgyldigVedlegg.json" )

        //send msg
        initAndRunContainer(SED_MOTTATT_TOPIC, OPPGAVE_TOPIC).also {
            it.sendMsgOnDefaultTopic("/eux/hendelser/P_BUC_01_P2000_MedUgyldigVedlegg.json")
            it.waitForlatch(sedListener)
        }

        //verify msg
        OppgaveMeldingVerification("429434378")
            .medHendelsetype("MOTTATT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("4303")
    }
}
