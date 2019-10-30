package no.nav.eessi.pensjon.oppgaverouting

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.BucType.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.YtelseType.*
import no.nav.eessi.pensjon.services.norg2.Diskresjonskode
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
        val enhet = routingService.route(bucType = P_BUC_01, landkode = MANGLER_LAND, fodselsDato = irrelevantDato(), geografiskTilknytning = dummyTilknytning)
        assertEquals(enhet, ID_OG_FORDELING)
    }

    @Test
    fun `Gitt manglende buc-type saa send oppgave til PENSJON_UTLAND`() {
        val enhet = routingService.route(navBruker = "010101010101", landkode = MANGLER_LAND, fodselsDato = irrelevantDato(), geografiskTilknytning = dummyTilknytning)
        assertEquals(enhet, PENSJON_UTLAND)
    }

    @Test
    fun `Gitt manglende ytelsestype for P_BUC_10 saa send oppgave til PENSJON_UTLAND`() {
        val enhet = routingService.route(navBruker = "010101010101", bucType = P_BUC_10, landkode = MANGLER_LAND, fodselsDato = irrelevantDato())
        assertEquals(enhet, PENSJON_UTLAND)
    }

    @Test
    fun `Routing for vanlige BUC'er`() {
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_01, fodselsDato = irrelevantDato(), geografiskTilknytning = dummyTilknytning))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(bucType = P_BUC_01, land = NORGE, fodselsDato = irrelevantDato(), geografiskTilknytning = dummyTilknytning))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_01, land = UTLAND, fodselsDato = irrelevantDato(), geografiskTilknytning = dummyTilknytning))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_01, land = UTLAND, fodselsDato = irrelevantDato()))

        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_02, fodselsDato = irrelevantDato()))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(bucType = P_BUC_02, land = NORGE, fodselsDato = irrelevantDato()))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_02, land = UTLAND, fodselsDato = irrelevantDato()))

        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_03, fodselsDato = irrelevantDato()))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(bucType = P_BUC_03, land = NORGE, fodselsDato = irrelevantDato()))
        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_03, land = UTLAND, fodselsDato = irrelevantDato()))

        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_04, fodselsDato = irrelevantDato()))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(bucType = P_BUC_04, land = NORGE, fodselsDato = irrelevantDato()))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_04, land = UTLAND, fodselsDato = irrelevantDato()))

        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_05, fodselsDato = alder18aar))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(bucType = P_BUC_05, land = NORGE, fodselsDato = alder18aar))
        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_05, land = UTLAND, fodselsDato = alder18aar))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_05, fodselsDato = alder17aar))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(bucType =  P_BUC_05, land = NORGE, fodselsDato = alder17aar))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_05, land = UTLAND, fodselsDato = alder17aar))

        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_06, fodselsDato = alder18aar))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(bucType = P_BUC_06, land = NORGE, fodselsDato = alder18aar))
        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_06, land = UTLAND, fodselsDato = alder18aar))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_06, fodselsDato = alder17aar))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(bucType = P_BUC_06,land = NORGE, fodselsDato = alder17aar))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_06, land = UTLAND, fodselsDato = alder17aar))

        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_07, fodselsDato = alder18aar))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(bucType = P_BUC_07, land = NORGE, fodselsDato = alder18aar))
        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_07, land = UTLAND, fodselsDato = alder18aar))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_07, fodselsDato = alder17aar))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(bucType = P_BUC_07, land = NORGE, fodselsDato = alder17aar))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_07, land = UTLAND, fodselsDato = alder17aar))

        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_08, fodselsDato = alder18aar))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(bucType = P_BUC_08, land = NORGE, fodselsDato = alder18aar))
        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_08, land = UTLAND, fodselsDato = alder18aar))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_08, fodselsDato = alder17aar))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(bucType = P_BUC_08, land = NORGE, fodselsDato = alder17aar))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_08, land = UTLAND, fodselsDato = alder17aar))

        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_09, fodselsDato = alder18aar))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(bucType = P_BUC_09, land = NORGE, fodselsDato = alder18aar))
        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_09, land = UTLAND, fodselsDato = alder18aar))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_09, fodselsDato = alder17aar))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(bucType = P_BUC_09, land = NORGE, fodselsDato = alder17aar))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_09, land = UTLAND, fodselsDato = alder17aar))

        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_10, fodselsDato = alder18aar, ytelse = AP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(bucType = P_BUC_10, land = NORGE, fodselsDato = alder18aar, ytelse = AP))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_10, land = UTLAND, fodselsDato = alder18aar, ytelse = AP))

        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_10, fodselsDato = alder17aar, ytelse = AP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(bucType = P_BUC_10, land = NORGE, fodselsDato = alder17aar, ytelse = AP))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_10, land = UTLAND, fodselsDato = alder17aar, ytelse = AP))

        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_10, fodselsDato = alder18aar, ytelse = GP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(bucType = P_BUC_10, land = NORGE, fodselsDato = alder18aar, ytelse = GP))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_10, land = UTLAND, fodselsDato = alder18aar, ytelse = GP))

        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_10, fodselsDato = alder17aar, ytelse = GP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(bucType = P_BUC_10, land = NORGE, fodselsDato = alder17aar, ytelse = GP))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_10, land = UTLAND, fodselsDato = alder17aar, ytelse = GP))

        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_10, fodselsDato = alder18aar, ytelse = UT))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(bucType = P_BUC_10, land = NORGE, fodselsDato = alder18aar, ytelse = UT))
        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_10, land = UTLAND, fodselsDato = alder18aar, ytelse = UT))

        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_10, fodselsDato = alder17aar, ytelse = UT))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(bucType = P_BUC_10, land = NORGE, fodselsDato = alder17aar, ytelse = UT))
        assertEquals(UFORE_UTLAND, enhetFor(bucType = P_BUC_10, land = UTLAND, fodselsDato = alder17aar, ytelse = UT))

        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_10, fodselsDato = alder59aar, ytelse = AP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(bucType = P_BUC_10, land = NORGE, fodselsDato = alder59aar, ytelse = AP))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_10, land = UTLAND, fodselsDato = alder59aar, ytelse = AP))

        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_10, fodselsDato = alder60aar, ytelse = AP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(bucType = P_BUC_10, land = NORGE, fodselsDato = alder60aar, ytelse = AP))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_10, land = UTLAND, fodselsDato = alder60aar, ytelse = AP))

        assertEquals(DISKRESJONSKODE, enhetFor(P_BUC_01, fodselsDato = alder60aar, geografiskTilknytning = dummyTilknytning, diskresjonskode = Diskresjonskode.SPSF, ytelse = AP))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(P_BUC_03, NORGE, alder60aar, diskresjonskode = Diskresjonskode.SPFO , ytelse = UT))
        assertEquals(PENSJON_UTLAND, enhetFor(bucType = P_BUC_10, land = UTLAND, fodselsDato = alder60aar, diskresjonskode = Diskresjonskode.SPFO, ytelse = GP))
        assertEquals(DISKRESJONSKODE, enhetFor(bucType = P_BUC_10, land = UTLAND, fodselsDato = alder60aar, diskresjonskode = Diskresjonskode.SPSF, ytelse = GP))
    }

    @Test
    fun `hentNorg2Enhet for bosatt utland`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordelig0001result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Service).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet("1208201515925",null,"SVE",P_BUC_01,null)
        val expected = PENSJON_UTLAND

        assertEquals(expected,actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordelig4803result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Service).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet("1208201515925","0322","NOR",P_BUC_01,null)
        val expected = NFP_UTLAND_OSLO

        assertEquals(expected,actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt nord-Norge`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordelig4862result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Service).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet("1208201515925","1102","NOR",P_BUC_01,null)
        val expected = NFP_UTLAND_AALESUND

        assertEquals(expected,actual)
    }

    @Test
    fun `hentNorg2Enhet for diskresjonkode`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordeling2103result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Service).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet("1208201515925","1102","NOR",P_BUC_01, Diskresjonskode.SPSF)
        val expected = DISKRESJONSKODE

        assertEquals(expected,actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge feil buc`() {
       val actual = routingService.hentNorg2Enhet("1208201515925","0322","NOR",P_BUC_03,null)
       val expected = null

        assertEquals(expected,actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge mock feil mot Norg2`() {
        doReturn(listOf<Norg2ArbeidsfordelingItem>())
                .whenever(norg2Service).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet("1208201515925","0322","NOR",P_BUC_01,null)
        val expected = null

        assertEquals(expected,actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge mock feil mot Norg2 error`() {
        doThrow(RuntimeException("dummy"))
                .whenever(norg2Service).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet("1208201515925","0322","NOR",P_BUC_01,null)
        val expected = null

        assertEquals(expected,actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge med diskresjon`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordeling2103result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Service).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet("1208201515925","0322","NOR",P_BUC_01, Diskresjonskode.SPSF)
        val expected = DISKRESJONSKODE

        assertEquals(expected,actual)
    }

    private fun enhetFor(bucType: BucType,
                         land: String? = null,
                         fodselsDato: LocalDate,
                         geografiskTilknytning: String? = null,
                         diskresjonskode: Diskresjonskode? = null,
                         ytelse: OppgaveRoutingModel.YtelseType? = null): OppgaveRoutingModel.Enhet {
        return routingService.route(
                "01010101010",
                bucType,
                land,
                fodselsDato,
                geografiskTilknytning,
                diskresjonskode,
                ytelse
            )
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
