package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_06
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("P_BUC_06 - IntegrationTest")
internal class PBuc06IntegrationTest : JournalforingTestBase() {

    /* ============================  UTGÅENDE  ============================ */
    @Nested
    @DisplayName("Utgående - Scenario 1")
    inner class Scenario1Utgaende {
        @Test
        fun `1 person i SED fnr finnes men ingen bestemsak men vi sjekker behandlingstema og at person er bosatt Norge som gir NFP_UTLAND_AALESUND`() {
            testRunner(FNR_OVER_62, saker = emptyList(), sakId = SAK_ID, sedType = SedType.P6000, bucType = P_BUC_06) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Person i SED med gyldig fnr uten sakType fra bestemsak der bruker erover 62 bosatt Norge saa rutes oppgaven til 4862 NFP_UTLAND_AALESUND`() {
            testRunner(FNR_OVER_62, saker = emptyList(), sakId = SAK_ID, bucType = P_BUC_06, sedType = SedType.P6000) {
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Person i SED med gyldig fnr uten sakType fra bestemsak bruker er under 62 bosatt Norge og en person i sed saa rutes oppgaven til 4476 UFORE_UTLANDSTILSNITT`() {
            testRunner(FNR_VOKSEN_UNDER_62, saker = emptyList(), sakId = SAK_ID, bucType = P_BUC_06, sedType = SedType.P6000) {
                assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Person i SED med gyldig fnr uten sakType fra bestemsak bruker er under 62 bosatt Sverige og en person i sed saa rutes oppgaven til 4475 UFORE_UTLAND`() {
            testRunner(FNR_VOKSEN_UNDER_62, saker = emptyList(), sakId = SAK_ID, bucType = P_BUC_06, land = "SE", sedType = SedType.P6000)  {
                assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Person i SED med gyldig fnr uten sakType fra bestemsak bruker er under 62 bosatt Sverige og en person i sed saa rutes oppgaven til 0001 PENSJON_UTLAND`() {
            testRunner(FNR_OVER_62, saker = emptyList(), sakId = SAK_ID, bucType = P_BUC_06, land = "SE", sedType = SedType.P6000)  {
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Person i SED med gyldig fnr uten sakType fra bestemsak bruker er barn bosatt Sverige og en person i sed saa rutes oppgaven til 0001 PENSJON_UTLAND`() {
            testRunner(FNR_BARN, saker = emptyList(), sakId = SAK_ID, bucType = P_BUC_06, land = "SE", sedType = SedType.P6000) {
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Person i SED med gyldig fnr uten sakType fra bestemsak bruker er barn bosatt Norge og en person i sed saa rutes oppgaven til 0001 PENSJON_UTLAND`() {
            testRunner(FNR_BARN, saker = emptyList(), sakId = SAK_ID, bucType = P_BUC_06, sedType = SedType.P6000) {
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
