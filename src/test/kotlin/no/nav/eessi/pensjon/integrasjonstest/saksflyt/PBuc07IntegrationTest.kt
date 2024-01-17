package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.*
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_07
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.journalforing.oppgave.OppgaveMelding
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.klienter.pesys.BestemSakResponse
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.Enhet.UFORE_UTLANDSTILSNITT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@DisplayName("P_BUC_07 – IntegrationTest")
internal class PBuc07IntegrationTest : JournalforingTestBase() {

    @Nested
    @DisplayName("Inngående")
    inner class Scenario1Inngaende {
        @ParameterizedTest
        @EnumSource(SedType::class, names = ["P12000", "P11000"])
        fun `Gitt en SED med forsikret uten gjenlevende saa skal forsikret benyttes som identifisert person`(sedType: SedType) {
            val allDocuemtActions = forenkletSEDS()

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                fnrVoksenSoker = null,
                krav = KravType.ALDER,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                fdatoBruker = "1971-06-11",
                sedType = sedType
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.first.tema)
                Assertions.assertEquals(UFORE_UTLANDSTILSNITT, it.first.journalfoerendeEnhet)
                Assertions.assertEquals(AKTOER_ID, it.second.aktoerId)
            }
        }

        @ParameterizedTest
        @EnumSource(SedType::class, names = ["P12000", "P11000"])
        fun `Gitt en SED med forsikret OG gjenlevende saa skal gjenlevende benyttes som identifisert person`(sedType: SedType) {
            val allDocuemtActions = forenkletSEDS()

            testRunnerVoksen(
                fnrVoksen = FNR_VOKSEN_UNDER_62,
                fnrVoksenSoker = FNR_VOKSEN_2,
                krav = KravType.ALDER,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                fdatoBruker = "1971-06-11",
                sedType = sedType
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.first.tema)
                Assertions.assertEquals(UFORE_UTLANDSTILSNITT, it.first.journalfoerendeEnhet)
                Assertions.assertEquals(AKTOER_ID_2, it.second.aktoerId)

            }
        }
    }

    private fun forenkletSEDS() = listOf(
        ForenkletSED("10001212", SedType.P12000, SedStatus.RECEIVED)
    )

    private fun testRunnerVoksen(
        fnrVoksen: String,
        fnrVoksenSoker: String?,
        aktor_voksen_1: String = AKTOER_ID,
        aktor_voksen_2: String = AKTOER_ID_2,
        bestemSak: BestemSakResponse? = null,
        land: String = "NOR",
        krav: KravType = KravType.GJENLEV,
        alleDocs: List<ForenkletSED>,
        relasjonAvod: RelasjonTilAvdod? = RelasjonTilAvdod.EGET_BARN,
        hendelseType: HendelseType,
        norg2enhet: Enhet? = null,
        fdatoBruker: String? = null,
        sedType: SedType = SedType.P12000,
        block: (Pair<OpprettJournalpostRequest, OppgaveMelding>) -> Unit
    ) {
        val eessisaknr = if (bestemSak?.sakInformasjonListe?.size == 1) bestemSak.sakInformasjonListe.first().sakId else null

        val sed = createSedPensjon(sedType, fnrVoksen, eessisaknr,  gjenlevendeFnr = fnrVoksenSoker, krav = krav, relasjon = relasjonAvod, fdato = fdatoBruker)
        initCommonMocks(sed, alleDocs)

        every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns createBrukerWith(
            fnrVoksen,
            "Voksen ",
            "Forsikret",
            land,
            aktorId = aktor_voksen_1
        )

        if (fnrVoksenSoker != null) {
            every { personService.hentPerson(NorskIdent(fnrVoksenSoker)) } returns createBrukerWith(
                fnrVoksenSoker,
                "Voksen",
                "Gjenlevende",
                land,
                aktorId = aktor_voksen_2
            )
        }
        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

        if (bestemSak != null) {
            every { fagmodulKlient.hentPensjonSaklist(aktor_voksen_2) } returns bestemSak.sakInformasjonListe
        }

        val (journalpost, _) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(sedType, P_BUC_07, FNR_VOKSEN_UNDER_62)

        val meldingSlot = slot<String>()

        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns norg2enhet

        when (hendelseType) {
            SENDT -> sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            else -> Assertions.fail()
        }

        val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)
        Assertions.assertEquals(hendelseType, oppgaveMelding.hendelseType)

        block(Pair(journalpost.captured, oppgaveMelding))

        verify { personService.hentPerson(any()) }
        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        clearAllMocks()
    }
}

