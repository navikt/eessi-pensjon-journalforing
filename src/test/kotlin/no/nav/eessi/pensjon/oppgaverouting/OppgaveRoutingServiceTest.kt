package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.norg2.Norg2ArbeidsfordelingItem
import no.nav.eessi.pensjon.klienter.norg2.Norg2Klient
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.klienter.norg2.NorgKlientRequest
import no.nav.eessi.pensjon.models.BucType.H_BUC_07
import no.nav.eessi.pensjon.models.BucType.P_BUC_01
import no.nav.eessi.pensjon.models.BucType.P_BUC_02
import no.nav.eessi.pensjon.models.BucType.P_BUC_03
import no.nav.eessi.pensjon.models.BucType.P_BUC_04
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
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.PersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.helpers.Fodselsnummer
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertNull

internal class OppgaveRoutingServiceTest {

    private val norg2Klient = mockk<Norg2Klient>()

    private val norg2Service = Norg2Service(norg2Klient)

    private val routingService = OppgaveRoutingService(norg2Service)

    companion object {
        private const val DUMMY_FNR = "09035225916" // Testbruker SLAPP SKILPADDE

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
                saktype = Saktype.ALDER,
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
                saktype = Saktype.UFOREP,
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
                saktype = Saktype.UFOREP,
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
                saktype = Saktype.UFOREP,
                sedType = SedType.R004,
                hendelseType = HendelseType.MOTTATT,
                sakInformasjon = null)))
    }

    @Test
    fun `Routing av mottatte sed R_BUC_02 med mer enn én person routes til ID_OG_FORDELING`() {
        val forsikret = IdentifisertPerson(
                "123",
                "Testern",
                false,
                null,
                "010",
                PersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), Relasjon.FORSIKRET))
        val avod = IdentifisertPerson(
                "234",
                "Avdod",
                false,
                null,
                "010",
                PersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), Relasjon.AVDOD))
        forsikret.personListe = listOf(forsikret, avod)

        val enhetresult = routingService.route(OppgaveRoutingRequest(
                aktorId = "123123123",
                fdato = irrelevantDato(),
                landkode = null,
                geografiskTilknytning = dummyTilknytning,
                bucType = R_BUC_02,
                saktype = Saktype.ALDER,
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

        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, saktype = Saktype.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, saktype = Saktype.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.LOPENDE), hendelseType = HendelseType.SENDT)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, saktype = Saktype.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.AVSLUTTET), hendelseType = HendelseType.SENDT)))

        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, saktype = Saktype.BARNEP, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, saktype = Saktype.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))


        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, saktype = Saktype.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, saktype = Saktype.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.LOPENDE), hendelseType = HendelseType.SENDT)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, saktype = Saktype.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.AVSLUTTET), hendelseType = HendelseType.SENDT)))


        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, saktype = Saktype.BARNEP, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, saktype = Saktype.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, saktype = Saktype.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.LOPENDE), hendelseType = HendelseType.SENDT)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, saktype = Saktype.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.AVSLUTTET), hendelseType = HendelseType.SENDT)))

        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, hendelseType = HendelseType.SENDT)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bucType = P_BUC_02, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, saktype = Saktype.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.LOPENDE), hendelseType = HendelseType.SENDT)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, saktype = Saktype.UFOREP, sakInformasjon = opprettSakInfo(SakStatus.AVSLUTTET), hendelseType = HendelseType.SENDT)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, saktype = Saktype.BARNEP, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, saktype = Saktype.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bucType = P_BUC_02, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))

    }

    @Test
    fun `Routing for P_BUC_10'er`() {
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = P_BUC_10, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = P_BUC_10, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = UTLAND, bucType = P_BUC_10, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, bucType = P_BUC_10, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = NORGE, bucType = P_BUC_10, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = UTLAND, bucType = P_BUC_10, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = P_BUC_10, saktype = Saktype.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = P_BUC_10, saktype = Saktype.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = UTLAND, bucType = P_BUC_10, saktype = Saktype.GJENLEV, hendelseType = HendelseType.SENDT)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, bucType = P_BUC_10, saktype = Saktype.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = NORGE, bucType = P_BUC_10, saktype = Saktype.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = UTLAND, bucType = P_BUC_10, saktype = Saktype.GJENLEV, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, bucType = P_BUC_10, saktype = Saktype.UFOREP, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = NORGE, bucType = P_BUC_10, saktype = Saktype.UFOREP, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder18aar, landkode = UTLAND, bucType = P_BUC_10, saktype = Saktype.UFOREP, hendelseType = HendelseType.SENDT)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, bucType = P_BUC_10, saktype = Saktype.UFOREP, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = NORGE, bucType = P_BUC_10, saktype = Saktype.UFOREP, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder17aar, landkode = UTLAND, bucType = P_BUC_10, saktype = Saktype.UFOREP, hendelseType = HendelseType.SENDT)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder59aar, bucType = P_BUC_10, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder59aar, landkode = NORGE, bucType = P_BUC_10, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder59aar, landkode = UTLAND, bucType = P_BUC_10, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, bucType = P_BUC_10, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, landkode = NORGE, bucType = P_BUC_10, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, landkode = UTLAND, bucType = P_BUC_10, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))
    }

    @Test
    fun `Routing for P_BUC_10 mottatt med bruk av Norg2 tjeneste`() {
        val enhetlist = fromResource("/norg2/norg2arbeidsfordelig4862med-viken-result.json")



        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val personRelasjon = PersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), Relasjon.FORSIKRET, Saktype.ALDER, SedType.P15000)
        val identifisertPerson = IdentifisertPerson("01010101010", "Ole Olsen", false, "NOR", "3005", personRelasjon, emptyList())

        val sedHendelseModel = SedHendelseModel(1232312L, "2321313", "P", P_BUC_10, "32131", avsenderId = "12313123",
            "SE", "SE", "2312312", "NO", "NO", "23123123","1",
            SedType.P15000, null )

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            Saktype.ALDER,
            sedHendelseModel,
            HendelseType.MOTTATT,
            null
        )

        val result = routingService.route(oppgaveroutingrequest)
        assertEquals(NFP_UTLAND_OSLO, result)

    }

    @Test
    fun `Gitt gjenlevendesak for P_BUC_02 mottatt når bruk av Norg2 tjeneste benyttes så routes det til PensjonUtland`() {
        val json = """
            {
            "id": 100026861,
            "diskresjonskode": "ANY",
            "oppgavetype": "ANY",
            "behandlingstype": "ae0104",
            "behandlingstema": "ab0011",
            "tema": "PEN",
            "temagruppe": "ANY",
            "geografiskOmraade": "3005",
            "enhetId": 100000617,
            "enhetNr": "0001",
            "enhetNavn": "NAV Pensjon Utland",
            "skalTilLokalkontor": false,
            "gyldigFra": "2017-09-30"
            }
        """.trimIndent()
         val mappedResponse = mapJsonToAny(json, typeRefs<Norg2ArbeidsfordelingItem>())


        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns listOf(mappedResponse)

        val personRelasjon = PersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), Relasjon.GJENLEVENDE, Saktype.GJENLEV, SedType.P2100)
        val identifisertPerson = IdentifisertPerson("01010101010", "Ole Olsen", false, "NOR", "3005", personRelasjon, emptyList())

        val sedHendelseModel = SedHendelseModel(1232312L, "2321313", "P", P_BUC_02, "32131", avsenderId = "12313123",
            "SE", "SE", "2312312", "NO", "NO", "23123123","1",
            SedType.P2100, null )

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            Saktype.GJENLEV,
            sedHendelseModel,
            HendelseType.MOTTATT,
            null
        )

        val result = routingService.route(oppgaveroutingrequest)
        assertEquals(PENSJON_UTLAND, result)

    }

    @Test
    fun `Gitt barnePensjon for P_BUC_02 mottatt når bruk av Norg2 tjeneste benyttes så routes det til PensjonUtland`() {
        val json = """
            {
            "id": 100026861,
            "diskresjonskode": "ANY",
            "oppgavetype": "ANY",
            "behandlingstype": "ae0107",
            "behandlingstema": "ab0255",
            "tema": "PEN",
            "temagruppe": "ANY",
            "geografiskOmraade": "3005",
            "enhetId": 100000617,
            "enhetNr": "0001",
            "enhetNavn": "NAV Pensjon Utland",
            "skalTilLokalkontor": false,
            "gyldigFra": "2017-09-30"
            }
        """.trimIndent()
        val mappedResponse = mapJsonToAny(json, typeRefs<Norg2ArbeidsfordelingItem>())


        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns listOf(mappedResponse)

        val personRelasjon = PersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), Relasjon.GJENLEVENDE, Saktype.BARNEP, SedType.P2100)
        val identifisertPerson = IdentifisertPerson("01010101010", "Ole Olsen", false, "SWE", "3005", personRelasjon, emptyList())

        val sedHendelseModel = SedHendelseModel(1232312L, "2321313", "P", P_BUC_02, "32131", avsenderId = "12313123",
            "SE", "SE", "2312312", "NO", "NO", "23123123","1",
            SedType.P2100, null )

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            Saktype.BARNEP,
            sedHendelseModel,
            HendelseType.MOTTATT,
            null
        )

        val result = routingService.route(oppgaveroutingrequest)
        assertEquals(PENSJON_UTLAND, result)

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

        assertEquals(DISKRESJONSKODE, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, harAdressebeskyttelse = true, geografiskTilknytning = dummyTilknytning, bucType = P_BUC_01, saktype = Saktype.ALDER, hendelseType = HendelseType.SENDT)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, landkode = NORGE, bucType = P_BUC_03, saktype = Saktype.UFOREP, hendelseType = HendelseType.SENDT)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, landkode = UTLAND, bucType = P_BUC_10, saktype = Saktype.GJENLEV, hendelseType = HendelseType.SENDT)))
        assertEquals(DISKRESJONSKODE, routingService.route(OppgaveRoutingRequest(aktorId = "01010101010", fdato = alder60aar, harAdressebeskyttelse = true, landkode = UTLAND, bucType = P_BUC_10, saktype = Saktype.GJENLEV, hendelseType = HendelseType.SENDT)))
    }

    private fun opprettSakInfo(sakStatus: SakStatus): SakInformasjon {
        return SakInformasjon(null, Saktype.UFOREP, sakStatus)
    }

    @Test
    fun `hentNorg2Enhet for bosatt utland`() {
        val enhetlist = fromResource("/norg2/norg2arbeidsfordelig0001result.json")

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val actual = norg2Service.hentArbeidsfordelingEnhet(NorgKlientRequest(landkode = "SVE"))

        assertEquals(PENSJON_UTLAND, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge`() {
        val enhetlist = fromResource("/norg2/norg2arbeidsfordelig4803result.json")

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val actual = norg2Service.hentArbeidsfordelingEnhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"))

        assertEquals(NFP_UTLAND_OSLO, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt nord-Norge`() {
        val enhetlist = fromResource("/norg2/norg2arbeidsfordelig4862result.json")

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val actual = norg2Service.hentArbeidsfordelingEnhet(NorgKlientRequest(geografiskTilknytning = "1102", landkode = "NOR"))

        assertEquals(NFP_UTLAND_AALESUND, actual)
    }

    @Test
    fun `hentNorg2Enhet for diskresjonkode`() {
        val enhetlist = fromResource("/norg2/norg2arbeidsfordeling2103result.json")

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val actual = norg2Service.hentArbeidsfordelingEnhet(
            NorgKlientRequest(geografiskTilknytning = "1102", landkode = "NOR", harAdressebeskyttelse = true)
        )

        assertEquals(DISKRESJONSKODE, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge feil buc`() {
        val actual = norg2Service.hentArbeidsfordelingEnhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"))

        assertNull(actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge mock feil mot Norg2`() {
        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns emptyList()

        val actual = norg2Service.hentArbeidsfordelingEnhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"))

        assertNull(actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge mock feil mot Norg2 error`() {
        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } throws RuntimeException("dummy")

        val actual = norg2Service.hentArbeidsfordelingEnhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"))

        assertNull(actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge med diskresjon`() {
        val enhetlist = fromResource("/norg2/norg2arbeidsfordeling2103result.json")
        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val actual = norg2Service.hentArbeidsfordelingEnhet(
            NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR", harAdressebeskyttelse = true)
        )

        assertEquals(DISKRESJONSKODE, actual)
    }

    @Test
    fun testEnumEnhets() {
        assertEquals(PENSJON_UTLAND, Enhet.getEnhet("0001"))

        assertEquals(NFP_UTLAND_OSLO, Enhet.getEnhet("4803"))

        assertEquals(DISKRESJONSKODE, Enhet.getEnhet("2103"))
    }

    private fun fromResource(file: String): List<Norg2ArbeidsfordelingItem> {
        val json = javaClass.getResource(file).readText()

        return mapJsonToAny(json, typeRefs())
    }

    private fun mockerEnPerson() = IdentifisertPerson(
            "123",
            "Testern",
            false,
            "NO",
            "010",
            PersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), Relasjon.FORSIKRET))

}
