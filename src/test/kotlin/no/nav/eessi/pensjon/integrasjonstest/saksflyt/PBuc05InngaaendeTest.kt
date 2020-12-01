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
import no.nav.eessi.pensjon.models.Enhet.PENSJON_UTLAND
import no.nav.eessi.pensjon.models.Enhet.UFORE_UTLAND
import no.nav.eessi.pensjon.models.Enhet.UFORE_UTLANDSTILSNITT
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("P_BUC_05 - Inngående Journalføring - IntegrationTest")
internal class PBuc05InngaaendeTest : JournalforingTestBase() {

    /**
     * P_BUC_05 INNGÅENDE
     */


    @Test
    fun `Scenario 1 - Kun én person, mangler FNR`() {
        testRunner(fnr = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 1 - Kun én person, ugyldig FNR`() {
        testRunner(fnr = "1244091349018340918341029", hendelseType = HendelseType.MOTTATT) {
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
    fun `Scenario 4 - én person, gyldig fnr`() {
        testRunner(FNR_OVER_60, sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
        }

        testRunner(FNR_VOKSEN, sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
        }

        testRunner(FNR_BARN, sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 4 - én person, gyldig fnr, bosatt utland`() {
        testRunner(FNR_OVER_60, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
        }

        testRunner(FNR_VOKSEN, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
        }

        testRunner(FNR_BARN, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 5 to personer angitt, gyldig fnr, rolle er 03, bosatt norge`() {
        testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = "03", sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            assertEquals(FNR_OVER_60, it.bruker!!.id)
        }

        testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = "03", sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            assertEquals(FNR_VOKSEN, it.bruker!!.id)
        }
    }

    @Test
    fun `Scenario 5 to personer angitt, gyldig fnr, rolle er 02, bosatt norge`() {
        testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = "02", sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            assertEquals(FNR_OVER_60, it.bruker!!.id)
        }

        testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = "02", sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            assertEquals(FNR_VOKSEN, it.bruker!!.id)
        }
    }

    @Test
    fun `Scenario 5 to personer angitt, gyldig fnr, rolle er 03, bosatt utland`() {
        testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = "03", sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            assertEquals(FNR_OVER_60, it.bruker!!.id)
        }

        testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = "03", sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
            assertEquals(FNR_VOKSEN, it.bruker!!.id)
        }
    }

    @Test
    fun `Scenario 5 to personer angitt, gyldig fnr, rolle er 02, bosatt utland`() {
        testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = "02", sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            assertEquals(FNR_OVER_60, it.bruker!!.id)
        }

        testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = "02", sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
            assertEquals(FNR_VOKSEN, it.bruker!!.id)
        }
    }

    @Test
    fun `Scenario 6 - To personer angitt, gyldig fnr, rolle 02 etterlatte, bosatt norge`() {
        testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, rolle = "01", sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
        }

        testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = "01", sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 6 - To personer angitt, gyldig fnr, rolle 02 etterlatte, bosatt utland`() {
        testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, rolle = "01", sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
        }

        testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = "01", sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
        }
    }

    private fun initCommonMocks(sed: String) {
        every { fagmodulKlient.hentAlleDokumenter(any()) } returns getResource("fagmodul/alldocumentsids.json")
        every { euxKlient.hentSed(any(), any()) } returns sed
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")
    }

    private fun getResource(resourcePath: String): String? =
            javaClass.classLoader.getResource(resourcePath)!!.readText()
}
