package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.Saktype

class Pbuc10 : BucTilEnhetHandler {

    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return when {
            request.harAdressebeskyttelse -> {
                adresseBeskyttelseLogging(request.sedType, request.bucType, Enhet.DISKRESJONSKODE)
                Enhet.DISKRESJONSKODE
            }
            erSakUgyldig(request) -> {
                logger.info("Router ${request.hendelseType} ${request.sedType} i ${request.bucType} til ${Enhet.ID_OG_FORDELING} på grunn av komplisert sak og person-identifisering")
                Enhet.ID_OG_FORDELING
            }
            kanAutomatiskJournalfores(request) -> {
                automatiskJournalforingLogging(request.sedType, request.bucType, Enhet.AUTOMATISK_JOURNALFORING)
                Enhet.AUTOMATISK_JOURNALFORING
            }
            else -> enhetFraLand(request)
        }
    }

    private fun enhetFraLand(request: OppgaveRoutingRequest): Enhet {
        return when {
            request.bosatt == Bosatt.NORGE -> routeNorge(request)
            request.saktype == Saktype.UFOREP -> {
                logger.info("Router ${request.hendelseType} ${request.sedType} i ${request.bucType} til ${Enhet.UFORE_UTLAND} på grunn av bosatt utland og sak er uføre")
                Enhet.UFORE_UTLAND
            }
            else -> {
                logger.info("Router ${request.hendelseType} ${request.sedType} i ${request.bucType} til ${Enhet.PENSJON_UTLAND} på grunn av bosatt utland og sak er ikke uføre")
                Enhet.PENSJON_UTLAND
            }
        }
    }

    private fun routeNorge(request: OppgaveRoutingRequest): Enhet {
        if (erMottattAlderEllerGjenlev(request)){
            logger.info("Router ${request.hendelseType} ${request.sedType} i ${request.bucType} til ${Enhet.NFP_UTLAND_AALESUND} på grunn av bosatt Norge, alder eller gjenlevende-sak")
            return Enhet.NFP_UTLAND_AALESUND
        }

        return when (request.saktype) {
            Saktype.UFOREP -> {
                logger.info("Router ${request.hendelseType} ${request.sedType} i ${request.bucType} til ${Enhet.UFORE_UTLANDSTILSNITT} på grunn av, bosatt Norge, uføre-sak")
                Enhet.UFORE_UTLANDSTILSNITT
            }
            else -> {
                logger.info("Router ${request.hendelseType} ${request.sedType} i ${request.bucType} til ${Enhet.ID_OG_FORDELING} på grunn av sak er hverken alder, gjenlevende eller uføre")
                Enhet.ID_OG_FORDELING
            }
        }
    }

    private fun erSakUgyldig(request: OppgaveRoutingRequest): Boolean {
        if (request.hendelseType ==  HendelseType.SENDT) {
            return request.run {
                identifisertPerson?.personRelasjon?.saktype == Saktype.GJENLEV
                        && saktype == Saktype.UFOREP
                        && sakInformasjon?.sakStatus == SakStatus.AVSLUTTET
                        && sakInformasjon.sakType == Saktype.UFOREP
            }
        }
        return false
    }

    private fun erMottattAlderEllerGjenlev(request: OppgaveRoutingRequest): Boolean {
        return request.run {
            hendelseType == HendelseType.MOTTATT
                    && (saktype == Saktype.ALDER || request.saktype == Saktype.GJENLEV)
        }
    }
}
