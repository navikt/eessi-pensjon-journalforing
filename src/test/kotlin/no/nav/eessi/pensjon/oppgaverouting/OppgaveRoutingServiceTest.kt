package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.norg2.Norg2ArbeidsfordelingItem
import no.nav.eessi.pensjon.klienter.norg2.Norg2Klient
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.klienter.norg2.NorgKlientRequest
import no.nav.eessi.pensjon.models.BucType
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
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.util.*

val norg2Klient = mockk<Norg2Klient>()
val norg2Service = Norg2Service(norg2Klient)
val routingService = OppgaveRoutingService(norg2Service)

fun irrelevantDato() = LocalDate.MIN
internal class OppgaveRoutingServiceTest {

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


    @Test
    fun `Gitt manglende fnr naar oppgave routes saa send oppgave til ID_OG_FORDELING`() {
        val enhet = routingService.route(
            OppgaveRoutingRequest(
                fdato = irrelevantDato(),
                landkode = MANGLER_LAND,
                geografiskTilknytning = dummyTilknytning,
                bucType = P_BUC_01,
                hendelseType = HendelseType.SENDT
            )
        )
        assertEquals(enhet, ID_OG_FORDELING)
    }

    @Test
    fun `Gitt manglende ytelsestype for P_BUC_10 saa send oppgave til PENSJON_UTLAND`() {
        val enhet = routingService.route(
            OppgaveRoutingRequest(
                aktorId = "010101010101",
                fdato = irrelevantDato(),
                landkode = MANGLER_LAND,
                bucType = P_BUC_10,
                hendelseType = HendelseType.SENDT
            )
        )
        assertEquals(enhet, PENSJON_UTLAND)
    }

    @Test
    fun `Routing for mottatt H_BUC_07`() {
        assertEquals(
            UFORE_UTLAND,
            routingService.route(
                OppgaveRoutingRequest(
                    aktorId = "01010101010",
                    fdato = alder18aar,
                    bucType = H_BUC_07,
                    hendelseType = HendelseType.SENDT
                )
            )
        )
        assertEquals(
            PENSJON_UTLAND,
            routingService.route(
                OppgaveRoutingRequest(
                    aktorId = "01010101010",
                    fdato = alder60aar,
                    bucType = H_BUC_07,
                    hendelseType = HendelseType.SENDT
                )
            )
        )

        assertEquals(
            UFORE_UTLANDSTILSNITT,
            routingService.route(
                OppgaveRoutingRequest(
                    aktorId = "01010101010",
                    fdato = alder18aar,
                    landkode = NORGE,
                    bucType = H_BUC_07,
                    hendelseType = HendelseType.SENDT
                )
            )
        )
        assertEquals(
            NFP_UTLAND_OSLO,
            routingService.route(
                OppgaveRoutingRequest(
                    aktorId = "01010101010",
                    fdato = alder60aar,
                    landkode = NORGE,
                    bucType = H_BUC_07,
                    hendelseType = HendelseType.SENDT
                )
            )
        )

        assertEquals(
            ID_OG_FORDELING,
            routingService.route(
                OppgaveRoutingRequest(
                    fdato = alder60aar,
                    landkode = NORGE,
                    bucType = H_BUC_07,
                    hendelseType = HendelseType.SENDT
                )
            )
        )
    }

    // ved bruk av fil kan jeg bruke denne: R_BUC_02-R005-AP.json
    @Test
    fun `Routing av mottatte sed R005 på R_BUC_02 ingen ytelse`() {
        assertEquals(
            ID_OG_FORDELING, routingService.route(
                OppgaveRoutingRequest(
                    aktorId = "01010101010",
                    fdato = irrelevantDato(),
                    landkode = NORGE,
                    geografiskTilknytning = dummyTilknytning,
                    bucType = R_BUC_02,
                    hendelseType = HendelseType.MOTTATT,
                    sakInformasjon = null,
                    identifisertPerson = mockerEnPerson()
                )
            )
        )
    }

