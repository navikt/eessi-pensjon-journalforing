package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.BucType

data class OppgaveRoutingModel(
        var bucType: BucType,
        var tildeltEnhet: Enhet
) {

    enum class Bosatt {
        NORGE,
        UTLAND,
        UKJENT
    }

    enum class YtelseType {
        AP,
        GP,
        UT
    }

    enum class Enhet(val enhetsNr : String) {
//        4803 behandler saker der bruker er bosatt i fylke 01-05
//        4862 behandler saker der bruker er bosatt i fylke 06-20
//        0001 behandler saker der bruker er bosatt i utlandet.

        PENSJON_UTLAND("0001"),
        UFORE_UTLANDSTILSNITT("4476"),
        UFORE_UTLAND("4475"),
        NFP_UTLAND_AALESUND("4862"),
        NFP_UTLAND_OSLO("4803"),
        ID_OG_FORDELING("4303")
    }

    enum class Krets {
        NFP,
        NAY
    }
}
