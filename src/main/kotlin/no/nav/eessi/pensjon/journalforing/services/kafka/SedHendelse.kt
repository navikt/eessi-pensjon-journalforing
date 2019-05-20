package no.nav.eessi.pensjon.journalforing.services.kafka


data class SedHendelse (
        val id: Long? = 0,
        val sedId: String? = null,
        val sektorKode: String? = null,
        val bucType: String? = null,
        val rinaSakId: String,
        val avsenderId: String? = null,
        val avsenderNavn: String? = null,
        val mottakerId: String? = null,
        val mottakerNavn: String? = null,
        val rinaDokumentId: String,
        val rinaDokumentVersjon: String? = null,
        val sedType: SedType?,
        val navBruker: String? = null
) {
    enum class SedType: Code  {

        // Pensjon
        P2000 {
            override fun decode() = "P2000"
            override fun toString() = "P2000 - Krav om alderspensjon"
        },
        P2100 {
            override fun decode() = "P2100"
            override fun toString() = "P2100 - Krav om gjenlevendepensjon"
        },
        P2200  {
            override fun decode() = "P2200"
            override fun toString() = "P2200 - Krav om uførepensjon"
        },
        P3000  {
            override fun decode() = "P3000"
            override fun toString() = "P3000 - Landspesifik info"
        },
        P4000 {
            override fun decode() = "P4000"
            override fun toString() = "P4000 - Brukers oversikt botid og arbeid"
        },
        P5000 {
            override fun decode() = "P5000"
            override fun toString() = "P5000 - Oversikt TT"
        },
        P6000 {
            override fun decode() = "P6000"
            override fun toString() = "P6000 - Melding om vedtak"
        },
        P7000 {
            override fun decode() = "P7000"
            override fun toString() = "P7000 - Samlet melding om vedtak"
        },
        P8000 {
            override fun decode() = "P8000"
            override fun toString() = "P8000 - Forespørsel om informasjon"
        },
        P9000 {
            override fun decode() = "P9000"
            override fun toString() = "P9000 - Svar på forespørsel om informasjon"
        },
        P10000 {
            override fun decode() = "P10000"
            override fun toString() = "P10000 - Oversendelse av informasjon"
        },

        // Administrative
        X001 {
            override fun decode() = "X001"
            override fun toString() = "X001 - Anmodning om avslutning"
        },
        X002 {
            override fun decode() = "X002"
            override fun toString() = "X002 - Anmodning om gjenåpning av avsluttet sak"
        },
        X003 {
            override fun decode() = "X003"
            override fun toString() = "X003 - Svar på anmodning om gjenåpning av avsluttet sak"
        },
        X004 {
            override fun decode() = "X004"
            override fun toString() = "X004 - Gjenåpne saken"
        },
        X005 {
            override fun decode() = "X005"
            override fun toString() = "X005 - Legg til ny institusjon"
        },
        X006 {
            override fun decode() = "X006"
            override fun toString() = "X006 - Fjern institusjon"
        },
        X007 {
            override fun decode() = "X007"
            override fun toString() = "X007 - Videresend sak"
        },
        X008 {
            override fun decode() = "X008"
            override fun toString() = "X008 - Ugyldiggjøre SED"
        },
        X009 {
            override fun decode() = "X009"
            override fun toString() = "X009 - Påminnelse"
        },
        X010 {
            override fun decode() = "X010"
            override fun toString() = "X010 - Svar på påminnelse"
        },
        X011 {
            override fun decode() = "X011"
            override fun toString() = "X011 - Avvis SED"
        },
        X012 {
            override fun decode() = "X012"
            override fun toString() = "X012 - Klargjør innhold"
        },
        X013 {
            override fun decode() = "X013"
            override fun toString() = "X013 - Svar på anmodning om klargjøring"
        },
        X050 {
            override fun decode() = "X050"
            override fun toString() = "X050 - Unntaksfeil"
        },

        // Horisontale
        H001 {
            override fun decode() = "H001"
            override fun toString() = "H001 - Melding/anmodning om informasjon"
        },
        H002 {
            override fun decode() = "H002"
            override fun toString() = "H002 - Svar på anmodning om informasjon"
        },
        H020 {
            override fun decode() = "H020"
            override fun toString() = "H020 - Krav om - refusjon - administrativ kontroll / medisinsk informasjon"
        },
        H021 {
            override fun decode() = "H021"
            override fun toString() = "H021 - Svar på krav om refusjon - administrativ kontroll / legeundersøkelse / medisinsk informasjon"
        },
        H070 {
            override fun decode() = "H070"
            override fun toString() = "H070 - Melding om dødsfall"
        },
        H120 {
            override fun decode() = "H120"
            override fun toString() = "H120 - Anmodning om medisinsk informasjon"
        },
        H121 {
            override fun decode() = "H121"
            override fun toString() = "H121 - Melding om medisinsk informasjon / Svar på forespørsel om medisinsk informasjon"
        }
    }

    interface Code {
        fun decode(): String
    }
}

