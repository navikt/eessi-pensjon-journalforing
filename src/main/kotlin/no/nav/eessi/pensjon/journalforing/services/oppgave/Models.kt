package no.nav.eessi.pensjon.journalforing.services.oppgave

data class Oppgave(
        var id: Long? = null,
        var tildeltEnhetsnr: String? = null,
        var endretAvEnhetsnr: String? = null,
        var opprettetAvEnhetsnr: String? = null,
        var journalpostId: String? = null,
        var journalpostkilde: String? = null,
        var behandlesAvApplikasjon: String? = null,
        var saksreferanse: String? = null,
        var bnr: String? = null,
        var samhandlernr: String? = null,
        var aktoerId: String? = null,
        var orgnr: String? = null,
        var tilordnetRessurs: String? = null,
        var beskrivelse: String? = null,
        var temagruppe: String? = null,
        var tema: String? = null,
        var behandlingstema: String? = null,
        var oppgavetype: String? = null,
        var behandlingstype: String? = null,
        var prioritet: String? = null,
        var versjon: String? = null,
        var mappeId: String? = null,
        var fristFerdigstillelse: String? = null,
        var aktivDato: String? = null,
        var opprettetTidspunkt: String? = null,
        var opprettetAv: String? = null,
        var endretAv: String? = null,
        var ferdigstiltTidspunkt: String? = null,
        var endretTidspunkt: String? = null,
        var status: String? = null,
        var metadata: Map<String, String>? = null
) {

    enum class OppgaveType : Code {
        GENERELL {
            override fun toString() = "GEN"
            override fun decode() = "Generell oppgave"
        },
        JOURNALFORING {
            override fun toString() = "JFR"
            override fun decode() = "Journalføringsoppgave"
        },
        BEHANDLE_SED {
            override fun toString() = "BEH_SED"
            override fun decode() = "Behandle SED"
        }
    }

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

    enum class Behandlingstema : Code {
        UTLAND {
            override fun toString() = "ab0313"
            override fun decode() = "Utland"
        },
        UFORE_UTLAND {
            override fun toString() = "ab0039"
            override fun decode() = "Uføreytelser fra utlandet"
        }
    }

    enum class Temagruppe : Code {
        PENSJON {
            override fun toString() = "PENS"
            override fun decode() = "Pensjon"
        },
        UFORETRYDG {
            override fun toString() = "UFRT"
            override fun decode() = "Uføretrydg"
        }
    }

    enum class Behandlingstype : Code {
        MOTTA_SOKNAD_UTLAND {
            override fun toString() = "ae0110"
            override fun decode() = "Motta søknad utland"
        },
        UTLAND {
            override fun toString() = "ae0106"
            override fun decode() = "Utland"
        }
    }

    enum class Prioritet {
        HOY,
        NORM,
        LAV
    }

    interface Code {
        fun decode(): String
    }
}
