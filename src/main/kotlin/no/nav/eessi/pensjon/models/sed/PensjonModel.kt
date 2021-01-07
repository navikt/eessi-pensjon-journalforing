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
data class SamletMeldingVedtak(val avslag: List<PensjonAvslagItem>? = null)

//P7000-5
data class PensjonAvslagItem(
        val pensjonType: String? = null,
        val pin: PinItem? = null
)

//P2000 - P2200 - //P10000
data class YtelserItem(val pin: PinItem? = null)
