package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.klienter.pesys.BestemSakResponse
import no.nav.eessi.pensjon.models.Enhet.AUTOMATISK_JOURNALFORING
import no.nav.eessi.pensjon.models.Enhet.DISKRESJONSKODE
import no.nav.eessi.pensjon.models.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.models.Enhet.NFP_UTLAND_AALESUND
import no.nav.eessi.pensjon.models.Enhet.PENSJON_UTLAND
import no.nav.eessi.pensjon.models.Enhet.UFORE_UTLAND
import no.nav.eessi.pensjon.models.Enhet.UFORE_UTLANDSTILSNITT
import no.nav.eessi.pensjon.models.HendelseType.SENDT
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

internal class PBuc05Test : JournalforingTestBase() {

    companion object {
        private const val FNR_OVER_60 = "01115043352"
        private const val FNR_VOKSEN = "01119043352"
        private const val FNR_VOKSEN_2 = "01118543352"
        private const val FNR_BARN = "01110854352"

        private const val AKTOER_ID = "0123456789000"
        private const val AKTOER_ID_2 = "0009876543210"

        private const val SAK_ID = "12345"
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

        every { fagmodulKlient.hentAlleDokumenter(any())} returns String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocumentsids_P_BUC_05_multiP8000.json")))
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
    fun `Scenario 3 - 1 person i SED fnr finnes, saktype er GENRL`() {
        val saker = listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING))

