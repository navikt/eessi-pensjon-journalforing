package no.nav.eessi.pensjon.klienter.fagmodul

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

class FagmodulKlientTest {

    private val fagmodulOidcRestTemplate: RestTemplate = mockk()
    private val fagmodulKlient = FagmodulKlient(fagmodulOidcRestTemplate)

    @Test
    fun `Gitt at saktypen ikke er en enum så skal vi kaste RuntimeException`() {
        val jsonResponse = getJsonBody("PAM_PAM")

        every { fagmodulKlientExchange() } returns ResponseEntity(jsonResponse, HttpStatus.OK)

        assertThrows<RuntimeException> {
            fagmodulKlient.hentPensjonSaklist("321654987")
        }
    }

    @Test
    fun `Gitt at apikallet feiler så skal vi logge en error og returnere en tom liste`() {
        every { fagmodulKlientExchange() } throws  RuntimeException("En feil oppstod under henting av pensjonsakliste")

        assertEquals(emptyList<SakInformasjon>(), fagmodulKlient.hentPensjonSaklist("321654987"))
    }

    @Test
    fun `Gitt at vi har sakType Alder, så skal vi returnere en liste over saksinformasjon`() {
        val jsonBody = getJsonBody(ALDER.name)

        every { fagmodulKlientExchange() } returns ResponseEntity(jsonBody, HttpStatus.OK)

        val result = fagmodulKlient.hentPensjonSaklist("321654987")

        assertEquals(jsonBody, result.toJson())

    }

    private fun fagmodulKlientExchange() = fagmodulOidcRestTemplate.exchange(
        "/pensjon/sakliste/321654987",
        HttpMethod.GET,
        null,
        String::class.java
    )

    private fun getJsonBody(sakType: String) = """
                [ {
                  "sakId" : "321",
                  "sakType" : "$sakType",
                  "sakStatus" : "LOPENDE",
                  "saksbehandlendeEnhetId" : "",
                  "nyopprettet" : false
                } ]
            """.trimIndent()
}