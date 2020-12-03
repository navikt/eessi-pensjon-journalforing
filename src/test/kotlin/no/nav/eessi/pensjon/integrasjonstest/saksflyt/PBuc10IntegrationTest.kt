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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

@DisplayName("P_BUC_10 - Utgående journalføring - IntegrationTest")
internal class PBuc10IntegrationTest : JournalforingTestBase() {

    companion object {
        private const val KRAV_ALDER = "01"
        private const val KRAV_UFORE = "03"
        private const val KRAV_GJENLEV = "02"
    }

    @Nested
    inner class Scenario1 {

         @Test
        fun `Krav om alderspensjon`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = mockAllDocumentsBuc( listOf(
                    Triple("10001212", "P15000", "sent")
            ))

            testRunner(FNR_VOKSEN_2, bestemsak, alleDocs = allDocuemtActions) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_OVER_60, bestemsak, alleDocs = allDocuemtActions) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }
    }

    @Nested
    inner class Scenario2 {

        @Test
        fun `Krav om uføretrygd`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = mockAllDocumentsBuc( listOf(
                    Triple("10001212", "P15000", "sent")
            ))

            testRunner(FNR_VOKSEN, bestemsak, krav = KRAV_UFORE, alleDocs = allDocuemtActions) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_VOKSEN_2, bestemsak, krav = KRAV_UFORE, alleDocs = allDocuemtActions) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }
    }


    @Nested
    inner class Scenario3 {

        @Test
        fun `Krav om barnepensjon - automatisk`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.BARNEP, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = mockAllDocumentsBuc( listOf(Triple("10001212", "P15000", "sent")))

            testRunnerBarn(FNR_VOKSEN, FNR_BARN, bestemsak, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, sedJson = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
                assertEquals(FNR_BARN, it.bruker?.id!!)
            }
        }

        @Test
        fun `Krav om barnepensjon ingen sak - id og fordeling`() {
            val allDocuemtActions = mockAllDocumentsBuc( listOf(Triple("10001212", "P15000", "sent")))

            testRunnerBarn(FNR_VOKSEN, FNR_BARN, null, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, sedJson = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                assertEquals(FNR_BARN, it.bruker?.id!!)
            }
        }

        @Test
        fun `Krav om barnepensjon - barn ukjent ident - id og fordeling`() {
            val allDocuemtActions = mockAllDocumentsBuc( listOf(Triple("10001212", "P15000", "sent")))

            testRunnerBarn(FNR_VOKSEN, null, null, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, sedJson = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                assertNull(it.bruker)
            }
        }

        @Test
        fun `Krav om barnepensjon - relasjon mangler - id og fordeling`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.BARNEP, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = mockAllDocumentsBuc( listOf(Triple("10001212", "P15000", "sent")))
            testRunnerBarn(FNR_VOKSEN, FNR_BARN, bestemsak, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = null, sedJson = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                assertEquals(FNR_BARN, it.bruker?.id!!)
            }
        }

        @Test
        fun `Test med Sed fra Rina BARNEP og bestemsak - automatisk`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = "22919587", sakType = YtelseType.BARNEP, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = mockAllDocumentsBuc( listOf(Triple("10001212", "P15000", "sent")))

            val valgtbarnfnr = "05020876176"
            testRunnerBarn("13017123321", "05020876176", bestemsak, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, sedJson = mockSED()) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
                assertEquals(valgtbarnfnr, it.bruker?.id!!)
            }
        }

    }

    @Nested
    inner class Scenario4 {

        @Test
        fun `Krav om gjenlevendeytelse - GP eller AP - ALDER - automatisk`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = mockAllDocumentsBuc( listOf(Triple("10001212", "P15000", "sent")))

            testRunnerVoksen(FNR_OVER_60, FNR_VOKSEN, bestemsak, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = "01") {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Krav om gjenlevendeytelse - GP eller AP - GJENLEV - automatisk`() {
            val bestemsak2 = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.GJENLEV, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = mockAllDocumentsBuc( listOf(Triple("10001212", "P15000", "sent")))
            testRunnerVoksen(FNR_OVER_60, FNR_VOKSEN, bestemsak2, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = "03") {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Krav om gjenlevendeytelse - GP eller AP - mangler relasjon - id og fordeling`() {
            val bestemsak2 = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = mockAllDocumentsBuc( listOf(Triple("10001212", "P15000", "sent")))
            testRunnerVoksen(FNR_OVER_60, FNR_VOKSEN, bestemsak2, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }

    }

    @Nested
    inner class Scenario5 {

        @Test
        fun `Krav om gjenlevendeytelse - Uføretrygd automatisk`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = mockAllDocumentsBuc( listOf(Triple("10001212", "P15000", "sent")))

            testRunnerVoksen(FNR_OVER_60, FNR_VOKSEN, bestemsak, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = "01") {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

        }

    }

    @Nested
    inner class Scenario6 {

        @Test
        fun `Krav om gjenlevendeytelse - Uføretrygd manuelt - id og fordeling`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET)))
            val allDocuemtActions = mockAllDocumentsBuc( listOf(Triple("10001212", "P15000", "sent")))

            testRunnerVoksen(FNR_OVER_60, FNR_VOKSEN, bestemsak, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = "01") {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

        }
    }

    @Nested
    inner class Scenario7 {

        @Test
        fun `Krav om gjenlevendeytelse - flere sakstyper i retur - id og fordeling`() {
            val bestemsak = BestemSakResponse(null, listOf(
                            SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                            SakInformasjon(sakId = "123456", sakType = YtelseType.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)))

            val allDocuemtActions = mockAllDocumentsBuc( listOf(
                    Triple("10001212", "P15000", "sent")
            ))

            testRunnerBarn(FNR_VOKSEN, FNR_BARN, bestemsak, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, sedJson = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerVoksen(FNR_OVER_60, FNR_VOKSEN, bestemsak, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }

    @Nested
    inner class Scenario8 {

        @Test
        fun `manuell oppgave det mangler er mangelfullt fnr dnr - kun en person - id og fordeling`() {
            val allDocuemtActions = mockAllDocumentsBuc( listOf(
                    Triple("10001212", "P15000", "sent")
            ))
            testRunner(null, krav = KRAV_ALDER, alleDocs = allDocuemtActions) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }

    @Nested
    inner class Scenario9 {

        @Test
        fun `mangler som fører til manuell oppgave - etterlatteytelser`() {
            val allDocuemtActions = mockAllDocumentsBuc( listOf(
                    Triple("10001212", "P15000", "sent")
            ))

            testRunnerBarn(FNR_VOKSEN, null, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, sedJson = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerBarn(FNR_VOKSEN, FNR_BARN, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = null, sedJson = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerVoksen(FNR_VOKSEN, FNR_VOKSEN_2, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerVoksen(FNR_VOKSEN, null, krav = KRAV_GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = "01") {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }


    private fun mockSED() : String {
        return """
            {"pensjon":{"gjenlevende":{"person":{"pin":[{"identifikator":"05020876176","land":"NO"}],"foedselsdato":"2008-02-05","etternavn":"TRANFLASKE","fornavn":"TYKKMAGET","kjoenn":"M","relasjontilavdod":{"relasjon":"06"}}}},"sedGVer":"4","nav":{"bruker":{"adresse":{"land":"NO","gate":"BEISKKÁNGEAIDNU 7","postnummer":"8803","by":"SANDNESSJØEN"},"person":{"fornavn":"BLÅ","pin":[{"land":"NO","institusjonsid":"NO:NAVAT07","institusjonsnavn":"NAV ACCEPTANCE TEST 07","identifikator":"13017123321"}],"kjoenn":"M","etternavn":"SKILPADDE","foedselsdato":"1971-01-13","statsborgerskap":[{"land":"NO"}]}},"eessisak":[{"institusjonsnavn":"NAV ACCEPTANCE TEST 07","saksnummer":"22919587","institusjonsid":"NO:NAVAT07","land":"NO"}],"krav":{"dato":"2020-10-01","type":"02"}},"sedVer":"2","sed":"P15000"}            
        """.trimIndent()
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

    private fun testRunnerVoksen(fnrVoksen: String,
                               fnrVoksenSoker: String?,
                               bestemSak: BestemSakResponse? = null,
                               sakId: String? = SAK_ID,
                               diskresjonkode: Diskresjonskode? = null,
                               land: String = "NOR",
                               krav: String = KRAV_ALDER,
                               alleDocs: String,
                               relasjonAvod: String? = "06",
                               block: (OpprettJournalpostRequest) -> Unit
    ) {

        val sed = createP15000(fnrVoksen, eessiSaknr = sakId, krav = krav, gfn = fnrVoksenSoker, relasjon = relasjonAvod)
        initCommonMocks(sed, alleDocs)

        every { personV3Service.hentPerson(fnrVoksen) } returns createBrukerWith(fnrVoksen, "Voksen ", "Forsikret", land)
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnrVoksen)) } returns AktoerId(AKTOER_ID)

        if (fnrVoksenSoker != null) {
            every { personV3Service.hentPerson(fnrVoksenSoker) } returns createBrukerWith(fnrVoksenSoker, "Voksen", "Gjenlevende", land)
            every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnrVoksenSoker)) } returns AktoerId(AKTOER_ID_2)
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
        if (fnrVoksenSoker != null) {
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
