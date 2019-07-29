package no.nav.eessi.pensjon.journalforing.services.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.metrics.counter
import no.nav.eessi.pensjon.journalforing.models.BucType
import no.nav.eessi.pensjon.journalforing.pdf.JournalPostDokument
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import kotlin.RuntimeException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

@Service
class JournalpostService(private val journalpostOidcRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(JournalpostService::class.java) }
    private val mapper = jacksonObjectMapper()

    private final val opprettJournalpostNavn = "eessipensjon_journalforing.opprettjournalpost"
    private val opprettJournalpostVellykkede = counter(opprettJournalpostNavn, "vellykkede")
    private val opprettJournalpostFeilede = counter(opprettJournalpostNavn, "feilede")

    fun opprettJournalpost(
            navBruker: String?,
            personNavn: String?,
            avsenderId: String,
            avsenderNavn: String,
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
    ): String {

        val avsenderMottaker = populerAvsenderMottaker(
                navBruker,
                avsenderId,
                avsenderNavn,
                mottakerId,
                mottakerNavn,
                sedHendelseType,
                personNavn
        )
        val behandlingstema = BucType.valueOf(bucType).BEHANDLINGSTEMA
        val bruker = when (navBruker){
            null -> null
            else -> Bruker(id = navBruker)
        }
        val journalpostType = populerJournalpostType(sedHendelseType)
        val sak = populerSak(arkivsaksnummer, arkivsaksystem)
        val tema = BucType.valueOf(bucType).TEMA
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
                tittel)

        //Send Request

        val path = "/journalpost?forsoekFerdigstill=$forsokFerdigstill"
        val builder = UriComponentsBuilder.fromUriString(path).build()

        try {
            logger.info("Kaller Joark for å generere en journalpost")
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val response = journalpostOidcRestTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.POST,
                    HttpEntity(requestBody.toString(), headers),
                    String::class.java
            )

            if(!response.statusCode.isError) {
                opprettJournalpostVellykkede.increment()
                logger.debug(response.body.toString())
                return mapper.readValue(response.body, JournalPostResponse::class.java).journalpostId
            } else {
                throw RuntimeException("Noe gikk galt under opprettelse av journalpost")
            }
        } catch(ex: Exception) {
            opprettJournalpostFeilede.increment()
            logger.error("noe gikk galt under opprettelse av journalpost, $ex")
            throw RuntimeException("Feil ved opprettelse av journalpost, $ex")
        }
    }

    private fun populerAvsenderMottaker(
            navBruker: String?,
            avsenderId: String,
            avsenderNavn: String,
            mottakerId: String,
            mottakerNavn: String,
            sedHendelseType: String,
            personNavn: String?): AvsenderMottaker {
        return if(navBruker.isNullOrEmpty() || personNavn.isNullOrEmpty()) {
            if(sedHendelseType == "SENDT") {
                AvsenderMottaker(avsenderId, IdType.ORGNR, avsenderNavn)
            } else {
                AvsenderMottaker(mottakerId, IdType.UTL_ORG, mottakerNavn)
            }
        } else {
            AvsenderMottaker(navBruker, IdType.FNR, personNavn)
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