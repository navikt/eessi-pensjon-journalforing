package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.verify
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.models.Enhet.NFP_UTLAND_AALESUND
import no.nav.eessi.pensjon.models.Enhet.PENSJON_UTLAND
import no.nav.eessi.pensjon.models.Enhet.UFORE_UTLAND
import no.nav.eessi.pensjon.models.Enhet.UFORE_UTLANDSTILSNITT
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.Tema.PENSJON
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
        testRunnerFlerePersoner(fnr = null, fnrAnnenPerson = FNR_BARN, rolle = "03", sakId = SAK_ID, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 2 manglende eller feil i FNR, DNR for forsikret - to personer angitt, ROLLE 02`() {
        testRunnerFlerePersoner(fnr = null, fnrAnnenPerson = FNR_VOKSEN, rolle = "02", sakId = SAK_ID, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 3 manglende eller feil FNR-DNR - to personer angitt - etterlatte`() {
        testRunnerFlerePersoner(fnr = null, fnrAnnenPerson = FNR_VOKSEN, rolle = "01", sakId = SAK_ID, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 3 manglende eller feil FNR-DNR - to personer angitt - etterlatte med feil FNR for annen person, eller soker`() {
        testRunnerFlerePersoner(fnr = FNR_VOKSEN_2, fnrAnnenPerson = null, rolle = "01", sakId = SAK_ID, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
        }
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

    @Nested
    inner class Scenario5 {
        @Test
        fun `2 personer angitt, gyldig fnr og ugyldig annenperson, rolle er 02, bosatt utland del 1`() {
            initSed(createSed(SedType.P8000, FNR_OVER_60, createAnnenPerson(fnr = FNR_BARN, rolle = "02"), null))
            initDokumenter(getMockDocuments())

            initMockPerson(FNR_OVER_60, aktoerId = AKTOER_ID, land = "SWE")
            initMockPerson(FNR_BARN, aktoerId = null) // Ikke returnere noe på barn

            consumeAndAssert(HendelseType.MOTTATT, SedType.P8000, BucType.P_BUC_05) {
                assertEquals(PENSJON, it.request.tema)
                assertEquals(PENSJON_UTLAND, it.request.journalfoerendeEnhet)
                assertEquals(FNR_OVER_60, it.request.bruker!!.id)
            }

            verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
            verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
        }

        @Test
        fun `2 personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 02, bosatt utland del 2`() {
            val valgtFNR = FNR_VOKSEN
            initSed(createSed(SedType.P8000, valgtFNR, createAnnenPerson(fnr = FNR_BARN, rolle = "02"), null))
            initDokumenter(getMockDocuments())

            initMockPerson(valgtFNR, aktoerId = AKTOER_ID, land = "SWE")
            initMockPerson(FNR_BARN, aktoerId = null) // Ikke returnere noe på barn

            consumeAndAssert(HendelseType.MOTTATT, SedType.P8000, BucType.P_BUC_05) {
                assertEquals(PENSJON, it.request.tema)
                assertEquals(UFORE_UTLAND, it.request.journalfoerendeEnhet)
                assertEquals(valgtFNR, it.request.bruker!!.id)
            }

            verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
            verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
        }

        @Test
        fun `2 personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 02, bosatt Norge del 3`() {
            val valgtFNR = FNR_VOKSEN
            initSed(createSed(SedType.P8000, valgtFNR, createAnnenPerson(fnr = FNR_BARN, rolle = "02"), null))
            initDokumenter(getMockDocuments())

            initMockPerson(valgtFNR, aktoerId = AKTOER_ID)
            initMockPerson(fnr = FNR_BARN, aktoerId = null) // Ikke returnere noe på barn

            consumeAndAssert(HendelseType.MOTTATT, SedType.P8000, BucType.P_BUC_05) {
                assertEquals(PENSJON, it.request.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, it.request.journalfoerendeEnhet)
                assertEquals(valgtFNR, it.request.bruker!!.id)
            }

            verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
            verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
        }

        @Test
        fun `2 personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 02, bosatt Norge del 4`() {
            val valgtFNR = FNR_OVER_60
            initSed(createSed(SedType.P8000, valgtFNR, createAnnenPerson(fnr = FNR_BARN, rolle = "02"), null))
            initDokumenter(getMockDocuments())

            initMockPerson(valgtFNR, aktoerId = AKTOER_ID)
            initMockPerson(fnr = FNR_BARN, aktoerId = null) // Ikke returnere noe på barn

            consumeAndAssert(HendelseType.MOTTATT, SedType.P8000, BucType.P_BUC_05) {
                assertEquals(PENSJON, it.request.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.request.journalfoerendeEnhet)
                assertEquals(valgtFNR, it.request.bruker!!.id)
            }

            verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
            verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
        }

        @Test
        fun `2 personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 01, bosatt Norge del 4`() {
            val valgtFNR = FNR_OVER_60
            initSed(createSed(SedType.P8000, valgtFNR, createAnnenPerson(fnr = FNR_BARN, rolle = "01"), null))
            initDokumenter(getMockDocuments())

            initMockPerson(valgtFNR, aktoerId = AKTOER_ID)
            initMockPerson(fnr = FNR_BARN, aktoerId = null) // Ikke returnere noe på barn

            consumeAndAssert(HendelseType.MOTTATT, SedType.P8000, BucType.P_BUC_05) {
                assertEquals(PENSJON, it.request.tema)
                assertEquals(ID_OG_FORDELING, it.request.journalfoerendeEnhet)
                assertNull(it.request.bruker)
            }

            verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
            verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
        }
    }

    @Test
    fun `Scenario 6 - To personer angitt, gyldig fnr, rolle 02 etterlatte, bosatt norge`() {
        testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, rolle = "02", sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
        }

        testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = "02", sakId = null, hendelseType = HendelseType.MOTTATT) {
            assertEquals(PENSJON, it.tema)
            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 6 - To personer angitt, gyldig fnr, rolle 02 etterlatte, bosatt utland`() {
        testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, rolle = "02", sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
        }

        testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = "02", sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
        }
    }

}
