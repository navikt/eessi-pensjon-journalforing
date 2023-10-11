package no.nav.eessi.pensjon.integrasjonstest

import no.nav.eessi.pensjon.EessiPensjonJournalforingTestApplication
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Test
import org.mockserver.integration.ClientAndServer
import org.mockserver.socket.PortFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest( classes = [IntegrasjonsTestConfig::class, EessiPensjonJournalforingTestApplication::class])
@ActiveProfiles("integrationtest")
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [SED_MOTTATT_TOPIC, OPPGAVE_TOPIC])
internal class SedMottattIntegrationTest : IntegrasjonsBase(){

    init {
        if (System.getProperty("mockServerport") == null) {
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
            .mockHttpRequestWithResponseFromFile("/buc/747729177/sed/44cb68f89a2f4e748934fb4722721018", HttpMethod.GET,"/sed/P2000-NAV.json")

        meldingForMottattListener("/eux/hendelser/FB_BUC_01_F001.json")
    }

    @Test
    fun `Gitt en mottatt P2000 med en fdato som er lik med fdato i fnr så skal oppgaven routes til 4303`() {

        //setup server
        CustomMockServer()
            .medJournalforing(false, "429434378")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .mockHttpRequestWithResponseFromJson(
                "/buc/147729",
                HttpMethod.GET,
                Buc(
                    id = "7477291",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                ).toJson()
            )
            .mockHttpRequestWithResponseFromFile("/buc/147729/sed/44cb68f89a2f4e748934fb4722721018", HttpMethod.GET,"/sed/P2000-NAV.json")
            .mockHttpRequestWithResponseFromFile("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646/filer",HttpMethod.GET,"/pdf/pdfResponseMedVedlegg.json")

        meldingForMottattListener("/eux/hendelser/P_BUC_01_P2000.json")
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
            .mockHttpRequestWithResponseFromJson(
                "/buc/7477291",
                HttpMethod.GET,Buc(
                    id = "7477291",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocuments_ugyldigFNR_ids.json")
                ).toJson()
            )
            .mockHttpRequestWithResponseFromFile(
                "/buc/7477291/sed/b12e06dda2c7474b9998c7139c841646fffx",
                HttpMethod.GET,"/sed/P2000-ugyldigFNR-NAV.json"
            )
            .mockHttpRequestWithResponseFromFile(
                "/buc/7477291/sed/b12e06dda2c7474b9998c7139c841646fffx/filer",
                HttpMethod.GET,"/pdf/pdfResponseUtenVedlegg.json"
            )
        //send msg
        meldingForMottattListener("/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json")

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
            .mockHttpRequestWithResponseFromJson(
                "/buc/147666",
                HttpMethod.GET,Buc(
                    id = "147666",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                ).toJson()
            )
            .mockHttpRequestWithResponseFromFile("/buc/147666/sed/44cb68f89a2f4e748934fb4722721018", HttpMethod.GET,"/sed/P2000-NAV.json")
            .mockHttpRequestWithResponseFromFile("/buc/147666/sed/b12e06dda2c7474b9998c7139c666666/filer",HttpMethod.GET,"/pdf/pdfResponseMedUgyldigVedlegg.json" )

        meldingForMottattListener("/eux/hendelser/P_BUC_01_P2000_MedUgyldigVedlegg.json")

        OppgaveMeldingVerification("429434378")
            .medHendelsetype("MOTTATT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("4303")
    }
}
