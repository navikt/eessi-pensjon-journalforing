package no.nav.eessi.pensjon.oppgaverouting

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.BucType.H_BUC_07
import no.nav.eessi.pensjon.models.BucType.P_BUC_01
import no.nav.eessi.pensjon.models.BucType.P_BUC_02
import no.nav.eessi.pensjon.models.BucType.P_BUC_03
import no.nav.eessi.pensjon.models.BucType.P_BUC_04
import no.nav.eessi.pensjon.models.BucType.P_BUC_05
import no.nav.eessi.pensjon.models.BucType.P_BUC_06
import no.nav.eessi.pensjon.models.BucType.P_BUC_07
import no.nav.eessi.pensjon.models.BucType.P_BUC_08
import no.nav.eessi.pensjon.models.BucType.P_BUC_09
import no.nav.eessi.pensjon.models.BucType.P_BUC_10
import no.nav.eessi.pensjon.models.BucType.R_BUC_02
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.Enhet.DISKRESJONSKODE
import no.nav.eessi.pensjon.models.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.models.Enhet.NFP_UTLAND_AALESUND
import no.nav.eessi.pensjon.models.Enhet.NFP_UTLAND_OSLO
import no.nav.eessi.pensjon.models.Enhet.OKONOMI_PENSJON
import no.nav.eessi.pensjon.models.Enhet.PENSJON_UTLAND
import no.nav.eessi.pensjon.models.Enhet.UFORE_UTLAND
import no.nav.eessi.pensjon.models.Enhet.UFORE_UTLANDSTILSNITT
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.PersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
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
    private lateinit var norg2Klient: Norg2Klient

    private lateinit var routingService: OppgaveRoutingService

    @BeforeEach
    fun setup() {
        routingService = OppgaveRoutingService(norg2Klient)
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
        val enhet = routingService.route(OppgaveRoutingRequest(fdato = irrelevantDato(), landkode = MANGLER_LAND, geografiskTilknytning = dummyTilknytning, bucType = P_BUC_01, hendelseType = HendelseType.SENDT))
        assertEquals(enhet, ID_OG_FORDELING)
    }

    @Test
    fun `Gitt manglende buc-type saa send oppgave til PENSJON_UTLAND`() {
        val enhet = routingService.route(OppgaveRoutingRequest(aktorId = "010101010101", fdato = irrelevantDato(), landkode = MANGLER_LAND, hendelseType = HendelseType.SENDT))
        assertEquals(enhet, PENSJON_UTLAND)
    }

    @Test
    fun `Gitt manglende ytelsestype for P_BUC_10 saa send oppgave til PENSJON_UTLAND`() {
        val enhet = routingService.route(OppgaveRoutingRequest(aktorId = "010101010101", fdato = irrelevantDato(), landkode = MANGLER_LAND, bucType = P_BUC_10, hendelseType = HendelseType.SENDT))
        assertEquals(enhet, PENSJON_UTLAND)
    }

    @Test
    fun `Routing for mottatt H_BUC_07`() {
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = H_BUC_07, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, bucType = H_BUC_07, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = H_BUC_07, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_OSLO, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, landkode = NORGE, bucType = H_BUC_07, hendelseType = HendelseType.SENDT)))

        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(fdato = alder60aar, landkode = NORGE, bucType = H_BUC_07, hendelseType = HendelseType.SENDT)))

    }

    // ved bruk av fil kan jeg bruke denne: R_BUC_02-R005-AP.json
    @Test
    fun `Routing av mottatte sed R005 på R_BUC_02 ingen ytelse`() {
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(
                aktorId = "01010101010",
                fdato = irrelevantDato(),
                landkode = NORGE,
                geografiskTilknytning = dummyTilknytning,
                bucType = R_BUC_02,
                hendelseType = HendelseType.MOTTATT,
                sakInformasjon = null,
                identifisertPerson = mockerEnPerson()
        )))
    }

    @Test
    fun `Routing av mottatte sed R005 på R_BUC_02 alderpensjon ytelse`() {
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(
                aktorId = "01010101010",
                fdato = irrelevantDato(),
                landkode = UTLAND,
                geografiskTilknytning = dummyTilknytning,
                bucType = R_BUC_02,
                ytelseType = YtelseType.ALDER,
                hendelseType = HendelseType.MOTTATT,
                sakInformasjon = null,
                identifisertPerson = mockerEnPerson()
        )))
    }

    @Test
    fun `Routing av mottatte sed R005 på R_BUC_02 uforepensjon ytelse`() {
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(
                aktorId = "01010101010",
                fdato = irrelevantDato(),
                landkode = UTLAND,
                geografiskTilknytning = dummyTilknytning,
                bucType = R_BUC_02,
                ytelseType = YtelseType.UFOREP,
                hendelseType = HendelseType.MOTTATT,
                sakInformasjon = null,
                identifisertPerson = mockerEnPerson()
        )))
    }

    @Test
    fun `Routing av mottatte sed R004 på R_BUC_02 skal gi en routing til UFORE_UTLAND`() {
        assertEquals(OKONOMI_PENSJON, routingService.route(OppgaveRoutingRequest(
                aktorId = "01010101010",
                fdato = irrelevantDato(),
                landkode = UTLAND,
                geografiskTilknytning = dummyTilknytning,
                bucType = R_BUC_02,
                ytelseType = YtelseType.UFOREP,
                sedType = SedType.R004,
                hendelseType = HendelseType.MOTTATT,
                sakInformasjon = null,
                identifisertPerson = mockerEnPerson()
        )))
    }

    @Test
    fun `Routing av mottatte sed R004 på R_BUC_02 ukjent ident`() {
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(
                aktorId = null,
                fdato = irrelevantDato(),
                landkode = UTLAND,
                geografiskTilknytning = dummyTilknytning,
                bucType = R_BUC_02,
                ytelseType = YtelseType.UFOREP,
                sedType = SedType.R004,
                hendelseType = HendelseType.MOTTATT,
                sakInformasjon = null)))
    }

    @Test
    fun `Routing av mottatte sed R_BUC_02 med mer enn én person routes til ID_OG_FORDELING`() {
        val forsikret = IdentifisertPerson(
                "123",
                "Testern",
                null,
                null,
                "010",
                PersonRelasjon("12345678910", Relasjon.FORSIKRET))
        val avod = IdentifisertPerson(
                "234",
                "Avdod",
                null,
                null,
                "010",
                PersonRelasjon("22345678910", Relasjon.AVDOD))
        forsikret.personListe = listOf(forsikret, avod)

        val enhetresult = routingService.route(OppgaveRoutingRequest(
                aktorId = "123123123",
                fdato = irrelevantDato(),
                landkode = null,
                geografiskTilknytning = dummyTilknytning,
                bucType = R_BUC_02,
                ytelseType = YtelseType.ALDER,
                sedType = SedType.R005,
                hendelseType = HendelseType.MOTTATT,
                sakInformasjon = null,
                identifisertPerson = forsikret))

        assertEquals(ID_OG_FORDELING, enhetresult)

    }


    // ved bruk av fil kan jeg bruke denne: R_BUC_02-R005-AP.json
    @Test
    fun `Routing av utgående seder i R_BUC_02`() {
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(
                aktorId = "01010101010",
                fdato = irrelevantDato(),
                landkode = NORGE,
                geografiskTilknytning = dummyTilknytning,
                bucType = R_BUC_02,
                sedType = SedType.R005,
                hendelseType = HendelseType.SENDT,
                sakInformasjon = null,
                identifisertPerson = mockerEnPerson()
        )))

        assertEquals(OKONOMI_PENSJON, routingService.route(OppgaveRoutingRequest(
                aktorId = "01010101010",
                fdato = irrelevantDato(),
                landkode = NORGE,
                geografiskTilknytning = dummyTilknytning,
                bucType = R_BUC_02,
                sedType = SedType.R004,
                hendelseType = HendelseType.SENDT,
                sakInformasjon = null,
                identifisertPerson = mockerEnPerson()
        )))

        assertEquals(OKONOMI_PENSJON, routingService.route(OppgaveRoutingRequest(
                aktorId = "01010101010",
                fdato = irrelevantDato(),
                landkode = UTLAND,
                geografiskTilknytning = dummyTilknytning,
                bucType = R_BUC_02,
                sedType = SedType.R004,
                hendelseType = HendelseType.SENDT,
                sakInformasjon = null,
                identifisertPerson = mockerEnPerson()
        )))

        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(
                aktorId = "01010101010",
                fdato = irrelevantDato(),
                landkode = UTLAND,
                geografiskTilknytning = dummyTilknytning,
                bucType = R_BUC_02,
                sedType = SedType.R005,
                hendelseType = HendelseType.SENDT,
                sakInformasjon = null,
                identifisertPerson = mockerEnPerson()
        )))

    }

    @Test
    fun `Routing for P_BUC_02'er`() {

        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, ytelseType = YtelseType.GJENLEV)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, ytelseType = YtelseType.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.LOPENDE))))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, ytelseType = YtelseType.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.AVSLUTTET))))

        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, ytelseType = YtelseType.BARNEP)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, ytelseType = YtelseType.GJENLEV)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, ytelseType = YtelseType.ALDER)))


        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, ytelseType = YtelseType.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, ytelseType = YtelseType.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.LOPENDE), hendelseType = HendelseType.SENDT)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, ytelseType = YtelseType.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.AVSLUTTET), hendelseType = HendelseType.SENDT)))


        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, ytelseType = YtelseType.BARNEP, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, ytelseType = YtelseType.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, ytelseType = YtelseType.ALDER, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, ytelseType = YtelseType.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.LOPENDE))))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, ytelseType = YtelseType.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.AVSLUTTET))))

        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, hendelseType = HendelseType.SENDT)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, ytelseType = YtelseType.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.LOPENDE), hendelseType = HendelseType.SENDT)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, ytelseType = YtelseType.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.AVSLUTTET), hendelseType = HendelseType.SENDT)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, ytelseType = YtelseType.BARNEP, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, ytelseType = YtelseType.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, ytelseType = YtelseType.ALDER, hendelseType = HendelseType.SENDT)))

    }

    @Test
    fun `Routing for P_BUC_10'er`() {

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = P_BUC_10, ytelseType = YtelseType.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = P_BUC_10, ytelseType = YtelseType.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = UTLAND, bucType = P_BUC_10, ytelseType = YtelseType.ALDER, hendelseType = HendelseType.SENDT)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, bucType = P_BUC_10, ytelseType = YtelseType.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = NORGE, bucType = P_BUC_10, ytelseType = YtelseType.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = UTLAND, bucType = P_BUC_10, ytelseType = YtelseType.ALDER, hendelseType = HendelseType.SENDT)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = P_BUC_10, ytelseType = YtelseType.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = P_BUC_10, ytelseType = YtelseType.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = UTLAND, bucType = P_BUC_10, ytelseType = YtelseType.GJENLEV, hendelseType = HendelseType.SENDT)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, bucType = P_BUC_10, ytelseType = YtelseType.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = NORGE, bucType = P_BUC_10, ytelseType = YtelseType.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = UTLAND, bucType = P_BUC_10, ytelseType = YtelseType.GJENLEV, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = P_BUC_10, ytelseType = YtelseType.UFOREP, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = P_BUC_10, ytelseType = YtelseType.UFOREP, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = UTLAND, bucType = P_BUC_10, ytelseType = YtelseType.UFOREP, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, bucType = P_BUC_10, ytelseType = YtelseType.UFOREP, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = NORGE, bucType = P_BUC_10, ytelseType = YtelseType.UFOREP, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = UTLAND, bucType = P_BUC_10, ytelseType = YtelseType.UFOREP, hendelseType = HendelseType.SENDT)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder59aar, bucType = P_BUC_10, ytelseType = YtelseType.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder59aar, landkode = NORGE, bucType = P_BUC_10, ytelseType = YtelseType.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder59aar, landkode = UTLAND, bucType = P_BUC_10, ytelseType = YtelseType.ALDER, hendelseType = HendelseType.SENDT)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, bucType = P_BUC_10, ytelseType = YtelseType.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, landkode = NORGE, bucType = P_BUC_10, ytelseType = YtelseType.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, landkode = UTLAND, bucType = P_BUC_10, ytelseType = YtelseType.ALDER, hendelseType = HendelseType.SENDT)))

    }

    @Test
    fun `Routing for vanlige BUC'er`() {

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), geografiskTilknytning = dummyTilknytning, bucType = P_BUC_01, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, geografiskTilknytning = dummyTilknytning, bucType = P_BUC_01, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, geografiskTilknytning = dummyTilknytning, bucType = P_BUC_01, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_01, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), bucType = P_BUC_03, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_03, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_03, hendelseType = HendelseType.SENDT)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), bucType = P_BUC_04, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_04, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_04, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = P_BUC_05, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = P_BUC_05, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = UTLAND, bucType = P_BUC_05, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, bucType = P_BUC_05, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = NORGE, bucType = P_BUC_05, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = UTLAND, bucType = P_BUC_05, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = P_BUC_06, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = P_BUC_06, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = UTLAND, bucType = P_BUC_06, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, bucType = P_BUC_06, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = NORGE, bucType = P_BUC_06, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = UTLAND, bucType = P_BUC_06, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = P_BUC_07, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = P_BUC_07, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = UTLAND, bucType = P_BUC_07, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, bucType = P_BUC_07, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = NORGE, bucType = P_BUC_07, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = UTLAND, bucType = P_BUC_07, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = P_BUC_08, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = P_BUC_08, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = UTLAND, bucType = P_BUC_08, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, bucType = P_BUC_08, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = NORGE, bucType = P_BUC_08, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = UTLAND, bucType = P_BUC_08, hendelseType = HendelseType.SENDT)))


        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), bucType = P_BUC_03, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_03, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_03, hendelseType = HendelseType.SENDT)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), bucType = P_BUC_04, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_04, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_04, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = P_BUC_05, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = P_BUC_05, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = UTLAND, bucType = P_BUC_05, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, bucType = P_BUC_05, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = NORGE, bucType = P_BUC_05, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = UTLAND, bucType = P_BUC_05, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = P_BUC_06, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = P_BUC_06, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = UTLAND, bucType = P_BUC_06, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, bucType = P_BUC_06, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = NORGE, bucType = P_BUC_06, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = UTLAND, bucType = P_BUC_06, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = P_BUC_07, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = P_BUC_07, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = UTLAND, bucType = P_BUC_07, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, bucType = P_BUC_07, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = NORGE, bucType = P_BUC_07, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = UTLAND, bucType = P_BUC_07, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = P_BUC_08, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = P_BUC_08, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = UTLAND, bucType = P_BUC_08, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, bucType = P_BUC_08, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = NORGE, bucType = P_BUC_08, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = UTLAND, bucType = P_BUC_08, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = P_BUC_09, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = P_BUC_09, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = UTLAND, bucType = P_BUC_09, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, bucType = P_BUC_09, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = NORGE, bucType = P_BUC_09, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = UTLAND, bucType = P_BUC_09, hendelseType = HendelseType.SENDT)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, diskresjonskode = Diskresjonskode.SPSF, geografiskTilknytning = dummyTilknytning, bucType = P_BUC_01, ytelseType = YtelseType.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, diskresjonskode = Diskresjonskode.SPFO, landkode = NORGE, bucType = P_BUC_03, ytelseType = YtelseType.UFOREP, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, diskresjonskode = Diskresjonskode.SPFO, landkode = UTLAND, bucType = P_BUC_10, ytelseType = YtelseType.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, diskresjonskode = Diskresjonskode.SPSF, landkode = UTLAND, bucType = P_BUC_10, ytelseType = YtelseType.GJENLEV, hendelseType = HendelseType.SENDT)))
    }

    private fun opprettSakInfo(sakStatus: SakStatus) : SakInformasjon {
        return SakInformasjon(null, YtelseType.UFOREP, sakStatus)
    }

    private fun opprettSakInfo(sakStatus: SakStatus) : SakInformasjon {
        return SakInformasjon("", YtelseType.UFOREP, sakStatus)
    }


    @Test
    fun `hentNorg2Enhet for bosatt utland`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordelig0001result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Klient).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(landkode = "SVE"), P_BUC_01)
        val expected = PENSJON_UTLAND

        assertEquals(expected, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordelig4803result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Klient).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"),
                P_BUC_01)
        val expected = NFP_UTLAND_OSLO

        assertEquals(expected, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt nord-Norge`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordelig4862result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Klient).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(geografiskTilknytning = "1102", landkode = "NOR"),
                P_BUC_01)
        val expected = NFP_UTLAND_AALESUND

        assertEquals(expected, actual)
    }

    @Test
    fun `hentNorg2Enhet for diskresjonkode`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordeling2103result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Klient).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(
                geografiskTilknytning = "1102",
                landkode = "NOR",
                diskresjonskode = "SPSF"),
                P_BUC_01)
        val expected = DISKRESJONSKODE

        assertEquals(expected, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge feil buc`() {
        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"),
                P_BUC_03)
        val expected = null

        assertEquals(expected, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge mock feil mot Norg2`() {
        doReturn(listOf<Norg2ArbeidsfordelingItem>())
                .whenever(norg2Klient).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"),
                P_BUC_01)
        val expected = null

        assertEquals(expected, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge mock feil mot Norg2 error`() {
        doThrow(RuntimeException("dummy"))
                .whenever(norg2Klient).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"),
                P_BUC_01)
        val expected = null

        assertEquals(expected, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge med diskresjon`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordeling2103result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Klient).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(
                geografiskTilknytning = "0322",
                landkode = "NOR",
                diskresjonskode = "SPSF"),
                P_BUC_01)
        val expected = DISKRESJONSKODE

        assertEquals(expected, actual)
    }

    @Test
    fun testEnumEnhets() {

        assertEquals(PENSJON_UTLAND, Enhet.getEnhet("0001"))

        assertEquals(NFP_UTLAND_OSLO, Enhet.getEnhet("4803"))

        assertEquals(DISKRESJONSKODE, Enhet.getEnhet("2103"))

    }

    private fun getJsonFileFromResource(filename: String): String {
        return String(Files.readAllBytes(Paths.get("src/test/resources/norg2/$filename")))
    }

    private fun mockerEnPerson() = IdentifisertPerson(
            "123",
            "Testern",
            null,
            "NO",
            "010",
            PersonRelasjon("12345678910", Relasjon.FORSIKRET))

}