        testRunner(FNR_OVER_60, saker) {
            assertEquals(PENSJON, it.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
        }

        testRunner(FNR_VOKSEN, saker) {
            assertEquals(PENSJON, it.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
        }

        testRunner(FNR_BARN, saker) {
            assertEquals(PENSJON, it.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 4 - 1 person i SED fnr finnes og saktype er GENRL, med flere sakstyper, person bosatt Norge`() {
        val saker = listOf(
                SakInformasjon(SAK_ID, YtelseType.GENRL, SakStatus.TIL_BEHANDLING),
                SakInformasjon("1240128", YtelseType.BARNEP, SakStatus.TIL_BEHANDLING)
        )

        testRunner(FNR_VOKSEN, saker) {
            assertEquals(PENSJON, it.tema)
            assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
        }

        testRunner(FNR_OVER_60, saker) {
            assertEquals(PENSJON, it.tema)
            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
        }

        testRunner(FNR_BARN, saker) {
            assertEquals(PENSJON, it.tema)
            assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 4 - 1 person i SED fnr finnes, saktype er GENRL, med flere sakstyper, person bosatt utland`() {
        val saker = listOf(
                SakInformasjon(SAK_ID, YtelseType.GENRL, SakStatus.TIL_BEHANDLING),
                SakInformasjon("124123", YtelseType.BARNEP, SakStatus.TIL_BEHANDLING)
        )

        testRunner(FNR_VOKSEN, saker, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
        }

        testRunner(FNR_OVER_60, saker, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
        }

        testRunner(FNR_BARN, saker, land = "SWE") {
            assertEquals(PENSJON, it.tema)
            assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 4 - 2 personer i SED fnr finnes, rolle er 02 og bestemsak finner flere sak Så journalføres manuelt på tema PENSJON og enhet NFP_UTLAND_AALESUND`() {
        val sed = createSedJson(SedType.P8000, FNR_OVER_60, createAnnenPersonJson(fnr = FNR_VOKSEN, rolle = "02"), SAK_ID)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_OVER_60, "Fornavn forsørger", "Etternavn", "NOR")
        every { personV3Service.hentPerson(FNR_VOKSEN) } returns createBrukerWith(FNR_VOKSEN, "familiemedlem", "Etternavn", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(AKTOER_ID)

        val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                SakInformasjon(sakId = SAK_ID, sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                SakInformasjon(sakId = "34234123", sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET)
        )
        every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID) } returns saker

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured

        // forvent tema == PEN og enhet 9999
        assertEquals(PENSJON, request.tema)
        assertEquals(NFP_UTLAND_AALESUND, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 5 - 1 person i SED fnr finnes og bestemsak finner sak UFORE Så journalføres automatisk på tema UFORETRYGD`() {
        val saker = listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING))

        testRunner(FNR_VOKSEN, saker) {
            assertEquals(UFORETRYGD, it.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
        }

        testRunner(FNR_OVER_60, saker) {
            assertEquals(UFORETRYGD, it.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
        }

        testRunner(FNR_BARN, saker) {
            assertEquals(UFORETRYGD, it.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 5 - 1 person i SED fnr finnes og bestemsak finner sak ALDER Så journalføres automatisk på tema PENSJON`() {
        val saker = listOf(
                SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                SakInformasjon(sakId = "2131123123", sakType = YtelseType.GENRL, sakStatus = SakStatus.LOPENDE)
        )

        testRunner(FNR_OVER_60, saker) {
            assertEquals(PENSJON, it.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 2 - 2 personer i SED og mangler rolle ingen fnr Så journalføres manuelt og tema på PENSJON enhet på ID_OG_FORDELING`() {
        val sed = createSedJson(SedType.P8000, null, createAnnenPersonJson(rolle = null), null)

        initCommonMocks(sed)

        val (journalpostSlot, _) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P8000)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpostSlot.captured

        // forvent tema == PEN og enhet 4303
        assertEquals(PENSJON, request.tema)
        assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 6 - 2 personer i SED, har rolle GJENLEV, fnr finnes og ingen bestemsak Så journalføres manuelt, tema PENSJON og enhet NFP_UTLAND_AALESUND`() {
        val sed = createSedJson(SedType.P8000, FNR_OVER_60, createAnnenPersonJson(fnr = FNR_BARN, rolle = "01"), null)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(FNR_BARN) } returns createBrukerWith(FNR_BARN, "Lever", "Helt i live", "NOR")
        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_OVER_60, "Død", "Helt Død", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_BARN)) } returns AktoerId(FNR_BARN + "00000")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(FNR_OVER_60 + "11111")

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(NFP_UTLAND_AALESUND, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)

        // forvent tema == PEN og enhet 4303
        assertEquals(PENSJON, request.tema)
        assertEquals(NFP_UTLAND_AALESUND, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 6 - 2 personer i SED, har rolle GJENLEV, fnr finnes og UTLAND og ingen bestemsak`() {
        val sed = createSedJson(SedType.P8000, FNR_OVER_60, createAnnenPersonJson(fnr = FNR_BARN, rolle = "01"), null)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(FNR_BARN) } returns createBrukerWith(FNR_BARN, "Lever", "Helt i live", "SWE")
        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_OVER_60, "Død", "Helt Død", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(FNR_OVER_60 + "00000")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_BARN)) } returns AktoerId(FNR_BARN + "11111")

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(PENSJON_UTLAND, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)

        // forvent tema == PEN og enhet 4303
        assertEquals(PENSJON, request.tema)
        assertEquals(PENSJON_UTLAND, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 6 - 2 personer i SED, har rolle GJENLEV, fnr finnes, men har ingen bestemsak`() {
        val sed = createSedJson(SedType.P8000, FNR_VOKSEN, createAnnenPersonJson(fnr = FNR_VOKSEN_2, rolle = "01"), null)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(FNR_VOKSEN) } returns createBrukerWith(FNR_VOKSEN, "Lever", "Helt i live", "NOR")
        every { personV3Service.hentPerson(FNR_VOKSEN_2) } returns createBrukerWith(FNR_VOKSEN_2, "Død", "Helt Død", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_VOKSEN)) } returns AktoerId(FNR_VOKSEN + "00000")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_VOKSEN_2)) } returns AktoerId(FNR_VOKSEN_2 + "11111")

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(UFORE_UTLANDSTILSNITT, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)

        assertEquals(PENSJON, request.tema)
        assertEquals(UFORE_UTLANDSTILSNITT, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 6 - 2 personer i SED, har rolle GJENLEV, fnr finnes og bestemsak finner sak UFØRE Så journalføres automatisk på tema UFORETRYGD`() {
        val sed = createSedJson(SedType.P8000, FNR_OVER_60, createAnnenPersonJson(fnr = FNR_VOKSEN, rolle = "01"), SAK_ID)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(FNR_VOKSEN) } returns createBrukerWith(FNR_VOKSEN, "Lever", "Helt i live", "NOR")
        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_OVER_60, "Død", "Helt Død", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_VOKSEN)) } returns AktoerId(AKTOER_ID)
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(AKTOER_ID_2)

        val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "34234123", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET)
        )
        every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID) } returns saker
        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured

