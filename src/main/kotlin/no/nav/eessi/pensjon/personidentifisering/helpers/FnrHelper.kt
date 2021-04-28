package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.models.sed.kanInneholdeIdentEllerFdato
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
                if (sed.type.kanInneholdeIdentEllerFdato()) {
                    logger.info("SED: ${sed.type}")

                    when (sed.type) {
                        SedType.P2100 -> leggTilGjenlevendeFnrHvisFinnes(sed, fnrListe)
                        SedType.P15000 -> {
                            val krav = sed.nav?.krav?.type
                            val saktype = if (krav == null) null else mapKravtypeTilSaktype(KravType.valueOf(krav))
                            logger.info("${sed.type.name}, krav: $krav,  saktype: $saktype")
                            if (krav == KravType.ETTERLATTE.name) {
                                logger.debug("legger til gjenlevende: ($saktype)")
                                leggTilGjenlevendeFnrHvisFinnes(sed, fnrListe, saktype)
                            } else {
                                logger.debug("legger til forsikret: ($saktype)")
                                leggTilForsikretFnrHvisFinnes(sed, fnrListe, saktype)
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
        val gjenlevende = sed.nav?.annenperson?.takeIf { it.person?.rolle == Rolle.ETTERLATTE.name }

        Fodselsnummer.fra(gjenlevende?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)?.let {
            fnrListe.add(PersonRelasjon(it, Relasjon.GJENLEVENDE, sedType = sed.type))
        }
    }

    private fun leggTilForsikretFnrHvisFinnes(sed: SED, fnrListe: MutableSet<PersonRelasjon>, saktype: Saktype? = null) {
        Fodselsnummer.fra(sed.nav?.bruker?.firstOrNull()?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)?.let {
            fnrListe.add(PersonRelasjon(it, Relasjon.FORSIKRET, saktype, sed.type))
        }
    }

    private fun mapKravtypeTilSaktype(krav: KravType?): Saktype? {
        return when (krav) {
            KravType.ALDER -> Saktype.ALDER
            KravType.ETTERLATTE -> Saktype.GJENLEV
            KravType.UFORE -> Saktype.UFOREP
            else -> null
        }
    }

    private fun leggTilGjenlevendeFnrHvisFinnes(sed: SED, fnrListe: MutableSet<PersonRelasjon>, saktype: Saktype? = null) {
        Fodselsnummer.fra(sed.nav?.bruker?.firstOrNull()?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
                ?.let {
                    fnrListe.add(PersonRelasjon(it, Relasjon.FORSIKRET, saktype, sed.type))
                    logger.debug("Legger til avdød person ${Relasjon.FORSIKRET}")
                }

        val gjenlevendePerson = sed.pensjon?.gjenlevende?.person

        gjenlevendePerson?.let { person ->
            val gjenlevendePin = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator) ?: return

            val gjenlevendeRelasjon = person.relasjontilavdod?.relasjon
            if (gjenlevendeRelasjon == null) {
                fnrListe.add(PersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, sedType = sed.type))
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med ukjente relasjoner")
                return
            }

            if (erGjenlevendeBarn(gjenlevendeRelasjon)) {
                fnrListe.add(PersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, Saktype.BARNEP, sedType = sed.type))
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med barnerelasjoner")
            } else {
                fnrListe.add(PersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, Saktype.GJENLEV, sedType = sed.type))
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

        val personPin = Fodselsnummer.fra(sed.nav?.bruker?.firstOrNull()?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
        val annenPersonPin = Fodselsnummer.fra(sed.nav?.annenperson?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
        val rolle = sed.nav?.annenperson?.person?.rolle
        logger.debug("Personpin: $personPin AnnenPersonpin $annenPersonPin  Annenperson rolle : $rolle")

        //hvis to personer ingen rolle return uten pin..
        if (personPin != null && annenPersonPin != null && rolle == null) return

        personPin?.let {
            fnrListe.add(PersonRelasjon(it, Relasjon.FORSIKRET, sedType = sed.type))
            logger.debug("Legger til person ${Relasjon.FORSIKRET} relasjon")
        }

        val annenPersonRelasjon = when (rolle) {
            Rolle.ETTERLATTE.name -> PersonRelasjon(annenPersonPin, Relasjon.GJENLEVENDE, sedType = sed.type)
            Rolle.FORSORGER.name -> PersonRelasjon(annenPersonPin, Relasjon.FORSORGER, sedType = sed.type)
            Rolle.BARN.name -> PersonRelasjon(annenPersonPin, Relasjon.BARN, sedType = sed.type)
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
                    val relasjon = mapRelasjon(bruker.tilbakekreving?.status?.type)

                    Fodselsnummer.fra(bruker.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
                            ?.let { PersonRelasjon(it, relasjon, sedType = sed.type) }
                } ?: emptyList()
    }

    //Kun for R_BUC_02
    private fun mapRelasjon(type: String?): Relasjon {
        return when (type) {
            "enke_eller_enkemann" -> Relasjon.GJENLEVENDE
            "forsikret_person" -> Relasjon.FORSIKRET
            "avdød_mottaker_av_ytelser" -> Relasjon.AVDOD
            else -> Relasjon.ANNET
        }
    }

   /* *//**
     * Forenklet uthenting forsikret (hovedperson) sin identifikator (fnr)
     *//*
    fun forsikretIdent(bruker: Bruker): String? =
        bruker.firstOrNull()?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator
*/
    fun erGjenlevendeBarn(relasjon: String): Boolean {
        return (relasjon == "EGET_BARN" ||
                relasjon =="ADOPTIVBARN" ||
                relasjon == "FOSTERBARN" ||
                relasjon =="STEBARN" )
    }

}

enum class Rolle(@JsonValue val kode: String) {
    ETTERLATTE("01"),
    FORSORGER("02"),
    BARN("03");
}

@Suppress("unused") // val kode (jsonvalue) brukes av jackson
enum class KravType(@JsonValue private val kode: String?) {
    ALDER("01"),
    ETTERLATTE("02"),
    UFORE("03")
}
