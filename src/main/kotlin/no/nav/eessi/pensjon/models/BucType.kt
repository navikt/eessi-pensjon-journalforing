package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonValue

// https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Tema
enum class Behandlingstema(@JsonValue val kode: String) {
    GJENLEVENDEPENSJON("ab0011"),
    ALDERSPENSJON("ab0254"),
    UFOREPENSJON("ab0194"),
    TILBAKEBETALING("ab0007")
}

// https://confluence.adeo.no/display/BOA/Tema
enum class Tema(@JsonValue val kode: String) {
    PENSJON("PEN"),
    UFORETRYGD("UFO")
}

// https://confluence.adeo.no/display/EP/Oversikt+BUC+og+SED
enum class BucType(val behandlingstema: Behandlingstema, val tema: Tema) {
    P_BUC_01(Behandlingstema.ALDERSPENSJON, Tema.PENSJON),
    P_BUC_02(Behandlingstema.GJENLEVENDEPENSJON, Tema.PENSJON),
    P_BUC_03(Behandlingstema.UFOREPENSJON, Tema.UFORETRYGD),
    P_BUC_04(Behandlingstema.ALDERSPENSJON, Tema.PENSJON),
    P_BUC_05(Behandlingstema.ALDERSPENSJON, Tema.PENSJON),
    P_BUC_06(Behandlingstema.ALDERSPENSJON, Tema.PENSJON),
    P_BUC_07(Behandlingstema.ALDERSPENSJON, Tema.PENSJON),
    P_BUC_08(Behandlingstema.ALDERSPENSJON, Tema.PENSJON),
    P_BUC_09(Behandlingstema.ALDERSPENSJON, Tema.PENSJON),
    P_BUC_10(Behandlingstema.ALDERSPENSJON, Tema.PENSJON),
    H_BUC_07(Behandlingstema.ALDERSPENSJON, Tema.PENSJON),
    R_BUC_02(Behandlingstema.TILBAKEBETALING, Tema.PENSJON)
}
