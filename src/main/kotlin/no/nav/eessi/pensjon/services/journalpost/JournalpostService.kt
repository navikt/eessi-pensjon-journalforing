package no.nav.eessi.pensjon.services.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.BucType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.HttpStatusCodeException

/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Service
class JournalpostService(
        private val journalpostOidcRestTemplate: RestTemplate,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(JournalpostService::class.java) }
    private val mapper = jacksonObjectMapper()
    private final val TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY = "eessi_pensjon_bucid"

    @Value("\${no.nav.orgnummer}")
    private lateinit var navOrgnummer: String

    fun opprettJournalpost(
            rinaSakId: String,
            navBruker: String?,
            personNavn: String?,
            avsenderId: String?,
            avsenderNavn: String?,
            mottakerId: String,
            mottakerNavn: String,
            bucType: String,
            sedType: String,
            sedHendelseType: String,
            eksternReferanseId: String?,
            kanal: String?,
            journalfoerendeEnhet: String?,
            arkivsaksnummer: String?,
            arkivsaksystem: String?,
            dokumenter: String,
            forsokFerdigstill: Boolean? = false
    ): String? {

        val avsenderMottaker = populerAvsenderMottaker(
                navBruker,
                sedHendelseType
        )
        val behandlingstema = BucType.valueOf(bucType).BEHANDLINGSTEMA
        val bruker = when (navBruker){
            null -> null
            else -> Bruker(id = navBruker)
        }
        val journalpostType = populerJournalpostType(sedHendelseType)
        val sak = populerSak(arkivsaksnummer, arkivsaksystem)
        val tema = BucType.valueOf(bucType).TEMA
        val tilleggsopplysninger = populerTilleggsopplysninger(rinaSakId)
        val tittel = "${journalpostType.decode()} $sedType"

        val requestBody = JournalpostRequest(
                avsenderMottaker,
                behandlingstema,
                bruker,
                dokumenter,
                eksternReferanseId,
                journalfoerendeEnhet,
                journalpostType,
                kanal,
                sak,
                tema,
                tilleggsopplysninger,
                tittel)


        //Send Request
        val path = "/journalpost?forsoekFerdigstill=$forsokFerdigstill"
        val builder = UriComponentsBuilder.fromUriString(path).build()

        return metricsHelper.measure("opprettjournalpost") {
            return@measure try {
                logger.info("Kaller Joark for å generere en journalpost")
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON
                val response = journalpostOidcRestTemplate.exchange(
                        builder.toUriString(),
                        HttpMethod.POST,
                        HttpEntity(requestBody.toString(), headers),
                        String::class.java)
                mapper.readValue(response.body, JournalPostResponse::class.java).journalpostId
            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under opprettelse av journalpost ex: $ex body: ${ex.responseBodyAsString}")
                throw java.lang.RuntimeException("En feil oppstod under opprettelse av journalpost ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch(ex: Exception) {
                logger.error("En feil oppstod under opprettelse av journalpost ex: $ex")
                throw java.lang.RuntimeException("En feil oppstod under opprettelse av journalpost ex: ${ex.message}")
            }
        }
    }

    private fun populerTilleggsopplysninger(rinaSakId: String): List<Tilleggsopplysning> {
        return listOf(Tilleggsopplysning(TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY, rinaSakId))
    }

    private fun populerAvsenderMottaker(
            navBruker: String?,
            sedHendelseType: String): AvsenderMottaker {
        return if(navBruker.isNullOrEmpty()) {
            if(sedHendelseType == "SENDT") {
                AvsenderMottaker(navOrgnummer, IdType.ORGNR)
            } else {
                AvsenderMottaker(null, null)
            }
        } else {
            AvsenderMottaker(navBruker, IdType.FNR)
        }
    }

    private fun populerJournalpostType(sedHendelseType: String): JournalpostType {
        return if(sedHendelseType == "SENDT") {
            JournalpostType.UTGAAENDE
        } else {
            JournalpostType.INNGAAENDE
        }
    }

    private fun populerSak(arkivsaksnummer: String?, arkivsaksystem: String?): Sak? {
        return if(arkivsaksnummer == null || arkivsaksystem == null){
            null
        } else {
            Sak(arkivsaksnummer, arkivsaksystem)
        }
    }
}
