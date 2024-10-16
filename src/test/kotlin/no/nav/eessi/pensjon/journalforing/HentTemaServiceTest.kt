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
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!
class HentTemaServiceTest {

    private val journalpostService: JournalpostService = mockk()
    private val gcpStorageService: GcpStorageService = mockk()

    @Test
    fun `Ved henting av enhet basert på behandlingstema ufoere der bruker er bosatt Utland sendes sak til enhet UFORE_UTLANDSTILSNITT`() {
        val hentTemaService = HentTemaService(journalpostService, gcpStorageService)

        val rinaDocumentId = "123456"
        val sedHendelse = SedHendelse(rinaSakId = rinaDocumentId, rinaDokumentId = rinaDocumentId, sektorKode = "P", bucType = P_BUC_02, rinaDokumentVersjon = "1")
        val sakinfo = SaksInfoSamlet(rinaDocumentId, SakInformasjon(rinaDocumentId, SakType.UFOREP, SakStatus.LOPENDE))
        val identifisertPerson = JournalforingServiceBase.identifisertPersonPDL(
            "321654987321",
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = rinaDocumentId),
            landkode = "SWE"
        )

        every { journalpostService.bestemBehandlingsTema(any(), any(),any(),any(), any()) } returns Behandlingstema.UFOREPENSJON

        val tema = hentTemaService.enhetBasertPaaBehandlingstema(sedHendelse, sakinfo, identifisertPerson, 1, null)
        assertEquals(Enhet.UFORE_UTLAND, tema)
    }

    @Test
    fun `Ved henting av enhet basert på behandlingstema ufoere der bruker er bosatt Norge sendes sak til enhet UFORE_UTLANDSTILSNITT`() {
        val hentTemaService = HentTemaService(journalpostService, gcpStorageService)

        val rinaDocumentId = "123456"
        val sedHendelse = SedHendelse(rinaSakId = rinaDocumentId, rinaDokumentId = rinaDocumentId, sektorKode = "P", bucType = P_BUC_02, rinaDokumentVersjon = "1")
        val sakinfo = SaksInfoSamlet(rinaDocumentId, SakInformasjon(rinaDocumentId, SakType.UFOREP, SakStatus.LOPENDE))
        val identifisertPerson = JournalforingServiceBase.identifisertPersonPDL(
            "321654987321",
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = rinaDocumentId),
            landkode = "NOR"
        )

        every { journalpostService.bestemBehandlingsTema(any(), any(),any(),any(), any()) } returns Behandlingstema.UFOREPENSJON

        val tema = hentTemaService.enhetBasertPaaBehandlingstema(sedHendelse, sakinfo, identifisertPerson, 1, null)
        assertEquals(Enhet.UFORE_UTLANDSTILSNITT, tema)
    }

    fun sedPersonRelasjon(fnr: Fodselsnummer? = LEALAUS_KAKE, relasjon: Relasjon = Relasjon.FORSIKRET, rinaDocumentId: String = "123456") =
        SEDPersonRelasjon(fnr = fnr, relasjon = relasjon, rinaDocumentId = rinaDocumentId)
}