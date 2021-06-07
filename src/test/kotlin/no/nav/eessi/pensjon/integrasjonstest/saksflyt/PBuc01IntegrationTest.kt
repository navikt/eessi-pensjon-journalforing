package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.handler.BehandleHendelseModel
import no.nav.eessi.pensjon.handler.HendelseKode
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.handler.OppgaveType.BEHANDLE_SED
import no.nav.eessi.pensjon.handler.OppgaveType.JOURNALFORING
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.klienter.pesys.BestemSakResponse
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet.AUTOMATISK_JOURNALFORING
import no.nav.eessi.pensjon.models.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.models.Enhet.PENSJON_UTLAND
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.HendelseType.MOTTATT
import no.nav.eessi.pensjon.models.HendelseType.SENDT
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

@DisplayName("P_BUC_01 – IntegrationTest")
internal class PBuc01IntegrationTest : JournalforingTestBase() {


    @Nested
    @DisplayName("Inngående")
    inner class InngaaendeP_BUC_01 {

        @Test
        fun `Krav om alderpensjon for inngående P2000 journalføres automatisk med bruk av bestemsak og det opprettes en oppgave type BEHANDLE_SED`() {
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(
                        sakId = SAK_ID,
                        sakType = Saktype.ALDER,
                        sakStatus = SakStatus.OPPRETTET
                    )
                )
            )
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P2000, SedStatus.RECEIVED))

            testRunnerVoksen(
                FNR_VOKSEN,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                krav = KravType.ALDER,
                forsokFerdigStilt = true,
                land = "SWE"
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(null, it.oppgaveMelding?.filnavn)
                assertEquals(PENSJON_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals(BEHANDLE_SED, it.oppgaveMelding?.oppgaveType)

                assertEquals(true, it.kravMeldingList?.isNotEmpty())
                assertEquals(1, it.kravMeldingList?.size)

                val kravMelding = it.kravMeldingList?.firstOrNull()
                assertEquals(HendelseKode.SOKNAD_OM_ALDERSPENSJON, kravMelding?.hendelsesKode)
                assertEquals("147729", kravMelding?.bucId)
                assertEquals(SAK_ID, kravMelding?.sakId)

            }

        }

        @Test
        fun `Krav om alderpensjon for inngående P2000 journalføres automatisk med bruk av bestemsak med ugyldig vedlegg og det opprettes to oppgaver type BEHANDLE_SED`() {
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(
                        sakId = SAK_ID,
                        sakType = Saktype.ALDER,
                        sakStatus = SakStatus.OPPRETTET
                    )
                )
            )
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P2000, SedStatus.RECEIVED))

            testRunnerVoksen(
                FNR_VOKSEN,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                krav = KravType.ALDER,
                forsokFerdigStilt = true,
                documentFiler = getDokumentfilerUtenGyldigVedlegg(),
                land = "SWE"
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, journalpostRequest.journalfoerendeEnhet)

                assertEquals(2, oppgaveMeldingList.size)

                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(null, it.oppgaveMelding?.filnavn)
                assertEquals(PENSJON_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals(BEHANDLE_SED, it.oppgaveMelding?.oppgaveType)

                assertNotNull(it.oppgaveMeldingUgyldig)
                assertEquals(BEHANDLE_SED, it.oppgaveMeldingUgyldig.oppgaveType)
                assertEquals(null, it.oppgaveMeldingUgyldig.journalpostId)
                assertEquals("docx.docx ", it.oppgaveMeldingUgyldig.filnavn)

                assertEquals(true, it.kravMeldingList?.isNotEmpty())
                assertEquals(1, it.kravMeldingList?.size)

                val kravMelding = it.kravMeldingList?.firstOrNull()
                assertEquals(HendelseKode.SOKNAD_OM_ALDERSPENSJON, kravMelding?.hendelsesKode)
                assertEquals("147729", kravMelding?.bucId)
                assertEquals(SAK_ID, kravMelding?.sakId)

            }

        }

        @Test
        fun `Krav om Alder P2000 journalføres automatisk med bruk av bestemsak uten forsokFerdigStilt`() {
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(
                        sakId = SAK_ID,
                        sakType = Saktype.ALDER,
                        sakStatus = SakStatus.OPPRETTET
                    )
                )
            )
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P2000, SedStatus.RECEIVED))

            testRunnerVoksen(
                FNR_VOKSEN,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                forsokFerdigStilt = false,
                land = "SWE"
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(AUTOMATISK_JOURNALFORING, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals("0123456789000", it.oppgaveMelding?.aktoerId)
                assertEquals(JOURNALFORING, it.oppgaveMelding?.oppgaveType)

            }
        }

        @Test
        fun `Krav om Alder P2000 feiler med bestemSak`() {
            val bestemsak = BestemSakResponse(null, emptyList())
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P2000, SedStatus.RECEIVED))

            testRunnerVoksen(
                FNR_VOKSEN,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                land = "SWE"
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(PENSJON_UTLAND, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(PENSJON_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals(JOURNALFORING, it.oppgaveMelding?.oppgaveType)
            }
        }

        @Test
        fun `Krav om Alder P2000 uten gyldig fnr sendes til ID og Fordeling`() {
            val bestemsak = BestemSakResponse(null, emptyList())
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P2000, SedStatus.RECEIVED))

            testRunnerVoksen(
                null,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                land = "SWE"
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(ID_OG_FORDELING, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(ID_OG_FORDELING, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals(null, it.oppgaveMelding?.aktoerId)
                assertEquals(JOURNALFORING, it.oppgaveMelding?.oppgaveType)

            }
        }

        @Test
        fun `Krav om Alder P2000 ingen fnr funnet benytter sokPerson finner person automatisk journalføring`() {
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(
                        sakId = SAK_ID,
                        sakType = Saktype.ALDER,
                        sakStatus = SakStatus.OPPRETTET
                    )
                )
            )

            val allDocuemtActions = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P2000, SedStatus.RECEIVED))


            testRunnerVoksenSokPerson(
                FNR_VOKSEN,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                land = "SWE",
                sokPerson = true
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(AUTOMATISK_JOURNALFORING, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals("0123456789000", it.oppgaveMelding?.aktoerId)
                assertEquals(JOURNALFORING, it.oppgaveMelding?.oppgaveType)
            }
        }
    }

    @Nested
    @DisplayName("Utgående")
    inner class UtgaaendeP_BUC_01 {

        @Test
        fun `Krav om alderpensjon for Utgående P2000 journalføres automatisk med bruk av bestemsak uten forsokFerdigStilt oppretter en oppgave type JOURNALFORING`() {
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(
                        sakId = SAK_ID,
                        sakType = Saktype.UFOREP,
                        sakStatus = SakStatus.LOPENDE
                    )
                )
            )
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P2200, SedStatus.SENT))

            testRunnerVoksen(
                FNR_VOKSEN,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = SENDT
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(UFORETRYGD, journalpostRequest.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(AUTOMATISK_JOURNALFORING, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals("0123456789000", it.oppgaveMelding?.aktoerId)
                assertEquals(HendelseType.SENDT, it.oppgaveMelding?.hendelseType)
                assertEquals(JOURNALFORING, it.oppgaveMelding?.oppgaveType)

            }

        }
        @Test
        fun `Krav om alderpensjon for P2000 journalføres manualt når bestemsak feiler`() {
            val bestemsak = BestemSakResponse(
                null, emptyList()
            )
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P2000, SedStatus.SENT))

            testRunnerVoksen(
                FNR_VOKSEN,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = SENDT
            ) {
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(PENSJON_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals("0123456789000", it.oppgaveMelding?.aktoerId)
                assertEquals(HendelseType.SENDT, it.oppgaveMelding?.hendelseType)
                assertEquals(JOURNALFORING, it.oppgaveMelding?.oppgaveType)

                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(PENSJON_UTLAND, journalpostRequest.journalfoerendeEnhet)
            }

        }

    }

    private fun testRunnerVoksenSokPerson(
        fnrVoksen: String,
        bestemSak: BestemSakResponse? = null,
        sakId: String? = SAK_ID,
        land: String = "NOR",
        krav: KravType = KravType.ALDER,
        alleDocs: List<ForenkletSED>,
        forsokFerdigStilt: Boolean = false,
        documentFiler: SedDokumentfiler = getDokumentfilerUtenVedlegg(),
        hendelseType: HendelseType,
        sokPerson: Boolean = true,
        block: (TestResult) -> Unit
    ) {

        val fnrSokVoken = if (sokPerson) null else fnrVoksen

        val mockPerson = createBrukerWith(fnrVoksen,  "Voksen ", "Forsikret", land, aktorId = AKTOER_ID)

        val sed = createSedPensjon(SedType.P2000, fnrSokVoken, eessiSaknr = sakId, krav = krav, pdlPerson = mockPerson)

        initCommonMocks(sed, alleDocs, documentFiler)


        if (sokPerson) {
            every { personService.sokPerson(any()) } returns setOf(IdentInformasjon(fnrVoksen, IdentGruppe.FOLKEREGISTERIDENT))
            every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns mockPerson
        }

        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak
        val (journalpost, _) = initJournalPostRequestSlot(forsokFerdigStilt)

        val hendelse = createHendelseJson(SedType.P2000, BucType.P_BUC_01)
        val meldingSlot = mutableListOf<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val kravmeldingSlot = mutableListOf<String>()
        every { kravInitHandlerKafka.sendDefault(any(), capture(kravmeldingSlot)).get() } returns mockk()
        every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns PENSJON_UTLAND

        when (hendelseType) {
            SENDT -> listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            else -> fail()
        }

        val kravMeldingList: List<BehandleHendelseModel> = kravmeldingSlot.map {
            mapJsonToAny(it, typeRefs<BehandleHendelseModel>())
        }
        val oppgaveMeldingList: List<OppgaveMelding> = meldingSlot.map {
            mapJsonToAny(it, typeRefs<OppgaveMelding>())
        }
        block(TestResult(journalpost.captured, oppgaveMeldingList, kravMeldingList))

        verify(exactly = 1) { euxService.hentBucDokumenter(any()) }
        if (fnrVoksen != null) verify { personService.hentPerson(any<Ident<*>>()) }
        verify(exactly = 1) { euxService.hentSed(any(), any()) }

        clearAllMocks()
    }

    private fun testRunnerVoksen(
        fnrVoksen: String?,
        bestemSak: BestemSakResponse? = null,
        sakId: String? = SAK_ID,
        land: String = "NOR",
        krav: KravType = KravType.ALDER,
        alleDocs: List<ForenkletSED>,
        forsokFerdigStilt: Boolean = false,
        documentFiler: SedDokumentfiler = getDokumentfilerUtenVedlegg(),
        hendelseType: HendelseType,
        block: (TestResult) -> Unit
    ) {
        val sed = createSedPensjon(SedType.P2000, fnrVoksen, eessiSaknr = sakId, krav = krav)
        initCommonMocks(sed, alleDocs, documentFiler)

        if (fnrVoksen != null) {
            every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns createBrukerWith(
                fnrVoksen,
                "Voksen ",
                "Forsikret",
                land,
                aktorId = AKTOER_ID
            )
        }

        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

        val (journalpost, _) = initJournalPostRequestSlot(forsokFerdigStilt)

        val hendelse = createHendelseJson(SedType.P2000, BucType.P_BUC_01)

        val meldingSlot = mutableListOf<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val kravmeldingSlot = mutableListOf<String>()
        every { kravInitHandlerKafka.sendDefault(any(), capture(kravmeldingSlot)).get() } returns mockk()

        every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns PENSJON_UTLAND

        when (hendelseType) {
            SENDT -> listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            else -> fail()
        }

        val kravMeldingList: List<BehandleHendelseModel> = kravmeldingSlot.map {
            mapJsonToAny(it, typeRefs<BehandleHendelseModel>())
        }
        val oppgaveMeldingList: List<OppgaveMelding> = meldingSlot.map {
            mapJsonToAny(it, typeRefs<OppgaveMelding>())
        }
        block(TestResult(journalpost.captured, oppgaveMeldingList, kravMeldingList))

        verify(exactly = 1) { euxService.hentBucDokumenter(any()) }
        if (fnrVoksen != null) verify { personService.hentPerson(any<Ident<*>>()) }
        verify(exactly = 1) { euxService.hentSed(any(), any()) }

        clearAllMocks()
    }

    private fun getResource(resourcePath: String): String =
        javaClass.getResource(resourcePath).readText()

    private fun getDokumentfilerUtenGyldigVedlegg(): SedDokumentfiler {
        val dokumentfilerJson = getResource("/pdf/pdfResponseMedUgyldigVedlegg.json")
        return mapJsonToAny(dokumentfilerJson, typeRefs())
    }

    data class TestResult(
        val opprettJournalpostRequest: OpprettJournalpostRequest,
        val oppgaveMeldingList: List<OppgaveMelding>,
        val kravMeldingList: List<BehandleHendelseModel>? = null
    ) {
        val oppgaveMeldingUgyldig = if (oppgaveMeldingList.size == 2) oppgaveMeldingList.first() else null
        val oppgaveMelding =
            if (oppgaveMeldingList.size == 2) oppgaveMeldingList.last() else if (oppgaveMeldingList.isNotEmpty()) oppgaveMeldingList.first() else null
    }
}
