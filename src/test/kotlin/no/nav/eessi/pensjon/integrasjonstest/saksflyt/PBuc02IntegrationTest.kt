package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.eux.model.sed.SedType
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
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("P_BUC_02 – IntegrationTest")
internal class PBuc02IntegrationTest: JournalforingTestBase() {

    @Nested
    @DisplayName("Utgående - Scenario 1")
    inner class Scenario1Utgaende {
        @Test
        fun `Krav om gjenlevende`() {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2100, SedStatus.SENT)
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                krav = KravType.ETTERLATTE,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.SENDT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Utland, Og bruker har løpende alderspensjon Så skal oppgaver sendes til 0001 NAV Pensjon Utland`() {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2100, SedStatus.SENT)
            )
            val bestemsak = BestemSakResponse(null, listOf(
                SakInformasjon(sakId = SAK_ID, sakType = Saktype.ALDER, sakStatus = SakStatus.LOPENDE)
            ))

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                bestemsak,
                SAK_ID,
                krav = KravType.ETTERLATTE,
                land = "SWE",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.MOTTATT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }


    }

/*
    Hvis sjekk av adresser i PDL er gjort,
    Og bruker er registrert med adresse Bosatt Utland,
    Og SED er del av P_BUC_02,
    Og bruker har løpende alderspensjon,  <-- bestemsak-- <-- pesys  --- saktype, sakstatus
    Så skal oppgaver sendes til 0001 NAV Pensjon Utland.



    Hvis sjekk av adresser i PDL er gjort,
    Og bruker er registrert med adresse Bosatt Utland,
    Og SED er del av P_BUC_02,
    Og bruker har løpende uføretrygd,
    Så skal oppgaver sendes til 4475 Uføretrygd Bosatt Utland.



    Hvis sjekk av adresser i PDL er gjort,
    Og bruker er registrert med adresse Bosatt Utland,
    Og SED er del av P_BUC_02,
    Og bruker har løpende gjenlevendepensjon eller barnepensjon,
    Så skal oppgaver sendes til 0001 NAV Pensjon Utland.


    Hvis sjekk av adresser i PDL er gjort,
    Og bruker er registrert med adresse Bosatt Norge,
    Og SED er del av P_BUC_02,
    Og bruker har løpende alderspensjon,
    Så skal oppgaver fordeles i henhold til NORG2.


    Hvis sjekk av adresser i PDL er gjort,
    Og bruker er registrert med adresse Bosatt Norge,
    Og SED er del av P_BUC_02,
    Og bruker har løpende uføretrygd,
    Så skal oppgaver sendes til 4476 4476 Uføretrygd med utlandstilsnitt.


    Hvis sjekk av adresser i PDL er gjort,
    Og bruker er registrert med adresse Bosatt Norge,
    Og SED er del av P_BUC_02,
    Og bruker har løpende gjenlevendeytelse eller barnepensjon,
    Så skal oppgaver sendes til 0001 NAV Pensjon Utland.

*/



    private fun testRunnerVoksen(
        fnrVoksen: String,
        fnrVoksenSoker: String?,
        bestemSak: BestemSakResponse? = null,
        sakId: String? = SAK_ID,
        land: String = "NOR",
        krav: KravType = KravType.ETTERLATTE,
        alleDocs: List<ForenkletSED>,
        relasjonAvod: RelasjonTilAvdod? = RelasjonTilAvdod.EGET_BARN,
        hendelseType: HendelseType,
        norg2enhet: Enhet? = null,
        block: (OpprettJournalpostRequest) -> Unit
    ) {
        val sed = createSedPensjon(SedType.P2100, fnrVoksen, eessiSaknr = sakId, gjenlevendeFnr = fnrVoksenSoker, krav = krav, relasjon = relasjonAvod)
        initCommonMocks(sed, alleDocs)

        every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns createBrukerWith(fnrVoksen, "Voksen ", "Forsikret", land, aktorId = AKTOER_ID)

        if (fnrVoksenSoker != null) {
            every { personService.hentPerson(NorskIdent(fnrVoksenSoker)) } returns createBrukerWith(fnrVoksenSoker, "Voksen", "Gjenlevende", land, aktorId = AKTOER_ID_2)
        }
        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

        val (journalpost, _) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P2100, BucType.P_BUC_02)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns norg2enhet

        println("SED: ${sed.toJson()}")
        when (hendelseType) {
            HendelseType.SENDT -> listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            HendelseType.MOTTATT -> listener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            else -> Assertions.fail()
        }

        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())
        Assertions.assertEquals(hendelseType, oppgaveMelding.hendelseType)

        block(journalpost.captured)

        verify(exactly = 1) { euxService.hentBucDokumenter(any()) }
        verify { personService.hentPerson(any<Ident<*>>()) }
        verify(exactly = 1) { euxService.hentSed(any(), any()) }

        clearAllMocks()
    }
}

