package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.*
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_10
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.AVSLUTTET
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.TIL_BEHANDLING
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.eux.model.sed.KravType.*
import no.nav.eessi.pensjon.journalforing.OpprettJournalpostRequest
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveType
import no.nav.eessi.pensjon.listeners.pesys.BestemSakResponse
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.junit.jupiter.api.Assertions.assertEquals
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

    val allDocuemtActions = forenkletSEDs()
    
    /* ============================
     * UTGÅENDE
     * ============================ */


    @Nested
    @DisplayName("Utgående - Scenario 1")
    inner class Scenario1Utgaende {
        @Test
        fun `Krav om alderspensjon`() {
             val bestemsak = bestemSakResponse(sakStatus = TIL_BEHANDLING)

             testRunner(FNR_OVER_62, bestemsak, alleDocs = allDocuemtActions, hendelseType = SENDT, norg2svar = null) {
                 assertEquals(PENSJON, it.tema)
                 assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
             }

             testRunner(FNR_OVER_62, bestemsak, alleDocs = allDocuemtActions, hendelseType = SENDT, norg2svar = null) {
                 assertEquals(PENSJON, it.tema)
                 assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
             }
        }
    }

    @Nested
    @DisplayName("Utgående - Scenario 2")
    inner class Scenario2Utgaende {

        @Test
        fun `Krav om uføretrygd`() {
            val bestemsak = bestemSakResponse(SakType.UFOREP,TIL_BEHANDLING )

            testRunner(FNR_VOKSEN_UNDER_62, bestemsak, krav = UFOREP, alleDocs = allDocuemtActions, hendelseType = SENDT, norg2svar = null) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }

            testRunner(FNR_VOKSEN_2, bestemsak, krav = UFOREP, alleDocs = allDocuemtActions, hendelseType = SENDT, norg2svar = null) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }

            testRunner(FNR_VOKSEN_2, bestemsak, alleDocs = allDocuemtActions, hendelseType = SENDT, norg2svar = null) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Krav om uføretrygd - sakstatus AVSLUTTET - ID_OG_FORDELING`() {
            val bestemsak = bestemSakResponse(SakType.UFOREP, AVSLUTTET)

            testRunner(FNR_VOKSEN_UNDER_62, bestemsak, krav = UFOREP, alleDocs = allDocuemtActions, hendelseType = SENDT, norg2svar = null) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }

            testRunner(FNR_VOKSEN_2, bestemsak, krav = UFOREP, alleDocs = allDocuemtActions, hendelseType = SENDT, norg2svar = null) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }
        }

    }

    @Nested
    @DisplayName("Utgående - Scenario 3")
    inner class Scenario3Utgaende {

        @Test
        fun `Krav om barnepensjon - automatisk`() {
            val bestemsak = bestemSakResponse(SakType.BARNEP, TIL_BEHANDLING)

            testRunnerBarn(
                FNR_VOKSEN_UNDER_62,
                FNR_BARN,
                bestemsak,
                krav = GJENLEV,
                alleDocs = allDocuemtActions,
                sedJson = null,
                hendelseType = SENDT
            ) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
                assertEquals(FNR_BARN, it.bruker?.id!!)
            }

        }

        @Test
        fun `Krav om barnepensjon ingen sak - sak sendes til NFP_UTLAND_AALESUND`() {

            testRunnerBarn(
                FNR_VOKSEN_UNDER_62,
                FNR_BARN,
                null,
                krav = GJENLEV,
                alleDocs = allDocuemtActions,
                sedJson = null,
                hendelseType = SENDT,
            ) {
                    assertEquals(PENSJON, it.tema)
                    assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
                    assertEquals(FNR_BARN, it.bruker?.id!!)
                }
        }

        @Test
        fun `Krav om barnepensjon - barn ukjent ident - id og fordeling`() {
            val sokBarn = createBrukerWith(FNR_BARN, "Barn", "Gjenlev", "SWE", aktorId = AKTOER_ID_2)

            testRunnerBarnUtenOppgaveUkjentBruker(
                FNR_VOKSEN_UNDER_62,
                null,
                null,
                krav = GJENLEV,
                alleDocs = allDocuemtActions,
                sedJson = null,
                hendelseType = SENDT,
                sokPerson = sokBarn
            )
        }

        @Test
        fun `Krav om barnepensjon - relasjon mangler - saknr funnet i utgående sed og journalføres automatisk `() {
            val bestemsak = bestemSakResponse(SakType.BARNEP, TIL_BEHANDLING)
            testRunnerBarn(
                FNR_VOKSEN_UNDER_62,
                FNR_BARN,
                bestemsak,
                krav = GJENLEV,
                alleDocs = allDocuemtActions,
                relasjonAvod = null,
                sedJson = null,
                hendelseType = SENDT
            ) {
                    assertEquals(PENSJON, it.tema)
                    assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
                    assertEquals(FNR_BARN,it.bruker?.id)
                }
        }

        @Test
        fun `Krav om barnepensjon - relasjon mangler - saknr ikke funnet - id og fordeling `() {
            testRunnerBarn(
                FNR_VOKSEN_UNDER_62,
                FNR_BARN,
                null,
                krav = GJENLEV,
                alleDocs = allDocuemtActions,
                relasjonAvod = null,
                sedJson = null,
                hendelseType = SENDT
            ) {
                    assertEquals(PENSJON, it.tema)
                    assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
                    assertEquals(FNR_BARN,it.bruker?.id)
                }
        }

        @Test
        fun `Test med Sed fra Rina BARNEP og bestemsak - automatisk`() {
            val bestemsak = bestemSakResponse(SakType.BARNEP, TIL_BEHANDLING)

            val valgtbarnfnr = "05020876176"
            testRunnerBarn(
                "13017123321",
                "05020876176",
                bestemsak,
                krav = GJENLEV,
                alleDocs = allDocuemtActions,
                sedJson = mockSED(),
                hendelseType = SENDT
            ) {
                    assertEquals(PENSJON, it.tema)
                    assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
                    assertEquals(valgtbarnfnr, it.bruker?.id!!)
                }
        }

    }

    @Nested
    @DisplayName("Utgående - Scenario 4")
    inner class Scenario4Utgaende {

        @Test
        fun `Krav om gjenlevendeytelse - GP eller AP - ALDER - automatisk`() {
            val bestemsak = bestemSakResponse(sakStatus = TIL_BEHANDLING)

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                FNR_OVER_62,
                bestemsak,
                krav = GJENLEV,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT
            ) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Krav om gjenlevendeytelse - GP eller AP - GJENLEV - automatisk`() {
            val bestemsak2 = bestemSakResponse(SakType.GJENLEV, TIL_BEHANDLING)

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                FNR_OVER_62,
                bestemsak2,
                krav = GJENLEV,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.SAMBOER,
                hendelseType = SENDT
            ) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Krav om gjenlevendeytelse - GP eller AP - mangler relasjon - sakid fra sed - maskinell journalføring`() {
            val bestemsak2 = bestemSakResponse(sakStatus = TIL_BEHANDLING)

            testRunnerVoksen(FNR_VOKSEN_2, FNR_OVER_62, bestemsak2, krav = GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = null, hendelseType = SENDT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Krav om gjenlevendeytelse - GP eller AP - mangler relasjon - mangler sakid - id og fordeling`() {
            testRunnerVoksen(FNR_VOKSEN_2, FNR_OVER_62, null, krav = GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = null, hendelseType = SENDT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

    }

    @Nested
    @DisplayName("Utgående - Scenario 5")
    inner class Scenario5Utgaende {

        @Test
        fun `Krav om gjenlevendeytelse - Uføretrygd automatisk`() {
            val bestemsak = bestemSakResponse(SakType.UFOREP, TIL_BEHANDLING)

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                FNR_OVER_62,
                bestemsak,
                krav = GJENLEV,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT
            ) {
                assertEquals(PENSJON, it.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }

        }

    }

    @Nested
    @DisplayName("Utgående - Scenario 6")
    inner class Scenario6Utgaende {

        @Test
        fun `Krav om gjenlevendeytelse - Uføretrygd manuelt - id og fordeling`() {
            val bestemsak = bestemSakResponse(SakType.UFOREP, AVSLUTTET)

            //TODO feil enhet og feil tema(person nummer 2 skal ikke trenge å være over 62 år for å få tema pensjon her)
            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                FNR_OVER_62,
                bestemsak,
                krav = GJENLEV,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT
            ) {
                assertEquals(PENSJON, it.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }

            //TODO Feil tema og feil enhet
            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62,
                FNR_OVER_62,
                null,
                krav = GJENLEV,
                alleDocs = allDocuemtActions,
                relasjonAvod = RelasjonTilAvdod.EKTEFELLE,
                hendelseType = SENDT
            ) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
                assertEquals(FNR_OVER_62, it.bruker?.id!!)
            }
        }
    }

    @Nested
    @DisplayName("Utgående - Scenario 7")
    inner class Scenario7Utgaende {

        @Test
        fun `Krav om gjenlevendeytelse - flere sakstyper i retur - sakid finnes i sed - maskinell journalføring`() {
            val bestemsak = BestemSakResponse(null, listOf(sakInformasjon(), sakInformasjon("123456", SakType.UFOREP)))

            testRunnerBarn(
                FNR_VOKSEN_UNDER_62,
                FNR_BARN,
                bestemsak,
                krav = GJENLEV,
                alleDocs = allDocuemtActions,
                hendelseType = SENDT,
            ) {
                    assertEquals(PENSJON, it.tema)
                    assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
                }

            testRunnerVoksen(FNR_VOKSEN_UNDER_62,FNR_OVER_62, bestemsak, krav = GJENLEV, alleDocs = allDocuemtActions, hendelseType = SENDT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Krav om gjenlevendeytelse - flere sakstyper i retur - ingen saker - id og fordeling`() {
            val bestemsak = null

            testRunnerBarn(
                FNR_VOKSEN_UNDER_62,
                FNR_BARN,
                bestemsak,
                krav = GJENLEV,
                alleDocs = allDocuemtActions,
                hendelseType = SENDT,
            ) {
                    assertEquals(PENSJON, it.tema)
                    assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
                }

            testRunnerVoksen(FNR_VOKSEN_2, FNR_OVER_62, bestemsak, krav = GJENLEV, alleDocs = allDocuemtActions, hendelseType = SENDT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }
    }

    @Nested
    @DisplayName("Utgående - Scenario 8")
    inner class Scenario8Utgaende {

        @Test
        fun `manuell oppgave det mangler er mangelfullt fnr dnr - kun en person - id og fordeling`() {
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
            testRunnerBarnUtenOppgaveUkjentBruker(FNR_VOKSEN_2, null, krav = GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = RelasjonTilAvdod.EKTEFELLE, hendelseType = SENDT)

//            testRunnerBarnUtenOppgave(FNR_VOKSEN_2, null, krav = GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = RelasjonTilAvdod.EKTEFELLE, hendelseType = SENDT) {
//                assertEquals(PENSJON, it.tema)
//                //TODO: Kan vi finne en smartere måte å finne behandlingstema for tilfeller der person ikke er identifiserbar og SED har pesysSakid i seg
//                assertEquals(GJENLEVENDEPENSJON, it.behandlingstema)
//                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
//            }

            testRunnerBarn(FNR_VOKSEN_UNDER_62, null, krav = GJENLEV, alleDocs = allDocuemtActions, hendelseType = MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerBarn(FNR_VOKSEN_UNDER_62, FNR_BARN, krav = GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = null, hendelseType = SENDT) {
                    assertEquals(PENSJON, it.tema)
                    assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
                }

            testRunnerVoksen(FNR_VOKSEN_UNDER_62, FNR_OVER_62, krav = GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = null, hendelseType = SENDT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Mangler som fører til manuell oppgave fremdeles for etterlatteytelser testRunnerBarnUtenOppgave`() {
            testRunnerBarnUtenOppgaveUkjentBruker(FNR_VOKSEN_UNDER_62, null, sakId = null, krav = GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = RelasjonTilAvdod.EKTEFELLE, hendelseType = SENDT)

//            testRunnerBarnUtenOppgave(FNR_VOKSEN_UNDER_62, null, sakId = null, krav = GJENLEV, alleDocs = allDocuemtActions, relasjonAvod = RelasjonTilAvdod.EKTEFELLE, hendelseType = SENDT) {
//                assertEquals(PENSJON, it.tema)
//                assertEquals(GJENLEVENDEPENSJON, it.behandlingstema)
//                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
//            }
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

            val allDocuemtActions = forenkletSEDs(sedStatus = SedStatus.RECEIVED)
            val bestemsak = bestemSakResponse(SakType.UFOREP,TIL_BEHANDLING)


            testRunner(FNR_OVER_62, null, land = "SWE", alleDocs = allDocuemtActions, hendelseType = MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_OVER_62, null, alleDocs = allDocuemtActions, hendelseType = MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_OVER_62, null, alleDocs = allDocuemtActions, hendelseType = MOTTATT, norg2svar = FAMILIE_OG_PENSJONSYTELSER_OSLO) {
                assertEquals(PENSJON, it.tema)
                assertEquals(FAMILIE_OG_PENSJONSYTELSER_OSLO, it.journalfoerendeEnhet)
            }

            testRunner(FNR_OVER_62, bestemsak, alleDocs = allDocuemtActions, hendelseType = MOTTATT, norg2svar = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

        }

        @Test
        fun `Krav om etterlatteytelser`() {
            val allDocuemtActions = forenkletSEDs(sedStatus = SedStatus.RECEIVED)

            testRunnerBarn(FNR_VOKSEN_2, null, alleDocs = allDocuemtActions, land = "SWE", krav = GJENLEV, hendelseType = MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerBarn(FNR_OVER_62, FNR_BARN, alleDocs = allDocuemtActions, krav = GJENLEV, hendelseType = MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerBarnmedSokPerson(FNR_OVER_62, FNR_BARN, alleDocs = allDocuemtActions, krav = GJENLEV, hendelseType = MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerBarn(FNR_OVER_62, FNR_BARN, alleDocs = allDocuemtActions, krav = GJENLEV, land = "SWE", hendelseType = MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

        }

        @Test
        fun `Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav ALDER skal routes til NFP_UTLAND_AALESUND 4862`() {
            val sed15000sent = SED.generateSedToClass<P15000>( createSedPensjon(SedType.P15000, FNR_OVER_62, krav = ALDER))
            val sedP5000mottatt = SED.generateSedToClass<P5000>( createSedPensjon(SedType.P5000, FNR_OVER_62, krav = ALDER))

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P15000, SedStatus.SENT),
                ForenkletSED("30002", SedType.P5000, SedStatus.RECEIVED),
                ForenkletSED("654654", SedType.P8000, SedStatus.EMPTY)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_10, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP5000mottatt.toJson() andThen sed15000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.hentPerson(NorskIdent(FNR_OVER_62)) } returns createBrukerWith(FNR_OVER_62, "Fornavn", "Pensjonisten", "NOR")
            every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P5000, P_BUC_10)

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(NFP_UTLAND_AALESUND, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P5000", oppgaveMelding.sedType?.name)

            assertEquals("INNGAAENDE", request.journalpostType.name)
            assertEquals(PENSJON, request.tema)
            assertEquals(NFP_UTLAND_AALESUND, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        }

        @Test
        fun `Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav ALDER bosatt utland skal routes til PENSJON_UTLAND 0001`() {
            val sed15000sent = SED.generateSedToClass<P15000>(createSedPensjon(SedType.P15000, FNR_OVER_62, krav = ALDER))
            val sedP5000mottatt = SED.generateSedToClass<P5000>(createSedPensjon(SedType.P5000, FNR_OVER_62))

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P15000, SedStatus.SENT),
                ForenkletSED("30002", SedType.P5000, SedStatus.RECEIVED)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_10, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP5000mottatt.toJson() andThen sed15000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.hentPerson(NorskIdent(FNR_OVER_62)) } returns createBrukerWith(FNR_OVER_62, "Fornavn", "Pensjonisten", "SWE")
            every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P5000, P_BUC_10)

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
        fun `Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav UFOEREP skal routes til UFOREP_UTLANDSTILSNITT 4476`() {
            val sed15000sent =  SED.generateSedToClass<P15000>(createSedPensjon(SedType.P15000, FNR_VOKSEN_UNDER_62, krav = UFOREP))
            val sedP5000mottatt = SED.generateSedToClass<P5000>(createSedPensjon(SedType.P5000, FNR_VOKSEN_UNDER_62))

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P15000, SedStatus.SENT),
                ForenkletSED("30002", SedType.P5000, SedStatus.RECEIVED)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_10, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP5000mottatt.toJson() andThen sed15000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.hentPerson(NorskIdent(FNR_VOKSEN_UNDER_62)) } returns createBrukerWith(FNR_VOKSEN_UNDER_62, "Fornavn", "Pensjonisten", "NOR")
            every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P5000, P_BUC_10)

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(UFORE_UTLANDSTILSNITT, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P5000", oppgaveMelding.sedType?.name)

            assertEquals("INNGAAENDE", request.journalpostType.name)
            assertEquals(UFORETRYGD, request.tema)
            assertEquals(UFORE_UTLANDSTILSNITT, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Flere sed i buc, mottar en P5000 tidligere mottatt P15000, krav UFOEREP bosatt utland skal routes til UFOREP_UTLAND 4475`() {
            val sed15000sent = SED.generateSedToClass<P15000>(createSedPensjon(SedType.P15000, FNR_VOKSEN_UNDER_62, krav = UFOREP))
            val sedP5000mottatt = SED.generateSedToClass<P5000>(createSedPensjon(SedType.P5000, FNR_VOKSEN_UNDER_62))

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P15000, SedStatus.SENT),
                ForenkletSED("30002", SedType.P5000, SedStatus.RECEIVED)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_10, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP5000mottatt.toJson() andThen sed15000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.hentPerson(NorskIdent(FNR_VOKSEN_UNDER_62)) } returns createBrukerWith(FNR_VOKSEN_UNDER_62, "Fornavn", "Pensjonisten", "SWE")
            every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P5000, P_BUC_10)

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(UFORE_UTLAND, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P5000", oppgaveMelding.sedType?.name)

            assertEquals("INNGAAENDE", request.journalpostType.name)
            assertEquals(UFORETRYGD, request.tema)
            assertEquals(UFORE_UTLAND, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Innkommende P15000 gjenlevende mangler søker`() {
            val sedP15000 = SED.generateSedToClass<P15000>(createSedPensjon(SedType.P15000, "12321", gjenlevendeFnr = null, krav = GJENLEV))

            val alleDocumenter = forenkletSEDs(sedStatus = SedStatus.RECEIVED)

            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_10, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP15000.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P15000, P_BUC_10)

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

            val sokPerson = setOf(IdentInformasjon(FNR_VOKSEN_UNDER_62, IdentGruppe.FOLKEREGISTERIDENT)) // korrekt return sokPerson

            val land = "SWE"
            val fnrSokVoken = null
            val mockGjenlevende = createBrukerWith(FNR_VOKSEN_UNDER_62,  "Voksen ", "Gjenlevnde", land, aktorId = AKTOER_ID)

            val sedP15000 = SED.generateSedToClass<P15000>(
                createSedPensjon(
                    SedType.P15000,
                    fnr = "12321",
                    gjenlevendeFnr = fnrSokVoken,
                    sivilstand = SivilstandItem(
                        fradato = "01-30-1980",
                        status = "01"
                    ),
                    krav = GJENLEV,
                    relasjon = RelasjonTilAvdod.EKTEFELLE,
                    pdlPerson = mockGjenlevende,
                    fdatoAnnenPerson = Fodselsnummer.fra(FNR_VOKSEN_UNDER_62)?.getBirthDateAsIso()
                )
            )

            val alleDocumenter = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P15000, SedStatus.RECEIVED))

            every { personService.sokPerson(any()) } returns sokPerson
            every { personService.hentPerson(NorskIdent(FNR_VOKSEN_UNDER_62)) } returns mockGjenlevende
            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_10, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP15000.toJsonSkipEmpty()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P15000, P_BUC_10)

            //kjør
            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(PENSJON_UTLAND, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P15000", oppgaveMelding.sedType?.name)

            assertEquals("INNGAAENDE", request.journalpostType.name)
            assertEquals(PENSJON_UTLAND, request.journalfoerendeEnhet)

            verify(exactly = 1) { personService.sokPerson(any()) }
            verify(exactly = 1) { personService.hentPerson(any()) }

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
            val allDocuemtActions = forenkletSEDs(sedStatus = SedStatus.RECEIVED)

            testRunner(
                FNR_VOKSEN_UNDER_62,
                bestemSak = null,
                land = "SWE",
                krav = UFOREP,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                norg2svar = null
            ) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
            }
        }
    }

    @Nested
    @DisplayName("Inngående - Scenario 4")
    inner class Scenario4Inngaende {
        @Test
        fun `Flere sed i buc, mottatt en P15000 med ukjent gjenlevende relasjon, krav GJENLEV sender en P5000 med korrekt gjenlevende denne skal journalføres automatisk`() {
            val sed15000mottatt = SED.generateSedToClass<P15000>(createSedPensjon(SedType.P15000, FNR_VOKSEN_2, gjenlevendeFnr = "", krav = GJENLEV, relasjon = RelasjonTilAvdod.EKTEFELLE))
            val sedP5000sendt = SED.generateSedToClass<P5000>(createSedPensjon(SedType.P5000, FNR_OVER_62, eessiSaknr = SAK_ID, gjenlevendeFnr = FNR_OVER_62))

            val saker = listOf(
                sakInformasjon(),
                sakInformasjon("34234123", SakType.UFOREP, AVSLUTTET)
            )

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P15000, SedStatus.RECEIVED),
                ForenkletSED("30002", SedType.P5000, SedStatus.SENT)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_10, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP5000sendt.toJson() andThen sed15000mottatt.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.hentPerson(NorskIdent(FNR_VOKSEN_2)) } returns createBrukerWith(FNR_VOKSEN_2, "Avdød", "død", "SWE")
            every { personService.hentPerson(NorskIdent(FNR_OVER_62)) } returns createBrukerWith(FNR_OVER_62, "Gjenlevende", "Lever", "SWE", aktorId = AKTOER_ID_2)
            every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID_2) } returns saker
            every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, _) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P5000, P_BUC_10)

            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            val request = journalpost.captured

            assertEquals("UTGAAENDE", request.journalpostType.name)
            assertEquals(PENSJON, request.tema)
            assertEquals(PENSJON_UTLAND, request.journalfoerendeEnhet)

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
        every { navansattKlient.navAnsattMedEnhetsInfo(any(), any()) } returns null

        if (fnrBarn != null) {
            every { personService.hentPerson(NorskIdent(fnrBarn)) } returns createBrukerWith(fnrBarn, "Barn", "Diskret", land, aktorId = AKTOER_ID_2)
        }
        if (sokPerson != null) {
            every { personService.sokPerson(any()) } returns setOf(
                IdentInformasjon(
                    FNR_OVER_62,
                    IdentGruppe.FOLKEREGISTERIDENT
                ), IdentInformasjon("BLÆ", IdentGruppe.AKTORID)
            )
            every { personService.hentPerson(any()) } returns sokPerson

        }

        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

        if (bestemSak != null) {
            every { fagmodulKlient.hentPensjonSaklist(any()) } returns bestemSak.sakInformasjonListe
        }

        val (journalpost, _) = initJournalPostRequestSlot()

        val forsikretfnr = if (krav == GJENLEV) fnrVoksen else null
        val hendelse = createHendelseJson(SedType.P15000, P_BUC_10, forsikretfnr)

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

        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        clearAllMocks()
    }

    private fun testRunnerBarnUtenOppgave(
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
        every { navansattKlient.navAnsattMedEnhetsInfo(any(), any()) } returns null

        if (fnrBarn != null) {
            every { personService.hentPerson(NorskIdent(fnrBarn)) } returns createBrukerWith(fnrBarn, "Barn", "Diskret", land, aktorId = AKTOER_ID_2)
        }
        if (sokPerson != null) {
            every { personService.sokPerson(any()) } returns setOf(
                IdentInformasjon(
                    FNR_OVER_62,
                    IdentGruppe.FOLKEREGISTERIDENT
                ), IdentInformasjon("BLÆ", IdentGruppe.AKTORID)
            )
            every { personService.hentPerson(any()) } returns sokPerson

        }

        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

        if (bestemSak != null) {
            every { fagmodulKlient.hentPensjonSaklist(any()) } returns bestemSak.sakInformasjonListe
        }

        val (journalpost, _) = initJournalPostRequestSlot()
        val forsikretfnr = if (krav == GJENLEV) fnrVoksen else null
        val hendelse = createHendelseJson(SedType.P15000, P_BUC_10, forsikretfnr)

        every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

        when (hendelseType) {
            SENDT -> sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            else -> fail()
        }
        block(journalpost.captured)

        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        clearAllMocks()
    }

    private fun testRunnerBarnUtenOppgaveUkjentBruker(
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
    ) {
        val sed = sedJson?.let { mapJsonToAny<P15000>(it) }
            ?: SED.generateSedToClass(createSedPensjon(SedType.P15000, fnrVoksen, eessiSaknr = sakId, gjenlevendeFnr = fnrBarn, krav = krav, pdlPerson = sokPerson , relasjon = relasjonAvod))

        initCommonMocks(sed, alleDocs)

        every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns createBrukerWith(fnrVoksen, "Mamma forsørger", "Etternavn", land, aktorId = AKTOER_ID)
        every { navansattKlient.navAnsattMedEnhetsInfo(any(), any()) } returns null

        if (fnrBarn != null) {
            every { personService.hentPerson(NorskIdent(fnrBarn)) } returns createBrukerWith(fnrBarn, "Barn", "Diskret", land, aktorId = AKTOER_ID_2)
        }
        if (sokPerson != null) {
            every { personService.sokPerson(any()) } returns setOf(
                IdentInformasjon(
                    FNR_OVER_62,
                    IdentGruppe.FOLKEREGISTERIDENT
                ), IdentInformasjon("BLÆ", IdentGruppe.AKTORID)
            )
            every { personService.hentPerson(any()) } returns sokPerson

        }

        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

        if (bestemSak != null) {
            every { fagmodulKlient.hentPensjonSaklist(any()) } returns bestemSak.sakInformasjonListe
        }

        val forsikretfnr = if (krav == GJENLEV) fnrVoksen else null
        val hendelse = createHendelseJson(SedType.P15000, P_BUC_10, forsikretfnr)

        every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

        when (hendelseType) {
            SENDT -> sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            else -> fail()
        }

        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
//        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        clearAllMocks()
    }


    private fun bestemSakResponse(sakType: SakType? = SakType.ALDER, sakStatus: SakStatus? = AVSLUTTET) =
        BestemSakResponse(null, listOf(SakInformasjon(sakId = SAK_ID, sakType = sakType!!, sakStatus = sakStatus!!)))

    private fun forenkletSEDs(sedStatus: SedStatus? = SedStatus.SENT) = listOf(ForenkletSED("10001212", SedType.P15000, sedStatus))

    private fun sakInformasjon(sakId: String? = SAK_ID, sakType: SakType? = SakType.ALDER, sakStatus: SakStatus? = TIL_BEHANDLING) =
        SakInformasjon(sakId = sakId, sakType = sakType!!, sakStatus = sakStatus!!)

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

        val sed = sedJson?.let { mapJsonToAny(it) }
            ?: createSedPensjon(
                SedType.P15000, fnrVoksen, eessiSaknr = sakId, gjenlevendeFnr = fnrBarn, krav = krav, relasjon = relasjonAvod, pdlPerson = mockBarn
            )

        initCommonMocks(sed, alleDocs)

        if (benyttSokPerson) {
            every { personService.sokPerson(any()) } returns setOf(IdentInformasjon(fnrBarn, IdentGruppe.FOLKEREGISTERIDENT), IdentInformasjon("BLÆ", IdentGruppe.AKTORID))
        }

        every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns createBrukerWith(fnrVoksen, "Mamma forsørger", "Etternavn", land, aktorId = AKTOER_ID)
        every { personService.hentPerson(NorskIdent(fnrBarn)) } returns mockBarn
        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak

        val (journalpost, _) = initJournalPostRequestSlot()

        val forsikretfnr = if (krav == GJENLEV) fnrVoksen else null
        val hendelse = createHendelseJson(SedType.P15000, P_BUC_10, forsikretfnr)

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

        verify { personService.hentPerson(any()) }
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

        every { navansattKlient.navAnsattMedEnhetsInfo(any(), any()) } returns null

        val (journalpost, _) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P15000, P_BUC_10)

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
            verify { personService.hentPerson(any()) }
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

        every { navansattKlient.navAnsattMedEnhetsInfo(any(), any()) } returns null
        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P15000, P_BUC_10)

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

        clearAllMocks()
    }
}
