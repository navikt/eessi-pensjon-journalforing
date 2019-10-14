package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.BucType.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.YtelseType.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals

class OppgaveRoutingServiceTest {

    val routingService = OppgaveRoutingService()

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
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_01, MANGLER_LAND, alleAldre(), geografiskTilknytning))
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

        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder18aar, geografiskTilknytning, AP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_10, NORGE, alder18aar, geografiskTilknytning, AP))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, UTLAND, alder18aar, geografiskTilknytning, AP))

        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder17aar, geografiskTilknytning, AP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_10, NORGE, alder17aar, geografiskTilknytning, AP))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, UTLAND, alder17aar, geografiskTilknytning, AP))

        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder18aar, geografiskTilknytning, GP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_10, NORGE, alder18aar, geografiskTilknytning, GP))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, UTLAND, alder18aar, geografiskTilknytning, GP))

        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder17aar, geografiskTilknytning, GP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_10, NORGE, alder17aar, geografiskTilknytning, GP))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, UTLAND, alder17aar, geografiskTilknytning, GP))

        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder18aar, geografiskTilknytning, UT))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(P_BUC_10, NORGE, alder18aar, geografiskTilknytning, UT))
        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_10, UTLAND, alder18aar, geografiskTilknytning, UT))

        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder17aar, geografiskTilknytning, UT))
        assertEquals(UFORE_UTLANDSTILSNITT, enhetFor(P_BUC_10, NORGE, alder17aar, geografiskTilknytning, UT))
        assertEquals(UFORE_UTLAND, enhetFor(P_BUC_10, UTLAND, alder17aar, geografiskTilknytning, UT))

        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder59aar, geografiskTilknytning, AP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_10, NORGE, alder59aar, geografiskTilknytning, AP))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, UTLAND, alder59aar, geografiskTilknytning, AP))

        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, MANGLER_LAND, alder60aar, geografiskTilknytning, AP))
        assertEquals(NFP_UTLAND_AALESUND, enhetFor(P_BUC_10, NORGE, alder60aar, geografiskTilknytning, AP))
        assertEquals(PENSJON_UTLAND, enhetFor(P_BUC_10, UTLAND, alder60aar, geografiskTilknytning, AP))
    }

    private fun enhetFor(bucType: BucType,
                         land: String?,
                         fodselsDato: String,
                         geografiskTilknytning: String,
                         ytelse: OppgaveRoutingModel.YtelseType? = null): OppgaveRoutingModel.Enhet {
        return routingService.route("01010101010", bucType, land, fodselsDato,geografiskTilknytning, ytelse)
                         }

}
