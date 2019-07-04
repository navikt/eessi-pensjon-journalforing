package no.nav.eessi.pensjon.journalforing.journalforing

import no.nav.eessi.pensjon.journalforing.documentconverter.DocumentConverter
import no.nav.eessi.pensjon.journalforing.documentconverter.MimeDocument
import no.nav.eessi.pensjon.journalforing.metrics.counter
import no.nav.eessi.pensjon.journalforing.models.BucType
import no.nav.eessi.pensjon.journalforing.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.journalforing.services.eux.EuxService
import no.nav.eessi.pensjon.journalforing.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.journalforing.services.fagmodul.HentYtelseTypeResponse
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingModel
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveService
import no.nav.eessi.pensjon.journalforing.services.oppgave.OpprettOppgaveModel
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonV3Service
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import no.nav.eessi.pensjon.journalforing.models.HendelseType
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.services.eux.MimeType
import no.nav.eessi.pensjon.journalforing.services.eux.SedDokumenterResponse
import no.nav.eessi.pensjon.journalforing.services.journalpost.*
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person

@Service
class JournalforingService(val euxService: EuxService,
                           val journalpostService: JournalpostService,
                           val oppgaveService: OppgaveService,
                           val aktoerregisterService: AktoerregisterService,
                           val personV3Service: PersonV3Service,
                           val fagmodulService: FagmodulService,
                           val oppgaveRoutingService: OppgaveRoutingService)  {

    private val logger = LoggerFactory.getLogger(JournalforingService::class.java)

    private val hentYtelseTypeMapper = HentYtelseTypeMapper()

    private final val genererJournalpostModelNavn = "eessipensjon_journalforing.genererjournalpostmodel"
    private val genererJournalpostModelVellykkede = counter(genererJournalpostModelNavn, "vellykkede")
    private val genererJournalpostModelFeilede = counter(genererJournalpostModelNavn, "feilede")


    fun journalfor(hendelseJson: String, hendelseType: HendelseType){
        val sedHendelse = SedHendelseModel.fromJson(hendelseJson)

        if (sedHendelse.sektorKode != "P") {
            // Vi ignorerer alle hendelser som ikke har vår sektorkode
            return
        }

        logger.info("rinadokumentID: ${sedHendelse.rinaDokumentId} rinasakID: ${sedHendelse.rinaSakId}")

        val person = hentPerson(sedHendelse.navBruker)
        val landkode = landKode(person)
        val aktoerId = hentAktoerId(sedHendelse.navBruker)

        val sedDokumenter = euxService.hentSedDokumenter(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
        val fodselsDatoISO = euxService.hentFodselsDato(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
        val isoDato = LocalDate.parse(fodselsDatoISO, DateTimeFormatter.ISO_DATE)
        val fodselsDato = isoDato.format(DateTimeFormatter.ofPattern("ddMMyy"))

        val personNavn = person?.personnavn?.sammensattNavn

        val requestBody = byggJournalPostRequest(sedHendelse, hendelseType, sedDokumenter, personNavn)
        val journalPostResponse = journalpostService.opprettJournalpost(requestBody.journalpostRequest, hendelseType,false)

        var ytelseType: OppgaveRoutingModel.YtelseType? = null

        if(sedHendelse.bucType == BucType.P_BUC_10) {
            ytelseType = hentYtelseTypeMapper.map(fagmodulService.hentYtelseTypeForPBuc10(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId))
        }

        val tildeltEnhet = oppgaveRoutingService.route(sedHendelse.navBruker, sedHendelse.bucType, landkode, fodselsDato, ytelseType)

        oppgaveService.opprettOppgave(OpprettOppgaveModel(
                sedType = sedHendelse.sedType.toString(),
                journalpostId = journalPostResponse.journalpostId,
                tildeltEnhetsnr = tildeltEnhet.enhetsNr,
                aktoerId = aktoerId,
                oppgaveType = OpprettOppgaveModel.OppgaveType.JOURNALFORING,
                rinaSakId = null,
                filnavn = null))

        if(requestBody.uSupporterteVedlegg.isNotEmpty()) {
            oppgaveService.opprettOppgave(OpprettOppgaveModel(
                    sedType = sedHendelse.sedType.toString(),
                    journalpostId = null,
                    tildeltEnhetsnr = tildeltEnhet.enhetsNr,
                    aktoerId = aktoerId,
                    oppgaveType = OpprettOppgaveModel.OppgaveType.BEHANDLE_SED,
                    rinaSakId = sedHendelse.rinaSakId,
                    filnavn = requestBody.uSupporterteVedlegg))
        }
    }

    private fun hentAktoerId(navBruker: String?): String? {
        if(navBruker == null) return null
        return try {
            val aktoerId = aktoerregisterService.hentGjeldendeAktoerIdForNorskIdent(navBruker)
            aktoerId
        } catch (ex: Exception) {
            logger.error("Det oppstod en feil ved henting av aktørid: $ex")
            null
        }
    }

    private fun hentPerson(navBruker: String?): Person? {
        if (navBruker == null) return null
        return try {
            personV3Service.hentPerson(navBruker)
        } catch (ex: Exception) {
            logger.error("Det oppstod en feil ved henting av person: $ex")
            null
        }
    }

    private fun landKode(person: Person?) = person?.bostedsadresse?.strukturertAdresse?.landkode?.value

    fun byggJournalPostRequest(sedHendelseModel: SedHendelseModel,
                               sedHendelseType: HendelseType,
                               sedDokumenter: SedDokumenterResponse,
                               personNavn: String?): JournalpostModel {
        try {
            val journalpostType = populerJournalpostType(sedHendelseType)
            val avsenderMottaker = populerAvsenderMottaker(sedHendelseModel, sedHendelseType, personNavn)
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
                                        DocumentConverter.convertToBase64PDF(MimeDocument(vedlegg.innhold, vedlegg.mimeType.toString())),
                                        Variantformat.ARKIV)), konverterFilendingTilPdf(vedlegg.filnavn)))
                    } catch(ex: Exception) {
                        uSupporterteVedlegg.add(vedlegg.filnavn)
                    }
                }
            }
            val tema = BUCTYPE.valueOf(sedHendelseModel.bucType.toString()).TEMA

            val tittel = when {
                sedHendelseModel.sedType != null -> "${journalpostType.decode()} ${sedHendelseModel.sedType}"
                else -> throw RuntimeException("sedType er null")
            }
            genererJournalpostModelVellykkede.increment()
            return JournalpostModel(JournalpostRequest(
                    avsenderMottaker = avsenderMottaker,
                    behandlingstema = behandlingstema,
                    bruker = bruker,
                    dokumenter = dokumenter,
                    tema = tema,
                    tittel = tittel,
                    journalpostType = journalpostType
            ), uSupporterteVedlegg)
        }

        catch (ex: Exception){
            genererJournalpostModelFeilede.increment()
            logger.error("noe gikk galt under konstruksjon av JournalpostModel, $ex")
            throw RuntimeException("Feil ved konstruksjon av JournalpostModel, $ex")
        }
    }
    fun konverterFilendingTilPdf(filnavn: String): String {
        return filnavn.replaceAfter(".", "pdf")
    }

    private fun populerAvsenderMottaker(sedHendelse: SedHendelseModel,
                                        sedHendelseType: HendelseType,
                                        personNavn: String?): AvsenderMottaker {
        return if(sedHendelse.navBruker.isNullOrEmpty() || personNavn.isNullOrEmpty()) {
            if(sedHendelseType == HendelseType.SENDT) {
                AvsenderMottaker(sedHendelse.avsenderId, IdType.ORGNR, sedHendelse.avsenderNavn)
            } else {
                AvsenderMottaker(sedHendelse.mottakerId, IdType.UTL_ORG, sedHendelse.mottakerNavn)
            }
        } else {
            AvsenderMottaker(sedHendelse.navBruker, IdType.FNR, personNavn)
        }
    }

    private fun populerJournalpostType(sedHendelseType: HendelseType): JournalpostType {
        return if(sedHendelseType == HendelseType.SENDT) {
            JournalpostType.UTGAAENDE
        } else {
            JournalpostType.INNGAAENDE
        }
    }
}

