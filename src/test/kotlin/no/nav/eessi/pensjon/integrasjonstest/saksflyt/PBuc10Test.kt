package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.klienter.pesys.BestemSakResponse
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet.AUTOMATISK_JOURNALFORING
import no.nav.eessi.pensjon.models.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.models.Enhet.PENSJON_UTLAND
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class PBuc10Test : JournalforingTestBase() {

    companion object {
        private const val FNR_OVER_60 = "01115043352"
        private const val FNR_VOKSEN = "01119043352"
        private const val FNR_VOKSEN_2 = "01118543352"
        private const val FNR_BARN = "01110854352"

        private const val AKTOER_ID = "0123456789000"
        private const val AKTOER_ID_2 = "0009876543210"

        private const val SAK_ID = "12345"

        private const val KRAV_ALDER = "01"
        private const val KRAV_UFORE = "03"
        private const val KRAV_GJENLEV = "02"

    }

    @Test
    @Disabled
    fun `Scenario 4 - 2 personer i SED der fnr finnes, rolle er 02, land er Sverige og bestemsak finner flere saker Så journalføres det manuelt på tema PENSJON og enhet PENSJON_UTLAND`() {
        val sed = createSedJson(SedType.P8000, FNR_OVER_60, createAnnenPersonJson(fnr = FNR_VOKSEN_2, rolle = "02"), SAK_ID)
        initCommonMocks(sed, "")

        every { personV3Service.hentPerson(FNR_VOKSEN_2) } returns createBrukerWith(FNR_VOKSEN_2, "Fornavn Barn", "Etternavn barn", "SWE")
        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_OVER_60, "familiemedlem", "Etternavn", "SWE")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(AKTOER_ID)

        val saker = listOf(
                SakInformasjon(sakId = SAK_ID, sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                SakInformasjon(sakId = "34234123", sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING)
        )
        every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID) } returns saker

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured

        assertEquals(PENSJON, request.tema)
        assertEquals(PENSJON_UTLAND, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun Scenario1() {
        val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING)))
        val allDocuemtActions = mockAllDocumentsBuc( listOf(
                Triple("10001212", "P15000", "sent")
        ))

        testRunner(FNR_VOKSEN, bestemsak, alleDocs = allDocuemtActions) {
            assertEquals(PENSJON, it.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
        }

        testRunner(FNR_OVER_60, bestemsak, alleDocs = allDocuemtActions) {
            assertEquals(PENSJON, it.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun Scenario2() {
        val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)))
        val allDocuemtActions = mockAllDocumentsBuc( listOf(
                Triple("10001212", "P15000", "sent")
        ))

        testRunner(FNR_VOKSEN, bestemsak, krav = KRAV_UFORE, alleDocs = allDocuemtActions) {
            assertEquals(UFORETRYGD, it.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
        }

        testRunner(FNR_OVER_60, bestemsak, krav = KRAV_UFORE, alleDocs = allDocuemtActions) {
            assertEquals(UFORETRYGD, it.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun Scenario7() {
        val bestemsak = BestemSakResponse(null, listOf(
                        SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                        SakInformasjon(sakId = "123456", sakType = YtelseType.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)))

        val allDocuemtActions = mockAllDocumentsBuc( listOf(
                Triple("10001212", "P15000", "sent")
        ))

        testRunnerBarn(FNR_VOKSEN, FNR_BARN, bestemsak, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions) {
            assertEquals(PENSJON, it.tema)
            assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
        }

        testRunnerBarn(FNR_OVER_60, FNR_VOKSEN, bestemsak, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions) {
            assertEquals(PENSJON, it.tema)
            assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
        }

    }

    @Test
    fun Scenario8() {
        val allDocuemtActions = mockAllDocumentsBuc( listOf(
                Triple("10001212", "P15000", "sent")
        ))

        testRunner(null, krav = KRAV_ALDER, alleDocs = allDocuemtActions) {
            assertEquals(PENSJON, it.tema)
            assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
        }

    }

    @Test
    fun Scenario9() {
        val allDocuemtActions = mockAllDocumentsBuc( listOf(
                Triple("10001212", "P15000", "sent")
        ))

        testRunnerBarn(FNR_VOKSEN, null, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions) {
            assertEquals(PENSJON, it.tema)
            assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
        }

        testRunnerBarn(FNR_VOKSEN, FNR_BARN, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, relasjon = null) {
            assertEquals(PENSJON, it.tema)
            assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
        }


    }



    private fun testRunnerBarn(fnrVoksen: String,
                               fnrBarn: String?,
                               bestemSak: BestemSakResponse? = null,
                               sakId: String? = SAK_ID,
                               diskresjonkode: Diskresjonskode? = null,
                               land: String = "NOR",
                               krav: String = KRAV_ALDER,
                               alleDocs: String,
                               relasjon: String? = "06",
                               block: (OpprettJournalpostRequest) -> Unit
    ) {

        val sed = createP15000(fnrVoksen, eessiSaknr = sakId, krav = krav, gfn = fnrBarn, relasjon = relasjon)
        initCommonMocks(sed, alleDocs)

        every { personV3Service.hentPerson(fnrVoksen) } returns createBrukerWith(fnrVoksen, "Mamma forsørger", "Etternavn", land)
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnrVoksen)) } returns AktoerId(AKTOER_ID)

        if (fnrBarn != null) {
            every { personV3Service.hentPerson(fnrBarn) } returns createBrukerWith(fnrBarn, "Barn", "Diskret", land)
            every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnrBarn)) } returns AktoerId(AKTOER_ID_2)
        }
        every { bestemSakKlient.kallBestemSak( any()) } returns bestemSak

        val (journalpost, _) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P15000, BucType.P_BUC_10)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())
        assertEquals(HendelseType.SENDT, oppgaveMelding.hendelseType)

        block(journalpost.captured)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        if (fnrBarn != null) {
            verify(exactly = 6) { personV3Service.hentPerson(any()) }
            verify(exactly = 2) { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, any<NorskIdent>()) }
        } else {
            verify(exactly = 2) { personV3Service.hentPerson(any()) }
            verify(exactly = 1) { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, any<NorskIdent>()) }
        }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }

        clearAllMocks()
    }

    private fun testRunner(fnr1: String?,
                           bestemSak: BestemSakResponse? = null,
                           sakId: String? = SAK_ID,
                           land: String = "NOR",
                           krav: String = KRAV_ALDER,
                           alleDocs: String,
                           block: (OpprettJournalpostRequest) -> Unit
    ) {

        val sed = createP15000(fnr1, eessiSaknr = sakId, krav = krav)
        initCommonMocks(sed, alleDocs)

        if (fnr1 != null) {
            every { personV3Service.hentPerson(fnr1) } returns createBrukerWith(fnr1, "Fornavn", "Etternavn", land)
            every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr1)) } returns AktoerId(AKTOER_ID)
            every { bestemSakKlient.kallBestemSak( any()) } returns bestemSak
        }

        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P15000, BucType.P_BUC_10)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        block(journalpost.captured)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }

        if (bestemSak == null)
            verify(exactly = 0) { bestemSakKlient.kallBestemSak(any()) }
        else
            verify(exactly = 1) { bestemSakKlient.kallBestemSak(any()) }

        clearAllMocks()
    }

    private fun initCommonMocks(sed: String, alleDocs: String) {
        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocs
        every { euxKlient.hentSed(any(), any()) } returns sed
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")
    }

    private fun getResource(resourcePath: String): String? =
            javaClass.classLoader.getResource(resourcePath)!!.readText()
}
