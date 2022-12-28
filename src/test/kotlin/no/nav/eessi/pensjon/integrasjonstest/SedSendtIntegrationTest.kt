package no.nav.eessi.pensjon.integrasjonstest

import no.nav.eessi.pensjon.EessiPensjonJournalforingTestApplication
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.utils.toJson
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
    topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC]
)
internal class SedSendtIntegrationTest : IntegrasjonsBase() {

    init {
        if(System.getProperty("mockServerport") == null){
            mockServer = ClientAndServer(PortFactory.findFreePort())
                .also {
                    System.setProperty("mockServerport", it.localPort.toString())
                }
        }
    }

    @Test
    fun `Når en sedSendt hendelse med en foreldre blir konsumert så skal den ikke opprette oppgave`() {

        //setup server
        CustomMockServer()
            .medJournalforing(false, "429434378")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medEuxGetRequest("/buc/747729177/sed/44cb68f89a2f4e748934fb4722721018", "/sed/P2000-NAV.json")

        meldingForSendtListener("/eux/hendelser/FB_BUC_01_F001.json")
    }


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

        meldingForSendtListener( "/eux/hendelser/P_BUC_01_P2000_ugyldigFNR.json")

        //then route to 4303
        OppgaveMeldingVerification("429434379")
            .medHendelsetype("SENDT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("4303")
    }

    @Test
    fun `Når en sedSendt hendelse blir konsumert skal det opprettes journalføringsoppgave for pensjon SEDer`() {

        //server setup
        CustomMockServer()
            .medJournalforing(false, "429434379")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medOppdaterDistribusjonsinfo()
            .medEuxGetRequestWithJson(
                "/buc/147666", Buc(
                    id = "147666",
                    participants = emptyList<Participant>(),
                    documents = opprettBucDocuments("/fagmodul/alldocumentsids.json")
                ).toJson()
            )
            .medEuxGetRequest("/buc/147666/sed/44cb68f89a2f4e748934fb4722721018","/sed/P2000-NAV.json")
            .medEuxGetRequest("/buc/147666/sed/b12e06dda2c7474b9998c7139c666666/filer","/pdf/pdfResponseMedUgyldigVedlegg.json")

        meldingForSendtListener( "/eux/hendelser/P_BUC_01_P2000_MedUgyldigVedlegg.json")

        //verify route
        OppgaveMeldingVerification("429434379")
            .medHendelsetype("SENDT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("4303")
    }

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

        meldingForSendtListener( "/eux/hendelser/P_BUC_03_P2200.json")

        //then route to 4303
        OppgaveMeldingVerification("429434379")
            .medHendelsetype("SENDT")
            .medSedtype("P2200")
            .medtildeltEnhetsnr("4303")
    }

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

        meldingForSendtListener( "/eux/hendelser/P_BUC_05_X008.json")

        //verify route
        OppgaveMeldingVerification("429434379")
            .medHendelsetype("SENDT")
            .medSedtype("X008")
            .medtildeltEnhetsnr("4303")
    }

}
