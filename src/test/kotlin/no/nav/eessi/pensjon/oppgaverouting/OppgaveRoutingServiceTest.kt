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
import no.nav.eessi.pensjon.services.norg2.Norg2ArbeidsfordelingItem
import no.nav.eessi.pensjon.services.norg2.Norg2Service
import no.nav.eessi.pensjon.services.norg2.Diskresjonskode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

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
        val MANGLER_LAND = null as String?
        const val NORGE: String = "NOR"
        const val UTLAND: String = "SE"

        private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMyy")
        // NFP krets er en person mellom 18 og 60 år
        val alder18aar: String = LocalDate.now().minusYears(18).minusDays(1).format(dateFormat)
        val alder59aar: String = LocalDate.now().minusYears(60).plusDays(1).format(dateFormat)


        // NAY krets er en person yngre enn 18 år eller eldre enn 60 år
        val alder17aar: String = LocalDate.now().minusYears(18).plusDays(1).format(dateFormat)
        val alder60aar: String = LocalDate.now().minusYears(60).minusDays(1).format(dateFormat)

        val geografiskTilknytning = "032342"
    }

    private fun alleAldre() = "0101" + Random.nextInt(0,100).toString()

    @Test
    fun `Gitt manglende fnr når oppgave routes så send oppgave til ID_OG_FORDELING`() {
        val enhet = routingService.route(null, P_BUC_01, MANGLER_LAND, alleAldre(), geografiskTilknytning, null)
        assertEquals(enhet, ID_OG_FORDELING)
    }

    @Test
    fun `Gitt manglende buc-type så send oppgave til PENSJON_UTLAND`() {
        val enhet = routingService.route("010101010101", null, MANGLER_LAND, alleAldre(), geografiskTilknytning,null)
        assertEquals(enhet, PENSJON_UTLAND)
    }

    @Test
    fun `Gitt manglende ytelsestype for P_BUC_10 så send oppgave til PENSJON_UTLAND`() {
        val enhet = routingService.route("010101010101", P_BUC_10, MANGLER_LAND, alleAldre(), geografiskTilknytning,null)
        assertEquals(enhet, PENSJON_UTLAND)
    }

    @Test
    fun `Routing for vanlige BUC'er`() {
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_01, MANGLER_LAND, alleAldre(),  geografiskTilknytning))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_01, NORGE, alleAldre(), geografiskTilknytning))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_01, UTLAND, alleAldre(), geografiskTilknytning))

        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_02, MANGLER_LAND, alleAldre(), geografiskTilknytning))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_02, NORGE, alleAldre(), geografiskTilknytning))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_02, UTLAND, alleAldre(), geografiskTilknytning))

        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_03, MANGLER_LAND, alleAldre(), geografiskTilknytning))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(P_BUC_03, NORGE, alleAldre(), geografiskTilknytning))
        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_03, UTLAND, alleAldre(), geografiskTilknytning))

        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_04, MANGLER_LAND, alleAldre(), geografiskTilknytning))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_04, NORGE, alleAldre(), geografiskTilknytning))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_04, UTLAND, alleAldre(), geografiskTilknytning))

        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_05, MANGLER_LAND, alder18aar, geografiskTilknytning))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(P_BUC_05, NORGE, alder18aar, geografiskTilknytning))
        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_05, UTLAND, alder18aar, geografiskTilknytning))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_05, MANGLER_LAND, alder17aar, geografiskTilknytning))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_05, NORGE, alder17aar, geografiskTilknytning))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_05, UTLAND, alder17aar, geografiskTilknytning))

        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_06, MANGLER_LAND, alder18aar, geografiskTilknytning))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(P_BUC_06, NORGE, alder18aar, geografiskTilknytning))
        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_06, UTLAND, alder18aar, geografiskTilknytning))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_06, MANGLER_LAND, alder17aar, geografiskTilknytning))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_06, NORGE, alder17aar, geografiskTilknytning))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_06, UTLAND, alder17aar, geografiskTilknytning))

        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_07, MANGLER_LAND, alder18aar, geografiskTilknytning))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(P_BUC_07, NORGE, alder18aar, geografiskTilknytning))
        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_07, UTLAND, alder18aar, geografiskTilknytning))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_07, MANGLER_LAND, alder17aar, geografiskTilknytning))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_07, NORGE, alder17aar, geografiskTilknytning))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_07, UTLAND, alder17aar, geografiskTilknytning))

        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_08, MANGLER_LAND, alder18aar, geografiskTilknytning))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(P_BUC_08, NORGE, alder18aar, geografiskTilknytning))
        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_08, UTLAND, alder18aar, geografiskTilknytning))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_08, MANGLER_LAND, alder17aar, geografiskTilknytning))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_08, NORGE, alder17aar, geografiskTilknytning))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_08, UTLAND, alder17aar, geografiskTilknytning))

        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_09, MANGLER_LAND, alder18aar, geografiskTilknytning))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(P_BUC_09, NORGE, alder18aar, geografiskTilknytning))
        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_09, UTLAND, alder18aar, geografiskTilknytning))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_09, MANGLER_LAND, alder17aar, geografiskTilknytning))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_09, NORGE, alder17aar, geografiskTilknytning))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_09, UTLAND, alder17aar, geografiskTilknytning))

        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder18aar, geografiskTilknytning, null, AP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_10, NORGE, alder18aar, geografiskTilknytning, null, AP))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, UTLAND, alder18aar, geografiskTilknytning, null, AP))

        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder17aar, geografiskTilknytning, null, AP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_10, NORGE, alder17aar, geografiskTilknytning, null, AP))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, UTLAND, alder17aar, geografiskTilknytning, null, AP))

        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder18aar, geografiskTilknytning, null, GP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_10, NORGE, alder18aar, geografiskTilknytning, null, GP))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, UTLAND, alder18aar, geografiskTilknytning, null, GP))

        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder17aar, geografiskTilknytning, null, GP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_10, NORGE, alder17aar, geografiskTilknytning, null, GP))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, UTLAND, alder17aar, geografiskTilknytning, null, GP))

        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder18aar, geografiskTilknytning, null, UT))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(P_BUC_10, NORGE, alder18aar, geografiskTilknytning, null, UT))
        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_10, UTLAND, alder18aar, geografiskTilknytning, null, UT))

        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder17aar, geografiskTilknytning, null, UT))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(P_BUC_10, NORGE, alder17aar, geografiskTilknytning, null, UT))
        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_10, UTLAND, alder17aar, geografiskTilknytning, null, UT))

        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder59aar, geografiskTilknytning, null, AP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_10, NORGE, alder59aar, geografiskTilknytning, null, AP))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, UTLAND, alder59aar, geografiskTilknytning, null, AP))

        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder60aar, geografiskTilknytning, null, AP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_10, NORGE, alder60aar, geografiskTilknytning, null,AP))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, UTLAND, alder60aar, geografiskTilknytning, null, AP))

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
                         land: String?,
                         fodselsDato: String,
                         geografiskTilknytning: String,
                         diskresjonskode: Diskresjonskode? =null,
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

    fun getJsonFileFromResource(filename: String): String {
        return String(Files.readAllBytes(Paths.get("src/test/resources/norg2/$filename")))
    }

}
