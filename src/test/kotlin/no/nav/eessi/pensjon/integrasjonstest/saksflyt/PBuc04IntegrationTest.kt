package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("P_BUC_04 – IntegrationTest")
internal class PBuc04IntegrationTest: JournalforingTestBase() {

    @Nested
    @DisplayName("Utgående")
    inner class UtgaaendeP_BUC_04 {

        @Test
        fun `1 person i SED fnr finnes Then journalfores On NFP_UTLAND_AALESUND med tema PENSJON`() {

            testRunnerP1000(FNR_VOKSEN) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

    }

    private fun testRunnerP1000(
        fnr: String?,
        land: String = "NOR",
        hendelseType: HendelseType = HendelseType.SENDT,
        assertBlock: (OpprettJournalpostRequest) -> Unit
    ) {
        val sed = SED.generateSedToClass<SED>(createSed(SedType.P1000, fnr))
        initCommonMocks(sed)

        every { personService.harAdressebeskyttelse(any(), any()) } returns false

        if (fnr != null) {
            every { personService.hentPerson(NorskIdent(fnr)) } returns createBrukerWith(
                fnr,
                "Fornavn",
                "Etternavn",
                land,
                aktorId = AKTOER_ID
            )
        }
        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P1000, BucType.P_BUC_04)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        if (hendelseType == HendelseType.SENDT)
            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
        else
            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        assertBlock(journalpost.captured)

        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }
        verify(exactly = 0) { bestemSakKlient.kallBestemSak(any()) }

        clearAllMocks()
    }
}