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
import no.nav.eessi.pensjon.models.sed.Document
import no.nav.eessi.pensjon.models.sed.Rolle
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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
        val sed = createSed(SedType.P8000, null, createAnnenPerson(fnr = FNR_BARN, rolle = Rolle.BARN), SAK_ID)
        initCommonMocks(sed)

        val barn = createBrukerWith(FNR_BARN, "Barn", "Vanlig", "NOR", "1213")
        every { personService.hentPerson(NorskIdent(FNR_BARN)) } returns barn

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val (journalpost, _) = initJournalPostRequestSlot()

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
        val sed = createSed(SedType.P8000, null, createAnnenPerson(fnr = FNR_VOKSEN, rolle = Rolle.FORSORGER), SAK_ID)
        initCommonMocks(sed)

        val voksen = createBrukerWith(FNR_VOKSEN, "Voksen", "Vanlig", "NOR", "1213")
        every { personService.hentPerson(NorskIdent(FNR_VOKSEN)) } returns voksen

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val (journalpost, _) = initJournalPostRequestSlot()

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
        val sed = createSed(SedType.P8000, null, createAnnenPerson(fnr = FNR_VOKSEN, rolle = Rolle.ETTERLATTE), SAK_ID)
        initCommonMocks(sed)

        val voksen = createBrukerWith(FNR_VOKSEN, "Voksen", "Vanlig", "NOR", "1213")
        every { personService.hentPerson(NorskIdent(FNR_VOKSEN)) } returns voksen

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val (journalpost, _) = initJournalPostRequestSlot()

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
        val sed = createSed(SedType.P8000, FNR_VOKSEN_2, createAnnenPerson(fnr = null, rolle = Rolle.ETTERLATTE), SAK_ID)
        initCommonMocks(sed)

        val voksen = createBrukerWith(FNR_VOKSEN_2, "Voksen", "Vanlig", "NOR", "1213")
        every { personService.hentPerson(NorskIdent(FNR_VOKSEN_2)) } returns voksen

        val hendelse = createHendelseJson(SedType.P8000)
        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val (journalpost, _) = initJournalPostRequestSlot()

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
        testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = Rolle.BARN, sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            assertEquals(FNR_OVER_60, it.bruker!!.id)
        }

        testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = Rolle.BARN, sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            assertEquals(FNR_VOKSEN, it.bruker!!.id)
        }
    }

    @Test
    fun `Scenario 5 to personer angitt, gyldig fnr, rolle er 02, bosatt norge`() {
        testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = Rolle.FORSORGER, sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            assertEquals(FNR_OVER_60, it.bruker!!.id)
        }

        testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = Rolle.FORSORGER, sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            assertEquals(FNR_VOKSEN, it.bruker!!.id)
        }
    }

    @Test
    fun `Scenario 5 to personer angitt, gyldig fnr, rolle er 03, bosatt utland`() {
        testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = Rolle.BARN, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            assertEquals(FNR_OVER_60, it.bruker!!.id)
        }

        testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = Rolle.BARN, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
            assertEquals(FNR_VOKSEN, it.bruker!!.id)
        }
    }

    @Test
    fun `Scenario 5 to personer angitt, gyldig fnr, rolle er 02, bosatt utland`() {
        testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = Rolle.FORSORGER, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            assertEquals(FNR_OVER_60, it.bruker!!.id)
        }

        testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = Rolle.FORSORGER, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
            assertEquals(FNR_VOKSEN, it.bruker!!.id)
        }
    }

    @Nested
    inner class Scenario5 {
        @Test
        fun `2 personer angitt, gyldig fnr og ugyldig annenperson, rolle er 02, bosatt utland del 1`() {
            val sed = createSed(SedType.P8000, FNR_OVER_60, createAnnenPerson(fnr = FNR_BARN, rolle = Rolle.FORSORGER), null)
            initCommonMocks(sed)

            val voksen = createBrukerWith(FNR_OVER_60, "Voksen", "Vanlig", "SWE", "1213")
            every { personService.hentPerson(NorskIdent(FNR_OVER_60)) } returns voksen
            every { personService.hentPerson(NorskIdent(FNR_BARN)) } returns null

            val hendelse = createHendelseJson(SedType.P8000)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

            assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())

            val request = journalpost.captured
            assertEquals(PENSJON, request.tema)
            assertEquals(PENSJON_UTLAND, request.journalfoerendeEnhet)
            assertEquals(FNR_OVER_60, request.bruker!!.id)

            verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
            verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
        }

        @Test
        fun `2 personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 02, bosatt utland del 2`() {
            val valgtFNR = FNR_VOKSEN
            val sed = createSed(SedType.P8000, valgtFNR, createAnnenPerson(fnr = FNR_BARN, rolle = Rolle.FORSORGER), null)
            initCommonMocks(sed)

            val voksen = createBrukerWith(valgtFNR, "Voksen", "Vanlig", "SWE", "1213")
            every { personService.hentPerson(NorskIdent(valgtFNR)) } returns voksen
            every { personService.hentPerson(NorskIdent(FNR_BARN)) } returns null

            val hendelse = createHendelseJson(SedType.P8000)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

            assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())

            val request = journalpost.captured
            assertEquals(PENSJON, request.tema)
            assertEquals(UFORE_UTLAND, request.journalfoerendeEnhet)
            assertEquals(valgtFNR, request.bruker!!.id)

            verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
            verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
        }

        @Test
        fun `2 personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 02, bosatt Norge del 3`() {
            val valgtFNR = FNR_VOKSEN
            val sed = createSed(SedType.P8000, valgtFNR, createAnnenPerson(fnr = FNR_BARN, rolle = Rolle.FORSORGER), null)
            initCommonMocks(sed)

            val voksen = createBrukerWith(valgtFNR, "Voksen", "Vanlig", "NOR", "1213")
            every { personService.hentPerson(NorskIdent(valgtFNR)) } returns voksen
            every { personService.hentPerson(NorskIdent(FNR_BARN)) } returns null

            val hendelse = createHendelseJson(SedType.P8000)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

            assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())

            val request = journalpost.captured
            assertEquals(PENSJON, request.tema)
            assertEquals(UFORE_UTLANDSTILSNITT, request.journalfoerendeEnhet)
            assertEquals(valgtFNR, request.bruker!!.id)

            verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
            verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
        }

        @Test
        fun `2 personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 02, bosatt Norge del 4`() {
            val valgtFNR = FNR_OVER_60
            val sed = createSed(SedType.P8000, valgtFNR, createAnnenPerson(fnr = FNR_BARN, rolle = Rolle.FORSORGER), null)
            initCommonMocks(sed)

            val voksen = createBrukerWith(valgtFNR, "Voksen", "Vanlig", "NOR", "1213")
            every { personService.hentPerson(NorskIdent(valgtFNR)) } returns voksen
            every { personService.hentPerson(NorskIdent(FNR_BARN)) } returns null

            val hendelse = createHendelseJson(SedType.P8000)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

            assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())

            val request = journalpost.captured
            assertEquals(PENSJON, request.tema)
            assertEquals(NFP_UTLAND_AALESUND, request.journalfoerendeEnhet)
            assertEquals(valgtFNR, request.bruker!!.id)

            verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
            verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
        }

        @Test
        fun `2 personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 01, bosatt Norge del 4`() {
            val valgtFNR = FNR_OVER_60
            val sed = createSed(SedType.P8000, valgtFNR, createAnnenPerson(fnr = FNR_BARN, rolle = Rolle.ETTERLATTE), null)
            initCommonMocks(sed)

            val voksen = createBrukerWith(valgtFNR, "Voksen", "Vanlig", "NOR", "1213")
            every { personService.hentPerson(NorskIdent(valgtFNR)) } returns voksen
            every { personService.hentPerson(NorskIdent(FNR_BARN)) } returns null

            val hendelse = createHendelseJson(SedType.P8000)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

            assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())

            val request = journalpost.captured
            assertEquals(PENSJON, request.tema)
            assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)
            assertNull(request.bruker)

            verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
            verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
        }

    }

