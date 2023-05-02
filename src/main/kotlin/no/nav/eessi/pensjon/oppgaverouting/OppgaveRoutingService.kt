package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_10
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.klienter.norg2.NorgKlientRequest
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
            "Tildelt enhet: $tildeltEnhet (${tildeltEnhet.enhetsNr}), " +
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

        return when(oppgave.bucType){
            P_BUC_01 -> routeTilGeografiskTilknytning(oppgave) ?: enhet
            P_BUC_02 -> {
                if (lopendeAldersSakINorge(oppgave)) routeTilGeografiskTilknytningMedPerson(oppgave) ?: enhet
                else enhet
            }
            P_BUC_10 -> routeTilGeografiskTilknytningMedPerson(oppgave) ?: enhet
            else ->  enhet
        }
    }

    private fun lopendeAldersSakINorge( oppgave: OppgaveRoutingRequest) =
        oppgave.landkode == "NOR" && oppgave.sakInformasjon?.sakType == ALDER && oppgave.sakInformasjon!!.sakStatus == LOPENDE

    private fun routeTilGeografiskTilknytningMedPerson(oppgave: OppgaveRoutingRequest): Enhet? {
        logger.debug("Benytter norg2 for buctype: ${oppgave.bucType}")
        val personRelasjon = oppgave.identifisertPerson?.personRelasjon
        val norgKlientRequest = NorgKlientRequest(
            oppgave.harAdressebeskyttelse,
            oppgave.landkode,
            oppgave.geografiskTilknytning,
            oppgave.saktype,
            personRelasjon
        )
        return norg2Service.hentArbeidsfordelingEnhet(norgKlientRequest)
    }

    private fun routeTilGeografiskTilknytning(oppgave: OppgaveRoutingRequest): Enhet? {
        val norgKlientRequest = NorgKlientRequest(
            oppgave.harAdressebeskyttelse,
            oppgave.landkode,
            oppgave.geografiskTilknytning,
            oppgave.saktype
        )
        return norg2Service.hentArbeidsfordelingEnhet(norgKlientRequest)
    }

    private fun finnEnhetFor(oppgave: OppgaveRoutingRequest) =
        EnhetFactory.hentHandlerFor(bucType = oppgave.bucType).finnEnhet(oppgave)

}
