package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.eux.model.sed.P2200
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.handler.BehandleHendelseModel
import no.nav.eessi.pensjon.handler.HendelseKode
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.handler.OppgaveType
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.klienter.pesys.BestemSakResponse
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet.AUTOMATISK_JOURNALFORING
import no.nav.eessi.pensjon.models.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.models.Enhet.UFORE_UTLAND
import no.nav.eessi.pensjon.models.Enhet.UFORE_UTLANDSTILSNITT
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.HendelseType.MOTTATT
import no.nav.eessi.pensjon.models.HendelseType.SENDT
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("P_BUC_03 – IntegrationTest")
internal class PBuc03IntegrationTest : JournalforingTestBase() {

    @Nested
    @DisplayName("Inngående")
    inner class InngaaendeP_BUC_03 {

        @Test
        fun `Krav om uføre for inngående P2200 journalføres automatisk med bruk av bestemsak og det opprettes en oppgave type BEHANDLE_SED`() {
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(
                        sakId = SAK_ID,
                        sakType = Saktype.UFOREP,
                        sakStatus = SakStatus.OPPRETTET
                    )
                )
            )
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P2200, SedStatus.RECEIVED))

            testRunnerVoksen(
                FNR_VOKSEN,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                forsokFerdigStilt = true,
                land = "SWE"
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(UFORETRYGD, journalpostRequest.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, journalpostRequest.journalfoerendeEnhet)

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
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(
                        sakId = SAK_ID,
                        sakType = Saktype.UFOREP,
                        sakStatus = SakStatus.OPPRETTET
                    )
                )
            )
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P2200, SedStatus.RECEIVED))

            testRunnerVoksen(
                FNR_VOKSEN,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                forsokFerdigStilt = true,
                documentFiler = getDokumentfilerUtenGyldigVedlegg(),
                land = "SWE"
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(UFORETRYGD, journalpostRequest.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, journalpostRequest.journalfoerendeEnhet)

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
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(
                        sakId = SAK_ID,
                        sakType = Saktype.UFOREP,
                        sakStatus = SakStatus.OPPRETTET
                    )
                )
            )
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P2200, SedStatus.RECEIVED))

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
                assertEquals(UFORETRYGD, journalpostRequest.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(AUTOMATISK_JOURNALFORING, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals("0123456789000", it.oppgaveMelding?.aktoerId)
                assertEquals(OppgaveType.JOURNALFORING, it.oppgaveMelding?.oppgaveType)

            }
        }



        @Test
        fun `Krav om uføre for inngående P2200 feiler med bestemSak`() {
            val bestemsak = BestemSakResponse(null, emptyList())
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P2200, SedStatus.RECEIVED))

            testRunnerVoksen(
                FNR_VOKSEN,
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
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P2200, SedStatus.RECEIVED))

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
            val allDocuemtActions = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P2200, SedStatus.RECEIVED))

            testRunnerVoksenMedSokPerson(
                FNR_VOKSEN,
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
    @DisplayName("Utgående")
    inner class UtgaaendeP_BUC_03 {

        @Test
        fun `Krav om uføre for Utgående P2200 journalføres automatisk med bruk av bestemsak uten forsokFerdigStilt oppretter en oppgave type JOURNALFORING`() {
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
                assertEquals(SENDT, it.oppgaveMelding?.hendelseType)
                assertEquals(OppgaveType.JOURNALFORING, it.oppgaveMelding?.oppgaveType)

            }

        }

        @Test
        fun `Krav om uføre for Utgående P2200 journalføres automatisk med bruk av bestemsak`() {
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
                forsokFerdigStilt = true,
                hendelseType = SENDT
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest

                assertEquals(UFORETRYGD, journalpostRequest.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, journalpostRequest.journalfoerendeEnhet)

                assertEquals(0, oppgaveMeldingList.size)

            }

        }

        @Test
        fun `Krav om uføre for Utgående P2200 journalføres manualt når bestemsak feiler`() {
            val bestemsak = BestemSakResponse(
                null, emptyList()
            )
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P2200, SedStatus.SENT))

            testRunnerVoksen(
                FNR_VOKSEN,
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
        krav: KravType = KravType.UFORE,
        alleDocs: List<ForenkletSED>,
        forsokFerdigStilt: Boolean = false,
        documentFiler: SedDokumentfiler = getDokumentfilerUtenVedlegg(),
        hendelseType: HendelseType,
        block: (TestResult) -> Unit
    ) {
        val sed = SED.generateSedToClass<P2200>(createSedPensjon(SedType.P2200, fnrVoksen, eessiSaknr = sakId, krav = krav))

        initCommonMocks(sed, alleDocs, documentFiler)

        if (fnrVoksen != null) {
        every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns createBrukerWith(fnrVoksen, "Voksen ", "Forsikret", land, aktorId = AKTOER_ID) }

        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

        if (bestemSak != null) {
            every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID) } returns bestemSak.sakInformasjonListe
        }

        val (journalpost, _) = initJournalPostRequestSlot(forsokFerdigStilt)

        val hendelse = createHendelseJson(SedType.P2200, BucType.P_BUC_03)

        val meldingSlot = mutableListOf<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val kravmeldingSlot = mutableListOf<String>()
        every { kravInitHandlerKafka.sendDefault(any(), capture(kravmeldingSlot)).get() } returns mockk()


        when (hendelseType) {
            SENDT -> sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            else -> fail()
        }

        val kravMeldingList: List<BehandleHendelseModel> = kravmeldingSlot.map {
            mapJsonToAny(it)
        }
        val oppgaveMeldingList: List<OppgaveMelding> = meldingSlot.map {
            mapJsonToAny(it)
        }
        block(TestResult(journalpost.captured, oppgaveMeldingList, kravMeldingList))

        if (fnrVoksen != null) verify { personService.hentPerson(any<Ident<*>>()) }

        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        clearAllMocks()
    }

    private fun getResource(resourcePath: String): String = javaClass.getResource(resourcePath).readText()

    private fun getDokumentfilerUtenGyldigVedlegg(): SedDokumentfiler {
        val dokumentfilerJson = getResource("/pdf/pdfResponseMedUgyldigVedlegg.json")
        return mapJsonToAny(dokumentfilerJson)
    }

    data class TestResult(
        val opprettJournalpostRequest: OpprettJournalpostRequest,
        val oppgaveMeldingList: List<OppgaveMelding>,
        val kravMeldingList: List<BehandleHendelseModel>? = null
    ) {
        val oppgaveMeldingUgyldig = if (oppgaveMeldingList.size == 2) oppgaveMeldingList.first() else null
        val oppgaveMelding = if (oppgaveMeldingList.size == 2) oppgaveMeldingList.last() else if (oppgaveMeldingList.isNotEmpty()) oppgaveMeldingList.first() else null
    }
}
