package no.nav.eessi.pensjon.services.fagmodul

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.doThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.RestClientException


@ExtendWith(MockitoExtension::class)
class FagmodulServiceTest {

    @Mock
    private lateinit var mockrestTemplate: RestTemplate

    lateinit var fagmodulService: FagmodulService

    private val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)

    @BeforeEach
    fun setup() {
        fagmodulService = FagmodulService(mockrestTemplate)
    }

    @Test
    fun `Gitt OK response når etterspør pin og ytelsetype så motta pin og ytelsetype`() {
        val rinaNr = "123"
        val dokumentId = "456"

        val responseBody = mapper.readValue(String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/pinogytelseResponse.json"))),
                HentPinOgYtelseTypeResponse::class.java)
        val responseEntity = ResponseEntity(responseBody, HttpStatus.OK)

        doReturn(responseEntity)
                .`when`(mockrestTemplate).exchange(
                        eq("/sed/ytelseKravtype/$rinaNr/sedid/$dokumentId"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(HentPinOgYtelseTypeResponse::class.java))

        val resp = fagmodulService.hentPinOgYtelseType(rinaNr, dokumentId)
        assertEquals(resp!!.fnr, "12345678910")
        assertEquals(resp.krav!!.type!!.name, "AP")
    }

    @Test
    fun `Gitt NOT_FOUND når etterspør pin og ytelsetype så kast exception`() {
        val rinaNr = "123"
        val dokumentId = "456"

        val responseBody = "Det oppstod en feil"
        val responseEntity = ResponseEntity(responseBody, HttpStatus.NOT_FOUND)

        doReturn(responseEntity)
                .`when`(mockrestTemplate).exchange(
                        eq("/sed/ytelseKravtype/$rinaNr/sedid/$dokumentId"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(HentPinOgYtelseTypeResponse::class.java))

        assertThrows<RuntimeException> {
            fagmodulService.hentPinOgYtelseType(rinaNr, dokumentId)
        }
    }

    @Test
    fun `Gitt RestClientException når etterspør pin og ytelsetype så kast exception`() {
        val rinaNr = "123"
        val dokumentId = "456"

        doThrow(RestClientException("oops"))
                .`when`(mockrestTemplate).exchange(
                        eq("/sed/ytelseKravtype/$rinaNr/sedid/$dokumentId"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(HentPinOgYtelseTypeResponse::class.java))
        assertThrows<RuntimeException> {
            fagmodulService.hentPinOgYtelseType(rinaNr, dokumentId)
        }
    }

    @Test
    fun `Gitt OK response når etterspør fodselsdato så motta fodselsdato`() {
        val rinaNr = "123"
        val buctype = "P_BUC_01"

        val responseEntity = ResponseEntity("2011-05-28", HttpStatus.OK)

        doReturn(responseEntity)
                .`when`(mockrestTemplate).exchange(
                        eq("/sed/fodselsdato/$rinaNr/buctype/$buctype"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(String::class.java))

        val resp = fagmodulService.hentFodselsdatoFraBuc(rinaNr, buctype)
        assertEquals(resp, "2011-05-28")
    }

    @Test
    fun `Gitt NOT_FOUND når etterspør fodselsdato så kast exception`() {
        val rinaNr = "123"
        val buctype = "P_BUC_01"

        val responseBody = "Det oppstod en feil"
        val responseEntity = ResponseEntity(responseBody, HttpStatus.NOT_FOUND)

        doReturn(responseEntity)
                .`when`(mockrestTemplate).exchange(
                        eq("/fodselsdato/$rinaNr/buctype/$buctype"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(String::class.java))
        assertThrows<RuntimeException> {
            fagmodulService.hentFodselsdatoFraBuc(rinaNr, buctype)
        }
    }

    @Test
    fun `Gitt RestClientException når etterspør fodselsdato så kast exception`() {
        val rinaNr = "123"
        val buctype = "P_BUC_01"

        doThrow(RestClientException("oops"))
                .`when`(mockrestTemplate).exchange(
                        eq("/sed/ytelseKravtype/$rinaNr/sedid/$buctype"),
                        any(HttpMethod::class.java),
                        any(HttpEntity::class.java),
                        eq(HentPinOgYtelseTypeResponse::class.java))
        assertThrows<RuntimeException> {
            fagmodulService.hentFodselsdatoFraBuc(rinaNr, buctype)
        }
    }
}
