package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.pesys.BestemSakResponse
import no.nav.eessi.pensjon.models.Enhet.AUTOMATISK_JOURNALFORING
import no.nav.eessi.pensjon.models.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.models.Enhet.NFP_UTLAND_AALESUND
import no.nav.eessi.pensjon.models.Enhet.PENSJON_UTLAND
import no.nav.eessi.pensjon.models.Enhet.UFORE_UTLANDSTILSNITT
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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity

internal class PBuc05Test : JournalforingTestBase() {

    @Test
    fun `Scenario 3 - 1 person i SED fnr finnes og bestemsak finner sak GENRL Så journalføres automatisk og tema på PENSJON`() {
        val fnr = "12078945600"
        val aktoer = "${fnr}1111"
        val saknr = "1223123123"

        val sed = createSedJson(SedType.P8000, fnr,null , saknr)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(fnr)} returns createBrukerWith(fnr, "Fornavn", "Etternavn", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoer)

        val saker = listOf(SakInformasjon(sakId = saknr, sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING))
        every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker
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

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 4 - 1 person i SED fnr finnes og bestemsak finner flere sak Så journalføres manuelt på teama PENSJON og enhet UFORE_UTLANDSTILSNITT`() {
        val fnr = "12078945600"
        val aktoer = "${fnr}1111"
        val saknr = "1223123123"

        val sed = createSedJson(SedType.P8000, fnr,null , saknr)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(fnr)} returns createBrukerWith(fnr, "Fornavn", "Etternavn", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoer)

        val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                SakInformasjon(sakId = saknr, sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                SakInformasjon(sakId = "34234123", sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET)
        )
        every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker
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

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 4 - 2 personer i SED fnr finnes, rolle er 02 og bestemsak finner flere sak Så journalføres manuelt på teama PENSJON og ehent NFP_UTLAND_AALESUND`() {
        val fnr = "07055045600"
        val afnr = "22078945600"
        val aktoer = "${fnr}1111"
        val saknr = "1223123123"

        val sed = createSedJson(SedType.P8000, fnr,createAnnenPersonJson(fnr = afnr, fdato =  "1950-05-07", rolle =  "02") , saknr)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(fnr)} returns createBrukerWith(fnr, "Fornavn forsørger", "Etternavn", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoer)

        val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                SakInformasjon(sakId = saknr, sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                SakInformasjon(sakId = "34234123", sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET)
        )
        every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker
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
        assertEquals(NFP_UTLAND_AALESUND, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 5 - 1 person i SED fnr finnes og bestemsak finner sak UFORE Så journalføres automatisk på tema UFORETRYGD`() {
        val fnr = "12078945600"
        val aktoer = "${fnr}1111"
        val saknr = "1223123123"

        val sed = createSedJson(SedType.P8000, fnr,null , saknr)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(fnr)} returns createBrukerWith(fnr, "Fornavn", "Etternavn", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoer)

        val saker = listOf(SakInformasjon(sakId = saknr, sakType = YtelseType.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING))
        every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker
        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured

        // forvent tema == PEN og enhet 9999
        assertEquals(UFORETRYGD, request.tema)
        assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 5 - 1 person i SED fnr finnes og bestemsak finner sak ALDER Så journalføres automatisk på tema PENSJON`() {
        val fnr = "12078945600"
        val aktoer = "${fnr}1111"
        val saknr = "1223123123"

        val sed = createSedJson(SedType.P8000, fnr,null , saknr)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(fnr)} returns createBrukerWith(fnr, "Fornavn", "Etternavn", "NOR")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoer)

        val saker = listOf(
                SakInformasjon(sakId = saknr, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                SakInformasjon(sakId = "2131123123", sakType = YtelseType.GENRL, sakStatus = SakStatus.LOPENDE)
        )
        every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker
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

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 2 - 2 personer i SED og mangler rolle ingen fnr Så journalføres manuelt og tema på PENSJON enhet på ID_OG_FORDELING`() {
        val fnr = null

        val sed = createSedJson(SedType.P8000, fnr, createAnnenPersonJson(rolle =  null), null)

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
        val afnr = "07055045602"
        val pfnr = "12078945600"

        val sed = createSedJson(SedType.P8000, pfnr, createAnnenPersonJson(fnr = afnr, fdato =  "1950-05-07", rolle =  "01"), null)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(afnr) } returns createBrukerWith(afnr, "Lever", "Helt i live", "NOR")
        every { personV3Service.hentPerson(pfnr)} returns createBrukerWith(pfnr, "Død", "Helt Død", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(afnr)) } returns AktoerId(afnr+"00000")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(pfnr)) } returns AktoerId(pfnr+"11111")

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
    fun `Scenario 6 - 2 personer i SED, har rolle GJENLEV, fnr finnes og UTLAND og ingen bestemsak Så journalføres manuelt, tema PENSJON og enhet PENSJON_UTLAND`() {
        val afnr = "07055045602"
        val pfnr = "12078945600"

        val sed = createSedJson(SedType.P8000, pfnr, createAnnenPersonJson(fnr = afnr, fdato =  "1950-05-07", rolle =  "01"), null)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(afnr) } returns createBrukerWith(afnr, "Lever", "Helt i live", "SWE")
        every { personV3Service.hentPerson(pfnr)} returns createBrukerWith(pfnr, "Død", "Helt Død", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(afnr)) } returns AktoerId(afnr+"00000")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(pfnr)) } returns AktoerId(pfnr+"11111")

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
    fun `Scenario 6 - 2 personer i SED, har rolle GJENLEV, fnr finnes og ingen bestemsak Så journalføres manuelt, tema PENSJON og enhet UFORE_UTLANDSTILSNITT)`() {
        val afnr = "12058005602"
        val pfnr = "12078945600"

        val sed = createSedJson(SedType.P8000, pfnr, createAnnenPersonJson(fnr = afnr, rolle =  "01"), null)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(afnr) } returns createBrukerWith(afnr, "Lever", "Helt i live", "NOR")
        every { personV3Service.hentPerson(pfnr)} returns createBrukerWith(pfnr, "Død", "Helt Død", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(afnr)) } returns AktoerId(afnr+"00000")
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(pfnr)) } returns AktoerId(pfnr+"11111")

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
        val fnr = "12078945600"
        val afnr = "12058005602"
        val aktoera = "${fnr}1111"
        val aktoerf = "${fnr}0000"
        val saknr = "1223123123"

        val sed = createSedJson(SedType.P8000, fnr,createAnnenPersonJson(fnr = afnr, fdato =  "1950-05-07", rolle =  "01") , saknr)
        initCommonMocks(sed)

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

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured

        println(request.toJson())

        // forvent tema == PEN og enhet 9999
        assertEquals(UFORETRYGD, request.tema)
        assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)
        assertEquals(afnr, request.bruker?.id)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 1 - 1 person i SED fnr mangler Så journalføres på ID_OG_FORDELING`() {
        val sed = createSedJson(SedType.P8000)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(any()) } returns Bruker()

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

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
        val fnr = "12058005602"
        val afnr = "12078945600"
        val aktoera = "${fnr}1111"
        val aktoerf = "${fnr}0000"
        val saknr = "1223123123"

