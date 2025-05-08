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

    /**
     * Henter pensjonssak fra PESYS basert på aktoerId og sakId fra SED.
     * Vurderer
     */
    fun hentPensjonSakFraPesys(aktoerId: String, alleSakIdFraSED: List<String>?, currentSed: SED?): Pair<SakInformasjon?, List<SakInformasjon>>? {
        if (alleSakIdFraSED.isNullOrEmpty()) return Pair(null, emptyList())

        val pensjonsInformasjon = hentPesysSakId(aktoerId)
        val collectedResults = alleSakIdFraSED.mapNotNull { sakId ->
            hentGyldigSakInformasjonFraPensjonSak(aktoerId, sakId, pensjonsInformasjon)
        }

        return collectedResults.find { currentSed?.nav?.eessisak?.any { eessiSak -> eessiSak.saksnummer == it.first?.sakId } == true }
            ?: collectedResults.find { alleSakIdFraSED.contains(it.first?.sakId) }
            ?: collectedResults.find { it.first != null }
            ?: collectedResults.firstOrNull()
            ?: Pair(null, emptyList())
    }

    fun hentPesysSakId(aktoerId: String): List<SakInformasjon> {
        val eessipenSakTyper = listOf(UFOREP, GJENLEV, BARNEP, ALDER, GENRL, OMSORG)

        return fagmodulKlient.hentPensjonSaklist(aktoerId)
            .filter { it.sakType in eessipenSakTyper }.also {
                secureLog.info("Svar fra pensjonsinformasjon: ${it.toJson()}")
            }
    }


    fun String?.erGyldigPesysNummer(): Boolean {
        if (this.isNullOrEmpty()) return false
        return this.length == 8 && this.first() in listOf('1', '2') && this.all { it.isDigit() }
    }

    private fun hentGyldigSakInformasjonFraPensjonSak(aktoerId: String, pesysSakIdFraSed: String?, saklistFraPesys: List<SakInformasjon>): Pair<SakInformasjon?, List<SakInformasjon>>? {

        if (saklistFraPesys.isEmpty()) {
            logger.warn("Finner ingen pensjonsinformasjon for aktoerid: $aktoerId med pesys sakID: $pesysSakIdFraSed ")
            return null
        }
        logger.info("aktoerid: $aktoerId pesys sakID: $pesysSakIdFraSed Pensjoninformasjon: ${saklistFraPesys.toJson()}")

        if (saklistFraPesys.none { it.sakId == pesysSakIdFraSed }) {
            logger.error("Vi finner en sak fra pesys som ikke matcher sakId fra sed for: $aktoerId med pesys sakID: $pesysSakIdFraSed fra listen: ${saklistFraPesys.toJson()}")
            return Pair(null, saklistFraPesys)
        }

        val gyldigSak = saklistFraPesys.firstOrNull { it.sakId == pesysSakIdFraSed } ?: return null.also {
            logger.info("Returnerer første match for pesys sakID: $pesysSakIdFraSed da flere saker ble funnet")
        }

        // saker med flere tilknyttede saker
        return if (saklistFraPesys.size > 1) {
            Pair(gyldigSak.copy(tilknyttedeSaker = saklistFraPesys.filterNot { it.sakId == gyldigSak.sakId }), saklistFraPesys)
        } else {
            Pair(gyldigSak, saklistFraPesys)
        }
    }

    fun hentPesysSakIdFraSED(sedListe: List<SED>, currentSed: SED?): Pair<String?, List<String>>? {
        val sakIdFraAlleSedIBuc = sedListe
            .mapNotNull { filterEESSIsak(it) }
            .map { trimSakidString(it) }
            .filter { it.erGyldigPesysNummer() }
            .filter { it in (currentSed?.nav?.eessisak?.mapNotNull { it.saksnummer } ?: emptyList()) }
            .distinct()
            .also { sakId -> logger.info("Fant sakId i SED: $sakId.") }

        if (sakIdFraAlleSedIBuc.isEmpty()) {
            logger.warn("Fant ingen sakId i SED")
            return Pair(null, emptyList())
        }
        if (sakIdFraAlleSedIBuc.size > 1) logger.warn("Fant flere sakId i SED: $sakIdFraAlleSedIBuc, filtrer bort de som ikke er i seden som behandles")

        val sakID = sakIdFraAlleSedIBuc.find { it == currentSed?.nav?.eessisak?.firstOrNull()?.saksnummer }
        return Pair(sakID ?: sakIdFraAlleSedIBuc.firstOrNull(), sakIdFraAlleSedIBuc)

    }

    fun hentGjennySakIdFraSed(currentSed: SED?): String? {
        val sakIdFraSed = currentSed?.nav?.eessisak?.mapNotNull { it.saksnummer }
            ?.map { id -> trimSakidString(id) }
            ?.distinct()
            .also { sakId -> logger.info("Fant gjenny sakId i SED: $sakId. Antall gjennysaker funnet: ${sakId?.size}") }

        if (sakIdFraSed?.isEmpty() == true) logger.warn("Fant ingen gjenny sakId i SEDen")
        return sakIdFraSed?.firstOrNull().also { logger.info("Gjenny sakId fra SED: $it") }
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