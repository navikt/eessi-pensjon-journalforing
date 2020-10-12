package no.nav.eessi.pensjon.oppgaverouting

import java.time.LocalDate
import java.time.Period

fun isBetween18and60(fodselsDato: LocalDate): Boolean {
    val alder = Period.between(fodselsDato, LocalDate.now())
    return (alder.years >= 18) && (alder.years < 60)
}

open class RoutingHelper