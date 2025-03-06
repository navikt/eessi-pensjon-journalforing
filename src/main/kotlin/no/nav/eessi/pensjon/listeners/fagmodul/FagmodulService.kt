package no.nav.eessi.pensjon.listeners.fagmodul

import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FagmodulService(private val fagmodulKlient: FagmodulKlient) {

    private val logger = LoggerFactory.getLogger(FagmodulService::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")

    fun hentPensjonSakFraPesys(aktoerId: String, alleSedIBuc: List<SED>, currentSed: SED?): SakInformasjon? {
        return hentSakIdFraSED(alleSedIBuc, currentSed,)?.let { sakId ->
            if (sakId.erGyldigPesysNummer().not()) {
                logger.warn("Det er registert feil eller ugyldig pesys sakID: ${sakId} for aktoerid: $aktoerId")
                return null
            }
            hentSakInformasjonFraPensjonSak(aktoerId, sakId)
        }
    }

    fun String?.erGyldigPesysNummer(): Boolean {
        if(this.isNullOrEmpty()) return false
        return this.length == 8 && this.first() in listOf('1', '2') && this.all { it.isDigit() }
    }

    private fun hentSakInformasjonFraPensjonSak(aktoerId: String, pesysSakId: String?): SakInformasjon? {
        val eessipenSakTyper = listOf(UFOREP, GJENLEV, BARNEP, ALDER, GENRL, OMSORG)
        val saklist: List<SakInformasjon> = fagmodulKlient.hentPensjonSaklist(aktoerId)
        secureLog.info("Svar fra pensjonsinformasjon: ${saklist.toJson()}")

        if(saklist.isEmpty()){
            logger.warn("Finner ingen pensjonsinformasjon for aktoerid: $aktoerId med pesys sakID: $pesysSakId ")
            return null
        }
        logger.info("aktoerid: $aktoerId pesys sakID: $pesysSakId Pensjoninformasjon: ${saklist.toJson()}")

        val gyldigSak = saklist.firstOrNull { it.sakId == pesysSakId }

        if (gyldigSak?.sakType !in eessipenSakTyper ||gyldigSak == null) {
            logger.info("Finner ingen sakId i saksliste for aktoer for sedSakId: $pesysSakId")
            return null
        }
        // saker med flere tilknyttede sakerx
        return gyldigSak.takeIf { saklist.size <= 1 }
            ?: gyldigSak.copy(tilknyttedeSaker = saklist.filterNot { it.sakId == gyldigSak.sakId })

    }

    fun hentSakIdFraSED(sedListe: List<SED>, currentSed: SED?, erGjennysak: Boolean? = null): String? {
        val sakerFraSed = sedListe
            .mapNotNull { sed -> filterEESSIsak(sed) }
            .map { id -> trimSakidString(id) }
            .filter { it.erGyldigPesysNummer() || erGjennysak == true }
            .distinct()
            .also { sakId -> logger.info("Fant sakId i SED: $sakId , sak er gjennysak: $erGjennysak") }

        if (sakerFraSed.isEmpty()) logger.warn("Fant ingen sakId i SED")

        if (sakerFraSed.size > 1) {
            logger.warn("Fant flere sakId i SED: $sakerFraSed, filtrer bort alle som ikke er pesysnr")

            //ser om vi har en treff mot SED som skal journalføres, dette vil kun gjelde utgående SED
            val sakID = sakerFraSed.find { it == currentSed?.nav?.eessisak?.firstOrNull()?.saksnummer }
            if (sakID != null) {
                logger.info("Fant pesys sakId fra SED med samme akId i EESSI: $sakID")
                return sakID
            }

            if (sakerFraSed.size > 1) {
                logger.error("Fant flere gyldige pesys sakId i SED: $sakerFraSed  (kan fremdeles finne saktype fra bestemsak)")
                return null
            }
            return sakerFraSed.firstOrNull().also { logger.info("Pesys sakId fra SED, etter filtrering: $it") }
        }

        return sakerFraSed.firstOrNull().also { logger.info("Pesys sakId fra SED: $it") }
    }

    private fun filterEESSIsak(sed: SED): String? {
        val sak = sed.nav?.eessisak ?: return null
        logger.info("Sak fra SED: ${sak.toJson()}")

        return sak.filter { it.land == "NO" }
            .mapNotNull { it.saksnummer }
            .lastOrNull()
    }

    //TODO: replace 11 sifre med * i tilfelle det er et fnr
    private fun trimSakidString(saknummerAsString: String) = saknummerAsString.replace("[^0-9]".toRegex(), "")



}