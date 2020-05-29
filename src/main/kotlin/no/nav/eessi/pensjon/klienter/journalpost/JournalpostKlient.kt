package no.nav.eessi.pensjon.klienter.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import javax.annotation.PostConstruct

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

    private lateinit var opprettjournalpost: MetricsHelper.Metric
    private lateinit var oppdaterDistribusjonsinfo: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        opprettjournalpost = metricsHelper.init("opprettjournalpost")
        oppdaterDistribusjonsinfo = metricsHelper.init("oppdaterDistribusjonsinfo")
    }

    fun opprettJournalpost(
            rinaSakId: String,
            fnr: String?,
            personNavn: String?,
            bucType: String,
            sedType: String,
            sedHendelseType: String,
            eksternReferanseId: String?,
            kanal: String?,
            journalfoerendeEnhet: String,
            arkivsaksnummer: String?,
            dokumenter: String,
            forsokFerdigstill: Boolean? = false,
            avsenderLand: String?,
            avsenderNavn: String?,
            ytelseType: YtelseType?): OpprettJournalPostResponse? {

        val avsenderMottaker = populerAvsenderMottaker(
                avsenderNavn,
                sedHendelseType,
                avsenderLand
        )
        val behandlingstema = hentBehandlingsTema(bucType, ytelseType)
        val bruker = when (fnr){
            null -> null
            else -> Bruker(id = fnr)
        }
        val journalpostType = populerJournalpostType(sedHendelseType)
        val sak = populerSak(arkivsaksnummer)
        val tema = hentTema(bucType, sedType, journalfoerendeEnhet, ytelseType)
        val tilleggsopplysninger = populerTilleggsopplysninger(rinaSakId)
        val tittel = "${journalpostType.decode()} $sedType"

        val requestBody = OpprettJournalpostRequest(
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

        return opprettjournalpost.measure {
            return@measure try {
                logger.info("Kaller Joark for å generere en journalpost: $path")
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

    fun hentBehandlingsTema(bucType: String, ytelseType: YtelseType?): String {
        return if (bucType == BucType.R_BUC_02.name) {
            return when (ytelseType) {
                YtelseType.UFOREP -> Behandlingstema.UFOREPENSJON.toString()
                YtelseType.GJENLEV -> Behandlingstema.GJENLEVENDEPENSJON.toString()
                else -> Behandlingstema.ALDERSPENSJON.toString()
            }
        } else {
            BucType.valueOf(bucType).BEHANDLINGSTEMA
        }
    }

    fun hentTema(bucType: String, sedType: String, enhet: String, ytelseType: YtelseType?): String {
        return if (bucType == BucType.R_BUC_02.name) {
            if (sedType == SedType.R004.name && enhet == "4819"){
                return Tema.PENSJON.toString()
            }
            return when (ytelseType) {
                YtelseType.UFOREP -> Tema.UFORETRYGD.toString()
                else -> Tema.PENSJON.toString()
            }
        } else {
            BucType.valueOf(bucType).TEMA
        }

    }

    /**
     *  Oppdaterer journaposten, Kanal og ekspedertstatus settes
     */
    fun oppdaterDistribusjonsinfo(journalpostId: String) {
        val path = "/journalpost/$journalpostId/oppdaterDistribusjonsinfo"
        val builder = UriComponentsBuilder.fromUriString(path).build()

        return oppdaterDistribusjonsinfo.measure {
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
