package no.nav.eessi.pensjon.service.buc

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.eux.ParticipantHolder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BucServiceTest {

    private val euxKlient: EuxKlient = mockk(relaxed = true)
    private val bucService = BucService(euxKlient)

    @Test
    fun `Gitt en BUC hvor norge er caseowner så returner isCaseowner true`() {
        val buc = javaClass.classLoader.getResource("eux/buc/bucNorskCaseOwner.json")!!.readText()
        val institusjonerIBuc = mapJsonToAny(buc, typeRefs<ParticipantHolder>()).participants

        every { euxKlient.hentInstitusjonerIBuc(any()) } returns institusjonerIBuc

        assertTrue(bucService.isCaseOwner("1234"))
    }

    @Test
    fun `Gitt en BUC hvor norge ikke er caseowner så returner isCaseowner false`() {
        val buc = javaClass.classLoader.getResource("eux/buc/bucIkkeNorskCaseOwner.json")!!.readText()
        val institusjonerIBuc = mapJsonToAny(buc, typeRefs<ParticipantHolder>()).participants

        every { euxKlient.hentInstitusjonerIBuc(any()) } returns institusjonerIBuc

        assertFalse(bucService.isCaseOwner("1234"))
    }
}