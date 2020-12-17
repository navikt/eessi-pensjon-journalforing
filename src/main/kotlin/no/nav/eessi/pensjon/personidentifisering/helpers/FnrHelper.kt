package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.personidentifisering.PersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FnrHelper {

    private val logger = LoggerFactory.getLogger(FnrHelper::class.java)

    private val sedMedForsikretPrioritet = listOf(SedType.H121, SedType.H120, SedType.H070)

    /**
     * leter etter et gyldig fnr i alle seder henter opp person i PersonV3
     * ved R_BUC_02 leter etter alle personer i Seder og lever liste
     */
    fun getPotensielleFnrFraSeder(seder: List<SED>): List<PersonRelasjon> {
        val fnrListe = mutableSetOf<PersonRelasjon>()

        seder.forEach { sed ->
            try {
                if (sed.type.kanInneholdeFnrEllerFdato) {
                    logger.info("SED: ${sed.type}")

                    when (sed.type) {
                        SedType.P2100 -> leggTilGjenlevendeFnrHvisFinnes(sed, fnrListe)
                        SedType.P15000 -> {
                            val krav = sed.nav?.krav?.type
                            val ytelseType = ytelseTypefraKravSed(krav)
                            logger.info("${sed.type.name}, krav: $krav,  ytelsetype: $ytelseType")
                            if (krav == "02") {
                                logger.debug("legger til gjenlevende: ($ytelseType)")
                                leggTilGjenlevendeFnrHvisFinnes(sed, fnrListe, ytelseType)
                            } else {
                                logger.debug("legger til forsikret: ($ytelseType)")
                                leggTilForsikretFnrHvisFinnes(sed, fnrListe, ytelseType)
                            }
                        }
                        SedType.R005 -> {
                            fnrListe.addAll(filterPinPersonR005(sed))
                        }
                        SedType.P5000, SedType.P6000 -> {
                            // Prøver å hente ut gjenlevende på andre seder enn P2100
                            leggTilGjenlevendeFnrHvisFinnes(sed, fnrListe)
                        }
                        SedType.P8000 -> {
                            leggTilAnnenGjenlevendeOgForsikretHvisFinnes(sed, fnrListe)
                        }
                        else -> {
                            // P10000, P9000
                            leggTilAnnenGjenlevendeFnrHvisFinnes(sed, fnrListe)
                            //P2000 - P2200 -- andre..  (H070)
                            leggTilForsikretFnrHvisFinnes(sed, fnrListe)
                        }
                    }
                }
            } catch (ex: Exception) {
                logger.warn("Noe gikk galt under innlesing av fnr fra sed", ex)
            }
        }

        logger.debug("===> ${fnrListe.toJson()} <===")

        val resultat = fnrListe
                .filter { it.erGyldig() || it.sedType in sedMedForsikretPrioritet }
                .distinctBy { it.fnr }

        return if (resultat.isEmpty()) fnrListe.distinctBy { it.fnr }
        else resultat
    }

    /**
     * P8000-P10000 - [01] Søker til etterlattepensjon
     * P8000-P10000 - [02] Forsørget/familiemedlem
     * P8000-P10000 - [03] Barn
     */
    private fun leggTilAnnenGjenlevendeFnrHvisFinnes(sed: SED, fnrListe: MutableSet<PersonRelasjon>) {
        val gjenlevende = sed.nav?.annenperson?.takeIf { it.person?.rolle == "01" }

        Fodselsnummer.fra(gjenlevende?.ident())?.let {
            fnrListe.add(PersonRelasjon(it, Relasjon.GJENLEVENDE, sedType = sed.type))
        }
    }

    private fun leggTilForsikretFnrHvisFinnes(sed: SED, fnrListe: MutableSet<PersonRelasjon>, ytelseType: YtelseType? = null) {
        Fodselsnummer.fra(sed.nav?.forsikretIdent())?.let {
            fnrListe.add(PersonRelasjon(it, Relasjon.FORSIKRET, ytelseType, sed.type))
        }
    }

    private fun ytelseTypefraKravSed(krav: String?): YtelseType? {
        return when (krav) {
            "01" -> YtelseType.ALDER
            "02" -> YtelseType.GJENLEV
            "03" -> YtelseType.UFOREP
            else -> null
        }
    }

    private fun leggTilGjenlevendeFnrHvisFinnes(sed: SED, fnrListe: MutableSet<PersonRelasjon>, ytelseType: YtelseType? = null) {
        Fodselsnummer.fra(sed.nav?.forsikretIdent())
                ?.let {
                    fnrListe.add(PersonRelasjon(it, Relasjon.FORSIKRET, ytelseType, sed.type))
                    logger.debug("Legger til avdød person ${Relasjon.FORSIKRET}")
                }

        val gjenlevendePerson = sed.pensjon?.gjenlevende?.person

        gjenlevendePerson?.let { person ->
            val gjenlevendePin = Fodselsnummer.fra(person.ident()) ?: return

            val gjenlevendeRelasjon = person.relasjontilavdod?.relasjon
            if (gjenlevendeRelasjon == null) {
                fnrListe.add(PersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, sedType = sed.type))
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med ukjente relasjoner")
                return
            }

            val gyldigeBarn = listOf("06", "07", "08", "09")
            if (gyldigeBarn.contains(gjenlevendeRelasjon)) {
                fnrListe.add(PersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, YtelseType.BARNEP, sedType = sed.type))
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med barnerelasjoner")
            } else {
                fnrListe.add(PersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, YtelseType.GJENLEV, sedType = sed.type))
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med gjenlevende relasjoner")
            }
        }
    }

    /**
     * P8000 - [01] Søker til etterlattepensjon
     * P8000 - [02] Forsørget/familiemedlem
     * P8000 - [03] Barn
     */
    private fun leggTilAnnenGjenlevendeOgForsikretHvisFinnes(sed: SED, fnrListe: MutableSet<PersonRelasjon>) {
        logger.debug("Leter i P8000")

        val personPin = Fodselsnummer.fra(sed.nav?.forsikretIdent())
        val annenPersonPin = Fodselsnummer.fra(sed.nav?.annenPersonIdent())
        val rolle = sed.nav?.annenPerson()?.rolle
        logger.debug("Personpin: $personPin AnnenPersonpin $annenPersonPin  Annenperson rolle : $rolle")

        //hvis to personer ingen rolle return uten pin..
        if (personPin != null && annenPersonPin != null && rolle == null) return

        personPin?.let {
            fnrListe.add(PersonRelasjon(it, Relasjon.FORSIKRET, sedType = sed.type))
            logger.debug("Legger til person ${Relasjon.FORSIKRET} relasjon")
        }

        val annenPersonRelasjon = when (rolle) {
            "01" -> PersonRelasjon(annenPersonPin, Relasjon.GJENLEVENDE, sedType = sed.type)
            "02" -> PersonRelasjon(annenPersonPin, Relasjon.FORSORGER, sedType = sed.type)
            "03" -> PersonRelasjon(annenPersonPin, Relasjon.BARN, sedType = sed.type)
            else -> PersonRelasjon(annenPersonPin, Relasjon.ANNET, sedType = sed.type)
        }
        fnrListe.add(annenPersonRelasjon)
        logger.debug("Legger til person med relasjon: ${annenPersonRelasjon.relasjon}")
    }

    /**
     * R005 har mulighet for flere personer.
     * har sed kun en person retureres dette fnr/dnr
     * har sed flere personer leter vi etter status 07/avdød_mottaker_av_ytelser og returnerer dette fnr/dnr
     *
     * Hvis ingen intreffer returnerer vi tom liste
     */
    private fun filterPinPersonR005(sed: SED): List<PersonRelasjon> {
        return sed.nav?.bruker
                ?.mapNotNull { bruker ->
                    val relasjon = getType(bruker.tilbakekreving?.status?.type)

                    Fodselsnummer.fra(bruker.person?.ident())
                            ?.let { PersonRelasjon(it, relasjon, sedType = sed.type) }
                } ?: emptyList()
    }

    //Kun for R_BUC_02
    private fun getType(type: String?): Relasjon {
        return when (type) {
            "enke_eller_enkemann" -> Relasjon.GJENLEVENDE
            "forsikret_person" -> Relasjon.FORSIKRET
            "avdød_mottaker_av_ytelser" -> Relasjon.AVDOD
            else -> Relasjon.ANNET
        }
    }

}
