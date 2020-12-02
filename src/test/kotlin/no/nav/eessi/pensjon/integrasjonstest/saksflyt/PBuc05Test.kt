package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.Enhet.AUTOMATISK_JOURNALFORING
import no.nav.eessi.pensjon.models.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.models.Enhet.NFP_UTLAND_AALESUND
import no.nav.eessi.pensjon.models.Enhet.PENSJON_UTLAND
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PBuc05Test : JournalforingTestBase() {

    companion object {
        private const val FNR_OVER_60 = "09035225916"   // SLAPP SKILPADDE
        private const val FNR_BARN = "12011577847"      // STERK BUSK
    }

    /**
     * P_BUC_05 DEL 1
     */

    @Test
    fun `Hente opp korrekt fnr fra P8000 som er sendt fra oss med flere P8000 i BUC`() {
        val fnr = "28127822044"
        val afnr = "05127921999"
        val aktoera = "${fnr}1111"
        val aktoerf = "${fnr}0000"
        val saknr = "1223123123"

        val sedP8000_2 = createSedJson(SedType.P8000, fnr,createAnnenPersonJson(fnr = afnr, rolle =  "01") , saknr)
        val sedP8000sendt = createSedJson(SedType.P8000, fnr,createAnnenPersonJson(fnr = afnr, rolle =  "01") , saknr)
        val sedP8000recevied = createSedJson(SedType.P8000, null, createAnnenPersonJson(fnr = null, rolle =  "01") , null)

        every { fagmodulKlient.hentAlleDokumenter(any())} returns getResource("fagmodul/alldocumentsids_P_BUC_05_multiP8000.json")
        every { euxKlient.hentSed(any(), any()) } returns sedP8000_2 andThen sedP8000recevied andThen  sedP8000sendt
        every { diskresjonService.hentDiskresjonskode(any()) } returns null
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")
        every { personV3Service.hentPerson(afnr) } returns createBrukerWith(afnr, "Lever", "Helt i live", "NOR")
        every { personV3Service.hentPerson(fnr)} returns createBrukerWith(fnr, "Død", "Helt Død", "NOR")
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

        val hendelse = """
            {
              "id": 1869,
              "sedId": "P6000_40000000004_2",
              "sektorKode": "P",
              "bucType": "P_BUC_05",
              "rinaSakId": "147729",
              "avsenderId": "NO:NAVT003",
              "avsenderNavn": "NAVT003",
              "avsenderLand": "NO",
              "mottakerId": "NO:NAVT007",
              "mottakerNavn": "NAV Test 07",
              "mottakerLand": "NO",
              "rinaDokumentId": "40000000004",
              "rinaDokumentVersjon": "2",
              "sedType": "P6000",
              "navBruker": null
            }
        """.trimIndent()

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
        val sedP8000recevied = createSedJson(SedType.P8000, null, fdato = "1955-07-11")
        val sedP5000sent = createSedP5000(FNR_OVER_60, FNR_BARN)

        val alleDocumenter = mockAllDocumentsBuc( listOf(
                Triple("10001", "P8000", "received"),
                Triple("30002", "P5000", "sent")
        ))

        every { personV3Service.hentPerson(FNR_BARN) } returns createBrukerWith(FNR_BARN, "Lever", "Helt i live", "NOR")
        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_OVER_60, "Død", "Helt Død", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_BARN)) } returns AktoerId(FNR_BARN + "00000")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(FNR_OVER_60 + "11111")

        every { fagmodulKlient.hentAlleDokumenter(any())} returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP8000recevied andThen sedP5000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = """
            {
              "id": 1869,
              "sedId": "P6000_40000000004_2",
              "sektorKode": "P",
              "bucType": "P_BUC_05",
              "rinaSakId": "147729",
              "avsenderId": "NO:NAVT003",
              "avsenderNavn": "NAVT003",
              "avsenderLand": "NO",
              "mottakerId": "NO:NAVT007",
              "mottakerNavn": "NAV Test 07",
              "mottakerLand": "NO",
              "rinaDokumentId": "40000000004",
              "rinaDokumentVersjon": "2",
              "sedType": "P5000",
              "navBruker": null
            }
        """.trimIndent()

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
        val sedP8000recevied = createSedJson(SedType.P8000, null, fdato = "1955-07-11")
        val sedP5000sent = createSedJson(SedType.P5000, null, fdato = "1955-07-11")

        val alleDocumenter = mockAllDocumentsBuc( listOf(
                Triple("10001", "P8000", "received"),
                Triple("30002", "P5000", "sent")
        ))

        every { fagmodulKlient.hentAlleDokumenter(any())} returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP8000recevied andThen sedP5000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = """
            {
              "id": 1869,
              "sedId": "P6000_40000000004_2",
              "sektorKode": "P",
              "bucType": "P_BUC_05",
              "rinaSakId": "147729",
              "avsenderId": "NO:NAVT003",
              "avsenderNavn": "NAVT003",
              "avsenderLand": "NO",
              "mottakerId": "NO:NAVT007",
              "mottakerNavn": "NAV Test 07",
              "mottakerLand": "NO",
              "rinaDokumentId": "40000000004",
              "rinaDokumentVersjon": "2",
              "sedType": "P5000",
              "navBruker": null
            }
        """.trimIndent()

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
        val sedP8000recevied = createSedJson(SedType.P8000, null, fdato = "1955-07-11")
        val sedP5000sent = createSedJson(SedType.P5000, fnr)

        val alleDocumenter = mockAllDocumentsBuc( listOf(
                Triple("10001", "P8000", "received"),
                Triple("30002", "P5000", "sent")
        ))

        every { fagmodulKlient.hentAlleDokumenter(any())} returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP8000recevied andThen sedP5000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")

        every { personV3Service.hentPerson(fnr) } returns createBrukerWith(fnr, "Lever", "Helt i live", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoer)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = """
            {
              "id": 1869,
              "sedId": "P6000_40000000004_2",
              "sektorKode": "P",
              "bucType": "P_BUC_05",
              "rinaSakId": "147729",
              "avsenderId": "NO:NAVT003",
              "avsenderNavn": "NAVT003",
              "avsenderLand": "NO",
              "mottakerId": "NO:NAVT007",
              "mottakerNavn": "NAV Test 07",
              "mottakerLand": "NO",
              "rinaDokumentId": "40000000004",
              "rinaDokumentVersjon": "2",
              "sedType": "P5000",
              "navBruker": null
            }
        """.trimIndent()

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
    fun `Scenario 13 - 3 Sed sendes som svar med fnr og sak finnes og er GENRL pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema NFP UTLAND AALESUND`() {
        val fnr = "07115521999"
        val sakid = "1231232323"
        val aktoer = "${fnr}111"
        val sedP8000recevied = createSedJson(SedType.P8000, null, fdato = "1955-07-11")
        val sedP5000sent = createSedJson(SedType.P5000, fnr, eessiSaknr = sakid)

        val alleDocumenter = mockAllDocumentsBuc( listOf(
                Triple("10001", "P8000", "received"),
                Triple("30002", "P5000", "sent")
        ))

        every { fagmodulKlient.hentAlleDokumenter(any())} returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP8000recevied andThen sedP5000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")
        every { personV3Service.hentPerson(fnr) } returns createBrukerWith(fnr, "Lever", "Helt i live", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoer)

        val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = sakid, sakType = YtelseType.GENRL, sakStatus = SakStatus.LOPENDE)
        )
        every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = """
            {
              "id": 1869,
              "sedId": "P6000_40000000004_2",
              "sektorKode": "P",
              "bucType": "P_BUC_05",
              "rinaSakId": "147729",
              "avsenderId": "NO:NAVT003",
              "avsenderNavn": "NAVT003",
              "avsenderLand": "NO",
              "mottakerId": "NO:NAVT007",
              "mottakerNavn": "NAV Test 07",
              "mottakerLand": "NO",
              "rinaDokumentId": "40000000004",
              "rinaDokumentVersjon": "2",
              "sedType": "P5000",
              "navBruker": null
            }
        """.trimIndent()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(NFP_UTLAND_AALESUND, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)

        assertEquals("UTGAAENDE", request.journalpostType.name)
        assertEquals(PENSJON, request.tema)
        assertEquals(NFP_UTLAND_AALESUND, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }

    }

    @Test
    fun `Scenario 13 - 4 Sed sendes som svar med fnr utland og sak finnes og er GENRL pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema PENSJON UTLAND`() {
        val fnr = "07115521999"
        val sakid = "1231232323"
        val aktoer = "${fnr}111"
        val sedP8000recevied = createSedJson(SedType.P8000, null, fdato = "1955-07-11")
        val sedP5000sent = createSedJson(SedType.P5000, fnr, eessiSaknr = sakid)

        val alleDocumenter = mockAllDocumentsBuc( listOf(
                Triple("10001", "P8000", "received"),
                Triple("30002", "P5000", "sent")
        ))

        every { fagmodulKlient.hentAlleDokumenter(any())} returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP8000recevied andThen sedP5000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")
        every { personV3Service.hentPerson(fnr) } returns createBrukerWith(fnr, "Lever", "Helt i live", "SWE")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoer)

        val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = sakid, sakType = YtelseType.GENRL, sakStatus = SakStatus.LOPENDE)
        )
        every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = """
            {
              "id": 1869,
              "sedId": "P6000_40000000004_2",
              "sektorKode": "P",
              "bucType": "P_BUC_05",
              "rinaSakId": "147729",
              "avsenderId": "NO:NAVT003",
              "avsenderNavn": "NAVT003",
              "avsenderLand": "NO",
              "mottakerId": "NO:NAVT007",
              "mottakerNavn": "NAV Test 07",
              "mottakerLand": "NO",
              "rinaDokumentId": "40000000004",
              "rinaDokumentVersjon": "2",
              "sedType": "P5000",
              "navBruker": null
            }
        """.trimIndent()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(PENSJON_UTLAND, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)

        assertEquals("UTGAAENDE", request.journalpostType.name)
        assertEquals(PENSJON, request.tema)
        assertEquals(PENSJON_UTLAND, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }

    }

    @Test
    fun `Scenario 13 - 5 Sed sendes som svar med fnr og sak finnes og er UFOREP pa tidligere mottatt P8000, journalføres automatisk`() {
        val fnr = "07115521999"
        val sakid = "1231232323"
        val aktoer = "${fnr}111"
        val sedP8000recevied = createSedJson(SedType.P8000, null, fdato = "1955-07-11")
        val sedP5000sent = createSedJson(SedType.P5000, fnr, eessiSaknr = sakid)

        val alleDocumenter = mockAllDocumentsBuc( listOf(
                Triple("10001", "P8000", "received"),
                Triple("30002", "P5000", "sent")
        ))

        every { fagmodulKlient.hentAlleDokumenter(any())} returns alleDocumenter
        every { euxKlient.hentSed(any(), any()) } returns sedP8000recevied andThen sedP5000sent
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")
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
        val hendelse = """
            {
              "id": 1869,
              "sedId": "P6000_40000000004_2",
              "sektorKode": "P",
              "bucType": "P_BUC_05",
              "rinaSakId": "147729",
              "avsenderId": "NO:NAVT003",
              "avsenderNavn": "NAVT003",
              "avsenderLand": "NO",
              "mottakerId": "NO:NAVT007",
              "mottakerNavn": "NAV Test 07",
              "mottakerLand": "NO",
              "rinaDokumentId": "40000000004",
              "rinaDokumentVersjon": "2",
              "sedType": "P5000",
              "navBruker": null
            }
        """.trimIndent()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured

        assertEquals("UTGAAENDE", request.journalpostType.name)
        assertEquals(UFORETRYGD, request.tema)
        assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }

    }

    private fun getResource(resourcePath: String): String? =
            javaClass.classLoader.getResource(resourcePath)!!.readText()
}
