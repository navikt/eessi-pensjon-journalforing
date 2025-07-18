package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.*
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_03
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.document.SedStatus.RECEIVED
import no.nav.eessi.pensjon.eux.model.document.SedStatus.SENT
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.eux.model.sed.P2200
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.journalforing.OpprettJournalpostRequest
import no.nav.eessi.pensjon.journalforing.krav.BehandleHendelseModel
import no.nav.eessi.pensjon.journalforing.krav.HendelseKode
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveType
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveType.JOURNALFORING_UT
import no.nav.eessi.pensjon.listeners.pesys.BestemSakResponse
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("P_BUC_03 – IntegrationTest")
internal class PBuc03IntegrationTest : JournalforingTestBase() {

    @Nested
    @DisplayName("Inngående P Buc 03")
    inner class InngaaendePBuc03 {

        @Test
        fun `Krav om uføre for inngående P2200 journalføres automatisk med bruk av bestemsak og det opprettes en oppgave type BEHANDLE_SED`() {
            val bestemsak = bestemSakResponse()
            val allDocuemtActions = forenkletSED()

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                forsokFerdigStilt = true,
                land = "SWE"
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(UFORETRYGD, journalpostRequest.tema)
                assertEquals(UFORE_UTLAND, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(null, it.oppgaveMelding?.filnavn)
                assertEquals(UFORE_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals(OppgaveType.BEHANDLE_SED, it.oppgaveMelding?.oppgaveType)

                assertEquals(true, it.kravMeldingList?.isNotEmpty())
                assertEquals(1, it.kravMeldingList?.size)

                val kravMelding = it.kravMeldingList?.firstOrNull()
                assertEquals(HendelseKode.SOKNAD_OM_UFORE, kravMelding?.hendelsesKode)
                assertEquals("147729", kravMelding?.bucId)
                assertEquals(SAK_ID, kravMelding?.sakId)

            }
        }

        @Test
        fun `Krav om uføre for inngående P2200 journalføres automatisk med bruk av bestemsak med ugyldig vedlegg og det opprettes to oppgaver type BEHANDLE_SED`() {
            val bestemsak = bestemSakResponse()
            val allDocuemtActions = forenkletSED()

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                forsokFerdigStilt = true,
                documentFiler = getDokumentfilerUtenGyldigVedlegg(),
                land = "SWE",
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(UFORETRYGD, journalpostRequest.tema)
                assertEquals(UFORE_UTLAND, journalpostRequest.journalfoerendeEnhet)

                println("************** ${oppgaveMeldingList}")

                assertEquals(2, oppgaveMeldingList.size)

                assertEquals(null, it.oppgaveMeldingUgyldig?.journalpostId)
                assertEquals("docx.docx ", it.oppgaveMeldingUgyldig?.filnavn)
                assertEquals(UFORE_UTLAND, it.oppgaveMeldingUgyldig?.tildeltEnhetsnr)
                assertEquals(OppgaveType.BEHANDLE_SED, it.oppgaveMeldingUgyldig?.oppgaveType)

                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(null, it.oppgaveMelding?.filnavn)
                assertEquals(UFORE_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals(OppgaveType.BEHANDLE_SED, it.oppgaveMelding?.oppgaveType)
            }
        }


        @Test
        fun `Krav om uføre for inngående P2200 journalføres automatisk med bruk av bestemsak uten forsokFerdigStilt`() {
            val bestemsak = bestemSakResponse()
            val allDocuemtActions = forenkletSED()

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                forsokFerdigStilt = false,
                land = "SWE"
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(UFORETRYGD, journalpostRequest.tema)
                assertEquals(UFORE_UTLAND, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(UFORE_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals("0123456789000", it.oppgaveMelding?.aktoerId)
                assertEquals(OppgaveType.JOURNALFORING, it.oppgaveMelding?.oppgaveType)

            }
        }

        @Test
        fun `Krav om uføre for inngående P2200 feiler med bestemSak`() {
            val bestemsak = BestemSakResponse(null, emptyList())
            val allDocuemtActions = forenkletSED()

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                land = "SWE"
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(UFORETRYGD, journalpostRequest.tema)
                assertEquals(UFORE_UTLAND, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(UFORE_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals(OppgaveType.JOURNALFORING, it.oppgaveMelding?.oppgaveType)
            }
        }

        @Test
        fun `Krav om uføre for inngående P2200 uten gyldig fnr sendes til ID og Fordeling`() {
            val bestemsak = BestemSakResponse(null, emptyList())
            val allDocuemtActions = forenkletSED()

            testRunnerVoksen(
                null,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                land = "SWE"
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(UFORETRYGD, journalpostRequest.tema)
                assertEquals(ID_OG_FORDELING, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(ID_OG_FORDELING, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals(null, it.oppgaveMelding?.aktoerId)
                assertEquals(OppgaveType.JOURNALFORING, it.oppgaveMelding?.oppgaveType)

            }
        }

        @Test
        fun `Krav om uføre for inngående P2200 uten gyldig fnr med sokPerson sendes til UFORE_UTLAND`() {
            val bestemsak = BestemSakResponse(null, emptyList())
            val allDocuemtActions = forenkletSED("b12e06dda2c7474b9998c7139c841646")

            testRunnerVoksenMedSokPerson(
                FNR_VOKSEN_UNDER_62,
                true,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                land = "SWE"
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(UFORETRYGD, journalpostRequest.tema)
                assertEquals(UFORE_UTLAND, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(UFORE_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals("0123456789000", it.oppgaveMelding?.aktoerId)
                assertEquals(OppgaveType.JOURNALFORING, it.oppgaveMelding?.oppgaveType)
            }
        }

    }

    @Nested
    @DisplayName("Utgående P Buc 03")
    inner class UtgaaendePBuc03 {

        @Test
        fun `Krav om uføre for Utgående P2200 journalføres automatisk med bruk av bestemsak uten forsokFerdigStilt oppretter en oppgave type JOURNALFORING`() {
            val bestemsak = bestemSakResponse(sakStatus = LOPENDE)
            val allDocuemtActions = forenkletSED(sedStatus = SENT)

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = SENDT
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(UFORETRYGD, journalpostRequest.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(UFORE_UTLANDSTILSNITT, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals("0123456789000", it.oppgaveMelding?.aktoerId)
                assertEquals(SENDT, it.oppgaveMelding?.hendelseType)
                assertEquals(JOURNALFORING_UT, it.oppgaveMelding?.oppgaveType)
            }
        }

        @Test
        fun `Krav om uføre for Utgående P2200 journalføres automatisk med bruk av bestemsak`() {
            val bestemsak = bestemSakResponse()
            val allDocuemtActions = forenkletSED(sedStatus = SENT)

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                bestemsak,
                alleDocs = allDocuemtActions,
                forsokFerdigStilt = true,
                hendelseType = SENDT
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest

                assertEquals(UFORETRYGD, journalpostRequest.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, journalpostRequest.journalfoerendeEnhet)

                assertEquals(0, oppgaveMeldingList.size)
            }
        }

        @Test
        fun `Krav om uføre for Utgående P2200 journalføres manualt når bestemsak feiler`() {
            val bestemsak = BestemSakResponse(null, emptyList())
            val allDocuemtActions = forenkletSED(sedStatus = SENT)

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = SENDT
            ) {
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(UFORETRYGD, journalpostRequest.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, journalpostRequest.journalfoerendeEnhet)
            }

        }

    }

    private fun testRunnerVoksen(
        fnrVoksen: String?,
        bestemSak: BestemSakResponse? = null,
        sakId: String? = SAK_ID,
        land: String = "NOR",
        krav: KravType = KravType.UFOREP,
        alleDocs: List<ForenkletSED>,
        forsokFerdigStilt: Boolean = false,
        documentFiler: SedDokumentfiler = getDokumentfilerUtenVedlegg(),
        hendelseType: HendelseType,
        block: (TestResult) -> Unit
    ) {
        val sed = SED.generateSedToClass<P2200>(createSedPensjon(P2200, fnrVoksen, eessiSaknr = sakId, krav = krav))

        initCommonMocks(sed, alleDocs, documentFiler)

        if (fnrVoksen != null) {
        every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns createBrukerWith(fnrVoksen, "Voksen ", "Forsikret", land, aktorId = AKTOER_ID) }
        sakId?.let {
            every { etterlatteService.hentGjennySak(any()) } returns mockHentGjennySak(it)
        } ?: run {
            every { etterlatteService.hentGjennySak(any()) } returns mockHentGjennySakMedError()
        }
        if (bestemSak != null) {
            every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak
        }else{
            //every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID) } returns bestemSak?.sakInformasjonListe!!
        }

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot(forsokFerdigStilt)

        val hendelse = createHendelseJson(P2200, P_BUC_03)

        val meldingSlot = mutableListOf<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val kravmeldingSlot = mutableListOf<String>()
        every { kravInitHandlerKafka.sendDefault(any(), capture(kravmeldingSlot)).get() } returns mockk()

        val journalpostRequest = slot<OpprettJournalpostRequest>()

        when (hendelseType) {
            SENDT -> sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
        }
        var oppgaveMeldingList: List<OppgaveMelding> = meldingSlot.map {
            mapJsonToAny(it)
        }

        if (!journalpostResponse.journalpostferdigstilt && oppgaveMeldingList.isEmpty() && journalpostRequest.captured.bruker == null) {
            createMockedJournalPostWithOppgave(journalpostRequest, hendelse, hendelseType)
        }
        val kravMeldingList: List<BehandleHendelseModel> = kravmeldingSlot.map {
            mapJsonToAny(it)
        }
        oppgaveMeldingList = meldingSlot.map {
            mapJsonToAny(it)
        }
        block(TestResult(journalpost.captured, oppgaveMeldingList, kravMeldingList))

        if (fnrVoksen != null) verify { personService.hentPerson(any()) }

        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        clearAllMocks()
    }

    private fun getDokumentfilerUtenGyldigVedlegg(): SedDokumentfiler {
        val dokumentfilerJson = javaClass.getResource("/pdf/pdfResponseMedUgyldigVedlegg.json")!!.readText()
        return mapJsonToAny(dokumentfilerJson)
    }

    private fun forenkletSED(id: String? = "10001212", sedStatus: SedStatus? = RECEIVED)
        = listOf(ForenkletSED(id!!, P2200, sedStatus))
    private fun bestemSakResponse(sakStatus: SakStatus? = LOPENDE) = BestemSakResponse(null, listOf(sakInformasjon(sakStatus)))

    private fun sakInformasjon(sakStatus: SakStatus? = LOPENDE) = SakInformasjon(sakId = SAK_ID, sakType = UFOREP, sakStatus = sakStatus!!)

    data class TestResult(
        val opprettJournalpostRequest: OpprettJournalpostRequest,
        val oppgaveMeldingList: List<OppgaveMelding>,
        val kravMeldingList: List<BehandleHendelseModel>? = null
    ) {
        val oppgaveMeldingUgyldig = if (oppgaveMeldingList.size == 2) oppgaveMeldingList.first() else null
        val oppgaveMelding = if (oppgaveMeldingList.size == 2) oppgaveMeldingList.last() else if (oppgaveMeldingList.isNotEmpty()) oppgaveMeldingList.first() else null
    }
}
