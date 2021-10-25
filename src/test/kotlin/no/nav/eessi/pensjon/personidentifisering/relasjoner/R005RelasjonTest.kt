package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.BucType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class R005RelasjonTest : RelasjonTestBase(){

    @Test
    fun `Gitt personer med rolle ANNET når personrelasjoner velges så ignorer disse`() {
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

        Assertions.assertEquals(0, actual.size)
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