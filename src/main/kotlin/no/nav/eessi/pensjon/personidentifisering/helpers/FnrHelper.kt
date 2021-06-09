package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.models.sed.kanInneholdeIdentEllerFdato
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SokKriterier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class FnrHelper {

    private val logger = LoggerFactory.getLogger(FnrHelper::class.java)

    private val sedMedForsikretPrioritet = listOf(SedType.H121, SedType.H120, SedType.H070)

    /**
     * leter etter et gyldig fnr i alle seder henter opp person i PersonV3
     * ved R_BUC_02 leter etter alle personer i Seder og lever liste
     */
    fun getPotensielleFnrFraSeder(seder: List<Pair<String, SED>>): List<SEDPersonRelasjon> {
        val fnrListe = mutableSetOf<SEDPersonRelasjon>()

        seder.forEach { (_,sed) ->
            try {
                if (sed.type.kanInneholdeIdentEllerFdato()) {
                    logger.info("SED: ${sed.type}")

                    when (sed.type) {
                        SedType.R005 ->  fnrListe.addAll(filterPinPersonR005(sed))
                        SedType.P2100 -> leggTilGjenlevendeFnrHvisFinnes(sed, fnrListe)
                        SedType.P15000 -> behandleP15000(sed, fnrListe)
                        SedType.P5000, SedType.P6000 -> leggTilGjenlevendeFnrHvisFinnes(sed, fnrListe)   // Prøver å hente ut gjenlevende på andre seder enn P2100
                        SedType.P8000 -> behandleP8000(sed, fnrListe)
                        else -> {
                            leggTilAnnenGjenlevendeFnrHvisFinnes(sed, fnrListe)   // P10000, P9000

                            leggTilForsikretFnrHvisFinnes(sed, fnrListe)          // P2000, P2200 og H070 ? flere?
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
    private fun leggTilAnnenGjenlevendeFnrHvisFinnes(sed: SED, fnrListe: MutableSet<SEDPersonRelasjon>) {
        val gjenlevende = sed.nav?.annenperson?.takeIf { it.person?.rolle == Rolle.ETTERLATTE.name }

        val sokPersonKriterie = gjenlevende?.let { sokPersonKriterie(it) }

        val fodselnummer = Fodselsnummer.fra(gjenlevende?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
        val fdato = gjenlevende?.let { mapFdatoTilLocalDate(it.person?.foedselsdato) }

        if (fodselnummer != null) {
            fnrListe.add(SEDPersonRelasjon(fodselnummer, Relasjon.GJENLEVENDE, sedType = sed.type, fdato = fdato))
        }
        if (sokPersonKriterie != null && fodselnummer == null) {
            fnrListe.add(SEDPersonRelasjon(null, Relasjon.GJENLEVENDE, sedType = sed.type, sokKriterier = sokPersonKriterie, fdato = fdato))
        }
    }

    private fun behandleP15000(sed: SED, fnrListe: MutableSet<SEDPersonRelasjon>) {
        val sedKravString = sed.nav?.krav?.type

        val saktype = if (sedKravString == null) null else mapKravtypeTilSaktype(sedKravString)

        logger.info("${sed.type.name}, krav: $sedKravString,  saktype: $saktype")

        if (saktype == Saktype.GJENLEV) {
            logger.debug("legger til gjenlevende: ($saktype)")
            leggTilGjenlevendeFnrHvisFinnes(sed, fnrListe, saktype)
        } else {
            logger.debug("legger til forsikret: ($saktype)")
            leggTilForsikretFnrHvisFinnes(sed, fnrListe, saktype)
        }
        
    }

    //P2000, P2200..P15000(forsikret)
    private fun leggTilForsikretFnrHvisFinnes(sed: SED, fnrListe: MutableSet<SEDPersonRelasjon>, saktype: Saktype? = null) {
        val sokPersonKriterie = sed.nav?.bruker?.let { sokPersonKriterie(it) }
        val fodselnummer = Fodselsnummer.fra(sed.nav?.bruker?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
        val fdato = mapFdatoTilLocalDate(sed.nav?.bruker?.person?.foedselsdato)

        if (fodselnummer != null) {
            fnrListe.add(SEDPersonRelasjon(fodselnummer, Relasjon.FORSIKRET, saktype, sed.type, fdato = fdato))
        }
        if (sokPersonKriterie != null && fodselnummer == null) {
            fnrListe.add(SEDPersonRelasjon(null, Relasjon.FORSIKRET, saktype, sed.type, sokPersonKriterie, fdato))
        }

    }

    private fun sokPersonKriterie(navBruker: Bruker) : SokKriterier? {
        logger.debug("fdato : ${navBruker.person?.foedselsdato}")
        logger.debug("fornavn : ${navBruker.person?.fornavn}")
        logger.debug("etternavn : ${navBruker.person?.etternavn}")

        val person = navBruker.person ?: return null
        val fodseldato =  person.foedselsdato ?: return null
        val fornavn = person.fornavn ?: return null
        val etternavn = person.etternavn ?: return null

        logger.debug("Oppretter SokKriterier")
        return SokKriterier(
            fornavn,
            etternavn,
            LocalDate.parse(fodseldato, DateTimeFormatter.ISO_DATE)
        )
    }

    private fun mapFdatoTilLocalDate(fdato: String?) : LocalDate? {
       return fdato?.let { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }
    }

    private fun mapKravtypeTilSaktype(krav: String?): Saktype? {
        return when (krav) {
            "02" -> Saktype.GJENLEV
            "03" -> Saktype.UFOREP
            else -> Saktype.ALDER
        }
    }

    private fun leggTilGjenlevendeFnrHvisFinnes(sed: SED, fnrListe: MutableSet<SEDPersonRelasjon>, saktype: Saktype? = null) {
        Fodselsnummer.fra(sed.nav?.bruker?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
                ?.let {
                    fnrListe.add(SEDPersonRelasjon(it, Relasjon.FORSIKRET, saktype, sed.type, fdato = mapFdatoTilLocalDate(sed.nav?.bruker?.person?.foedselsdato)))
                    logger.debug("Legger til avdød person ${Relasjon.FORSIKRET}")
                }

        val gjenlevende = sed.pensjon?.gjenlevende
        val gjenlevendePerson = gjenlevende?.person

        gjenlevendePerson?.let { person ->
            val gjenlevendePin = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val gjenlevendeFdato = mapFdatoTilLocalDate(gjenlevendePerson.foedselsdato)

            val gjenlevendeRelasjon = person.relasjontilavdod?.relasjon
            if (gjenlevendeRelasjon == null) {
                fnrListe.add(SEDPersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, sedType = sed.type, fdato = gjenlevendeFdato))
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med ukjente relasjoner")
                return
            }

            val sokPersonKriterie = gjenlevende.let { sokPersonKriterie(it) }
            if (erGjenlevendeBarn(gjenlevendeRelasjon)) {

                if (gjenlevendePin != null) {
                    fnrListe.add(SEDPersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, Saktype.BARNEP, sedType = sed.type, sokKriterier = sokPersonKriterie, gjenlevendeFdato))
                    logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med barnerelasjoner")
                }
                if (sokPersonKriterie != null && gjenlevendePin == null) {
                    fnrListe.add(SEDPersonRelasjon(null, Relasjon.GJENLEVENDE, Saktype.BARNEP, sedType = sed.type, sokKriterier = sokPersonKriterie, gjenlevendeFdato))
                    logger.debug("Legger til sokPersonKriterie ${Relasjon.GJENLEVENDE} med barnerelasjoner")
                }

            } else {

                if (gjenlevendePin != null) {
                    fnrListe.add(SEDPersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, Saktype.GJENLEV, sedType = sed.type, fdato = gjenlevendeFdato))
                    logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med gjenlevende relasjoner")
                }
                if (sokPersonKriterie != null && gjenlevendePin == null) {
                    fnrListe.add(SEDPersonRelasjon(null, Relasjon.GJENLEVENDE, Saktype.GJENLEV, sedType = sed.type, sokKriterier = sokPersonKriterie, gjenlevendeFdato))
                    logger.debug("Legger til sokPersonKriterie ${Relasjon.GJENLEVENDE} med gjenlevende relasjoner")
                }

            }
        }
    }

    /**
     * P8000 - [01] Søker til etterlattepensjon
     * P8000 - [02] Forsørget/familiemedlem
     * P8000 - [03] Barn
     */
    private fun behandleP8000(sed: SED, fnrListe: MutableSet<SEDPersonRelasjon>) {
        logger.debug("Leter i P8000")

        val forsikretBruker = sed.nav?.bruker
        val personPin = Fodselsnummer.fra(forsikretBruker?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
        val personFdato = mapFdatoTilLocalDate(forsikretBruker?.person?.foedselsdato)

        val annenPerson = sed.nav?.annenperson
        val annenPersonPin = Fodselsnummer.fra(annenPerson?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
        val annenPersonFdato = mapFdatoTilLocalDate(annenPerson?.person?.foedselsdato)

        val rolle = annenPerson?.person?.rolle

        val sokForsikretKriterie = forsikretBruker?.let { sokPersonKriterie(it) }
        val sokAnnenPersonKriterie = annenPerson?.let { sokPersonKriterie(it) }

        logger.debug("Personpin: $personPin, AnnenPersonpin $annenPersonPin, Annenperson rolle : $rolle, SokForsikret: ${sokForsikretKriterie != null}")

        personPin?.let {
            fnrListe.add(SEDPersonRelasjon(it, Relasjon.FORSIKRET, sedType = sed.type, fdato = personFdato))
            logger.debug("Legger til person ${Relasjon.FORSIKRET} relasjon")
        }
        if (sokForsikretKriterie != null && personPin == null && annenPersonPin == null) {
            fnrListe.add(SEDPersonRelasjon(null, Relasjon.FORSIKRET, sedType = sed.type, sokKriterier = sokForsikretKriterie, fdato = personFdato))
            logger.debug("Legger til sokPerson kriterie")
        }

        val annenPersonRelasjon = when (rolle) {
            Rolle.ETTERLATTE.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.GJENLEVENDE, sedType = sed.type, fdato = annenPersonFdato)
            Rolle.FORSORGER.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.FORSORGER, sedType = sed.type, fdato = annenPersonFdato)
            Rolle.BARN.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.BARN, sedType = sed.type, fdato = annenPersonFdato)
            else -> SEDPersonRelasjon(annenPersonPin, Relasjon.ANNET, sedType = sed.type, fdato = annenPersonFdato)
        }
        if (annenPersonPin != null) {
            fnrListe.add(annenPersonRelasjon)
            logger.debug("Legger til person med relasjon: ${annenPersonRelasjon.relasjon}")
        }
        if (annenPersonPin == null && sokAnnenPersonKriterie != null) {
            val newRelasjonMedSok = annenPersonRelasjon.copy(fnr = null, sokKriterier = sokAnnenPersonKriterie)
            fnrListe.add(newRelasjonMedSok)
            logger.debug("Legger til sokPersonKriterie ${Relasjon.GJENLEVENDE} med gjenlevende relasjoner")
        }


    }

    /**
     * R005 har mulighet for flere personer.
     * har sed kun en person retureres dette fnr/dnr
     * har sed flere personer leter vi etter status 07/avdød_mottaker_av_ytelser og returnerer dette fnr/dnr
     *
     * Hvis ingen intreffer returnerer vi tom liste
     */
    private fun filterPinPersonR005(sed: SED): List<SEDPersonRelasjon> {
        return sed.nav?.brukere
                ?.mapNotNull { bruker ->
                    val relasjon = mapRelasjon(bruker.tilbakekreving?.status?.type)
                    val fdato = mapFdatoTilLocalDate(bruker.person?.foedselsdato)
                    Fodselsnummer.fra(bruker.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
                            ?.let { SEDPersonRelasjon(it, relasjon, sedType = sed.type, fdato = fdato) }
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
