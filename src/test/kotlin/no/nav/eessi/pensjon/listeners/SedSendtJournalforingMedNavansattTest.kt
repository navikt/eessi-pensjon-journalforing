package no.nav.eessi.pensjon.listeners

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import no.nav.eessi.pensjon.eux.EuxCacheableKlient
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.integrasjonstest.saksflyt.JournalforingTestBase
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.journalforing.OpprettJournalPostResponse
import no.nav.eessi.pensjon.journalforing.OpprettJournalpostRequest
import no.nav.eessi.pensjon.journalforing.bestemenhet.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.bestemenhet.norg2.Norg2Klient
import no.nav.eessi.pensjon.journalforing.bestemenhet.norg2.Norg2Service
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostKlient
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveHandler
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.pdf.PDFService
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.listeners.navansatt.NavansattKlient
import no.nav.eessi.pensjon.listeners.pesys.BestemSakKlient
import no.nav.eessi.pensjon.listeners.pesys.BestemSakService
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPDLPerson
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.statistikk.StatistikkPublisher
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.kafka.support.Acknowledgment
import org.springframework.web.client.RestTemplate
import java.time.LocalDate

internal class SedSendtJournalforingMedNavansattTest {

    private val acknowledgment = mockk<Acknowledgment>(relaxUnitFun = true)
    private val cr = mockk<ConsumerRecord<String, String>>(relaxed = true)
    private val norg2Klient = mockk<Norg2Klient>()
    private val norg2Service = Norg2Service(norg2Klient)
    private val oppgaveRoutingService = OppgaveRoutingService(norg2Service)
    private val personidentifiseringService = mockk<PersonidentifiseringService>(relaxed = true)
    private val euxKlientLib = mockk<EuxKlientLib>(relaxed = true)
    private val euxCacheableKlient = EuxCacheableKlient(euxKlientLib)
    private val euxService = EuxService(euxCacheableKlient)
    private val bestemSakKlient = mockk<BestemSakKlient>(relaxed = true)
    private val bestemSakService = BestemSakService(bestemSakKlient)
    private val fagmodulKlient = mockk<FagmodulKlient>(relaxed = true)
    private val fagmodulService = FagmodulService(fagmodulKlient)
    private val journalpostKlient = mockk<JournalpostKlient>(relaxed = true)
    private val journalpostService = JournalpostService(journalpostKlient)
    private val oppgaveHandler = mockk<OppgaveHandler>(relaxed = true)
    private val statistikkPublisher = mockk<StatistikkPublisher>(relaxed = true)
    private val navansattRestTemplate = mockk<RestTemplate>(relaxed = true)
    private val navansattKlient = NavansattKlient(navansattRestTemplate)
    private val gcpStorageService = mockk<GcpStorageService>()
    private val journalforingService =
        JournalforingService(
            journalpostService, oppgaveRoutingService,
            mockk<PDFService>(relaxed = true).also {
                every { it.hentDokumenterOgVedlegg(any(), any(), any()) } returns Pair("1234568", emptyList())
            },
            oppgaveHandler, mockk(), gcpStorageService, statistikkPublisher, mockk()
        )

    private val sedListener = SedSendtListener(
        journalforingService,
        personidentifiseringService,
        euxService,
        fagmodulService,
        bestemSakService,
        navansattKlient,
        gcpStorageService,
        "test",
    )

    @BeforeEach
    fun setup() {
        justRun { gcpStorageService.arkiverteSakerForRinaId(any(), any()) }
    }

