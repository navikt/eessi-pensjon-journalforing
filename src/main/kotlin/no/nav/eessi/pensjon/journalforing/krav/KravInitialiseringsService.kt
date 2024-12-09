package no.nav.eessi.pensjon.journalforing.krav

import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.sed.P2000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class KravInitialiseringsService (private val kravInitialiseringsHandler: KravInitialiseringsHandler) {

    val logger = LoggerFactory.getLogger(KravInitialiseringsService::class.java)

    fun initKrav(
        sedHendelse: SedHendelse,
        sakInformasjon: SakInformasjon?,
        sed: SED?
    ) {

        when(sedHendelse.sedType) {
            P2000 -> {
                if(sed == null){
                    logger.error("P2000 med rinaSakId: ${sedHendelse.rinaSakId} mangler SED informasjon, avslutter sending av krav")
                }
                else if ((sed as P2000).validerForKravinit()) {
                    val hendelse = BehandleHendelseModel(
                        sakId = sakInformasjon?.sakId,
                        bucId = sedHendelse.rinaSakId,
                        hendelsesKode = HendelseKode.SOKNAD_OM_ALDERSPENSJON,
                        beskrivelse = "Det er mottatt søknad om alderspensjon. Kravet er opprettet automatisk"
                    )
                    kravInitialiseringsHandler.putKravInitMeldingPaaKafka(hendelse)
                }  else {
                    logger.warn("P2000 mangler sivilstand, sivilstand.dato eller statsborgerskap")
                }

            }
            P2200 -> {
                val hendelse = BehandleHendelseModel(
                    sakId = sakInformasjon?.sakId,
                    bucId = sedHendelse.rinaSakId,
                    hendelsesKode = HendelseKode.SOKNAD_OM_UFORE,
                    beskrivelse = "Det er mottatt søknad om uføretrygd. Kravet er opprettet."
                )
                kravInitialiseringsHandler.putKravInitMeldingPaaKafka(hendelse)
            }
            else ->  logger.warn("Ikke støttet sedtype for initiering av krav")
        }
    }

    companion object{
        fun P2000.validerForKravinit(): Boolean {
            val logger = LoggerFactory.getLogger(KravInitialiseringsService::class.java)
            return nav?.bruker?.person?.let { person ->
                val isStatsborgerskapValid = person.statsborgerskap != null
                val isSivilstandValid = person.sivilstand?.none { it.fradato == null } == true

                logger.warn("Sjekker statsborgerskap: ${person.statsborgerskap}, sivilstand: ${person.sivilstand}")

                isStatsborgerskapValid && isSivilstandValid
            } ?: false
        }
    }
}


