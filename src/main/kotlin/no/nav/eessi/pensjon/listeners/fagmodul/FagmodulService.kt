package no.nav.eessi.pensjon.listeners.fagmodul

import no.nav.eessi.pensjon.eux.model.BucType
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

    fun hentPesysSakId(aktoerId: String, bucType: BucType): List<SakInformasjon>? {
        val eessipenSakTyper = listOf(UFOREP, GJENLEV, BARNEP, ALDER, GENRL, OMSORG)

        val sak = fagmodulKlient.hentPensjonSaklist(aktoerId).also {secureLog.info("Svar fra pensjonsinformasjon, før filtrering: ${it.toJson()}")}
            .filter { it.sakId != null && it.sakType in eessipenSakTyper }
            .also {
                secureLog.info("Svar fra pensjonsinformasjon: ${it.toJson()}")
            }
        if (bucType == BucType.P_BUC_03) {
            return sak.sortedBy { if (it.sakType == UFOREP) 0 else 1 }
        } else {
            logger.info("Velger ALDER før UFOERE dersom begge finnes")
            return sak.sortedBy { if (it.sakType == ALDER) 0 else 1 }
        }
    }

    fun String?.erGyldigPesysNummer(): Boolean {
        if (this.isNullOrEmpty()) return false
        return this.length == 8 && this.first() in listOf('1', '2') && this.all { it.isDigit() }
    }

    fun hentGyldigSakInformasjonFraPensjonSak(
        aktoerId: String,
        pesysSakIdFraSed: String?,
        saklistFraPesys: List<SakInformasjon>,
        bucType: BucType? = null
    ): Pair<SakInformasjon?, List<SakInformasjon>>? {

        if (saklistFraPesys.isEmpty()) {
            logger.warn("Finner ingen pensjonsinformasjon for aktoerid: $aktoerId med pesys sakID: $pesysSakIdFraSed ")
            return null
        }
        logger.info("aktoerid: $aktoerId pesys sakID: $pesysSakIdFraSed Pensjoninformasjon: ${saklistFraPesys.toJson()}")

        if (saklistFraPesys.none { it.sakId == pesysSakIdFraSed }) {
            logger.error("Vi finner en sak fra pesys som ikke matcher sakId fra sed for: $aktoerId med pesys sakID: $pesysSakIdFraSed fra listen: ${saklistFraPesys.toJson()}")
            if(bucType == BucType.P_BUC_01 && saklistFraPesys.any { sak -> sak.sakType == ALDER } ) {
                return Pair(saklistFraPesys.first(), saklistFraPesys)
            }
            if(bucType == BucType.P_BUC_03 && saklistFraPesys.any { sak -> sak.sakType == UFOREP } ) {
                return Pair(saklistFraPesys.first(), saklistFraPesys)
            }
            return Pair(null, saklistFraPesys)
        }

        val gyldigSak = saklistFraPesys.firstOrNull { it.sakId == pesysSakIdFraSed } ?: return null.also {
            logger.info("Returnerer første match for pesys sakID: $pesysSakIdFraSed da flere saker ble funnet")
        }

        return Pair(gyldigSak, saklistFraPesys)
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

        val sakIdCurrentSed = currentSed?.nav?.eessisak
            ?.filter { it.land == "NO" }
            ?.mapNotNull { it.saksnummer }
            ?.filter { it.erGyldigPesysNummer() }
            ?.distinct()
            ?: emptyList()
        return Pair(sakIdCurrentSed, sakIdFraAlleSedIBuc)
    }

    fun hentGjennySakIdFraSed(currentSed: SED?): String? {
        val sakIdFraSed = currentSed?.nav?.eessisak?.filter { it.land == "NO" }
            ?.mapNotNull { it.saksnummer}
            ?.map { id -> trimSakidString(id) }
            ?.filter {  it.length == 5 }
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