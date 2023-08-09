package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PBuc05UtenSakTypeIT : JournalforingTestBase(){

    @Test
    fun `Gitt 2 personer i SED, har rolle barn 03 og sak er`() {
        testRunnerFlerePersoner(
            FNR_OVER_62,
            FNR_BARN, emptyList(), rolle = Rolle.BARN) {
            assertEquals(PENSJON, it.tema)
            assertEquals(Enhet.NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `2 personer i SED, har rolle barn 02 FORSORGER`() {
        testRunnerFlerePersoner(
            FNR_VOKSEN_UNDER_62,
            FNR_BARN, emptyList(), rolle = Rolle.FORSORGER) {
            assertEquals(PENSJON, it.tema)
            assertEquals(Enhet.ID_OG_FORDELING, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `2 personer i SED, har rolle barn 01 etterlatte `() {
        testRunnerFlerePersoner(
            FNR_VOKSEN_UNDER_62,
            FNR_BARN, emptyList(), rolle = Rolle.ETTERLATTE) {
            assertEquals(PENSJON, it.tema)
            assertEquals(Enhet.NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `2 personer i SED, har rolle barn 03 `() {
        testRunnerFlerePersoner(
            FNR_VOKSEN_UNDER_62,
            FNR_BARN, emptyList(), rolle = null) {
            assertEquals(PENSJON, it.tema)
            assertEquals(Enhet.UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
        }
    }

}