package no.nav.eessi.pensjon.oppgaverouting

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.models.JournalforingPerson
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.BucType.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.YtelseType.*
import no.nav.eessi.pensjon.services.norg2.Norg2ArbeidsfordelingItem
import no.nav.eessi.pensjon.services.norg2.Norg2Service
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class OppgaveRoutingServiceTest {

    @Spy
    private lateinit var norg2Service: Norg2Service

    private lateinit var routingService: OppgaveRoutingService

    @BeforeEach
    fun setup() {
        routingService = OppgaveRoutingService(norg2Service)
    }

    companion object {
        const val dummyTilknytning = "032342"
        val MANGLER_LAND = null as String?
        const val NORGE: String = "NOR"
        const val UTLAND: String = "SE"

        // NFP krets er en person mellom 18 og 60 år
        val alder18aar: LocalDate = LocalDate.now().minusYears(18).minusDays(1)
        val alder59aar: LocalDate = LocalDate.now().minusYears(60).plusDays(1)


        // NAY krets er en person yngre enn 18 år eller eldre enn 60 år
        val alder17aar: LocalDate = LocalDate.now().minusYears(18).plusDays(1)
        val alder60aar: LocalDate = LocalDate.now().minusYears(60)

    }

    private fun irrelevantDato() = LocalDate.MIN

    @Test
    fun `Gitt manglende fnr naar oppgave routes saa send oppgave til ID_OG_FORDELING`() {
        val enhet = routingService.route(JournalforingPerson(fdato = irrelevantDato(), landkode = MANGLER_LAND, geografiskTilknytning = dummyTilknytning), bucType = P_BUC_01)
        assertEquals(enhet, ID_OG_FORDELING)
    }

    @Test
    fun `Gitt manglende buc-type saa send oppgave til PENSJON_UTLAND`() {
        val enhet = routingService.route(JournalforingPerson(fnr = "010101010101", landkode = MANGLER_LAND, fdato = irrelevantDato()))
        assertEquals(enhet, PENSJON_UTLAND)
    }

    @Test
    fun `Gitt manglende ytelsestype for P_BUC_10 saa send oppgave til PENSJON_UTLAND`() {
        val enhet = routingService.route(JournalforingPerson(fnr = "010101010101", landkode = MANGLER_LAND, fdato = irrelevantDato()), bucType = P_BUC_10)
        assertEquals(enhet, PENSJON_UTLAND)
    }

    @Test
    fun `Routing for vanlige BUC'er`() {
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = irrelevantDato(), geografiskTilknytning = dummyTilknytning, fnr = "01010101010"), bucType = P_BUC_01))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(JournalforingPerson(fdato = irrelevantDato(), geografiskTilknytning = dummyTilknytning, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_01))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = irrelevantDato(), geografiskTilknytning = dummyTilknytning, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_01))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = irrelevantDato(), landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_01))

        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = irrelevantDato(), fnr = "01010101010"), bucType = P_BUC_02))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(JournalforingPerson(fdato = irrelevantDato(), landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_02))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = irrelevantDato(), landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_02))

        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = irrelevantDato(), fnr = "01010101010"), bucType = P_BUC_03))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(JournalforingPerson(fdato = irrelevantDato(), landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_03))
        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = irrelevantDato(), landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_03))

        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = irrelevantDato(), fnr = "01010101010"), bucType = P_BUC_04))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(JournalforingPerson(fdato = irrelevantDato(), landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_04))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = irrelevantDato(), landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_04))

        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, fnr = "01010101010"), bucType = P_BUC_05))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_05))
        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_05))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, fnr = "01010101010"), bucType = P_BUC_05))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_05))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_05))

        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, fnr = "01010101010"), bucType = P_BUC_06))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_06))
        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_06))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, fnr = "01010101010"), bucType = P_BUC_06))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_06))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_06))

        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, fnr = "01010101010"), bucType = P_BUC_07))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_07))
        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_07))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, fnr = "01010101010"), bucType = P_BUC_07))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_07))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_07))

        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, fnr = "01010101010"), bucType = P_BUC_08))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_08))
        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_08))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, fnr = "01010101010"), bucType = P_BUC_08))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_08))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_08))

        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, fnr = "01010101010"), bucType = P_BUC_09))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_09))
        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_09))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, fnr = "01010101010"), bucType = P_BUC_09))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_09))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_09))

        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = AP.name))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = AP.name))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = AP.name))

        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = AP.name))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = AP.name))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = AP.name))

        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = GP.name))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = GP.name))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = GP.name))

        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = GP.name))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = GP.name))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = GP.name))

        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = UT.name))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = UT.name))
        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = alder18aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = UT.name))

        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = UT.name))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = UT.name))
        assertEquals(UFORE_UTLAND, enhetFor(JournalforingPerson(fdato = alder17aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = UT.name))

        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder59aar, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = AP.name))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(JournalforingPerson(fdato = alder59aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = AP.name))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder59aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = AP.name))

        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder60aar, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = AP.name))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(JournalforingPerson(fdato = alder60aar, landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = AP.name))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder60aar, landkode = UTLAND, fnr = "01010101010"), bucType = P_BUC_10, ytelseType = AP.name))

        assertEquals(DISKRESJONSKODE, enhetFor(JournalforingPerson(fdato = alder60aar, geografiskTilknytning = dummyTilknytning, diskresjonskode = "SPSF", fnr = "01010101010"), bucType = P_BUC_01, ytelseType = AP.name))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(JournalforingPerson(fdato = alder60aar, diskresjonskode = "SPFO", landkode = NORGE, fnr = "01010101010"), bucType = P_BUC_03, ytelseType = UT.name))
        assertEquals(PENSJON_UTLAND, enhetFor(JournalforingPerson(fdato = alder60aar, landkode = UTLAND, diskresjonskode = "SPFO", fnr = "01010101010"), bucType = P_BUC_10, ytelseType = GP.name))
        assertEquals(DISKRESJONSKODE, enhetFor(JournalforingPerson(fdato = alder60aar, landkode = UTLAND, diskresjonskode = "SPSF", fnr = "01010101010"), bucType = P_BUC_10, ytelseType = GP.name))
    }

    @Test
    fun `hentNorg2Enhet for bosatt utland`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordelig0001result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Service).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(JournalforingPerson(
                fnr = "1208201515925",
                landkode = "SVE",
                fdato = LocalDate.of(1950,1,1)),
            P_BUC_01)
        val expected = PENSJON_UTLAND

        assertEquals(expected,actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordelig4803result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Service).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(JournalforingPerson(
                fnr = "1208201515925",
                geografiskTilknytning = "0322",
                landkode = "NOR",
                fdato = LocalDate.of(1950,1,1)),
            P_BUC_01)
        val expected = NFP_UTLAND_OSLO

        assertEquals(expected,actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt nord-Norge`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordelig4862result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Service).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(JournalforingPerson(
                fnr = "1208201515925",
                geografiskTilknytning = "1102",
                landkode = "NOR",
                fdato = LocalDate.of(1950,1,1)),
            P_BUC_01)
        val expected = NFP_UTLAND_AALESUND

        assertEquals(expected,actual)
    }

    @Test
    fun `hentNorg2Enhet for diskresjonkode`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordeling2103result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Service).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(JournalforingPerson(
                fnr = "1208201515925",
                geografiskTilknytning = "1102",
                landkode = "NOR",
                diskresjonskode = "SPSF",
                fdato = LocalDate.of(1950,1,1)),
            P_BUC_01)
        val expected = DISKRESJONSKODE

        assertEquals(expected,actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge feil buc`() {
       val actual = routingService.hentNorg2Enhet(JournalforingPerson(
               fnr = "1208201515925",
               geografiskTilknytning = "0322",
               landkode = "NOR",
               fdato = LocalDate.of(1950,1,1)),
            P_BUC_03)
       val expected = null

        assertEquals(expected,actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge mock feil mot Norg2`() {
        doReturn(listOf<Norg2ArbeidsfordelingItem>())
                .whenever(norg2Service).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(JournalforingPerson(
                fnr = "1208201515925",
                geografiskTilknytning = "0322",
                landkode = "NOR",
                fdato = LocalDate.of(1950,1,1)),
            P_BUC_01)
        val expected = null

        assertEquals(expected,actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge mock feil mot Norg2 error`() {
        doThrow(RuntimeException("dummy"))
                .whenever(norg2Service).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(JournalforingPerson(
                fnr = "1208201515925",
                geografiskTilknytning = "0322",
                landkode = "NOR",
                fdato = LocalDate.of(1950,1,1)),
            P_BUC_01)
        val expected = null

        assertEquals(expected,actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge med diskresjon`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordeling2103result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Service).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(JournalforingPerson(
                fnr = "1208201515925",
                geografiskTilknytning = "0322",
                landkode = "NOR",
                diskresjonskode = "SPSF",
                fdato = LocalDate.of(1950,1,1)),
            P_BUC_01)
        val expected = DISKRESJONSKODE

        assertEquals(expected,actual)
    }

    private fun enhetFor(person: JournalforingPerson,
                         bucType: BucType,
                         ytelseType: String? = null): OppgaveRoutingModel.Enhet {
        return routingService.route(person, bucType, ytelseType)
        }

    @Test
    fun testEnumEnhets() {

        assertEquals(PENSJON_UTLAND, OppgaveRoutingModel.Enhet.getEnhet("0001"))

        assertEquals(NFP_UTLAND_OSLO, OppgaveRoutingModel.Enhet.getEnhet("4803"))

        assertEquals(DISKRESJONSKODE, OppgaveRoutingModel.Enhet.getEnhet("2103"))

    }

    private fun getJsonFileFromResource(filename: String): String {
        return String(Files.readAllBytes(Paths.get("src/test/resources/norg2/$filename")))
    }

}
