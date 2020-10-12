package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.BucType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class BucHandlerTest {

    @Test
    fun `Verifiser at riktig handler blir valgt`() {
        // null
        val resultat_null = BucTilEnhetHandlerCreator.getHandler(null)
        assertTrue(resultat_null is DefaultBucTilEnhetHandler)

        // P_PUC_*
        val pbuc01 = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_01)
        assertTrue(pbuc01 is Pbuc01)

        val pbuc02 = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_02)
        assertTrue(pbuc02 is Pbuc02)

        val pbuc03 = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_03)
        assertTrue(pbuc03 is Pbuc03)

        val pbuc04 = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_04)
        assertTrue(pbuc04 is Pbuc04)

        val pbuc05 = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_05)
        assertTrue(pbuc05 is DefaultBucTilEnhetHandler)

        val pbuc06 = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_06)
        assertTrue(pbuc06 is DefaultBucTilEnhetHandler)

        val pbuc07 = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_07)
        assertTrue(pbuc07 is DefaultBucTilEnhetHandler)

        val pbuc08 = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_08)
        assertTrue(pbuc08 is DefaultBucTilEnhetHandler)

        val pbuc09 = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_09)
        assertTrue(pbuc09 is DefaultBucTilEnhetHandler)

        val pbuc10 = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_10)
        assertTrue(pbuc10 is Pbuc10)

        // H_BUC_07
        val hbuc07 = BucTilEnhetHandlerCreator.getHandler(BucType.H_BUC_07)
        assertTrue(hbuc07 is Hbuc07)

        // R_BUC_02
        val rbuc02 = BucTilEnhetHandlerCreator.getHandler(BucType.R_BUC_02)
        assertTrue(rbuc02 is Rbuc02)
    }
}