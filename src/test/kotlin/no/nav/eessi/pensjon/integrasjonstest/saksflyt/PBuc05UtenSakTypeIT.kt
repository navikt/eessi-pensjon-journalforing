package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import no.nav.eessi.pensjon.models.Behandlingstema.UFOREPENSJON
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.logger
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
            assertEquals(UFORETRYGD, it.tema)
            assertEquals(Enhet.ID_OG_FORDELING, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `2 personer i SED, har rolle barn 01 etterlatte `() {
        testRunnerFlerePersoner(
            FNR_VOKSEN_UNDER_62,
            FNR_BARN, emptyList(), rolle = Rolle.ETTERLATTE) {
            assertEquals(UFORETRYGD, it.tema)
            assertEquals(Enhet.NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
        }
    }

    @Test
    fun `2 personer i SED, ingen rolle`() {
        testRunnerFlerePersoner(
            FNR_VOKSEN_UNDER_62,
            FNR_BARN, emptyList(), rolle = null) {
            logger.info("logging: $it")
            assertEquals(UFORETRYGD, it.tema)
            assertEquals(UFOREPENSJON, it.behandlingstema)
            assertEquals(Enhet.UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)

        }
    }

}