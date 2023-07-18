package no.nav.eessi.pensjon.klienter.fagmodul

import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FagmodulService(private val fagmodulKlient: FagmodulKlient) {

    private val logger = LoggerFactory.getLogger(FagmodulService::class.java)


    fun hentPensjonSakFraPesys(aktoerId: String, alleSedIBuc: List<SED>): SakInformasjon? {
        return hentSakIdFraSED(alleSedIBuc)?.let { sakId ->
            validerSakIdFraSEDogReturnerPensjonSak(aktoerId, sakId)
        }
    }

    private fun validerSakIdFraSEDogReturnerPensjonSak(aktoerId: String, pesysSakId: String): SakInformasjon? {
        val eessipenSakTyper = listOf(UFOREP, GJENLEV, BARNEP, ALDER, GENRL, OMSORG)
        val saklist: List<SakInformasjon> = try {
            fagmodulKlient.hentPensjonSaklist(aktoerId)
        } catch (e: Exception) {
            logger.warn("Feil ved henting av saker på aktørId=$aktoerId – Returnerer tom liste. ", e)
            return null
        }
        if(saklist.isEmpty()){
            logger.warn("Finner ingen pensjonsinformasjon for aktoerid: $aktoerId med pesys sakID: $pesysSakId ") 
        }
        else{
            logger.info("aktoerid: $aktoerId pesys sakID: $pesysSakId Pensjoninformasjon: ${saklist.toJson()}")
        }

        val gyldigSak = saklist.firstOrNull { it.sakId == pesysSakId }
        if (gyldigSak?.sakType !in eessipenSakTyper ||gyldigSak == null) {
            logger.info("Finner ingen sakId i saksliste for aktoer for sedSakId: $pesysSakId")
            return null
        }
        //noe skjer når det er generell saker
        return if (saklist.size > 1)
            gyldigSak.copy(tilknyttedeSaker = saklist.filterNot { it.sakId == gyldigSak.sakId })
        else
            gyldigSak
    }

    private fun hentSakIdFraSED(sedListe: List<SED>): String? {
        return sedListe
            .mapNotNull { sed -> filterEESSIsak(sed) }
            .map { id -> trimSakidString(id) }
            .distinct()
            .singleOrNull()
            .also { sakId -> logger.debug("Fant sakId i SED: $sakId") }
    }

    private fun filterEESSIsak(sed: SED): String? {
        val sak = sed.nav?.eessisak ?: return null

        return sak.filter { it.land == "NO" }
            .mapNotNull { it.saksnummer }
            .lastOrNull()
    }

    private fun trimSakidString(saknummerAsString: String) = saknummerAsString.replace("[^0-9]".toRegex(), "")



}