package no.nav.eessi.pensjon.journalforing

import com.google.api.gax.paging.Page
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Bucket
import com.google.cloud.storage.Storage
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType.P2000
import no.nav.eessi.pensjon.eux.model.SedType.P2100
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.integrasjonstest.saksflyt.JournalforingTestBase
import no.nav.eessi.pensjon.journalforing.JournalpostType.INNGAAENDE
import no.nav.eessi.pensjon.journalforing.bestemenhet.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.etterlatte.EtterlatteService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveHandler
import no.nav.eessi.pensjon.journalforing.pdf.PDFService
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.journalforing.saf.SafSak
import no.nav.eessi.pensjon.models.Behandlingstema.ALDERSPENSJON
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.oppgaverouting.Enhet.PENSJON_UTLAND
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPDLPerson
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.statistikk.StatistikkPublisher
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

private const val AKTOERID = "12078945602"
private const val RINADOK_ID = "3123123"
private const val SLAPP_SKILPADDE = "09035225916"
private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!
class JournalforingServiceMedGCPTest {

    lateinit var gcpStorageService : GcpStorageService
    lateinit var gcpStorage: Storage
    lateinit var safClient: SafClient
    lateinit var journalforingService: JournalforingService
    lateinit var oppgaveroutingService: OppgaveRoutingService
    lateinit var pdfService: PDFService
    lateinit var journalpostService: JournalpostService
    lateinit var oppgaveHandler: OppgaveHandler
    lateinit var statistikkPublisher: StatistikkPublisher
    lateinit var vurderBrukerInfo: VurderBrukerInfo
    lateinit var hentSakService: HentSakService
    lateinit var hentTemaService: HentTemaService

    var etterlatteService = mockk<EtterlatteService>()

    @BeforeEach
    fun setup() {
        gcpStorage = mockk<Storage>()
        every { gcpStorage.get(eq("bucket"), *anyVararg()) } returns mockk<Bucket>()

        gcpStorageService = GcpStorageService("bucket", "bucket", gcpStorage)
        safClient =  mockk()
        oppgaveroutingService = mockk()
        pdfService = mockk()
        journalpostService = mockk()
        oppgaveHandler = mockk()
        statistikkPublisher = mockk()
        hentSakService = HentSakService(etterlatteService, gcpStorageService)
        hentTemaService = HentTemaService(etterlatteService, journalpostService, gcpStorageService)
        vurderBrukerInfo = VurderBrukerInfo(gcpStorageService, journalpostService, oppgaveHandler)
        journalforingService = JournalforingService(
            journalpostService,
            oppgaveroutingService,
            pdfService,
            oppgaveHandler,
            mockk(),
            gcpStorageService,
            statistikkPublisher,
            vurderBrukerInfo,
            hentSakService,
            hentTemaService,
            env = null
        )
    }

