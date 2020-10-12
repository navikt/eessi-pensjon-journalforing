package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.*
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.NORGE
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.UTLAND
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class OppgaveRoutingService(private val norg2Klient: Norg2Klient) {

    private val logger = LoggerFactory.getLogger(OppgaveRoutingService::class.java)

    fun route(routingRequest: OppgaveRoutingRequest): Enhet {
        logger.debug("personfdato: ${routingRequest.fdato},  bucType: ${routingRequest.bucType}, ytelseType: ${routingRequest.ytelseType}")

        if (routingRequest.fnr == null) return ID_OG_FORDELING
        val norgKlientRequest = NorgKlientRequest(routingRequest.diskresjonskode, routingRequest.landkode, routingRequest.geografiskTilknytning)

        val tildeltEnhet = hentNorg2Enhet(norgKlientRequest, routingRequest.bucType)
                ?: bestemEnhet(routingRequest)

        logger.info("Router oppgave til $tildeltEnhet (${tildeltEnhet.enhetsNr}) for:" +
                "Buc: ${routingRequest.bucType}, " +
                "Landkode: ${routingRequest.landkode}, " +
                "Fødselsdato: ${routingRequest.fdato}, " +
                "Geografisk Tilknytning: ${routingRequest.geografiskTilknytning}, " +
                "Ytelsetype: ${routingRequest.ytelseType}")

        return tildeltEnhet
    }

    private fun bestemEnhet(routingRequest: OppgaveRoutingRequest): Enhet {
        //------------------

        routingRequest.bosatt = bosatt(routingRequest.landkode)

        return when {
            routingRequest.diskresjonskode != null && routingRequest.diskresjonskode == "SPSF" -> DISKRESJONSKODE
            routingRequest.bucType == null -> PENSJON_UTLAND
            else -> routingRequest.route(routingRequest)
        }
    }

    fun hentNorg2Enhet(person: NorgKlientRequest, bucType: BucType?): Enhet? {

        return when (bucType) {
            BucType.P_BUC_01 -> {
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
}

class OppgaveRoutingRequest(
        val fnr: String? = null,
        val fdato: LocalDate,
        val diskresjonskode: String? = null,
        val landkode: String? = null,
        val geografiskTilknytning: String? = null,
        val ytelseType: YtelseType? = null,
        val sedType: SedType? = null,
        val hendelseType: HendelseType? = null,
        val sakStatus: SakStatus? = null,
        val identifisertPerson: IdentifisertPerson? = null,
        var bosatt: Bosatt? = null,
        val bucType: BucType? = null)

