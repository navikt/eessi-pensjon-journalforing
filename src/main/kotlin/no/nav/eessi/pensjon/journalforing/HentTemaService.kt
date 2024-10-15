package no.nav.eessi.pensjon.journalforing

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.journalforing.etterlatte.EtterlatteService
import no.nav.eessi.pensjon.journalforing.journalpost.JournalpostService
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Period


private const val FAGSAK = "FAGSAK"
private const val PP01 = "PP01"
private const val EY = "EY"
@Service
class HentTemaService(
    private val etterlatteService: EtterlatteService,
    private val journalpostService: JournalpostService,
    private val gcpStorageService: GcpStorageService
) {

    private val logger = LoggerFactory.getLogger(HentTemaService::class.java)

    /**
     * Tema er PENSJON såfremt det ikke er en
     * - uføre buc (P_BUC_03)
     * - saktype er UFØRETRYGD
     */
    fun hentTema(
        sedhendelse: SedHendelse?,
        fnr: Fodselsnummer?,
        identifisertePersoner: Int,
        saksInfo: SaksInfoSamlet?,
        currentSed: SED?
    ): Tema {
        val ufoereSak = saksInfo?.saktype == SakType.UFOREP
        if(fnr == null) {
            if(sedhendelse?.bucType == BucType.P_BUC_03 || ufoereSak || currentSed is P15000 && currentSed.hasUforePensjonType()) return Tema.UFORETRYGD
            return Tema.PENSJON
        }
        if (sedhendelse?.rinaSakId != null && gcpStorageService.gjennyFinnes(sedhendelse.rinaSakId)) {
            val blob = gcpStorageService.hentFraGjenny(sedhendelse.rinaSakId)
            return if (blob?.contains("BARNEP") == true) Tema.EYBARNEP else Tema.OMSTILLING
        }

        //https://confluence.adeo.no/pages/viewpage.action?pageId=603358663
        val enPersonOgUforeAlderUnder62 = identifisertePersoner == 1 && erUforAlderUnder62(fnr)
        return when (sedhendelse?.bucType) {

            BucType.P_BUC_01, BucType.P_BUC_02 -> if (identifisertePersoner == 1 && (ufoereSak || enPersonOgUforeAlderUnder62)) Tema.UFORETRYGD else Tema.PENSJON
            BucType.P_BUC_03 -> Tema.UFORETRYGD
            BucType.P_BUC_06 -> temaPbuc06(currentSed, enPersonOgUforeAlderUnder62, saksInfo)
            BucType.P_BUC_07, BucType.P_BUC_08 -> temaPbuc07Og08(currentSed, enPersonOgUforeAlderUnder62, saksInfo)
            BucType.P_BUC_04, BucType.P_BUC_05, BucType.P_BUC_09 -> if (enPersonOgUforeAlderUnder62 || ufoereSak) Tema.UFORETRYGD else Tema.PENSJON
            BucType.P_BUC_10 -> temaPbuc10(currentSed, enPersonOgUforeAlderUnder62, saksInfo)
            else -> if (ufoereSak && erUforAlderUnder62(fnr)) Tema.UFORETRYGD else Tema.PENSJON

        }.also { logger.info("Henting av tema for ${sedhendelse?.bucType ?: "ukjent bucType"} gir tema: $it, hvor enPersonOgUforeAlderUnder62: $enPersonOgUforeAlderUnder62") }
    }

    private fun temaPbuc10(
        currentSed: SED?,
        enPersonOgUforeAlderUnder62: Boolean,
        saksInfo: SaksInfoSamlet?
    ): Tema {
        val uforeSakTypeEllerUforPerson = saksInfo?.saktype == SakType.UFOREP || enPersonOgUforeAlderUnder62
        val isUforePensjon = if (currentSed is P15000 && saksInfo?.sakInformasjon?.sakStatus == SakStatus.LOPENDE) currentSed.hasUforePensjonType() else false
        return if (isUforePensjon || uforeSakTypeEllerUforPerson) Tema.UFORETRYGD else Tema.PENSJON
    }

    private fun temaPbuc07Og08(
        currentSed: SED?,
        enPersonOgUforeAlderUnder62: Boolean,
        saksInfo: SaksInfoSamlet?
    ): Tema {
        val isUforeP12000 = (currentSed as? P12000)?.hasUforePensjonType() ?: false
        val isUforeSakType = saksInfo?.saktype == SakType.UFOREP

        return if (isUforeP12000 || enPersonOgUforeAlderUnder62 || isUforeSakType) Tema.UFORETRYGD else Tema.PENSJON
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
        val uforeSakTypeEllerUforPerson = saksInfo?.saktype == SakType.UFOREP || enPersonOgUforeAlderUnder62
        return if (isUforePensjon || uforeSakTypeEllerUforPerson) Tema.UFORETRYGD else Tema.PENSJON
    }

    fun enhetBasertPaaBehandlingstema(
        sedHendelse: SedHendelse?,
        sakinfo: SaksInfoSamlet?,
        identifisertPerson: IdentifisertPerson,
        antallIdentifisertePersoner: Int,
        currentSed: SED?
    ): Enhet {
        val tema = hentTema(sedHendelse, identifisertPerson.fnr, antallIdentifisertePersoner, sakinfo, currentSed)
        val behandlingstema = journalpostService.bestemBehandlingsTema(
            sedHendelse?.bucType!!,
            sakinfo?.saktype,
            tema,
            antallIdentifisertePersoner,
            currentSed
        )

        logger.info("${sedHendelse.sedType} gir landkode: ${identifisertPerson.landkode}, behandlingstema: $behandlingstema, tema: $tema")

        return if (identifisertPerson.landkode == "NOR") {
            when (behandlingstema) {
                Behandlingstema.GJENLEVENDEPENSJON, Behandlingstema.BARNEP, Behandlingstema.ALDERSPENSJON, Behandlingstema.TILBAKEBETALING -> Enhet.NFP_UTLAND_AALESUND
                Behandlingstema.UFOREPENSJON -> Enhet.UFORE_UTLANDSTILSNITT
            }
        } else when (behandlingstema) {
            Behandlingstema.GJENLEVENDEPENSJON, Behandlingstema.BARNEP, Behandlingstema.ALDERSPENSJON, Behandlingstema.TILBAKEBETALING -> Enhet.PENSJON_UTLAND
            Behandlingstema.UFOREPENSJON -> Enhet.UFORE_UTLANDSTILSNITT
        }
    }

    private fun hentGjennySak(sakIdFromSed: String?, euxCaseId: String): Sak? {
        sakIdFromSed?.let { sakId ->
            etterlatteService.hentGjennySak(sakId).fold(
                onSuccess = { gjennySak -> return Sak(FAGSAK, gjennySak?.id.toString(), EY) },
                onFailure = { logger.warn("Finner ingen gjennySak for rinasakId: $euxCaseId, og sakID: $sakId") }
            )
        }
        return null
    }

    private fun hentGjennyFraGCP(euxCaseId: String): Sak? {
        return gcpStorageService.hentFraGjenny(euxCaseId)?.let { mapJsonToAny<GjennySak>(it) }?.sakId
            ?.let { Sak(FAGSAK, it, EY) }
    }

    private fun validerPesysSak(sakInfo: SakInformasjon?, sakIdFromSed: String?): Sak? {
        sakInfo?.sakId?.takeIf { it.isNotBlank() && it.erGyldigPesysNummer() }?.let { sakId ->
            return lagSak(sakId, sakInfo.sakType, sakInfo.sakStatus)
        }

        sakIdFromSed?.takeIf { it.isNotBlank() && it.erGyldigPesysNummer() }?.let {
            logger.info("Har funnet saksinformasjon fra SED: $it")
            return Sak(FAGSAK, it, PP01)
        }

        return null
    }

    private fun lagSak(sakId: String, sakType: SakType?, sakStatus: SakStatus?): Sak {
        logger.info("Har funnet saksinformasjon fra pesys: $sakId, saksType:$sakType, sakStatus:$sakStatus")
        return Sak(FAGSAK, sakId, PP01)
    }

    private fun logOgReturnerNull(euxCaseId: String, sakIdFromSed: String?, sakInfo: SakInformasjon?): Sak? {
        logger.warn(
            """RinaID: $euxCaseId
            | sakIdFromSed: $sakIdFromSed eller sakId fra saksInformasjon: ${sakInfo?.sakId}
            | mangler verdi eller er ikke gyldig pesys nummer""".trimMargin()
        )
        return null
    }

    fun String?.erGyldigPesysNummer(): Boolean {
        return this?.let { it.length == 8 && it.first() in listOf('1', '2') && it.all { char -> char.isDigit() } } ?: false
    }

    fun erUforAlderUnder62(fnr: Fodselsnummer?) = Period.between(fnr?.getBirthDate(), LocalDate.now()).years in 18..61
}