//    @Test
//    fun `Scenario 5 to personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 02, bosatt Norge`() {
//        testRunnerFlerePersoner(FNR_OVER_60, "231231", rolle = "02", sakId = null, hendelseType = HendelseType.MOTTATT, land = "NOR") {
//            assertEquals(PENSJON, it.tema)
//            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
//            assertEquals(FNR_OVER_60, it.bruker!!.id)
//        }
//
//        testRunnerFlerePersoner(FNR_VOKSEN, "231231", rolle = "02", sakId = null, hendelseType = HendelseType.MOTTATT, land = "NOR") {
//            assertEquals(PENSJON, it.tema)
//            assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
//            assertEquals(FNR_VOKSEN, it.bruker!!.id)
//        }
//
//    }

    @Test
    fun `Scenario 6 - To personer angitt, gyldig fnr, rolle 02 etterlatte, bosatt norge`() {
        testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, rolle = Rolle.ETTERLATTE, sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
        }

        testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = Rolle.ETTERLATTE, sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 6 - To personer angitt, gyldig fnr, rolle 02 etterlatte, bosatt utland`() {
        testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, rolle = Rolle.ETTERLATTE, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
        }

        testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = Rolle.ETTERLATTE, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
        }
    }

    private fun initCommonMocks(sed: SED) {
        val documents = mapJsonToAny(getResource("/fagmodul/alldocumentsids.json"), typeRefs<List<Document>>())

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns documents
        every { euxKlient.hentSed(any(), any()) } returns sed
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("/pdf/pdfResponseUtenVedlegg.json")
    }

    private fun getResource(resourcePath: String): String =
            javaClass.getResource(resourcePath).readText()
}