        val sed = createSedJson(SedType.P8000, fnr,createAnnenPersonJson(fnr = afnr, fdato =  "1950-05-07", rolle =  "02") , saknr)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(fnr) } returns createBrukerWith(afnr, "Hovedpersonen", "forsikret", "NOR")
        every { personV3Service.hentPerson(afnr)} returns createBrukerWith(fnr, "Ikke hovedperson", "familiemedlem", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(afnr)) } returns AktoerId(aktoera)
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoerf)

        val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = saknr, sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "34234123", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET)
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
        assertEquals(UFORETRYGD, request.tema)
        assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)
        assertEquals(fnr, request.bruker?.id)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 7 - 2 personer i SED, har rolle familiemedlem, fnr finnes og bestemsak finner sak ALDER Så journalføres automatisk på tema PENSJON`() {
        val fnr = "12058005602"
        val afnr = "12078945600"
        val aktoera = "${fnr}1111"
        val aktoerf = "${fnr}0000"
        val saknr = "1223123123"

        val sed = createSedJson(SedType.P8000, fnr,createAnnenPersonJson(fnr = afnr, fdato =  "1950-05-07", rolle =  "02") , saknr)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(fnr) } returns createBrukerWith(afnr, "Hovedpersonen", "forsikret", "NOR")
        every { personV3Service.hentPerson(afnr)} returns createBrukerWith(fnr, "Ikke hovedperson", "familiemedlem", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(afnr)) } returns AktoerId(aktoera)
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoerf)

        val saker = listOf(
                SakInformasjon(sakId = saknr, sakType = YtelseType.ALDER, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "123123123", sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "34234123", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET)
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
        assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)
        assertEquals(fnr, request.bruker?.id)

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

        val sed = createSedJson(SedType.P8000, fnr,createAnnenPersonJson(fnr = afnr, fdato =  "1950-05-07", rolle =  "02") , saknr)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(fnr) } returns createBrukerWith(afnr, "Hovedpersonen", "forsikret", "NOR")
        every { personV3Service.hentPerson(afnr)} returns createBrukerWith(fnr, "Ikke hovedperson", "familiemedlem", "NOR")

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
    @Disabled
    fun `Scenario 7 - 2 personer i SED, har rolle familiemedlem, fnr finnes og bestemsak finner ingen sak Så journalføres manuelt på tema PENSJON og enhet ID_OG_FORDELING`() {
        val fnr = "12055005602"
        val afnr = "12078945600"
        val aktoera = "${fnr}1111"
        val aktoerf = "${fnr}0000"
        val saknr = "122x31 231 /23q"

        val sed = createSedJson(SedType.P8000, fnr,createAnnenPersonJson(fnr = afnr, fdato =  "1989-05-07", rolle =  "02") , saknr)
        initCommonMocks(sed)

        every { personV3Service.hentPerson(fnr) } returns createBrukerWith(afnr, "Hovedpersonen", "forsikret", "NOR")
        every { personV3Service.hentPerson(afnr)} returns createBrukerWith(fnr, "Ikke hovedperson", "familiemedlem", "NOR")

        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(afnr)) } returns AktoerId(aktoera)
        every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(aktoerf)

        val saker = listOf(
                SakInformasjon(sakId = "234123123", sakType = YtelseType.ALDER, sakStatus = SakStatus.AVSLUTTET)
        )
        every { fagmodulKlient.hentPensjonSaklist(aktoerf) } returns saker
        //every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

        val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        val request = journalpost.captured
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())


        assertEquals("JOURNALFORING", oppgaveMelding.oppgaveType())
        assertEquals(ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
        assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)

        // forvent tema == PEN og enhet 9999
        assertEquals(PENSJON, request.tema)
        assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)
        assertEquals(fnr, request.bruker?.id)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    private fun initCommonMocks(sed: String) {
        every { fagmodulKlient.hentAlleDokumenter(any()) } returns getResource("fagmodul/alldocumentsids.json")
        every { euxKlient.hentSed(any(), any()) } returns sed
        every { diskresjonService.hentDiskresjonskode(any()) } returns null
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")
    }

    private fun getResource(resourcePath: String): String? =
        javaClass.classLoader.getResource(resourcePath)!!.readText()
}
