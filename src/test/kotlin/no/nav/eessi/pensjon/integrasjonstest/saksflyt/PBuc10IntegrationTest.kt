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
import no.nav.eessi.pensjon.eux.model.sed.KravType.ALDER
import no.nav.eessi.pensjon.eux.model.sed.KravType.ETTERLATTE
import no.nav.eessi.pensjon.eux.model.sed.KravType.UFORE
import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.eux.model.sed.SED

import no.nav.eessi.pensjon.eux.model.sed.SivilstandItem
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.handler.OppgaveType
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.klienter.pesys.BestemSakResponse
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.Enhet.AUTOMATISK_JOURNALFORING
import no.nav.eessi.pensjon.models.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.models.Enhet.NFP_UTLAND_OSLO
import no.nav.eessi.pensjon.models.Enhet.PENSJON_UTLAND
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.HendelseType.MOTTATT
import no.nav.eessi.pensjon.models.HendelseType.SENDT
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("P_BUC_10 – IntegrationTest")
internal class PBuc10IntegrationTest : JournalforingTestBase() {

    /**
     * Flytskjema utgående:
     * https://confluence.adeo.no/pages/viewpage.action?pageId=395744235
     *
     * Flytskjema inngående:
     * https://confluence.adeo.no/pages/viewpage.action?pageId=395744201
     */

    /* ============================
     * UTGÅENDE
     * ============================ */

