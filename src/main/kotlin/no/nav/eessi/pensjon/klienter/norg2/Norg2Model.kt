package no.nav.eessi.pensjon.klienter.norg2

import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon


data class NorgKlientRequest(val harAdressebeskyttelse: Boolean = false,
                             val landkode: String? = null,
                             val geografiskTilknytning: String? = null,
                             val saktype: Saktype? = null,
                             val SEDPersonRelasjon: SEDPersonRelasjon? = null)

data class Norg2ArbeidsfordelingRequest(
    val tema: String = "PEN",
    val diskresjonskode: String? = "ANY",
    val behandlingstema: String = "ANY",
    val behandlingstype: String = "ANY",
    val geografiskOmraade: String = "ANY",
    val skalTilLokalkontor: Boolean = false,
    val oppgavetype: String = "ANY",
    val temagruppe: String = "ANY"
)

class Norg2ArbeidsfordelingItem(
    val oppgavetype: String? = null,
    val enhetNr: String? = null,
    val behandlingstema: String? = null,
    val temagruppe: String? = null,
    val skalTilLokalkontor: Boolean? = null,
    val behandlingstype: String? = null,
    val geografiskOmraade: String? = null,
    val tema: String? = null,
    val enhetNavn: String? = null,
    val diskresjonskode: String? = null,
    val gyldigFra: String? = null,
    val enhetId: Int? = null,
    val id: Int? = null
)

//https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Behandlingstyper
enum class BehandlingType(val kode: String) {
    BOSATT_NORGE("ae0104"),
    BOSATT_UTLAND("ae0107")
}

enum class Norg2BehandlingsTema( val kode: String) {
    BARNEP("ab0255"),
    GJENLEV("ab0011"),
    ANY("ANY")
}