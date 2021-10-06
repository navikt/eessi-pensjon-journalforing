package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.annotation.JsonValue

enum class Rolle(@JsonValue val kode: String) {
    ETTERLATTE("01"),
    FORSORGER("02"),
    BARN("03");
}