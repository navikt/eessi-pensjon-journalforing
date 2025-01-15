package no.nav.eessi.pensjon.journalforing

import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.models.Behandlingstema.*
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.models.Tema.*
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class HentTemaService(
    private val journalpostService: JournalpostService,
    private val gcpStorageService: GcpStorageService
) {
    private val logger = LoggerFactory.getLogger(HentTemaService::class.java)

    /**
     * Tema er PENSJON såfremt det ikke er en
     * - uføre buc (P_BUC_03)
     * - saktype er UFØRETRYGD
     * - gjenny tema; EYBARNEP eller EYOMSTILLING
     */

    fun hentTema(
        sedhendelse: SedHendelse?,
        alder: Int?,
        identifisertePersoner: Int,
        saksInfo: SaksInfoSamlet?,
        currentSed: SED?
    ): Tema {
        val ufoereSak = saksInfo?.saktypeFraSed == UFOREP
        if(alder == null) {
            if(sedhendelse?.bucType == P_BUC_03 || ufoereSak || currentSed is P15000 && currentSed.hasUforePensjonType()) return UFORETRYGD
            return PENSJON
        }
        if (sedhendelse?.rinaSakId != null && gcpStorageService.gjennyFinnes(sedhendelse.rinaSakId)) {
            val blob = gcpStorageService.hentFraGjenny(sedhendelse.rinaSakId)
            return if (blob?.contains("BARNEP") == true) EYBARNEP else OMSTILLING
        }

        //https://confluence.adeo.no/pages/viewpage.action?pageId=603358663
        val erUforeAlderUnder62 = erUforAlderUnder62(alder)
        val enPersonOgUforeAlderUnder62 = identifisertePersoner == 1 && erUforeAlderUnder62
        return when (sedhendelse?.bucType) {

            P_BUC_03 -> UFORETRYGD
            P_BUC_06 -> temaPbuc06(currentSed, enPersonOgUforeAlderUnder62, saksInfo)
            P_BUC_10 -> temaPbuc10(currentSed, enPersonOgUforeAlderUnder62, saksInfo)
            P_BUC_07, P_BUC_08 -> temaPbuc07Og08(currentSed, enPersonOgUforeAlderUnder62, saksInfo)
            P_BUC_04, P_BUC_05, P_BUC_09 -> if (enPersonOgUforeAlderUnder62 || ufoereSak) UFORETRYGD else PENSJON
            P_BUC_01, P_BUC_02 -> if (identifisertePersoner == 1 && (ufoereSak || enPersonOgUforeAlderUnder62)) UFORETRYGD else PENSJON
            else -> if (ufoereSak && erUforeAlderUnder62) UFORETRYGD else PENSJON
        }.also { logger.info("Henting av tema for ${sedhendelse?.bucType ?: "ukjent bucType"} gir tema: $it, hvor enPersonOgUforeAlderUnder62: $enPersonOgUforeAlderUnder62") }
    }

    private fun temaPbuc10(
        currentSed: SED?,
        enPersonOgUforeAlderUnder62: Boolean,
        saksInfo: SaksInfoSamlet?
    ): Tema {
        val uforeSakTypeEllerUforPerson = saksInfo?.saktypeFraSed == UFOREP || enPersonOgUforeAlderUnder62
        val isUforePensjon = if (currentSed is P15000 && saksInfo?.sakInformasjonFraPesys?.sakStatus == SakStatus.LOPENDE) currentSed.hasUforePensjonType() else false
        return if (isUforePensjon || uforeSakTypeEllerUforPerson) UFORETRYGD else PENSJON
    }

    private fun temaPbuc07Og08(
        currentSed: SED?,
        enPersonOgUforeAlderUnder62: Boolean,
        saksInfo: SaksInfoSamlet?
    ): Tema {
        val isUforeP12000 = (currentSed as? P12000)?.hasUforePensjonType() ?: false
        val isUforeSakType = saksInfo?.saktypeFraSed == UFOREP

        return if (isUforeP12000 || enPersonOgUforeAlderUnder62 || isUforeSakType) UFORETRYGD else PENSJON
    }

    private fun temaPbuc06(
        currentSed: SED?,
        enPersonOgUforeAlderUnder62: Boolean,
        saksInfo: SaksInfoSamlet?
    ): Tema {
        val isUforePensjon = when (currentSed) {
            is P5000 -> currentSed.hasUforePensjonType()
            is P6000 -> currentSed.hasUforePensjonType()
            is P7000 -> currentSed.hasUforePensjonType()
            is P10000 -> currentSed.hasUforePensjonType()
            else -> false
        }
        val uforeSakTypeEllerUforPerson = saksInfo?.saktypeFraSed == UFOREP || enPersonOgUforeAlderUnder62
        return if (isUforePensjon || uforeSakTypeEllerUforPerson) UFORETRYGD else PENSJON
    }

    fun enhetBasertPaaBehandlingstema(
        sedHendelse: SedHendelse?,
        sakinfo: SaksInfoSamlet?,
        identifisertPerson: IdentifisertPerson,
        antallIdentifisertePersoner: Int,
        currentSed: SED?,
        tema: Tema
    ): Enhet {

        val behandlingstema = journalpostService.bestemBehandlingsTema(
            sedHendelse?.bucType!!,
            sakinfo?.saktypeFraSed,
            tema,
            antallIdentifisertePersoner,
            currentSed
        )

        logger.info("${sedHendelse.sedType} gir landkode: ${identifisertPerson.landkode}, behandlingstema: $behandlingstema, tema: $tema")

        return if (identifisertPerson.landkode == "NOR")
            when (behandlingstema) {
                GJENLEVENDEPENSJON, BARNEP, ALDERSPENSJON, TILBAKEBETALING -> Enhet.NFP_UTLAND_AALESUND
                UFOREPENSJON -> Enhet.UFORE_UTLANDSTILSNITT
            } else when (behandlingstema) {
                GJENLEVENDEPENSJON, BARNEP, ALDERSPENSJON, TILBAKEBETALING -> Enhet.PENSJON_UTLAND
                UFOREPENSJON -> Enhet.UFORE_UTLAND
            }
    }

    fun erUforAlderUnder62(alder: Int): Boolean {
        return alder in 18..61
    }
}