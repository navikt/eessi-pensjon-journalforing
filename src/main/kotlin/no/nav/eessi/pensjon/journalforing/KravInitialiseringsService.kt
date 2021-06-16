package no.nav.eessi.pensjon.journalforing

import no.nav.eessi.pensjon.eux.model.sed.P2000
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.handler.BehandleHendelseModel
import no.nav.eessi.pensjon.handler.HendelseKode
import no.nav.eessi.pensjon.handler.KravInitialiseringsHandler
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class KravInitialiseringsService (private val kravInitialiseringsHandler: KravInitialiseringsHandler) {


    @Value("\${namespace}")
    lateinit var nameSpace: String

    private val logger = LoggerFactory.getLogger(KravInitialiseringsService::class.java)

    fun initKrav(
        sedHendelseModel: SedHendelseModel,
        sakInformasjon: SakInformasjon?,
        pbuc01mottatt: Boolean,
        pbuc03mottatt: Boolean
    ) {

        if (pbuc01mottatt) {
            if (sedHendelseModel.sedType == SedType.P2000  && (nameSpace == "q2" || nameSpace == "test")) {
                val hendelse = BehandleHendelseModel(
                    sakId = sakInformasjon?.sakId,
                    bucId = sedHendelseModel.rinaSakId,
                    hendelsesKode = HendelseKode.SOKNAD_OM_ALDERSPENSJON,
                    beskrivelse = "Det er mottatt søknad om alderspensjon. Kravet er opprettet automatisk"
                )
                kravInitialiseringsHandler.putKravInitMeldingPaaKafka(hendelse)
            } else logger.warn("Ikke støttet sedtype for initiering av krav")
        }

        if (pbuc03mottatt) {
            if (sedHendelseModel.sedType == SedType.P2200) {
                val hendelse = BehandleHendelseModel(
                    sakId = sakInformasjon?.sakId,
                    bucId = sedHendelseModel.rinaSakId,
                    hendelsesKode = HendelseKode.SOKNAD_OM_UFORE,
                    beskrivelse = "Det er mottatt søknad om uføretrygd. Kravet er opprettet."
                )
                kravInitialiseringsHandler.putKravInitMeldingPaaKafka(hendelse)
            } else logger.warn("Ikke støttet sedtype for initiering av krav")
        }
    }
}
fun P2000.validerForKravinit() = (nav?.bruker?.person?.sivilstand != null
        && nav?.bruker?.person?.statsborgerskap != null
        && nav?.bruker?.person?.sivilstand?.filter { it.fradato == null }.isNullOrEmpty())
