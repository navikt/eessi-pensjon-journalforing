package no.nav.eessi.pensjon.models

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
