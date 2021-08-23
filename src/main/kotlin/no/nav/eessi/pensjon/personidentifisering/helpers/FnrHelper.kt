package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.P10000
import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.P2000
import no.nav.eessi.pensjon.eux.model.sed.P2200
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.P8000
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.models.BucType
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
    fun getPotensielleFnrFraSeder(seder: List<Pair<String, SED>>, bucType: BucType): List<SEDPersonRelasjon> {
        val fnrListe = mutableSetOf<SEDPersonRelasjon>()

        seder.forEach { (_,sed) ->
            try {
                if (sed.type.kanInneholdeIdentEllerFdato()) {
                    logger.info("SED: ${sed.type}, class: ${sed.javaClass.simpleName}")

                    when (sed) {
                        is R005   -> behandleR005(sed, fnrListe)
                        is P15000 -> behandleP15000(sed, fnrListe)
                        is P6000  -> leggTilGjenlevendeFnrHvisFinnes(sed.nav?.bruker, sed.p6000Pensjon?.gjenlevende, sed.type, fnrListe = fnrListe)
                        is P5000  -> leggTilGjenlevendeFnrHvisFinnes(sed.nav?.bruker, sed.p5000Pensjon?.gjenlevende, sed.type, fnrListe = fnrListe)
                        is P8000  -> behandleP8000AndP10000(sed.nav, sed.type, fnrListe,bucType)
                        is P10000 -> behandleP8000AndP10000(sed.nav, sed.type, fnrListe, bucType)
                        is P2000, is P2200 -> leggTilForsikretFnrHvisFinnes(sed, fnrListe)          // P2000, P2200, P5000, og H070 ? flere?
                        else -> {
                            logger.debug("Else sedType: ${sed.type}")
                            when (sed.type) {
                                SedType.P2100 -> leggTilGjenlevendeFnrHvisFinnes(sed.nav?.bruker, sed.pensjon?.gjenlevende, sed.type, fnrListe = fnrListe)
                                in sedMedForsikretPrioritet ->  leggTilForsikretFnrHvisFinnes(sed, fnrListe)          // P2000, P2200, P5000, og H070 ? flere?
                                else -> {
                                    leggTilAnnenGjenlevendeFnrHvisFinnes(sed, fnrListe)   // P9000
                                    leggTilForsikretFnrHvisFinnes(sed, fnrListe)          // flere?
                                }
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                logger.warn("Noe gikk galt under innlesing av fnr fra sed", ex)
            }
        }
        val resultat = fnrListe
                .filter { it.erGyldig() || it.sedType in sedMedForsikretPrioritet }
                .filterNot { it.filterUbrukeligeElemeterAvSedPersonRelasjon() }
                .distinctBy { it.fnr }

        return resultat.ifEmpty { fnrListe.distinctBy { it.fnr } }
    }

    /**
     * P8000-P10000 - [01] Søker til etterlattepensjon
     * P8000-P10000 - [02] Forsørget/familiemedlem
     * P8000-P10000 - [03] Barn
     */
    private fun leggTilAnnenGjenlevendeFnrHvisFinnes(sed: SED, fnrListe: MutableSet<SEDPersonRelasjon>) {
        val gjenlevende = sed.nav?.annenperson?.takeIf { it.person?.rolle == Rolle.ETTERLATTE.name }
        gjenlevende?.let { bruker ->
            val sokPersonKriterie = sokPersonKriterie(bruker)
            val fodselnummer = Fodselsnummer.fra(bruker.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val fdato =  mapFdatoTilLocalDate(bruker.person?.foedselsdato)
            fnrListe.add(SEDPersonRelasjon(fodselnummer, Relasjon.GJENLEVENDE, sedType = sed.type, sokKriterier = sokPersonKriterie, fdato = fdato))
        }
    }

    private fun behandleP15000(sed: P15000, fnrListe: MutableSet<SEDPersonRelasjon>) {
        val sedKravString = sed.nav?.krav?.type

        val saktype = if (sedKravString == null) null else mapKravtypeTilSaktype(sedKravString)

        logger.info("${sed.type.name}, krav: $sedKravString,  saktype: $saktype")

        if (saktype == Saktype.GJENLEV) {
            logger.debug("legger til gjenlevende: ($saktype)")
            leggTilGjenlevendeFnrHvisFinnes(
                sed.nav?.bruker,
                sed.p15000Pensjon?.gjenlevende,
                sed.type,
                fnrListe = fnrListe,
                saktype = saktype)
        } else {
            logger.debug("legger til forsikret: ($saktype)")
            leggTilForsikretFnrHvisFinnes(sed, fnrListe, saktype)
        }
        
    }

    //P2000, P2200..P15000(forsikret)
    private fun leggTilForsikretFnrHvisFinnes(sed: SED, fnrListe: MutableSet<SEDPersonRelasjon>, saktype: Saktype? = null) {
        val forsikretBruker = sed.nav?.bruker
        forsikretBruker?.let {  bruker ->
            val sokPersonKriterie =  sokPersonKriterie(bruker)
            val fodselnummer = Fodselsnummer.fra(bruker.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val fdato = mapFdatoTilLocalDate(bruker.person?.foedselsdato)

            fnrListe.add(SEDPersonRelasjon(fodselnummer, Relasjon.FORSIKRET, saktype, sed.type, sokPersonKriterie, fdato))
            logger.debug("Legger til person ${Relasjon.FORSIKRET}, med $saktype og sedType: ${sed.type}")
        }

    }

    private fun sokPersonKriterie(navBruker: Bruker) : SokKriterier? {
        val person = navBruker.person ?: return null
        val fdatotmp: String = person.foedselsdato ?: return null
        val fornavn: String = person.fornavn ?: return null
        val etternavn: String = person.etternavn ?: return null
        val fodseldato: LocalDate = mapFdatoTilLocalDate(fdatotmp)!!
        val sokKriterier = SokKriterier(
            fornavn,
            etternavn,
            fodseldato
        )
        logger.debug("Oppretter SokKriterier: ${sokKriterier.fornavn}, ${sokKriterier.etternavn}, ${sokKriterier.foedselsdato}")
        return sokKriterier
    }

    private fun mapFdatoTilLocalDate(fdato: String?) : LocalDate? =
        fdato?.let { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }
            .also { logger.debug("Parse fdato fra sed: $fdato, return ${it.toString()}")}

    private fun mapKravtypeTilSaktype(krav: String?): Saktype {
        return when (krav) {
            "02" -> Saktype.GJENLEV
            "03" -> Saktype.UFOREP
            else -> Saktype.ALDER
        }
    }

    private fun leggTilGjenlevendeFnrHvisFinnes(
        forsikretBruker: Bruker? = null,
        gjenlevendeBruker: Bruker? = null,
        sedType: SedType,
        fnrListe: MutableSet<SEDPersonRelasjon>,
        saktype: Saktype? = null
    ) {

        //forsikretPerson (avdød eller søker)
        forsikretBruker?.let {
            val forsikretPersonKriterie = forsikretBruker?.let { sokPersonKriterie(it) }
            val forsikretFnr = Fodselsnummer.fra(forsikretBruker?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val fdato = mapFdatoTilLocalDate(forsikretBruker?.person?.foedselsdato)

            fnrListe.add(SEDPersonRelasjon(forsikretFnr, Relasjon.FORSIKRET, saktype, sedType, fdato = fdato, sokKriterier = forsikretPersonKriterie))
            logger.debug("Legger til forsikret-person ${Relasjon.FORSIKRET}")
        }

        //gjenlevendePerson (søker)
        val gjenlevendePerson = gjenlevendeBruker?.person
        gjenlevendePerson?.let { person ->
            val gjenlevendePin = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val gjenlevendeFdato = mapFdatoTilLocalDate(gjenlevendePerson.foedselsdato)
            val sokPersonKriterie = gjenlevendeBruker.let { sokPersonKriterie(it) }

            val gjenlevendeRelasjon = person.relasjontilavdod?.relasjon
            if (gjenlevendeRelasjon == null) {
                fnrListe.add(SEDPersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, sedType = sedType, sokKriterier = sokPersonKriterie, fdato = gjenlevendeFdato))
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med ukjente relasjoner")
                return
            }

            val sakType =  if (erGjenlevendeBarn(gjenlevendeRelasjon)) {
                Saktype.BARNEP
            } else {
                Saktype.GJENLEV
            }
            fnrListe.add(SEDPersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, sakType, sedType = sedType, sokKriterier = sokPersonKriterie, gjenlevendeFdato))
            logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med sakType: $sakType")
        }

    }

    /**
     * P8000 - [01] Søker til etterlattepensjon
     * P8000 - [02] Forsørget/familiemedlem
     * P8000 - [03] Barn
     * P10000
     */
    private fun behandleP8000AndP10000(
        nav: Nav?,
        sedType: SedType,
        fnrListe: MutableSet<SEDPersonRelasjon>,
        bucType: BucType
    ) {
        logger.debug("Leter i $sedType")

        val forsikretBruker = nav?.bruker
        val personPin = Fodselsnummer.fra(forsikretBruker?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
        val personFdato = mapFdatoTilLocalDate(forsikretBruker?.person?.foedselsdato)

        val annenPerson = nav?.annenperson
        val annenPersonPin = Fodselsnummer.fra(annenPerson?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
        val annenPersonFdato = mapFdatoTilLocalDate(annenPerson?.person?.foedselsdato)

        val rolle = annenPerson?.person?.rolle

        val sokForsikretKriterie = forsikretBruker?.let { sokPersonKriterie(it) }
        val sokAnnenPersonKriterie = annenPerson?.let { sokPersonKriterie(it) }

        logger.debug("Personpin: $personPin, AnnenPersonpin $annenPersonPin, Annenperson rolle : $rolle, sokForsikret: ${sokForsikretKriterie != null}")

        //kap.2 forsikret person
        fnrListe.add(SEDPersonRelasjon(personPin, Relasjon.FORSIKRET, sedType = sedType, sokKriterier = sokForsikretKriterie, fdato = personFdato))
        logger.debug("Legger til person ${Relasjon.FORSIKRET} relasjon")

        //Annenperson søker/barn o.l
        if (bucType == BucType.P_BUC_05 || bucType == BucType.P_BUC_10 || bucType == BucType.P_BUC_02) {
            val annenPersonRelasjon = when (rolle) {
                Rolle.ETTERLATTE.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.GJENLEVENDE, sedType = sedType, sokKriterier = sokAnnenPersonKriterie , fdato = annenPersonFdato)
                Rolle.FORSORGER.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.FORSORGER, sedType = sedType, sokKriterier = sokAnnenPersonKriterie, fdato = annenPersonFdato)
                Rolle.BARN.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.BARN, sedType = sedType, sokKriterier = sokAnnenPersonKriterie, fdato = annenPersonFdato)
                else -> SEDPersonRelasjon(annenPersonPin, Relasjon.ANNET, sedType = sedType, sokKriterier = sokAnnenPersonKriterie, fdato = annenPersonFdato)
            }

            fnrListe.add(annenPersonRelasjon)
            logger.debug("Legger til person med relasjon: ${annenPersonRelasjon.relasjon}, sokForsikret: ${sokAnnenPersonKriterie != null}")
        }
    }

    /**
     * R005 har mulighet for flere personer.
     * har sed kun en person retureres dette fnr/dnr
     * har sed flere personer leter vi etter status 07/avdød_mottaker_av_ytelser og returnerer dette fnr/dnr
     *
     * Hvis ingen intreffer returnerer vi tom liste
     */
    private fun behandleR005(sed: R005, fnrListe: MutableSet<SEDPersonRelasjon>) {
        fnrListe.addAll(filterPinPersonR005(sed))
    }

    private fun filterPinPersonR005(sed: R005): List<SEDPersonRelasjon> {
        return sed.recoveryNav?.brukere
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
