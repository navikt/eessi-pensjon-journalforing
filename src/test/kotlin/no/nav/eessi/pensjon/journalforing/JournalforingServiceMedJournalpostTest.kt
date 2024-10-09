package no.nav.eessi.pensjon.journalforing

import io.mockk.*
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OppgaveMelding
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate

private const val AKTOERID = "12078945602"
private const val RINADOK_ID = "3123123"
private val LEALAUS_KAKE = Fodselsnummer.fra("22117320034")!!

internal class JournalforingServiceMedJournalpostTest : JournalforingServiceBase() {

    @Test
    fun `Sendt P6000 med all info for at forsoekFerdigstill returnerer true saa skal det populere Journalpostresponsen med pesys sakid`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_06_P6000.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        val saksInformasjon = SakInformasjon(sakId = "22874955", sakType = SakType.ALDER, sakStatus = SakStatus.LOPENDE)

        val requestSlot = slot<OpprettJournalpostRequest>()
        val forsoekFedrigstillSlot = slot<Boolean>()
        every {
            journalpostKlient.opprettJournalpost(
                capture(requestSlot),
                capture(forsoekFedrigstillSlot),
                any()
            )
        } returns mockk(relaxed = true)
        every { gcpStorageService.hentFraGjenny(any()) } returns null


        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            SaksInfoSamlet(saktype = SakType.ALDER, sakInformasjon = saksInformasjon),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            currentSed = SED(type = SedType.P6000)
        )
        val journalpostRequest = requestSlot.captured
        val erMuligAaFerdigstille = forsoekFedrigstillSlot.captured

        assertEquals("22874955", journalpostRequest.sak?.fagsakid)
        assertEquals(true, erMuligAaFerdigstille)

    }

    private fun navAnsattInfo(): Pair<String, Enhet?> = Pair("Z990965", Enhet.NFP_UTLAND_AALESUND)

    @Test
    fun `Sendt P6000 med manglende saksinfo skal returnere false på forsoekFerdigstill`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_06_P6000.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )

        val forsoekFerdigstillSlot = slot<Boolean>()
        every { journalpostKlient.opprettJournalpost(any(), capture(forsoekFerdigstillSlot), any()) } returns mockk(relaxed = true)
        every { gcpStorageService.hentFraGjenny(any()) } returns null
        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            currentSed = SED(type = SedType.P6000)
        )
        val erMuligAaFerdigstille = forsoekFerdigstillSlot.captured

        assertEquals(false, erMuligAaFerdigstille)
    }

    @ParameterizedTest
    @EnumSource(BucType::class, names = ["R_BUC_02", "P_BUC_06", "P_BUC_09"])
    fun `Sendt SED med manglende bruker skal lage journalpost`(bucType: BucType) {
        val generiskSED = SedHendelse.fromJson(javaClass.getResource("/eux/hendelser/P_BUC_06_P6000.json")!!.readText())
            .copy(bucType = bucType, navBruker = null)

        val forsoekFerdigstillSlot = slot<Boolean>()
        every { journalpostKlient.opprettJournalpost(any(), capture(forsoekFerdigstillSlot), any()) } returns mockk(relaxed = true)

        journalforingService.journalfor(
            sedHendelse = generiskSED,
            hendelseType = HendelseType.SENDT,
            identifisertPerson = null,
            fdato = null,
            identifisertePersoner = 0,
            navAnsattInfo = navAnsattInfo(),
            currentSed = SED(type = generiskSED.sedType!!)
        )

        verify(exactly = 1) { journalpostKlient.opprettJournalpost(any(), any(), any()) }
        assertEquals(false, forsoekFerdigstillSlot.captured)
    }

    @Test
    fun `Sendt P_BUC_2 2100 med omstilling skal lage journalpost`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_02_P2100.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val forsoekFerdigstillSlot = slot<Boolean>()
        val journalpostSlot = slot<OpprettJournalpostRequest>()

        every { journalpostKlient.opprettJournalpost(capture(journalpostSlot), capture(forsoekFerdigstillSlot), any()) } returns mockk(relaxed = true)
        every { gcpStorageService.gjennyFinnes(sedHendelse.rinaSakId) } returns true
        every { gcpStorageService.hentFraGjenny(any()) } returns """{ "sakId" : "147730","sakType" : "EYO"}""".trimIndent()

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )
        journalforingService.journalfor(
            sedHendelse,
            HendelseType.SENDT,
            identifisertPerson,
            LocalDate.now(),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            currentSed = SED(type = SedType.P2100)
        )

        // journalposten opprettes
        verify(atLeast = 1) { journalpostKlient.opprettJournalpost(any(), any(), any()) }
        // journalposten har tema omstilling
        assertEquals(journalpostSlot.captured.tema, Tema.OMSTILLING)
        assertEquals(true, forsoekFerdigstillSlot.captured)
    }

    @Test
    fun `Mottatt P_BUC_2 2100 med omstilling skal lage journalpost uten ferdigstilling`() {
        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_02_P2100.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)

        val forsoekFerdigstillSlot = slot<Boolean>()
        val journalpostSlot = slot<OpprettJournalpostRequest>()

        every { journalpostKlient.opprettJournalpost(capture(journalpostSlot), capture(forsoekFerdigstillSlot), any()) } returns mockk(relaxed = true)
        every { gcpStorageService.gjennyFinnes(sedHendelse.rinaSakId) } returns true
        every { gcpStorageService.hentFraGjenny(any()) } returns """{ "sakId" : "147730","sakType" : "EYO"}""".trimIndent()

        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )
        journalforingService.journalfor(
            sedHendelse,
            HendelseType.MOTTATT,
            identifisertPerson,
            LocalDate.now(),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            currentSed = SED(type = SedType.P2100)
        )

        // journalposten opprettes
        verify(atLeast = 1) { journalpostKlient.opprettJournalpost(any(), any(), any()) }
        // journalposten har tema omstilling
        assertEquals(journalpostSlot.captured.tema, Tema.OMSTILLING)
        assertEquals(false, forsoekFerdigstillSlot.captured)
    }

    @Test
    fun `Innkommende P2000 fra utlanded som oppfyller alle krav til maskinell journalføring skal opprette behandle SED oppgave`() {

        val hendelse = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_SE.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(hendelse)
        val kravtype = "01"
        val identifisertPerson = identifisertPersonPDL(
            AKTOERID,
            sedPersonRelasjon(LEALAUS_KAKE, Relasjon.FORSIKRET, rinaDocumentId = RINADOK_ID)
        )
        val saksInformasjon = SakInformasjon(sakId = "22874955", sakType = SakType.ALDER, sakStatus = SakStatus.LOPENDE)

        val requestSlot = slot<OpprettJournalpostRequest>()
        every {
            journalpostKlient.opprettJournalpost(
                capture(requestSlot),
                any(),
                any()
            )
        } returns mockk<OpprettJournalPostResponse>(relaxed = true).apply {
            val opprettJournalPostResponse: OpprettJournalPostResponse =
                mockk<OpprettJournalPostResponse>(relaxed = true).apply {
                    every { journalpostferdigstilt } returns true
                }
            every { journalpostKlient.opprettJournalpost(any(), any(), any()) } returns opprettJournalPostResponse
        }

        val capturedMelding = slot<OppgaveMelding>()
        justRun { oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(capture(capturedMelding)) }
        justRun { kravHandeler.putKravInitMeldingPaaKafka(any()) }
        every { gcpStorageService.hentFraGjenny(any()) } returns null

        journalforingService.journalfor(
            sedHendelse,
            HendelseType.MOTTATT,
            identifisertPerson,
            LEALAUS_KAKE.getBirthDate(),
            SaksInfoSamlet(saktype = SakType.ALDER, sakInformasjon = saksInformasjon),
            identifisertePersoner = 1,
            navAnsattInfo = navAnsattInfo(),
            currentSed = mockk<P2000>(relaxed = true).apply {
                every { nav?.bruker?.person?.sivilstand } returns listOf(SivilstandItem("01-01-2023"))
                every { nav?.krav } returns mapJsonToAny<Krav>("""{"type":"$kravtype"}""")
                every { nav?.bruker?.person?.statsborgerskap } returns listOf(StatsborgerskapItem("NO"))
            },
        )
        capturedMelding.captured
        assertEquals(
            """
            {
              "sedType" : "P2000",
              "journalpostId" : "",
              "tildeltEnhetsnr" : "0001",
              "aktoerId" : "12078945602",
              "rinaSakId" : "147729",
              "hendelseType" : "MOTTATT",
              "filnavn" : null,
              "oppgaveType" : "BEHANDLE_SED",
              "tema" : "UFO"
            }
        """.trimIndent(), capturedMelding.captured.toJson()
        )
    }
}