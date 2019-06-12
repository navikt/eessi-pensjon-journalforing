package no.nav.eessi.pensjon.journalforing.services.eux

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.utils.counter
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
    private final val hentPdfTellerNavn = "eessipensjon_journalforing.hentpdf"
    private val hentPdfVellykkede = counter(hentPdfTellerNavn, "vellykkede")
    private val hentPdfFeilede = counter(hentPdfTellerNavn, "feilede")
    private val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)


    fun hentSedDokumenter(rinaNr: String, dokumentId: String): SedDokumenterResponse {
        val path = "/buc/$rinaNr/sed/$dokumentId/filer"
        try {
            logger.info("Henter PDF for SED og tilhørende vedlegg for rinaNr: $rinaNr , dokumentId: $dokumentId")
            val response = euxOidcRestTemplate.exchange(path,
                    HttpMethod.GET,
                    HttpEntity(""),
                    String::class.java)
            if (!response.statusCode.isError) {
                logger.info("Hentet PDF fra eux")
                val resp  = mapper.readValue(response.body, SedDokumenterResponse::class.java)
                logger.info("Mappet response til SedDokumenterResponse")
                hentPdfVellykkede.increment()
                return resp
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
}
