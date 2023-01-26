package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_10
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.klienter.norg2.NorgKlientRequest
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.SakInformasjon
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OppgaveRoutingService(private val norg2Service: Norg2Service) {

    private val logger = LoggerFactory.getLogger(OppgaveRoutingService::class.java)

    fun route(routingRequest: OppgaveRoutingRequest): Enhet {
        if (routingRequest.aktorId == null) {
            logger.info("AktørID mangler. Bruker enhet ID_OG_FORDELING.")
            return Enhet.ID_OG_FORDELING
        }

        val tildeltEnhet = tildelEnhet(routingRequest)

        logger.info(
            "Router oppgave til $tildeltEnhet (${tildeltEnhet.enhetsNr}), " +
                    "Buc: ${routingRequest.bucType}, " +
                    "Landkode: ${routingRequest.landkode}, " +
                    "Fødselsdato: ${routingRequest.fdato}, " +
                    "Geografisk Tilknytning: ${routingRequest.geografiskTilknytning}, " +
                    "saktype: ${routingRequest.saktype}"
        )

        return tildeltEnhet
    }

    private fun tildelEnhet(oppgave: OppgaveRoutingRequest): Enhet {
        val enhet = finnEnhetFor(oppgave)

        if (enhet == Enhet.AUTOMATISK_JOURNALFORING)
            return enhet

        logger.debug("enhet: $enhet")

        if (oppgave.bucType == P_BUC_01) {
            val norgKlientRequest = NorgKlientRequest(
                oppgave.harAdressebeskyttelse,
                oppgave.landkode,
                oppgave.geografiskTilknytning,
                oppgave.saktype
            )
            return norg2Service.hentArbeidsfordelingEnhet(norgKlientRequest) ?: enhet
        }

        if (erGydligBuc10eller02(oppgave.bucType, oppgave.landkode, oppgave.sakInformasjon)) {
            logger.debug("Benytter norg2 for buctype: ${oppgave.bucType}")
            val personRelasjon = oppgave.identifisertPerson?.personRelasjon
            val norgKlientRequest = NorgKlientRequest(
                oppgave.harAdressebeskyttelse,
                oppgave.landkode,
                oppgave.geografiskTilknytning,
                oppgave.saktype,
                personRelasjon
            )
            return norg2Service.hentArbeidsfordelingEnhet(norgKlientRequest) ?: enhet
        }
        return enhet
    }

    private fun finnEnhetFor(oppgave: OppgaveRoutingRequest) =
        EnhetFactory.hentHandlerFor(bucType = oppgave.bucType).finnEnhet(oppgave)

    fun erGydligBuc10eller02(bucType: BucType, landkode: String?, sakInformasjon: SakInformasjon?): Boolean {
        if (bucType == P_BUC_10) return true
        return bucType == P_BUC_02 && landkode == "NOR" && sakInformasjon?.sakType == ALDER && sakInformasjon.sakStatus == LOPENDE
    }
}
