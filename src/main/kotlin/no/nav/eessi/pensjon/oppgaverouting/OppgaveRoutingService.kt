package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.BucType.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.YtelseType.UT
import no.nav.eessi.pensjon.services.norg2.Diskresjonskode
import no.nav.eessi.pensjon.services.norg2.Norg2ArbeidsfordelingRequestException
import no.nav.eessi.pensjon.services.norg2.Norg2Service
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Period

@Service
class OppgaveRoutingService(private val norg2Service: Norg2Service) {

    private val logger = LoggerFactory.getLogger(OppgaveRoutingService::class.java)

    fun route(navBruker: String? = null,
              bucType: BucType? = null,
              landkode: String? = null,
              fodselsDato: LocalDate,
              geografiskTilknytning: String? = null,
              diskresjonskode: Diskresjonskode? = null,
              ytelseType: OppgaveRoutingModel.YtelseType? = null): Enhet {

        logger.debug("navBruker: $navBruker  bucType: $bucType  landkode: $landkode  fodselsDato: $fodselsDato  geografiskTilknytning: $geografiskTilknytning  ytelseType: $ytelseType")

        val tildeltEnhet = hentNorg2Enhet(navBruker, geografiskTilknytning, landkode, bucType, diskresjonskode) ?: bestemTildeltEnhet(navBruker, landkode, bucType, fodselsDato, ytelseType, diskresjonskode)

        logger.info("Router oppgave til $tildeltEnhet (${tildeltEnhet.enhetsNr}) for:" +
                "Buc: $bucType, " +
                "Landkode: $landkode, " +
                "Fødselsdato: $fodselsDato, " +
                "Geografisk Tilknytning: $geografiskTilknytning, " +
                "Ytelsetype: $ytelseType")

        return tildeltEnhet
    }

    private fun bestemTildeltEnhet(navBruker: String?, landkode: String?, bucType: BucType?, fodselsDato: LocalDate, ytelseType: OppgaveRoutingModel.YtelseType?, diskresjonskode: Diskresjonskode?): Enhet {
        logger.info("Bestemmer tildelt enhet")
        return when {
                    navBruker == null -> ID_OG_FORDELING

                    diskresjonskode != null && diskresjonskode == Diskresjonskode.SPSF -> DISKRESJONSKODE

                    NORGE == bosatt(landkode) ->
                        when (bucType) {
                            P_BUC_01, P_BUC_02, P_BUC_04 -> NFP_UTLAND_AALESUND
                            P_BUC_03 -> UFORE_UTLANDSTILSNITT
                            P_BUC_05, P_BUC_06, P_BUC_07, P_BUC_08, P_BUC_09 ->
                                if (isBetween18and60(fodselsDato)) UFORE_UTLANDSTILSNITT else NFP_UTLAND_AALESUND
                            P_BUC_10 ->
                                if (ytelseType == UT) UFORE_UTLANDSTILSNITT else NFP_UTLAND_AALESUND
                            else -> NFP_UTLAND_AALESUND // Ukjent buc-type
                        }

                    else ->
                        when (bucType) {
                            P_BUC_01, P_BUC_02, P_BUC_04 -> PENSJON_UTLAND
                            P_BUC_03 -> UFORE_UTLAND
                            P_BUC_05, P_BUC_06, P_BUC_07, P_BUC_08, P_BUC_09 ->
                                if (isBetween18and60(fodselsDato)) UFORE_UTLAND else PENSJON_UTLAND
                            P_BUC_10 ->
                                if (ytelseType == UT) UFORE_UTLAND else PENSJON_UTLAND
                            else -> PENSJON_UTLAND // Ukjent buc-type
                        }
                }
    }

    fun hentNorg2Enhet(navBruker: String?, geografiskTilknytning: String?, landkode: String?, bucType: BucType?, diskresjonskode: Diskresjonskode?): Enhet? {
        if (navBruker == null) return null

        return when(bucType) {
            P_BUC_01 -> {
                try {
                    val enhetVerdi = norg2Service.hentArbeidsfordelingEnhet(geografiskTilknytning, landkode, diskresjonskode)
                    logger.info("Norg2tildeltEnhet: $enhetVerdi")
                    enhetVerdi?.let { Enhet.getEnhet(it) }
                } catch (rqe: Norg2ArbeidsfordelingRequestException) {
                    logger.error("Norg2 request feil ${rqe.message}")
                    null
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
                landkode.isNullOrEmpty() -> UKJENT
                landkode == "NOR" -> NORGE
                else -> UTLAND
            }

    private fun isBetween18and60(fodselsDato: LocalDate): Boolean {
        val alder = Period.between(fodselsDato, LocalDate.now())
        return (alder.years >= 18) && (alder.years < 60)
    }
}
