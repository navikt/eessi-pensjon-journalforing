package no.nav.eessi.pensjon.klienter.journalpost

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.buc.SakType.BARNEP
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Behandlingstema.*
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
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
        fnr: Fodselsnummer?,
        sedHendelseType: HendelseType,
        journalfoerendeEnhet: Enhet,
        arkivsaksnummer: Sak?,
        dokumenter: String,
        saktype: SakType?,
        institusjon: AvsenderMottaker,
        identifisertePersoner: Int,
        saksbehandlerInfo: Pair<String, Enhet?>? = null,
        tema: Tema
    ): OpprettJournalPostResponse? {

        val request = OpprettJournalpostRequest(
            avsenderMottaker = institusjon,
            behandlingstema = bestemBehandlingsTema(sedHendelse.bucType!!, saktype, tema, identifisertePersoner),
            bruker = fnr?.let { Bruker(id = it.value) },
            journalpostType = bestemJournalpostType(sedHendelseType),
            sak = arkivsaksnummer,
            tema = tema,
            tilleggsopplysninger = listOf(Tilleggsopplysning(TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY, sedHendelse.rinaSakId)),
            tittel = lagTittel(bestemJournalpostType(sedHendelseType), sedHendelse.sedType!!),
            dokumenter = dokumenter,
            journalfoerendeEnhet = saksbehandlerInfo?.second ?: journalfoerendeEnhet
        )

        val forsokFerdigstill: Boolean = kanSakFerdigstilles(request)

        return journalpostKlient.opprettJournalpost(request, forsokFerdigstill, saksbehandlerInfo?.first)
    }

    private fun kanSakFerdigstilles(request: OpprettJournalpostRequest): Boolean {
        val alleVerdierErSatt = listOf(
            request.bruker,
            request.journalfoerendeEnhet,
            request.kanal,
            request.tema,
            request.sak,
            request.avsenderMottaker,
            request.tittel,
            request.dokumenter
        ).any { it == null }
        if(!alleVerdierErSatt)
        {
            val vasketFnr = request.bruker?.id?.isNotEmpty()
            """Journalpost kan ikke ferdigstilles da det mangler data:
                |    sak: ${request.sak},
                |    tema: ${request.tema},
                |    kanal: ${request.kanal},
                |    tittel: ${request.tittel},
                |    dokumenter.str: ${request.dokumenter.length},
                |    avsenderMottaker: ${request.avsenderMottaker},
                |    bruker: ${if (vasketFnr) "*******" else "Mangler fnr"},
                |    journalfoerendeEnhet: ${request.journalfoerendeEnhet}""".trimMargin()

        }
        return !alleVerdierErSatt
    }

    /**
     *  Ferdigstiller journalposten.
     *
     *  @param journalpostId: ID til journalposten som skal ferdigstilles.
     */
    fun oppdaterDistribusjonsinfo(journalpostId: String) = journalpostKlient.oppdaterDistribusjonsinfo(journalpostId)
    fun settStatusAvbrutt(journalpostId: String) = journalpostKlient.settStatusAvbrutt(journalpostId)

    fun bestemBehandlingsTema(bucType: BucType, saktype: SakType?, tema: Tema, identifisertePersoner: Int): Behandlingstema {

        if(bucType == P_BUC_01) return ALDERSPENSJON
        if(bucType == P_BUC_02) return GJENLEVENDEPENSJON
        if(bucType == P_BUC_03) return UFOREPENSJON

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

    private fun bestemJournalpostType(sedHendelseType: HendelseType): JournalpostType =
            if (sedHendelseType == HendelseType.SENDT) JournalpostType.UTGAAENDE
            else JournalpostType.INNGAAENDE

    private fun lagTittel(journalpostType: JournalpostType,
                          sedType: SedType) = "${journalpostType.decode()} ${sedType.typeMedBeskrivelse()}"
}