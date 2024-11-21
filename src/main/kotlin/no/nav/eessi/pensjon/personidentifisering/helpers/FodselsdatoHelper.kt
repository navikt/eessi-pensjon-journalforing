package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.eux.kanInneholdeIdentEllerFdato
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.eux.model.SedType.P10000 as SedTypeP10000
import no.nav.eessi.pensjon.eux.model.SedType.P2000 as SedTypeP2000
import no.nav.eessi.pensjon.eux.model.SedType.P5000 as SedTypeP5000
import no.nav.eessi.pensjon.eux.model.SedType.P6000 as SedTypeP6000
import no.nav.eessi.pensjon.eux.model.SedType.H120 as SedTypeH120
import no.nav.eessi.pensjon.eux.model.SedType.H121 as SedTypeH121
import no.nav.eessi.pensjon.eux.model.SedType.H070 as SedTypeH070
import no.nav.eessi.pensjon.eux.model.SedType.R005 as SedTypeR005
import no.nav.eessi.pensjon.eux.model.SedType.P2100 as SedTypeP2100
import no.nav.eessi.pensjon.eux.model.SedType.P2200 as SedTypeP2200
import no.nav.eessi.pensjon.eux.model.SedType.P8000 as SedTypeP8000
import no.nav.eessi.pensjon.eux.model.SedType.P9000 as SedTypeP9000
import no.nav.eessi.pensjon.eux.model.SedType.P11000 as SedTypeP11000
import no.nav.eessi.pensjon.eux.model.SedType.P12000 as SedTypeP12000
import no.nav.eessi.pensjon.eux.model.SedType.P15000 as SedTypeP15000
import no.nav.eessi.pensjon.eux.model.SedType.X005 as SedTypeX005
import no.nav.eessi.pensjon.eux.model.SedType.X008 as SedTypeX008
import no.nav.eessi.pensjon.eux.model.SedType.X010 as SedTypeX010
import no.nav.eessi.pensjon.eux.model.sed.KravType.GJENLEV
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
            if (seder.isEmpty())
                throw RuntimeException("Kan ikke hente fødselsdato fra tom SED-liste.")

            val fdato = seder
                    .filter { it.type.kanInneholdeIdentEllerFdato() }
                    .mapNotNull { filterFodselsdato(it) }
                    .firstOrNull()

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
            return seder.any { it.type == SedTypeP15000 && it.nav?.krav?.type == GJENLEV }
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
            return try {
                val fdato = when (sed.type) {
                    SedTypeR005 -> filterPersonR005Fodselsdato(sed as R005)
                    SedTypeP2000, SedTypeP2200, SedTypeH120, SedTypeH121, SedTypeH070 -> filterPersonFodselsdato(sed.nav?.bruker?.person)
                    SedTypeP2100 -> filterGjenlevendeFodselsdato(sed.pensjon?.gjenlevende)
                    SedTypeP5000, SedTypeP6000 -> leggTilGjenlevendeFdatoHvisFinnes(sed.nav?.bruker?.person, sed.pensjon?.gjenlevende)
                    SedTypeP8000, SedTypeP10000 -> leggTilAnnenPersonFdatoHvisFinnes(sed.nav?.annenperson?.person) ?: filterPersonFodselsdato(sed.nav?.bruker?.person)
                    SedTypeP9000 -> filterPersonFodselsdato(sed.nav?.bruker?.person) ?: leggTilAnnenPersonFdatoHvisFinnes(sed.nav?.annenperson?.person)
                    SedTypeP11000, SedTypeP12000 -> leggTilGjenlevendeFdatoHvisFinnes(sed.nav?.bruker?.person, sed.pensjon?.gjenlevende)
                    SedTypeP15000 -> filterP15000(sed as P15000)
                    SedTypeX005, SedTypeX008, SedTypeX010 -> filterPersonFodselsdatoX00Sed(sed)
                    else ->
                        leggTilAnnenPersonFdatoHvisFinnes(sed.nav?.annenperson?.person) ?: filterPersonFodselsdato(sed.nav?.bruker?.person)
                }

                fdato?.let { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }.also {
                    if (it != null) logger.info("Fant fødselsdato i ${sed.type}, fdato: $it")
                }
            } catch (ex: Exception) {
                logger.error("Noe gikk galt ved henting av fødselsdato fra SED", ex)
                null
            }
        }
        private fun leggTilGjenlevendeFdatoHvisFinnes(person: Person?, gjenlevende: Bruker?): String? {
            return if (gjenlevende != null) filterGjenlevendeFodselsdato(gjenlevende)
            else filterPersonFodselsdato(person)
        }

        private fun filterP15000(sed: P15000): String? {
            return if (sed.nav?.krav?.type == GJENLEV) filterGjenlevendeFodselsdato(sed.pensjon?.gjenlevende)
            else filterPersonFodselsdato(sed.nav?.bruker?.person)
        }


        /**
         * R005 har mulighet for flere personer.
         * har sed kun en person returneres dette fdato
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
        private fun leggTilAnnenPersonFdatoHvisFinnes(annenPerson: Person?): String? {
            logger.info("Annen persons rolle er: ${annenPerson?.rolle}")
            if (annenPerson?.rolle != Rolle.ETTERLATTE.kode) return null
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
