package no.nav.eessi.pensjon.journalforing

import java.time.LocalDate


class JournalforingPerson(val fnr : String?,
                          val aktoerId: String?,
                          val fdato: LocalDate?,
                          val personNavn: String?,
                          val diskresjonskode: String?,
                          val landkode: String?,
                          val geografiskTilknytning: String?)