    @Nested
    @DisplayName("Utgående - Scenario 1")
    inner class Scenario1Utgaende {
        @Test
        fun `Krav om alderspensjon`() {
             val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = Saktype.ALDER, sakStatus = SakStatus.TIL_BEHANDLING)))
             val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))

             testRunner(FNR_VOKSEN_2, bestemsak, alleDocs = allDocuemtActions, hendelseType = SENDT, norg2svar = null) {
                 assertEquals(PENSJON, it.tema)
                 assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
             }

             testRunner(FNR_OVER_60, bestemsak, alleDocs = allDocuemtActions, hendelseType = SENDT, norg2svar = null) {
                 assertEquals(PENSJON, it.tema)
                 assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
             }
        }
    }


    @Nested
    @DisplayName("Utgående - Scenario 2")
    inner class Scenario2Utgaende {

        @Test
        fun `Krav om uføretrygd`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = Saktype.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))

            testRunner(FNR_VOKSEN, bestemsak, krav = UFORE, alleDocs = allDocuemtActions, hendelseType = SENDT, norg2svar = null) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_VOKSEN_2, bestemsak, krav = UFORE, alleDocs = allDocuemtActions, hendelseType = SENDT, norg2svar = null) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Krav om uføretrygd - sakstatus AVSLUTTET - AUTOMATISK_JOURNALFORING`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = Saktype.UFOREP, sakStatus = SakStatus.AVSLUTTET)))
            val allDocuemtActions = listOf(
                    ForenkletSED("10001212", SedType.P15000, SedStatus.SENT)
            )

            testRunner(FNR_VOKSEN, bestemsak, krav = UFORE, alleDocs = allDocuemtActions, hendelseType = SENDT, norg2svar = null) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_VOKSEN_2, bestemsak, krav = UFORE, alleDocs = allDocuemtActions, hendelseType = SENDT, norg2svar = null) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

    }


    @Nested
    @DisplayName("Utgående - Scenario 3")
    inner class Scenario3Utgaende {

        @Test
        fun `Krav om barnepensjon - automatisk`() {
            val fnr = Fodselsnummer.fra("05020876176")
            val validfnr = fnr?.getBirthDate()
            val fnr2 = Fodselsnummer.fra("09035225916")
            println("fnr $fnr, validfnr $validfnr, fnr2: $fnr2")

            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = Saktype.BARNEP, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))

            testRunnerBarn(
                FNR_VOKSEN,
                FNR_BARN,
                bestemsak,
                krav = ETTERLATTE,
                alleDocs = allDocuemtActions,
                sedJson = null,
                hendelseType = SENDT
            ) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
                assertEquals(FNR_BARN, it.bruker?.id!!)
            }

        }

        @Test
        fun `Krav om barnepensjon ingen sak - id og fordeling`() {
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))

            testRunnerBarn(
                FNR_VOKSEN,
                FNR_BARN,
                null,
                krav = ETTERLATTE,
                alleDocs = allDocuemtActions,
                sedJson = null,
                hendelseType = SENDT,
            ) {
                    assertEquals(PENSJON, it.tema)
                    assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                    assertEquals(FNR_BARN, it.bruker?.id!!)
                }
        }

        @Test
        fun `Krav om barnepensjon - barn ukjent ident - id og fordeling`() {
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))

            val sokBarn = createBrukerWith(FNR_BARN, "Barn", "Gjenlev", "SWE", aktorId = AKTOER_ID_2)

            testRunnerBarn(
                FNR_VOKSEN,
                null,
                null,
                krav = ETTERLATTE,
                alleDocs = allDocuemtActions,
                sedJson = null,
                hendelseType = SENDT,
                sokPerson = sokBarn
            ){
                    assertEquals(PENSJON, it.tema)
                    assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                    assertNull(it.bruker)
                }
        }

        @Test
        fun `Krav om barnepensjon - relasjon mangler - saknr funnet i utgående sed og journalføres automatisk `() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = Saktype.BARNEP, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))
            testRunnerBarn(
                FNR_VOKSEN,
                FNR_BARN,
                bestemsak,
                krav = ETTERLATTE,
                alleDocs = allDocuemtActions,
                relasjonAvod = null,
                sedJson = null,
                hendelseType = SENDT
            ) {
                    assertEquals(PENSJON, it.tema)
                    assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
                    assertEquals(FNR_BARN,it.bruker?.id)
                }
        }

        @Test
        fun `Krav om barnepensjon - relasjon mangler - saknr ikke funnet - id og fordeling `() {
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))
            testRunnerBarn(
                FNR_VOKSEN,
                FNR_BARN,
                null,
                krav = ETTERLATTE,
                alleDocs = allDocuemtActions,
                relasjonAvod = null,
                sedJson = null,
                hendelseType = SENDT
            ) {
                    assertEquals(PENSJON, it.tema)
                    assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                    assertEquals(FNR_BARN,it.bruker?.id)
                }
        }

        @Test
        fun `Test med Sed fra Rina BARNEP og bestemsak - automatisk`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = "22919587", sakType = Saktype.BARNEP, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))
            val fnr = Fodselsnummer.fra("05020876176")
            println("fnr: $fnr")

            val valgtbarnfnr = "05020876176"
            testRunnerBarn(
                "13017123321",
                "05020876176",
                bestemsak,
                krav = ETTERLATTE,
                alleDocs = allDocuemtActions,
                sedJson = mockSED(),
                hendelseType = SENDT
            ) {
                    assertEquals(PENSJON, it.tema)
                    assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
                    assertEquals(valgtbarnfnr, it.bruker?.id!!)
                }
        }

    }


    @Nested
    @DisplayName("Utgående - Scenario 4")
    inner class Scenario4Utgaende {

        @Test
        fun `Krav om gjenlevendeytelse - GP eller AP - ALDER - automatisk`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = Saktype.ALDER, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))

            testRunnerVoksen(
                FNR_OVER_60,
                FNR_VOKSEN,
                bestemsak,
                krav = ETTERLATTE,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT
            ) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Krav om gjenlevendeytelse - GP eller AP - GJENLEV - automatisk`() {
            val bestemsak2 = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = Saktype.GJENLEV, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))

            testRunnerVoksen(
                FNR_OVER_60,
                FNR_VOKSEN,
                bestemsak2,
                krav = ETTERLATTE,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.SAMBOER,
                hendelseType = SENDT
            ) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Krav om gjenlevendeytelse - GP eller AP - mangler relasjon - sakid fra sed - automatisk journalføring`() {
            val bestemsak2 = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = Saktype.ALDER, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))

            testRunnerVoksen(FNR_OVER_60, FNR_VOKSEN, bestemsak2, krav = ETTERLATTE, alleDocs = allDocuemtActions, relasjonAvod = null, hendelseType = SENDT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Krav om gjenlevendeytelse - GP eller AP - mangler relasjon - mangler sakid - id og fordeling`() {
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))
            testRunnerVoksen(FNR_OVER_60, FNR_VOKSEN, null, krav = ETTERLATTE, alleDocs = allDocuemtActions, relasjonAvod = null, hendelseType = SENDT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }

    }


    @Nested
    @DisplayName("Utgående - Scenario 5")
    inner class Scenario5Utgaende {

        @Test
        fun `Krav om gjenlevendeytelse - Uføretrygd automatisk`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = Saktype.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)))
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))

            testRunnerVoksen(
                FNR_OVER_60,
                FNR_VOKSEN,
                bestemsak,
                krav = ETTERLATTE,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT
            ) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

        }

    }


    @Nested
    @DisplayName("Utgående - Scenario 6")
    inner class Scenario6Utgaende {

        @Test
        fun `Krav om gjenlevendeytelse - Uføretrygd manuelt - id og fordeling`() {
            val bestemsak = BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = Saktype.UFOREP, sakStatus = SakStatus.AVSLUTTET)))
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))

            testRunnerVoksen(
                FNR_OVER_60,
                FNR_VOKSEN,
                bestemsak,
                krav = ETTERLATTE,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT
            ) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerVoksen(
                FNR_OVER_60,
                FNR_VOKSEN,
                null,
                krav = ETTERLATTE,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT
            ) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                assertEquals(FNR_VOKSEN, it.bruker?.id!!)
            }


        }
    }


    @Nested
    @DisplayName("Utgående - Scenario 7")
    inner class Scenario7Utgaende {

        @Test
        fun `Krav om gjenlevendeytelse - flere sakstyper i retur - sakid finnes i sed - automatisk journalføring`() {
            val bestemsak = BestemSakResponse(null, listOf(
                            SakInformasjon(sakId = SAK_ID, sakType = Saktype.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                            SakInformasjon(sakId = "123456", sakType = Saktype.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)))

            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))

            testRunnerBarn(
                FNR_VOKSEN,
                FNR_BARN,
                bestemsak,
                krav = ETTERLATTE,
                alleDocs = allDocuemtActions,
                hendelseType = SENDT,
            ) {
                    assertEquals(PENSJON, it.tema)
                    assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
                }

            testRunnerVoksen(FNR_OVER_60, FNR_VOKSEN, bestemsak, krav = ETTERLATTE, alleDocs = allDocuemtActions, hendelseType = SENDT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Krav om gjenlevendeytelse - flere sakstyper i retur - ingen saker - id og fordeling`() {
            val bestemsak = null
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))

            testRunnerBarn(
                FNR_VOKSEN,
                FNR_BARN,
                bestemsak,
                krav = ETTERLATTE,
                alleDocs = allDocuemtActions,
                hendelseType = SENDT,
            ) {
                    assertEquals(PENSJON, it.tema)
                    assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                }

            testRunnerVoksen(FNR_OVER_60, FNR_VOKSEN, bestemsak, krav = ETTERLATTE, alleDocs = allDocuemtActions, hendelseType = SENDT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }


    @Nested
    @DisplayName("Utgående - Scenario 8")
    inner class Scenario8Utgaende {

        @Test
        fun `manuell oppgave det mangler er mangelfullt fnr dnr - kun en person - id og fordeling`() {
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P15000, SedStatus.SENT))

            testRunner(null, krav = ALDER, alleDocs = allDocuemtActions, hendelseType = MOTTATT, norg2svar = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }


    @Nested
    @DisplayName("Utgående - Scenario 9")
    inner class Scenario9Utgaende {

        @Test
        fun `mangler som fører til manuell oppgave - etterlatteytelser`() {
            val allDocuemtActions = listOf(
                    ForenkletSED("10001212", SedType.P15000, SedStatus.SENT)
            )

            testRunnerBarn(FNR_VOKSEN, null, krav = ETTERLATTE, alleDocs = allDocuemtActions, hendelseType = MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerBarn(
                FNR_VOKSEN,
                FNR_BARN,
                krav = ETTERLATTE,
                alleDocs = allDocuemtActions,
                relasjonAvod = null,
                hendelseType = SENDT,
            ) {
                    assertEquals(PENSJON, it.tema)
                    assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                }

            testRunnerVoksen(FNR_VOKSEN, FNR_VOKSEN_2, krav = ETTERLATTE, alleDocs = allDocuemtActions, relasjonAvod = null, hendelseType = SENDT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerVoksen(FNR_VOKSEN, null, krav = ETTERLATTE, alleDocs = allDocuemtActions, relasjonAvod = RelasjonTilAvdod.EKTEFELLE, hendelseType = SENDT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }



    /* ============================
     * INNGÅENDE
     * ============================ */


    @Nested
    @DisplayName("Inngående - Scenario 1")
    inner class Scenario1Inngaende {
        @Test
        fun `Krav om alderspensjon`() {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P15000, SedStatus.RECEIVED)
            )

            testRunner(FNR_VOKSEN_2, null, land = "SWE", alleDocs = allDocuemtActions, hendelseType = MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_OVER_60, null, alleDocs = allDocuemtActions, hendelseType = MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(Enhet.NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_OVER_60, null, alleDocs = allDocuemtActions, hendelseType = MOTTATT, norg2svar = NFP_UTLAND_OSLO) {
                assertEquals(PENSJON, it.tema)
                assertEquals(Enhet.NFP_UTLAND_OSLO, it.journalfoerendeEnhet)
            }

        }

//        @Test
//        fun `Krav om etterlatteytelser`() {
//
//            val allDocuemtActions = listOf(
//                ForenkletSED("10001212", SedType.P15000, SedStatus.RECEIVED)
//            )

//            testRunnerBarn(FNR_VOKSEN_2, null, alleDocs = allDocuemtActions, land = "SWE", krav = ETTERLATTE, hendelseType = MOTTATT) {
//                assertEquals(PENSJON, it.tema)
//                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
//            }
//
//            testRunnerBarn(FNR_OVER_60, FNR_BARN, alleDocs = allDocuemtActions, krav = ETTERLATTE, hendelseType = MOTTATT) {
//                assertEquals(PENSJON, it.tema)
//                assertEquals(Enhet.NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
//            }

//            testRunnerBarnmedSokPerson(FNR_OVER_60, FNR_BARN, alleDocs = allDocuemtActions, krav = ETTERLATTE, hendelseType = MOTTATT) {
//                assertEquals(PENSJON, it.tema)
//                assertEquals(Enhet.NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
//            }

//            testRunnerBarn(FNR_OVER_60, FNR_BARN, alleDocs = allDocuemtActions, krav = ETTERLATTE, land = "SWE", hendelseType = MOTTATT) {
//                assertEquals(PENSJON, it.tema)
//                assertEquals(Enhet.PENSJON_UTLAND, it.journalfoerendeEnhet)
//            }
//
//        }

        @Test
        fun `Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav ALDER skal routes til NFP_UTLAND_AALESUND 4862`() {
            val sed15000sent = SED.generateSedToClass<P15000>( createSedPensjon(SedType.P15000, FNR_OVER_60, krav = ALDER))
            val sedP5000mottatt = SED.generateSedToClass<P5000>( createSedPensjon(SedType.P5000, FNR_OVER_60, krav = ALDER))

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P15000, SedStatus.SENT),
                ForenkletSED("30002", SedType.P5000, SedStatus.RECEIVED),
                ForenkletSED("654654", SedType.P8000, SedStatus.EMPTY)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(BucType.P_BUC_10, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP5000mottatt.toJson() andThen sed15000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.hentPerson(NorskIdent(FNR_OVER_60)) } returns createBrukerWith(FNR_OVER_60, "Fornavn", "Pensjonisten", "NOR")
            every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P5000, BucType.P_BUC_10)

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(Enhet.NFP_UTLAND_AALESUND, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P5000", oppgaveMelding.sedType?.name)

            assertEquals("INNGAAENDE", request.journalpostType.name)
            assertEquals(PENSJON, request.tema)
            assertEquals(Enhet.NFP_UTLAND_AALESUND, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        }

        @Test
        fun `Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav ALDER bosatt utland skal routes til PENSJON_UTLAND 0001`() {
            val sed15000sent = SED.generateSedToClass<P15000>(createSedPensjon(SedType.P15000, FNR_OVER_60, krav = ALDER))
            val sedP5000mottatt = SED.generateSedToClass<P5000>(createSedPensjon(SedType.P5000, FNR_OVER_60))

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P15000, SedStatus.SENT),
                ForenkletSED("30002", SedType.P5000, SedStatus.RECEIVED)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(BucType.P_BUC_10, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP5000mottatt.toJson() andThen sed15000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.hentPerson(NorskIdent(FNR_OVER_60)) } returns createBrukerWith(FNR_OVER_60, "Fornavn", "Pensjonisten", "SWE")
            every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P5000, BucType.P_BUC_10)

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(PENSJON_UTLAND, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P5000", oppgaveMelding.sedType?.name)

            assertEquals("INNGAAENDE", request.journalpostType.name)
            assertEquals(PENSJON, request.tema)
            assertEquals(PENSJON_UTLAND, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav UFOEREP skal routes til UFORE_UTLANDSTILSNITT 4476`() {
            val sed15000sent =  SED.generateSedToClass<P15000>(createSedPensjon(SedType.P15000, FNR_VOKSEN, krav = UFORE))
            val sedP5000mottatt = SED.generateSedToClass<P5000>(createSedPensjon(SedType.P5000, FNR_VOKSEN))

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P15000, SedStatus.SENT),
                ForenkletSED("30002", SedType.P5000, SedStatus.RECEIVED)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(BucType.P_BUC_10, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP5000mottatt.toJson() andThen sed15000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.hentPerson(NorskIdent(FNR_VOKSEN)) } returns createBrukerWith(FNR_VOKSEN, "Fornavn", "Pensjonisten", "NOR")
            every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P5000, BucType.P_BUC_10)

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(Enhet.UFORE_UTLANDSTILSNITT, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P5000", oppgaveMelding.sedType?.name)

            assertEquals("INNGAAENDE", request.journalpostType.name)
            assertEquals(UFORETRYGD, request.tema)
            assertEquals(Enhet.UFORE_UTLANDSTILSNITT, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav UFOEREP bosatt utland skal routes til UFORE_UTLAND 4475`() {
            val sed15000sent = SED.generateSedToClass<P15000>(createSedPensjon(SedType.P15000, FNR_VOKSEN, krav = UFORE))
            val sedP5000mottatt = SED.generateSedToClass<P5000>(createSedPensjon(SedType.P5000, FNR_VOKSEN))

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P15000, SedStatus.SENT),
                ForenkletSED("30002", SedType.P5000, SedStatus.RECEIVED)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(BucType.P_BUC_10, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP5000mottatt.toJson() andThen sed15000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.hentPerson(NorskIdent(FNR_VOKSEN)) } returns createBrukerWith(FNR_VOKSEN, "Fornavn", "Pensjonisten", "SWE")
            every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P5000, BucType.P_BUC_10)

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(Enhet.UFORE_UTLAND, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P5000", oppgaveMelding.sedType?.name)

            assertEquals("INNGAAENDE", request.journalpostType.name)
            assertEquals(UFORETRYGD, request.tema)
            assertEquals(Enhet.UFORE_UTLAND, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Innkommende P15000 gjenlevende mangler søker`() {
            val sedP15000 = SED.generateSedToClass<P15000>(createSedPensjon(SedType.P15000, "12321", gjenlevendeFnr = null, krav = ETTERLATTE))

            val alleDocumenter = listOf(ForenkletSED("30002", SedType.P15000, SedStatus.RECEIVED))

            every { euxKlient.hentBuc(any()) } returns bucFrom(BucType.P_BUC_10, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP15000.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P15000, BucType.P_BUC_10)

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P15000", oppgaveMelding.sedType?.name)

            assertEquals("INNGAAENDE", request.journalpostType.name)
            assertEquals(PENSJON, request.tema)
            assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Innkommende P15000 gjenlevende og forsikret mangler fnr prøver søk persom på gjenlevende`() {

            val sokPerson = setOf(IdentInformasjon(FNR_VOKSEN, IdentGruppe.FOLKEREGISTERIDENT)) // korrekt return sokPerson

            val land = "SWE"
            val fnrSokVoken = null
            val mockGjenlevende = createBrukerWith(FNR_VOKSEN,  "Voksen ", "Gjenlevnde", land, aktorId = AKTOER_ID)

            val sedP15000 = SED.generateSedToClass<P15000>(createSedPensjon(SedType.P15000, "12321", gjenlevendeFnr = fnrSokVoken, sivilstand = SivilstandItem("01-30-1980", "01"),  krav = ETTERLATTE, relasjon = RelasjonTilAvdod.EKTEFELLE ,  pdlPerson = mockGjenlevende, fdatoAnnenPerson = Fodselsnummer.fra(FNR_VOKSEN)?.getBirthDateAsIso()))

            val alleDocumenter = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P15000, SedStatus.RECEIVED))

            every { personService.sokPerson(any()) } returns sokPerson
            every { personService.hentPerson(NorskIdent(FNR_VOKSEN)) } returns mockGjenlevende
            every { euxKlient.hentBuc(any()) } returns bucFrom(BucType.P_BUC_10, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP15000.toJsonSkipEmpty()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P15000, BucType.P_BUC_10)

            //kjør
            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(PENSJON_UTLAND, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P15000", oppgaveMelding.sedType?.name)

            assertEquals("INNGAAENDE", request.journalpostType.name)
            assertEquals(PENSJON, request.tema)
            assertEquals(PENSJON_UTLAND, request.journalfoerendeEnhet)

            verify(exactly = 1) { personService.sokPerson(any()) }
            verify(exactly = 1) { personService.hentPerson(any<Ident<*>>()) }

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

    }

    @Nested
    @DisplayName("Inngående - Scenario 2")
    inner class Scenario2Inngaende {
        @Test
        fun `Krav om uføretrygd`() {

            val allDocuemtActions = listOf(
                ForenkletSED("10001212", SedType.P15000, SedStatus.RECEIVED)
            )

            testRunner(
                FNR_VOKSEN,
                bestemSak = null,
                land = "SWE",
                krav = UFORE,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                norg2svar = null
            ) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(Enhet.UFORE_UTLAND, it.journalfoerendeEnhet)
            }
        }
    }

    @Nested
    @DisplayName("Inngående - Scenario 4")
    inner class Scenario4Inngaende {
        @Test
        fun `Flere sed i buc, mottatt en P15000 med ukjent gjenlevende relasjon, krav GJENLEV sender en P5000 med korrekt gjenlevende denne skal journalføres automatisk`() {
            val sed15000mottatt = SED.generateSedToClass<P15000>(createSedPensjon(SedType.P15000, FNR_OVER_60, gjenlevendeFnr = "", krav = ETTERLATTE, relasjon = RelasjonTilAvdod.EKTEFELLE))
            val sedP5000sendt = SED.generateSedToClass<P5000>(createSedPensjon(SedType.P5000, FNR_OVER_60, eessiSaknr = SAK_ID, gjenlevendeFnr = FNR_VOKSEN_2))

            val saker = listOf(
                SakInformasjon(sakId = SAK_ID, sakType = Saktype.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                SakInformasjon(sakId = "34234123", sakType = Saktype.UFOREP, sakStatus = SakStatus.AVSLUTTET)
            )

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P15000, SedStatus.RECEIVED),
                ForenkletSED("30002", SedType.P5000, SedStatus.SENT)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(BucType.P_BUC_10, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP5000sendt.toJson() andThen sed15000mottatt.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.hentPerson(NorskIdent(FNR_OVER_60)) } returns createBrukerWith(FNR_OVER_60, "Avdød", "død", "SWE")
            every { personService.hentPerson(NorskIdent(FNR_VOKSEN_2)) } returns createBrukerWith(FNR_VOKSEN_2, "Gjenlevende", "Lever", "SWE", aktorId = AKTOER_ID_2)
            every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID_2) } returns saker

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, _) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P5000, BucType.P_BUC_10)

            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            val request = journalpost.captured

            assertEquals("UTGAAENDE", request.journalpostType.name)
            assertEquals(PENSJON, request.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }
    }


    private fun mockSED() : String {
        return """
            {"pensjon":{"gjenlevende":{"person":{"pin":[{"identifikator":"05020876176","land":"NO"}],"foedselsdato":"2008-02-05","etternavn":"TRANFLASKE","fornavn":"TYKKMAGET","kjoenn":"M","relasjontilavdod":{"relasjon":"06"}}}},"sedGVer":"4","nav":{"bruker":{"adresse":{"land":"NO","gate":"BEISKKÁNGEAIDNU 7","postnummer":"8803","by":"SANDNESSJØEN"},"person":{"fornavn":"BLÅ","pin":[{"land":"NO","institusjonsid":"NO:NAVAT07","institusjonsnavn":"NAV ACCEPTANCE TEST 07","identifikator":"13017123321"}],"kjoenn":"M","etternavn":"SKILPADDE","foedselsdato":"1971-01-13","statsborgerskap":[{"land":"NO"}]}},"eessisak":[{"institusjonsnavn":"NAV ACCEPTANCE TEST 07","saksnummer":"22919587","institusjonsid":"NO:NAVAT07","land":"NO"}],"krav":{"dato":"2020-10-01","type":"02"}},"sedVer":"2","sed":"P15000"}
        """.trimIndent()
    }

    private fun testRunnerBarn(
        fnrVoksen: String,
        fnrBarn: String?,
        bestemSak: BestemSakResponse? = null,
        sakId: String? = SAK_ID,
        land: String = "NOR",
        krav: KravType = ALDER,
        alleDocs: List<ForenkletSED>,
        relasjonAvod: RelasjonTilAvdod? = RelasjonTilAvdod.EGET_BARN,
        sedJson: String? = null,
        hendelseType: HendelseType,
        sokPerson: Person? = null,
        block: (OpprettJournalpostRequest) -> Unit
    ) {
        val sed = sedJson?.let { mapJsonToAny<P15000>(it) }
                ?: SED.generateSedToClass(createSedPensjon(SedType.P15000, fnrVoksen, eessiSaknr = sakId, gjenlevendeFnr = fnrBarn, krav = krav, pdlPerson = sokPerson , relasjon = relasjonAvod))

        initCommonMocks(sed, alleDocs)

        every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns createBrukerWith(fnrVoksen, "Mamma forsørger", "Etternavn", land, aktorId = AKTOER_ID)

        if (fnrBarn != null) {
            every { personService.hentPerson(NorskIdent(fnrBarn)) } returns createBrukerWith(fnrBarn, "Barn", "Diskret", land, aktorId = AKTOER_ID_2)
        }
        if (sokPerson != null) {
            every { personService.sokPerson(any()) } returns setOf(
                IdentInformasjon(
                    FNR_OVER_60,
                    IdentGruppe.FOLKEREGISTERIDENT
                ), IdentInformasjon("BLÆ", IdentGruppe.AKTORID)
            )
            every { personService.hentPerson(any<Ident<*>>()) } returns sokPerson

        }

        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

        if (bestemSak != null) {
            every { fagmodulKlient.hentPensjonSaklist(any()) } returns bestemSak.sakInformasjonListe
        }

        val (journalpost, _) = initJournalPostRequestSlot()

        val forsikretfnr = if (krav == ETTERLATTE) fnrVoksen else null
        val hendelse = createHendelseJson(SedType.P15000, BucType.P_BUC_10, forsikretfnr)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

        when (hendelseType) {
            SENDT -> sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            else -> fail()
        }


        val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)
        assertEquals(hendelseType, oppgaveMelding.hendelseType)

        block(journalpost.captured)

//        if (sokPerson != null || fnrBarn != null) {
//            verify { personService.hentPerson(any<Ident<*>>()) }
//        }

        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        clearAllMocks()
    }

    protected fun testRunnerBarnmedSokPerson(fnrVoksen: String,
                               fnrBarn: String,
                               benyttSokPerson: Boolean = true,
                               bestemSak: BestemSakResponse? = null,
                               sakId: String? = SAK_ID,
                               land: String = "NOR",
                               krav: KravType = ALDER,
                               alleDocs: List<ForenkletSED>,
                               relasjonAvod: RelasjonTilAvdod? = RelasjonTilAvdod.EGET_BARN,
                               sedJson: String? = null,
                               hendelseType: HendelseType,
                               block: (OpprettJournalpostRequest) -> Unit
    ) {
        val mockBarn = createBrukerWith(fnrBarn, "Barn", "Diskret", land, aktorId = AKTOER_ID_2)

        val fnrBarnsok = if (benyttSokPerson) null else fnrBarn

        val sed = sedJson?.let { mapJsonToAny(it) }
            ?: createSedPensjon(
                SedType.P15000, fnrVoksen, eessiSaknr = sakId, gjenlevendeFnr = fnrBarnsok, krav = krav, relasjon = relasjonAvod, pdlPerson = mockBarn
            )

        initCommonMocks(sed, alleDocs)

        if (benyttSokPerson) {
            every { personService.sokPerson(any()) } returns setOf(IdentInformasjon(fnrBarn, IdentGruppe.FOLKEREGISTERIDENT), IdentInformasjon("BLÆ", IdentGruppe.AKTORID))
        }

        every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns createBrukerWith(fnrVoksen, "Mamma forsørger", "Etternavn", land, aktorId = AKTOER_ID)
        every { personService.hentPerson(NorskIdent(fnrBarn)) } returns mockBarn
        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

        val (journalpost, _) = initJournalPostRequestSlot()

        val forsikretfnr = if (krav == ETTERLATTE) fnrVoksen else null
        val hendelse = createHendelseJson(SedType.P15000, BucType.P_BUC_10, forsikretfnr)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

        when (hendelseType) {
            SENDT -> sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            else -> fail()
        }


        val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)
        assertEquals(hendelseType, oppgaveMelding.hendelseType)

        block(journalpost.captured)

        verify { personService.hentPerson(any<Ident<*>>()) }
        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        clearAllMocks()
    }

    private fun testRunnerVoksen(
        fnrVoksen: String,
        fnrVoksenSoker: String?,
        bestemSak: BestemSakResponse? = null,
        sakId: String? = SAK_ID,
        land: String = "NOR",
        krav: KravType = ALDER,
        alleDocs: List<ForenkletSED>,
        relasjonAvod: RelasjonTilAvdod? = RelasjonTilAvdod.EGET_BARN,
        hendelseType: HendelseType,
        nor2enhet: Enhet? = null,
        block: (OpprettJournalpostRequest) -> Unit
    ) {
        val sed = SED.generateSedToClass<P15000>(createSedPensjon(SedType.P15000, fnrVoksen, eessiSaknr = sakId, gjenlevendeFnr = fnrVoksenSoker, krav = krav, relasjon = relasjonAvod))
        initCommonMocks(sed, alleDocs)


        every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns createBrukerWith(fnrVoksen, "Voksen ", "Forsikret", land, aktorId = AKTOER_ID)

        if (fnrVoksenSoker != null) {
            every { personService.hentPerson(NorskIdent(fnrVoksenSoker)) } returns createBrukerWith(fnrVoksenSoker, "Voksen", "Gjenlevende", land, aktorId = AKTOER_ID_2)
        }
        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak
        if (bestemSak != null) {
            every { fagmodulKlient.hentPensjonSaklist(any()) } returns bestemSak.sakInformasjonListe
        }

        val (journalpost, _) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P15000, BucType.P_BUC_10)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns nor2enhet

        when (hendelseType) {
            SENDT -> sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            else -> fail()
        }

        val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)
        assertEquals(hendelseType, oppgaveMelding.hendelseType)

        block(journalpost.captured)

        if (fnrVoksenSoker != null) {
            verify { personService.hentPerson(any<Ident<*>>()) }
        }
        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        clearAllMocks()
    }

    private fun testRunner(
        fnr1: String?,
        bestemSak: BestemSakResponse? = null,
        sakId: String? = SAK_ID,
        land: String = "NOR",
        krav: KravType = ALDER,
        alleDocs: List<ForenkletSED>,
        hendelseType: HendelseType,
        norg2svar: Enhet? = null,
        block: (OpprettJournalpostRequest) -> Unit
    ) {

        val sed = SED.generateSedToClass<P15000>(createSedPensjon(SedType.P15000, fnr1, eessiSaknr = sakId, krav = krav))
        initCommonMocks(sed, alleDocs)

        if (fnr1 != null) {
            every { personService.hentPerson(NorskIdent(fnr1)) } returns createBrukerWith(fnr1, "Fornavn", "Etternavn", land, aktorId = AKTOER_ID)
            every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak
            if (bestemSak != null) {
                every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID) } returns bestemSak.sakInformasjonListe
            }
        }

        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P15000, BucType.P_BUC_10)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns norg2svar

        when (hendelseType) {
            SENDT -> sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            else -> fail()
        }

        block(journalpost.captured)

        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        if (bestemSak == null)
            verify(exactly = 0) { bestemSakKlient.kallBestemSak(any()) }
        else
            verify(exactly = 1) { bestemSakKlient.kallBestemSak(any()) }

        clearAllMocks()
    }
}
