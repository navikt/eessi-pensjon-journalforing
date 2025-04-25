package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_06
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class PBuc06IntegrationTest : JournalforingTestBase() {

    @ParameterizedTest
    @DisplayName("PBUC_06 - P6000")
    @CsvSource(
        "09035225916, 10, PENSJON",
        "09035225916, 20, PENSJON",
        "11067122781, 30, UFORETRYGD"
    )
    fun `Gitt en P6000 med enkeltkrav type ufore eller pensjon så skal tema bli deretter`(fnr: String, kravType: String, tema: String) {

        val sed = SED(
            type = SedType.P6000,
            pensjon = P6000Pensjon(
                vedtak = listOf(VedtakItem(type = kravType))
            )
        )
        val sedSendtJson = javaClass.getResource("/eux/hendelser/P_BUC_06_P6000.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(sedSendtJson)

        val actual = hentTemaService.hentTema(sedHendelse, Fodselsnummer.fra(fnr)?.getAge(), 1, null, sed)
        assertEquals(tema, actual.toString())
    }

    @ParameterizedTest
    @DisplayName("P_BUC_06 - P5000")
    @CsvSource(
        "09035225916, 10, PENSJON",
        "09035225916, 20, PENSJON",
        "11067122781, 30, UFORETRYGD"
    )
    fun `Gitt en P5000 med enkeltkrav krav med type ufore eller pensjon så skal tema bli deretter`(fnr: String, kravType: String, tema: String) {

        val sed = SED(
            type = SedType.P5000,
            pensjon = P5000Pensjon(
                medlemskapboarbeid = Medlemskapboarbeid(
                    enkeltkrav = KravtypeItem(krav = kravType)
                )
            )
        )
        val sedSendtJson = javaClass.getResource("/eux/hendelser/P_BUC_06_P5000.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(sedSendtJson)

        val actual = hentTemaService.hentTema(sedHendelse, Fodselsnummer.fra(fnr)?.getAge(), 1, null, sed)
        assertEquals(tema, actual.toString())
    }

    @ParameterizedTest
    @DisplayName("PBUC_06 - P7000")
    @CsvSource(
        "09035225916, 01, PENSJON",
        "09035225916, 03, PENSJON",
        "11067122781, 02, UFORETRYGD"
    )
    fun `Gitt en P7000 med enkeltkrav krav med type ufore eller pensjon s å skal tema bli deretter`(fnr: String, penType: String, tema: String) {

        val sed = SED(
            type = SedType.P7000,
            pensjon = P7000Pensjon(
                samletVedtak = SamletMeldingVedtak(
                    tildeltepensjoner = listOf(TildeltPensjonItem(pensjonType = penType))
                )
            )
        )
        val sedSendtJson = javaClass.getResource("/eux/hendelser/P_BUC_06_P7000.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(sedSendtJson)

        val actual = hentTemaService.hentTema(sedHendelse, Fodselsnummer.fra(fnr)?.getAge(), 1,null, sed)

        assertEquals(tema, actual.toString())
    }

    @ParameterizedTest
    @DisplayName("PBUC_06 - P10000")
    @CsvSource(
        "09035225916, 10, PENSJON",
        "09035225916, 11, PENSJON",
        "11067122781, 08, UFORETRYGD"
    )
    fun `Gitt en P10000 med pensjonstype ufore eller pensjon så skal tema bli deretter`(fnr: String, penType: String, tema: String) {

        val sed = SED(
            type = SedType.P10000,
            pensjon = P10000Pensjon(
                merinformasjon = MerInformasjon(
                    listOf(YtelseItem(ytelsestype = penType))
                )
            )
        )
        val sedSendtJson = javaClass.getResource("/eux/hendelser/P_BUC_06_P10000.json")!!.readText()
        val sedHendelse = SedHendelse.fromJson(sedSendtJson)

        val actual = hentTemaService.hentTema(sedHendelse, Fodselsnummer.fra(fnr)?.getAge(), 1, null, sed)
        assertEquals(tema, actual.toString())
    }

    /* ============================  UTGÅENDE  ============================ */
    @Nested
    @DisplayName("Utgående - Scenario 1")
    inner class Scenario1Utgaende {
        @Test
        fun `1 person i SED fnr finnes men ingen bestemsak men vi sjekker behandlingstema og at person er bosatt Norge som gir NFP_UTLAND_AALESUND`() {
            testRunnerPBuc06(FNR_OVER_62, saker = emptyList(), sakId = SAK_ID, sedType = SedType.P6000, bucType = P_BUC_06) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Person i SED med gyldig fnr uten sakType fra bestemsak der bruker erover 62 bosatt Norge saa rutes oppgaven til 4862 NFP_UTLAND_AALESUND`() {
            testRunnerPBuc06(FNR_OVER_62, saker = emptyList(), sakId = SAK_ID, bucType = P_BUC_06, sedType = SedType.P6000) {
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Person i SED med gyldig fnr uten sakType fra bestemsak bruker er under 62 bosatt Norge og en person i sed saa rutes oppgaven til 4476 UFORE_UTLANDSTILSNITT`() {
            testRunnerPBuc06(FNR_VOKSEN_UNDER_62, saker = emptyList(), sakId = SAK_ID, bucType = P_BUC_06, sedType = SedType.P6000) {
                assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Person i SED med gyldig fnr uten sakType fra bestemsak bruker er under 62 bosatt Sverige og en person i sed saa rutes oppgaven til 4475 UFORE_UTLAND`() {
            testRunnerPBuc06(FNR_VOKSEN_UNDER_62, saker = emptyList(), sakId = SAK_ID, bucType = P_BUC_06, land = "SE", sedType = SedType.P6000)  {
                assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Person i SED med gyldig fnr uten sakType fra bestemsak bruker er under 62 bosatt Sverige og en person i sed saa rutes oppgaven til 0001 PENSJON_UTLAND`() {
            testRunnerPBuc06(FNR_OVER_62, saker = emptyList(), sakId = SAK_ID, bucType = P_BUC_06, land = "SE", sedType = SedType.P6000)  {
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Person i SED med gyldig fnr uten sakType fra bestemsak bruker er barn bosatt Sverige og en person i sed saa rutes oppgaven til 0001 PENSJON_UTLAND`() {
            testRunnerPBuc06(FNR_BARN, saker = emptyList(), sakId = SAK_ID, bucType = P_BUC_06, land = "SE", sedType = SedType.P6000) {
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Person i SED med gyldig fnr uten sakType fra bestemsak bruker er barn bosatt Norge og en person i sed saa rutes oppgaven til 0001 PENSJON_UTLAND`() {
            testRunnerPBuc06(FNR_BARN, saker = emptyList(), sakId = SAK_ID, bucType = P_BUC_06, sedType = SedType.P6000) {
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `1 person i SED, men fnr er feil`() {
            testRunner(fnr = "123456789102356878546525468432", sedType = SedType.P6000, bucType = P_BUC_06) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `1 person i SED, men fnr mangler`() {
            testRunner(fnr = null, sedType = SedType.P6000, bucType = P_BUC_06) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }

    /* ============================ INNGÅENDE ============================ */
    @Nested
    @DisplayName("Inngående - Scenario 1")
    inner class Scenario1Inngaende {
        @Test
        fun `Kun én person, mangler FNR`() {
            testRunner(fnr = null, hendelseType = MOTTATT, bucType = P_BUC_06) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Kun én person, ugyldig FNR`() {
            testRunner(fnr = "1244091349018340918341029", hendelseType = MOTTATT, bucType = P_BUC_06) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }
}
