package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.Saktype
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.Period

val logger: Logger = LoggerFactory.getLogger(BucTilEnhetHandler::class.java)

interface BucTilEnhetHandler {
    fun hentEnhet(request: OppgaveRoutingRequest): Enhet

    fun kanAutomatiskJournalfores(request: OppgaveRoutingRequest): Boolean {
        return request.run {
            hendelseType == HendelseType.SENDT
                    && saktype != null
                    && !aktorId.isNullOrBlank()
                    && !sakInformasjon?.sakId.isNullOrBlank()
        }
    }

    fun adresseBeskyttelseLogging(sedType: SedType?, bucType: BucType, enhet: Enhet) {
        logger.info("Router $sedType i $bucType til ${enhet.enhetsNr} på grunn av adressebeskyttelse")
    }

    fun automatiskJournalforingLogging(sedType: SedType?, bucType: BucType, enhet: Enhet) {
        logger.info("Router $sedType i $bucType til ${enhet.enhetsNr} på grunn av automatisk journalføring")
    }

    fun bosattNorgeLogging(sedType: SedType?, bucType: BucType, enhet: Enhet) {
        logger.info("Router $sedType i $bucType til ${enhet.enhetsNr} på grunn av personen er bosatt i norge")
    }

    fun bosattNorgeLogging(sedType: SedType?, bucType: BucType, sakType: Saktype, enhet: Enhet) {
        logger.info("Router $sedType i $bucType til ${enhet.enhetsNr} på grunn av saktype: $sakType og personen er bosatt i norge")
    }

    fun bosattUtlandLogging(sedType: SedType?, bucType: BucType, sakType: Saktype, enhet: Enhet) {
        logger.info("Router $sedType i $bucType til ${enhet.enhetsNr} på grunn av saktype: $sakType og personen er bosatt i utlandet")
    }

    fun ingenSærreglerLogging(sedType: SedType?, bucType: BucType, enhet: Enhet) {
        logger.info("Router $sedType i $bucType til ${enhet.enhetsNr} på grunn av ingen særregler ble inntruffet")
    }


}

class BucTilEnhetHandlerCreator {
    companion object {
        fun getHandler(type: BucType): BucTilEnhetHandler {
            return when (type) {
                P_BUC_01 -> Pbuc01()
                P_BUC_02 -> Pbuc02()
                P_BUC_03 -> Pbuc03()
                P_BUC_04 -> Pbuc04()
                P_BUC_05 -> Pbuc05()
                P_BUC_06,
                P_BUC_07,
                P_BUC_08,
                P_BUC_09 -> DefaultBucTilEnhetHandler()
                P_BUC_10 -> Pbuc10()
                H_BUC_07 -> Hbuc07()
                R_BUC_02 -> Rbuc02()
                else -> DefaultBucTilEnhetHandler()
            }
        }
    }
}


fun LocalDate.ageIsBetween18and60(): Boolean {
    val age = Period.between(this, LocalDate.now())
    return (age.years >= 18) && (age.years < 60)
}

fun LocalDate.ageIsBetween18and62(): Boolean {
    val age = Period.between(this, LocalDate.now())
    return (age.years >= 18) && (age.years < 62)
}
