package no.nav.eessi.pensjon.journalforing.services.oppgave

import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelseModel.BucType

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
        PENSJON_UTLAND("0001"),
        UFORE_UTLANDSTILSNITT("4476"),
        UFORE_UTLAND("4475"),
        NFP_UTLAND_AALESUND("4862"),
        UKJENT("0001"),
        ID_OG_FORDELING("4303")
    }

    enum class Krets {
        NFP,
        NAY
    }
}
