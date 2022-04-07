package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.klienter.pesys.BestemSakResponse
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("P_BUC_07 – IntegrationTest")
internal class PBuc07IntegrationTest : JournalforingTestBase() {

    @Nested
    @DisplayName("Inngående")
    inner class Scenario1Inngaende {
        @Test
        fun `Gjenlevende der fdato er det samme som FNR`() {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P12000, SedStatus.RECEIVED)
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                krav = KravType.ETTERLATTE,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.MOTTATT,
                norg2enhet = null,
                fdatoBruker = Fodselsnummer.fra(FNR_VOKSEN_2)?.getBirthDate().toString()
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Gjenlevende der fdato er forskjellig fra FNR skal sendes til id og fordeling adsdasd`() {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P12000, SedStatus.RECEIVED)
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                krav = KravType.ETTERLATTE,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.MOTTATT,
                norg2enhet = null,
                fdatoBruker = "1988-01-01"
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }


    private fun testRunnerVoksen(
        fnrVoksen: String,
        fnrVoksenSoker: String?,
        bestemSak: BestemSakResponse? = null,
        land: String = "NOR",
        krav: KravType = KravType.ETTERLATTE,
        alleDocs: List<ForenkletSED>,
        relasjonAvod: RelasjonTilAvdod? = RelasjonTilAvdod.EGET_BARN,
        hendelseType: HendelseType,
        norg2enhet: Enhet? = null,
        fdatoBruker: String? = null,
        block: (OpprettJournalpostRequest) -> Unit
    ) {
        val eessisaknr = if (bestemSak?.sakInformasjonListe?.size == 1) {
            bestemSak.sakInformasjonListe.first().sakId
        } else {
            null
        }

        val sed = createSedPensjon(SedType.P12000, fnrVoksen, eessisaknr,  gjenlevendeFnr = fnrVoksenSoker, krav = krav, relasjon = relasjonAvod, fdato = fdatoBruker)
        initCommonMocks(sed, alleDocs)

        every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns createBrukerWith(
            fnrVoksen,
            "Voksen ",
            "Forsikret",
            land,
            aktorId = AKTOER_ID
        )

        if (fnrVoksenSoker != null) {
            every { personService.hentPerson(NorskIdent(fnrVoksenSoker)) } returns createBrukerWith(
                fnrVoksenSoker,
                "Voksen",
                "Gjenlevende",
                land,
                aktorId = AKTOER_ID_2
            )
        }
        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

        if (bestemSak != null) {
            every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID_2) } returns bestemSak.sakInformasjonListe
        }

        val (journalpost, _) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P12000, BucType.P_BUC_07, FNR_VOKSEN)

        val meldingSlot = slot<String>()

        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns norg2enhet

        when (hendelseType) {
            HendelseType.SENDT -> sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            HendelseType.MOTTATT -> mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            else -> Assertions.fail()
        }

        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())
        Assertions.assertEquals(hendelseType, oppgaveMelding.hendelseType)

        block(journalpost.captured)

        verify { personService.hentPerson(any<Ident<*>>()) }
        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        clearAllMocks()
    }
}

