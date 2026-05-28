package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.eux.model.sed.EessisakItem
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FiltrerPesysSakFraSedUtilTest {

    @Test
    fun `erGyldigPesysNummer godkjenner gyldige pesysnummer`() {
        assertTrue("12345678".erGyldigPesysNummer())
        assertTrue("22345678".erGyldigPesysNummer())
    }

    @Test
    fun `erGyldigPesysNummer avviser ugyldige verdier`() {
        assertFalse(null.erGyldigPesysNummer())
        assertFalse("".erGyldigPesysNummer())
        assertFalse("32345678".erGyldigPesysNummer())
        assertFalse("1234567A".erGyldigPesysNummer())
        assertFalse("1234567".erGyldigPesysNummer())
    }

    @Test
    fun `trimSakidString fjerner alle tegn som ikke er sifre`() {
        assertEquals("12345678", FiltrerPesysSakFraSedUtil.trimSakidString(" 12-34/56.78 "))
    }

    @Test
    fun `hentPesysSakIdFraSED trimmer filtrerer og dedupliserer norske saker`() {
        val sedListe = listOf(
            sed(saksnummer = "12 34-56/78"),
            sed(saksnummer = "12345678"),
            sed(saksnummer = "22345678"),
            sed(saksnummer = "12345678", land = "SE"),
            sed(saksnummer = "ugyldig")
        )

        val result = FiltrerPesysSakFraSedUtil.hentPesysSakIdFraSED(sedListe, sed(saksnummer = "12345678"))

        assertEquals(listOf("12345678"), result?.first)
        assertEquals(listOf("12345678", "22345678"), result?.second)
    }

    @Test
    fun `hentGyldigSakInformasjonFraPensjonSak returnerer null naar saklisten er tom`() {
        val result = FiltrerPesysSakFraSedUtil.hentGyldigSakInformasjonFraPensjonSak(
            aktoerId = "aktoer",
            pesysSakIdFraSed = "12345678",
            saklistFraPesys = emptyList()
        )

        assertNull(result)
    }

    @Test
    fun `hentGyldigSakInformasjonFraPensjonSak prioriterer foerste sak i P_BUC_01 naar ALDER finnes`() {
        val saker = listOf(
            sakInformasjon("22345678", ALDER),
            sakInformasjon("12345678", UFOREP)
        )

        val result = FiltrerPesysSakFraSedUtil.hentGyldigSakInformasjonFraPensjonSak(
            aktoerId = "aktoer",
            pesysSakIdFraSed = "99999999",
            saklistFraPesys = saker,
            bucType = BucType.P_BUC_01
        )

        assertEquals(saker.first(), result?.first)
        assertEquals(saker, result?.second)
    }

    @Test
    fun `hentGyldigSakInformasjonFraPensjonSak prioriterer foerste sak i P_BUC_03 naar UFOREP finnes`() {
        val saker = listOf(
            sakInformasjon("22345678", UFOREP),
            sakInformasjon("12345678", ALDER)
        )

        val result = FiltrerPesysSakFraSedUtil.hentGyldigSakInformasjonFraPensjonSak(
            aktoerId = "aktoer",
            pesysSakIdFraSed = "99999999",
            saklistFraPesys = saker,
            bucType = BucType.P_BUC_03
        )

        assertEquals(saker.first(), result?.first)
        assertEquals(saker, result?.second)
    }

    @Test
    fun `hentGyldigSakInformasjonFraPensjonSak returnerer matchende sak naar sakId finnes`() {
        val expected = sakInformasjon("12345678", ALDER)
        val saker = listOf(
            sakInformasjon("22345678", UFOREP),
            expected
        )

        val result = FiltrerPesysSakFraSedUtil.hentGyldigSakInformasjonFraPensjonSak(
            aktoerId = "aktoer",
            pesysSakIdFraSed = "12345678",
            saklistFraPesys = saker
        )

        assertEquals(expected, result?.first)
        assertEquals(saker, result?.second)
    }

    private fun sed(saksnummer: String?, land: String = "NO") = SED(
        nav = Nav(eessisak = listOf(EessisakItem(saksnummer = saksnummer, land = land)))
    )

    private fun sakInformasjon(sakId: String, sakType: no.nav.eessi.pensjon.eux.model.buc.SakType) =
        SakInformasjon(sakId = sakId, sakType = sakType, sakStatus = LOPENDE)
}
