package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.H_BUC_07
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_03
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_04
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_05
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_10
import no.nav.eessi.pensjon.eux.model.BucType.R_BUC_02
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class BucHandlerTest {

    @Test
    fun `Verifiser at riktig handler blir valgt`() {
        // P_PUC_*
        val pbuc01 = EnhetFactory.hentHandlerFor(P_BUC_01)
        assertTrue(pbuc01 is Pbuc01)

        val pbuc02 = EnhetFactory.hentHandlerFor(P_BUC_02)
        assertTrue(pbuc02 is Pbuc02)

        val pbuc03 = EnhetFactory.hentHandlerFor(P_BUC_03)
        assertTrue(pbuc03 is Pbuc03)

        val pbuc04 = EnhetFactory.hentHandlerFor(P_BUC_04)
        assertTrue(pbuc04 is Pbuc04)

        val pbuc05 = EnhetFactory.hentHandlerFor(P_BUC_05)
        assertTrue(pbuc05 is Pbuc05)

        val pbuc10 = EnhetFactory.hentHandlerFor(P_BUC_10)
        assertTrue(pbuc10 is Pbuc10)

        // H_BUC_07
        val hbuc07 = EnhetFactory.hentHandlerFor(H_BUC_07)
        assertTrue(hbuc07 is Hbuc07)

        // R_BUC_02
        val rbuc02 = EnhetFactory.hentHandlerFor(R_BUC_02)
        assertTrue(rbuc02 is Rbuc02)
    }

    @ParameterizedTest
    @EnumSource(value = BucType::class, names = ["P_BUC_06", "P_BUC_07", "P_BUC_08", "P_BUC_09"])
    fun `P_BUC_06, 07, 08, og 09 skal brukes default handler`(bucType: BucType) {
        val handler = EnhetFactory.hentHandlerFor(bucType)
        assertTrue(handler is DefaultEnhetHandler)
    }

}