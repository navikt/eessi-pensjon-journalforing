package no.nav.eessi.pensjon.architecture.saksflyt

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.klienter.pesys.BestemSakResponse
import no.nav.eessi.pensjon.models.SedType
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity

internal class PBuc05Test : JournalforingTestBase() {

    @Test
    fun `Flere personer i SED og mangler rolle`() {
        val fnr = "12078945602"
        val alldocsid = getResource("fagmodul/alldocumentsids.json")
        val sed = createSedJson(SedType.P8000, fnr, true)

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alldocsid
        every { euxKlient.hentSed(any(), any()) } returns sed
        every { diskresjonService.hentDiskresjonskode(any()) } returns null
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")

        val journalpost = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P8000)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val body = journalpost.captured.body
        val postRequest = mapJsonToAny(body!!, typeRefs<OpprettJournalpostRequest>(), true)

        // forvent tema == PEN og enhet 4303
        assertEquals("PEN", postRequest.tema)
        assertEquals("4303", postRequest.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Kun 1 person i SED, men fnr mangler`() {
        val alldocsid = getResource("fagmodul/alldocumentsids.json")
        val sed = createSedJson(SedType.P8000)

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alldocsid
        every { euxKlient.hentSed(any(), any()) } returns sed
        every { personV3Service.hentPerson(any()) } returns Bruker()
        every { diskresjonService.hentDiskresjonskode(any()) } returns null

        val pdfResponseUtenVedlegg = getResource("pdf/pdfResponseUtenVedlegg.json")
        every { euxKlient.hentSedDokumenter(any(), any()) } returns pdfResponseUtenVedlegg

        val journalpost = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P8000)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val body = journalpost.captured.body
        val postRequest = mapJsonToAny(body!!, typeRefs<OpprettJournalpostRequest>(), true)

        // forvent tema == PEN og enhet 4303
        assertEquals("PEN", postRequest.tema)
        assertEquals("4303", postRequest.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Kun 1 person i SED der fnr finnes, men saksnummer mangler`() {
        val alldocsid = getResource("fagmodul/alldocumentsids.json")
        val sed = createSedJson(SedType.P8000)

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alldocsid
        every { euxKlient.hentSed(any(), any()) } returns sed
        every { personV3Service.hentPerson(any()) } returns Bruker()
        every { diskresjonService.hentDiskresjonskode(any()) } returns null

        val pdfResponseUtenVedlegg = getResource("pdf/pdfResponseUtenVedlegg.json")
        every { euxKlient.hentSedDokumenter(any(), any()) } returns pdfResponseUtenVedlegg
        every {
            bestemSakOidcRestTemplate.exchange("/", HttpMethod.POST, any<HttpEntity<String>>(), any<Class<String>>())
        } returns ResponseEntity.ok().body(BestemSakResponse(null, sakInformasjonListe = emptyList()).toJson())

        val journalpost = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P8000)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val body = journalpost.captured.body
        val postRequest = mapJsonToAny(body!!, typeRefs<OpprettJournalpostRequest>(), true)

        // forvent tema == PEN og enhet 4303
        assertEquals("PEN", postRequest.tema)
        assertEquals("4303", postRequest.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    private fun getResource(resourcePath: String): String? =
        javaClass.classLoader.getResource(resourcePath).readText()
}