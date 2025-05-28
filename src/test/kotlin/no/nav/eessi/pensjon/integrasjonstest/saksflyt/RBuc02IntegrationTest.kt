package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.*
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.R_BUC_02
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.R005
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.*
import no.nav.eessi.pensjon.eux.model.buc.SakType.GJENLEV
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.journalforing.OpprettJournalPostResponse
import no.nav.eessi.pensjon.journalforing.OpprettJournalpostRequest
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPDLPerson
import no.nav.eessi.pensjon.personidentifisering.relasjoner.RelasjonTestBase
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

@DisplayName("R_BUC_01 – IntegrationTest")
internal class RBuc02IntegrationTest : JournalforingTestBase() {

        @BeforeEach
        fun beforetest() {
            every { etterlatteService.hentGjennySak(any()) } returns Result.failure(IllegalStateException("Uventet statuskode: 500 for sakId"))
            every { gcpStorageService.hentFraGjenny(any()) } returns null

        }

        //I denne testen har vi alle verdier som trengs for å automatisk journalføre sed R005. Vi oppretter en BEHANDLE_SED oppgave siden hendelsestypen er MOTTATT
        @Test
        fun `Gitt at det kommer inn en R005 og det finnes en GJENLEVENDE på sed så skal seden journalføres på den gjenlevende`() {
            val pdlPerson = IdentifisertPDLPerson(
                aktoerId = "123654987321",
                landkode = "NOR",
                geografiskTilknytning = null,
                personRelasjon = sedPersonRelasjon(Relasjon.GJENLEVENDE, "11067122781")
            )

            val forsoekFerdigstillSlot = slot<Boolean>()
            val journalpostSlot = slot<OpprettJournalpostRequest>()

            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokfilerUtenVedlegg()
            every { personService.hentPerson(NorskIdent(FNR_VOKSEN_UNDER_62)) } returns
                    JournalforingTestBase().createBrukerWith(
                        FNR_VOKSEN_UNDER_62,
                        aktorId = AKTOER_ID
                    )

            every { journalpostKlient.opprettJournalpost(capture(journalpostSlot), capture(forsoekFerdigstillSlot), any()) } returns mockk<OpprettJournalPostResponse>()
                    .apply {
                every { journalpostferdigstilt } returns true
                every { journalpostId } returns "123456789"
            }

            val capturedMelding = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(capturedMelding)).get() } returns mockk()

            journalforingService.journalfor(
                sedHendelse = sedHendelse(),
                hendelseType = MOTTATT,
                identifisertPerson = pdlPerson,
                fdato = LocalDate.of(1971, 6, 11),
                saksInfoSamlet = SaksInfoSamlet(
                    saksIdFraSed = "25432122",
                    sakInformasjonFraPesys = SakInformasjon(
                        sakId = "654321",
                        sakType = GJENLEV,
                        sakStatus = LOPENDE,
                        saksbehandlendeEnhetId = "4812",
                        nyopprettet = false
                    ),
                    saktypeFraSed = GJENLEV
                ),
                harAdressebeskyttelse = false,
                identifisertePersoner = 2,
                navAnsattInfo = null,
                currentSed = RelasjonTestBase().createR005(
                    forsikretFnr = null,
                    forsikretTilbakekreving = "forsikret_person",
                    annenPersonFnr = FNR_VOKSEN_UNDER_62,
                )
            )

            verify(exactly = 1) { oppgaveHandlerKafka.sendDefault(any(), any()) }

            capturedMelding.captured
            assertEquals(
                """
            {
              "sedType" : "R005",
              "journalpostId" : "123456789",
              "tildeltEnhetsnr" : "4862",
              "aktoerId" : "123654987321",
              "rinaSakId" : "123456",
              "hendelseType" : "MOTTATT",
              "filnavn" : null,
              "oppgaveType" : "BEHANDLE_SED",
              "tema" : "PEN",
              "sendeAdvarsel" : false
            }
        """.trimIndent(), capturedMelding.captured)

            assertEquals(PENSJON, journalpostSlot.captured.tema)
            assertEquals(FNR_VOKSEN_UNDER_62, journalpostSlot.captured.bruker?.id)
            assertEquals(Enhet.NFP_UTLAND_AALESUND, journalpostSlot.captured.journalfoerendeEnhet)

        }

    //I denne testen mangler det verdier som trengs for å automatisk journalføre sed R005. Vi oppretter derfor en JOURNALFORINGs oppgave
    @Test
    fun `Gitt at det kommer inn en R005 og det finnes en FORSIKRET og en GJENLEVENDE på sed så skal seden journalføres på den gjenlevende`() {
        val pdlPerson = IdentifisertPDLPerson(
            aktoerId = "123654987321",
            landkode = "NOR",
            geografiskTilknytning = null,
            personRelasjon = sedPersonRelasjon(Relasjon.GJENLEVENDE, "11067122781")
        )

        val forsoekFerdigstillSlot = slot<Boolean>()
        val journalpostSlot = slot<OpprettJournalpostRequest>()

        every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokfilerUtenVedlegg()
        every { personService.hentPerson(NorskIdent(FNR_VOKSEN_UNDER_62)) } returns
                JournalforingTestBase().createBrukerWith(
                    FNR_VOKSEN_UNDER_62,
                    aktorId = AKTOER_ID
                )
        every { journalpostKlient.opprettJournalpost(capture(journalpostSlot), capture(forsoekFerdigstillSlot), any()) } returns mockk<OpprettJournalPostResponse>()
            .apply {
                every { journalpostferdigstilt } returns false
                every { journalpostId } returns "123456789"
            }

        val capturedMelding = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(capturedMelding)).get() } returns mockk()

        journalforingService.journalfor(
            sedHendelse = sedHendelse(),
            hendelseType = MOTTATT,
            identifisertPerson = pdlPerson,
            fdato = LocalDate.of(1971, 6, 11),
            saksInfoSamlet = SaksInfoSamlet(
                saksIdFraSed = "25432122",
                sakInformasjonFraPesys = SakInformasjon(
                    sakId = "654321",
                    sakType = GJENLEV,
                    sakStatus = LOPENDE,
                    saksbehandlendeEnhetId = "4812",
                    nyopprettet = false,
                ),
                saktypeFraSed = GJENLEV
            ),
            harAdressebeskyttelse = false,
            identifisertePersoner = 2,
            navAnsattInfo = null,
            currentSed = RelasjonTestBase().createR005(
                forsikretFnr = null,
                forsikretTilbakekreving = "forsikret_person",
                annenPersonFnr = FNR_VOKSEN_UNDER_62,
            )
        )

        verify(exactly = 1) { oppgaveHandlerKafka.sendDefault(any(), any()) }

        capturedMelding.captured
        assertEquals(
            """
            {
              "sedType" : "R005",
              "journalpostId" : "123456789",
              "tildeltEnhetsnr" : "4862",
              "aktoerId" : "123654987321",
              "rinaSakId" : "123456",
              "hendelseType" : "MOTTATT",
              "filnavn" : null,
              "oppgaveType" : "JOURNALFORING",
              "tema" : "PEN",
              "sendeAdvarsel" : false
            }
        """.trimIndent(), capturedMelding.captured)

        assertEquals(PENSJON, journalpostSlot.captured.tema)
        assertEquals(FNR_VOKSEN_UNDER_62, journalpostSlot.captured.bruker?.id)
        assertEquals(Enhet.NFP_UTLAND_AALESUND, journalpostSlot.captured.journalfoerendeEnhet)


    }
    @ParameterizedTest
    @DisplayName("hentIdentfisertPerson skal returnere riktig relasjon å journalføre på for R_BUC_02")
    @CsvSource(
        value = [
            "FORSIKRET, 09035225916, GJENLEVENDE, 11067122781, 11067122781, GJENLEVENDE",
            "FORSIKRET, 09035225916, FORSIKRET, 22117320034, null, null",
            "GJENLEVENDE, 11067122781, GJENLEVENDE, 12011577847, null, null"
            ],
        nullValues = ["null"]
    )
    fun `Gitt at det kommer en R004, R005 eller R006 i R_BUC_02 så skal identifisert person returnere gjenlevende`(personRelasjon: Relasjon, fnr: String, personRelasjonGjenlev: Relasjon, fnrGjenlev: String, forventetFnr: String?, forventetRelasjon: String?) {
        val identifisertePersonerISed = listOf(
            IdentifisertPDLPerson(AKTOER_ID, "NO", personRelasjon = sedPersonRelasjon(personRelasjon, fnr),fnr = Fodselsnummer.fra(fnr), geografiskTilknytning = null),
            IdentifisertPDLPerson(AKTOER_ID_2, "NO", personRelasjon = sedPersonRelasjon(personRelasjonGjenlev, fnrGjenlev), fnr = Fodselsnummer.fra(fnrGjenlev), geografiskTilknytning = null)
        )

        val sedRelasjoner = listOf(
            sedPersonRelasjon(personRelasjon, fnr),
            sedPersonRelasjon(personRelasjonGjenlev, fnrGjenlev)
        )

        val actual = personidentifiseringService.hentIdentifisertPerson(R_BUC_02, R005, MOTTATT, "123456", identifisertePersonerISed, sedRelasjoner)
        println("actual: $actual")

        assertEquals(forventetFnr, actual?.fnr?.value)
        assertEquals(forventetRelasjon, actual?.personRelasjon?.relasjon?.name)
    }

    private fun sedPersonRelasjon(relasjon: Relasjon, fnr: String) = SEDPersonRelasjon(
        fnr = Fodselsnummer.fra(fnr),
        relasjon = relasjon,
        saktype = GJENLEV,
        sedType = R005,
        sokKriterier = null,
        fdato = Fodselsnummer.fra(fnr)?.getBirthDate(),
        rinaDocumentId = "654321"
    )


    private fun sedHendelse(sektorkode: String? = "R", bucType: BucType? = R_BUC_02, sedType: SedType? = R005) : SedHendelse {
        return SedHendelse(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = sektorkode!!,
            bucType = bucType, rinaDokumentVersjon = "1", sedType = sedType
        )
    }

    private fun getDokfilerUtenVedlegg(): SedDokumentfiler {
        val dokumentfilerJson = javaClass.getResource("/pdf/pdfResponseUtenVedlegg.json")!!.readText()
        return mapJsonToAny(dokumentfilerJson)
    }

}