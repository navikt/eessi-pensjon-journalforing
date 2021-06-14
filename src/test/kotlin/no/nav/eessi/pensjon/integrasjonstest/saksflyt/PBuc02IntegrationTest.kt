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
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@DisplayName("P_BUC_02 – IntegrationTest")
internal class PBuc02IntegrationTest : JournalforingTestBase() {

    @Nested
    @DisplayName("Inngående")
    inner class Scenario1Inngaende {
        @Test
        fun `Krav om gjenlevende`() {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2100, SedStatus.RECEIVED)
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                krav = KravType.ETTERLATTE,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.MOTTATT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Utland, Og bruker har løpende alderspensjon så routes oppgave til PENSJON_UTLAND`() {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2100, SedStatus.RECEIVED)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = Saktype.ALDER, sakStatus = SakStatus.LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.ETTERLATTE,
                land = "SWE",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.MOTTATT,
                norg2enhet = Enhet.NFP_UTLAND_OSLO
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Utland, Og bruker har løpende uføretrygd, så routes oppgave til UFORE_UTLAND`() {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2100, SedStatus.RECEIVED)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = Saktype.UFOREP, sakStatus = SakStatus.LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.ETTERLATTE,
                land = "SWE",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.MOTTATT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.UFORETRYGD, it.tema)
                Assertions.assertEquals(Enhet.UFORE_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @ParameterizedTest
        @EnumSource(
            Saktype::class, names = [
                "BARNEP", "GJENLEV"
            ]
        )
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Utland, Og bruker har løpende GjenlevP eller BarneP, Så routes oppgave til NAV Pensjon Utland`(
            saktype: Saktype
        ) {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2100, SedStatus.RECEIVED)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = saktype, sakStatus = SakStatus.LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                bestemsak,
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

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Norge og har løpende alderspensjon, Så skal oppgaver fordeles i henhold til NORG2`( ) {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2000, SedStatus.RECEIVED)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.ALDER, sakStatus = SakStatus.LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.ALDER,
                land = "NOR",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.MOTTATT,
                norg2enhet = Enhet.NFP_UTLAND_OSLO
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.NFP_UTLAND_OSLO, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Norge, Og bruker har løpende uføretrygd, Så skal oppgaver sendes til 4476 Uføretrygd med utlandstilsnitt`() {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2200, SedStatus.RECEIVED)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = Saktype.UFOREP, sakStatus = SakStatus.LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.UFORE,
                land = "NOR",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.MOTTATT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.UFORETRYGD, it.tema)
                Assertions.assertEquals(Enhet.UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }
        }

        @ParameterizedTest
        @EnumSource(
            Saktype::class, names = [
                "BARNEP", "GJENLEV"
            ]
        )
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Norge, Og bruker har løpende gjenlevendeytelse eller barnepensjon, Så skal oppgaver sendes til 0001 NAV Pensjon Utland`(saktype: Saktype) {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2100, SedStatus.RECEIVED)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = saktype, sakStatus = SakStatus.LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.UFORE,
                land = "NOR",
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
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Utland, Og bruker har løpende alderspensjon så routes oppgave til PENSJON_UTLAND`() {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2100, SedStatus.RECEIVED)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = Saktype.ALDER, sakStatus = SakStatus.LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.ETTERLATTE,
                land = "SWE",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.SENDT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Utland, Og bruker har løpende uføretrygd, så routes oppgave til UFORE_UTLAND`() {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2100, SedStatus.SENT)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = Saktype.UFOREP, sakStatus = SakStatus.LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.ETTERLATTE,
                land = "SWE",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.SENDT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.UFORETRYGD, it.tema)
                Assertions.assertEquals(Enhet.UFORE_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @ParameterizedTest
        @EnumSource(
            Saktype::class, names = [
                "BARNEP", "GJENLEV"
            ]
        )
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Utland, Og bruker har løpende GjenlevP eller BarneP, Så routes oppgave til NAV Pensjon Utland`(
            saktype: Saktype
        ) {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2100, SedStatus.SENT)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = saktype, sakStatus = SakStatus.LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.ETTERLATTE,
                land = "SWE",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.SENDT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Norge og har løpende alderspensjon, Så journalføres automatisk`( ) {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2000, SedStatus.SENT)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.ALDER, sakStatus = SakStatus.LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.ALDER,
                land = "NOR",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.SENDT,
                norg2enhet = Enhet.NFP_UTLAND_OSLO
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Norge og har løpende alderspensjon, Så skal oppgaver fordeles i henhold til NORG2`( ) {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2000, SedStatus.SENT)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = Saktype.ALDER, sakStatus = SakStatus.LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.ALDER,
                land = "NOR",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.SENDT,
                norg2enhet = Enhet.NFP_UTLAND_OSLO
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.NFP_UTLAND_OSLO, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Norge, Og bruker har løpende uføretrygd, Så skal oppgaver sendes til 4476 Uføretrygd med utlandstilsnitt`() {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2200, SedStatus.SENT)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = Saktype.UFOREP, sakStatus = SakStatus.LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.UFORE,
                land = "NOR",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.SENDT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.UFORETRYGD, it.tema)
                Assertions.assertEquals(Enhet.UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }
        }

        @ParameterizedTest
        @EnumSource(
            Saktype::class, names = [
                "BARNEP", "GJENLEV"
            ]
        )
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Norge, Og bruker har løpende gjenlevendeytelse eller barnepensjon, Så skal oppgaver sendes til 0001 NAV Pensjon Utland`(saktype: Saktype) {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P2100, SedStatus.SENT)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = saktype, sakStatus = SakStatus.LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.UFORE,
                land = "NOR",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = HendelseType.SENDT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(Enhet.PENSJON_UTLAND, it.journalfoerendeEnhet)
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
        block: (OpprettJournalpostRequest) -> Unit
    ) {
        val sed = createSedPensjon(
            SedType.P2100,
            fnrVoksen,
            gjenlevendeFnr = fnrVoksenSoker,
            krav = krav,
            relasjon = relasjonAvod
        )
        initCommonMocks(sed, alleDocs)

        every { euxService.hentBuc (any()) } returns mockk(relaxed = true)

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

        val (journalpost, _) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P2100, BucType.P_BUC_02)

        val meldingSlot = slot<String>()

        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns norg2enhet

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

