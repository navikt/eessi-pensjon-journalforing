package no.nav.eessi.pensjon.journalforing.journalpost
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson

class VurderAvbrutt {

    val bucsIkkeTilAvbrutt = listOf(R_BUC_02, M_BUC_02, M_BUC_03a, M_BUC_03b)
    val sedsIkkeTilAvbrutt = listOf(X001, X002, X003, X004, X005, X006, X007, X008, X009, X010, X013, X050, H001, H002, H020, H021, H070, H120, H121)

    /**
     * Journalposten skal settes til avbrutt ved manglende fnr, sed er sendt og buc/sed er ikke i listen med unntak
     *
     * @param identifiedPerson identifisert person.
     * @param hendelseType Sendt eller mottatt.
     * @param sedHendelse sed hendelse.
     * @return true om journalpost settes til avbrutt
     */
    fun skalKanselleres(
        identifiedPerson: IdentifisertPerson?,
        hendelseType: HendelseType,
        sedHendelse: SedHendelse
    ): Boolean {
        val statusToBeCanceled = identifiedPerson?.personRelasjon?.fnr == null &&
                    hendelseType == SENDT &&
                    sedHendelse.bucType !in bucsIkkeTilAvbrutt &&
                    sedHendelse.sedType !in sedsIkkeTilAvbrutt

        return statusToBeCanceled
    }
}