    @Test
    fun `Routing av mottatte sed R005 på R_BUC_02 alderpensjon ytelse`() {
        assertEquals(
            PENSJON_UTLAND, routingService.route(
                OppgaveRoutingRequest(
                    aktorId = "01010101010",
                    fdato = irrelevantDato(),
                    landkode = UTLAND,
                    geografiskTilknytning = dummyTilknytning,
                    bucType = R_BUC_02,
                    saktype = Saktype.ALDER,
                    hendelseType = HendelseType.MOTTATT,
                    sakInformasjon = null,
                    identifisertPerson = mockerEnPerson()
                )
            )
        )
    }

    @Test
    fun `Routing av mottatte sed R005 på R_BUC_02 uforepensjon ytelse`() {
        assertEquals(
            UFORE_UTLAND, routingService.route(
                OppgaveRoutingRequest(
                    aktorId = "01010101010",
                    fdato = irrelevantDato(),
                    landkode = UTLAND,
                    geografiskTilknytning = dummyTilknytning,
                    bucType = R_BUC_02,
                    saktype = Saktype.UFOREP,
                    hendelseType = HendelseType.MOTTATT,
                    sakInformasjon = null,
                    identifisertPerson = mockerEnPerson()
                )
            )
        )
    }

    @Test
    fun `Routing av mottatte sed R004 på R_BUC_02 skal gi en routing til UFORE_UTLAND`() {
        assertEquals(
            OKONOMI_PENSJON, routingService.route(
                OppgaveRoutingRequest(
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
                )
            )
        )
    }

    @Test
    fun `Routing av mottatte sed R004 på R_BUC_02 ukjent ident`() {
        assertEquals(
            ID_OG_FORDELING, routingService.route(
                OppgaveRoutingRequest(
                    aktorId = null,
                    fdato = irrelevantDato(),
                    landkode = UTLAND,
                    geografiskTilknytning = dummyTilknytning,
                    bucType = R_BUC_02,
                    saktype = Saktype.UFOREP,
                    sedType = SedType.R004,
                    hendelseType = HendelseType.MOTTATT,
                    sakInformasjon = null
                )
            )
        )
    }

