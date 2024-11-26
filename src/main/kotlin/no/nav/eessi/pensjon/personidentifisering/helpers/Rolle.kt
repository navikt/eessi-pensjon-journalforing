package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class Rolle(@JsonValue val kode: String) {
    ETTERLATTE("01"),
    FORSORGER("02"),
    BARN("03");

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String?): Rolle? {
            if (value == null) return null
            return Rolle.entries.firstOrNull { it.kode == value }
        }
    }
}