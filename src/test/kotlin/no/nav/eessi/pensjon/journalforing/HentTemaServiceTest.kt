package no.nav.eessi.pensjon.journalforing

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private const val RINA_ID = "123456"

class HentTemaServiceTest {

    private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!
    private val journalpostService: JournalpostService = mockk()
    private val gcpStorageService: GcpStorageService = mockk()
    private val hentTemaService = HentTemaService(journalpostService, gcpStorageService)

    @Test
    fun `Ved henting av enhet basert på behandlingstema ufoere der bruker er bosatt Utland sendes sak til enhet UFORE_UTLAND`() {
        val identifisertPerson = JournalforingServiceBase.identifisertPersonPDL(
            "321654987321",
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINA_ID),
            landkode = "SWE"
        )

        every { journalpostService.bestemBehandlingsTema(any(), any(),any(),any(), any()) } returns Behandlingstema.UFOREPENSJON

        val tema = hentTemaService.enhetBasertPaaBehandlingstema(
            sedHendelse(),
            saksInfoSamlet(),
            identifisertPerson,
            1,
            null,
            Tema.PENSJON
        )
        assertEquals(Enhet.UFORE_UTLAND, tema)
    }

    @Test
    fun `Ved henting av enhet basert på behandlingstema ufoere der bruker er bosatt Norge sendes sak til enhet UFORE_UTLANDSTILSNITT`() {
        val identifisertPerson = JournalforingServiceBase.identifisertPersonPDL(
            "321654987321",
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINA_ID),
            landkode = "NOR"
        )

        every { journalpostService.bestemBehandlingsTema(any(), any(),any(),any(), any()) } returns Behandlingstema.UFOREPENSJON

        val tema = hentTemaService.enhetBasertPaaBehandlingstema(
            sedHendelse(),
            saksInfoSamlet(),
            identifisertPerson,
            1,
            null,
            Tema.PENSJON
        )
        assertEquals(Enhet.UFORE_UTLANDSTILSNITT, tema)
    }

    private fun saksInfoSamlet() = SaksInfoSamlet(
        RINA_ID,
        SakInformasjon(RINA_ID, SakType.UFOREP, SakStatus.LOPENDE),
        pesysSaker = emptyList()
    )

    private fun sedHendelse() = SedHendelse(rinaSakId = RINA_ID, rinaDokumentId = RINA_ID, sektorKode = "P", bucType = P_BUC_02, rinaDokumentVersjon = "1")

    fun sedPersonRelasjon(fnr: Fodselsnummer? = LEALAUS_KAKE, relasjon: Relasjon = Relasjon.FORSIKRET, rinaDocumentId: String = RINA_ID) =
        SEDPersonRelasjon(fnr = fnr, relasjon = relasjon, rinaDocumentId = rinaDocumentId)
}