    @Test
    fun `Routing av mottatte sed R_BUC_02 med mer enn én person routes til ID_OG_FORDELING`() {
        val forsikret = IdentifisertPerson(
            "123",
            "Testern",
            null,
            "010",
            SEDPersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )
        val avod = IdentifisertPerson(
            "234",
            "Avdod",
            null,
            "010",
            SEDPersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), Relasjon.AVDOD, rinaDocumentId =  "3123123")
        )
        forsikret.personListe = listOf(forsikret, avod)

        val enhetresult = routingService.route(
            OppgaveRoutingRequest(
                aktorId = "123123123",
                fdato = irrelevantDato(),
                landkode = null,
                geografiskTilknytning = dummyTilknytning,
                bucType = R_BUC_02,
                saktype = Saktype.ALDER,
                sedType = SedType.R005,
                hendelseType = HendelseType.MOTTATT,
                sakInformasjon = null,
                identifisertPerson = forsikret
            )
        )

        assertEquals(ID_OG_FORDELING, enhetresult)

    }

    // ved bruk av fil kan jeg bruke denne: R_BUC_02-R005-AP.json
    class Routing_R_BUC_02 {
        private companion object {
            @JvmStatic
            fun arguments() =
                Arrays.stream(
                    arrayOf(
                        TestArgumentsPBuc02(ID_OG_FORDELING, NORGE, SedType.R005),
                        TestArgumentsPBuc02(OKONOMI_PENSJON, NORGE, SedType.R004),
                        TestArgumentsPBuc02(OKONOMI_PENSJON, UTLAND, SedType.R004),
                        TestArgumentsPBuc02(ID_OG_FORDELING, UTLAND, SedType.R005),
                    )
                )
        }
        private fun mockerEnPerson() = IdentifisertPerson(
            "123",
            "Testern",
            "NO",
            "010",
            SEDPersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
        )

        data class TestArgumentsPBuc02(
            val expectedResult: Enhet,
            val landkode: String?,
            val sedType: SedType
        )

        @ParameterizedTest
        @MethodSource("arguments")
        fun `Routing for R_BUC_02'er`(arguments: TestArgumentsPBuc02) {
            assertEquals(
                arguments.expectedResult, routingService.route(
                    OppgaveRoutingRequest(
                        aktorId = "01010101010",
                        fdato = irrelevantDato(),
                        landkode = arguments.landkode,
                        geografiskTilknytning = dummyTilknytning,
                        bucType = R_BUC_02,
                        sedType = arguments.sedType,
                        hendelseType = HendelseType.SENDT,
                        sakInformasjon = null,
                        identifisertPerson = mockerEnPerson()
                    )
                )
            )
        }
    }

    class Routing_P_BUC_02 {
        private companion object {
            @JvmStatic
            fun arguments() =
                Arrays.stream(
                    arrayOf(
                        TestArgumentsPBuc02(PENSJON_UTLAND, NORGE, Saktype.GJENLEV),
                        TestArgumentsPBuc02(UFORE_UTLANDSTILSNITT, NORGE, Saktype.UFOREP, SakStatus.LOPENDE),
                        TestArgumentsPBuc02(ID_OG_FORDELING, NORGE, Saktype.UFOREP, SakStatus.AVSLUTTET),
                        TestArgumentsPBuc02(PENSJON_UTLAND, NORGE, Saktype.BARNEP),

                        TestArgumentsPBuc02(NFP_UTLAND_AALESUND, NORGE, Saktype.ALDER),
                        TestArgumentsPBuc02(UFORE_UTLANDSTILSNITT, NORGE, Saktype.UFOREP, SakStatus.LOPENDE),
                        TestArgumentsPBuc02(ID_OG_FORDELING, NORGE, Saktype.UFOREP, SakStatus.AVSLUTTET),
                        TestArgumentsPBuc02(PENSJON_UTLAND, NORGE, Saktype.BARNEP),

                        TestArgumentsPBuc02(NFP_UTLAND_AALESUND, NORGE, Saktype.ALDER),
                        TestArgumentsPBuc02(UFORE_UTLAND, UTLAND, Saktype.UFOREP, SakStatus.LOPENDE),
                        TestArgumentsPBuc02(ID_OG_FORDELING, UTLAND, Saktype.UFOREP, SakStatus.AVSLUTTET),
                        TestArgumentsPBuc02(ID_OG_FORDELING, UTLAND),
                        TestArgumentsPBuc02(ID_OG_FORDELING, NORGE),

                        TestArgumentsPBuc02(UFORE_UTLAND, UTLAND, Saktype.UFOREP, SakStatus.LOPENDE),
                        TestArgumentsPBuc02(ID_OG_FORDELING, UTLAND, Saktype.UFOREP, SakStatus.AVSLUTTET),
                        TestArgumentsPBuc02(PENSJON_UTLAND, UTLAND, Saktype.BARNEP),
                        TestArgumentsPBuc02(PENSJON_UTLAND, UTLAND, Saktype.GJENLEV),
                        TestArgumentsPBuc02(PENSJON_UTLAND, UTLAND, Saktype.ALDER),
                    )
                )
        }
        data class TestArgumentsPBuc02(
            val expectedResult: Enhet,
            val landkode: String?,
            val saktype: Saktype? = null,
            val sakStatus: SakStatus? = null
        )

        private fun opprettSakInfo(sakStatus: SakStatus): SakInformasjon {
            return SakInformasjon(null, Saktype.UFOREP, sakStatus)
        }

        @ParameterizedTest
        @MethodSource("arguments")
        fun `Routing for P_BUC_02'er`(arguments: TestArgumentsPBuc02) {

            assertEquals(
                arguments.expectedResult,
                routingService.route(
                    OppgaveRoutingRequest(
                        aktorId = "01010101010",
                        fdato = irrelevantDato(),
                        landkode = arguments.landkode,
                        bucType = P_BUC_02,
                        saktype = arguments.saktype,
                        sakInformasjon = arguments.sakStatus?.let { opprettSakInfo(it) },
                        hendelseType = HendelseType.SENDT
                    )
                )
            )
        }
    }

    data class TestArguments(
        val expectedResult: Enhet,
        val alder: LocalDate,
        val landkode: String?,
        val saktype: Saktype?
    )

    class Routing_P_BUC_10 {
        private companion object {
            @JvmStatic
            fun arguments() =
                Arrays.stream(
                    arrayOf(
                        TestArguments(PENSJON_UTLAND, alder18aar, null, Saktype.ALDER),
                        TestArguments(ID_OG_FORDELING, alder18aar, NORGE, Saktype.ALDER),
                        TestArguments(PENSJON_UTLAND, alder18aar, UTLAND, Saktype.ALDER),
                        TestArguments(PENSJON_UTLAND, alder17aar, null, Saktype.ALDER),
                        TestArguments(ID_OG_FORDELING, alder17aar, NORGE, Saktype.ALDER),
                        TestArguments(PENSJON_UTLAND, alder17aar, UTLAND, Saktype.ALDER),

                        TestArguments(ID_OG_FORDELING, alder18aar, NORGE, Saktype.GJENLEV),
                        TestArguments(PENSJON_UTLAND, alder18aar, UTLAND, Saktype.GJENLEV),
                        TestArguments(PENSJON_UTLAND, alder17aar, null, Saktype.GJENLEV),
                        TestArguments(ID_OG_FORDELING, alder17aar, NORGE, Saktype.GJENLEV),
                        TestArguments(PENSJON_UTLAND, alder17aar, UTLAND, Saktype.GJENLEV),

                        TestArguments(UFORE_UTLAND, alder18aar, null, Saktype.UFOREP),
                        TestArguments(UFORE_UTLANDSTILSNITT, alder18aar, NORGE, Saktype.UFOREP),
                        TestArguments(UFORE_UTLAND, alder18aar, UTLAND, Saktype.UFOREP),
                        TestArguments(UFORE_UTLAND, alder17aar, null, Saktype.UFOREP),
                        TestArguments(UFORE_UTLANDSTILSNITT, alder17aar, NORGE, Saktype.UFOREP),
                        TestArguments(UFORE_UTLAND, alder17aar, UTLAND, Saktype.UFOREP),

                        TestArguments(PENSJON_UTLAND, alder59aar, null, Saktype.ALDER),
                        TestArguments(ID_OG_FORDELING, alder59aar, NORGE, Saktype.ALDER),
                        TestArguments(PENSJON_UTLAND, alder59aar, UTLAND, Saktype.ALDER),
                        TestArguments(PENSJON_UTLAND, alder60aar, null, Saktype.ALDER),
                        TestArguments(ID_OG_FORDELING, alder60aar, NORGE, Saktype.ALDER),
                        TestArguments(PENSJON_UTLAND, alder60aar, UTLAND, Saktype.ALDER),
                    )
                )
        }

        @ParameterizedTest
        @MethodSource("arguments")
        fun `Routing_P_BUC_10`(arguments: TestArguments) {

            assertEquals(
                arguments.expectedResult,
                routingService.route(
                    OppgaveRoutingRequest(
                        aktorId = "01010101010",
                        fdato = arguments.alder,
                        bucType = P_BUC_10,
                        landkode = arguments.landkode,
                        saktype = arguments.saktype,
                        hendelseType = HendelseType.SENDT
                    )
                )
            )
        }
    }

    @Test
    fun `Routing for P_BUC_10 mottatt med bruk av Norg2 tjeneste`() {
        val enhetlist = fromResource("/norg2/norg2arbeidsfordelig4862med-viken-result.json")

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val personRelasjon =
            SEDPersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), Relasjon.FORSIKRET, Saktype.ALDER, SedType.P15000, rinaDocumentId =  "3123123")
        val identifisertPerson =
            IdentifisertPerson("01010101010", "Ole Olsen", "NOR", "3005", personRelasjon, personListe = emptyList())

        val sedHendelseModel = SedHendelseModel(
            1232312L, "2321313", "P", P_BUC_10, "32131", avsenderId = "12313123",
            "SE", "SE", "2312312", "NO", "NO", "23123123", "1",
            SedType.P15000, null
        )

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            Saktype.ALDER,
            sedHendelseModel,
            HendelseType.MOTTATT,
            null,
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

        val personRelasjon =
            SEDPersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), Relasjon.GJENLEVENDE, Saktype.GJENLEV, SedType.P2100, rinaDocumentId =  "3123123")
        val identifisertPerson =
            IdentifisertPerson("01010101010", "Ole Olsen", "NOR", "3005", personRelasjon, personListe = emptyList())

        val sedHendelseModel = SedHendelseModel(
            1232312L, "2321313", "P", P_BUC_02, "32131", avsenderId = "12313123",
            "SE", "SE", "2312312", "NO", "NO", "23123123", "1",
            SedType.P2100, null
        )

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            Saktype.GJENLEV,
            sedHendelseModel,
            HendelseType.MOTTATT,
            null,
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

        val personRelasjon =
            SEDPersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), Relasjon.GJENLEVENDE, Saktype.BARNEP, SedType.P2100, rinaDocumentId =  "3123123")
        val identifisertPerson = IdentifisertPerson(
            "01010101010",
            "Ole Olsen",
            "SWE",
            "3005",
            personRelasjon,
            personListe = emptyList()
        )

        val sedHendelseModel = SedHendelseModel(
            1232312L, "2321313", "P", P_BUC_02, "32131", avsenderId = "12313123",
            "SE", "SE", "2312312", "NO", "NO", "23123123", "1",
            SedType.P2100, null
        )

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            Saktype.BARNEP,
            sedHendelseModel,
            HendelseType.MOTTATT,
            null,
        )

        val result = routingService.route(oppgaveroutingrequest)
        assertEquals(PENSJON_UTLAND, result)

    }

    @Test
    fun `Gitt aldersak for P_BUC_01 mottatt når bruk av Norg2 tjeneste benyttes så routes det til NFP_UTLAND_OSLO`() {
        val enhetlist = fromResource("/norg2/norg2arbeidsfordelig4862med-viken-result.json")

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val personRelasjon =
            SEDPersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), Relasjon.FORSIKRET, Saktype.ALDER, SedType.P2000, rinaDocumentId =  "3123123")
        val identifisertPerson =
            IdentifisertPerson("01010101010", "Ole Olsen", "NOR", "3005", personRelasjon, personListe = emptyList())

        val sedHendelseModel = SedHendelseModel(
            1232312L, "2321313", "P", P_BUC_01, "32131", avsenderId = "12313123",
            "SE", "SE", "2312312", "NO", "NO", "23123123", "1",
            SedType.P2000, null
        )

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            Saktype.ALDER,
            sedHendelseModel,
            HendelseType.MOTTATT,
            null,
        )

        val result = routingService.route(oppgaveroutingrequest)
        assertEquals(NFP_UTLAND_OSLO, result)

    }

    @Test
    fun `Gitt uføresak for P_BUC_02 mottatt når bruk av Norg2 tjeneste benyttes så routes det til UFORE_UTLAND`() {
        val json = """
            {
            "id": 100026861,
            "diskresjonskode": "ANY",
            "oppgavetype": "ANY",
            "behandlingstype": "ae0107",
            "behandlingstema": "ANY",
            "tema": "UFO",
            "temagruppe": "ANY",
            "geografiskOmraade": "ANY",
            "enhetId": 100000617,
            "enhetNr": "4475",
            "enhetNavn": "UFORE UTLAND",
            "skalTilLokalkontor": false,
            "gyldigFra": "2017-09-30"
            }
        """.trimIndent()
        val mappedResponse = mapJsonToAny(json, typeRefs<Norg2ArbeidsfordelingItem>())

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns listOf(mappedResponse)

        val personRelasjon =
            SEDPersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), Relasjon.FORSIKRET, Saktype.UFOREP, SedType.P2200, rinaDocumentId =  "3123123")
        val identifisertPerson =
            IdentifisertPerson("01010101010", "Ole Olsen", "SWE", null, personRelasjon, personListe = emptyList())

        val sedHendelseModel = SedHendelseModel(
            1232312L, "2321313", "P", P_BUC_03, "32131", avsenderId = "12313123",
            "NO", "NO", "2312312", "SE", "SE", "23123123", "1",
            SedType.P2200, null
        )

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            Saktype.UFOREP,
            sedHendelseModel,
            HendelseType.SENDT,
            null,
        )

        val result = routingService.route(oppgaveroutingrequest)
        assertEquals(UFORE_UTLAND, result)
    }

    class Routing_vanligeBucs {
        data class TestArgumentsBucs(
            val expectedResult: Enhet,
            val bucType: BucType,
            val landkode: String? = null,
            val geografiskTilknytning: String? = null,
            val fdato: LocalDate? = null,
            val saksType: Saktype? = null,
            val adressebeskyttet: Boolean? = false
        )

        private companion object {
            @JvmStatic
            fun arguments() =
                Arrays.stream(
                    arrayOf(
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_01,  dummyTilknytning, fdato = irrelevantDato()),
                        TestArgumentsBucs(NFP_UTLAND_AALESUND, P_BUC_01, NORGE, dummyTilknytning, fdato = irrelevantDato()),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_01, UTLAND, dummyTilknytning, fdato = irrelevantDato()),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_01, UTLAND,  fdato = irrelevantDato()),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_03,   fdato = irrelevantDato()),

                        TestArgumentsBucs(UFORE_UTLANDSTILSNITT, P_BUC_03, NORGE,  fdato = irrelevantDato()),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_03, UTLAND,  fdato = irrelevantDato()),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_04,   fdato = irrelevantDato()),
                        TestArgumentsBucs(NFP_UTLAND_AALESUND, P_BUC_04, NORGE, fdato = irrelevantDato()),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_04, UTLAND),

                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_06, UTLAND, fdato = alder18aar),
                        TestArgumentsBucs(UFORE_UTLANDSTILSNITT, P_BUC_06, NORGE,  fdato = alder18aar),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_06,   fdato = alder18aar),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_06, fdato = alder17aar),
                        TestArgumentsBucs(NFP_UTLAND_AALESUND, P_BUC_06, NORGE,  fdato = alder17aar),

                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_06, UTLAND, fdato = alder17aar),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_07,  fdato = alder18aar),
                        TestArgumentsBucs(UFORE_UTLANDSTILSNITT, P_BUC_07, NORGE,  fdato = alder18aar),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_07, fdato = alder18aar),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_07, fdato = alder17aar),

                        TestArgumentsBucs(NFP_UTLAND_AALESUND, P_BUC_06, NORGE, fdato = alder17aar),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_07, UTLAND,  fdato = alder17aar),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_08, fdato = alder18aar),
                        TestArgumentsBucs(UFORE_UTLANDSTILSNITT, P_BUC_08, NORGE, fdato = alder18aar),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_08, UTLAND, fdato = alder18aar),

                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_08, fdato = alder17aar),
                        TestArgumentsBucs(NFP_UTLAND_AALESUND, P_BUC_08, NORGE,  fdato = alder17aar),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_08, UTLAND, fdato = alder17aar),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_03),
                        TestArgumentsBucs(UFORE_UTLANDSTILSNITT, P_BUC_03, NORGE),

                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_03, UTLAND, fdato = alder17aar),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_04, fdato = alder17aar),
                        TestArgumentsBucs(NFP_UTLAND_AALESUND, P_BUC_04,NORGE, fdato = alder17aar),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_04, UTLAND),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_06, fdato = alder18aar),

                        TestArgumentsBucs(UFORE_UTLANDSTILSNITT, P_BUC_06, NORGE, fdato = alder18aar),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_06, UTLAND, fdato = alder18aar),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_06, fdato = alder17aar),
                        TestArgumentsBucs(NFP_UTLAND_AALESUND, P_BUC_06, NORGE, fdato = alder17aar),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_06, UTLAND, fdato = alder17aar),

                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_07,  fdato = alder18aar),
                        TestArgumentsBucs(UFORE_UTLANDSTILSNITT, P_BUC_07, NORGE, fdato = alder18aar),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_07, UTLAND, fdato = alder18aar),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_07, fdato = alder17aar),
                        TestArgumentsBucs(NFP_UTLAND_AALESUND, P_BUC_07, NORGE, fdato = alder17aar),

                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_07, UTLAND, fdato = alder17aar),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_08, fdato = alder18aar),
                        TestArgumentsBucs(UFORE_UTLANDSTILSNITT, P_BUC_08, NORGE, fdato = alder18aar),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_08, UTLAND, fdato = alder18aar),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_08, fdato = alder17aar),

                        TestArgumentsBucs(NFP_UTLAND_AALESUND, P_BUC_07, NORGE, fdato = alder17aar),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_08, UTLAND, fdato = alder17aar),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_09, fdato = alder18aar),
                        TestArgumentsBucs(UFORE_UTLANDSTILSNITT, P_BUC_09, NORGE, fdato = alder18aar),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_09, UTLAND, fdato = alder18aar),


                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_07, UTLAND, fdato = alder17aar),
                        TestArgumentsBucs(NFP_UTLAND_AALESUND, P_BUC_08, NORGE, fdato = alder17aar),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_09, UTLAND, fdato = alder17aar),
                        TestArgumentsBucs(DISKRESJONSKODE, P_BUC_01, NORGE, fdato = alder60aar, adressebeskyttet = true, saksType = Saktype.ALDER),
                        TestArgumentsBucs(UFORE_UTLANDSTILSNITT, P_BUC_03, NORGE, fdato = alder60aar, saksType = Saktype.UFOREP),

                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_10, UTLAND, fdato = alder60aar, saksType = Saktype.GJENLEV),
                        TestArgumentsBucs(DISKRESJONSKODE, P_BUC_10, UTLAND, fdato = alder60aar, adressebeskyttet = true, saksType = Saktype.GJENLEV),
                        )
                )
        }

        @ParameterizedTest
        @MethodSource("arguments")
        fun `Ruting for vanlige BUCer`(arguments: TestArgumentsBucs) {
            assertEquals(
                arguments.expectedResult,
                routingService.route(
                    OppgaveRoutingRequest(
                        aktorId = "01010101010",
                        fdato = arguments.fdato ?: irrelevantDato(),
                        geografiskTilknytning = arguments.geografiskTilknytning,
                        bucType = arguments.bucType,
                        landkode = arguments.landkode,
                        hendelseType = HendelseType.SENDT,
                        harAdressebeskyttelse = arguments.adressebeskyttet ?: false,
                        saktype = arguments.saksType
                    )
                )
            )
        }
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

        val actual =
            norg2Service.hentArbeidsfordelingEnhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"))

        assertEquals(NFP_UTLAND_OSLO, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt nord-Norge`() {
        val enhetlist = fromResource("/norg2/norg2arbeidsfordelig4862result.json")

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val actual =
            norg2Service.hentArbeidsfordelingEnhet(NorgKlientRequest(geografiskTilknytning = "1102", landkode = "NOR"))

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
        val actual =
            norg2Service.hentArbeidsfordelingEnhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"))

        assertNull(actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge mock feil mot Norg2`() {
        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns emptyList()

        val actual =
            norg2Service.hentArbeidsfordelingEnhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"))

        assertNull(actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge mock feil mot Norg2 error`() {
        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } throws RuntimeException("dummy")

        val actual =
            norg2Service.hentArbeidsfordelingEnhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"))

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

    fun mockerEnPerson() = IdentifisertPerson(
        "123",
        "Testern",
        "NO",
        "010",
        SEDPersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), Relasjon.FORSIKRET, rinaDocumentId =  "3123123")
    )

}
