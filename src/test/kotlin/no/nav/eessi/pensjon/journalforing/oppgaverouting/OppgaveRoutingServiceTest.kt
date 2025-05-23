package no.nav.eessi.pensjon.journalforing.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.AVSLUTTET
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.journalforing.bestemenhet.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.bestemenhet.norg2.Norg2ArbeidsfordelingItem
import no.nav.eessi.pensjon.journalforing.bestemenhet.norg2.Norg2Klient
import no.nav.eessi.pensjon.journalforing.bestemenhet.norg2.Norg2Service
import no.nav.eessi.pensjon.journalforing.bestemenhet.norg2.NorgKlientRequest
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPDLPerson
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.util.*
import java.util.stream.Stream

val norg2Klient = mockk<Norg2Klient>()
val norg2Service = Norg2Service(norg2Klient)
val routingService = OppgaveRoutingService(norg2Service)

private const val AKTOER_ID = "01010101010"

fun irrelevantDato(): LocalDate = LocalDate.MIN

internal class OppgaveRoutingServiceTest {

    companion object {
        private const val DUMMY_FNR = "09035225916" // Testbruker SLAPP SKILPADDE

        const val DUMMY_TILKNYTNING = "032342"
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
        val enhet = routingService.hentEnhet(oppgaveRoutingRequest(irrelevantDato(), P_BUC_01, MANGLER_LAND, hendelseType = SENDT, aktoerId = null))
        assertEquals(enhet, ID_OG_FORDELING)
    }

    @Test
    fun `Gitt manglende ytelsestype for P_BUC_10 saa send oppgave til PENSJON_UTLAND`() {
        val enhet = routingService.hentEnhet( oppgaveRoutingRequest(irrelevantDato(), P_BUC_10, MANGLER_LAND, hendelseType = SENDT))
        assertEquals(enhet, PENSJON_UTLAND)
    }

    // Routing: https://confluence.adeo.no/pages/viewpage.action?pageId=579152774
    @Test
    fun `Routing for mottatt H_BUC_07`() {
        //Ukjent bruker
        assertEquals(
            ID_OG_FORDELING,
            routingService.hentEnhet(oppgaveRoutingRequest(alder60aar, H_BUC_07, NORGE, null))
        )

        assertEquals(
            UFORE_UTLANDSTILSNITT,
            routingService.hentEnhet(oppgaveRoutingRequest(alder18aar, H_BUC_07, NORGE))
        )

        assertEquals(
            FAMILIE_OG_PENSJONSYTELSER_OSLO,
            routingService.hentEnhet(oppgaveRoutingRequest(alder60aar, H_BUC_07, NORGE))
        )

        assertEquals(
            UFORE_UTLANDSTILSNITT,
            routingService.hentEnhet(oppgaveRoutingRequest(alder18aar, H_BUC_07, NORGE))
        )

        assertEquals(
            FAMILIE_OG_PENSJONSYTELSER_OSLO,
            routingService.hentEnhet(oppgaveRoutingRequest(alder60aar, H_BUC_07, NORGE))
        )

        //UTLAND
        assertEquals(
            PENSJON_UTLAND,
            routingService.hentEnhet(oppgaveRoutingRequest(alder60aar, H_BUC_07, UTLAND))
        )

        assertEquals(
            UFORE_UTLAND,
            routingService.hentEnhet(oppgaveRoutingRequest(alder18aar, H_BUC_07, UTLAND))
        )

    }

