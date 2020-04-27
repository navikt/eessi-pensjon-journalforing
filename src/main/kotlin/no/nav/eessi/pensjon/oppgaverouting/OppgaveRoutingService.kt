package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.BucType.*
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Period
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.YtelseType.*

@Service
class OppgaveRoutingService(private val norg2Klient: Norg2Klient) {

    private val logger = LoggerFactory.getLogger(OppgaveRoutingService::class.java)

    fun route(routingRequest: OppgaveRoutingRequest): Enhet {
        println(routingRequest)
        logger.debug("person: $routingRequest,  bucType: ${routingRequest.bucType}, ytelseType: ${routingRequest.ytelseType}")

        if (routingRequest.fnr == null) return ID_OG_FORDELING
        val norgKlientRequest = NorgKlientRequest(routingRequest.diskresjonskode, routingRequest.landkode, routingRequest.geografiskTilknytning)

        val tildeltEnhet = hentNorg2Enhet(norgKlientRequest, routingRequest.bucType)
                ?: bestemEnhet(routingRequest, routingRequest.bucType)

        logger.info("Router oppgave til $tildeltEnhet (${tildeltEnhet.enhetsNr}) for:" +
                "Buc: ${routingRequest.bucType}, " +
                "Landkode: ${routingRequest.landkode}, " +
                "Fødselsdato: ${routingRequest.fdato}, " +
                "Geografisk Tilknytning: ${routingRequest.geografiskTilknytning}, " +
                "Ytelsetype: ${routingRequest.ytelseType}")

        return tildeltEnhet
    }

    private fun bestemEnhet(routingRequest: OppgaveRoutingRequest, bucType: BucType?) : Enhet {
        return when {
            routingRequest.fnr == null -> ID_OG_FORDELING
            routingRequest.diskresjonskode != null && routingRequest.diskresjonskode == "SPSF" -> DISKRESJONSKODE

            bucType == R_BUC_02 -> bestemRbucEnhet(routingRequest, bucType)
            else -> bestemTildeltEnhet(routingRequest, bucType, routingRequest.ytelseType)
        }
    }

    private fun bestemTildeltEnhet(routingRequest: OppgaveRoutingRequest, bucType: BucType?, ytelseType: String?): Enhet {
        logger.info("Bestemmer tildelt enhet")
        val bosatt = bosatt(routingRequest.landkode)

        return when {
            NORGE == bosatt -> {
                when (bucType) {
                    P_BUC_01, P_BUC_02, P_BUC_04 -> NFP_UTLAND_AALESUND
                    P_BUC_03 -> UFORE_UTLANDSTILSNITT
                    P_BUC_05, P_BUC_06, P_BUC_07, P_BUC_08, P_BUC_09 ->
                        if (isBetween18and60(routingRequest.fdato)) UFORE_UTLANDSTILSNITT else NFP_UTLAND_AALESUND
                    P_BUC_10 ->
                        if (ytelseType == UT.name) UFORE_UTLANDSTILSNITT else NFP_UTLAND_AALESUND
                    H_BUC_07 ->
                        if (isBetween18and60(routingRequest.fdato)) UFORE_UTLANDSTILSNITT else NFP_UTLAND_OSLO
                    else -> NFP_UTLAND_AALESUND // Ukjent buc-type
                }
            }
            else -> {
                when (bucType) {
                    P_BUC_01, P_BUC_02, P_BUC_04 -> PENSJON_UTLAND
                    P_BUC_03 -> UFORE_UTLAND
                    P_BUC_05, P_BUC_06, P_BUC_07, P_BUC_08, P_BUC_09 ->
                        if (isBetween18and60(routingRequest.fdato)) UFORE_UTLAND else PENSJON_UTLAND
                    P_BUC_10 ->
                        if (ytelseType == UT.name) UFORE_UTLAND else PENSJON_UTLAND
                    H_BUC_07 ->
                        if (isBetween18and60(routingRequest.fdato)) UFORE_UTLAND else PENSJON_UTLAND
                    else -> PENSJON_UTLAND // Ukjent buc-type
                }
            }
        }
    }

    private fun bestemRbucEnhet(routingRequest: OppgaveRoutingRequest, bucType: BucType?): Enhet {
        val bosatt = bosatt(routingRequest.landkode)
        if (HendelseType.MOTTATT == routingRequest.hendelseType) {
            return when (bosatt) {
                NORGE ->  ID_OG_FORDELING
                else -> PENSJON_UTLAND
            }
        } else {
            return when (bosatt) {
                NORGE ->  ID_OG_FORDELING
                else -> {
                    if (routingRequest.sedType == SedType.R004) {
                        OKONOMI_PENSJON
                    } else
                    PENSJON_UTLAND
                }
            }
        }

    }

    fun hentNorg2Enhet(person: NorgKlientRequest, bucType: BucType?): Enhet? {

        return when (bucType) {
            P_BUC_01 -> {
                try {
                    val enhetVerdi = norg2Klient.hentArbeidsfordelingEnhet(person)
                    logger.info("Norg2tildeltEnhet: $enhetVerdi")
                    enhetVerdi?.let { Enhet.getEnhet(it) }
                } catch (ex: Exception) {
                    logger.error("Ukjent feil oppstod; ${ex.message}")
                    null
                }
            }
            else -> null
        }
    }

    private fun bosatt(landkode: String?): Bosatt =
            when {
                landkode.isNullOrEmpty() -> Bosatt.UKJENT
                landkode == "NOR" -> NORGE
                else -> UTLAND
            }

    private fun isBetween18and60(fodselsDato: LocalDate): Boolean {
        val alder = Period.between(fodselsDato, LocalDate.now())
        return (alder.years >= 18) && (alder.years < 60)
    }
}

class OppgaveRoutingRequest(
        val fnr: String? = null,
        val fdato: LocalDate,
        val diskresjonskode: String? = null,
        val landkode: String? = null,
        val geografiskTilknytning: String? = null,
        val bucType: BucType? = null,
        val ytelseType: String? = null,
        val sedType: SedType? = null,
        val hendelseType: HendelseType? = null)
