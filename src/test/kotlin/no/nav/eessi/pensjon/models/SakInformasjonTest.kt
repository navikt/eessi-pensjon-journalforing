package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.skyscreamer.jsonassert.JSONAssert

internal class SakInformasjonTest {

    @ParameterizedTest
    @EnumSource(SakType::class)
    fun `Gitt at vi faar inn en sakstype`(type: SakType) {
        val json = """
            {
              "sakId":"15005679",
              "sakType":"$type",
              "sakStatus":"LOPENDE",
              "saksbehandlendeEnhetId":"",
              "nyopprettet":false
            }
        """.trimIndent()

        val actual = SakInformasjon(
                sakId = "15005679",
                sakType = type,
                sakStatus = SakStatus.LOPENDE
        ).toJson()

        JSONAssert.assertEquals(actual, json, true)

    }
}