    private fun oppgaveRoutingRequest(
        fdato: LocalDate? = alder18aar,
        bucType: BucType? = P_BUC_02,
        landkode: String? = NORGE,
        aktoerId: String? = AKTOER_ID,
        hendelseType: HendelseType? = SENDT,
        geoTilknytning: String? = DUMMY_TILKNYTNING,
        sakInformasjon: SakInformasjon ?= null,
        identifisertPerson: IdentifisertPDLPerson?= mockerEnPerson("NO", DUMMY_TILKNYTNING, FORSIKRET, "Testern"),
        sakType: SakType? = ALDER,
        sedType: SedType? = R004
    ): OppgaveRoutingRequest {
        return OppgaveRoutingRequest(
            aktorId = aktoerId,
            fdato = fdato!!,
            bucType = bucType!!,
            landkode = landkode,
            hendelseType = hendelseType!!,
            geografiskTilknytning = geoTilknytning,
            sakInformasjon = sakInformasjon,
            identifisertPerson = identifisertPerson,
            saktype = sakType,
            sedType = sedType
        )
    }

    // ved bruk av fil kan jeg bruke denne: R_BUC_02-R005-AP.json
    @Test
    fun `Routing av mottatte sed R005 på R_BUC_02 ingen ytelse`() {
        assertEquals(
            ID_OG_FORDELING, routingService.hentEnhet(
                oppgaveRoutingRequest(irrelevantDato(), R_BUC_02, NORGE, aktoerId = null)
            )
        )
    }

    @Test
    fun `Routing av mottatte sed R005 på R_BUC_02 alderpensjon ytelse`() {
        assertEquals(
            PENSJON_UTLAND, routingService.hentEnhet(
                oppgaveRoutingRequest(irrelevantDato(), R_BUC_02, UTLAND, hendelseType = MOTTATT, sedType = R005)
            )
        )
    }

    @Test
    fun `Routing av mottatte sed R005 på R_BUC_02 uforepensjon ytelse`() {
        assertEquals(
            UFORE_UTLAND, routingService.hentEnhet(
                oppgaveRoutingRequest(irrelevantDato(), R_BUC_02, UTLAND, hendelseType = MOTTATT, sakType = UFOREP, sedType = R005)
            )
        )
    }

    @Test
    fun `Routing av mottatte sed R004 på R_BUC_02 skal gi en routing til UFORE_UTLAND`() {
        assertEquals(
            OKONOMI_PENSJON, routingService.hentEnhet(
                oppgaveRoutingRequest(irrelevantDato(), R_BUC_02, UTLAND, sakType = UFOREP, hendelseType = MOTTATT, sedType = R004)
            )
        )
    }

    @Test
    fun `Routing av mottatte sed R004 på R_BUC_02 ukjent ident`() {
        assertEquals(
            ID_OG_FORDELING, routingService.hentEnhet(
                oppgaveRoutingRequest(irrelevantDato(), R_BUC_02, UTLAND, null, sakType = UFOREP, hendelseType = MOTTATT, sedType = R004)
            )
        )
    }

    @Test
    fun `Routing av mottatte sed R_BUC_02 med mer enn én person routes til ID_OG_FORDELING`() {
        val forsikret = mockerEnPerson("NO", DUMMY_TILKNYTNING, FORSIKRET, "Testern")
        val avod = mockerEnPerson(landkode = null, relasjon = AVDOD, navn = "AVDOD")
        forsikret.personListe = listOf(forsikret, avod)

        val enhetresult = routingService.hentEnhet(
            oppgaveRoutingRequest(irrelevantDato(), R_BUC_02,null, sedType = R005, hendelseType = MOTTATT, identifisertPerson= forsikret)
        )

        assertEquals(ID_OG_FORDELING, enhetresult)

    }

