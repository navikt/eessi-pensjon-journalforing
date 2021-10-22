package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.SokKriterier
import java.time.LocalDate

data class IdentifisertPerson(
    val aktoerId: String,                               //fra PDL
    val personNavn: String?,                            //fra PDL
    val landkode: String?,         //fra PDL
    val geografiskTilknytning: String?,                              //fra PDL
    val personRelasjon: SEDPersonRelasjon,                 //fra PDL
    val fodselsdato: String? = null,              //innhenting fra FnrHelper og SED
    var personListe: List<IdentifisertPerson>? = null   //fra PDL){}
) {
    override fun toString(): String {
        return "IdentifisertPerson(aktoerId='$aktoerId', personNavn=$personNavn, landkode=$landkode, geografiskTilknytning=$geografiskTilknytning, personRelasjon=$personRelasjon)"
    }
    fun flereEnnEnPerson() = personListe != null && personListe!!.size > 1
}

data class SEDPersonRelasjon(
    val fnr: Fodselsnummer?,
    val relasjon: Relasjon,
    val saktype: Saktype? = null,
    val sedType: SedType? = null,
    val sokKriterier: SokKriterier? = null,
    val fdato: LocalDate? = null,
    val rinaDocumentId: String
) {
    fun isFnrDnrSinFdatoLikSedFdato(): Boolean {
        //fdato == null and return true validation allow fnr
        //fdato == null and return false validation fail
        if (fdato == null) return true

        return fnr?.getBirthDate() == fdato
    }


}

enum class Relasjon {
    FORSIKRET,
    GJENLEVENDE,
    AVDOD,
    ANNET,
    BARN,
    FORSORGER
}
