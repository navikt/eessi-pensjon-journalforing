package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonValue

// https://confluence.adeo.no/display/BOA/Tema
enum class Tema(@JsonValue val kode: String) {
    PENSJON("PEN"),
    UFORETRYGD("UFO"),
    OMSTILLING("EYO"),
    EYBARNEP("EYB"),
}