package no.nav.eessi.pensjon.models

enum class SedType: Code {

    // Pensjon
    P1000 {
        override fun decode() = "P1000"
        override fun toString() = "P1000 - Anmodning om perioder med omsorg for barn"
    },
    P1100 {
        override fun decode() = "P1100"
        override fun toString() = "P1100 - Svar på anmodning om perioder med omsorg for barn"
    },
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
    P3000_AT  {
        override fun decode() = "P3000_AT"
        override fun toString() = "P3000_AT - Landsspesifikk informasjon - Østerrike"
    },
    P3000_BE  {
        override fun decode() = "P3000_BE"
        override fun toString() = "P3000_BE - Landsspesifikk informasjon - Belgia"
    },
    P3000_BG  {
        override fun decode() = "P3000_BG"
        override fun toString() = "P3000_BG - Landsspesifikk informasjon - Bulgaria"
    },
    P3000_CH  {
        override fun decode() = "P3000_CH"
        override fun toString() = "P3000_CH - Landsspesifikk informasjon - Sveits"
    },
    P3000_CY  {
        override fun decode() = "P3000_CY"
        override fun toString() = "P3000_CY - Landsspesifikk informasjon - Kypros"
    },
    P3000_CZ  {
        override fun decode() = "P3000_CZ"
        override fun toString() = "P3000_CZ - Landsspesifikk informasjon - Republikken Tsjekkia"
    },
    P3000_DE  {
        override fun decode() = "P3000_DE"
        override fun toString() = "P3000_DE - Landsspesifikk informasjon - Tyskland"
    },
    P3000_DK  {
        override fun decode() = "P3000_DK"
        override fun toString() = "P3000_DK - Landsspesifikk informasjon - Danmark"
    },
    P3000_EE  {
        override fun decode() = "P3000_EE"
        override fun toString() = "P3000_EE - Landsspesifikk informasjon - Estland"
    },
    P3000_EL  {
        override fun decode() = "P3000_EL"
        override fun toString() = "P3000_EL - Landsspesifikk informasjon - Hellas"
    },
    P3000_ES  {
        override fun decode() = "P3000_ES"
        override fun toString() = "P3000_ES - Landsspesifikk informasjon - Spania"
    },
    P3000_FI  {
        override fun decode() = "P3000_FI"
        override fun toString() = "P3000_FI - Landsspesifikk informasjon - Finland"
    },
    P3000_FR  {
        override fun decode() = "P3000_FR"
        override fun toString() = "P3000_FR - Landsspesifikk informasjon - Frankrike"
    },
    P3000_HR  {
        override fun decode() = "P3000_HR"
        override fun toString() = "P3000_HR - Landsspesifikk informasjon - Kroatia"
    },
    P3000_HU  {
        override fun decode() = "P3000_HU"
        override fun toString() = "P3000_HU - Landsspesifikk informasjon - Ungarn"
    },
    P3000_IE  {
        override fun decode() = "P3000_IE"
        override fun toString() = "P3000_IE - Landsspesifikk informasjon - Irland"
    },
    P3000_IS  {
        override fun decode() = "P3000_IS"
        override fun toString() = "P3000_IS - Landsspesifikk informasjon - Island "
    },
    P3000_IT  {
        override fun decode() = "P3000_IT"
        override fun toString() = "P3000_IT - Landsspesifikk informasjon - Italia"
    },
    P3000_LI  {
        override fun decode() = "P3000_LI"
        override fun toString() = "P3000_LI - Landsspesifikk informasjon - Liechtenstein"
    },
    P3000_LT  {
        override fun decode() = "P3000_LT"
        override fun toString() = "P3000_LT - Landsspesifikk informasjon - Litauen"
    },
    P3000_LU  {
        override fun decode() = "P3000_LU"
        override fun toString() = "P3000_LU - Landsspesifikk informasjon - Luxembourg"
    },
    P3000_LV  {
        override fun decode() = "P3000_LV"
        override fun toString() = "P3000_LV - Landsspesifikk informasjon - Latvia"
    },
    P3000_MT  {
        override fun decode() = "P3000_MT"
        override fun toString() = "P3000_MT - Landsspesifikk informasjon - Malta"
    },
    P3000_NL  {
        override fun decode() = "P3000_NL"
        override fun toString() = "P3000_NL - Landsspesifikk informasjon - Nederland"
    },
    P3000_NO  {
        override fun decode() = "P3000_NO"
        override fun toString() = "P3000_NO - Landsspesifikk informasjon - Norge"
    },
    P3000_PL  {
        override fun decode() = "P3000_PL"
        override fun toString() = "P3000_PL - Landsspesifikk informasjon - Polen"
    },
    P3000_PT  {
        override fun decode() = "P3000_PT"
        override fun toString() = "P3000_PT - Landsspesifikk informasjon - Portugal"
    },
    P3000_RO  {
        override fun decode() = "P3000_RO"
        override fun toString() = "P3000_RO - Landsspesifikk informasjon - Romania"
    },
    P3000_SE  {
        override fun decode() = "P3000_SE"
        override fun toString() = "P3000_SE - Landsspesifikk informasjon - Sverige"
    },
    P3000_SI  {
        override fun decode() = "P3000_SI"
        override fun toString() = "P3000_SI - Landsspesifikk informasjon - Slovenia"
    },
    P3000_SK  {
        override fun decode() = "P3000_SK"
        override fun toString() = "P3000_SK - Landsspesifikk informasjon - Slovakia"
    },
    P3000_UK  {
        override fun decode() = "P3000_UK"
        override fun toString() = "P3000_UK - Landsspesifikk informasjon - Storbritannia"
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
    P11000 {
        override fun decode() = "P11000"
        override fun toString() = "P11000 - Anmodning om pensjonsbeløp"
    },
    P12000 {
        override fun decode() = "P12000"
        override fun toString() = "P12000 - Informasjon om pensjonsbeløp"
    },
    P13000 {
        override fun decode() = "P13000"
        override fun toString() = "P13000 - Informasjon om pensjonstillegg"
    },
    P14000 {
        override fun decode() = "P14000"
        override fun toString() = "P14000 - Endring i personlige forhold"
    },
    P15000 {
        override fun decode() = "P15000"
        override fun toString() = "P15000 - Overføring av pensjonssaker til EESSI"
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
