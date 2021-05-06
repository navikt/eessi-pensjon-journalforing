package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.models.sed.kanInneholdeIdentEllerFdato
import no.nav.eessi.pensjon.personidentifisering.PersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.Relasjon
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
    fun getPotensielleFnrFraSeder(seder: List<SED>): List<PersonRelasjon> {
        val fnrListe = mutableSetOf<PersonRelasjon>()

        seder.forEach { sed ->
            try {
                if (sed.type.kanInneholdeIdentEllerFdato()) {
                    logger.info("SED: ${sed.type}")

                    when (sed.type) {
                        SedType.R005 ->  fnrListe.addAll(filterPinPersonR005(sed))
                        SedType.P2100 -> leggTilGjenlevendeFnrHvisFinnes(sed, fnrListe)
                        SedType.P15000 -> behandleP15000(sed, fnrListe)
                        SedType.P5000, SedType.P6000 -> leggTilGjenlevendeFnrHvisFinnes(sed, fnrListe)   // Prøver å hente ut gjenlevende på andre seder enn P2100
                        SedType.P8000 -> leggTilAnnenGjenlevendeOgForsikretHvisFinnes(sed, fnrListe)
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
    private fun leggTilAnnenGjenlevendeFnrHvisFinnes(sed: SED, fnrListe: MutableSet<PersonRelasjon>) {
        val gjenlevende = sed.nav?.annenperson?.takeIf { it.person?.rolle == Rolle.ETTERLATTE.name }

        val sokPersonKriterie = gjenlevende?.let { sokPersonKriterie(it) }

        val fodselnummer = Fodselsnummer.fra(gjenlevende?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)

        if (fodselnummer != null) {
            fnrListe.add(PersonRelasjon(fodselnummer, Relasjon.GJENLEVENDE, sedType = sed.type))
        }
        if (sokPersonKriterie != null && fodselnummer == null) {
            fnrListe.add(PersonRelasjon(null, Relasjon.GJENLEVENDE, sedType = sed.type, sokKriterier = sokPersonKriterie))
        }
    }

    private fun behandleP15000(sed: SED, fnrListe: MutableSet<PersonRelasjon>) {
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
    private fun leggTilForsikretFnrHvisFinnes(sed: SED, fnrListe: MutableSet<PersonRelasjon>, saktype: Saktype? = null) {
        val sokPersonKriterie = sed.nav?.bruker?.let { sokPersonKriterie(it) }
        val fodselnummer = Fodselsnummer.fra(sed.nav?.bruker?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)

        logger.debug("-".repeat(100) +
        """
            
            søkPerson: $sokPersonKriterie
            fodselnr : ${fodselnummer?.value}

        """.trimIndent() +
        "-".repeat(100)
        )

        if (fodselnummer != null) {
            fnrListe.add(PersonRelasjon(fodselnummer, Relasjon.FORSIKRET, saktype, sed.type))
        }
        if (sokPersonKriterie != null && fodselnummer == null) {
            fnrListe.add(PersonRelasjon(null, Relasjon.FORSIKRET, saktype, sed.type, sokPersonKriterie))
        }

    }

    private fun sokPersonKriterie(navBruker: Bruker) : SokKriterier? {
        // fnr -> pdl -> person -> automatisk journalføring ?
        // null -> ikke -> null -> id og fortdel
        // null -> søkperson -> fnr -> pdl -> person -> automatisk jorunalføring ?
        // null -> søkperson -> null/formange -> null -> null -> id og fordelg
        logger.debug("fdato : ${navBruker.person?.foedselsdato}")
        logger.debug("fornavn : ${navBruker.person?.fornavn}")
        logger.debug("etternavn : ${navBruker.person?.etternavn}")

        val person = navBruker.person ?: return null
        val fodseldato =  person.foedselsdato ?: return null
        val fornavn = person.fornavn ?: return null
        val etternavn = person.etternavn ?: return null

        return SokKriterier(
            fornavn,
            etternavn,
            LocalDate.parse(fodseldato, DateTimeFormatter.ISO_DATE)
        )
    }

    private fun mapKravtypeTilSaktype(krav: String?): Saktype? {
        return when (krav) {
            "02" -> Saktype.GJENLEV
            "03" -> Saktype.UFOREP
            else -> Saktype.ALDER
        }
    }

    private fun leggTilGjenlevendeFnrHvisFinnes(sed: SED, fnrListe: MutableSet<PersonRelasjon>, saktype: Saktype? = null) {
        Fodselsnummer.fra(sed.nav?.bruker?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
                ?.let {
                    fnrListe.add(PersonRelasjon(it, Relasjon.FORSIKRET, saktype, sed.type))
                    logger.debug("Legger til avdød person ${Relasjon.FORSIKRET}")
                }

        val gjenlevende = sed.pensjon?.gjenlevende
        val gjenlevendePerson = gjenlevende?.person

        gjenlevendePerson?.let { person ->
            val gjenlevendePin = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)

            val gjenlevendeRelasjon = person.relasjontilavdod?.relasjon
            if (gjenlevendeRelasjon == null) {
                fnrListe.add(PersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, sedType = sed.type))
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med ukjente relasjoner")
                return
            }

            val sokPersonKriterie = gjenlevende.let { sokPersonKriterie(it) }
            if (erGjenlevendeBarn(gjenlevendeRelasjon)) {

                if (gjenlevendePin != null) {
                    fnrListe.add(PersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, Saktype.BARNEP, sedType = sed.type, sokKriterier = sokPersonKriterie))
                    logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med barnerelasjoner")
                }
                if (sokPersonKriterie != null && gjenlevendePin == null) {
                    fnrListe.add(PersonRelasjon(null, Relasjon.GJENLEVENDE, Saktype.BARNEP, sedType = sed.type, sokKriterier = sokPersonKriterie))
                    logger.debug("Legger til sokPersonKriterie ${Relasjon.GJENLEVENDE} med barnerelasjoner")
                }

            } else {

                if (gjenlevendePin != null) {
                    fnrListe.add(PersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, Saktype.GJENLEV, sedType = sed.type))
                    logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med gjenlevende relasjoner")
                }
                if (sokPersonKriterie != null && gjenlevendePin == null) {
                    fnrListe.add(PersonRelasjon(null, Relasjon.GJENLEVENDE, Saktype.GJENLEV, sedType = sed.type, sokKriterier = sokPersonKriterie))
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
    private fun leggTilAnnenGjenlevendeOgForsikretHvisFinnes(sed: SED, fnrListe: MutableSet<PersonRelasjon>) {
        logger.debug("Leter i P8000")

        val personPin = Fodselsnummer.fra(sed.nav?.bruker?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
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
        return sed.nav?.brukere
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
