package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import com.fasterxml.jackson.core.type.TypeReference
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
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
import no.nav.eessi.pensjon.models.sed.KravType
import no.nav.eessi.pensjon.models.sed.KravType.UFORE
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
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
        fun `Krav om uføre for inngående P2200 journalføres automatisk med bruk av bestemsak`() {
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
                land = "SWE"
            ) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
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
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
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
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }

    @Nested
    @DisplayName("Utgående")
    inner class UtgaaendeP_BUC_03 {

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
                hendelseType = SENDT
            ) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
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
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }

        }

    }

    private fun testRunnerVoksen(
        fnrVoksen: String?,
        bestemSak: BestemSakResponse? = null,
        sakId: String? = SAK_ID,
        land: String = "NOR",
        krav: KravType = UFORE,
        alleDocs: List<ForenkletSED>,
        hendelseType: HendelseType,
        block: (OpprettJournalpostRequest) -> Unit
    ) {
        val sed = createSedPensjon(SedType.P2200, fnrVoksen, eessiSaknr = sakId, krav = krav)
        initCommonMocks(sed, alleDocs)

        if (fnrVoksen != null) {
        every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns createBrukerWith(fnrVoksen, "Voksen ", "Forsikret", land, aktorId = AKTOER_ID)
        }

        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

        val (journalpost, _) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P2200, BucType.P_BUC_03)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        when (hendelseType) {
            SENDT -> listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            else -> fail()
        }

        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())
        assertEquals(hendelseType, oppgaveMelding.hendelseType)

        block(journalpost.captured)

        verify(exactly = 1) { euxService.hentBucDokumenter(any()) }
        if (fnrVoksen != null) verify { personService.hentPerson(any<Ident<*>>()) }
        verify(exactly = 1) { euxService.hentSed(any(), any(), any<TypeReference<SED>>()) }

        clearAllMocks()
    }


    private fun initCommonMocks(sed: SED, alleDocs: List<ForenkletSED>) {
        every { euxService.hentBucDokumenter(any()) } returns alleDocs
        every { euxService.hentSed(any(), any(), any<TypeReference<SED>>()) } returns sed
        every { euxService.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
    }

    private fun getResource(resourcePath: String): String =
        javaClass.getResource(resourcePath).readText()

    private fun getDokumentfilerUtenVedlegg(): SedDokumentfiler {
        val dokumentfilerJson = getResource("/pdf/pdfResponseUtenVedlegg.json")
        return mapJsonToAny(dokumentfilerJson, typeRefs())
    }
}
