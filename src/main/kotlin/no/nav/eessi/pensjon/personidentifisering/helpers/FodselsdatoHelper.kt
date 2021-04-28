package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.models.sed.kanInneholdeIdentEllerFdato
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FodselsdatoHelper {
    companion object {
        private val logger = LoggerFactory.getLogger(FodselsdatoHelper::class.java)

        /**
         *
         * Fødselsnummer er ikke nødvendig for å fortsette journalføring men fødselsdato
         * er obligatorisk felt i alle krav SED og bør finnes for enhver BUC
         *
         * @return siste fødselsdato i SED-listen som [LocalDate]
         */
        fun fraSedListe(seder: List<SED>, kansellerteSeder: List<SED>): LocalDate? {
            if (seder.isEmpty() && kansellerteSeder.isEmpty())
                throw RuntimeException("Kan ikke hente fødselsdato fra tom SED-liste.")

            val fdato = seder
                    .filter { it.type.kanInneholdeIdentEllerFdato() }
                    .mapNotNull { filterFodselsdato(it) }
                    .firstOrNull()

            if (fdato != null) {
                return fdato
            }

            val kansellertfdato = kansellerteSeder
                    .filter { it.type.kanInneholdeIdentEllerFdato() }
                    .mapNotNull { filterFodselsdato(it) }
                    .firstOrNull()

            if (kansellertfdato != null) {
                return kansellertfdato
            } else if (sederUtenFdato(seder)) {
                return null
            }
            throw RuntimeException("Fant ingen fødselsdato i listen av SEDer")
        }

        private fun sederUtenFdato(seder: List<SED>) : Boolean {
            return seder.any { it.type == SedType.P15000 && it.nav?.krav?.type == KravType.ETTERLATTE.name }
        }

        private fun filterFodselsdato(sed: SED): LocalDate? {
            return try {
                val fdato = when (sed.type) {
                    SedType.R005 -> filterPersonR005Fodselsdato(sed)
                    SedType.P2100 -> filterGjenlevendeFodselsdato(sed)
                    SedType.P15000 -> filterP15000(sed)
                    else -> filterAnnenPersonFodselsdato(sed) ?: filterPersonFodselsdato(sed)
                }

                fdato?.let { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }
            } catch (ex: Exception) {
                logger.error("Noe gikk galt ved henting av fødselsdato fra SED", ex)
                null
            }
        }

        private fun filterP15000(sed: SED): String? {
            return if (sed.nav?.krav?.type == KravType.ETTERLATTE) filterGjenlevendeFodselsdato(sed)
            else filterPersonFodselsdato(sed)
        }


        /**
         * R005 har mulighet for flere personer.
         * har sed kun en person retureres dette fdato
         * har sed flere personer leter vi etter status 07/avdød_mottaker_av_ytelser og returnerer dette fdato
         *
         * * hvis ingen intreffer returnerer vi null
         */
        private fun filterPersonR005Fodselsdato(sed: SED): String? =
                sed.nav?.bruker?.first()?.person?.foedselsdato

        /**
         * P10000 - [01] Søker til etterlattepensjon
         * P10000 - [02] Forsørget/familiemedlem
         * P10000 - [03] Barn
         */
        private fun filterAnnenPersonFodselsdato(sed: SED): String? {
            val annenPerson = sed.nav?.annenperson ?: return null
            if (annenPerson.person?.rolle != Rolle.ETTERLATTE.name) return null

            return annenPerson.person?.foedselsdato
        }

        private fun filterGjenlevendeFodselsdato(sed: SED): String? = sed.pensjon?.gjenlevende?.person?.foedselsdato

        private fun filterPersonFodselsdato(sed: SED): String? = sed.nav?.bruker?.firstOrNull()?.person?.foedselsdato
    }
}
