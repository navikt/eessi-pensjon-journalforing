package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.sed.SED
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class FdatoHelper {

    private val logger = LoggerFactory.getLogger(FdatoHelper::class.java)

    fun finnEnFdatoFraSEDer(seder: List<SED>): LocalDate {
        var fdato: String?

        seder.forEach { sed ->
            try {
                if (sed.type.kanInneholdeFnrEllerFdato) {
                    when (sed.type) {
                        SedType.P2100 -> {
                            fdato = filterGjenlevendeFDatoNode(sed)
                        }
                        SedType.P15000 -> {
                            fdato = if (sed.nav?.krav?.type == "02") {
                                filterGjenlevendeFDatoNode(sed)
                            } else {
                                filterPersonFDatoNode(sed)
                            }
                        }
                        SedType.R005 -> {
                            fdato = filterPersonR005DatoNode(sed)
                        }
                        else -> {
                            fdato = filterAnnenPersonFDatoNode(sed)
                            if (fdato == null) {
                                //P2000 - P2200 -- andre..
                                fdato = filterPersonFDatoNode(sed)
                            }
                        }
                    }
                    if(fdato != null) {
                        return LocalDate.parse(fdato, DateTimeFormatter.ISO_DATE)
                    }
                }
            } catch (ex: Exception) {
                logger.error("Noe gikk galt ved henting av fødselsdato fra SED", ex)
            }
        }
        // Fødselsnummer er ikke nødvendig for å fortsette journalføring men fødselsdato er obligatorisk felt i alle krav SED og bør finnes for enhver BUC
        throw RuntimeException("Fant ingen fødselsdato i listen av SEDer")
    }


    /**
     * R005 har mulighet for flere personer.
     * har sed kun en person retureres dette fdato
     * har sed flere personer leter vi etter status 07/avdød_mottaker_av_ytelser og returnerer dette fdato
     *
     * * hvis ingen intreffer returnerer vi null
     */
    private fun filterPersonR005DatoNode(sed: SED): String? =
            sed.nav?.bruker?.first()?.person?.foedselsdato

    /**
     * P10000 - [01] Søker til etterlattepensjon
     * P10000 - [02] Forsørget/familiemedlem
     * P10000 - [03] Barn
     */
    private fun filterAnnenPersonFDatoNode(sed: SED): String? {
        val annenPerson = sed.nav?.annenperson ?: return null
        if (annenPerson.person?.rolle != "01") return null

        return annenPerson.person.foedselsdato
    }

    private fun filterGjenlevendeFDatoNode(sedRootNode: SED): String? =
            sedRootNode.pensjon?.gjenlevende?.person?.foedselsdato

    private fun filterPersonFDatoNode(sed: SED): String? =
            sed.nav?.bruker?.firstOrNull()?.person?.foedselsdato
}
