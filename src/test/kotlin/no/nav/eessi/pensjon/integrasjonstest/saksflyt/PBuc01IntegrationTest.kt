package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.*
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.P2200
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.*
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.journalforing.OpprettJournalpostRequest
import no.nav.eessi.pensjon.journalforing.krav.BehandleHendelseModel
import no.nav.eessi.pensjon.journalforing.krav.HendelseKode
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveType.*
import no.nav.eessi.pensjon.listeners.pesys.BestemSakResponse
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.oppgaverouting.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.oppgaverouting.Enhet.PENSJON_UTLAND
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Npid
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

@DisplayName("P_BUC_01 – IntegrationTest")
internal class PBuc01IntegrationTest : JournalforingTestBase() {

    @Nested
    @DisplayName("Inngående")
    inner class InngaaendePBuc01 {

        @BeforeEach
        fun beforetest(){
            every { etterlatteService.hentGjennySak(any()) }  returns mockHentGjennySak("12345678")
            every { gcpStorageService.hentFraGjenny(any()) }  returns null

        }

        @Disabled
        @Test
        fun `Krav om alderpensjon for inngående P2000 journalføres automatisk med bruk av bestemsak og det opprettes en oppgave type BEHANDLE_SED`() {
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(
                        sakId = SAK_ID,
                        sakType = ALDER,
                        sakStatus = LOPENDE
                    )
                )
            )
            val allDocuemtActions = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P2000, SedStatus.RECEIVED))

            testRunnerVoksen(
                FNR_OVER_62,
                bestemsak,
                land = "SWE",
                krav = KravType.ALDER,
                alleDocs = allDocuemtActions,
                forsokFerdigStilt = true,
                hendelseType = MOTTATT,
                sivilstand = SivilstandItem(LocalDate.of(2020, 10, 11).toString(), SivilstandRina.enslig),
                statsborgerskap = StatsborgerskapItem("SWE")
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(PENSJON_UTLAND, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(null, it.oppgaveMelding?.filnavn)
                assertEquals(PENSJON_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals(BEHANDLE_SED, it.oppgaveMelding?.oppgaveType)

                assertEquals(true, it.kravMeldingList?.isNotEmpty())
                assertEquals(1, it.kravMeldingList?.size)

                val kravMelding = it.kravMeldingList?.firstOrNull()
                assertEquals(HendelseKode.SOKNAD_OM_ALDERSPENSJON, kravMelding?.hendelsesKode)
                assertEquals("147729", kravMelding?.bucId)
                assertEquals(SAK_ID, kravMelding?.sakId)
            }
        }

        @Test
        fun `Krav om alderpensjon der person ikke er identifiserbar men pesys sakId finnes i sed så skal vi opprette journalpost, settes til avbrutt og ikke journalføringsoppgave`() {
            val allDocuemtActions = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P2000, SedStatus.SENT))

