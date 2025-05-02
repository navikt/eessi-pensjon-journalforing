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

    fun hentPensjonSakFraPesys(aktoerId: String, alleSedIBuc: List<SED>, currentSed: SED?): Pair<SakInformasjon?, List<SakInformasjon>>? {
        return hentSakIdFraSED(alleSedIBuc, currentSed)?.let { sakId ->
            if (sakId.first.erGyldigPesysNummer().not()) {
                logger.warn("Det er registert feil eller ugyldig pesys sakID: ${sakId.first} for aktoerid: $aktoerId")
                return null
            }
            hentSakInformasjonFraPensjonSak(aktoerId, sakId.first)
        }
    }

    fun String?.erGyldigPesysNummer(): Boolean {
        if(this.isNullOrEmpty()) return false
        return this.length == 8 && this.first() in listOf('1', '2') && this.all { it.isDigit() }
    }

    private fun hentSakInformasjonFraPensjonSak(aktoerId: String, pesysSakId: String?): Pair<SakInformasjon?, List<SakInformasjon>>? {
        val eessipenSakTyper = listOf(UFOREP, GJENLEV, BARNEP, ALDER, GENRL, OMSORG)

        val saklist: List<SakInformasjon> = fagmodulKlient.hentPensjonSaklist(aktoerId)
            .filter { it.sakType in eessipenSakTyper }

        secureLog.info("Svar fra pensjonsinformasjon: ${saklist.toJson()}")

        if(saklist.isEmpty()){
            logger.warn("Finner ingen pensjonsinformasjon for aktoerid: $aktoerId med pesys sakID: $pesysSakId ")
            return null
        }
        logger.info("aktoerid: $aktoerId pesys sakID: $pesysSakId Pensjoninformasjon: ${saklist.toJson()}")

        if(saklist.none { it.sakId == pesysSakId }) {
            logger.error("Vi finner en sak fra pesys som ikke matcher sakId fra sed for: $aktoerId med pesys sakID: $pesysSakId fra listen: ${saklist.toJson()}")
            return null
        }

        val gyldigSak = saklist.firstOrNull { it.sakId == pesysSakId } ?: return null.also {
            logger.info("Returnerer første match for pesys sakID: $pesysSakId da flere saker ble funnet")
        }

        // saker med flere tilknyttede saker
        return if (saklist.size > 1) {
            Pair(gyldigSak.copy(tilknyttedeSaker = saklist.filterNot { it.sakId == gyldigSak.sakId }), saklist)
        } else {
            Pair(gyldigSak, saklist)
        }
    }

    fun hentSakIdFraSED(sedListe: List<SED>, currentSed: SED?): Pair<String?, List<String?>>? {
        val sakIdFraAlleSedIBuc = sedListe
            .mapNotNull { filterEESSIsak(it) }
            .map { trimSakidString(it) }
            .filter { it.erGyldigPesysNummer() }
            .filter { it == currentSed?.nav?.eessisak?.firstOrNull()?.saksnummer }
            .distinct()
            .also { sakId -> logger.info("Fant sakId i SED: $sakId.") }

        if (sakIdFraAlleSedIBuc.isEmpty()) {
            logger.warn("Fant ingen sakId i SED")
            return Pair(null, listOf(null))
        }
        if(sakIdFraAlleSedIBuc.size > 1) logger.warn("Fant flere sakId i SED: $sakIdFraAlleSedIBuc, filtrer bort de som ikke er i seden som behandles")

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