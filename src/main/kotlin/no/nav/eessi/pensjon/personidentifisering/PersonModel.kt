package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.helpers.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.SokKriterier

data class IdentifisertPerson(
    val aktoerId: String,
    val personNavn: String?,
    val harAdressebeskyttelse: Boolean = false,
    val landkode: String?,
    val geografiskTilknytning: String?,
    val personRelasjon: PersonRelasjon,
    val fodselsdato: String? = null,
    var personListe: List<IdentifisertPerson>? = null
) {
    override fun toString(): String {
        return "IdentifisertPerson(aktoerId='$aktoerId', personNavn=$personNavn, harAdressebeskyttelse=$harAdressebeskyttelse, landkode=$landkode, geografiskTilknytning=$geografiskTilknytning, personRelasjon=$personRelasjon)"
    }

    fun flereEnnEnPerson() = personListe != null && personListe!!.size > 1
}

data class PersonRelasjon(
    val fnr: Fodselsnummer?,
    val relasjon: Relasjon,
    val saktype: Saktype? = null,
    val sedType: SedType? = null,
    val sokKriterier: SokKriterier? = null
) {
    fun erGyldig(): Boolean = sedType != null && (saktype != null || relasjon == Relasjon.GJENLEVENDE)
}

enum class Relasjon {
    FORSIKRET,
    GJENLEVENDE,
    AVDOD,
    ANNET,
    BARN,
    FORSORGER
}
