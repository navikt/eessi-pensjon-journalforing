package no.nav.eessi.pensjon.journalforing.oppgaverouting

import no.nav.eessi.pensjon.journalforing.models.sed.SedHendelseModel
import no.nav.eessi.pensjon.journalforing.models.sed.SedHendelseModel.BucType.*
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingModel.Bosatt
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingModel.Bosatt.*
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingModel.Enhet
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingModel.Krets.NAY
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingModel.Krets.NFP
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingModel.YtelseType.UT
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

@Service
class OppgaveRoutingService {

    private val logger = LoggerFactory.getLogger(OppgaveRoutingService::class.java)

    fun route(sedHendelse: SedHendelseModel,
              landkode: String?,
              fodselsDato: String,
              ytelseType: OppgaveRoutingModel.YtelseType?): Enhet {

        val tildeltEnhet =
                when {
                    sedHendelse.navBruker == null -> ID_OG_FORDELING

                    NORGE == bosatt(landkode) ->
                        when (sedHendelse.bucType) {
                            P_BUC_01, P_BUC_02, P_BUC_04 -> NFP_UTLAND_AALESUND
                            P_BUC_03 -> UFORE_UTLANDSTILSNITT
                            P_BUC_05, P_BUC_06, P_BUC_07, P_BUC_08, P_BUC_09 ->
                                if (krets(fodselsDato) == NAY) UFORE_UTLANDSTILSNITT else NFP_UTLAND_AALESUND
                            P_BUC_10 ->
                                if (ytelseType == UT) UFORE_UTLANDSTILSNITT else NFP_UTLAND_AALESUND
                            else -> NFP_UTLAND_AALESUND // Ukjent buc-type
                        }

                    else ->
                        when (sedHendelse.bucType) {
                            P_BUC_01, P_BUC_02, P_BUC_04 -> PENSJON_UTLAND
                            P_BUC_03 -> UFORE_UTLAND
                            P_BUC_05, P_BUC_06, P_BUC_07, P_BUC_08, P_BUC_09 ->
                                if (krets(fodselsDato) == NAY) UFORE_UTLAND else PENSJON_UTLAND
                            P_BUC_10 ->
                                if (ytelseType == UT) UFORE_UTLAND else PENSJON_UTLAND
                            else -> PENSJON_UTLAND // Ukjent buc-type
                        }
                }

        logger.info("Router oppgave til $tildeltEnhet (${tildeltEnhet.enhetsNr}) " +
                "for Buc: ${sedHendelse.bucType}, " +
                "Hendelsetype: ${sedHendelse.sedType?.decode()}, " +
                "Landkode: $landkode, " +
                "Fødselsdato: $fodselsDato, " +
                "Ytelsetype: $ytelseType")

        return tildeltEnhet
    }

    private fun bosatt(landkode: String?): Bosatt =
            when {
                landkode.isNullOrEmpty() -> UKJENT
                landkode == "NOR" -> NORGE
                else -> UTLAND
            }

    private fun krets(fodselsDato: String): OppgaveRoutingModel.Krets {
        val fodselsdatoString = fodselsDato.substring(0,6)
        val format = DateTimeFormatter.ofPattern("ddMMyy")
        val fodselsdatoDate = LocalDate.parse(fodselsdatoString, format)
        val dagensDate = LocalDate.now()
        val period = Period.between(fodselsdatoDate, dagensDate)

        return if((period.years >= 18) && (period.years < 60)) NAY else NFP
    }
}
