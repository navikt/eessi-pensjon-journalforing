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
            fnr: String?,
            personNavn: String?,
            bucType: String,
            sedType: String,
            sedHendelseType: String,
            eksternReferanseId: String?,
            kanal: String?,
            journalfoerendeEnhet: String?,
            arkivsaksnummer: String?,
            dokumenter: String,
            forsokFerdigstill: Boolean? = false,
            avsenderLand: String?
    ): JournalPostResponse? {

        val avsenderMottaker = populerAvsenderMottaker(
                fnr,
                personNavn,
                sedHendelseType,
                avsenderLand
        )
        val behandlingstema = BucType.valueOf(bucType).BEHANDLINGSTEMA
        val bruker = when (fnr){
            null -> null
            else -> Bruker(id = fnr)
        }
        val journalpostType = populerJournalpostType(sedHendelseType)
        val sak = populerSak(arkivsaksnummer)
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
                mapper.readValue(response.body, JournalPostResponse::class.java)
            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under opprettelse av journalpost ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod under opprettelse av journalpost ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch(ex: Exception) {
                logger.error("En feil oppstod under opprettelse av journalpost ex: $ex")
                throw RuntimeException("En feil oppstod under opprettelse av journalpost ex: ${ex.message}")
            }
        }
    }

    private fun populerTilleggsopplysninger(rinaSakId: String): List<Tilleggsopplysning> {
        return listOf(Tilleggsopplysning(TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY, rinaSakId))
    }

    private fun populerAvsenderMottaker(
            fnr: String?,
            mottakerNavn: String?,
            sedHendelseType: String,
            avsenderLand: String?): AvsenderMottaker {

        return if(sedHendelseType == "SENDT") {
            AvsenderMottaker(navOrgnummer, IdType.ORGNR, "NAV", "NO")
        } else {
            if(fnr.isNullOrEmpty()) {
                AvsenderMottaker(null, null, null, avsenderLand)
            } else {
                AvsenderMottaker(fnr, IdType.FNR, mottakerNavn, avsenderLand)
            }
        }
    }

    private fun populerJournalpostType(sedHendelseType: String): JournalpostType {
        return if(sedHendelseType == "SENDT") {
            JournalpostType.UTGAAENDE
        } else {
            JournalpostType.INNGAAENDE
        }
    }

    private fun populerSak(arkivsaksnummer: String?): Sak? {
        return if(arkivsaksnummer == null){
            null
        } else {
            Sak(arkivsaksnummer, "PSAK")
        }
    }
}
