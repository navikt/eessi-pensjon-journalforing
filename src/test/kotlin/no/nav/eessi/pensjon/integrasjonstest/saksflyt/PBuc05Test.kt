package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet.AUTOMATISK_JOURNALFORING
import no.nav.eessi.pensjon.models.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.models.sed.DocStatus
import no.nav.eessi.pensjon.models.sed.Document
import no.nav.eessi.pensjon.models.sed.Rolle
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PBuc05Test : JournalforingTestBase() {

    companion object {
        private const val FNR_OVER_60 = "09035225916"   // SLAPP SKILPADDE
        private const val FNR_VOKSEN = "11067122781"    // KRAFTIG VEGGPRYD
        private const val FNR_VOKSEN_2 = "22117320034"  // LEALAUS KAKE
        private const val FNR_BARN = "12011577847"      // STERK BUSK

    }

    /**
     * P_BUC_05 DEL 1
     */

    @Test
    fun `2 personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 01, bosatt Norge del 4`() {
        val sed = createSed(SedType.P8000, FNR_OVER_60, createAnnenPerson(fnr = FNR_BARN, rolle = Rolle.ETTERLATTE), null)
        every { euxKlient.hentSed(any(), any()) } returns sed

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns getMockDocuments()
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("/pdf/pdfResponseUtenVedlegg.json")

        val voksen = createBrukerWith(FNR_OVER_60, "Voksen", "Vanlig", "NOR", "1213", null)
        every { personV3Service.hentPerson(FNR_OVER_60) } returns voksen
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(AKTOER_ID)
        every { personV3Service.hentPerson(JournalforingTestBase.FNR_BARN) } returns null

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val (journalpost, _) = initJournalPostRequestSlot()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())

        val request = journalpost.captured
        assertEquals(PENSJON, request.tema)
        assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)
        assertNull(request.bruker)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Hente opp korrekt fnr fra P8000 som er sendt fra oss med flere P8000 i BUC`() {
        val fnr = "28127822044"
        val afnr = "05127921999"
        val aktoera = "${fnr}1111"
        val aktoerf = "${fnr}0000"
        val saknr = "1223123123"

        val sedP8000_2 = createSed(SedType.P8000, fnr, createAnnenPerson(fnr = afnr, rolle = Rolle.ETTERLATTE), saknr)
        val sedP8000sendt = createSed(SedType.P8000, fnr, createAnnenPerson(fnr = afnr, rolle = Rolle.ETTERLATTE), saknr)
        val sedP8000recevied = createSed(SedType.P8000, null, createAnnenPerson(fnr = null, rolle = Rolle.ETTERLATTE), null)

        val dokumenter = mapJsonToAny(getResource("/fagmodul/alldocumentsids_P_BUC_05_multiP8000.json"), typeRefs<List<Document>>())
        every { fagmodulKlient.hentAlleDokumenter(any()) } returns dokumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP8000_2 andThen sedP8000recevied andThen sedP8000sendt
        every { diskresjonService.hentDiskresjonskode(any()) } returns null
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("/pdf/pdfResponseUtenVedlegg.json")
        every { personV3Service.hentPerson(afnr) } returns createBrukerWith(afnr, "Lever", "Helt i live", "NOR")
        every { personV3Service.hentPerson(fnr) } returns createBrukerWith(fnr, "Død", "Helt Død", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(afnr)) } returns AktoerId(aktoera)
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoerf)

        val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = saknr, sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "34234123", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET)
        )
        every { fagmodulKlient.hentPensjonSaklist(aktoera) } returns saker
        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

        val (journalpost, _) = initJournalPostRequestSlot(true)
        val hendelse = createHendelseJson(SedType.P6000, BucType.P_BUC_05)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured

        // forvent tema == PEN og enhet 9999
        assertEquals(UFORETRYGD, request.tema)
        assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)
        assertEquals(afnr, request.bruker?.id)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 3) { euxKlient.hentSed(any(), any()) }

    }

    @Test
    fun `Scenario 13 - 0 Sed sendes som svar med flere personer pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema PEN og enhet ID OG FORDELING `() {
        val sedP8000recevied = createSed(SedType.P8000, null, fdato = "1955-07-11")
        val sedP5000sent = createSedPensjon(SedType.P5000, FNR_OVER_60, gjenlevendeFnr = FNR_BARN)

        val alleDocumenter = listOf(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P5000, DocStatus.SENT)
        )

        every { personV3Service.hentPerson(FNR_BARN) } returns createBrukerWith(FNR_BARN, "Lever", "Helt i live", "NOR")
        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_OVER_60, "Død", "Helt Død", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_BARN)) } returns AktoerId(FNR_BARN + "00000")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(FNR_OVER_60 + "11111")

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP8000recevied andThen sedP5000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("/pdf/pdfResponseUtenVedlegg.json")

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P5000, BucType.P_BUC_05)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
        assertEquals("P5000", oppgaveMelding.sedType?.name)

        assertEquals("UTGAAENDE", request.journalpostType.name)
        assertEquals(PENSJON, request.tema)
        assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 13 - 1 Sed sendes som svar med flere personer pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema PEN og enhet ID OG FORDELING `() {
        val sedP8000recevied = createSed(SedType.P8000, null, fdato = "1955-07-11")
        val sedP5000sent = createSed(SedType.P5000, null, fdato = "1955-07-11")

        val alleDocumenter = listOf(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P5000, DocStatus.SENT)
        )

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP8000recevied andThen sedP5000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("/pdf/pdfResponseUtenVedlegg.json")

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P5000, BucType.P_BUC_05)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
        assertEquals("P5000", oppgaveMelding.sedType?.name)

        assertEquals("UTGAAENDE", request.journalpostType.name)
        assertEquals(PENSJON, request.tema)
        assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 13 - 2 Sed sendes som svar med fnr pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema PEN og enhet ID OG FORDELING `() {
        val fnr = "07115521999"
        val aktoer = "${fnr}111"
        val sedP8000recevied = createSed(SedType.P8000, null, fdato = "1955-07-11")
        val sedP5000sent = createSed(SedType.P5000, fnr)

        val alleDocumenter = listOf(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P5000, DocStatus.SENT)
        )

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP8000recevied andThen sedP5000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("/pdf/pdfResponseUtenVedlegg.json")

        every { personV3Service.hentPerson(fnr) } returns createBrukerWith(fnr, "Lever", "Helt i live", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoer)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P5000, BucType.P_BUC_05)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
        assertEquals("P5000", oppgaveMelding.sedType?.name)

        assertEquals("UTGAAENDE", request.journalpostType.name)
        assertEquals(PENSJON, request.tema)
        assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }

    }

    @Test
    fun `Scenario 13 - 2 Sed sendes som svar med fnr pa tidligere mottatt P8000 ingen ident, svar sed med fnr og sakid i sed journalføres automatisk `() {
        val fnr = FNR_VOKSEN
        val aktoer = "${fnr}111"
        val sakid = SAK_ID
        val sedP8000recevied = createSed(SedType.P8000, null, fdato = "1955-07-11")
        val sedP9000sent = createSed(SedType.P9000, fnr, eessiSaknr = sakid)

        val alleDocumenter = listOf(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P9000, DocStatus.SENT)
        )

        val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = YtelseType.OMSORG, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = sakid, sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE)
        )

        every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker
        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP8000recevied andThen sedP9000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("/pdf/pdfResponseUtenVedlegg.json")

        every { personV3Service.hentPerson(fnr) } returns createBrukerWith(fnr, "KRAFTIG ", "VEGGPRYD", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoer)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P9000, BucType.P_BUC_05)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(AUTOMATISK_JOURNALFORING, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
        assertEquals("P9000", oppgaveMelding.sedType?.name)

        assertEquals("UTGAAENDE", request.journalpostType.name)
        assertEquals(UFORETRYGD, request.tema)
        assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)
        assertEquals(fnr, request.bruker?.id!!)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 13 - 2 Sed sendes som svar med fnr pa tidligere mottatt P8000 ingen ident, svar sed med fnr og ingen sakid i sed journalføres UFO `() {
        val fnr = FNR_VOKSEN
        val aktoer = "${fnr}111"
        val sakid = SAK_ID
        val sedP8000recevied = createSed(SedType.P8000, null, fdato = "1955-07-11")
        val sedP9000sent = createSed(SedType.P9000, fnr, eessiSaknr = sakid)

        val alleDocumenter = listOf(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P9000, DocStatus.SENT)
        )

        val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = YtelseType.OMSORG, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "123123123123123", sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE)
        )

        every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker
        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP8000recevied andThen sedP9000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("/pdf/pdfResponseUtenVedlegg.json")

        every { personV3Service.hentPerson(fnr) } returns createBrukerWith(fnr, "KRAFTIG ", "VEGGPRYD", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoer)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P9000, BucType.P_BUC_05)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
        assertEquals("P9000", oppgaveMelding.sedType?.name)

        assertEquals("UTGAAENDE", request.journalpostType.name)
        assertEquals(PENSJON, request.tema)
        assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)
        assertEquals(fnr, request.bruker?.id!!)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }

    }

    @Test
    fun `Scenario 13 - 3 Sed sendes som svar med fnr og sak finnes og er GENRL pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema NFP UTLAND AALESUND`() {
        val fnr = FNR_VOKSEN
        val sakid = "1231232323"
        val aktoer = "${fnr}111"
        val sedP8000recevied = createSed(SedType.P8000, null, fdato = "1955-07-11")
        val sedP5000sent = createSed(SedType.P5000, fnr, eessiSaknr = sakid)

        val alleDocumenter = listOf(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P5000, DocStatus.SENT)
        )

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP8000recevied andThen sedP5000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("/pdf/pdfResponseUtenVedlegg.json")
        every { personV3Service.hentPerson(fnr) } returns createBrukerWith(fnr, "Lever", "Helt i live", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoer)

        val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "34234234234234", sakType = YtelseType.GENRL, sakStatus = SakStatus.LOPENDE)
        )
        every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P6000, BucType.P_BUC_05)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)

        assertEquals("UTGAAENDE", request.journalpostType.name)
        assertEquals(PENSJON, request.tema)
        assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }

    }

    @Test
    fun `Scenario 13 - 4 Sed sendes som svar med fnr utland og sak finnes og er GENRL pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema PENSJON UTLAND`() {
        val fnr = FNR_VOKSEN
        val sakid = "1231232323"
        val aktoer = "${fnr}111"
        val sedP8000recevied = createSed(SedType.P8000, null, fdato = "1955-07-11")
        val sedP5000sent = createSed(SedType.P5000, fnr, eessiSaknr = sakid)

        val alleDocumenter = listOf(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P5000, DocStatus.SENT)
        )

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP8000recevied andThen sedP5000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("/pdf/pdfResponseUtenVedlegg.json")
        every { personV3Service.hentPerson(fnr) } returns createBrukerWith(fnr, "Lever", "Helt i live", "SWE")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoer)

        val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "123123123123123123", sakType = YtelseType.GENRL, sakStatus = SakStatus.LOPENDE)
        )
        every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P6000, BucType.P_BUC_05)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)

        assertEquals("UTGAAENDE", request.journalpostType.name)
        assertEquals(PENSJON, request.tema)
        assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }

    }

    @Test
    fun `Scenario 13 - 5 Sed sendes som svar med fnr og sak finnes og er UFOREP pa tidligere mottatt P8000, journalføres automatisk`() {
        val fnr = FNR_VOKSEN
        val sakid = "1231232323"
        val aktoer = "${fnr}111"
        val sedP8000recevied = createSed(SedType.P8000, null, fdato = "1955-07-11")
        val sedP5000sent = createSed(SedType.P5000, fnr, eessiSaknr = sakid)

        val alleDocumenter = listOf(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P5000, DocStatus.SENT)
        )

        every { fagmodulKlient.hentAlleDokumenter(any()) } returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP8000recevied andThen sedP5000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("/pdf/pdfResponseUtenVedlegg.json")
        every { personV3Service.hentPerson(fnr) } returns createBrukerWith(fnr, "Lever", "Helt i live", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoer)

        val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = YtelseType.OMSORG, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = sakid, sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE)
        )
        every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker
        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

        val (journalpost, _) = initJournalPostRequestSlot(true)
        val hendelse = createHendelseJson(SedType.P6000, BucType.P_BUC_05)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured

        assertEquals("UTGAAENDE", request.journalpostType.name)
        assertEquals(UFORETRYGD, request.tema)
        assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }

    }

    private fun getResource(resourcePath: String): String =
            javaClass.getResource(resourcePath).readText()
}
