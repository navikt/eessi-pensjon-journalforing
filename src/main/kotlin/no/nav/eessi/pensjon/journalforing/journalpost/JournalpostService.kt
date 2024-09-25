package no.nav.eessi.pensjon.journalforing.journalpost

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.buc.SakType.BARNEP
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.journalforing.*
import no.nav.eessi.pensjon.journalforing.Bruker
import no.nav.eessi.pensjon.journalforing.Sak
import no.nav.eessi.pensjon.journalforing.saf.*
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Behandlingstema.*
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.models.Tema.*
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class JournalpostService(private val journalpostKlient: JournalpostKlient) {

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
        fnr: Fodselsnummer? = null,
        sedHendelseType: HendelseType,
        journalfoerendeEnhet: Enhet,
        arkivsaksnummer: Sak? = null,
        dokumenter: String,
        saktype: SakType? = null,
        institusjon: AvsenderMottaker,
        identifisertePersoner: Int,
        saksbehandlerInfo: Pair<String, Enhet?>? = null,
        tema: Tema,
        currentSed: SED? = null
    ): OpprettJournalpostRequest {
        logger.info("Oppretter OpprettJournalpostRequest for ${sedHendelse.rinaSakId}")

        return OpprettJournalpostRequest(
            avsenderMottaker = institusjon,
            behandlingstema = bestemBehandlingsTema(sedHendelse.bucType!!, saktype, tema, identifisertePersoner, currentSed),
            bruker = fnr?.let { Bruker(id = it.value) },
            journalpostType = bestemJournalpostType(sedHendelseType),
            sak = arkivsaksnummer,
            tema = tema,
            tilleggsopplysninger = listOf(Tilleggsopplysning(TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY, sedHendelse.rinaSakId)),
            tittel = lagTittel(bestemJournalpostType(sedHendelseType), sedHendelse.sedType!!),
            dokumenter = dokumenter,
            journalfoerendeEnhet = saksbehandlerInfo?.second ?: journalfoerendeEnhet
        )
    }

    fun sendJournalPost(journalpostRequest: OpprettJournalpostRequest,
                        sedHendelse: SedHendelse,
                        hendelseType: HendelseType,
                        saksbehandlerIdent: String?): OpprettJournalPostResponse? {

        val gjenny = journalpostRequest.tema in listOf(EYBARNEP, OMSTILLING)
        val forsokFerdigstill: Boolean = if(gjenny) false else kanSakFerdigstilles(journalpostRequest, sedHendelse.bucType!!, hendelseType)

        return journalpostKlient.opprettJournalpost(journalpostRequest, forsokFerdigstill, saksbehandlerIdent)
    }

    fun sendJournalPost(journalpostRequest: JournalpostMedSedInfo,
                        saksbehandlerIdent: String?): OpprettJournalPostResponse? {
        return sendJournalPost(journalpostRequest.journalpostRequest, journalpostRequest.sedHendelse, journalpostRequest.sedHendelseType, saksbehandlerIdent)
    }

    /** Oppdatere journalpost med kall til dokarkiv:
     *  https://dokarkiv-q2.nais.preprod.local/swagger-ui/index.html#/journalpostapi/oppdaterJournalpost
     */
    fun oppdaterJournalpost(
        journalpostResponse: JournalpostResponse,
        kjentBruker: Bruker,
        tema: Tema,
        enhet: Enhet,
        behandlingsTema: Behandlingstema
    ) {
        journalpostKlient.oppdaterJournalpostMedBruker(
            OppdaterJournalpost(
                journalpostId = journalpostResponse.journalpostId!!,
                dokumenter = journalpostResponse.dokumenter,
                sak = journalpostResponse.sak,
                bruker = kjentBruker,
                tema = tema,
                enhet = enhet,
                behandlingsTema = behandlingsTema
            )
        ).also { logger.debug("Oppdatert journalpostId: ${journalpostResponse.journalpostId}") }
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
        if (bucType == P_BUC_02 && sedHendelseType == HendelseType.MOTTATT) return false
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
     *  @param journalPostResponse: response fra joark
     */
    fun skalStatusSettesTilAvbrutt(
        fnrForRelasjon: Fodselsnummer?,
        hendelseType: HendelseType,
        sedHendelse: SedHendelse,
        journalPostResponse: OpprettJournalPostResponse?
    ): Boolean {
        if (journalPostResponse == null) {
            logger.warn("Ingen gyldig journalpost; setter ikke avbrutt")
            return false
        }

        if (fnrForRelasjon != null) {
            logger.warn("IdentifiedPerson med journalpostID: ${journalPostResponse.journalpostId} har fnr; setter ikke avbrutt")
            return false
        }

        if (hendelseType != HendelseType.SENDT) {
            logger.warn("HendelseType er mottatt; setter ikke avbrutt")
            return false
        }
        return true
    }

    fun settJournalpostTilAvbrutt(journalpostId: String) = journalpostKlient.oppdaterJournalpostMedAvbrutt(journalpostId)

    fun bestemBehandlingsTema(
        bucType: BucType,
        saktype: SakType?,
        tema: Tema,
        identifisertePersoner: Int,
        currentSed: SED?
    ): Behandlingstema {
        val noenSedInPbuc06list = listOf(SedType.P5000, SedType.P6000, SedType.P7000, SedType.P10000)

        if(bucType == P_BUC_01) return ALDERSPENSJON
        if(bucType == P_BUC_02) return GJENLEVENDEPENSJON
        if(bucType == P_BUC_03) return UFOREPENSJON

        if (bucType == P_BUC_06 && currentSed?.type in noenSedInPbuc06list) return BehandlingstemaPbuc06(currentSed)
        if (bucType == P_BUC_10 && currentSed?.type == SedType.P15000) return behandlingstemaPbuc10(currentSed)

        if (tema == UFORETRYGD && identifisertePersoner <= 1) return UFOREPENSJON
        if (tema == PENSJON && identifisertePersoner >= 2) return GJENLEVENDEPENSJON

        return if (bucType in listOf(P_BUC_05, P_BUC_06, P_BUC_07, P_BUC_08, P_BUC_09, P_BUC_10)) {
            return when (saktype) {
                GJENLEV, BARNEP -> GJENLEVENDEPENSJON
                UFOREP -> UFOREPENSJON
                else -> {
                    if (bucType == R_BUC_02) TILBAKEBETALING
                    ALDERSPENSJON
                }
            }
        } else ALDERSPENSJON
    }

    private fun BehandlingstemaPbuc06(currentSed: SED?) : Behandlingstema {
        return when {
            currentSed is P5000 && currentSed.hasUforePensjonType() -> UFOREPENSJON
            currentSed is P5000 && currentSed.hasGjenlevPensjonType() -> GJENLEVENDEPENSJON

            currentSed is P6000 && currentSed.hasUforePensjonType() -> UFOREPENSJON
            currentSed is P6000 && currentSed.hasGjenlevPensjonType() -> GJENLEVENDEPENSJON

            currentSed is P7000 && currentSed.hasUforePensjonType() -> UFOREPENSJON
            currentSed is P7000 && currentSed.hasGjenlevPensjonType() -> GJENLEVENDEPENSJON

            currentSed is P10000 && currentSed.hasUforePensjonType() -> UFOREPENSJON
            currentSed is P10000 && currentSed.hasGjenlevPensjonType() -> GJENLEVENDEPENSJON

        else -> ALDERSPENSJON
        }
    }

    private fun behandlingstemaPbuc10(currentSed: SED?) : Behandlingstema {
        return when {
            currentSed is P15000 && currentSed.hasUforePensjonType() -> UFOREPENSJON
            currentSed is P15000 && currentSed.hasGjenlevendePensjonType() -> GJENLEVENDEPENSJON
            else -> ALDERSPENSJON
        }
    }

    private fun bestemJournalpostType(sedHendelseType: HendelseType): JournalpostType =
            if (sedHendelseType == HendelseType.SENDT) JournalpostType.UTGAAENDE
            else JournalpostType.INNGAAENDE

    private fun lagTittel(journalpostType: JournalpostType,
                          sedType: SedType) = "${journalpostType.decode()} ${sedType.typeMedBeskrivelse()}"
}