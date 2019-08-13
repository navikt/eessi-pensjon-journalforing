package no.nav.eessi.pensjon.models

// https://confluence.adeo.no/display/BOA/Behandlingstema
enum class Behandlingstema : Code {
    GJENLEVENDEPENSJON {
        override fun toString() = "ab0011"
        override fun decode() = "Gjenlevendepensjon"
    },
    ALDERSPENSJON {
        override fun toString() = "ab0254"
        override fun decode() = "Alderspensjon"
    },
    UFOREPENSJON {
        override fun toString() = "ab0194"
        override fun decode() = "Uførepensjon"
    }
}

// https://confluence.adeo.no/display/BOA/Tema
enum class Tema : Code {
    PENSJON {
        override fun toString() = "PEN"
        override fun decode() = "Pensjon"
    },
    UFORETRYGD {
        override fun toString() = "UFO"
        override fun decode() = "Uføretrygd"
    }
}

enum class BucType (val BEHANDLINGSTEMA: String, val TEMA: String){
    P_BUC_01(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_02(Behandlingstema.GJENLEVENDEPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_03(Behandlingstema.UFOREPENSJON.toString(), Tema.UFORETRYGD.toString()),
    P_BUC_04(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_05(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_06(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_07(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_08(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_09(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_10(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString())
}
