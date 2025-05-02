package no.nav.eessi.pensjon.journalforing.journalpost

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.buc.SakType.BARNEP
import no.nav.eessi.pensjon.eux.model.document.SedVedlegg
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.journalforing.*
import no.nav.eessi.pensjon.journalforing.Bruker
import no.nav.eessi.pensjon.journalforing.JournalpostType.*
import no.nav.eessi.pensjon.journalforing.Sak
import no.nav.eessi.pensjon.journalforing.opprettoppgave.OpprettOppgaveService
import no.nav.eessi.pensjon.journalforing.pdf.PDFService
import no.nav.eessi.pensjon.journalforing.saf.*
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Behandlingstema.*
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.models.Tema.*
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class JournalpostService(
    val journalpostKlient: JournalpostKlient,
    val pdfService: PDFService,
    val oppgaveService: OpprettOppgaveService
) {

    private val logger = LoggerFactory.getLogger(JournalpostService::class.java)

    companion object {
        private const val TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY = "eessi_pensjon_bucid"
    }

    /**
     * Bygger en {@link OpprettJournalpostRequest} som videresendes til {@link JournalpostKlient}.
     *
     * @return {@link OpprettJournalPostResponse?} som inneholder
     */
    fun opprettJournalpost(
        sedHendelse: SedHendelse,
        identifisertPerson: IdentifisertPerson? = null,
        sedHendelseType: HendelseType,
        tildeltJoarkEnhet: Enhet,
        arkivsaksnummer: Sak? = null,
        saktype: SakType? = null,
        institusjon: AvsenderMottaker,
        identifisertePersoner: Int,
        saksbehandlerInfo: Pair<String, Enhet?>? = null,
        tema: Tema,
        currentSed: SED? = null
    ): OpprettJournalpostRequest {
        logger.info("Oppretter OpprettJournalpostRequest for sed: ${sedHendelse.sedType} med rinasSakId: ${sedHendelse.rinaSakId}")
        val dokumenter = hentDocuments(sedHendelse, tildeltJoarkEnhet, identifisertPerson?.aktoerId, tema)
        val fnrFraPersonrelasjon = identifisertPerson?.personRelasjon?.fnr
        return OpprettJournalpostRequest(
            avsenderMottaker = institusjon,
            behandlingstema = bestemBehandlingsTema(sedHendelse.bucType!!, saktype, tema, identifisertePersoner, currentSed),
            bruker = fnrFraPersonrelasjon?.let { Bruker(id = it.value) },
            journalpostType = bestemJournalpostType(sedHendelseType),
            sak = arkivsaksnummer,
            tema = tema,
            tilleggsopplysninger = listOf(Tilleggsopplysning(TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY, sedHendelse.rinaSakId)),
            tittel = lagTittel(bestemJournalpostType(sedHendelseType), sedHendelse.sedType!!),
            dokumenter = dokumenter,
            journalfoerendeEnhet = saksbehandlerInfo?.second ?: tildeltJoarkEnhet
        )
    }


    private fun hentDocuments(
        sedHendelse: SedHendelse,
        tildeltJoarkEnhet: Enhet,
        aktoerId: String?,
        tema: Tema
    ): String {
        val (documents, _) = sedHendelse.run {
            sedType?.let {
                pdfService.hentDokumenterOgVedlegg(rinaSakId, rinaDokumentId, it).also { documentsAndAttachments ->
                    if (documentsAndAttachments.second.isNotEmpty()) {
                        oppgaveService.opprettBehandleSedOppgave(
                            null,
                            tildeltJoarkEnhet,
                            aktoerId,
                            sedHendelse,
                            usupporterteFilnavn(documentsAndAttachments.second),
                            tema
                        )
                    }
                }
            } ?: throw IllegalStateException("sedType is null")
        }
        logger.info("Dokument hentet, størrelse: ${dokumentStorrelse(documents)}")
        return documents
    }

    //TODO: flyttes til PDF/DOK
    fun dokumentStorrelse(input: String): Double {
        val byteSize = input.length * 2
        return byteSize / (1024.0 * 1024.0)
    }

    //TODO: flyttes til PDF/DOK
    private fun usupporterteFilnavn(uSupporterteVedlegg: List<SedVedlegg>): String {
        return uSupporterteVedlegg.joinToString(separator = "") { it.filnavn + " " }
    }

    fun sendJournalPost(journalpostRequest: OpprettJournalpostRequest,
                        sedHendelse: SedHendelse,
                        hendelseType: HendelseType,
                        saksbehandlerIdent: String?): OpprettJournalPostResponse? {

        val gjenny = when {
            hendelseType == MOTTATT && journalpostRequest.tema in listOf(EYBARNEP, OMSTILLING) -> false
            hendelseType == SENDT && journalpostRequest.tema in listOf(EYBARNEP, OMSTILLING) -> true
            else -> false
        }

        val forsokFerdigstill: Boolean = gjenny || kanSakFerdigstilles(journalpostRequest, sedHendelse.bucType!!, hendelseType)
        val journalforingResponse  = journalpostKlient.opprettJournalpost(journalpostRequest, forsokFerdigstill, saksbehandlerIdent)

        // Vi har en uvanlig situasjon der vi forventer ferdigstilling, men dokarkiv klarer ikke å ferdigstill
        if(forsokFerdigstill && journalforingResponse?.journalpostferdigstilt == false){
            logger.error("Forventet ferdigstilling feilet")
        }

        return journalforingResponse
    }

    fun kanSakFerdigstilles(request: OpprettJournalpostRequest, bucType: BucType, sedHendelseType: HendelseType): Boolean {
        val detFinnesNull = listOf(
            request.bruker,
            request.journalfoerendeEnhet,
            request.kanal,
            request.tema,
            request.sak,
            request.avsenderMottaker,
            request.tittel,
            request.dokumenter
        ).any { it == null }
        if (bucType == P_BUC_02 && sedHendelseType == MOTTATT || sedHendelseType == MOTTATT && request.tema in listOf(EYBARNEP, OMSTILLING)) return false
        if(detFinnesNull)
        {
            val vasketFnr = request.bruker?.id?.isNotEmpty()
            logger.info("""Journalpost kan ikke ferdigstilles da det mangler data:
                |    sak: ${request.sak},
                |    tema: ${request.tema},
                |    kanal: ${request.kanal},
                |    tittel: ${request.tittel},
                |    dokumenter.str: ${request.dokumenter.length},
                |    avsenderMottaker: ${request.avsenderMottaker},
                |    bruker: ${if (vasketFnr == true) "*******" else "Mangler fnr"},
                |    journalfoerendeEnhet: ${request.journalfoerendeEnhet}""".trimMargin())
            return false
        }
        return true
    }

    /**
     *  Ferdigstiller journalposten.
     *
     *  @param journalpostId: ID til journalposten som skal ferdigstilles.
     */
    fun oppdaterDistribusjonsinfo(journalpostId: String) = journalpostKlient.oppdaterDistribusjonsinfo(journalpostId)

    /**
     *  Ferdigstiller journalposten.
     *
     *  @param identifisertPerson: ID til journalposten som skal ferdigstilles.
     *  @param hendelseType: SENDT eller MOTTATT.
     *  @param sedHendelse: sed fra eux
     *  @param journalpostId: response fra joark
     */
    fun journalpostSattTilAvbrutt(
        fnrForRelasjon: Fodselsnummer?,
        hendelseType: HendelseType,
        sedHendelse: SedHendelse,
        journalpostId: String?
    ): Boolean {
        if (journalpostId == null) {
            logger.warn("Ingen gyldig journalpost; setter ikke avbrutt")
            return false
        }

        if (fnrForRelasjon != null) {
            logger.warn("IdentifiedPerson med journalpostID: $journalpostId har fnr; setter ikke avbrutt")
            return false
        }

        // R-BUC skal behandles manuelt selv om vi mangler fnummer eller identifisert person
        val rSeder = listOf(SedType.R004, SedType.R005, SedType.R006)
        if(sedHendelse.sedType in rSeder && hendelseType == SENDT){
            logger.warn("HendelseType er utgående og SED er av typen ${sedHendelse.sedType}; settes derfor ikke til avbrutt")
            return false
        }

        if (hendelseType == MOTTATT) {
            logger.warn("HendelseType er mottatt; setter ikke avbrutt")
            return false
        }

        logger.info("Journalpost:$journalpostId settes til avbrutt for rinaNr: ${sedHendelse.rinaSakId}, buc: ${sedHendelse.bucType}, sed:${sedHendelse.rinaDokumentId}")
        journalpostKlient.oppdaterJournalpostMedAvbrutt(journalpostId)
        return true
    }

    fun bestemBehandlingsTema(
        bucType: BucType,
        saktype: SakType?,
        tema: Tema,
        identifisertePersoner: Int,
        currentSed: SED?
    ): Behandlingstema {
        val pensjonsBucer = listOf(P_BUC_05, P_BUC_06, P_BUC_07, P_BUC_08, P_BUC_09, P_BUC_10)
        val noenSedInPbuc06list = listOf(SedType.P5000, SedType.P6000, SedType.P7000, SedType.P10000)

        //TODO: Er dette nødvendig? allerde i varetatt i BehandlingstemaPbuc06
        return when {
            bucType == P_BUC_01 -> ALDERSPENSJON
            bucType == P_BUC_02 -> GJENLEVENDEPENSJON
            bucType == P_BUC_03 -> UFOREPENSJON
            bucType == P_BUC_06 && currentSed?.type in noenSedInPbuc06list -> BehandlingstemaPbuc06(currentSed!!)
            bucType == P_BUC_10 && currentSed?.type == SedType.P15000 -> BehandlingstemaPbuc06(currentSed)
            tema == UFORETRYGD && identifisertePersoner <= 1 -> UFOREPENSJON
            tema == PENSJON && identifisertePersoner >= 2 -> GJENLEVENDEPENSJON
            bucType in pensjonsBucer -> when (saktype) {
                GJENLEV, BARNEP -> GJENLEVENDEPENSJON
                UFOREP -> UFOREPENSJON
                else -> if (bucType == R_BUC_02) TILBAKEBETALING else ALDERSPENSJON
            }
            else -> ALDERSPENSJON
        }
    }

    private fun BehandlingstemaPbuc06(currentSed: SED): Behandlingstema {
        return when {
            currentSed is UforePensjon && currentSed.hasUforePensjonType() -> UFOREPENSJON
            currentSed is GjenlevPensjon && currentSed.hasGjenlevPensjonType() -> GJENLEVENDEPENSJON
            else -> ALDERSPENSJON
        }
    }

    private fun bestemJournalpostType(sedHendelseType: HendelseType): JournalpostType =
            if (sedHendelseType == SENDT) UTGAAENDE
            else INNGAAENDE

    private fun lagTittel(journalpostType: JournalpostType,
                          sedType: SedType) = "${journalpostType.decode()} ${sedType.typeMedBeskrivelse()}"
}