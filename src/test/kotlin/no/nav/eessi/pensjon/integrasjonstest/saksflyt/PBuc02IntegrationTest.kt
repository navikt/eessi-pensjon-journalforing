package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.*
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.journalforing.OpprettJournalpostRequest
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.listeners.pesys.BestemSakResponse
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*

@DisplayName("P_BUC_02 – IntegrationTest")
internal class PBuc02IntegrationTest : JournalforingTestBase() {

    @BeforeEach
    fun setupClass() {
        justRun { gcpStorageService.lagre(any(), any())}
    }

    @Nested
    @DisplayName("Inngående")
    inner class Scenario1Inngaende {
        @Test
        fun `Krav om gjenlevende`() {
            println(UUID.randomUUID().toString().toString())
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2100, SedStatus.RECEIVED)
            )

            testRunnerVoksen(
                FNR_VOKSEN_2,
                FNR_OVER_62,
                krav = KravType.GJENLEV,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = MOTTATT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Utland, Og bruker har løpende alderspensjon så routes oppgave til PENSJON_UTLAND`() {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2100, SedStatus.RECEIVED)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = ALDER, sakStatus = LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN_2,
                FNR_OVER_62,
                bestemsak,
                krav = KravType.GJENLEV,
                land = "SWE",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = MOTTATT,
                norg2enhet = FAMILIE_OG_PENSJONSYTELSER_OSLO
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Sjekker om identifisert person er over 67 aar dersom han er det skal vi ikke håndtere det som en gjennysak og tema pensjon returneres`() {
            val allDocuemtActions = listOf(ForenkletSED("10001212", P2100, SedStatus.RECEIVED))
            every { gcpStorageService.gjennyFinnes(any()) } returns false

            testRunnerVoksen(
                FNR_VOKSEN_2,
                FNR_OVER_62,
                null,
                krav = KravType.GJENLEV,
                land = "SWE",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = MOTTATT,
                norg2enhet = FAMILIE_OG_PENSJONSYTELSER_OSLO
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Sjekker om identifisert person er under 67 aar dersom han er det skal vi sette det til gjennysak og tema omstilling returneres i dette tilfellet`() {
            val allDocuemtActions = listOf(ForenkletSED("10001212", P2100, SedStatus.RECEIVED))
            val gjennysakIBucket = """
            {
              "sakId" : "123456",
              "sakType" : "OMSORG"
            }
        """.trimIndent()

            every { gcpStorageService.hent(any(), any()) } returns gjennysakIBucket
            every { gcpStorageService.gjennyFinnes(any()) } returns true

            testRunnerVoksen(
                FNR_VOKSEN_2,
                FNR_VOKSEN_2,
                null,
                krav = KravType.GJENLEV,
                land = "SWE",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = MOTTATT,
                norg2enhet = FAMILIE_OG_PENSJONSYTELSER_OSLO
            ) {
                Assertions.assertEquals(Tema.OMSTILLING, it.tema)
                Assertions.assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Sjekker om identifisert person er barn i en mottatt P2100 og setter den til gjennysak med tema eybarnep`() {
            val allDocuemtActions = listOf(ForenkletSED("10001212", P2100, SedStatus.RECEIVED))
            val gjennysakIBucket = """
            {
              "sakId" : "123456",
              "sakType" : "BARNEP"
            }
        """.trimIndent()

            every { gcpStorageService.gjennyFinnes(any()) } returns true
            every { gcpStorageService.hentFraGjenny(any()) } returns gjennysakIBucket

            testRunnerVoksen(
                FNR_VOKSEN_2,
                FNR_BARN,
                null,
                krav = KravType.GJENLEV,
                land = "SWE",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = MOTTATT,
                norg2enhet = FAMILIE_OG_PENSJONSYTELSER_OSLO
            ) {
                Assertions.assertEquals(Tema.EYBARNEP, it.tema)
                Assertions.assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Utland, Og bruker har løpende uføretrygd, så routes oppgave til UFORE_UTLAND`() {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2100, SedStatus.RECEIVED)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = UFOREP, sakStatus = LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.GJENLEV,
                land = "SWE",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = MOTTATT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.UFORETRYGD, it.tema)
                Assertions.assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Norge, Og bruker har løpende uføretrygd, så routes oppgave til UFORE_UTLANDSTILSNITT`() {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2100, SedStatus.RECEIVED)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = UFOREP, sakStatus = LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.GJENLEV,
                land = "NOR",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = MOTTATT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.UFORETRYGD, it.tema)
                Assertions.assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }
        }

        @ParameterizedTest
        @EnumSource(
            SakType::class, names = [
                "BARNEP", "GJENLEV"
            ]
        )
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Utland, Og bruker har løpende GjenlevP eller BarneP, Så routes oppgave til NAV Pensjon Utland`(
            saktype: SakType
        ) {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2100, SedStatus.RECEIVED)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = saktype, sakStatus = LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN_2,
                FNR_OVER_62,
                bestemsak,
                krav = KravType.GJENLEV,
                land = "SWE",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = MOTTATT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Disabled
        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Norge og har løpende alderspensjon, Så skal oppgaver fordeles i henhold til NORG2`( ) {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2000, SedStatus.RECEIVED)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = SAK_ID, sakType = ALDER, sakStatus = LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                FNR_OVER_62,
                bestemsak,
                krav = KravType.ALDER,
                land = "NOR",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = MOTTATT,
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Norge, Og bruker har løpende uføretrygd, Så skal oppgaver sendes til PENSJON med utlandstilsnitt`() {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2200, SedStatus.RECEIVED)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = UFOREP, sakStatus = LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.UFOREP,
                land = "NOR",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = MOTTATT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.UFORETRYGD, it.tema)
                Assertions.assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }
        }

        @ParameterizedTest
        @EnumSource(
            SakType::class, names = [
                "BARNEP", "GJENLEV"
            ]
        )
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Norge, og burker har gjenlevendeytelse eller barnepensjon, Så skal oppgaver sendes til NFP_UTLAND_AALESUND`(saktype: SakType) {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2100, SedStatus.RECEIVED)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = saktype, sakStatus = LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_OVER_62,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.UFOREP,
                land = "NOR",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = MOTTATT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.UFORETRYGD, it.tema)
                Assertions.assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }
    }


    @Nested
    @DisplayName("Utgående - Scenario 1")
    inner class Scenario1Utgaende {
        @Test
        fun `Krav om gjenlevende`() {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2100, SedStatus.SENT)
            )

            testRunnerVoksen(
                FNR_VOKSEN_2,
                FNR_OVER_62,
                krav = KravType.GJENLEV,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Utland, Og bruker har løpende alderspensjon så routes oppgave til PENSJON_UTLAND`() {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2100, SedStatus.RECEIVED)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = ALDER, sakStatus = LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN_2,
                FNR_OVER_62,
                bestemsak,
                krav = KravType.GJENLEV,
                land = "SWE",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Utland, Og bruker har løpende uføretrygd, så routes oppgave til UFORE_UTLAND`() {
            every { gcpStorageService.gjennyFinnes("147729") } returns false
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2100, SedStatus.SENT)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = UFOREP, sakStatus = LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.GJENLEV,
                land = "SWE",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.UFORETRYGD, it.tema)
                Assertions.assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @ParameterizedTest
        @EnumSource(
            SakType::class, names = [
                "BARNEP", "GJENLEV"
            ]
        )
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Utland, Og bruker har løpende GjenlevP eller BarneP, Så routes oppgave til NAV Pensjon Utland`(
            saktype: SakType
        ) {
            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2100, SedStatus.SENT)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = saktype, sakStatus = LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN_2,
                FNR_OVER_62,
                bestemsak,
                krav = KravType.GJENLEV,
                land = "SWE",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Norge og har løpende alderspensjon, Så journalføres automatisk`( ) {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2000, SedStatus.SENT)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = SAK_ID, sakType = ALDER, sakStatus = LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN_2,
                FNR_OVER_62,
                bestemsak,
                krav = KravType.ALDER,
                land = "NOR",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT,
                norg2enhet = FAMILIE_OG_PENSJONSYTELSER_OSLO
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(FAMILIE_OG_PENSJONSYTELSER_OSLO, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Norge og har løpende alderspensjon, Så skal oppgaver fordeles i henhold til NORG2`( ) {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2000, SedStatus.SENT)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = ALDER, sakStatus = LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN_2,
                FNR_OVER_62,
                bestemsak,
                krav = KravType.ALDER,
                land = "NOR",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT,
                norg2enhet = FAMILIE_OG_PENSJONSYTELSER_OSLO
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(FAMILIE_OG_PENSJONSYTELSER_OSLO, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Norge, Og bruker har løpende uføretrygd, Så skal oppgaver sendes til 4476 Uføretrygd med utlandstilsnitt`() {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2200, SedStatus.SENT)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = UFOREP, sakStatus = LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                FNR_VOKSEN_2,
                bestemsak,
                krav = KravType.UFOREP,
                land = "NOR",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.UFORETRYGD, it.tema)
                Assertions.assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }
        }

        @ParameterizedTest
        @EnumSource(
            SakType::class, names = [
                "BARNEP", "GJENLEV"
            ]
        )
        fun `Hvis sjekk av adresser i PDL er gjort, Og bruker er registrert med adresse Bosatt Norge, Og bruker har løpende gjenlevendeytelse eller barnepensjon, Så skal oppgaver sendes til 0001 NAV NFP_UTLAND_AALESUND`(saktype: SakType) {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", P2100, SedStatus.SENT)
            )
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(sakId = null, sakType = saktype, sakStatus = LOPENDE)
                )
            )

            testRunnerVoksen(
                FNR_VOKSEN_2,
                FNR_OVER_62,
                bestemsak,
                krav = KravType.UFOREP,
                land = "NOR",
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT,
                norg2enhet = null
            ) {
                Assertions.assertEquals(Tema.PENSJON, it.tema)
                Assertions.assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }
    }

    private fun testRunnerVoksen(
        fnrVoksen: String,
        fnrVoksenSoker: String?,
        bestemSak: BestemSakResponse? = null,
        land: String = "NOR",
        krav: KravType = KravType.GJENLEV,
        alleDocs: List<ForenkletSED>,
        relasjonAvod: RelasjonTilAvdod? = RelasjonTilAvdod.EGET_BARN,
        hendelseType: HendelseType,
        norg2enhet: Enhet? = null,
        sedHendelse: SED? = null,
        block: (OpprettJournalpostRequest) -> Unit
    ) {
        val eessisaknr = if (bestemSak?.sakInformasjonListe?.size == 1) {
            bestemSak.sakInformasjonListe.first().sakId
        } else {
            null
        }

        val sed = sedHendelse ?: createSedPensjon(P2100, fnrVoksen, eessisaknr,  gjenlevendeFnr = fnrVoksenSoker, krav = krav, relasjon = relasjonAvod)
        initCommonMocks(sed, alleDocs)

        every { etterlatteService.hentGjennySak(any()) } returns mockHentGjennySakMedError()
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

        if (bestemSak != null) every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak
        else every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID_2) } returns emptyList()

        val (journalpost, _) = initJournalPostRequestSlot()
        val hendelse = createHendelseJson(P2100, P_BUC_02)
        val meldingSlot = slot<String>()

        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns norg2enhet

        when (hendelseType) {
            SENDT -> sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
        }

        val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)
        Assertions.assertEquals(hendelseType, oppgaveMelding.hendelseType)

        block(journalpost.captured)

        verify { personService.hentPerson(any()) }
        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        clearAllMocks()
    }
}

