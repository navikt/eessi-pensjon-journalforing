package no.nav.eessi.pensjon.journalforing

import no.nav.eessi.pensjon.eux.model.sed.P2000
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.handler.*
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalPostResponse
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class KravInitialiseringsService (private val kravInitialiseringsHandler: KravInitialiseringsHandler,
                                  private val oppgaveHandler: OppgaveHandler) {


    @Value("\${namespace}")
    lateinit var nameSpace: String

    private val logger = LoggerFactory.getLogger(KravInitialiseringsService::class.java)

    fun fixKRav(
        sedHendelseModel: SedHendelseModel,
        hendelseType: HendelseType,
        tildeltEnhet: Enhet,
        journalPostResponse: OpprettJournalPostResponse,
        sakInformasjon: SakInformasjon?,
        oppgaveEnhet: Enhet,
        aktoerId: String?
    ) {
        val bucType = sedHendelseModel.bucType
        val pbuc01mottatt = (bucType == BucType.P_BUC_01)
                && (hendelseType == HendelseType.MOTTATT && tildeltEnhet == Enhet.AUTOMATISK_JOURNALFORING && journalPostResponse.journalpostferdigstilt)

        val pbuc03mottatt = (bucType == BucType.P_BUC_03)
                && (hendelseType == HendelseType.MOTTATT && tildeltEnhet == Enhet.AUTOMATISK_JOURNALFORING && journalPostResponse.journalpostferdigstilt)


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

            opprettBehandleSedOppgave(
                journalPostResponse.journalpostId,
                oppgaveEnhet,
                aktoerId,
                sedHendelseModel
            )
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

            opprettBehandleSedOppgave(
                journalPostResponse.journalpostId,
                oppgaveEnhet,
                aktoerId,
                sedHendelseModel
            )
        }
    }

    private fun opprettBehandleSedOppgave(
        journalpostId: String? = null,
        oppgaveEnhet: Enhet,
        aktoerId: String? = null,
        sedHendelseModel: SedHendelseModel,
        uSupporterteVedlegg: String? = null
    ) {
        val oppgave = OppgaveMelding(
            sedHendelseModel.sedType,
            journalpostId,
            oppgaveEnhet,
            aktoerId,
            sedHendelseModel.rinaSakId,
            HendelseType.MOTTATT,
            uSupporterteVedlegg,
            OppgaveType.BEHANDLE_SED
        )
        oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(oppgave)
    }
}
fun P2000.validerForKravinit() = (nav?.bruker?.person?.sivilstand != null
        && nav?.bruker?.person?.statsborgerskap != null
        && !nav?.bruker?.person?.sivilstand?.filter { it.fradato == null }.isNullOrEmpty())