    // ved bruk av fil kan jeg bruke denne: R_BUC_02-R005-AP.json
    class RoutingRBuc02 {
        private companion object {
            @JvmStatic
            fun arguments(): Stream<TestArgumentsPBuc02> =
                Arrays.stream(
                    arrayOf(
                        TestArgumentsPBuc02(OKONOMI_PENSJON, NORGE, R004),
                        TestArgumentsPBuc02(ID_OG_FORDELING, NORGE, R005),
                        TestArgumentsPBuc02(ID_OG_FORDELING, NORGE, R006),
                        TestArgumentsPBuc02(OKONOMI_PENSJON, UTLAND, R004),
                        TestArgumentsPBuc02(ID_OG_FORDELING, UTLAND, R005),
                        TestArgumentsPBuc02(ID_OG_FORDELING, UTLAND, R006),
                    )
                )
        }
        private fun mockerEnPerson() = IdentifisertPDLPerson(
            "123",
            "NO",
            "010",
            SEDPersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), FORSIKRET, rinaDocumentId =  "3123123"),
            personNavn = "Testern",
            identer = null
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
                arguments.expectedResult, routingService.hentEnhet(
                    OppgaveRoutingRequest(
                        aktorId = AKTOER_ID,
                        fdato = irrelevantDato(),
                        landkode = arguments.landkode,
                        geografiskTilknytning = DUMMY_TILKNYTNING,
                        bucType = R_BUC_02,
                        sedType = arguments.sedType,
                        hendelseType = SENDT,
                        sakInformasjon = null,
                        identifisertPerson = mockerEnPerson()
                    )
                )
            )
        }
    }

    class RoutingPBuc02 {
        private companion object {
            @JvmStatic
            fun arguments(): Stream<TestArgumentsPBuc02> =
                Arrays.stream(
                    arrayOf(
                        TestArgumentsPBuc02(NFP_UTLAND_AALESUND, NORGE, GJENLEV),
                        TestArgumentsPBuc02(UFORE_UTLANDSTILSNITT, NORGE, UFOREP, LOPENDE),
                        TestArgumentsPBuc02(UFORE_UTLANDSTILSNITT, NORGE, UFOREP, AVSLUTTET),
                        TestArgumentsPBuc02(NFP_UTLAND_AALESUND, NORGE, BARNEP),

                        TestArgumentsPBuc02(NFP_UTLAND_AALESUND, NORGE, ALDER),
                        TestArgumentsPBuc02(UFORE_UTLANDSTILSNITT, NORGE, UFOREP, LOPENDE),

                        TestArgumentsPBuc02(NFP_UTLAND_AALESUND, NORGE, ALDER),
                        TestArgumentsPBuc02(UFORE_UTLAND, UTLAND, UFOREP, LOPENDE),
                        TestArgumentsPBuc02(UFORE_UTLAND, UTLAND, UFOREP, AVSLUTTET),
                        TestArgumentsPBuc02(ID_OG_FORDELING, UTLAND),
                        TestArgumentsPBuc02(ID_OG_FORDELING, NORGE),

                        TestArgumentsPBuc02(UFORE_UTLAND, UTLAND, UFOREP, LOPENDE),
                        TestArgumentsPBuc02(PENSJON_UTLAND, UTLAND, BARNEP),
                        TestArgumentsPBuc02(PENSJON_UTLAND, UTLAND, GJENLEV),
                        TestArgumentsPBuc02(PENSJON_UTLAND, UTLAND, ALDER),
                    )
                )
        }
        data class TestArgumentsPBuc02(
            val expectedResult: Enhet,
            val landkode: String?,
            val saktype: SakType? = null,
            val sakStatus: SakStatus? = null
        )

        private fun opprettSakInfo(sakStatus: SakStatus): SakInformasjon {
            return SakInformasjon(null, UFOREP, sakStatus)
        }

        @ParameterizedTest
        @MethodSource("arguments")
        fun `Routing for P_BUC_02'er`(arguments: TestArgumentsPBuc02) {

            assertEquals(
                arguments.expectedResult,
                routingService.hentEnhet(
                    OppgaveRoutingRequest(
                        aktorId = AKTOER_ID,
                        fdato = irrelevantDato(),
                        landkode = arguments.landkode,
                        bucType = P_BUC_02,
                        saktype = arguments.saktype,
                        sakInformasjon = arguments.sakStatus?.let { opprettSakInfo(it) },
                        hendelseType = SENDT
                    )
                )
            )
        }
    }

    data class TestArguments(
        val expectedResult: Enhet,
        val alder: LocalDate,
        val landkode: String?,
        val saktype: SakType?
    )

    class RoutingPBuc10 {
        private companion object {
            @JvmStatic
            fun arguments(): Stream<TestArguments> =
                Arrays.stream(
                    arrayOf(
                        TestArguments(PENSJON_UTLAND, alder18aar, null, ALDER),
                        TestArguments(ID_OG_FORDELING, alder18aar, NORGE, ALDER),
                        TestArguments(PENSJON_UTLAND, alder18aar, UTLAND, ALDER),
                        TestArguments(PENSJON_UTLAND, alder17aar, null, ALDER),
                        TestArguments(ID_OG_FORDELING, alder17aar, NORGE, ALDER),
                        TestArguments(PENSJON_UTLAND, alder17aar, UTLAND, ALDER),

                        TestArguments(ID_OG_FORDELING, alder18aar, NORGE, GJENLEV),
                        TestArguments(PENSJON_UTLAND, alder18aar, UTLAND, GJENLEV),
                        TestArguments(PENSJON_UTLAND, alder17aar, null, GJENLEV),
                        TestArguments(ID_OG_FORDELING, alder17aar, NORGE, GJENLEV),
                        TestArguments(PENSJON_UTLAND, alder17aar, UTLAND, GJENLEV),

                        TestArguments(UFORE_UTLAND, alder18aar, null, UFOREP),
                        TestArguments(UFORE_UTLANDSTILSNITT, alder18aar, NORGE, UFOREP),
                        TestArguments(UFORE_UTLAND, alder18aar, UTLAND, UFOREP),
                        TestArguments(UFORE_UTLAND, alder17aar, null, UFOREP),
                        TestArguments(UFORE_UTLANDSTILSNITT, alder17aar, NORGE, UFOREP),
                        TestArguments(UFORE_UTLAND, alder17aar, UTLAND, UFOREP),

                        TestArguments(PENSJON_UTLAND, alder59aar, null, ALDER),
                        TestArguments(ID_OG_FORDELING, alder59aar, NORGE, ALDER),
                        TestArguments(PENSJON_UTLAND, alder59aar, UTLAND, ALDER),
                        TestArguments(PENSJON_UTLAND, alder60aar, null, ALDER),
                        TestArguments(ID_OG_FORDELING, alder60aar, NORGE, ALDER),
                        TestArguments(PENSJON_UTLAND, alder60aar, UTLAND, ALDER),
                    )
                )
        }

        @ParameterizedTest
        @MethodSource("arguments")
        fun Routing_P_BUC_10(arguments: TestArguments) {

            assertEquals(
                arguments.expectedResult,
                routingService.hentEnhet(
                    OppgaveRoutingRequest(
                        aktorId = AKTOER_ID,
                        fdato = arguments.alder,
                        bucType = P_BUC_10,
                        landkode = arguments.landkode,
                        saktype = arguments.saktype,
                        hendelseType = SENDT
                    )
                )
            )
        }
    }

    @Test
    fun `Routing for P_BUC_10 mottatt med bruk av Norg2 tjeneste`() {
        val enhetlist = norg2ArbeidsfordelingItemListe("/norg2/norg2arbeidsfordelig4862med-viken-result.json")
        val identifisertPerson = mockerEnPerson(landkode = "NOR", geoTilknytning = "3005", navn = "Ole Olsen")

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            ALDER,
            sedHendelse(P_BUC_10, P15000),
            MOTTATT,
            null
        )

        val result = routingService.hentEnhet(oppgaveroutingrequest)
        assertEquals(FAMILIE_OG_PENSJONSYTELSER_OSLO, result)
    }

    @Test
    fun `Gitt gjenlevendesak for P_BUC_02 mottatt når bruk av Norg2 tjeneste benyttes så routes det til PensjonUtland`() {
        val identifisertPerson = mockerEnPerson(landkode = "NOR", geoTilknytning = "3005", relasjon = GJENLEVENDE, navn = "Ole Olsen")

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
        val mappedResponse = mapJsonToAny<Norg2ArbeidsfordelingItem>(json)

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns listOf(mappedResponse)

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            GJENLEV,
            sedHendelse(sedType = P2100, land = "NO"),
            MOTTATT,
            null,
        )

        val result = routingService.hentEnhet(oppgaveroutingrequest)
        assertEquals(NFP_UTLAND_AALESUND, result)

    }

    @Test
    fun `Gitt barnePensjon for P_BUC_02 mottatt når bruk av Norg2 tjeneste benyttes så routes det til PensjonUtland`() {
        val identifisertPerson = mockerEnPerson(landkode = "SWE", geoTilknytning = "3005", relasjon = GJENLEVENDE, navn = "Ole Olsen")

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
        val mappedResponse = mapJsonToAny<Norg2ArbeidsfordelingItem>(json)

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns listOf(mappedResponse)

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            BARNEP,
            sedHendelse(sedType = P2100, land = "NO"),
            hendelseType = MOTTATT,
            null,
        )

        val result = routingService.hentEnhet(oppgaveroutingrequest)
        assertEquals(PENSJON_UTLAND, result)

    }

    @Test
    fun `Gitt aldersak for P_BUC_01 mottatt når bruk av Norg2 tjeneste benyttes så routes det til FAMILIE_OG_PENSJONSYTELSER_OSLO`() {
        val enhetlist = norg2ArbeidsfordelingItemListe("/norg2/norg2arbeidsfordelig4862med-viken-result.json")
        val identifisertPerson = mockerEnPerson(landkode = "NOR", geoTilknytning = "3005", navn = "Ole Olsen")

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            ALDER,
            sedHendelse(P_BUC_01, P2100),
            MOTTATT,
            null,
        )

        val result = routingService.hentEnhet(oppgaveroutingrequest)
        assertEquals(FAMILIE_OG_PENSJONSYTELSER_OSLO, result)

    }

    @Test
    fun `Gitt uføresak for P_BUC_02 mottatt når bruk av Norg2 tjeneste benyttes så routes det til UFORE_UTLAND`() {
        val identifisertPerson = mockerEnPerson(landkode = "SWE", geoTilknytning = null, navn = "Ole Olsen")

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
        val mappedResponse = mapJsonToAny<Norg2ArbeidsfordelingItem>(json)

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns listOf(mappedResponse)

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            UFOREP,
            sedHendelse(P_BUC_03, P2200, "SE"),
            SENDT,
            null,
        )

        val result = routingService.hentEnhet(oppgaveroutingrequest)
        assertEquals(UFORE_UTLAND, result)
    }

    class RoutingStandardBucs {
        data class TestArgumentsBucs(
            val expectedResult: Enhet,
            val bucType: BucType,
            val landkode: String? = null,
            val geografiskTilknytning: String? = null,
            val fdato: LocalDate? = null,
            val saksType: SakType? = null,
            val adressebeskyttet: Boolean? = false
        )

        private companion object {
            @JvmStatic
            fun arguments(): Stream<TestArgumentsBucs> =
                Arrays.stream(
                    arrayOf(
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_01,  DUMMY_TILKNYTNING, fdato = irrelevantDato()),
                        TestArgumentsBucs(NFP_UTLAND_AALESUND, P_BUC_01, NORGE, DUMMY_TILKNYTNING, fdato = irrelevantDato()),
                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_01, UTLAND, DUMMY_TILKNYTNING, fdato = irrelevantDato()),
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
                        TestArgumentsBucs(DISKRESJONSKODE, P_BUC_01, NORGE, fdato = alder60aar, adressebeskyttet = true, saksType = ALDER),
                        TestArgumentsBucs(UFORE_UTLANDSTILSNITT, P_BUC_03, NORGE, fdato = alder60aar, saksType = UFOREP),

                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_10, UTLAND, fdato = alder60aar, saksType = GJENLEV),
                        TestArgumentsBucs(DISKRESJONSKODE, P_BUC_10, UTLAND, fdato = alder60aar, adressebeskyttet = true, saksType = GJENLEV),
                        )
                )
        }

        @ParameterizedTest
        @MethodSource("arguments")
        fun `Ruting for vanlige BUCer`(arguments: TestArgumentsBucs) {
            assertEquals(
                arguments.expectedResult,
                routingService.hentEnhet(
                    OppgaveRoutingRequest(
                        aktorId = AKTOER_ID,
                        fdato = arguments.fdato ?: irrelevantDato(),
                        geografiskTilknytning = arguments.geografiskTilknytning,
                        bucType = arguments.bucType,
                        landkode = arguments.landkode,
                        hendelseType = SENDT,
                        harAdressebeskyttelse = arguments.adressebeskyttet ?: false,
                        saktype = arguments.saksType
                    )
                )
            )
        }
    }

    @Test
    fun `hentNorg2Enhet for bosatt utland`() {
        val enhetlist = norg2ArbeidsfordelingItemListe("/norg2/norg2arbeidsfordelig0001result.json")

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val actual = norg2Service.hentArbeidsfordelingEnhet(NorgKlientRequest(landkode = "SVE"))

        assertEquals(PENSJON_UTLAND, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge`() {
        val enhetlist = norg2ArbeidsfordelingItemListe("/norg2/norg2arbeidsfordelig4803result.json")

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val actual =
            norg2Service.hentArbeidsfordelingEnhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"))

        assertEquals(FAMILIE_OG_PENSJONSYTELSER_OSLO, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt nord-Norge`() {
        val enhetlist = norg2ArbeidsfordelingItemListe("/norg2/norg2arbeidsfordelig4862result.json")

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val actual =
            norg2Service.hentArbeidsfordelingEnhet(NorgKlientRequest(geografiskTilknytning = "1102", landkode = "NOR"))

        assertEquals(NFP_UTLAND_AALESUND, actual)
    }

    @Test
    fun `hentNorg2Enhet for diskresjonkode`() {
        val enhetlist = norg2ArbeidsfordelingItemListe("/norg2/norg2arbeidsfordeling2103result.json")

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
        val enhetlist = norg2ArbeidsfordelingItemListe("/norg2/norg2arbeidsfordeling2103result.json")
        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val actual = norg2Service.hentArbeidsfordelingEnhet(
            NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR", harAdressebeskyttelse = true)
        )

        assertEquals(DISKRESJONSKODE, actual)
    }

    @Test
    fun testEnumEnhets() {
        assertEquals(PENSJON_UTLAND, Enhet.getEnhet("0001"))
        assertEquals(FAMILIE_OG_PENSJONSYTELSER_OSLO, Enhet.getEnhet("4803"))
        assertEquals(DISKRESJONSKODE, Enhet.getEnhet("2103"))
    }

    private fun norg2ArbeidsfordelingItemListe(file: String): List<Norg2ArbeidsfordelingItem> {
        val json = javaClass.getResource(file)!!.readText()
        return mapJsonToAny(json)
    }

    fun mockerEnPerson(
        landkode: String? = "NO",
        geoTilknytning: String? = DUMMY_TILKNYTNING,
        relasjon: Relasjon? = FORSIKRET,
        navn: String? = "Testeren"
    ) = IdentifisertPDLPerson(
        aktoerId = AKTOER_ID,
        landkode,
        geografiskTilknytning = geoTilknytning,
        personRelasjon = SEDPersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), relasjon!!, rinaDocumentId =  "3123123"),
        personNavn = navn,
        identer = null
    )

    private fun sedHendelse(bucType: BucType? = P_BUC_02, sedType: SedType? = P2100, land: String ?= "NO") = SedHendelse(
        1232312L, "2321313", "P", bucType, "32131", avsenderId = "12313123",
        "SE", "SE", "2312312", land, land, "23123123", "1",
        sedType, null
    )

}
