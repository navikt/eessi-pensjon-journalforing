package no.nav.eessi.pensjon.integrasjonstest.mottatt

import no.nav.eessi.pensjon.EessiPensjonJournalforingTestApplication
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.integrasjonstest.CustomMockServer
import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsBase
import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.integrasjonstest.OPPGAVE_TOPIC
import no.nav.eessi.pensjon.integrasjonstest.SED_MOTTATT_TOPIC
import no.nav.eessi.pensjon.json.toJson
import org.junit.jupiter.api.Test
import org.mockserver.integration.ClientAndServer
import org.mockserver.socket.PortFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest( classes = [IntegrasjonsTestConfig::class, EessiPensjonJournalforingTestApplication::class], value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [SED_MOTTATT_TOPIC, OPPGAVE_TOPIC])
internal class SedMottattIntegrationTest : IntegrasjonsBase(){

    init {
        if(System.getProperty("mockServerport") == null){
        mockServer = ClientAndServer(PortFactory.findFreePort())
            .also {
                System.setProperty("mockServerport", it.localPort.toString())
            }
        }
    }

    @Test
    fun `Sender 1 Foreldre SED til Kafka`() {
        //given
        CustomMockServer()
            .medJournalforing(false, "429434378")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medEuxGetRequest("/buc/747729177/sed/44cb68f89a2f4e748934fb4722721018","/sed/P2000-NAV.json")

        meldingForMottattListener(sedMottattTemplate, "/eux/hendelser/FB_BUC_01_F001.json")
    }

    @Test
    fun `Gitt en mottatt P2000 så skal den routes til 4303`() {

        //setup server
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

        meldingForMottattListener(sedMottattTemplate, "/eux/hendelser/P_BUC_01_P2000.json")
        //verify route
        OppgaveMeldingVerification("429434378")
            .medHendelsetype("MOTTATT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("4303")
    }

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
        meldingForMottattListener(sedMottattTemplate, "/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json")

        //then route to 4303
        OppgaveMeldingVerification("429434388")
            .medHendelsetype("MOTTATT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("4303")
    }


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

        meldingForMottattListener(sedMottattTemplate, "/eux/hendelser/P_BUC_01_P2000_MedUgyldigVedlegg.json")

        //verify msg
        OppgaveMeldingVerification("429434378")
            .medHendelsetype("MOTTATT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("4303")
    }
}
