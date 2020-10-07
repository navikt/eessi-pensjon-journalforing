package no.nav.eessi.pensjon.models

// https://confluence.adeo.no/display/BOA/Behandlingstema
enum class Behandlingstema : Code {
    GJENLEVENDEPENSJON {
        override fun toString() = "ab0011"
        override fun decode() = "Gjenlevendepensjon" //TODO: må finne ut om det er gjenlevende eller barnepensjon
    },
    ALDERSPENSJON {
        override fun toString() = "ab0254"
        override fun decode() = "Alderspensjon"
    },
    UFOREPENSJON {
        override fun toString() = "ab0194"
        override fun decode() = "Uførepensjon"
    },
    TILBAKEBETALING {
        override fun toString() = "ab0007"
        override fun decode() = "Tilbakebetaling" //TODO: Er dette riktig?
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

enum class BucType (val BEHANDLINGSTEMA: String, val TEMA: String, val buc : Buc){
    P_BUC_01(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString(), Pbuc01()),
    P_BUC_02(Behandlingstema.GJENLEVENDEPENSJON.toString(), Tema.PENSJON.toString(), Pbuc02()),
    P_BUC_03(Behandlingstema.UFOREPENSJON.toString(), Tema.UFORETRYGD.toString(), Pbuc03()),
    P_BUC_04(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString(), Pbuc04()),
    P_BUC_05(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString(), Pbuc05()),
    P_BUC_06(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString(), Pbuc06()),
    P_BUC_07(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString(), Pbuc07()),
    P_BUC_08(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString(), Pbuc08()),
    P_BUC_09(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString(), Pbuc09()),
    P_BUC_10(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString(), Pbuc10()),
    H_BUC_07(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString(), Hbuc07()),
    R_BUC_02(Behandlingstema.TILBAKEBETALING.toString(), Tema.PENSJON.toString(), Rbuc02())

}

interface Code {
    fun decode(): String
}