    @Test
    fun `Etter oppdatering av journalpost på ukjent bruker slettes oppføringen i gcpStorage`(){
        val rinaId = "12345"
        val journalpostId = "54321111"
        val blobId = BlobId.of("bucket", rinaId)

        val journalpostResponse = JournalpostResponse(
            journalpostId,
            rinaId,
            PENSJON,
            emptyList(),
            Journalstatus.MOTTATT,
            false,
            AvsenderMottaker(id = "Avsender", navn = "NAV", land = "NO"),
            behandlingstema = ALDERSPENSJON,
            journalforendeEnhet = "PENSJON_UTLAND",
            bruker = Bruker(SLAPP_SKILPADDE, "FNR"),
            sak = SafSak(fagsakId = rinaId, sakstype = "ALDER", tema = "PEN", fagsaksystem = "PEN", arkivsaksnummer = "321654"),
            temanavn = PENSJON.name,
            datoOpprettet = LocalDateTime.now()
        )

        val opprettJournalpostRequest = OpprettJournalpostRequest(
            avsenderMottaker = AvsenderMottaker(id = rinaId, navn = "NAV", land = "NO"),
            behandlingstema = ALDERSPENSJON,
            bruker = Bruker(SLAPP_SKILPADDE, "FNR"),
            journalpostType = INNGAAENDE,
            sak = Sak(sakstype = "ALDER", fagsakid = "64646", fagsaksystem = "PEN"),
            tema = PENSJON,
            tilleggsopplysninger = listOf(Tilleggsopplysning("rinaSakId", rinaId)),
            tittel = "Inngående dokument",
            dokumenter = "1354",
            journalfoerendeEnhet = PENSJON_UTLAND
        )

        every { safClient.hentJournalpost(any()) } returns journalpostResponse
        every { oppgaveroutingService.hentEnhet(any()) } returns PENSJON_UTLAND
        every { pdfService.hentDokumenterOgVedlegg(any(), any(), any()) } returns Pair("Supported Documents", emptyList())
        every { journalpostService.opprettJournalpost(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
                opprettJournalpostRequest
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns GjennySak("123", "").toJson().toByteArray()
        } andThen mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns GjennySak("123", "").toJson().toByteArray()
        } andThen mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns GjennySak("123", "").toJson().toByteArray()
        } andThen mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns GjennySak("123", "").toJson().toByteArray()
        } andThen mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns Pair(JournalpostMedSedInfo(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)), mockk<BlobId>(relaxed = true)).toJson().toByteArray()
        }
        val blobList = mockk<Page<Blob>>().apply {
            every { iterateAll() } returns listOf(mockk<Blob>("blob").apply {
                every { name } returns blobId.name
            })
            every { values } returns listOf(mockk<Blob>("blob").apply {
                every { name } returns blobId.name
            })
        }

        every { gcpStorage.delete(blobId) } returns true
        every { gcpStorage.list(any<String>())} returns blobList

        every { safClient.hentJournalpost(any()) } returns journalpostResponse

        justRun { journalpostService.oppdaterJournalpost(any(), any(), any(), any(), any()) }
        every { journalpostService.sendJournalPost(any<OpprettJournalpostRequest>(), any(), any(), any()) } returns OpprettJournalPostResponse(
            journalpostId = journalpostId,
            journalstatus = Journalstatus.MOTTATT.name,
            melding = null,
            false,
        )
        every { journalpostService.skalStatusSettesTilAvbrutt(any(), any(), any(), any()) } returns false
        justRun { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(any()) }
        justRun { statistikkPublisher.publiserStatistikkMelding(any()) }
        every { etterlatteService.hentGjennySak(eq("1234")) } returns JournalforingTestBase.mockHentGjennySak("123")

        val sedHendelse = SedHendelse(
            sedType = P2100,
            rinaDokumentId = "19fd5292007e4f6ab0e337e89079aaf4",
            bucType = P_BUC_01,
            rinaSakId = rinaId,
            avsenderId = "NO:noinst002",
            avsenderNavn = "NOINST002",
            mottakerId = "SE:123456789",
            mottakerNavn = "SE INST002",
            rinaDokumentVersjon = "1",
            sektorKode = "P",
        )

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.MOTTATT,
            identifisertPersonPDL(AKTOERID, sedPersonRelasjon(Fodselsnummer.fra(SLAPP_SKILPADDE))),
            LocalDate.of(1952,3,9),
            SaksInfoSamlet(saktype = ALDER),
            identifisertePersoner = 1,
            navAnsattInfo = null,
            currentSed = SED(type = P2000)
        )
    }

    private fun identifisertPersonPDL(
        aktoerId: String = AKTOERID,
        personRelasjon: SEDPersonRelasjon?,
        landkode: String? = "",
        geografiskTilknytning: String? = "",
        fnr: Fodselsnummer? = null,
        personNavn: String = "Test Testesen"
    ): IdentifisertPDLPerson =
        IdentifisertPDLPerson(
            aktoerId,
            landkode,
            geografiskTilknytning,
            personRelasjon,
            fnr,
            personNavn = personNavn,
            identer = null
        )

    private fun sedPersonRelasjon(fnr: Fodselsnummer? = LEALAUS_KAKE, relasjon: Relasjon = Relasjon.FORSIKRET, rinaDocumentId: String = RINADOK_ID) =
        SEDPersonRelasjon(fnr = fnr, relasjon = relasjon, rinaDocumentId = rinaDocumentId)

}