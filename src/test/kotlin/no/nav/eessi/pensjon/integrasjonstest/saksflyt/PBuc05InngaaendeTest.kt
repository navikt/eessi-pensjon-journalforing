package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.models.Enhet.NFP_UTLAND_AALESUND
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

//@Disabled
@DisplayName("P_BUC_05 - Inngående Journalføring - IntegrationTest")
internal class PBuc05InngaaendeTest : JournalforingTestBase() {

    companion object {
        private const val FNR_OVER_60 = "01115043352"
        private const val FNR_VOKSEN = "01119043352"
        private const val FNR_VOKSEN_2 = "01118543352"
        private const val FNR_BARN = "01110854352"

        private const val AKTOER_ID = "0123456789000"
        private const val AKTOER_ID_2 = "0009876543210"

        private const val SAK_ID = "12345"
    }

    /**
     * P_BUC_05 INNGÅENDE
     */


    @Test
    fun `Scenario 1 - Kun én person, mangler FNR`() {
        testRunner(fnr = null) {
            assertEquals(PENSJON, it.tema)
            assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 1 - Kun én person, ugyldig FNR`() {
        testRunner(fnr = "1244091349018340918341029") {
            assertEquals(PENSJON, it.tema)
            assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 2 manglende eller feil i FNR, DNR for forsikret - to personer angitt, ROLLE 03`() {
        val sed = createSedJson(SedType.P8000, null, createAnnenPersonJson(fnr = FNR_BARN, rolle = "03"), SAK_ID)
        initCommonMocks(sed)

        val barn = createBrukerWith(FNR_BARN, "Barn", "Vanlig", "NOR", "1213", null)
        every { personV3Service.hentPerson(FNR_BARN) } returns barn

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_BARN)) } returns AktoerId(AKTOER_ID)

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())

        val request = journalpost.captured
        // forvent tema == PEN og enhet 9999
        assertEquals(PENSJON, request.tema)
        assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 2 manglende eller feil i FNR, DNR for forsikret - to personer angitt, ROLLE 02`() {
        val sed = createSedJson(SedType.P8000, null, createAnnenPersonJson(fnr = FNR_VOKSEN, rolle = "02"), SAK_ID)
        initCommonMocks(sed)

        val voksen = createBrukerWith(FNR_VOKSEN, "Voksen", "Vanlig", "NOR", "1213", null)
        every { personV3Service.hentPerson(FNR_VOKSEN) } returns voksen

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())

        val request = journalpost.captured
        assertEquals(PENSJON, request.tema)
        assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }


    @Test
    fun `Scenario 3 manglende eller feil FNR-DNR - to personer angitt - etterlatte`() {
        val sed = createSedJson(SedType.P8000, null, createAnnenPersonJson(fnr = FNR_VOKSEN, rolle = "01"), SAK_ID)
        initCommonMocks(sed)

        val voksen = createBrukerWith(FNR_VOKSEN, "Voksen", "Vanlig", "NOR", "1213", null)
        every { personV3Service.hentPerson(FNR_VOKSEN) } returns voksen

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())

        val request = journalpost.captured
        // forvent tema == PEN og enhet 9999
        assertEquals(PENSJON, request.tema)
        assertEquals(NFP_UTLAND_AALESUND, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 3 manglende eller feil FNR-DNR - to personer angitt - etterlatte med feil FNR for annen person, eller soker`() {
        val sed = createSedJson(SedType.P8000, FNR_VOKSEN_2, createAnnenPersonJson(fnr = null, rolle = "01"), SAK_ID)
        initCommonMocks(sed)

        val voksen = createBrukerWith(FNR_VOKSEN_2, "Voksen", "Vanlig", "NOR", "1213", null)
        every { personV3Service.hentPerson(FNR_VOKSEN_2) } returns voksen

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())

        val request = journalpost.captured
        // forvent tema == PEN og enhet 9999
        assertEquals(PENSJON, request.tema)
        assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 4 to personer angitt, norsk fnr eller dnr er oppgitt og er riktig for den forsikrede ,rolle er 02, forsikret`() {
        val sed = createSedJson(SedType.P8000, FNR_OVER_60, createAnnenPersonJson(fnr = FNR_VOKSEN, rolle = "02"), SAK_ID)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_VOKSEN, "Hovedpersonen", "forsikret", "NOR")
        every { personV3Service.hentPerson(FNR_VOKSEN) } returns createBrukerWith(FNR_OVER_60, "Ikke hovedperson", "familiemedlem", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_VOKSEN)) } returns AktoerId(AKTOER_ID)
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(AKTOER_ID_2)


        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())

        val request = journalpost.captured

        assertEquals(PENSJON, request.tema)
        assertEquals(NFP_UTLAND_AALESUND, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 4 to personer angitt, norsk fnr eller dnr er oppgitt og er riktig for den forsikrede ,rolle er 03, forsikret`() {
        val sed = createSedJson(SedType.P8000, FNR_OVER_60, createAnnenPersonJson(fnr = FNR_VOKSEN, rolle = "03"), SAK_ID)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_VOKSEN, "Hovedpersonen", "forsikret", "NOR")
        every { personV3Service.hentPerson(FNR_VOKSEN) } returns createBrukerWith(FNR_OVER_60, "Ikke hovedperson", "familiemedlem", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_VOKSEN)) } returns AktoerId(AKTOER_ID)
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(AKTOER_ID_2)


        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())

        val request = journalpost.captured

        assertEquals(PENSJON, request.tema)
        assertEquals(NFP_UTLAND_AALESUND, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }


    private fun initCommonMocks(sed: String) {
        every { fagmodulKlient.hentAlleDokumenter(any()) } returns getResource("fagmodul/alldocumentsids.json")
        every { euxKlient.hentSed(any(), any()) } returns sed
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")
    }

    private fun getResource(resourcePath: String): String? =
            javaClass.classLoader.getResource(resourcePath)!!.readText()
}
