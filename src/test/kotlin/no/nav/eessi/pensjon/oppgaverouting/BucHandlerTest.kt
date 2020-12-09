package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.BucType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class BucHandlerTest {

    @Test
    fun `Verifiser at riktig handler blir valgt`() {
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
        assertTrue(pbuc05 is Pbuc05)

        val pbuc10 = BucTilEnhetHandlerCreator.getHandler(BucType.P_BUC_10)
        assertTrue(pbuc10 is Pbuc10)

        // H_BUC_07
        val hbuc07 = BucTilEnhetHandlerCreator.getHandler(BucType.H_BUC_07)
        assertTrue(hbuc07 is Hbuc07)

        // R_BUC_02
        val rbuc02 = BucTilEnhetHandlerCreator.getHandler(BucType.R_BUC_02)
        assertTrue(rbuc02 is Rbuc02)
    }

    @ParameterizedTest
    @EnumSource(value = BucType::class, names = ["P_BUC_06", "P_BUC_07", "P_BUC_08", "P_BUC_09"])
    fun `P_BUC_06, 07, 08, og 09 skal brukes default handler`(bucType: BucType) {
        val handler = BucTilEnhetHandlerCreator.getHandler(bucType)
        assertTrue(handler is DefaultBucTilEnhetHandler)
    }

    @Test
    fun `Ukjent BucType skal kaste exception`() {
        assertThrows<RuntimeException> {
            BucTilEnhetHandlerCreator.getHandler(null)
        }
    }

}