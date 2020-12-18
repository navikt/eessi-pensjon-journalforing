package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostType
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.klienter.pesys.BestemSakResponse
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.models.sed.DocStatus
import no.nav.eessi.pensjon.models.sed.Document
import no.nav.eessi.pensjon.models.sed.KravType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test


@DisplayName("P_BUC_10 - Inngående journalføring - IntegrationTest")
internal class PBuc10InngaaendeTest : JournalforingTestBase() {

    @Nested
    inner class Scenario1 {

        @Test
        fun `Krav om alderspensjon`() {

            val allDocuemtActions = listOf(
                    Document("10001212", SedType.P15000, DocStatus.RECEIVED)
            )

            testRunner(FNR_VOKSEN_2, null, alleDocs = allDocuemtActions, land = "SWE") {
                assertEquals(Tema.PENSJON, it.tema)
                assertEquals(Enhet.PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_OVER_60, null, alleDocs = allDocuemtActions) {
                assertEquals(Tema.PENSJON, it.tema)
                assertEquals(Enhet.NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

        }

        @Test
        fun `Krav om etterlatteytelser`() {

            val allDocuemtActions = listOf(
                    Document("10001212", SedType.P15000, DocStatus.RECEIVED)
            )

            testRunner(FNR_VOKSEN_2, null, alleDocs = allDocuemtActions, land = "SWE", krav = KravType.ETTERLATTE) {
                assertEquals(Tema.PENSJON, it.tema)
                assertEquals(Enhet.ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_OVER_60, FNR_BARN, alleDocs = allDocuemtActions, krav = KravType.ETTERLATTE) {
                assertEquals(Tema.PENSJON, it.tema)
                assertEquals(Enhet.NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_OVER_60, FNR_BARN, alleDocs = allDocuemtActions, krav = KravType.ETTERLATTE, land = "SWE") {
                assertEquals(Tema.PENSJON, it.tema)
                assertEquals(Enhet.PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

        }

    }

    @Nested
    inner class Scenario2 {


        @Test
        fun `Krav om uføretrygd`() {

            val allDocuemtActions = listOf(
                    Document("10001212", SedType.P15000, DocStatus.RECEIVED)
            )

            testRunner(FNR_VOKSEN, null, alleDocs = allDocuemtActions, land = "SWE", krav = KravType.UFORE) {
                assertEquals(Tema.UFORETRYGD, it.tema)
                assertEquals(Enhet.UFORE_UTLAND, it.journalfoerendeEnhet)
            }
        }


    }

    @Test
    fun `Scenario 1  - Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav ALDER skal routes til NFP_UTLAND_AALESUND 4862`() {
        initSed(
                createSedPensjon(SedType.P15000, FNR_OVER_60, krav = KravType.ALDER),
                createSedPensjon(SedType.P5000, FNR_OVER_60)
        )
        initDokumenter(
                Document("10001", SedType.P15000, DocStatus.SENT),
                Document("30002", SedType.P5000, DocStatus.RECEIVED)
        )

        initMockPerson(FNR_OVER_60, aktoerId = AKTOER_ID)

        consumeAndAssert(HendelseType.MOTTATT, SedType.P5000, BucType.P_BUC_10) {
            assertEquals("JOURNALFORING", it.melding!!.oppgaveType())
            assertEquals(Enhet.NFP_UTLAND_AALESUND, it.melding.tildeltEnhetsnr)
            assertEquals(SedType.P5000, it.melding.sedType)

            assertEquals(it.response.journalpostId, it.melding.journalpostId)

            assertEquals(JournalpostType.INNGAAENDE, it.request.journalpostType)
            assertEquals(Tema.PENSJON, it.request.tema)
            assertEquals(Enhet.NFP_UTLAND_AALESUND, it.request.journalfoerendeEnhet)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 1  - Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav ALDER bosatt utland skal routes til PENSJON_UTLAND 0001`() {
        initSed(
                createSedPensjon(SedType.P15000, FNR_OVER_60, krav = KravType.ALDER),
                createSedPensjon(SedType.P5000, FNR_OVER_60)
        )
        initDokumenter(
                Document("10001", SedType.P15000, DocStatus.SENT),
                Document("30002", SedType.P5000, DocStatus.RECEIVED)
        )

        initMockPerson(FNR_OVER_60, aktoerId = AKTOER_ID, land = "SWE")

        consumeAndAssert(HendelseType.MOTTATT, SedType.P5000, BucType.P_BUC_10) {
            assertEquals("JOURNALFORING", it.melding!!.oppgaveType())
            assertEquals(Enhet.PENSJON_UTLAND, it.melding.tildeltEnhetsnr)
            assertEquals(SedType.P5000, it.melding.sedType)

            assertEquals(it.response.journalpostId, it.melding.journalpostId)

            assertEquals("INNGAAENDE", it.request.journalpostType.name)
            assertEquals(Tema.PENSJON, it.request.tema)
            assertEquals(Enhet.PENSJON_UTLAND, it.request.journalfoerendeEnhet)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 1  - Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav UFOEREP skal routes til UFORE_UTLANDSTILSNITT 4476`() {
        initSed(
                createSedPensjon(SedType.P15000, FNR_VOKSEN, krav = KravType.UFORE),
                createSedPensjon(SedType.P5000, FNR_VOKSEN)
        )
        initDokumenter(
                Document("10001", SedType.P15000, DocStatus.SENT),
                Document("30002", SedType.P5000, DocStatus.RECEIVED)
        )

        initMockPerson(FNR_VOKSEN, aktoerId = AKTOER_ID)

        consumeAndAssert(HendelseType.MOTTATT, SedType.P5000, BucType.P_BUC_10) {
            assertEquals("JOURNALFORING", it.melding!!.oppgaveType())
            assertEquals(Enhet.UFORE_UTLANDSTILSNITT, it.melding.tildeltEnhetsnr)
            assertEquals(SedType.P5000, it.melding.sedType)

            assertEquals(it.response.journalpostId, it.melding.journalpostId)

            assertEquals(JournalpostType.INNGAAENDE, it.request.journalpostType)
            assertEquals(Tema.UFORETRYGD, it.request.tema)
            assertEquals(Enhet.UFORE_UTLANDSTILSNITT, it.request.journalfoerendeEnhet)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 1  - Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav UFOEREP bosatt utland skal routes til UFORE_UTLAND 4475`() {
        initSed(
                createSedPensjon(SedType.P15000, FNR_VOKSEN, krav = KravType.UFORE),
                createSedPensjon(SedType.P5000, FNR_VOKSEN)
        )
        initDokumenter(
                Document("10001", SedType.P15000, DocStatus.SENT),
                Document("30002", SedType.P5000, DocStatus.RECEIVED)
        )
        initMockPerson(FNR_VOKSEN, aktoerId = AKTOER_ID, land = "SWE")

        consumeAndAssert(HendelseType.MOTTATT, SedType.P5000, BucType.P_BUC_10) {
            assertEquals("JOURNALFORING", it.melding!!.oppgaveType())
            assertEquals(Enhet.UFORE_UTLAND, it.melding.tildeltEnhetsnr)
            assertEquals("P5000", it.melding.sedType?.name)

            assertEquals(it.response.journalpostId, it.melding.journalpostId)

            assertEquals(JournalpostType.INNGAAENDE, it.request.journalpostType)
            assertEquals(Tema.UFORETRYGD, it.request.tema)
            assertEquals(Enhet.UFORE_UTLAND, it.request.journalfoerendeEnhet)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 4  - Flere sed i buc, mottar en P15000 med ukjent gjenlevende relasjon, krav GJENLEV sender en P5000 med korrekt gjenlevende denne skal journalføres automatisk`() {
        initSed(
                createSedPensjon(SedType.P15000, FNR_OVER_60, gjenlevendeFnr = "", krav = KravType.ETTERLATTE, relasjon = "01"),
                createSedPensjon(SedType.P5000, FNR_OVER_60, gjenlevendeFnr = FNR_VOKSEN_2, eessiSaknr = SAK_ID)
        )
        initSaker(AKTOER_ID_2,
                SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                SakInformasjon(sakId = "34234123", sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET)
        )
        initDokumenter(
                Document("10001", SedType.P15000, DocStatus.RECEIVED),
                Document("30002", SedType.P5000, DocStatus.SENT)
        )

        initMockPerson(FNR_OVER_60, aktoerId = AKTOER_ID, land = "SWE")
        initMockPerson(FNR_VOKSEN_2, aktoerId = AKTOER_ID_2, land = "SWE")

        consumeAndAssert(HendelseType.SENDT, SedType.P5000, BucType.P_BUC_10, ferdigstilt = true) {
            assertNull(it.melding)

            assertEquals(JournalpostType.UTGAAENDE, it.request.journalpostType)
            assertEquals(Tema.PENSJON, it.request.tema)
            assertEquals(Enhet.AUTOMATISK_JOURNALFORING, it.request.journalfoerendeEnhet)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    private fun testRunner(forsikretFnr: String?,
                           gjenlevFnr: String? = null,
                           bestemSak: BestemSakResponse? = null,
                           sakId: String? = SAK_ID,
                           land: String = "NOR",
                           krav: KravType = KravType.ALDER,
                           alleDocs: List<Document>,
                           relasjonAvdod: String? = "06",
                           block: (OpprettJournalpostRequest) -> Unit
    ) {

        initSed(createSedPensjon(SedType.P15000, forsikretFnr, eessiSaknr = sakId, krav = krav, gjenlevendeFnr = gjenlevFnr, relasjon = relasjonAvdod))
        initDokumenter(alleDocs)

        if (forsikretFnr != null) initMockPerson(forsikretFnr, aktoerId = AKTOER_ID, land = land)

        if (gjenlevFnr != null) initMockPerson(gjenlevFnr, aktoerId = AKTOER_ID_2, land = land)

        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

        consumeAndAssert(HendelseType.MOTTATT, SedType.P15000, BucType.P_BUC_10) {
            block(it.request)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }

        if (bestemSak == null)
            verify(exactly = 0) { bestemSakKlient.kallBestemSak(any()) }
        else
            verify(exactly = 1) { bestemSakKlient.kallBestemSak(any()) }

        clearAllMocks()
    }

}
