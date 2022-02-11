package no.nav.eessi.pensjon.integrasjonstest.mottatt

import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.integrasjonstest.CustomMockServer
import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsBase
import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.integrasjonstest.OPPGAVE_TOPIC
import no.nav.eessi.pensjon.integrasjonstest.SED_MOTTATT_TOPIC
import no.nav.eessi.pensjon.json.toJson
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest( classes = [IntegrasjonsTestConfig::class], value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(topics = [SED_MOTTATT_TOPIC, OPPGAVE_TOPIC])
@Disabled
internal class PBuc01UgyldigFNRIntegrasjonsTest : IntegrasjonsBase() {

    @Test
    fun `Sender Pensjon SED (P2000) med ugyldig FNR og forventer routing til 4303`() {

        //setup server
        CustomMockServer()
            .medJournalforing(false, "429434388")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medEuxGetRequestWithJson(
                "/buc/7477291", Buc(
                    id = "7477291",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocuments_ugyldigFNR_ids.json")
                ).toJson()
            )
            .medEuxGetRequest(
                "/buc/7477291/sed/b12e06dda2c7474b9998c7139c841646fffx",
                "/sed/P2000-ugyldigFNR-NAV.json"
            )
            .medEuxGetRequest(
                "/buc/7477291/sed/b12e06dda2c7474b9998c7139c841646fffx/filer",
                "/pdf/pdfResponseUtenVedlegg.json"
            )

        //send msg
        initAndRunContainer(SED_MOTTATT_TOPIC, OPPGAVE_TOPIC).also {
            it.sendMsgOnDefaultTopic("/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json")
            it.waitForlatch(mottattListener)
        }

        //then route to 4303
        OppgaveMeldingVerification("429434388")
            .medHendelsetype("MOTTATT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("4303")
    }
}
