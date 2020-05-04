package no.nav.eessi.pensjon.models

enum class SedType(val kanInneholdeFnrEllerFdato: Boolean) {

    // Pensjon
    P1000(false) {
        override fun toString() = "P1000 - Anmodning om perioder med omsorg for barn"
    },
    P1100(false) {
        override fun toString() = "P1100 - Svar på anmodning om perioder med omsorg for barn"
    },
    P2000(true) {
        override fun toString() = "P2000 - Krav om alderspensjon"
    },
    P2100(true) {
        override fun toString() = "P2100 - Krav om gjenlevendepensjon"
    },
    P2200(true)  {
        override fun toString() = "P2200 - Krav om uførepensjon"
    },
    P3000_AT(true)  {
        override fun toString() = "P3000_AT - Landsspesifikk informasjon - Østerrike"
    },
    P3000_BE(true)  {
        override fun toString() = "P3000_BE - Landsspesifikk informasjon - Belgia"
    },
    P3000_BG(true)  {
        override fun toString() = "P3000_BG - Landsspesifikk informasjon - Bulgaria"
    },
    P3000_CH(true)  {
        override fun toString() = "P3000_CH - Landsspesifikk informasjon - Sveits"
    },
    P3000_CY(true)  {
        override fun toString() = "P3000_CY - Landsspesifikk informasjon - Kypros"
    },
    P3000_CZ(true)  {
        override fun toString() = "P3000_CZ - Landsspesifikk informasjon - Republikken Tsjekkia"
    },
    P3000_DE(true)  {
        override fun toString() = "P3000_DE - Landsspesifikk informasjon - Tyskland"
    },
    P3000_DK(true)  {
        override fun toString() = "P3000_DK - Landsspesifikk informasjon - Danmark"
    },
    P3000_EE(true)  {
        override fun toString() = "P3000_EE - Landsspesifikk informasjon - Estland"
    },
    P3000_EL(true)  {
        override fun toString() = "P3000_EL - Landsspesifikk informasjon - Hellas"
    },
    P3000_ES(true)  {
        override fun toString() = "P3000_ES - Landsspesifikk informasjon - Spania"
    },
    P3000_FI(true)  {
        override fun toString() = "P3000_FI - Landsspesifikk informasjon - Finland"
    },
    P3000_FR(true)  {
        override fun toString() = "P3000_FR - Landsspesifikk informasjon - Frankrike"
    },
    P3000_HR(true)  {
        override fun toString() = "P3000_HR - Landsspesifikk informasjon - Kroatia"
    },
    P3000_HU(true)  {
        override fun toString() = "P3000_HU - Landsspesifikk informasjon - Ungarn"
    },
    P3000_IE(true)  {
        override fun toString() = "P3000_IE - Landsspesifikk informasjon - Irland"
    },
    P3000_IS(true)  {
        override fun toString() = "P3000_IS - Landsspesifikk informasjon - Island "
    },
    P3000_IT(true)  {
        override fun toString() = "P3000_IT - Landsspesifikk informasjon - Italia"
    },
    P3000_LI(true)  {
        override fun toString() = "P3000_LI - Landsspesifikk informasjon - Liechtenstein"
    },
    P3000_LT(true)  {
        override fun toString() = "P3000_LT - Landsspesifikk informasjon - Litauen"
    },
    P3000_LU(true)  {
        override fun toString() = "P3000_LU - Landsspesifikk informasjon - Luxembourg"
    },
    P3000_LV(true)  {
        override fun toString() = "P3000_LV - Landsspesifikk informasjon - Latvia"
    },
    P3000_MT(true)  {
        override fun toString() = "P3000_MT - Landsspesifikk informasjon - Malta"
    },
    P3000_NL(true)  {
        override fun toString() = "P3000_NL - Landsspesifikk informasjon - Nederland"
    },
    P3000_NO(true)  {
        override fun toString() = "P3000_NO - Landsspesifikk informasjon - Norge"
    },
    P3000_PL(true)  {
        override fun toString() = "P3000_PL - Landsspesifikk informasjon - Polen"
    },
    P3000_PT(true)  {
        override fun toString() = "P3000_PT - Landsspesifikk informasjon - Portugal"
    },
    P3000_RO(true)  {
        override fun toString() = "P3000_RO - Landsspesifikk informasjon - Romania"
    },
    P3000_SE(true)  {
        override fun toString() = "P3000_SE - Landsspesifikk informasjon - Sverige"
    },
    P3000_SI(true)  {
        override fun toString() = "P3000_SI - Landsspesifikk informasjon - Slovenia"
    },
    P3000_SK(true)  {
        override fun toString() = "P3000_SK - Landsspesifikk informasjon - Slovakia"
    },
    P3000_UK(true)  {
        override fun toString() = "P3000_UK - Landsspesifikk informasjon - Storbritannia"
    },
    P4000(true) {
        override fun toString() = "P4000 - Brukers oversikt botid og arbeid"
    },
    P5000(true) {
        override fun toString() = "P5000 - Oversikt TT"
    },
    P6000(true) {
        override fun toString() = "P6000 - Melding om vedtak"
    },
    P7000(true) {
        override fun toString() = "P7000 - Samlet melding om vedtak"
    },
    P8000(true) {
        override fun toString() = "P8000 - Forespørsel om informasjon"
    },
    P9000(true) {
        override fun toString() = "P9000 - Svar på forespørsel om informasjon"
    },
    P10000(true) {
        override fun toString() = "P10000 - Oversendelse av informasjon"
    },
    P11000(false) {
        override fun toString() = "P11000 - Anmodning om pensjonsbeløp"
    },
    P12000(false) {
        override fun toString() = "P12000 - Informasjon om pensjonsbeløp"
    },
    P13000(false) {
        override fun toString() = "P13000 - Informasjon om pensjonstillegg"
    },
    P14000(true) {
        override fun toString() = "P14000 - Endring i personlige forhold"
    },
    P15000(true) {
        override fun toString() = "P15000 - Overføring av pensjonssaker til EESSI"
    },
    // Administrative
    X001(false) {
        override fun toString() = "X001 - Anmodning om avslutning"
    },
    X002(false) {
        override fun toString() = "X002 - Anmodning om gjenåpning av avsluttet sak"
    },
    X003(false) {
        override fun toString() = "X003 - Svar på anmodning om gjenåpning av avsluttet sak"
    },
    X004(false) {
        override fun toString() = "X004 - Gjenåpne saken"
    },
    X005(false) {
        override fun toString() = "X005 - Legg til ny institusjon"
    },
    X006(false) {
        override fun toString() = "X006 - Fjern institusjon"
    },
    X007(false) {
        override fun toString() = "X007 - Videresend sak"
    },
    X008(false) {
        override fun toString() = "X008 - Ugyldiggjøre SED"
    },
    X009(false) {
        override fun toString() = "X009 - Påminnelse"
    },
    X010(false) {
        override fun toString() = "X010 - Svar på påminnelse"
    },
    X011(false) {
        override fun toString() = "X011 - Avvis SED"
    },
    X012(false) {
        override fun toString() = "X012 - Klargjør innhold"
    },
    X013(false) {
        override fun toString() = "X013 - Svar på anmodning om klargjøring"
    },
    X050(false) {
        override fun toString() = "X050 - Unntaksfeil"
    },
    X100(false) {
        override fun toString() = "X100 - Endre deltaker"
    },

    // Horisontale
    H001(false) {
        override fun toString() = "H001 - Melding/anmodning om informasjon"
    },
    H002(false) {
        override fun toString() = "H002 - Svar på anmodning om informasjon"
    },
    H020(true) {
        override fun toString() = "H020 - Krav om - refusjon - administrativ kontroll / medisinsk informasjon"
    },
    H021(true) {
        override fun toString() = "H021 - Svar på krav om refusjon - administrativ kontroll / legeundersøkelse / medisinsk informasjon"
    },
    H070(true) {
        override fun toString() = "H070 - Melding om dødsfall"
    },
    H120(true) {
        override fun toString() = "H120 - Anmodning om medisinsk informasjon"
    },
    H121(true) {
        override fun toString() = "H121 - Melding om medisinsk informasjon / Svar på forespørsel om medisinsk informasjon"
    },

    // Seder i R_BUC_02 Motregning av overskytende utbetaling i etterbetalinger er
    R004(true) {
        override fun toString() = "R004 - Melding om utbetaling"
    },
    R005(true) {
        override fun toString() = "R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig)"
    },
    R006(true) {
        override fun toString() = "R006 - Svar på anmodning om informasjon"
    }
}
