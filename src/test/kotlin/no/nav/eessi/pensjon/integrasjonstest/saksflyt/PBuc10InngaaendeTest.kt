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
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test


@DisplayName("P_BUC_10 - Inngående journalføring - IntegrationTest")
internal class PBuc10InngaaendeTest : JournalforingTestBase() {

    companion object {
        private const val KRAV_ALDER = "01"
        private const val KRAV_UFORE = "03"
        private const val KRAV_GJENLEV = "02"
    }

    @Nested
    inner class Scenario1 {

        @Test
        fun `Krav om alderspensjon`() {

            val allDocuemtActions = mockAllDocumentsBuc( listOf(
                    Triple("10001212", "P15000", "received")
            ))

            testRunner(FNR_VOKSEN_2, null, alleDocs = allDocuemtActions, land = "SWE") {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_OVER_60, null, alleDocs = allDocuemtActions) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

        }

        @Test
        fun `Krav om etterlatteytelser`() {

            val allDocuemtActions = mockAllDocumentsBuc( listOf(
                    Triple("10001212", "P15000", "received")
            ))

            testRunnerBarn(FNR_VOKSEN_2, null, alleDocs = allDocuemtActions, land = "SWE", krav = KRAV_GJENLEV) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerBarn(FNR_OVER_60, FNR_BARN, alleDocs = allDocuemtActions, krav = KRAV_GJENLEV) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerBarn(FNR_OVER_60, FNR_BARN, alleDocs = allDocuemtActions, krav = KRAV_GJENLEV, land = "SWE") {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

        }

    }

    @Nested
    inner class Scenario2 {


        @Test
        fun `Krav om uføretrygd`() {

            val allDocuemtActions = mockAllDocumentsBuc( listOf(
                    Triple("10001212", "P15000", "received")
            ))

            testRunner(FNR_VOKSEN, null, alleDocs = allDocuemtActions, land = "SWE", krav = KRAV_UFORE) {
                Assertions.assertEquals(Tema.UFORETRYGD, it.tema)
                Assertions.assertEquals(Enhet.UFORE_UTLAND, it.journalfoerendeEnhet)
            }
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
                               relasjonAvod: String? = "06",
                               sedJson: String? = null,
                               block: (OpprettJournalpostRequest) -> Unit
    ) {

        val sed = sedJson ?: createP15000(fnrVoksen, eessiSaknr = sakId, krav = krav, gfn = fnrBarn, relasjon = relasjonAvod)
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

        listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())
        Assertions.assertEquals(HendelseType.MOTTATT, oppgaveMelding.hendelseType)

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

        listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

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

    private fun getResource(resourcePath: String): String? = javaClass.classLoader.getResource(resourcePath)!!.readText()
}
