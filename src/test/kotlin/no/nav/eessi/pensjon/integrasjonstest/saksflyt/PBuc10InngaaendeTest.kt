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
import no.nav.eessi.pensjon.models.sed.DocStatus
import no.nav.eessi.pensjon.models.sed.Document
import no.nav.eessi.pensjon.models.sed.Pensjon
import no.nav.eessi.pensjon.models.sed.KravType
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.models.sed.SED
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
        private val KRAV_ALDER = KravType.ALDER
        private val KRAV_UFORE = KravType.UFORE
        private val KRAV_GJENLEV = KravType.ETTERLATTE
    }

    @Nested
    inner class Scenario1 {

        @Test
        fun `Krav om alderspensjon`() {

            val allDocuemtActions = listOf(
                    Document("10001212", SedType.P15000, DocStatus.RECEIVED)
            )

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

            val allDocuemtActions = listOf(
                    Document("10001212", SedType.P15000, DocStatus.RECEIVED)
            )

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

            val allDocuemtActions = listOf(
                    Document("10001212", SedType.P15000, DocStatus.RECEIVED)
            )

            testRunner(FNR_VOKSEN, null, alleDocs = allDocuemtActions, land = "SWE", krav = KRAV_UFORE) {
                Assertions.assertEquals(Tema.UFORETRYGD, it.tema)
                Assertions.assertEquals(Enhet.UFORE_UTLAND, it.journalfoerendeEnhet)
            }
        }


    }

    @Test
    fun `Innkommende P15000 gjenlevende mangler søker`() {
        val sedP5000tmp = createSedPensjon(SedType.P15000, "12321", gjenlevendeFnr = null, krav = KRAV_GJENLEV)
        val sedP15000mottatt = SED(
            type = sedP5000tmp.type,
            nav = sedP5000tmp.nav,
            pensjon = Pensjon()
        )

        val alleDocumenter = listOf(
            Document("30002", SedType.P5000, DocStatus.RECEIVED)
        )

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP15000mottatt
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P15000, BucType.P_BUC_10)

        listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        Assertions.assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        Assertions.assertEquals(Enhet.ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
        Assertions.assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
        Assertions.assertEquals("P15000", oppgaveMelding.sedType?.name)

        Assertions.assertEquals("INNGAAENDE", request.journalpostType.name)
        Assertions.assertEquals(Tema.PENSJON, request.tema)
        Assertions.assertEquals(Enhet.ID_OG_FORDELING, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 1  - Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav ALDER skal routes til NFP_UTLAND_AALESUND 4862`() {
        val sed15000sent = createSedPensjon(SedType.P15000, FNR_OVER_60, krav = KRAV_ALDER)
        val sedP5000mottatt = createSedPensjon(SedType.P5000, FNR_OVER_60)

        val alleDocumenter = listOf(
                Document("10001", SedType.P15000, DocStatus.SENT),
                Document("30002", SedType.P5000, DocStatus.RECEIVED)
        )

        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_OVER_60, "Fornavn", "Pensjonisten", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(FNR_OVER_60 + "11111")

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP5000mottatt andThen sed15000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P5000, BucType.P_BUC_10)

        listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        Assertions.assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        Assertions.assertEquals(Enhet.NFP_UTLAND_AALESUND, oppgaveMelding.tildeltEnhetsnr)
        Assertions.assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
        Assertions.assertEquals("P5000", oppgaveMelding.sedType?.name)

        Assertions.assertEquals("INNGAAENDE", request.journalpostType.name)
        Assertions.assertEquals(Tema.PENSJON, request.tema)
        Assertions.assertEquals(Enhet.NFP_UTLAND_AALESUND, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 1  - Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav ALDER bosatt utland skal routes til PENSJON_UTLAND 0001`() {
        val sed15000sent = createSedPensjon(SedType.P15000, FNR_OVER_60, krav = KRAV_ALDER)
        val sedP5000mottatt = createSedPensjon(SedType.P5000, FNR_OVER_60)

        val alleDocumenter = listOf(
                Document("10001", SedType.P15000, DocStatus.SENT),
                Document("30002", SedType.P5000, DocStatus.RECEIVED)
        )

        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_OVER_60, "Fornavn", "Pensjonisten", "SWE")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(FNR_OVER_60 + "11111")

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP5000mottatt andThen sed15000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P5000, BucType.P_BUC_10)

        listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        Assertions.assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        Assertions.assertEquals(Enhet.PENSJON_UTLAND, oppgaveMelding.tildeltEnhetsnr)
        Assertions.assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
        Assertions.assertEquals("P5000", oppgaveMelding.sedType?.name)

        Assertions.assertEquals("INNGAAENDE", request.journalpostType.name)
        Assertions.assertEquals(Tema.PENSJON, request.tema)
        Assertions.assertEquals(Enhet.PENSJON_UTLAND, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 1  - Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav UFOEREP skal routes til UFORE_UTLANDSTILSNITT 4476`() {
        val sed15000sent = createSedPensjon(SedType.P15000, FNR_VOKSEN, krav = KRAV_UFORE)
        val sedP5000mottatt = createSedPensjon(SedType.P5000, FNR_VOKSEN)

        val alleDocumenter = listOf(
                Document("10001", SedType.P15000, DocStatus.SENT),
                Document("30002", SedType.P5000, DocStatus.RECEIVED)
        )

        every { personV3Service.hentPerson(FNR_VOKSEN) } returns createBrukerWith(FNR_VOKSEN, "Fornavn", "Pensjonisten", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_VOKSEN)) } returns AktoerId(FNR_VOKSEN + "11111")

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP5000mottatt andThen sed15000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P5000, BucType.P_BUC_10)

        listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        Assertions.assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        Assertions.assertEquals(Enhet.UFORE_UTLANDSTILSNITT, oppgaveMelding.tildeltEnhetsnr)
        Assertions.assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
        Assertions.assertEquals("P5000", oppgaveMelding.sedType?.name)

        Assertions.assertEquals("INNGAAENDE", request.journalpostType.name)
        Assertions.assertEquals(Tema.UFORETRYGD, request.tema)
        Assertions.assertEquals(Enhet.UFORE_UTLANDSTILSNITT, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 1  - Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav UFOEREP bosatt utland skal routes til UFORE_UTLAND 4475`() {
        val sed15000sent = createSedPensjon(SedType.P15000, FNR_VOKSEN, krav = KRAV_UFORE)
        val sedP5000mottatt = createSedPensjon(SedType.P5000, FNR_VOKSEN)

        val alleDocumenter = listOf(
                Document("10001", SedType.P15000, DocStatus.SENT),
                Document("30002", SedType.P5000, DocStatus.RECEIVED)
        )

        every { personV3Service.hentPerson(FNR_VOKSEN) } returns createBrukerWith(FNR_VOKSEN, "Fornavn", "Pensjonisten", "SWE")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_VOKSEN)) } returns AktoerId(FNR_VOKSEN + "11111")

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP5000mottatt andThen sed15000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P5000, BucType.P_BUC_10)

        listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        Assertions.assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        Assertions.assertEquals(Enhet.UFORE_UTLAND, oppgaveMelding.tildeltEnhetsnr)
        Assertions.assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
        Assertions.assertEquals("P5000", oppgaveMelding.sedType?.name)

        Assertions.assertEquals("INNGAAENDE", request.journalpostType.name)
        Assertions.assertEquals(Tema.UFORETRYGD, request.tema)
        Assertions.assertEquals(Enhet.UFORE_UTLAND, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 4  - Flere sed i buc, mottar en P15000 med ukjent gjenlevende relasjon, krav GJENLEV sender en P5000 med korrekt gjenlevende denne skal journalføres automatisk`() {
        val sed15000sent = createSedPensjon(SedType.P15000, FNR_OVER_60, gjenlevendeFnr = "", krav = KRAV_GJENLEV, relasjon = RelasjonTilAvdod.EKTEFELLE)
        val sedP5000mottatt = createSedPensjon(SedType.P5000, FNR_OVER_60, gjenlevendeFnr = FNR_VOKSEN_2, eessiSaknr = SAK_ID)

        val saker = listOf(
                SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                SakInformasjon(sakId = "34234123", sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET)
        )

        val alleDocumenter = listOf(
                Document("10001", SedType.P15000, DocStatus.RECEIVED),
                Document("30002", SedType.P5000, DocStatus.SENT)
        )

        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_OVER_60, "Avdød", "død", "SWE")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(FNR_OVER_60 + "11111")

        every { personV3Service.hentPerson(FNR_VOKSEN_2) } returns createBrukerWith(FNR_VOKSEN_2, "Gjenlevende", "Lever", "SWE")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_VOKSEN_2)) } returns AktoerId(FNR_VOKSEN_2 + "11111")

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP5000mottatt andThen sed15000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")
        every { fagmodulKlient.hentPensjonSaklist(FNR_VOKSEN_2 + "11111") } returns saker

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P5000, BucType.P_BUC_10)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured


        Assertions.assertEquals("UTGAAENDE", request.journalpostType.name)
        Assertions.assertEquals(Tema.PENSJON, request.tema)
        Assertions.assertEquals(Enhet.AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    private fun testRunnerBarn(fnrVoksen: String,
                               fnrBarn: String?,
                               bestemSak: BestemSakResponse? = null,
                               sakId: String? = SAK_ID,
                               land: String = "NOR",
                               krav: KravType = KravType.ALDER,
                               alleDocs: List<Document>,
                               relasjonAvod: RelasjonTilAvdod? = RelasjonTilAvdod.EGET_BARN,
                               block: (OpprettJournalpostRequest) -> Unit
    ) {
        val sed = createSedPensjon(SedType.P15000, fnrVoksen, eessiSaknr = sakId, krav = krav, gjenlevendeFnr = fnrBarn, relasjon = relasjonAvod)
        initCommonMocks(sed, alleDocs)

        every { personV3Service.hentPerson(fnrVoksen) } returns createBrukerWith(fnrVoksen, "Mamma forsørger", "Etternavn", land)
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnrVoksen)) } returns AktoerId(AKTOER_ID)

        if (fnrBarn != null) {
            every { personV3Service.hentPerson(fnrBarn) } returns createBrukerWith(fnrBarn, "Barn", "Diskret", land)
            every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnrBarn)) } returns AktoerId(AKTOER_ID_2)
        }
        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

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
                           krav: KravType = KravType.ALDER,
                           alleDocs: List<Document>,
                           block: (OpprettJournalpostRequest) -> Unit
    ) {

        val sed = createSedPensjon(SedType.P15000, fnr1, eessiSaknr = sakId, krav = krav)
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

    private fun initCommonMocks(sed: SED, alleDocs: List<Document>) {
        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocs
        every { euxKlient.hentSed(any(), any()) } returns sed
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")
    }

    private fun getResource(resourcePath: String): String =
            javaClass.classLoader.getResource(resourcePath)!!.readText()
}
