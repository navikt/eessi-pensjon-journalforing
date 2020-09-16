package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.klienter.pesys.SakStatus
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.BucType.*
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.NORGE
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.UTLAND
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Period

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
        return when {

            routingRequest.diskresjonskode != null && routingRequest.diskresjonskode == "SPSF" -> DISKRESJONSKODE

            routingRequest.bucType == R_BUC_02 -> bestemRbucEnhet(routingRequest)

            else -> bestemTildeltEnhet(routingRequest)
        }
    }

    private fun bestemTildeltEnhet(routingRequest: OppgaveRoutingRequest): Enhet {
        logger.info("Bestemmer tildelt enhet")
        val bosatt = bosatt(routingRequest.landkode)

        return when (bosatt) {
            NORGE -> {
                when (routingRequest.bucType) {
                    P_BUC_01, P_BUC_04 -> NFP_UTLAND_AALESUND
                    P_BUC_02 -> bestemPBuc02Enhet(routingRequest, bosatt)
                    P_BUC_03 -> UFORE_UTLANDSTILSNITT
                    P_BUC_05, P_BUC_06, P_BUC_07, P_BUC_08, P_BUC_09 ->
                        if (isBetween18and60(routingRequest.fdato)) UFORE_UTLANDSTILSNITT else NFP_UTLAND_AALESUND
                    P_BUC_10 ->
                        if (routingRequest.ytelseType == YtelseType.UFOREP) UFORE_UTLANDSTILSNITT else NFP_UTLAND_AALESUND
                    H_BUC_07 ->
                        if (isBetween18and60(routingRequest.fdato)) UFORE_UTLANDSTILSNITT else NFP_UTLAND_OSLO
                    else -> NFP_UTLAND_AALESUND // Ukjent buc-type
                }
            }
            else -> {
                when (routingRequest.bucType) {
                    P_BUC_01, P_BUC_04 -> PENSJON_UTLAND
                    P_BUC_02 -> bestemPBuc02Enhet(routingRequest, bosatt)
                    P_BUC_03 -> UFORE_UTLAND
                    P_BUC_05, P_BUC_06, P_BUC_07, P_BUC_08, P_BUC_09 ->
                        if (isBetween18and60(routingRequest.fdato)) UFORE_UTLAND else PENSJON_UTLAND
                    P_BUC_10 ->
                        if (routingRequest.ytelseType == YtelseType.UFOREP) UFORE_UTLAND else PENSJON_UTLAND
                    H_BUC_07 ->
                        if (isBetween18and60(routingRequest.fdato)) UFORE_UTLAND else PENSJON_UTLAND
                    else -> PENSJON_UTLAND // Ukjent buc-type
                }
            }
        }
    }

    private fun bestemPBuc02Enhet(routingRequest: OppgaveRoutingRequest, bosatt: Bosatt): Enhet =

            when (bosatt) {
                NORGE -> {
                    when (routingRequest.ytelseType) {
                        YtelseType.UFOREP -> if (routingRequest.sakStatus != SakStatus.AVSLUTTET) UFORE_UTLANDSTILSNITT else ID_OG_FORDELING
                        YtelseType.ALDER -> NFP_UTLAND_AALESUND
                        YtelseType.BARNEP -> NFP_UTLAND_AALESUND
                        YtelseType.GJENLEV -> NFP_UTLAND_AALESUND
                        else -> ID_OG_FORDELING
                    }
                }
                else -> {
                    when (routingRequest.ytelseType) {
                        YtelseType.UFOREP -> if (routingRequest.sakStatus != SakStatus.AVSLUTTET) UFORE_UTLAND else ID_OG_FORDELING
                        YtelseType.ALDER -> PENSJON_UTLAND
                        YtelseType.BARNEP -> PENSJON_UTLAND
                        YtelseType.GJENLEV -> PENSJON_UTLAND
                        else -> ID_OG_FORDELING
                    }
                }
            }

    private fun bestemRbucEnhet(routingRequest: OppgaveRoutingRequest): Enhet {
        val ytelseType = routingRequest.ytelseType

        if (routingRequest.identifisertPerson != null && routingRequest.identifisertPerson.flereEnnEnPerson()) {
            return ID_OG_FORDELING
        }

        if (routingRequest.sedType == SedType.R004) {
            return OKONOMI_PENSJON
        }

        if (HendelseType.MOTTATT == routingRequest.hendelseType) {
            return when (ytelseType) {
                YtelseType.ALDER -> PENSJON_UTLAND
                YtelseType.UFOREP -> UFORE_UTLAND
                else -> ID_OG_FORDELING
            }
        }


        return ID_OG_FORDELING
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
        val ytelseType: YtelseType? = null,
        val sedType: SedType? = null,
        val hendelseType: HendelseType? = null,
        val sakStatus: SakStatus? = null,
        val identifisertPerson: IdentifisertPerson? = null
)