//            every { etterlatteService.hentGjennySak(any()) }  returns mockHentGjennySak("12345678")

            testRunnerVoksen(
                null,
                null,
                land = "SWE",
                alleDocs = allDocuemtActions,
                forsokFerdigStilt = false,
                hendelseType = SENDT,
                sivilstand = null,
                statsborgerskap = StatsborgerskapItem("SWE")
            ) {
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(ID_OG_FORDELING, journalpostRequest.journalfoerendeEnhet)
            }
        }

        @Disabled
        @Test
        fun `Krav om alderpensjon for inngående P2000 journalføres automatisk med bruk av bestemsak med ugyldig vedlegg og det opprettes to oppgaver type BEHANDLE_SED`() {
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(
                        sakId = SAK_ID,
                        sakType = ALDER,
                        sakStatus = LOPENDE
                    )
                )
            )
            val allDocuemtActions = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P2000, SedStatus.RECEIVED))

            testRunnerVoksen(
                FNR_OVER_62,
                bestemsak,
                land = "SWE",
                krav = KravType.ALDER,
                alleDocs = allDocuemtActions,
                forsokFerdigStilt = true,
                documentFiler = getDokumentfilerUtenGyldigVedlegg(),
                hendelseType = MOTTATT,
                sivilstand = SivilstandItem(LocalDate.of(2020, 10, 11).toString(), SivilstandRina.enslig),
                statsborgerskap = StatsborgerskapItem("SWE")
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(PENSJON_UTLAND, journalpostRequest.journalfoerendeEnhet)

                assertEquals(2, oppgaveMeldingList.size)

                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(null, it.oppgaveMelding?.filnavn)
                assertEquals(PENSJON_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals(BEHANDLE_SED, it.oppgaveMelding?.oppgaveType)

                assertNotNull(it.oppgaveMeldingUgyldig)
                assertEquals(BEHANDLE_SED, it.oppgaveMeldingUgyldig!!.oppgaveType)
                assertEquals(null, it.oppgaveMeldingUgyldig.journalpostId)
                assertEquals("docx.docx ", it.oppgaveMeldingUgyldig.filnavn)

                assertEquals(true, it.kravMeldingList?.isNotEmpty())
                assertEquals(1, it.kravMeldingList?.size)

                val kravMelding = it.kravMeldingList?.firstOrNull()
                assertEquals(HendelseKode.SOKNAD_OM_ALDERSPENSJON, kravMelding?.hendelsesKode)
                assertEquals("147729", kravMelding?.bucId)
                assertEquals(SAK_ID, kravMelding?.sakId)

            }

        }

        @Test
        fun `Krav om Alder P2000 journalføres automatisk med bruk av bestemsak uten forsokFerdigStilt`() {
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(
                        sakId = SAK_ID,
                        sakType = ALDER,
                        sakStatus = LOPENDE
                    )
                )
            )
            val allDocuemtActions = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P2000, SedStatus.RECEIVED))

            testRunnerVoksen(
                FNR_OVER_62, bestemsak, land = "SWE", alleDocs = allDocuemtActions, forsokFerdigStilt = false, hendelseType = MOTTATT
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(PENSJON_UTLAND, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(PENSJON_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals("0123456789000", it.oppgaveMelding?.aktoerId)
                assertEquals(JOURNALFORING, it.oppgaveMelding?.oppgaveType)
            }
        }

        @Test
        fun `Krav om Alder P2000 feiler med bestemSak`() {
            val bestemsak = BestemSakResponse(null, emptyList())
            val allDocuemtActions = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P2000, SedStatus.RECEIVED))

            testRunnerVoksen(
                FNR_OVER_62, bestemsak, land = "SWE", alleDocs = allDocuemtActions, hendelseType = MOTTATT
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(PENSJON_UTLAND, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(PENSJON_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals(JOURNALFORING, it.oppgaveMelding?.oppgaveType)
            }
        }



        @Test
        fun `Krav om Alder P2000 uten gyldig validering fnr-fato og sed-fdato går til ID og Fordeling`() {
            val bestemsak = BestemSakResponse(null, emptyList())
            val allDocuemtActions = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P2000, SedStatus.RECEIVED))
            val fdato = "1970-06-20"

            testRunnerVoksen(
                FNR_VOKSEN_UNDER_62, bestemsak, land = "SWE", alleDocs = allDocuemtActions, hendelseType = MOTTATT, fdato = fdato
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(ID_OG_FORDELING, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(ID_OG_FORDELING, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals(null, it.oppgaveMelding?.aktoerId)
                assertEquals(JOURNALFORING, it.oppgaveMelding?.oppgaveType)

            }
        }


        @Test
        fun `Krav om Alder P2000 uten gyldig fnr sendes til ID og Fordeling`() {
            val bestemsak = BestemSakResponse(null, emptyList())
            val allDocuemtActions = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P2000, SedStatus.RECEIVED))

            testRunnerVoksen(
                null, bestemsak, land = "SWE", alleDocs = allDocuemtActions, hendelseType = MOTTATT
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(ID_OG_FORDELING, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(ID_OG_FORDELING, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals(null, it.oppgaveMelding?.aktoerId)
                assertEquals(JOURNALFORING, it.oppgaveMelding?.oppgaveType)

            }
        }

    }

    @Nested
    @DisplayName("Inngående sokPerson")
    inner class InngaaendeSokPersonPBUC01 {

        @Test
        fun `Krav om Alder P2000 ingen fnr funnet benytter sokPerson finner person medfører maskinell journalføring`() {
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(
                        sakId = SAK_ID,
                        sakType = ALDER,
                        sakStatus = LOPENDE
                    )
                )
            )

            val allDocuemtActions = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P2000, SedStatus.RECEIVED))


            testRunnerVoksenSokPerson(
                FNR_VOKSEN_UNDER_62,
                bestemsak,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                land = "SWE",
                sokPerson = setOf(IdentInformasjon(FNR_VOKSEN_UNDER_62, IdentGruppe.FOLKEREGISTERIDENT))
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(PENSJON_UTLAND, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(PENSJON_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals("0123456789000", it.oppgaveMelding?.aktoerId)
                assertEquals(JOURNALFORING, it.oppgaveMelding?.oppgaveType)
            }
        }

        @Test
        fun `Krav om Alder P2000 ingen fnr funnet benytter sokPerson som heller ikke finner person Oppgave routes til ID Og Fordeling`() {

            val allDocuemtActions = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P2000, SedStatus.RECEIVED))

            testRunnerVoksenSokPerson(
                FNR_VOKSEN_UNDER_62,
                null,
                alleDocs = allDocuemtActions,
                hendelseType = MOTTATT,
                land = "SWE",
                sokPerson = emptySet()
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(ID_OG_FORDELING, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(ID_OG_FORDELING, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals(null, it.oppgaveMelding?.aktoerId)
                assertEquals(JOURNALFORING, it.oppgaveMelding?.oppgaveType)
            }
        }

        @Test
        fun `Flere sed i buc, mottar en P5000 tidligere mottatt P2000, krav ALDER skal routes til PENSJON_UTLAND 001`() {
            val pdlPerson = createBrukerWith(FNR_OVER_62, "Voksen ", "Forsikret", "SWE", aktorId = AKTOER_ID)
            val fdato = pdlPerson.foedselsdato?.foedselsdato.toString()
            val sed20000mottatt = SED.generateSedToClass<P2000>( createSedPensjon(SedType.P2000, null, krav = KravType.ALDER, pdlPerson = pdlPerson, fdato = fdato))
            val sedP5000mottatt = SED.generateSedToClass<P5000>( createSedPensjon(SedType.P5000, null, krav = KravType.ALDER, pdlPerson = pdlPerson, fdato = fdato))

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P2000, SedStatus.RECEIVED),
                ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P5000, SedStatus.RECEIVED),
                ForenkletSED("654654", SedType.P8000, SedStatus.EMPTY)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_01, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sed20000mottatt.toJson() andThen sedP5000mottatt.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.sokPerson(any()) } returns setOf(
                    IdentInformasjon(
                        FNR_OVER_62,
                        IdentGruppe.FOLKEREGISTERIDENT
                    ), IdentInformasjon("BLÆ", IdentGruppe.AKTORID)
                )
            every { personService.hentPerson(NorskIdent(FNR_OVER_62)) } returns pdlPerson
            every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns null

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P5000, P_BUC_01)

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(PENSJON_UTLAND, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P5000", oppgaveMelding.sedType?.name)

            assertEquals("INNGAAENDE", request.journalpostType.name)
            assertEquals(PENSJON, request.tema)
            assertEquals(PENSJON_UTLAND, request.journalfoerendeEnhet)

            verify(exactly = 1) { personService.sokPerson(any())}
            verify(exactly = 1) { personService.hentPerson(any()) }

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        }

    }
        @Test
        fun `Gitt en P_BUC_01 med flere P8000 med forskjellige roller - så velges forsikret person som det journalfores på`() {
            val fnr = "09035225916"
            val afnr = "05127921999"
            val bfnr = "05121021999"
            val aktoerf = "${fnr}0000"
            val saknr = "1223123123"

            val sedP8000 = SED.generateSedToClass<no.nav.eessi.pensjon.eux.model.sed.P8000>(createSed(SedType.P8000, fnr, null, saknr, fdato = "1952-03-09"))
            val sedP8000sendt = SED.generateSedToClass<no.nav.eessi.pensjon.eux.model.sed.P8000>(createSed(SedType.P8000, fnr, createAnnenPerson(fnr = afnr, rolle = Rolle.FORSORGER), saknr))
            val sedP8000recevied = SED.generateSedToClass<no.nav.eessi.pensjon.eux.model.sed.P8000>(createSed(SedType.P8000, fnr, createAnnenPerson(fnr = bfnr, rolle = Rolle.BARN), null))

            val dokumenter = listOf(
                ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P8000, SedStatus.RECEIVED),
                ForenkletSED("b12e06dda2c7474b9998c7139c841647", SedType.P8000, SedStatus.SENT),
                ForenkletSED("b12e06dda2c7474b9998c7139c841648", SedType.P8000, SedStatus.RECEIVED)
            )

            every { euxKlient.hentBuc(any()) } returns Buc(id = "2", processDefinitionName = "P_BUC_01", documents = bucDocumentsFrom(dokumenter))
            every { euxKlient.hentSedJson(any(), any()) } returns sedP8000.toJson() andThen sedP8000sendt.toJson() andThen sedP8000recevied.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.harAdressebeskyttelse(any()) } returns false
            every { personService.hentPerson(NorskIdent(fnr)) } returns createBrukerWith(fnr, "Forsikret", "Personen", "NOR", aktorId = aktoerf)
            every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns PENSJON_UTLAND
            every { etterlatteService.hentGjennySak(any()) } returns mockHentGjennySakMedError()

            val (journalpost, _) = initJournalPostRequestSlot(false)
            val hendelse = createHendelseJson(SedType.P8000, P_BUC_01)

            val meldingSlot = mutableListOf<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMeldingList: List<OppgaveMelding> = meldingSlot.map {
                mapJsonToAny(it)
            }

            val request = journalpost.captured

            // forvent tema == PEN og enhet Pensjon Utland
            assertEquals(PENSJON, request.tema)
            assertEquals(PENSJON_UTLAND, request.journalfoerendeEnhet)
            assertEquals(fnr, request.bruker?.id)

            assertEquals(1, oppgaveMeldingList.size)
            val oppgaveMelding = oppgaveMeldingList.first()
            assertEquals("429434378", oppgaveMelding.journalpostId)
            assertEquals(null, oppgaveMelding.filnavn)
            assertEquals(PENSJON_UTLAND, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(SedType.P8000, oppgaveMelding.sedType)

            verify(exactly = 1) { personService.hentPerson(any<NorskIdent>()) }
            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 3) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()

        }

    @Nested
    @DisplayName("Utgående")
    inner class UtgaaendePbuc01 {

        @Test
        fun `Krav om alderpensjon for utgående P2000 med NPID journalføres automatisk med bruk av bestemsak med ugyldig vedlegg og det opprettes to oppgaver type BEHANDLE_SED`() {
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(
                        sakId = SAK_ID,
                        sakType = ALDER,
                        sakStatus = LOPENDE
                    )
                )
            )
            val allDocuemtActions = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P2000, SedStatus.SENT))

            every { personService.hentPerson(Npid("12345678901")) } returns createBrukerWith(FNR_OVER_62, "Voksen ", "Forsikret", "SWE", aktorId = AKTOER_ID)

            testRunnerVoksen(
                FNR_OVER_62,
                bestemsak,
                land = "SWE",
                krav = KravType.ALDER,
                alleDocs = allDocuemtActions,
                forsokFerdigStilt = true,
                documentFiler = getDokumentfilerUtenGyldigVedlegg(),
                hendelseType = MOTTATT,
                sivilstand = SivilstandItem(LocalDate.of(2020, 10, 11).toString(), SivilstandRina.enslig),
                statsborgerskap = StatsborgerskapItem("SWE")
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(PENSJON_UTLAND, journalpostRequest.journalfoerendeEnhet)

                assertEquals(2, oppgaveMeldingList.size)

                assertEquals(PENSJON_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals(BEHANDLE_SED, it.oppgaveMelding?.oppgaveType)

                assertNotNull(it.oppgaveMeldingUgyldig)
                assertEquals(BEHANDLE_SED, it.oppgaveMeldingUgyldig!!.oppgaveType)
            }
        }

        @Test
        fun `Krav om alderpensjon for Utgående P2000 journalføres automatisk med bruk av bestemsak uten forsokFerdigStilt oppretter en oppgave type JOURNALFORING`() {
            val bestemsak = BestemSakResponse(
                null, listOf(
                    SakInformasjon(
                        sakId = SAK_ID,
                        sakType = ALDER,
                        sakStatus = LOPENDE
                    )
                )
            )
            val allDocuemtActions = listOf(ForenkletSED("10001212", P2200, SedStatus.SENT))

            testRunnerVoksen(
                FNR_OVER_62, bestemsak, alleDocs = allDocuemtActions, hendelseType = SENDT
            ) {
                val oppgaveMeldingList = it.oppgaveMeldingList
                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(PENSJON_UTLAND, journalpostRequest.journalfoerendeEnhet)

                assertEquals(1, oppgaveMeldingList.size)
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(PENSJON_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals("0123456789000", it.oppgaveMelding?.aktoerId)
                assertEquals(SENDT, it.oppgaveMelding?.hendelseType)
                assertEquals(JOURNALFORING_UT, it.oppgaveMelding?.oppgaveType)

            }

        }
        @Test
        fun `Krav om alderpensjon for P2000 journalføres manualt når bestemsak feiler`() {
            val bestemsak = BestemSakResponse(
                null, emptyList()
            )
            val allDocuemtActions = listOf(ForenkletSED("10001212", SedType.P2000, SedStatus.SENT))

            testRunnerVoksen(
                FNR_OVER_62, bestemsak, alleDocs = allDocuemtActions, hendelseType = SENDT
            ) {
                assertEquals("429434378", it.oppgaveMelding?.journalpostId)
                assertEquals(PENSJON_UTLAND, it.oppgaveMelding?.tildeltEnhetsnr)
                assertEquals("0123456789000", it.oppgaveMelding?.aktoerId)
                assertEquals(JOURNALFORING_UT, it.oppgaveMelding?.oppgaveType)
                assertEquals(JOURNALFORING_UT, it.oppgaveMelding?.oppgaveType)

                val journalpostRequest = it.opprettJournalpostRequest
                assertEquals(PENSJON, journalpostRequest.tema)
                assertEquals(PENSJON_UTLAND, journalpostRequest.journalfoerendeEnhet)
            }
        }
    }

    private fun testRunnerVoksenSokPerson(
        fnrVoksen: String,
        bestemSak: BestemSakResponse? = null,
        sakId: String? = SAK_ID,
        land: String = "NOR",
        krav: KravType = KravType.ALDER,
        alleDocs: List<ForenkletSED>,
        forsokFerdigStilt: Boolean = false,
        documentFiler: SedDokumentfiler = getDokumentfilerUtenVedlegg(),
        hendelseType: HendelseType,
        sokPerson: Set<IdentInformasjon> = emptySet(),
        block: (TestResult) -> Unit
    ) {

        val fnrSokVoken = null
        val mockPerson = createBrukerWith(fnrVoksen, "Voksen ", "Forsikret", land, aktorId = AKTOER_ID)
        val sed = SED.generateSedToClass<P2000>(
            createSedPensjon(
                SedType.P2000,
                fnrSokVoken,
                eessiSaknr = sakId,
                krav = krav,
                pdlPerson = mockPerson,
                fdato = mockPerson.foedselsdato?.foedselsdato
            )
        )

        initCommonMocks(sed, alleDocs, documentFiler)

        every { personService.sokPerson(any()) } returns sokPerson
        every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns mockPerson

        every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak
        val (journalpost, _) = initJournalPostRequestSlot(forsokFerdigStilt)

        val hendelse = createHendelseJson(SedType.P2000, P_BUC_01)
        val meldingSlot = mutableListOf<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val kravmeldingSlot = mutableListOf<String>()
        every { kravInitHandlerKafka.sendDefault(any(), capture(kravmeldingSlot)).get() } returns mockk()
        every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns PENSJON_UTLAND

        val journalpostRequest = slot<OpprettJournalpostRequest>()
        sakId?.let {
            every { etterlatteService.hentGjennySak(any()) } returns mockHentGjennySak(it)
        } ?: run {
            every { etterlatteService.hentGjennySak(any()) } returns mockHentGjennySakMedError()
        }
        when (hendelseType) {
            SENDT -> sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
        }

        createMockedJournalPostWithOppgave(journalpostRequest, hendelse, hendelseType)

        val kravMeldingList: List<BehandleHendelseModel> = kravmeldingSlot.map {
            mapJsonToAny(it)
        }

        val oppgaveMeldingList: List<OppgaveMelding> = meldingSlot.map {
            mapJsonToAny(it)
        }
        block(TestResult(journalpost.captured, oppgaveMeldingList, kravMeldingList))

        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }
       // }

        clearAllMocks()
    }

    private fun testRunnerVoksen(
        fnrVoksen: String?,
        bestemSak: BestemSakResponse? = null,
        sakId: String? = SAK_ID,
        land: String = "NOR",
        krav: KravType = KravType.ALDER,
        alleDocs: List<ForenkletSED>,
        forsokFerdigStilt: Boolean = false,
        documentFiler: SedDokumentfiler = getDokumentfilerUtenVedlegg(),
        hendelseType: HendelseType,
        sivilstand: SivilstandItem? = null,
        statsborgerskap: StatsborgerskapItem? = null,
        fdato: String? = null,
        block: (TestResult) -> Unit
    ) {

        val mockPerson = if (fnrVoksen != null) {
            val mockp = createBrukerWith(
                fnrVoksen, "Voksen ", "Forsikret", land, aktorId = AKTOER_ID
            )
            if (Fodselsnummer.fra(fnrVoksen)?.erNpid != true)
                every { personService.hentPerson(NorskIdent(fnrVoksen)) } returns mockp
            else
                every { personService.hentPerson(Npid(fnrVoksen)) } returns mockp
            mockp
        } else {
            null
        }
        val sed = SED.generateSedToClass<P2000>(createSedPensjon(
            SedType.P2000,
            fnrVoksen,
            eessiSaknr = sakId,
            krav = krav,
            sivilstand = sivilstand,
            statsborgerskap = statsborgerskap,
            pdlPerson = mockPerson,
            fdato = fdato
        ))

        val bucland = if (land === "SWE") "SE" else "NO"
        initCommonMocks(sed, alleDocs, documentFiler, bucLand = bucland)

        if (bestemSak != null) {
            every { bestemSakKlient.kallBestemSak(any()) } returns bestemSak
        }

        val (journalpost, _) = initJournalPostRequestSlot(forsokFerdigStilt)

        val hendelse = createHendelseJson(SedType.P2000, P_BUC_01, fnrVoksen)

        val meldingSlot = mutableListOf<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        val kravmeldingSlot = mutableListOf<String>()
        every { kravInitHandlerKafka.sendDefault(any(), capture(kravmeldingSlot)).get() } returns mockk()

        every { norg2Service.hentArbeidsfordelingEnhet(any()) } returns PENSJON_UTLAND

        sakId?.let {
            every { etterlatteService.hentGjennySak(any()) } returns mockHentGjennySak(it)
        } ?: run {
            every { etterlatteService.hentGjennySak(any()) } returns mockHentGjennySakMedError()
        }

        val journalpostRequest = slot<OpprettJournalpostRequest>()

        when (hendelseType) {
            SENDT -> sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
            MOTTATT -> mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))
        }

        createMockedJournalPostWithOppgave(journalpostRequest, hendelse, hendelseType)

        val kravMeldingList: List<BehandleHendelseModel> = kravmeldingSlot.map {
            mapJsonToAny(it)
        }

        val oppgaveMeldingList: List<OppgaveMelding> = meldingSlot.map {
            mapJsonToAny(it)
        }
        block(TestResult(journalpost.captured, oppgaveMeldingList, kravMeldingList))

        if (fnrVoksen != null) verify { personService.hentPerson(any()) }

        verify(exactly = 1) { euxKlient.hentBuc(any()) }
        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
        verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

        clearAllMocks()
    }

    private fun getDokumentfilerUtenGyldigVedlegg(): SedDokumentfiler =
        mapJsonToAny(javaClass.getResource("/pdf/pdfResponseMedUgyldigVedlegg.json")!!.readText())

    data class TestResult(
        val opprettJournalpostRequest: OpprettJournalpostRequest,
        val oppgaveMeldingList: List<OppgaveMelding>,
        val kravMeldingList: List<BehandleHendelseModel>? = null
    ) {
        val oppgaveMeldingUgyldig = if (oppgaveMeldingList.size == 2) oppgaveMeldingList.first() else null
        val oppgaveMelding =
            if (oppgaveMeldingList.size == 2) oppgaveMeldingList.last() else if (oppgaveMeldingList.isNotEmpty()) oppgaveMeldingList.first() else null
    }
}
