package no.nav.eessi.pensjon.buc

import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SedDokumentHelper(private val fagmodulKlient: FagmodulKlient,
                        private val euxKlient: EuxKlient) {

    private val logger = LoggerFactory.getLogger(SedDokumentHelper::class.java)

    private val validSedtype = listOf("P2000", "P2100", "P2200", "P1000",
            "P5000", "P6000", "P7000", "P8000", "P9000",
            "P10000", "P1100", "P11000", "P12000", "P14000", "P15000", "H070", "R005")

    fun hentAlleSedIBuc(rinaSakId: String): List<SED> {
        return fagmodulKlient.hentAlleDokumenter(rinaSakId)
                .filterNot { doc -> doc.status == "empty" }
                .filter { doc -> doc.type.name in validSedtype }
                .mapNotNull { sed -> euxKlient.hentSed(rinaSakId, sed.id) }
    }

    fun hentYtelseType(sedHendelse: SedHendelseModel, alleSedIBuc: List<SED>): YtelseType? {
        //hent ytelsetype fra R_BUC_02 - R005 sed
        if (sedHendelse.bucType == BucType.R_BUC_02) {
            return alleSedIBuc
                    .firstOrNull { it.type == SedType.R005 }
                    ?.let { filterYtelseTypeR005(it) }

        } else if (sedHendelse.bucType == BucType.P_BUC_10) {
            //hent ytelsetype fra P15000 overgang fra papir til rina. (saktype)
            val sed = alleSedIBuc.firstOrNull { it.type == SedType.P15000 }
            if (sed != null) {
                return when (sed.nav?.krav?.type) {
                    "02" -> YtelseType.GJENLEV
                    "03" -> YtelseType.UFOREP
                    else -> YtelseType.ALDER
                }
            }
        }
        return null
    }

    fun hentPensjonSakFraSED(aktoerId: String, alleSedIBuc: List<SED>): SakInformasjon? {
        return hentSakIdFraSED(alleSedIBuc)?.let { sakId ->
            validerSakIdFraSEDogReturnerPensjonSak(aktoerId, sakId)
        }
    }

    private fun filterYtelseTypeR005(sed: SED): YtelseType {
        val type = sed.tilbakekreving?.feilutbetaling?.ytelse?.type

        return when (type) {
            "alderspensjon" -> YtelseType.ALDER
            "uførepensjon" -> YtelseType.UFOREP
            "etterlattepensjon_enke", "etterlattepensjon_enkemann", "andre_former_for_etterlattepensjon" -> YtelseType.GJENLEV
            else -> throw RuntimeException("Klarte ikke å finne ytelsetype for R_BUC_02")
        }
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

    private fun validerSakIdFraSEDogReturnerPensjonSak(aktoerId: String, sedSakId: String): SakInformasjon? {
        val saklist: List<SakInformasjon> = try {
            fagmodulKlient.hentPensjonSaklist(aktoerId)
        } catch (e: Exception) {
            logger.warn("Feil ved henting av saker på aktørId=$aktoerId – Returnerer tom liste. ", e)
            return null
        }
        logger.info("aktoerid: $aktoerId sedSak: $sedSakId Pensjoninformasjon: ${saklist.toJson()}")

        val gyldigSak = saklist.firstOrNull { it.sakId == sedSakId }
        return if (saklist.size > 1)
            gyldigSak?.copy(tilknyttedeSaker = saklist.filterNot { it.sakId == gyldigSak.sakId })
        else
            gyldigSak
    }

}
