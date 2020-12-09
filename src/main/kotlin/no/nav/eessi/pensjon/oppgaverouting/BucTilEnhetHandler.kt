package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
import java.time.LocalDate
import java.time.Period

interface BucTilEnhetHandler {
    fun hentEnhet(request: OppgaveRoutingRequest): Enhet

    fun kanAutomatiskJournalfores(request: OppgaveRoutingRequest): Boolean {
        return request.run {
            hendelseType == HendelseType.SENDT
                    && ytelseType != null
                    && !aktorId.isNullOrBlank()
                    && !sakInformasjon?.sakId.isNullOrBlank()
        }
    }

    fun erStrengtFortrolig(diskresjonskode: Diskresjonskode?): Boolean = diskresjonskode == Diskresjonskode.SPSF
}

class BucTilEnhetHandlerCreator {
    companion object {
        fun getHandler(type: BucType?): BucTilEnhetHandler {
            return when (type) {
                BucType.P_BUC_01 -> Pbuc01()
                BucType.P_BUC_02 -> Pbuc02()
                BucType.P_BUC_03 -> Pbuc03()
                BucType.P_BUC_04 -> Pbuc04()
                BucType.P_BUC_05 -> Pbuc05()
                BucType.P_BUC_06,
                BucType.P_BUC_07,
                BucType.P_BUC_08,
                BucType.P_BUC_09 -> DefaultBucTilEnhetHandler()
                BucType.P_BUC_10 -> Pbuc10()
                BucType.H_BUC_07 -> Hbuc07()
                BucType.R_BUC_02 -> Rbuc02()
                else -> throw RuntimeException("BucType $type er ukjent/ugyldig")
            }
        }
    }
}


fun LocalDate.ageIsBetween18and60(): Boolean {
    val age = Period.between(this, LocalDate.now())
    return (age.years >= 18) && (age.years < 60)
}
