package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.BucType.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Bosatt.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.klienter.norg2.Norg2ArbeidsfordelingRequestException
import no.nav.eessi.pensjon.klienter.norg2.Norg2Klient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Period
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.YtelseType.*
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson

@Service
class OppgaveRoutingService(private val norg2Klient: Norg2Klient) {

    private val logger = LoggerFactory.getLogger(OppgaveRoutingService::class.java)

    fun route(person: IdentifisertPerson,
              bucType: BucType? = null,
              ytelseType: String? = null): Enhet {

        logger.debug("person: $person,  bucType: $bucType, ytelseType: $ytelseType")

        val tildeltEnhet = hentNorg2Enhet(person, bucType) ?: bestemTildeltEnhet(person, bucType, ytelseType)

        logger.info("Router oppgave til $tildeltEnhet (${tildeltEnhet.enhetsNr}) for:" +
                "Buc: $bucType, " +
                "Landkode: ${person.landkode}, " +
                "Fødselsdato: ${person.fdato}, " +
                "Geografisk Tilknytning: ${person.geografiskTilknytning}, " +
                "Ytelsetype: $ytelseType")

        return tildeltEnhet
    }

    private fun bestemTildeltEnhet(person: IdentifisertPerson, bucType: BucType?, ytelseType: String?): Enhet {
        logger.info("Bestemmer tildelt enhet")
        return when {
                    person.fnr == null -> ID_OG_FORDELING

                    person.diskresjonskode != null && person.diskresjonskode == "SPSF" -> DISKRESJONSKODE

                    NORGE == bosatt(person.landkode) ->
                        when (bucType) {
                            P_BUC_01, P_BUC_02, P_BUC_04 -> NFP_UTLAND_AALESUND
                            P_BUC_03 -> UFORE_UTLANDSTILSNITT
                            P_BUC_05, P_BUC_06, P_BUC_07, P_BUC_08, P_BUC_09 ->
                                if (isBetween18and60(person.fdato)) UFORE_UTLANDSTILSNITT else NFP_UTLAND_AALESUND
                            P_BUC_10 ->
                                if (ytelseType == UT.name) UFORE_UTLANDSTILSNITT else NFP_UTLAND_AALESUND
                            else -> NFP_UTLAND_AALESUND // Ukjent buc-type
                        }

                    else ->
                        when (bucType) {
                            P_BUC_01, P_BUC_02, P_BUC_04 -> PENSJON_UTLAND
                            P_BUC_03 -> UFORE_UTLAND
                            P_BUC_05, P_BUC_06, P_BUC_07, P_BUC_08, P_BUC_09 ->
                                if (isBetween18and60(person.fdato)) UFORE_UTLAND else PENSJON_UTLAND
                            P_BUC_10 ->

                                if (ytelseType == UT.name) UFORE_UTLAND else PENSJON_UTLAND
                            else -> PENSJON_UTLAND // Ukjent buc-type
                        }
                }
    }

    fun hentNorg2Enhet(person: IdentifisertPerson, bucType: BucType?): Enhet? {
        if (person.fnr == null) return null

        return when(bucType) {
            P_BUC_01 -> {
                try {
                    val enhetVerdi = norg2Klient.hentArbeidsfordelingEnhet(person)
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
                landkode.isNullOrEmpty() -> Bosatt.UKJENT
                landkode == "NOR" -> NORGE
                else -> UTLAND
            }

    private fun isBetween18and60(fodselsDato: LocalDate): Boolean {
        val alder = Period.between(fodselsDato, LocalDate.now())
        return (alder.years >= 18) && (alder.years < 60)
    }
}
