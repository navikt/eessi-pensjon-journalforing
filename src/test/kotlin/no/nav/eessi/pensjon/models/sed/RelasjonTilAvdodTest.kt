package no.nav.eessi.pensjon.models.sed

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod.ADOPTIVBARN
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod.ANNEN_SLEKTNING
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod.BARNEBARN
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod.EGET_BARN
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod.EKTEFELLE
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod.FOSTERBARN
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod.PART_I_ET_REGISTRERT_PARTNERSKAP
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod.SAMBOER
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod.STEBARN
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod.SØSKEN
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod.TIDLIGERE_EKTEFELLE
import no.nav.eessi.pensjon.models.sed.RelasjonTilAvdod.TIDLIGERE_PARTNER_I_ET_REGISTRERT_PARTNERSKAP
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class RelasjonTilAvdodTest {

    @Test
    fun `Relasjon til avdod serde`() {
        assertEquals(EKTEFELLE, serde(EKTEFELLE))
        assertEquals(PART_I_ET_REGISTRERT_PARTNERSKAP, serde(PART_I_ET_REGISTRERT_PARTNERSKAP))
        assertEquals(SAMBOER, serde(SAMBOER))
        assertEquals(TIDLIGERE_EKTEFELLE, serde(TIDLIGERE_EKTEFELLE))
        assertEquals(TIDLIGERE_PARTNER_I_ET_REGISTRERT_PARTNERSKAP, serde(TIDLIGERE_PARTNER_I_ET_REGISTRERT_PARTNERSKAP))
        assertEquals(EGET_BARN, serde(EGET_BARN))
        assertEquals(ADOPTIVBARN, serde(ADOPTIVBARN))
        assertEquals(FOSTERBARN, serde(FOSTERBARN))
        assertEquals(STEBARN, serde(STEBARN))
        assertEquals(BARNEBARN, serde(BARNEBARN))
        assertEquals(SØSKEN, serde(SØSKEN))
        assertEquals(ANNEN_SLEKTNING, serde(ANNEN_SLEKTNING))
    }

    @Test
    fun `Kode til avdod relasjon`() {
        val ektefelleJson = EKTEFELLE.toJson()
        assertEquals("\"01\"", ektefelleJson)

        val partIEtRegistrertPartnerskapJson = PART_I_ET_REGISTRERT_PARTNERSKAP.toJson()
        assertEquals("\"02\"", partIEtRegistrertPartnerskapJson)

        val samboerJson = SAMBOER.toJson()
        assertEquals("\"03\"", samboerJson)

        val tidligereEktefelleJson = TIDLIGERE_EKTEFELLE.toJson()
        assertEquals("\"04\"", tidligereEktefelleJson)

        val tidligerePartnerIEtRegistrertPartnerskapJson = TIDLIGERE_PARTNER_I_ET_REGISTRERT_PARTNERSKAP.toJson()
        assertEquals("\"05\"", tidligerePartnerIEtRegistrertPartnerskapJson)

        val egetBarnJson = EGET_BARN.toJson()
        assertEquals("\"06\"", egetBarnJson)

        val adoptivbarnJson = ADOPTIVBARN.toJson()
        assertEquals("\"07\"", adoptivbarnJson)

        val fosterbarnJson = FOSTERBARN.toJson()
        assertEquals("\"08\"", fosterbarnJson)

        val stebarnJson = STEBARN.toJson()
        assertEquals("\"09\"", stebarnJson)

        val barnebarnJson = BARNEBARN.toJson()
        assertEquals("\"10\"", barnebarnJson)

        val søskenJson = SØSKEN.toJson()
        assertEquals("\"11\"", søskenJson)

        val annenSlektningJson = ANNEN_SLEKTNING.toJson()
        assertEquals("\"12\"", annenSlektningJson)
    }

    @Test
    fun `Gjenlevende barn`() {
        assertTrue(EGET_BARN.erGjenlevendeBarn())
        assertTrue(ADOPTIVBARN.erGjenlevendeBarn())
        assertTrue(FOSTERBARN.erGjenlevendeBarn())
        assertTrue(STEBARN.erGjenlevendeBarn())

        assertFalse(EKTEFELLE.erGjenlevendeBarn())
        assertFalse(PART_I_ET_REGISTRERT_PARTNERSKAP.erGjenlevendeBarn())
        assertFalse(SAMBOER.erGjenlevendeBarn())
        assertFalse(TIDLIGERE_EKTEFELLE.erGjenlevendeBarn())
        assertFalse(TIDLIGERE_PARTNER_I_ET_REGISTRERT_PARTNERSKAP.erGjenlevendeBarn())
        assertFalse(BARNEBARN.erGjenlevendeBarn())
        assertFalse(SØSKEN.erGjenlevendeBarn())
        assertFalse(ANNEN_SLEKTNING.erGjenlevendeBarn())
    }

    private fun serde(relasjon: RelasjonTilAvdod): RelasjonTilAvdod? {
        val json = RelasjonAvdodItem(relasjon).toJson()

        val item = mapJsonToAny(json, typeRefs<RelasjonAvdodItem>())

        return item.relasjon
    }
}
