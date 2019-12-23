package no.nav.eessi.pensjon.models

import java.time.LocalDate


data class JournalforingPerson(val fnr : String? = null,
                          val aktoerId: String? = null,
                          val fdato: LocalDate,
                          val personNavn: String? = null,
                          val diskresjonskode: String? = null,
                          val landkode: String? = null,
                          val geografiskTilknytning: String? = null)