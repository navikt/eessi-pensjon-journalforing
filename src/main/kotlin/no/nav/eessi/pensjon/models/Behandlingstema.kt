package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonValue

// https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Tema
enum class Behandlingstema(@JsonValue val kode: String) {
    GJENLEVENDEPENSJON("ab0011"),
    ALDERSPENSJON("ab0254"),
    UFOREPENSJON("ab0194"),
    BARNEP("ab0255"),
    TILBAKEBETALING("ab0007");
}