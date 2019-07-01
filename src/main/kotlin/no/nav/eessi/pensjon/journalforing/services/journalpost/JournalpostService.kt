package no.nav.eessi.pensjon.journalforing.services.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.services.documentconverter.DokumentKonvertererModel
import no.nav.eessi.pensjon.journalforing.services.documentconverter.DocumentConverterService
import no.nav.eessi.pensjon.journalforing.services.eux.MimeType
import no.nav.eessi.pensjon.journalforing.services.eux.SedDokumenterResponse
import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelseModel
import no.nav.eessi.pensjon.journalforing.metrics.counter
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
import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelseModel.SedHendelseType.*
import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelseModel.SedHendelseType
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonV3Service

@Service
class JournalpostService(private val journalpostOidcRestTemplate: RestTemplate,
                         val dokumentConverterService: DocumentConverterService,
                         val personV3Service: PersonV3Service) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(JournalpostService::class.java) }
    private val mapper = jacksonObjectMapper()

    private final val opprettJournalpostNavn = "eessipensjon_journalforing.opprettjournalpost"
    private val opprettJournalpostVellykkede = counter(opprettJournalpostNavn, "vellykkede")
    private val opprettJournalpostFeilede = counter(opprettJournalpostNavn, "feilede")

    private final val genererJournalpostModelNavn = "eessipensjon_journalforing.genererjournalpostmodel"
    private val genererJournalpostModelVellykkede = counter(genererJournalpostModelNavn, "vellykkede")
    private val genererJournalpostModelFeilede = counter(genererJournalpostModelNavn, "feilede")

    fun byggJournalPostRequest(sedHendelseModel: SedHendelseModel,
                               sedHendelseType: SedHendelseType,
                               sedDokumenter: SedDokumenterResponse): JournalpostModel {
        try {
            val avsenderMottaker = populerAvsenderMottaker(sedHendelseModel, sedHendelseType)
            val behandlingstema = BUCTYPE.valueOf(sedHendelseModel.bucType.toString()).BEHANDLINGSTEMA

            val bruker = when {
                sedHendelseModel.navBruker != null -> Bruker(id = sedHendelseModel.navBruker)
                else -> null
            }

            val dokumenter =  mutableListOf<Dokument>()
            dokumenter.add(Dokument(sedHendelseModel.sedId,
                    "SED",
                    listOf(Dokumentvarianter(fysiskDokument = sedDokumenter.sed.innhold,
                            filtype = sedDokumenter.sed.mimeType!!.decode(),
                            variantformat = Variantformat.ARKIV)), sedDokumenter.sed.filnavn))

            val uSupporterteVedlegg = ArrayList<String>()

            sedDokumenter.vedlegg?.forEach{ vedlegg ->

                if(vedlegg.mimeType == null) {
                    uSupporterteVedlegg.add(vedlegg.filnavn)
                } else {
                    try {
                        dokumenter.add(Dokument(sedHendelseModel.sedId,
                                "SED",
                                listOf(Dokumentvarianter(MimeType.PDF.decode(),
                                        dokumentConverterService.konverterFraBildeTilBase64EncodedPDF(DokumentKonvertererModel(vedlegg.innhold, vedlegg.mimeType)),
                                        Variantformat.ARKIV)), konverterFilendingTilPdf(vedlegg.filnavn)))
                    } catch(ex: Exception) {
                        uSupporterteVedlegg.add(vedlegg.filnavn)
                    }
                }
            }
            val tema = BUCTYPE.valueOf(sedHendelseModel.bucType.toString()).TEMA

            val tittel = when {
                sedHendelseModel.sedType != null -> "Utgående ${sedHendelseModel.sedType}"
                else -> throw RuntimeException("sedType er null")
            }
            genererJournalpostModelVellykkede.increment()
            return JournalpostModel(JournalpostRequest(
                avsenderMottaker = avsenderMottaker,
                behandlingstema = behandlingstema,
                bruker = bruker,
                dokumenter = dokumenter,
                tema = tema,
                tittel = tittel
            ), uSupporterteVedlegg)
        }

    catch (ex: Exception){
            genererJournalpostModelFeilede.increment()
            logger.error("noe gikk galt under konstruksjon av JournalpostModel, $ex")
            throw RuntimeException("Feil ved konstruksjon av JournalpostModel, $ex")
        }
    }

    fun opprettJournalpost(journalpostRequest: JournalpostRequest,
                           sedHendelseType: SedHendelseType,
                           forsokFerdigstill: Boolean) :JournalPostResponse {

        val path = "/journalpost?forsoekFerdigstill=$forsokFerdigstill"
        val builder = UriComponentsBuilder.fromUriString(path).build()

        try {
            logger.info("Kaller Joark for å generere en journalpost")
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val response = journalpostOidcRestTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.POST,
                    HttpEntity(journalpostRequest.toString(), headers),
                    String::class.java
            )

            if(!response.statusCode.isError) {
                opprettJournalpostVellykkede.increment()
                logger.debug(response.body.toString())
                return mapper.readValue(response.body, JournalPostResponse::class.java)

            } else {
                throw RuntimeException("Noe gikk galt under opprettelse av journalpost")
            }
        } catch(ex: Exception) {
            opprettJournalpostFeilede.increment()
            logger.error("noe gikk galt under opprettelse av journalpost, $ex")
            throw RuntimeException("Feil ved opprettelse av journalpost, $ex")
        }
    }

    fun konverterFilendingTilPdf(filnavn: String): String {
        return filnavn.replaceAfter(".", "pdf")
    }

    private fun populerAvsenderMottaker(sedHendelse: SedHendelseModel,
                                        sedHendelseType: SedHendelseType): AvsenderMottaker {
        return if(sedHendelse.navBruker.isNullOrEmpty()) {
            if(sedHendelseType == SENDT) {
                AvsenderMottaker(sedHendelse.avsenderId, IdType.UTL_ORG, sedHendelse.avsenderNavn)
            } else {
                AvsenderMottaker(sedHendelse.mottakerId, IdType.UTL_ORG, sedHendelse.mottakerNavn)
            }
        } else {
            try {
                val person = personV3Service.hentPerson(sedHendelse.navBruker)
                AvsenderMottaker(sedHendelse.navBruker, IdType.FNR, person.personnavn.sammensattNavn)
            } catch (ex: Exception) {
                logger.warn("Klarte ikke å hente navn for fnr: ${sedHendelse.navBruker}, fyller ut UTL_ORG istedenfor")
                AvsenderMottaker(sedHendelse.avsenderId, IdType.UTL_ORG, sedHendelse.avsenderNavn)
            }
        }
    }
}