    @Test
    fun `Navansatt ved kall til pensjonSakInformasjonSendt ved en saktype vi ikke behandler rutes oppgave i hht til regler i journalforingsEnhet`() {
        // Denne oppgaven blir rutet til UFORE_UTLANDSTILSNITT siden det er en identifisert person under 62 år (over 18) som er bosatt Norge
        val aktoerId = "3216549873212"
        val bucJson = javaClass.getResource("/buc/M_BUC.json")!!.readText()
        val buc = mapJsonToAny<Buc>(bucJson)

        val sedHendelse = SedHendelse(
            sedType = SedType.M051,
            rinaDokumentId = "19fd5292007e4f6ab0e337e89079aaf4",
            bucType = BucType.M_BUC_03a,
            rinaSakId = "123456789",
            avsenderId = "NO:noinst002",
            avsenderNavn = "NOINST002",
            mottakerId = "SE:123456789",
            mottakerNavn = "SE INST002",
            rinaDokumentVersjon = "1",
            sektorKode = "M",
        )

        val sedJson = javaClass.getResource("/sed/M051.json")!!.readText()

        val sakInformasjon = SakInformasjon(
            sakId = "654321",
            sakType = SakType.ALDER,
            sakStatus = SakStatus.LOPENDE,
            saksbehandlendeEnhetId = "",
            nyopprettet = false
        )

        val identifisertPerson = identifisertPersonPDL(
            aktoerId = aktoerId,
            landkode = "NOR",
            geografiskTilknytning = null,
            personRelasjon = SEDPersonRelasjon(
                fnr = Fodselsnummer.fra(JournalforingTestBase.FNR_VOKSEN_UNDER_62),
                relasjon = Relasjon.FORSIKRET,
                saktype = null,
                sedType = SedType.M051,
                fdato = LocalDate.of(1971, 6, 11),
                rinaDocumentId = "19fd5292007e4f6ab0e337e89079aaf4"
            ),
            fnr = Fodselsnummer.fra(JournalforingTestBase.FNR_VOKSEN_UNDER_62)
        )

        every { euxService.hentBuc(any()) } returns buc
        every { euxKlientLib.hentSedJson(any(), any()) } returns sedJson
        every { personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(any()) } returns false
        every { personidentifiseringService.hentIdentifisertPerson(any(), any(), any(), any(), any(), any()) } returns identifisertPerson
        every { personidentifiseringService.hentIdentifisertePersoner(any()) } returns listOf(identifisertPerson)
        every { personidentifiseringService.hentFodselsDato(any(), any()) } returns LocalDate.of(1971, 6, 11)
        every { fagmodulKlient.hentPensjonSaklist(eq(aktoerId)) } returns listOf(sakInformasjon)
        every { gcpStorageService.gjennyFinnes(any())} returns false
        every { gcpStorageService.journalFinnes(any())} returns false
        justRun { journalpostKlient.oppdaterDistribusjonsinfo(any()) }
        justRun { gcpStorageService.lagreJournalpostDetaljer(any(), any(), any(), any(), any()) }

        val opprettJournalPostResponse = OpprettJournalPostResponse(
            journalpostId = "12345",
            journalstatus = "",
            melding = "",
            journalpostferdigstilt = false,
        )

        val requestSlot = slot<OpprettJournalpostRequest>()
        every { journalpostKlient.opprettJournalpost(capture(requestSlot), any(), null) } returns opprettJournalPostResponse

        val requestSlotOppgave = slot<OppgaveMelding>()
        justRun { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(capture(requestSlotOppgave)) }

        sedListener.consumeSedSendt(sedHendelse.toJson(), cr, acknowledgment)
    }
    @Test
    fun `Sjekker at vi får ut navansatt med enhetsinfo som vi kan opprette journalpost med`() {
        val responseAnsattMedEnhetsId = """
            {
                "ident":"Z990965",
                "navn":"Andersen, Anette Christin",
                "fornavn":"Anette Christin",
                "etternavn":"Andersen",
                "epost":"Anette.Christin.Andersen@nav.no",
                "groups":[
                    "Group_be80eb75-e270-40ca-a5f9-29ae8b63eecd",
                    "1209XX-GA-Brukere",
                    "4407-GO-Enhet",
                    "0000-GA-EESSI-CLERK-UFORE",
                    "0000-GA-Person-EndreSprakMalform",
                    "0000-GA-Person-EndreKommunikasjon",
                    "0000-ga-eessi-basis",
                    "0000-GA-Arena","0000-GA-STDAPPS"
                ]
            }
        """.trimIndent()
        every { navansattRestTemplate.exchange(
            "/navansatt/Z990965",
            HttpMethod.GET,
            any(),
            String::class.java)
        } returns ResponseEntity.ok(responseAnsattMedEnhetsId)

        val responseHentEnheter = """
            [
                {
                "id":"0001",
                "navn":"NAV Familie- og pensjonsytelser Utland",
                "nivaa":"SPESEN"
                },
                {
                "id":"4407",
                "navn":"NAV Arbeid og ytelser Tønsberg",
                "nivaa":"EN"
                }
            ] 
        """.trimIndent()
        every { navansattRestTemplate.exchange(
            "/navansatt/Z990965/enheter",
            HttpMethod.GET,
            any(),
            String::class.java)
        } returns ResponseEntity.ok(responseHentEnheter)

        val hendelse = SedHendelse(
            sedType = SedType.M051,
            rinaDokumentId = "19fd5292007e4f6ab0e337e89079aaf4",
            bucType = BucType.M_BUC_03a,
            rinaSakId = "123456789",
            avsenderId = "NO:noinst002",
            avsenderNavn = "NOINST002, NO INST002, NO",
            mottakerId = "SE:123456789",
            mottakerNavn = "SE, SE INST002, SE",
            rinaDokumentVersjon = "1",
            sektorKode = "M",
        )
        val buc = mapJsonToAny<Buc>(javaClass.getResource("/buc/M_BUC.json")!!.readText())

        val ansattMedEnhetsInfo  = navansattKlient.navAnsattMedEnhetsInfo(buc, hendelse)

        assertEquals("Z990965", ansattMedEnhetsInfo?.first)
        assertEquals(Enhet.ARBEID_OG_YTELSER_TONSBERG, ansattMedEnhetsInfo?.second)
        assertEquals("(Z990965, ARBEID_OG_YTELSER_TONSBERG)", ansattMedEnhetsInfo.toString())
    }


    fun identifisertPersonPDL(
        aktoerId: String = "3216549873215",
        personRelasjon: SEDPersonRelasjon?,
        landkode: String? = "",
        geografiskTilknytning: String? = "",
        fnr: Fodselsnummer? = null,
        personNavn: String = "Test Testesen"
    ): IdentifisertPDLPerson =
        IdentifisertPDLPerson(aktoerId, landkode, geografiskTilknytning, personRelasjon, fnr, personNavn = personNavn)

}