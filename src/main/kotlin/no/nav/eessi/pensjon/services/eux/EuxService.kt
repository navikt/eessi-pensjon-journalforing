package no.nav.eessi.pensjon.services.eux

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.Metrics.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.lang.RuntimeException

@Service
class EuxService(private val euxOidcRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(EuxService::class.java) }

    private val hentPdfVellykkede = counter("eessipensjon_journalforing", "http_request", "hentpdf", "type", "vellykkede")
    private val hentPdfFeilede = counter("eessipensjon_journalforing", "http_request", "hentpdf", "type", "feilede")

    private val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)


    fun hentSedDokumenter(rinaNr: String, dokumentId: String): String? {
        val path = "/buc/$rinaNr/sed/$dokumentId/filer"
        try {
            logger.info("Henter PDF for SED og tilhørende vedlegg for rinaNr: $rinaNr , dokumentId: $dokumentId")
            val response = euxOidcRestTemplate.exchange(path,
                    HttpMethod.GET,
                    HttpEntity(""),
                    String::class.java)
            if (!response.statusCode.isError) {
                logger.info("Hentet PDF fra eux")
                return response.body
            } else {
                hentPdfFeilede.increment()
                throw RuntimeException("Noe gikk galt under henting av PDF fra eux: ${response.statusCode}")
            }
        } catch (ex: Exception) {
            hentPdfFeilede.increment()
            logger.error("Noe gikk galt under henting av PDF fra eux: ${ex.message}")
            throw RuntimeException("Feil ved henting av PDF")
        }
    }

    fun hentSed(rinaNr: String, dokumentId: String) : String? {
        val path = "/buc/$rinaNr/sed/$dokumentId"
        try {
            logger.info("Henter SED for rinaNr: $rinaNr , dokumentId: $dokumentId")
            val response = euxOidcRestTemplate.exchange(path,
                    HttpMethod.GET,
                    HttpEntity(""),
                    String::class.java)
            if (!response.statusCode.isError) {
                logger.info("Hentet SED fra eux")
                hentPdfVellykkede.increment()
                return response.body
            } else {
                hentPdfFeilede.increment()
                throw RuntimeException("Noe gikk galt under henting av SED fra eux: ${response.statusCode}")
            }
        } catch (ex: Exception) {
            hentPdfFeilede.increment()
            logger.error("Noe gikk galt under henting av SED fra eux: ${ex.message}")
            throw RuntimeException("Feil ved henting av SED")
        }
    }

    fun hentFodselsDato(rinaNr: String, dokumentId: String): String {
        val sed = hentSed(rinaNr, dokumentId)
        val rootNode = mapper.readValue(sed, JsonNode::class.java)
        val foedselsdatoNode = rootNode.path("nav")
                .path("bruker")
                .path("person")
                .path("foedselsdato")
        return foedselsdatoNode.textValue()
    }
}
