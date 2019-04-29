package no.nav.eessi.pensjon.journalforing.services.eux

import io.micrometer.core.instrument.Metrics.counter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder


@Service
class PdfService(private val rinaRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PdfService::class.java) }

    private final val hentPdfTellerNavn = "eessipensjon_frontend-api.hentpesoninformasjon"
    private val hentPdfVellykkede = counter(hentPdfTellerNavn, "vellykkede")
    private val hentPdfFeilede = counter(hentPdfTellerNavn, "feilede")

    fun hentPdf(rinaNr: String, dokumentId: String) {
        val path = "/buc/{$rinaNr}/sed/{$dokumentId}/pdf"

        val builder = UriComponentsBuilder.fromUriString(path).build()
        val httpEntity = HttpEntity("")

        try {
            logger.info("Kaller RINA for å hente PDF")
            val response = fagmodulUntToRestTemplate.exchange(builder.toUriString(),
                    HttpMethod.GET,
                    httpEntity,
                    Personinformasjon::class.java)
            if (!response.statusCode.isError) {
                hentPesoninformasjonVellykkede.increment()
                return response.body
            } else {
                hentPesoninformasjonFeilede.increment()
                throw PersonInformasjonException("Noe gikk galt under henting av persjoninformasjon fra fagmodulen: ${response.statusCode}")
            }
        } catch (ex: Exception) {
            hentPesoninformasjonFeilede.increment()
            logger.error("Noe gikk galt under henting av persjoninformasjon fra fagmodulen: ${ex.message}")
            throw PersonInformasjonException("Feil ved henting av Personinformasjon")
        }
    }
