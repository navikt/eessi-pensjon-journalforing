package no.nav.eessi.pensjon.journalforing.services.oppgave

import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelseModel
import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelseModel.BucType.*
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel.Krets.*
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel.Enhet
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel.Bosatt.*
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel.Bosatt
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

        val bosatt: Bosatt
        var tildeltEnhet: Enhet = Enhet.UKJENT

        if(sedHendelse.navBruker == null) {
            return ID_OG_FORDELING
        }

        when (sedHendelse.bucType) {
            P_BUC_01 -> {
                tildeltEnhet = if(landkode.isNullOrEmpty()) {
                    NFP_UTLAND_AALESUND
                } else {
                    bosatt = mapBosatt(landkode)
                    if(bosatt == NORGE) {
                        NFP_UTLAND_AALESUND
                    } else {
                        PENSJON_UTLAND
                    }
                }
            }
            P_BUC_02 -> {
                tildeltEnhet = if(landkode.isNullOrEmpty()) {
                    NFP_UTLAND_AALESUND
                } else {
                    bosatt = mapBosatt(landkode)
                    if(bosatt == NORGE) {
                        NFP_UTLAND_AALESUND
                    } else {
                        PENSJON_UTLAND
                    }
                }
            }
            P_BUC_03 -> {
                tildeltEnhet = if(landkode.isNullOrEmpty()) {
                    UFORE_UTLANDSTILSNITT
                } else {
                    bosatt = mapBosatt(landkode)
                    if(bosatt == NORGE) {
                        UFORE_UTLANDSTILSNITT
                    } else {
                        UFORE_UTLAND
                    }
                }
            }
            P_BUC_04 -> {
                tildeltEnhet = if(landkode.isNullOrEmpty()) {
                    NFP_UTLAND_AALESUND
                } else {
                    bosatt = mapBosatt(landkode)
                    if(bosatt == NORGE) {
                        NFP_UTLAND_AALESUND
                    } else {
                        PENSJON_UTLAND
                    }
                }
            }
            P_BUC_05 -> {
                when(bestemKrets(fodselsDato)) {
                    NFP -> {
                        tildeltEnhet = if(landkode.isNullOrEmpty()) {
                            UFORE_UTLANDSTILSNITT
                        } else {
                            bosatt = mapBosatt(landkode)
                            if(bosatt == NORGE) {
                                UFORE_UTLANDSTILSNITT
                            } else {
                                UFORE_UTLAND
                            }
                        }
                    }
                    NAY -> {
                        tildeltEnhet = if(landkode.isNullOrEmpty()) {
                            NFP_UTLAND_AALESUND
                        } else {
                            bosatt = mapBosatt(landkode)
                            if(bosatt == NORGE) {
                                NFP_UTLAND_AALESUND
                            } else {
                                PENSJON_UTLAND
                            }
                        }
                    }
                }
            }
            P_BUC_06 -> {
                when(bestemKrets(fodselsDato)) {
                    NFP -> {
                        tildeltEnhet = if(landkode.isNullOrEmpty()) {
                            UFORE_UTLANDSTILSNITT
                        } else {
                            bosatt = mapBosatt(landkode)
                            if(bosatt == NORGE) {
                                UFORE_UTLANDSTILSNITT
                            } else {
                                UFORE_UTLAND
                            }
                        }
                    }
                    NAY -> {
                        tildeltEnhet = if(landkode.isNullOrEmpty()) {
                            NFP_UTLAND_AALESUND
                        } else {
                            bosatt = mapBosatt(landkode)
                            if(bosatt == NORGE) {
                                NFP_UTLAND_AALESUND
                            } else {
                                PENSJON_UTLAND
                            }
                        }
                    }
                }
            }
            P_BUC_07 -> {
                when(bestemKrets(fodselsDato)) {
                    NFP -> {
                        tildeltEnhet = if(landkode.isNullOrEmpty()) {
                            UFORE_UTLANDSTILSNITT
                        } else {
                            bosatt = mapBosatt(landkode)
                            if(bosatt == NORGE) {
                                UFORE_UTLANDSTILSNITT
                            } else {
                                UFORE_UTLAND
                            }
                        }
                    }
                    NAY -> {
                        tildeltEnhet = if(landkode.isNullOrEmpty()) {
                            NFP_UTLAND_AALESUND
                        } else {
                            bosatt = mapBosatt(landkode)
                            if(bosatt == NORGE) {
                                NFP_UTLAND_AALESUND
                            } else {
                                PENSJON_UTLAND
                            }
                        }
                    }
                }
            }
            P_BUC_08 -> {
                when(bestemKrets(fodselsDato)) {
                    NFP -> {
                        tildeltEnhet = if(landkode.isNullOrEmpty()) {
                            UFORE_UTLANDSTILSNITT
                        } else {
                            bosatt = mapBosatt(landkode)
                            if(bosatt == NORGE) {
                                UFORE_UTLANDSTILSNITT
                            } else {
                                UFORE_UTLAND
                            }
                        }
                    }
                    NAY -> {
                        tildeltEnhet = if(landkode.isNullOrEmpty()) {
                            NFP_UTLAND_AALESUND
                        } else {
                            bosatt = mapBosatt(landkode)
                            if(bosatt == NORGE) {
                                NFP_UTLAND_AALESUND
                            } else {
                                PENSJON_UTLAND
                            }
                        }
                    }
                }
            }
            P_BUC_09 -> {
                when(bestemKrets(fodselsDato)) {
                    NFP -> {
                        tildeltEnhet = if(landkode.isNullOrEmpty()) {
                            UFORE_UTLANDSTILSNITT
                        } else {
                            bosatt = mapBosatt(landkode)
                            if(bosatt == NORGE) {
                                UFORE_UTLANDSTILSNITT
                            } else {
                                UFORE_UTLAND
                            }
                        }
                    }
                    NAY -> {
                        tildeltEnhet = if(landkode.isNullOrEmpty()) {
                            NFP_UTLAND_AALESUND
                        } else {
                            bosatt = mapBosatt(landkode)
                            if(bosatt == NORGE) {
                                NFP_UTLAND_AALESUND
                            } else {
                                PENSJON_UTLAND
                            }
                        }
                    }
                }
            }
            P_BUC_10 -> {
                when(ytelseType) {
                    AP -> {
                        tildeltEnhet = if(landkode.isNullOrEmpty()) {
                            NFP_UTLAND_AALESUND
                        } else {
                            bosatt = mapBosatt(landkode)
                            if(bosatt == NORGE) {
                                NFP_UTLAND_AALESUND
                            } else {
                                PENSJON_UTLAND
                            }
                        }
                    }
                    GP -> {
                        tildeltEnhet = if(landkode.isNullOrEmpty()) {
                            NFP_UTLAND_AALESUND
                        } else {
                            bosatt = mapBosatt(landkode)
                            if(bosatt == NORGE) {
                                NFP_UTLAND_AALESUND
                            } else {
                                PENSJON_UTLAND
                            }
                        }
                    }
                    UT -> {
                        tildeltEnhet = if(landkode.isNullOrEmpty()) {
                            NFP_UTLAND_AALESUND
                        } else {
                            bosatt = mapBosatt(landkode)
                            if(bosatt == NORGE) {
                                UFORE_UTLANDSTILSNITT
                            } else {
                                UFORE_UTLAND
                            }
                        }
                    }
                }
            }
        }
        logger.info("Oppgave blir tildelt enhet: : $tildeltEnhet ${tildeltEnhet.name}")
        return tildeltEnhet
    }

    private fun mapBosatt(landkode: String): Bosatt {
        return if(landkode == "NOR") {
            NORGE
        } else {
            UTLAND
        }
    }

    private fun bestemKrets(fodselsDato: String): OppgaveRoutingModel.Krets {
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
