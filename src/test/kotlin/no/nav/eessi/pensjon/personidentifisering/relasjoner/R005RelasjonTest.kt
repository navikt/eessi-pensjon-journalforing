package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

internal class R005RelasjonTest : RelasjonTestBase(){

    @Test
    fun hentRelasjoner() {
        val forsikretFnr = SLAPP_SKILPADDE
        val annenPersonFnr = KRAFTIG_VEGGPRYD

        val actual = R005Relasjon(
            createR005(
                forsikretFnr = forsikretFnr, forsikretTilbakekreving = "debitor",
                annenPersonFnr = annenPersonFnr, annenPersonTilbakekreving = "debitor"
            ),
            BucType.R_BUC_02,
            "123123"
        ).hentRelasjoner()

        val forste = SEDPersonRelasjon(Fodselsnummer.fra(forsikretFnr), Relasjon.ANNET, sedType = SedType.R005, fdato = LocalDate.of(1952,3,9), rinaDocumentId = "123123")
        val andre = SEDPersonRelasjon(Fodselsnummer.fra(annenPersonFnr), Relasjon.ANNET, sedType = SedType.R005, fdato = LocalDate.of(1971,6,11), rinaDocumentId = "123123")

        Assertions.assertEquals(2, actual.size)
        assertTrue(actual.contains(forste))
        assertTrue(actual.contains(andre))

    }

    @Test
    fun `Gitt et gyldig fnr og relasjon avdod så skal det identifiseres en person`() {
        val gjenlevFnr = LEALAUS_KAKE

        val sedjson = createR005(
            forsikretFnr = SLAPP_SKILPADDE, forsikretTilbakekreving = "avdød_mottaker_av_ytelser",
            annenPersonFnr = gjenlevFnr, annenPersonTilbakekreving = "enke_eller_enkemann"
        ).toJson()
        val sed = mapJsonToAny(sedjson, typeRefs<R005>())

        val relasjon = R005Relasjon(sed, BucType.R_BUC_02, "23123").hentRelasjoner()

        assertEquals(2, relasjon.size)
    }
}