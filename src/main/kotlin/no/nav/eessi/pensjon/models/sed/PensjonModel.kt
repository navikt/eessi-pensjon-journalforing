package no.nav.eessi.pensjon.models.sed

data class Pensjon(
        val gjenlevende: Bruker? = null,
        val bruker: Bruker? = null,

        //p2000 - p2200
        val ytelser: List<YtelserItem>? = null,

        //P2000, P2100, P2200, P8000?? Noen men ikke alle
        val vedlegg: List<String>? = null,

        //P7000
        val samletVedtak: SamletMeldingVedtak? = null,

        //P10000 //P9000
        val merinformasjon: Merinformasjon? = null
)

//P10000 -- innholder ytteligere informasjon om person (se kp. 5.1) som skal oversendes tilbake
data class Merinformasjon(
        val person: Person? = null,
        val bruker: Bruker? = null,
        val ytelser: List<YtelserItem>? = null
)

//P7000
data class SamletMeldingVedtak(
        val avslag: List<PensjonAvslagItem>? = null,
        val vedtaksammendrag: String? = null,
        val startdatoPensjonsRettighet: String? = null,  // 4.1.5
        val reduksjonsGrunn: String? = null    // 4.1.7
)

//P7000-5
data class PensjonAvslagItem(
        val pensjonType: String? = null,
        val pin: PinItem? = null
)

//Institusjon
data class Institusjon(
        val institusjonsid: String? = null,
        val institusjonsnavn: String? = null,
        val saksnummer: String? = null,
        val sektor: String? = null,
        val land: String? = null,
        val pin: String? = null,
        val personNr: String? = null,
        val innvilgetPensjon: String? = null,  // 4.1.3.
        val utstedelsesDato: String? = null,  //4.1.4.
        val startdatoPensjonsRettighet: String? = null  //4.1.5
)

//P2000 - P2200 - //P10000
data class YtelserItem(
        val annenytelse: String? = null,
        val pin: PinItem? = null,
        val ytelse: String? = null,
        val status: String? = null,

        val ytelsestype: String? = null
)
