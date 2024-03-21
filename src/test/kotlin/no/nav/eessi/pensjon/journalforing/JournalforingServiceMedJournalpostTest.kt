package no.nav.eessi.pensjon.journalforing

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

private const val AKTOERID = "12078945602"
private const val RINADOK_ID = "3123123"
private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!

internal class JournalforingServiceMedJournalpostTest :  JournalforingServiceBase(){

    @Test
    fun `Sendt P6000 med all infor for forsoekFerdigstill true skal populere Journalpostresponsen med pesys sakid`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_06_P6000.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        val saksInformasjon = SakInformasjon(sakId = "22874955", sakType = SakType.ALDER, sakStatus = SakStatus.LOPENDE)

        val requestSlot = slot<OpprettJournalpostRequest>()
        val forsoekFedrigstillSlot = slot<Boolean>()
        every { journalpostKlient.opprettJournalpost(capture(requestSlot), capture(forsoekFedrigstillSlot), any()) } returns mockk(relaxed = true)


        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            SakType.ALDER,
            sakInformasjon = saksInformasjon,
            SED(type = SedType.P6000),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null,
        )
        val journalpostRequest = requestSlot.captured
        val erMuligAaFerdigstille = forsoekFedrigstillSlot.captured

        println(journalpostRequest)

        Assertions.assertEquals("22874955", journalpostRequest.sak?.fagsakid)
        Assertions.assertEquals(true, erMuligAaFerdigstille)

    }

    private fun navAnsattInfo(): Pair<String, Enhet?> = Pair("Z990965",Enhet.NFP_UTLAND_AALESUND)

    @Test
    fun `Sendt P6000 med manglende saksinfo skal returnere false på forsoekFerdigstill`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_06_P6000.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        val forsoekFerdigstillSlot = slot<Boolean>()
        every { journalpostKlient.opprettJournalpost(any(), capture(forsoekFerdigstillSlot), any()) } returns mockk(relaxed = true)


        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            null,
            sakInformasjon = null,
            SED(type = SedType.P6000),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        val erMuligAaFerdigstille = forsoekFerdigstillSlot.captured

        Assertions.assertEquals(false, erMuligAaFerdigstille)

    }

    @Disabled
    @Test
    fun `Innkommende P2000 fra Sverige som oppfyller alle krav til maskinell journalføring skal opprette behandle SED oppgave`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_SE.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        val saksInformasjon = SakInformasjon(sakId = "22874955", sakType = SakType.ALDER, sakStatus = SakStatus.LOPENDE)

        val requestSlot = slot<OpprettJournalpostRequest>()
        val forsoekFedrigstillSlot = slot<Boolean>()
        every { journalpostKlient.opprettJournalpost(capture(requestSlot), capture(forsoekFedrigstillSlot), null) } returns mockk(relaxed = true)

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            SakType.ALDER,
            sakInformasjon = saksInformasjon,
            SED(type = SedType.P2000),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            gjennySakId = null
        )
        val journalpostRequest = requestSlot.captured
        val erMuligAaFerdigstille = forsoekFedrigstillSlot.captured

        println(journalpostRequest)

        verify(exactly = 1) { journalforingService.opprettBehandleSedOppgave(any(), any(), any(), any()) }

        Assertions.assertEquals("22874955", journalpostRequest.sak?.fagsakid)
        Assertions.assertEquals(true, erMuligAaFerdigstille)
        Assertions.assertEquals(Enhet.ID_OG_FORDELING, journalpostRequest.journalfoerendeEnhet)

    }
}