        println(request.toJson())

        // forvent tema == PEN og enhet 9999
        assertEquals(UFORETRYGD, request.tema)
        assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)
        assertEquals(FNR_VOKSEN, request.bruker?.id)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 1 - 1 person i SED, men fnr mangler`() {
        testRunner(fnr1 = null) {
            assertEquals(PENSJON, it.tema)
            assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `Scenario 1 - 1 person i SED fnr finnes men ingen bestemsak Så journalføres på ID_OG_FORDELING`() {
        initCommonMocks(createSedJson(SedType.P8000))

        every { personV3Service.hentPerson(any()) } returns Bruker()
        every { bestemSakKlient.kallBestemSak(any()) } returns BestemSakResponse(null, emptyList())

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(SedType.P8000)

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured

        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)

        // forvent tema == PEN og enhet 4303
        assertEquals(PENSJON, request.tema)
        assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 7 - 2 personer i SED, har rolle familiemedlem, fnr finnes og bestemsak finner sak UFØRE Så journalføres automatisk på tema UFORETRYGD`() {
        val sed = createSedJson(SedType.P8000, FNR_OVER_60, createAnnenPersonJson(fnr = FNR_VOKSEN, rolle = "02"), SAK_ID)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_VOKSEN, "Hovedpersonen", "forsikret", "NOR")
        every { personV3Service.hentPerson(FNR_VOKSEN) } returns createBrukerWith(FNR_OVER_60, "Ikke hovedperson", "familiemedlem", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_VOKSEN)) } returns AktoerId(AKTOER_ID)
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(AKTOER_ID_2)

        val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "34234123", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET)
        )
        every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID_2) } returns saker
        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured

        println(request.toJson())

        // forvent tema == PEN og enhet 9999
        assertEquals(UFORETRYGD, request.tema)
        assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)
        assertEquals(FNR_OVER_60, request.bruker?.id)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 7 - 2 personer i SED, har rolle familiemedlem, fnr finnes og bestemsak finner sak ALDER Så journalføres automatisk på tema PENSJON`() {
        val sed = createSedJson(SedType.P8000, FNR_OVER_60, createAnnenPersonJson(fnr = FNR_VOKSEN, rolle = "02"), SAK_ID)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_VOKSEN, "Hovedpersonen", "forsikret", "NOR")
        every { personV3Service.hentPerson(FNR_VOKSEN) } returns createBrukerWith(FNR_OVER_60, "Ikke hovedperson", "familiemedlem", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_VOKSEN)) } returns AktoerId(AKTOER_ID)
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(AKTOER_ID_2)

        val saker = listOf(
                SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "123123123", sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "34234123", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET)
        )
        every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID_2) } returns saker
        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured

        // forvent tema == PEN og enhet 9999
        assertEquals(PENSJON, request.tema)
        assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)
        assertEquals(FNR_OVER_60, request.bruker?.id)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 7 - 2 personer i SED, har rolle familiemedlem, fnr finnes og bestemsak finner sak GENEREL Så journalføres manuelt på tema PENSJON og enhet UFORE_UTLANDSTILSNITT`() {
        val fnr = "12058005602"
        val afnr = "12078945600"
        val aktoera = "${fnr}1111"
        val aktoerf = "${fnr}0000"
        val saknr = "1223123123"

        val sed = createSedJson(SedType.P8000, fnr, createAnnenPersonJson(fnr = afnr, rolle = "02"), saknr)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(fnr) } returns createBrukerWith(afnr, "Hovedpersonen", "forsikret", "NOR")
        every { personV3Service.hentPerson(afnr) } returns createBrukerWith(fnr, "Ikke hovedperson", "familiemedlem", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(afnr)) } returns AktoerId(aktoera)
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoerf)

        val saker = listOf(
                SakInformasjon(sakId = "234123123", sakType = YtelseType.ALDER, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "123123123", sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = saknr, sakType = YtelseType.GENRL, sakStatus = SakStatus.LOPENDE)
        )
        every { fagmodulKlient.hentPensjonSaklist(aktoerf) } returns saker
        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured

        println(request.toJson())

        // forvent tema == PEN og enhet 9999
        assertEquals(PENSJON, request.tema)
        assertEquals(UFORE_UTLANDSTILSNITT, request.journalfoerendeEnhet)
        assertEquals(fnr, request.bruker?.id)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 8 - 2 personer i SED, har rolle barn 03 og ingen skjerming`() {
        val sed = createSedJson(SedType.P8000, FNR_OVER_60, createAnnenPersonJson(fnr = FNR_BARN, rolle = "03"), SAK_ID)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_OVER_60, "Hovedpersonen", "forsikret", "NOR")
        every { personV3Service.hentPerson(FNR_BARN) } returns createBrukerWith(FNR_BARN, "Ikke hovedperson", "familiemedlem", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(AKTOER_ID)
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_BARN)) } returns AktoerId("1111")

        every { diskresjonService.hentDiskresjonskode(any()) } returns null // Ingen skjerming (diskresjonskode == null)

        every {
            fagmodulKlient.hentPensjonSaklist(AKTOER_ID)
        } returns listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.AVSLUTTET))

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(AUTOMATISK_JOURNALFORING, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(AKTOER_ID, oppgaveMelding.aktoerId)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)

        val request = journalpost.captured
        // forvent tema == PEN og enhet 9999
        assertEquals(PENSJON, request.tema)
        assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)
        assertEquals(FNR_OVER_60, request.bruker?.id)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 9 - 2 personer i SED fnr finnes, barn med kode6 opprettes en journalføringsoppgave på tema PEN og enhet 2103 Vikafossen`() {
        val sed = createSedJson(SedType.P8000, FNR_OVER_60, createAnnenPersonJson(fnr = FNR_BARN, rolle = "03"), SAK_ID)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(FNR_OVER_60) } returns createBrukerWith(FNR_OVER_60, "Mamma forsørger", "Etternavn", "NOR")

        val barn = createBrukerWith(FNR_BARN, "Barn", "Diskret", "NOR", "1213", "SPSF")
        every { personV3Service.hentPerson(FNR_BARN) } returns barn

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_OVER_60)) } returns AktoerId(AKTOER_ID)
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(FNR_BARN)) } returns AktoerId(AKTOER_ID_2)

        val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = YtelseType.GJENLEV, sakStatus = SakStatus.TIL_BEHANDLING),
                SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)
        )
        every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID) } returns saker

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())

        // forvent tema == PEN og enhet 2103
        assertEquals(UFORETRYGD, request.tema)
        assertEquals(DISKRESJONSKODE, request.journalfoerendeEnhet)

        assertEquals(DISKRESJONSKODE, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(SENDT, oppgaveMelding.hendelseType)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    private fun testRunner(fnr1: String?,
                           saker: List<SakInformasjon> = emptyList(),
                           sakId: String? = SAK_ID,
                           land: String = "NOR",
                           request: (OpprettJournalpostRequest) -> Unit
    ) {
        val sed = createSedJson(SedType.P8000, fnr1, null, sakId)
        initCommonMocks(sed)

        if (fnr1 != null) {
            every { personV3Service.hentPerson(fnr1) } returns createBrukerWith(fnr1, "Fornavn", "Etternavn", land)
            every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr1)) } returns AktoerId(AKTOER_ID)
        }

        every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID) } returns saker
        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        request(journalpost.captured)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
        verify(exactly = 0) { bestemSakKlient.kallBestemSak(any()) }

        if (saker.isEmpty())
            verify(exactly = 0) { fagmodulKlient.hentPensjonSaklist(any()) }
        else
            verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }

        clearAllMocks()
    }

    private fun initCommonMocks(sed: String) {
        every { fagmodulKlient.hentAlleDokumenter(any()) } returns getResource("fagmodul/alldocumentsids.json")
        every { euxKlient.hentSed(any(), any()) } returns sed
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")
    }

    private fun getResource(resourcePath: String): String? =
            javaClass.classLoader.getResource(resourcePath)!!.readText()
}
