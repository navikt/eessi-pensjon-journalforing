package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.helpers.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.SokKriterier
import org.slf4j.LoggerFactory
import java.time.LocalDate

data class IdentifisertPerson(
    val aktoerId: String,                               //fra PDL
    val personNavn: String?,                            //fra PDL
    val harAdressebeskyttelse: Boolean = false,         //fra PDL
    val landkode: String?,                              //fra PDL
    val geografiskTilknytning: String?,                 //fra PDL
    val personRelasjon: SEDPersonRelasjon,              //innhenting fra FnrHelper og SED
    val fodselsdato: String? = null,                    // FnrHelper og SED
    var personListe: List<IdentifisertPerson>? = null   //fra PDL
) {
    override fun toString(): String {
        return "IdentifisertPerson(aktoerId='$aktoerId', personNavn=$personNavn, harAdressebeskyttelse=$harAdressebeskyttelse, landkode=$landkode, geografiskTilknytning=$geografiskTilknytning, personRelasjon=$personRelasjon)"
    }
    fun flereEnnEnPerson() = personListe != null && personListe!!.size > 1
}

data class SEDPersonRelasjon(
    val fnr: Fodselsnummer?,
    val relasjon: Relasjon,
    val saktype: Saktype? = null,
    val sedType: SedType? = null,
    val sokKriterier: SokKriterier? = null,
    val fdato: LocalDate? = null
) {
    private val logger = LoggerFactory.getLogger(SEDPersonRelasjon::class.java)

    fun validateFnrOgDato(): Boolean {
        if (fdato == null) return true

        return if (fnr != null) {
            val validertFnr = fnr.getBirthDate() == fdato
            logger.debug("Validert fnr-dato: ${fnr.getBirthDate()} sed-fdato: $fdato")
            validertFnr
        } else {
            true
        }
    }
    fun erGyldig(): Boolean = sedType != null && (saktype != null || relasjon == Relasjon.GJENLEVENDE)
    fun filterUbrukeligeElemeterAvSedPersonRelasjon(): Boolean = fnr == null && fdato == null && sokKriterier == null
}

enum class Relasjon {
    FORSIKRET,
    GJENLEVENDE,
    AVDOD,
    ANNET,
    BARN,
    FORSORGER
}
