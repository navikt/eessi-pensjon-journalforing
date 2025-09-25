package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory

object FiltrerPesysSakFraSedUtil {

    private val logger = LoggerFactory.getLogger(FiltrerPesysSakFraSedUtil::class.java)

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

    private fun filterEESSIsak(sed: SED): List<String>? {
        val sak = sed.nav?.eessisak ?: return null
        logger.info("Sak fra SED: ${sak.toJson()}")

        return sak.filter { it.land == "NO" }
            .mapNotNull { it.saksnummer }
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
            logger.warn("Vi finner en sak fra pesys som ikke matcher sakId fra sed for: $aktoerId med pesys sakID: $pesysSakIdFraSed fra listen: ${saklistFraPesys.toJson()}")
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


    //TODO: replace 11 sifre med * i tilfelle det er et fnr
    fun trimSakidString(saknummerAsString: String) = saknummerAsString.replace("[^0-9]".toRegex(), "")
}