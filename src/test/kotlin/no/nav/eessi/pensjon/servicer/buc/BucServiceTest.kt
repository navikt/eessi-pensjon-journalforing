package no.nav.eessi.pensjon.servicer.buc

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
internal class BucServiceTest {

    private val euxKlient: EuxKlient = mockk(relaxed = true)
    private val bucService = BucService(euxKlient)

    @Test
    fun`Gitt en BUC hvor norge er caseowner så returner isCaseowner true`() {
        val buc = javaClass.classLoader.getResource("eux/buc/bucNorskCaseOwner.json")!!.readText()
        every { euxKlient.hentBuc(any()) }.answers { buc }

        assertTrue(bucService.isCaseOwner("1234"))
    }

    @Test
    fun`Gitt en BUC hvor norge ikke er caseowner så returner isCaseowner false`() {
        val buc = javaClass.classLoader.getResource("eux/buc/bucIkkeNorskCaseOwner.json")!!.readText()
        every { euxKlient.hentBuc(any()) }.answers { buc }

        assertFalse(bucService.isCaseOwner("1234"))
    }
}