private class HentYtelseTypeMapper {
    fun map(hentYtelseTypeResponse: HentYtelseTypeResponse) : OppgaveRoutingModel.YtelseType {
        return when(hentYtelseTypeResponse.type) {
            HentYtelseTypeResponse.YtelseType.AP -> OppgaveRoutingModel.YtelseType.AP
            HentYtelseTypeResponse.YtelseType.GP -> OppgaveRoutingModel.YtelseType.GP
            HentYtelseTypeResponse.YtelseType.UT -> OppgaveRoutingModel.YtelseType.UT
        }
    }
}

// https://confluence.adeo.no/display/BOA/Behandlingstema
enum class Behandlingstema : Code {
    GJENLEVENDEPENSJON {
        override fun toString() = "ab0011"
        override fun decode() = "Gjenlevendepensjon"
    },
    ALDERSPENSJON {
        override fun toString() = "ab0254"
        override fun decode() = "Alderspensjon"
    },
    UFOREPENSJON {
        override fun toString() = "ab0194"
        override fun decode() = "Uførepensjon"
    }
}

// https://confluence.adeo.no/display/BOA/Tema
enum class Tema : Code {
    PENSJON {
        override fun toString() = "PEN"
        override fun decode() = "Pensjon"
    },
    UFORETRYGD {
        override fun toString() = "UFO"
        override fun decode() = "Uføretrygd"
    }
}

enum class BUCTYPE (val BEHANDLINGSTEMA: String, val TEMA: String){
    P_BUC_01(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_02(Behandlingstema.GJENLEVENDEPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_03(Behandlingstema.UFOREPENSJON.toString(), Tema.UFORETRYGD.toString()),
    P_BUC_04(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_05(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_06(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_07(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_08(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_09(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_10(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString())
}

interface Code {
    fun decode(): String
}