package no.nav.eessi.pensjon.listeners.fagmodul

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.collections.firstOrNull

@Service
class FagmodulService(private val fagmodulKlient: FagmodulKlient) {

    private val logger = LoggerFactory.getLogger(FagmodulService::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")

    /**
     * Henter pensjonssak fra PESYS basert på aktoerId og sakId fra SED.
     * 1. Om vi ikke har sakId fra SED, henter vi sakId fra PESYS og returnerer listen uten match mot sd
     * 2. Om vi har sakId fra SED, henter vi sakId fra PESYS og returnerer match mot sakId fra SED om den finnes, og listen
     */
    fun hentPensjonSakFraPesys(
        aktoerId: String,
        alleSakIdFraSED: List<String>?,
        pesysIdFraSed: List<String>,
        bucType: BucType
    ): Pair<SakInformasjon?, List<SakInformasjon>>? {
        val pensjonsInformasjon = hentPesysSakId(aktoerId, bucType)
        if (alleSakIdFraSED.isNullOrEmpty()) return Pair(null, pensjonsInformasjon)

        val resultatFraCurrentSedId = pesysIdFraSed.mapNotNull { sakId ->
            hentGyldigSakInformasjonFraPensjonSak(aktoerId, sakId, pensjonsInformasjon)
        }

        val resultatFraAlleSedId = alleSakIdFraSED.mapNotNull { sakId ->
            hentGyldigSakInformasjonFraPensjonSak(aktoerId, sakId, pensjonsInformasjon)
        }
        val collectedResults = resultatFraAlleSedId + resultatFraCurrentSedId
        return collectedResults.find { it.first != null}
            ?: collectedResults.find { alleSakIdFraSED.contains(it.first?.sakId) }
            ?: collectedResults.firstOrNull()
            ?: Pair(null, emptyList())
    }

    fun hentPesysSakId(aktoerId: String, bucType: BucType): List<SakInformasjon> {
        val eessipenSakTyper = listOf(UFOREP, GJENLEV, BARNEP, ALDER, GENRL, OMSORG)

        val sak = fagmodulKlient.hentPensjonSaklist(aktoerId)
            .filter { it.sakId != null }
            .filter { it.sakType in eessipenSakTyper }
            .also {
                secureLog.info("Svar fra pensjonsinformasjon: ${it.toJson()}")
            }
        if (bucType == BucType.P_BUC_03) {
            sak.sortedBy { it.sakType == UFOREP }
        } else {
            sak.sortedBy { it.sakType == UFOREP }
        }

        return sak

    }

    fun String?.erGyldigPesysNummer(): Boolean {
        if (this.isNullOrEmpty()) return false
        return this.length == 8 && this.first() in listOf('1', '2') && this.all { it.isDigit() }
    }

    fun hentGyldigSakInformasjonFraPensjonSak(aktoerId: String, pesysSakIdFraSed: String?, saklistFraPesys: List<SakInformasjon>): Pair<SakInformasjon?, List<SakInformasjon>>? {

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

    fun hentPesysSakIdFraSED(sedListe: List<SED>, currentSed: SED?): Pair<List<String>, List<String>>? {
        val sakIdFraAlleSedIBuc = sedListe
            .flatMap { filterEESSIsak(it) ?: emptyList() }
            .map { sakId: String -> trimSakidString(sakId) }
            .filter { it.erGyldigPesysNummer() }
            .distinct()
            .also { sakId -> logger.info("Fant sakId i SED: $sakId.") }

        if (sakIdFraAlleSedIBuc.isEmpty()) {
            logger.warn("Fant ingen sakId i SED")
            return Pair(emptyList(), emptyList())
        }
        if (sakIdFraAlleSedIBuc.size > 1) logger.warn("Fant flere sakId i SED: $sakIdFraAlleSedIBuc, filtrer bort de som ikke er i seden som behandles")

        val sakIdCurrentSed = currentSed?.nav?.eessisak?.mapNotNull { it.saksnummer }?.distinct() ?: emptyList()
        return Pair(sakIdCurrentSed, sakIdFraAlleSedIBuc)
    }

    fun hentGjennySakIdFraSed(currentSed: SED?): String? {
        val sakIdFraSed = currentSed?.nav?.eessisak?.mapNotNull { it.saksnummer }
            ?.map { id -> trimSakidString(id) }
            ?.distinct()
            .also { sakId -> logger.info("Fant gjenny sakId i SED: $sakId. Antall gjennysaker funnet: ${sakId?.size}") }

        if (sakIdFraSed?.isEmpty() == true) logger.warn("Fant ingen gjenny sakId i SEDen")
        return sakIdFraSed?.firstOrNull().also { logger.info("Gjenny sakId fra SED: $it") }
    }

    private fun filterEESSIsak(sed: SED): List<String>? {
        val sak = sed.nav?.eessisak ?: return null
        logger.info("Sak fra SED: ${sak.toJson()}")

        return sak.filter { it.land == "NO" }
            .mapNotNull { it.saksnummer }
    }

    //TODO: replace 11 sifre med * i tilfelle det er et fnr
    private fun trimSakidString(saknummerAsString: String) = saknummerAsString.replace("[^0-9]".toRegex(), "")


}