package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson

/**
 * R_BUC_02: Motregning av overskytende utbetaling i etterbetalinger
 */
class Rbuc02 : BucTilEnhetHandler {
    override fun hentEnhet(request: OppgaveRoutingRequest): Enhet {
        return when {
            request.harAdressebeskyttelse -> {
                adresseBeskyttelseLogging(request.sedType, request.bucType, Enhet.DISKRESJONSKODE)
                Enhet.DISKRESJONSKODE
            }
            erPersonUgyldig(request.identifisertPerson) -> {
                logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.ID_OG_FORDELING.enhetsNr} på grunn av ingen treff på person")
                Enhet.ID_OG_FORDELING
            }
            request.sedType == SedType.R004 -> {
                logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.OKONOMI_PENSJON.enhetsNr} på grunn av SED er R004")
                Enhet.OKONOMI_PENSJON
            }
            kanAutomatiskJournalfores(request) -> {
                automatiskJournalforingLogging(request.sedType, request.bucType, Enhet.AUTOMATISK_JOURNALFORING)
                Enhet.AUTOMATISK_JOURNALFORING
            }
            else -> hentEnhetForYtelse(request)
        }
    }

    private fun hentEnhetForYtelse(request: OppgaveRoutingRequest): Enhet {
        return if (request.hendelseType == HendelseType.SENDT) {
            logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.ID_OG_FORDELING.enhetsNr} på grunn av traff ingen særregler og SED er sendt")
            Enhet.ID_OG_FORDELING
        } else {
            when (request.saktype) {
                Saktype.ALDER -> {
                    logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.PENSJON_UTLAND.enhetsNr} på grunn av traff ingen særregler og SED er mottatt med sakstype: alder")
                    Enhet.PENSJON_UTLAND
                }
                Saktype.UFOREP -> {
                    logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.UFORE_UTLAND.enhetsNr} på grunn av traff ingen særregler og SED er mottatt med sakstype: uføre")
                    Enhet.UFORE_UTLAND
                }
                else -> {
                    logger.info("Router ${request.sedType} i ${request.bucType} til ${Enhet.ID_OG_FORDELING.enhetsNr} på grunn av traff ingen særregler og SED er mottatt men sakstype er hverken alder eller uføre")
                    Enhet.ID_OG_FORDELING
                }
            }
        }
    }

    private fun erPersonUgyldig(person: IdentifisertPerson?): Boolean =
            person == null || person.aktoerId.isBlank() || person.flereEnnEnPerson()
}
