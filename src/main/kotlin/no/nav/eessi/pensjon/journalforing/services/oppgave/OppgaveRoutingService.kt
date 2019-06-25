package no.nav.eessi.pensjon.journalforing.services.oppgave

import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelseModel
import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelseModel.BucType.*
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel.Bosatt
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel.Bosatt.NORGE
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel.Bosatt.UTLAND
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel.Enhet
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel.Krets.NAY
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel.Krets.NFP
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel.YtelseType.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

private val logger = LoggerFactory.getLogger(OppgaveRoutingService::class.java)

@Service
class OppgaveRoutingService {

    fun route(sedHendelse: SedHendelseModel,
              landkode: String?,
              fodselsDato: String,
              ytelseType: OppgaveRoutingModel.YtelseType?): Enhet {

        logger.info("Router oppgave for hendelsetype: ${sedHendelse.sedType}, " +
                "Landkode: $landkode, " +
                "fødselsdato: $fodselsDato, " +
                "Ytelsetype: $ytelseType")

        if(sedHendelse.navBruker == null) {
            return ID_OG_FORDELING
        }

        val tildeltEnhet = when (sedHendelse.bucType) {
            P_BUC_01,P_BUC_02,P_BUC_04 -> {
                if(landkode.isNullOrEmpty() || bosatt(landkode) == NORGE) {
                    NFP_UTLAND_AALESUND
                } else {
                    PENSJON_UTLAND
                }
            }
            P_BUC_03 -> {
                if(landkode.isNullOrEmpty() || bosatt(landkode) == NORGE) {
                    UFORE_UTLANDSTILSNITT
                } else {
                    UFORE_UTLAND
                }
            }
            P_BUC_05,P_BUC_06,P_BUC_07,P_BUC_08,P_BUC_09 -> {
                when(krets(fodselsDato)) {
                    NFP -> {
                        if(landkode.isNullOrEmpty() || bosatt(landkode) == NORGE) {
                            UFORE_UTLANDSTILSNITT
                        } else {
                            UFORE_UTLAND
                        }
                    }
                    NAY -> {
                        if(landkode.isNullOrEmpty() || bosatt(landkode) == NORGE) {
                            NFP_UTLAND_AALESUND
                        } else {
                            PENSJON_UTLAND
                        }
                    }
                }
            }
            P_BUC_10 -> {
                when(ytelseType) {
                    AP,GP -> {
                        if(landkode.isNullOrEmpty() || bosatt(landkode) == NORGE) {
                            NFP_UTLAND_AALESUND
                        } else {
                            PENSJON_UTLAND
                        }
                    }
                    UT -> {
                        if(landkode.isNullOrEmpty()) {
                            NFP_UTLAND_AALESUND
                        } else {
                            if(bosatt(landkode) == NORGE) {
                                UFORE_UTLANDSTILSNITT
                            } else {
                                UFORE_UTLAND
                            }
                        }
                    }
                    else -> PENSJON_UTLAND // Ukjent ytelsestype
                }
            }
            else -> PENSJON_UTLAND // Ukjent buc-type
        }
        logger.info("Oppgave blir tildelt enhet: : $tildeltEnhet ${tildeltEnhet.name}")
        return tildeltEnhet
    }

    private fun bosatt(landkode: String): Bosatt {
        return if(landkode == "NOR") {
            NORGE
        } else {
            UTLAND
        }
    }

    private fun krets(fodselsDato: String): OppgaveRoutingModel.Krets {
        val fodselsdatoString = fodselsDato.substring(0,6)
        val format = DateTimeFormatter.ofPattern("ddMMyy")
        val fodselsdatoDate = LocalDate.parse(fodselsdatoString, format)
        val dagensDate = LocalDate.now()
        val period = Period.between(fodselsdatoDate, dagensDate)

        return if(  (period.years > 18) && (period.years < 60)) {
            NFP
        } else NAY
    }
}
