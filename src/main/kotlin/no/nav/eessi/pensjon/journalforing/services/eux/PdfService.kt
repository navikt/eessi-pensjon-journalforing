package no.nav.eessi.pensjon.journalforing.services.eux

import io.micrometer.core.instrument.Metrics.counter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.lang.RuntimeException


@Service
class PdfService(private val euxOidcRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PdfService::class.java) }

//    private final val hentPdfTellerNavn = "eessipensjon_journalforing.henbtpdf"
//    private val hentPdfVellykkede = counter(hentPdfTellerNavn, "vellykkede")
//    private val hentPdfFeilede = counter(hentPdfTellerNavn, "feilede")

    fun hentPdf(rinaNr: String, dokumentId: String): String? {
        val path = "/buc/$rinaNr/sed/$dokumentId/pdf"

        val builder = UriComponentsBuilder.fromUriString(path).build()
        val httpEntity = HttpEntity("")

        try {
            logger.info("Kaller RINA for å hente PDF for rinaNr: $rinaNr , dokumentId: $dokumentId")
            val response = euxOidcRestTemplate.exchange(builder.toUriString(),
                    HttpMethod.GET,
                    httpEntity,
                    String::class.java)
            if (!response.statusCode.isError) {
            //    hentPdfVellykkede.increment()
                return response.body
            } else {
           //     hentPdfFeilede.increment()
                throw RuntimeException("Noe gikk galt under henting av PDF fra eux: ${response.statusCode}")
            }
        } catch (ex: Exception) {
         //   hentPdfFeilede.increment()
            logger.error("Noe gikk galt under henting av PDF fra eux: ${ex.message}")
            throw RuntimeException("Feil ved henting av PDF")
        }
    }
}
