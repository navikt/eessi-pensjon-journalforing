package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.eux.kanInneholdeIdentEllerFdato
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.SedType.P10000
import no.nav.eessi.pensjon.eux.model.SedType.P2000
import no.nav.eessi.pensjon.eux.model.SedType.P2100
import no.nav.eessi.pensjon.eux.model.SedType.P2200
import no.nav.eessi.pensjon.eux.model.SedType.P8000
import no.nav.eessi.pensjon.eux.model.SedType.P9000
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.eux.model.sed.KravType.GJENLEV
import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.X005
import no.nav.eessi.pensjon.eux.model.sed.X008
import no.nav.eessi.pensjon.eux.model.sed.X010
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
        fun fdatoFraSedListe(seder: List<SED>): LocalDate? {
            logger.info("Starter fdatoFraSedListe med ${seder.size} SEDer")
            if (seder.isEmpty())
                throw RuntimeException("Kan ikke hente fødselsdato fra tom SED-liste.")

            val filtered = seder.filter { it.type.kanInneholdeIdentEllerFdato() }
            logger.info("Filtrerte SEDer som kan inneholde ident eller fødselsdato: ${filtered.size}")

            val fdato = filtered.firstNotNullOfOrNull { filterFodselsdato(it) }
            logger.info("Resultat fødselsdato: ${fdato?.toString()?.take(4)}")

            if (fdato != null) {
                return fdato
            }

            if (sederUtenFdato(seder)) {
                logger.warn("Fant ingen fødselsdato i listen av SEDer (P15000 uten FNR)")
                return null
            }
            throw RuntimeException("Fant ingen fødselsdato i listen av SEDer")
        }

        //Det er noen XSed som mangler FNR
        private fun sederUtenFdato(seder: List<SED>) : Boolean {
            val result = seder.any { it.type == SedType.P15000 && it.nav?.krav?.type == GJENLEV }
            logger.info("sederUtenFdato resultat: $result")
            return result
        }

        /**
         * Finner fdato fra de forskjellige sed / sedtypene
         * NB: det må brukes sedtype for vurdering da flere sed ikke er direkte implementert (P11000 mm),
         *     men benytter baseklassen SED og sedType for vurdering
         *
         * @param sed, spesfikke sed som skal vurderes
         * @return Fødselsdato som [LocalDate]
         */
        fun filterFodselsdato(sed: SED): LocalDate? {
            logger.info("Starter filterFodselsdato for SED-type: ${sed.type}")
            return try {
                val fdato = when (sed.type) {
                    R005 -> filterPersonR005Fodselsdato(sed as R005)
                    X005, X008, X010 -> filterPersonFodselsdatoX00Sed(sed)
                    P2000, P2200 -> filterPersonFodselsdato(sed.nav?.bruker?.person)
                    P2100 -> filterGjenlevendeFodselsdato(sed.pensjon?.gjenlevende)
                    P5000 -> leggTilGjenlevendeFdatoHvisFinnes(sed.nav?.bruker?.person, (sed as P5000).pensjon?.gjenlevende)
                    P6000 -> leggTilGjenlevendeFdatoHvisFinnes(sed.nav?.bruker?.person, (sed as P6000).pensjon?.gjenlevende)
                    P8000, P10000 ->  leggTilAnnenPersonFdatoHvisFinnes(sed.nav?.annenperson?.person) ?: filterPersonFodselsdato(sed.nav?.bruker?.person)
                    P9000 ->  filterPersonFodselsdato(sed.nav?.bruker?.person)?: leggTilAnnenPersonFdatoHvisFinnes(sed.nav?.annenperson?.person)
                    P11000 -> leggTilGjenlevendeFdatoHvisFinnes(sed.nav?.bruker?.person, sed.pensjon?.gjenlevende)
                    P12000 -> leggTilGjenlevendeFdatoHvisFinnes(sed.nav?.bruker?.person, sed.pensjon?.gjenlevende)
                    P15000 -> filterP15000(sed as P15000)
                    H121, H120, H070 -> filterPersonFodselsdato(sed.nav?.bruker?.person)
                    else -> leggTilAnnenPersonFdatoHvisFinnes(sed.nav?.annenperson?.person) ?: filterPersonFodselsdato(sed.nav?.bruker?.person)
                }

                logger.info("filterFodselsdato fant fødselsdato-streng: $fdato")
                fdato?.let { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }.also {
                    if (it != null) logger.info("Fant fødselsdato i ${sed.type}, fdato: $it")
                }
            } catch (ex: Exception) {
                logger.error("Noe gikk galt ved henting av fødselsdato fra SED", ex)
                null
            }
        }
        private fun leggTilGjenlevendeFdatoHvisFinnes(person: Person?, gjenlevende: Bruker?): String? {
            val result = if (gjenlevende != null) filterGjenlevendeFodselsdato(gjenlevende)
            else filterPersonFodselsdato(person)
            logger.info("leggTilGjenlevendeFdatoHvisFinnes resultat: $result")
            return result
        }

        private fun filterP15000(sed: P15000): String? {
            val result = if (sed.nav?.krav?.type == GJENLEV) filterGjenlevendeFodselsdato(sed.pensjon?.gjenlevende)
            else filterPersonFodselsdato(sed.nav?.bruker?.person)
            logger.info("filterP15000 resultat: $result")
            return result
        }

        /**
         * R005 har mulighet for flere personer.
         * har sed kun en person returneres dette fdato
         * har sed flere personer leter vi etter status 07/avdød_mottaker_av_ytelser og returnerer dette fdato
         *
         * * hvis ingen intreffer returnerer vi null
         */

        private fun filterPersonR005Fodselsdato(sed: R005): String? {
            val result = sed.recoveryNav?.forsikret?.bruker?.person?.foedselsdato
            logger.info("filterPersonR005Fodselsdato resultat: $result")
            return result
        }

        /**
         * P10000 - [01] Søker til etterlattepensjon
         * P10000 - [02] Forsørget/familiemedlem
         * P10000 - [03] Barn
         */
        private fun leggTilAnnenPersonFdatoHvisFinnes(annenPerson: Person?): String? {
            logger.info("Annen persons rolle er: ${annenPerson?.rolle}")
            if (annenPerson?.rolle != Rolle.ETTERLATTE.kode) return null
            val result = annenPerson.foedselsdato
            logger.info("leggTilAnnenPersonFdatoHvisFinnes resultat: $result")
            return result
        }

        private fun filterGjenlevendeFodselsdato(gjenlevende: Bruker?): String? {
            val result = gjenlevende?.person?.foedselsdato
            logger.info("filterGjenlevendeFodselsdato resultat: $result")
            return result
        }

        private fun filterPersonFodselsdato(person: Person?): String? {
            val result = person?.foedselsdato
            logger.info("filterPersonFodselsdato resultat: $result")
            return result
        }

        private fun filterPersonFodselsdatoX00Sed(sed: SED) : String? {
            val result = when (sed) {
                is X005 -> filterPersonFodselsdato(sed.xnav?.sak?.kontekst?.bruker?.person)
                is X010 -> filterPersonFodselsdato(sed.xnav?.sak?.kontekst?.bruker?.person)
                is X008 -> filterPersonFodselsdato(sed.xnav?.sak?.kontekst?.bruker?.person)
                else -> null
            }
            logger.info("filterPersonFodselsdatoX00Sed resultat: $result")
            return result
        }
    }
}
