package no.nav.eessi.pensjon.models.sed

import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod.ADOPTIVBARN
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod.ANNEN_SLEKTNING
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod.BARNEBARN
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod.EGET_BARN
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod.EKTEFELLE
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod.FOSTERBARN
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod.PART_I_ET_REGISTRERT_PARTNERSKAP
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod.SAMBOER
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod.STEBARN
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod.SØSKEN
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod.TIDLIGERE_EKTEFELLE
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod.TIDLIGERE_PARTNER_I_ET_REGISTRERT_PARTNERSKAP
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RelasjonTilAvdodTest {

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
}
