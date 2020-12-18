package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
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
import no.nav.eessi.pensjon.models.sed.DocStatus
import no.nav.eessi.pensjon.models.sed.Document
import no.nav.eessi.pensjon.models.sed.KravType
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.personidentifisering.helpers.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("P_BUC_10 - Utgående journalføring - IntegrationTest")
internal class PBuc10IntegrationTest : JournalforingTestBase() {

    @Nested
    inner class Scenario1 {

        @Test
        fun `Krav om alderspensjon, person under 60`() {
            initSed(createSedPensjon(SedType.P15000, FNR_VOKSEN_2, eessiSaknr = SAK_ID, krav = KravType.ALDER))

            initDokumenter(Document("10001212", SedType.P15000, DocStatus.SENT))

            initBestemSak(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING))

            initMockPerson(FNR_VOKSEN_2, AKTOER_ID)

            consumeAndAssert(HendelseType.SENDT, SedType.P15000, BucType.P_BUC_10, ferdigstilt = true) {
                assertNull(it.melding)
                assertEquals(PENSJON, it.request.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.request.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Krav om alderspensjon, person over 60`() {
            initSed(createSedPensjon(SedType.P15000, FNR_OVER_60, eessiSaknr = SAK_ID, krav = KravType.ALDER))

            initDokumenter(Document("10001212", SedType.P15000, DocStatus.SENT))

            initBestemSak(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING))

            initMockPerson(FNR_OVER_60, aktoerId = AKTOER_ID)

            consumeAndAssert(HendelseType.SENDT, SedType.P15000, BucType.P_BUC_10, ferdigstilt = true) {
                assertNull(it.melding)
                assertEquals(PENSJON, it.request.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.request.journalfoerendeEnhet)
            }
        }
    }

    @Nested
    inner class Scenario2 {

        @Test
        fun `Krav om uføretrygd`() {
            initSed(createSedPensjon(SedType.P15000, FNR_VOKSEN_2, eessiSaknr = SAK_ID, krav = KravType.UFORE))
            initDokumenter(Document("10001212", SedType.P15000, DocStatus.SENT))
            initBestemSak(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING))
            initMockPerson(FNR_VOKSEN_2, aktoerId = AKTOER_ID)

            consumeAndAssert(HendelseType.SENDT, SedType.P15000, BucType.P_BUC_10, ferdigstilt = true) {
                assertEquals(UFORETRYGD, it.request.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.request.journalfoerendeEnhet)
                assertEquals(FNR_VOKSEN_2, it.request.bruker?.id)
            }
        }

        @Test
        fun `Krav om uføretrygd - sakstatus AVSLUTTET - AUTOMATISK_JOURNALFORING`() {
            initSed(createSedPensjon(SedType.P15000, FNR_VOKSEN_2, eessiSaknr = SAK_ID, krav = KravType.UFORE))
            initBestemSak(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET))
            initDokumenter(Document("10001212", SedType.P15000, DocStatus.SENT))
            initMockPerson(FNR_VOKSEN_2, aktoerId = AKTOER_ID)

            consumeAndAssert(HendelseType.SENDT, SedType.P15000, BucType.P_BUC_10, ferdigstilt = true) {
                assertEquals(UFORETRYGD, it.request.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.request.journalfoerendeEnhet)
                assertEquals(FNR_VOKSEN_2, it.request.bruker?.id)
            }
        }
    }


    @Nested
    inner class Scenario3 {

        @Test
        fun `Krav om barnepensjon - automatisk`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.BARNEP, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = listOf(Document("10001212", SedType.P15000, DocStatus.SENT))

            testRunner(FNR_VOKSEN, FNR_BARN, bestemsak, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions, sedOverride = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
                assertEquals(FNR_BARN, it.bruker?.id!!)
            }
        }

        @Test
        fun `Krav om barnepensjon ingen sak - id og fordeling`() {
            val allDocuemtActions = listOf(Document("10001212", SedType.P15000, DocStatus.SENT))

            testRunner(FNR_VOKSEN, FNR_BARN, null, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions, sedOverride = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                assertEquals(FNR_BARN, it.bruker?.id!!)
            }
        }

        @Test
        fun `Krav om barnepensjon - barn ukjent ident - id og fordeling`() {
            val allDocuemtActions = listOf(Document("10001212", SedType.P15000, DocStatus.SENT))

            testRunner(FNR_VOKSEN, null, null, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions, sedOverride = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                assertNull(it.bruker)
            }
        }

        @Test
        fun `Krav om barnepensjon - relasjon mangler - id og fordeling`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.BARNEP, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = listOf(Document("10001212", SedType.P15000, DocStatus.SENT))
            testRunner(FNR_VOKSEN, FNR_BARN, bestemsak, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions, relasjonAvod = null, sedOverride = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                assertEquals(FNR_BARN, it.bruker?.id)
            }
        }

        @Test
        fun `Test med Sed fra Rina BARNEP og bestemsak - automatisk`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = "22919587", sakType = YtelseType.BARNEP, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = listOf(Document("10001212", SedType.P15000, DocStatus.SENT))
            val fnr = Fodselsnummer.fra("05020876176")
            println("fnr: $fnr")

            val mockSED = mapJsonToAny(mockSED(), typeRefs<SED>())

            val valgtbarnfnr = "05020876176"
            testRunner("13017123321", "05020876176", bestemsak, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions, sedOverride = mockSED) {
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
            val allDocuemtActions = listOf(Document("10001212", SedType.P15000, DocStatus.SENT))

            testRunner(FNR_OVER_60, FNR_VOKSEN, bestemsak, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions, relasjonAvod = RelasjonTilAvdod.EKTEFELLE) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Krav om gjenlevendeytelse - GP eller AP - GJENLEV - automatisk`() {
            val bestemsak2 = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.GJENLEV, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = listOf(Document("10001212", SedType.P15000, DocStatus.SENT))

            testRunner(FNR_OVER_60, FNR_VOKSEN, bestemsak2, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions, relasjonAvod = RelasjonTilAvdod.SAMBOER) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Krav om gjenlevendeytelse - GP eller AP - mangler relasjon - id og fordeling`() {
            val bestemsak2 = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = listOf(Document("10001212", SedType.P15000, DocStatus.SENT))

            testRunner(FNR_OVER_60, FNR_VOKSEN, bestemsak2, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions, relasjonAvod = null) {
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
            val allDocuemtActions = listOf(Document("10001212", SedType.P15000, DocStatus.SENT))

            testRunner(FNR_OVER_60, FNR_VOKSEN, bestemsak, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions, relasjonAvod = RelasjonTilAvdod.EKTEFELLE) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

        }

    }

    @Nested
    inner class SendtGjenlevendeKrav {

        @Test
        fun `Krav om gjenlevendeytelse - Uføretrygd manuelt - id og fordeling`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET)))
            val allDocuemtActions = listOf(Document("10001212", SedType.P15000, DocStatus.SENT))

            testRunner(FNR_OVER_60, FNR_VOKSEN, bestemsak, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions, relasjonAvod = RelasjonTilAvdod.EKTEFELLE) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_OVER_60, FNR_VOKSEN, null, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions, relasjonAvod = RelasjonTilAvdod.EKTEFELLE) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                assertEquals(FNR_VOKSEN, it.bruker?.id!!)
            }
        }

        @Test
        fun `Krav om gjenlevendeytelse - flere sakstyper i retur - id og fordeling`() {
            val bestemsak = BestemSakResponse(null, listOf(
                            SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                            SakInformasjon(sakId = "123456", sakType = YtelseType.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)))

            val allDocuemtActions = listOf(Document("10001212", SedType.P15000, DocStatus.SENT))

            testRunner(FNR_VOKSEN, FNR_BARN, bestemsak, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_OVER_60, FNR_VOKSEN, bestemsak, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }

    @Nested
    inner class Scenario8 {

        @Test
        fun `manuell oppgave det mangler er mangelfullt fnr dnr - kun en person - id og fordeling`() {
            initSed(createSedPensjon(SedType.P15000, fnr = null, eessiSaknr = SAK_ID, krav = KravType.ALDER))

            initDokumenter(Document("10001212", SedType.P15000, DocStatus.SENT))

            consumeAndAssert(HendelseType.SENDT, SedType.P15000, BucType.P_BUC_10) {
                assertEquals(PENSJON, it.request.tema)
                assertEquals(ID_OG_FORDELING, it.request.journalfoerendeEnhet)
            }
        }
    }

    @Nested
    inner class Scenario9 {

        @Test
        fun `mangler som fører til manuell oppgave - etterlatteytelser`() {
            val allDocuemtActions = listOf(
                    Document("10001212", SedType.P15000, DocStatus.SENT)
            )

            testRunner(FNR_VOKSEN, null, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_VOKSEN, FNR_BARN, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions, relasjonAvod = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_VOKSEN, FNR_VOKSEN_2, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions, relasjonAvod = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_VOKSEN, null, krav = KravType.ETTERLATTE, alleDocs = allDocuemtActions, relasjonAvod = RelasjonTilAvdod.EKTEFELLE) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }


    private fun mockSED(): String {
        return """
            {"pensjon":{"gjenlevende":{"person":{"pin":[{"identifikator":"05020876176","land":"NO"}],"foedselsdato":"2008-02-05","etternavn":"TRANFLASKE","fornavn":"TYKKMAGET","kjoenn":"M","relasjontilavdod":{"relasjon":"06"}}}},"sedGVer":"4","nav":{"bruker":{"adresse":{"land":"NO","gate":"BEISKKÁNGEAIDNU 7","postnummer":"8803","by":"SANDNESSJØEN"},"person":{"fornavn":"BLÅ","pin":[{"land":"NO","institusjonsid":"NO:NAVAT07","institusjonsnavn":"NAV ACCEPTANCE TEST 07","identifikator":"13017123321"}],"kjoenn":"M","etternavn":"SKILPADDE","foedselsdato":"1971-01-13","statsborgerskap":[{"land":"NO"}]}},"eessisak":[{"institusjonsnavn":"NAV ACCEPTANCE TEST 07","saksnummer":"22919587","institusjonsid":"NO:NAVAT07","land":"NO"}],"krav":{"dato":"2020-10-01","type":"02"}},"sedVer":"2","sed":"P15000"}            
        """.trimIndent()
    }

    private fun testRunner(forsikretFnr: String,
                           gjenlevFnr: String?,
                           bestemSak: BestemSakResponse? = null,
                           sakId: String? = SAK_ID,
                           land: String = "NOR",
                           krav: KravType = KravType.ALDER,
                           alleDocs: List<Document>,
                           relasjonAvod: RelasjonTilAvdod? = RelasjonTilAvdod.EGET_BARN,
                           sedOverride: SED? = null,
                           block: (OpprettJournalpostRequest) -> Unit
    ) {

        val sed = sedOverride
                ?: createSedPensjon(SedType.P15000, forsikretFnr, eessiSaknr = sakId, krav = krav, gjenlevendeFnr = gjenlevFnr, relasjon = relasjonAvod)
        initSed(sed)
        initDokumenter(alleDocs)

        // FORSIKRET PERSON
        initMockPerson(forsikretFnr, aktoerId = AKTOER_ID, land = land)

        // ANNEN/GJENLEVENDE PERSON
        gjenlevFnr?.run { initMockPerson(fnr = this, aktoerId = AKTOER_ID_2, land = land) }

        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

        val fnr = if (krav == KravType.ETTERLATTE) forsikretFnr else null
        consumeAndAssert(HendelseType.SENDT, SedType.P15000, BucType.P_BUC_10, hendelseFnr = fnr) {
            block(it.request)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        if (gjenlevFnr != null) {
            verify { personV3Service.hentPerson(any()) }
            verify { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, any<NorskIdent>()) }
        } else {
            verify { personV3Service.hentPerson(any()) }
            verify { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, any<NorskIdent>()) }
        }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }

        clearAllMocks()
    }

}
