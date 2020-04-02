package no.nav.eessi.pensjon.klienter.journalpost

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
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException

/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Component
class JournalpostKlient(
        private val journalpostOidcRestTemplate: RestTemplate,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(JournalpostKlient::class.java) }
    private val mapper = jacksonObjectMapper()
    private final val TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY = "eessi_pensjon_bucid"

    @Value("\${no.nav.orgnummer}")
    private lateinit var navOrgnummer: String

    fun opprettJournalpost(journalpostModel: JournalpostKlientModel): OpprettJournalPostResponse? {

        val avsenderMottaker = populerAvsenderMottaker(
                journalpostModel.avsenderNavn,
                journalpostModel.sedHendelseType,
                journalpostModel.avsenderLand
        )
        val behandlingstema = BucType.valueOf(journalpostModel.bucType).BEHANDLINGSTEMA
        val bruker = when (journalpostModel.fnr){
            null -> null
            else -> Bruker(id = journalpostModel.fnr)
        }
        val journalpostType = populerJournalpostType(journalpostModel.sedHendelseType)
        val sak = populerSak(journalpostModel.arkivsaksnummer)
        val tema = BucType.valueOf(journalpostModel.bucType).TEMA
        val tilleggsopplysninger = populerTilleggsopplysninger(journalpostModel.rinaSakId)
        val tittel = "${journalpostType.decode()} ${journalpostModel.sedType}"

        val requestBody = OpprettJournalpostRequest(
                avsenderMottaker,
                behandlingstema,
                bruker,
                journalpostModel.dokumenter,
                journalpostModel.eksternReferanseId,
                journalpostModel.journalfoerendeEnhet,
                journalpostType,
                journalpostModel.kanal,
                sak,
                tema,
                tilleggsopplysninger,
                tittel)


        //Send Request
        val path = "/journalpost?forsoekFerdigstill=${journalpostModel.forsokFerdigstill}"
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
                mapper.readValue(response.body, OpprettJournalPostResponse::class.java)
            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under opprettelse av journalpost ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod under opprettelse av journalpost ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch(ex: Exception) {
                logger.error("En feil oppstod under opprettelse av journalpost ex: $ex")
                throw RuntimeException("En feil oppstod under opprettelse av journalpost ex: ${ex.message}")
            }
        }
    }

    /**
     *  Oppdaterer journaposten, Kanal og ekspedertstatus settes
     */
    fun oppdaterDistribusjonsinfo(journalpostId: String) {
        val path = "/journalpost/$journalpostId/oppdaterDistribusjonsinfo"
        val builder = UriComponentsBuilder.fromUriString(path).build()

        return metricsHelper.measure("oppdaterDistribusjonsinfo") {
            try {
                logger.info("Oppdaterer distribusjonsinfo for journalpost: $journalpostId")
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON

                journalpostOidcRestTemplate.exchange(
                        builder.toUriString(),
                        HttpMethod.PATCH,
                        HttpEntity(OppdaterDistribusjonsinfoRequest().toString(), headers),
                        String::class.java)

            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under oppdatering av distribusjonsinfo på journalpostId: $journalpostId ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod under oppdatering av distribusjonsinfo på journalpostId: $journalpostId ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch(ex: Exception) {
                logger.error("En feil oppstod under oppdatering av distribusjonsinfo på journalpostId: $journalpostId ex: $ex")
                throw RuntimeException("En feil oppstod under oppdatering av distribusjonsinfo på journalpostId: $journalpostId ex: ${ex.message}")
            }
        }
    }

    private fun populerTilleggsopplysninger(rinaSakId: String): List<Tilleggsopplysning> {
        return listOf(Tilleggsopplysning(TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY, rinaSakId))
    }

    private fun populerAvsenderMottaker(
            avsenderNavn: String?,
            sedHendelseType: String,
            avsenderLand: String?): AvsenderMottaker {

        return if(sedHendelseType == "SENDT") {
            AvsenderMottaker(navOrgnummer, IdType.ORGNR, "NAV", "NO")
        } else {
            val justertAvsenderLand = justerAvsenderLand(avsenderLand)
            AvsenderMottaker(null, null, avsenderNavn, justertAvsenderLand)
        }
    }

    /**
     * PESYS støtter kun GB
     */
    private fun justerAvsenderLand(avsenderLand: String?): String? {
        if (avsenderLand == "UK") {
            return  "GB"
        }
        return avsenderLand
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
