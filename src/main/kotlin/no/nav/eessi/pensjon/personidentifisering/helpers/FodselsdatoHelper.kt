package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.eux.model.sed.X005
import no.nav.eessi.pensjon.eux.model.sed.X008
import no.nav.eessi.pensjon.eux.model.sed.X010
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
        fun fdatoFraSedListe(seder: List<SED>, kansellerteSeder: List<SED>): LocalDate? {
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
            return seder.any { it.type == SedType.P15000 && it.nav?.krav?.type == "02" }
        }

        private fun filterFodselsdato(sed: SED): LocalDate? {
            return try {
                val fdato = when (sed.type) {
                    SedType.R005 -> filterPersonR005Fodselsdato(sed as R005)
                    SedType.P2000, SedType.P2200 -> filterPersonFodselsdato(sed.nav?.bruker?.person)
                    SedType.P2100 -> filterGjenlevendeFodselsdato(sed.pensjon?.gjenlevende)
                    SedType.P5000 -> leggTilGjenlevendeFdatoHvisFinnes(sed.nav?.bruker?.person, (sed as P5000).p5000Pensjon?.gjenlevende)
                    SedType.P6000 -> leggTilGjenlevendeFdatoHvisFinnes(sed.nav?.bruker?.person, (sed as P6000).p6000Pensjon?.gjenlevende)
                    SedType.P8000, SedType.P10000 -> leggTilAnnenPersonFdatoHvisFinnes(sed.nav?.bruker?.person, sed.nav?.annenperson?.person)
                    SedType.P15000 -> filterP15000(sed as P15000)
                    SedType.H121, SedType.H120, SedType.H070 -> filterPersonFodselsdato(sed.nav?.bruker?.person)
                    SedType.X005, SedType.X008, SedType.X010 -> filterPersonFodselsdatoX00Sed(sed)
                    else -> filterAnnenPersonFodselsdato(sed.nav?.annenperson?.person) ?: filterPersonFodselsdato(sed.nav?.bruker?.person)
                }

                fdato?.let { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }.also {
                    if (it != null) logger.info("Fant fødselsdato i ${sed.type}")
                }
            } catch (ex: Exception) {
                logger.error("Noe gikk galt ved henting av fødselsdato fra SED", ex)
                null
            }
        }

        private fun leggTilAnnenPersonFdatoHvisFinnes(person: Person?, annenPerson: Person?): String? {
            return filterAnnenPersonFodselsdato(annenPerson) ?: filterPersonFodselsdato(person)
        }

        private fun leggTilGjenlevendeFdatoHvisFinnes(person: Person?, gjenlevende: Bruker?): String? {
            return if (gjenlevende != null) filterGjenlevendeFodselsdato(gjenlevende)
            else filterPersonFodselsdato(person)
        }

        private fun filterP15000(sed: P15000): String? {
            return if (sed.nav?.krav?.type == "02") filterGjenlevendeFodselsdato(sed.p15000Pensjon?.gjenlevende)
            else filterPersonFodselsdato(sed.nav?.bruker?.person)
        }


        /**
         * R005 har mulighet for flere personer.
         * har sed kun en person retureres dette fdato
         * har sed flere personer leter vi etter status 07/avdød_mottaker_av_ytelser og returnerer dette fdato
         *
         * * hvis ingen intreffer returnerer vi null
         */
        private fun filterPersonR005Fodselsdato(sed: R005): String? =
                sed.recoveryNav?.brukere?.first()?.person?.foedselsdato

        /**
         * P10000 - [01] Søker til etterlattepensjon
         * P10000 - [02] Forsørget/familiemedlem
         * P10000 - [03] Barn
         */
        private fun filterAnnenPersonFodselsdato(annenPerson: Person?): String? {
            if (annenPerson?.rolle != Rolle.ETTERLATTE.name) return null
            return annenPerson.foedselsdato
        }

        private fun filterGjenlevendeFodselsdato(gjenlevende: Bruker?): String? = gjenlevende?.person?.foedselsdato

        private fun filterPersonFodselsdato(person: Person?): String? = person?.foedselsdato

        private fun filterPersonFodselsdatoX00Sed(sed: SED) : String? {
            return when {
                sed is X005 -> filterPersonFodselsdato(sed.xnav?.sak?.kontekst?.bruker?.person)
                sed is X010 -> filterPersonFodselsdato(sed.xnav?.sak?.kontekst?.bruker?.person)
                sed is X008 -> filterPersonFodselsdato(sed.xnav?.sak?.kontekst?.bruker?.person)
                else -> null
            }
        }
    }
}
