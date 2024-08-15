package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
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

        val actual = journalforingService.hentTema(sedHendelse, Fodselsnummer.fra(fnr), 1, null, null, sed)
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

        val actual = journalforingService.hentTema(sedHendelse, Fodselsnummer.fra(fnr), 1, null, null, sed)
        assertEquals(tema, actual.toString())
    }

    @ParameterizedTest
    @DisplayName("PBUC_06 - P7000")
    @CsvSource(
        "09035225916, 01, PENSJON",
        "09035225916, 03, PENSJON",
        "11067122781, 02, UFORETRYGD"
    )
    fun `Gitt en P7000 med enkeltkrav krav med type ufore eller pensjon så skal tema bli deretter`(fnr: String, penType: String, tema: String) {

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

        val actual = journalforingService.hentTema(sedHendelse, Fodselsnummer.fra(fnr), 1, null, null, sed)
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

        val actual = journalforingService.hentTema(sedHendelse, Fodselsnummer.fra(fnr), 1, null, null, sed)
        assertEquals(tema, actual.toString())
    }



}
